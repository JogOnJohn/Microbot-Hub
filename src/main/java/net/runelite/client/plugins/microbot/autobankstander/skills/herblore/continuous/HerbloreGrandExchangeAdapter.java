package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Bounded GE adapter. The controller remains the authority for every price and accounting decision. */
@Slf4j
public final class HerbloreGrandExchangeAdapter {
    private final ContinuousHerbloreController controller;
    private GrandExchangeSlots activeSlot;
    private int activeItemId = -1;
    private boolean selling;
    private int totalQuantityReconciled;

    public HerbloreGrandExchangeAdapter(ContinuousHerbloreController controller) {
        if (controller == null) throw new IllegalArgumentException("controller is required");
        this.controller = controller;
    }

    public boolean placeBuy(int itemId, int quantity, int unitPrice, long availableCoins) {
        if (activeSlot != null || !controller.mayBuy(unitPrice, quantity, availableCoins)) return false;
        String name = itemName(itemId);
        if (name == null || Rs2GrandExchange.hasBuyOffer(itemId) != null) return false;
        if (!Rs2GrandExchange.buyItem(name, unitPrice, quantity)) return false;
        sleepUntil(() -> Rs2GrandExchange.hasBuyOffer(itemId) != null, 3000);
        GrandExchangeOfferDetails details = Rs2GrandExchange.hasBuyOffer(itemId);
        if (details == null) {
            controller.stop("ambiguous GE buy dispatch attribution");
            return false;
        }
        activeSlot = details.getSlot();
        activeItemId = itemId;
        selling = false;
        log.info("Placed bounded GE buy: {} x {} at <= {} in {}", quantity, name, unitPrice, activeSlot);
        return true;
    }

    public boolean placeSell(int itemId, int quantity, int unitPrice) {
        if (activeSlot != null || !controller.maySell(unitPrice)) return false;
        String name = itemName(itemId);
        if (name == null || Rs2GrandExchange.hasSellOffer(itemId) != null) return false;
        if (!Rs2GrandExchange.sellItem(name, quantity, unitPrice)) return false;
        sleepUntil(() -> Rs2GrandExchange.hasSellOffer(itemId) != null, 3000);
        GrandExchangeOfferDetails details = Rs2GrandExchange.hasSellOffer(itemId);
        if (details == null) {
            controller.stop("ambiguous GE sell dispatch attribution");
            return false;
        }
        activeSlot = details.getSlot();
        activeItemId = itemId;
        selling = true;
        log.info("Placed bounded GE sell: {} x {} at >= {} in {}", quantity, name, unitPrice, activeSlot);
        return true;
    }

    /** Returns true only after a terminal offer was reconciled and collected to the bank. */
    public boolean reconcileAndCollect() {
        if (activeSlot == null) return false;
        GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(activeSlot);
        if (details == null || !details.isCompleted()) return false;
        if (details.getItemId() != activeItemId || details.isSelling() != selling) {
            controller.stop("ambiguous GE slot attribution");
            return false;
        }

        int completed = details.getQuantitySold();
        int actualCoins = details.getSpent();
        if (completed < 0 || actualCoins < 0) {
            controller.stop("ambiguous GE completion accounting");
            return false;
        }
        if (!Rs2GrandExchange.collectOffer(activeSlot, true)) return false;
        if (selling) controller.recordSale(actualCoins);
        else controller.recordPurchase(actualCoins);
        totalQuantityReconciled += completed;
        log.info("Reconciled GE {} in {}: item={}, quantity={}, coins={}",
                selling ? "sale" : "purchase", activeSlot, activeItemId, completed, actualCoins);
        clear();
        return true;
    }

    public boolean abortAndCollect() {
        if (activeSlot == null) return true;
        GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(activeSlot);
        if (details != null && (details.getItemId() != activeItemId
                || details.isSelling() != selling || details.getQuantitySold() < 0
                || details.getSpent() < 0)) {
            controller.stop("ambiguous GE abort accounting");
            return false;
        }
        boolean aborted = Rs2GrandExchange.abortOffer(itemName(activeItemId), true);
        if (aborted) {
            if (details != null) {
                if (selling) controller.recordSale(details.getSpent());
                else controller.recordPurchase(details.getSpent());
                totalQuantityReconciled += details.getQuantitySold();
            }
            clear();
        }
        return aborted;
    }

    private String itemName(int itemId) {
        if (itemId < 0 || Microbot.getRs2ItemManager() == null
                || Microbot.getRs2ItemManager().getItemComposition(itemId) == null) return null;
        return Microbot.getRs2ItemManager().getItemComposition(itemId).getName();
    }

    private void clear() {
        activeSlot = null;
        activeItemId = -1;
        selling = false;
    }

    public GrandExchangeSlots getActiveSlot() { return activeSlot; }
    public int getTotalQuantityReconciled() { return totalQuantityReconciled; }
    public void resetCycleQuantity() { totalQuantityReconciled = 0; }
}
