package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Separate, restartable Herblore workflow. Each processing phase delegates to a
 * quantity-limited normal processor; GE and decant actions remain explicit and
 * are reconciled before the controller may advance.
 */
@Slf4j
public final class ContinuousHerbloreProcessor implements BankStandingProcessor {
    private final ConfigData config;
    private final ContinuousHerblorePlan plan;
    private final ContinuousHerbloreController controller;
    private final HerbloreGrandExchangeAdapter exchange;
    private final HerbloreDecantAdapter decanter = new HerbloreDecantAdapter();
    private HerbloreRecipeMetadata recipe;
    private HerbloreProcessor phaseWorker;
    private ContinuousHerblorePhase workerPhase;
    private int baselineFinishedContainers;
    private int expectedOutputContainers;
    private String detail = "Awaiting precheck";
    private String lastEquipmentStatus = "Not checked";

    public ContinuousHerbloreProcessor(ConfigData config) {
        this.config = new ConfigData(config);
        this.plan = new ContinuousHerblorePlan(
                config.getContinuousCapitalReserve(), config.getContinuousMaxBuyPrice(),
                config.getContinuousMinSellPrice(), config.getContinuousRetryLimit(),
                config.getContinuousPhaseTimeoutSeconds() * 1000L,
                config.getContinuousStopLoss(), config.getContinuousCycleLimit(),
                config.isContinuousUnlimitedCycles(), config.isContinuousDecant(),
                true);
        this.controller = new ContinuousHerbloreController(plan, System.currentTimeMillis());
        this.exchange = new HerbloreGrandExchangeAdapter(controller);
    }

    @Override
    public boolean validate() {
        try {
            recipe = HerbloreRecipeMetadata.resolve(config.getFinishedPotion());
        } catch (RuntimeException ex) {
            detail = "Recipe metadata unavailable: " + ex.getMessage();
            log.info(detail);
            return false;
        }
        if (!recipe.hasCleanAndUnfinishedPhases() || !recipe.hasResolvedFinishedItems()) {
            detail = "Recipe is not a normal herb-to-potion shape";
            log.info("Continuous mode rejected {}: {}", config.getFinishedPotion(), detail);
            return false;
        }
        if (Rs2Player.getRealSkillLevel(Skill.HERBLORE) < recipe.level) {
            detail = "Herblore level " + recipe.level + " required";
            return false;
        }
        detail = recipe.potion + " x " + config.getContinuousQuantity();
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        requirements.add("Coins above reserve");
        requirements.add("Grimy " + recipe.herb.name().toLowerCase());
        requirements.add("Vials of water");
        requirements.add("Secondary ingredient " + recipe.secondaryId);
        return requirements;
    }

    /** The outer bank-stander state machine should hand control to this orchestrator. */
    @Override public boolean hasRequiredItems() { return true; }
    @Override public boolean performBanking() { return true; }

    @Override
    public boolean process() {
        long now = System.currentTimeMillis();
        if (controller.isPhaseTimedOut(now)) {
            exchange.abortAndCollect();
            phaseWorker = null;
            workerPhase = null;
            controller.failPhase("phase timeout", now);
            log.info("Restarting continuous phase {} (retry {}/{})",
                    controller.getPhase(), controller.getPhaseRetries(), config.getContinuousRetryLimit());
            return true;
        }

        switch (controller.getPhase()) {
            case PRECHECK: return precheck(now);
            case ACQUIRE_INPUTS: return acquire(now);
            case CLEAN_HERBS:
            case MAKE_UNFINISHED:
            case MAKE_FINISHED: return processPhase(now);
            case OPTIONAL_DECANT: return decant(now);
            case OPTIONAL_SELL: return sell(now);
            case RECONCILE: return reconcile(now);
            case STOPPED: return true;
            default: return false;
        }
    }

    private boolean precheck(long now) {
        detail = "Precheck and baseline";
        if (!ensureBankOpen()) return true;
        baselineFinishedContainers = bankFinishedContainers();
        expectedOutputContainers = config.getContinuousQuantity();
        if (config.isUseAmuletOfChemistry() && !recipe.chemistryEligible) {
            controller.stop("selected recipe is not chemistry eligible");
            return true;
        }
        controller.succeedPhase(now);
        return true;
    }

    private boolean acquire(long now) {
        detail = "Acquiring bounded inputs";
        if (exchange.getActiveSlot() != null) {
            if (!ensureExchangeOpen()) return true;
            exchange.reconcileAndCollect();
            return true;
        }
        if (!ensureBankOpen()) return true;

        Purchase need = nextPurchase();
        if (need == null) {
            exchange.resetCycleQuantity();
            controller.succeedPhase(now);
            return true;
        }

        long bankCoins = Rs2Bank.count(ItemID.COINS);
        long inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
        long available = bankCoins + inventoryCoins;
        int guide = Math.max(1, Rs2GrandExchange.getPrice(need.itemId));
        int unitPrice = Math.min(config.getContinuousMaxBuyPrice(),
                Math.max(1, (int) Math.min(Integer.MAX_VALUE, (guide * 105L + 99L) / 100L)));
        long budget = (long) unitPrice * need.quantity;
        if (!controller.mayBuy(unitPrice, need.quantity, available)) {
            controller.stop("purchase exceeds reserve, buy ceiling, or stop loss");
            return true;
        }
        int withdraw = (int) Math.min(Integer.MAX_VALUE, Math.max(0, budget - inventoryCoins));
        if (withdraw > 0) Rs2Bank.withdrawX(ItemID.COINS, withdraw);
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 2000) || !ensureExchangeOpen()) return true;
        if (!exchange.placeBuy(need.itemId, need.quantity, unitPrice,
                Rs2Inventory.itemQuantity(ItemID.COINS))) {
            controller.failPhase("GE buy dispatch failed", now);
        }
        return true;
    }

    private Purchase nextPurchase() {
        int target = config.getContinuousQuantity();
        int grimyMissing = Math.max(0, target - Rs2Bank.count(recipe.grimyHerbId));
        if (grimyMissing > 0) return new Purchase(recipe.grimyHerbId, grimyMissing);
        int vialMissing = Math.max(0, target - Rs2Bank.count(recipe.vialOfWaterId));
        if (vialMissing > 0) return new Purchase(recipe.vialOfWaterId, vialMissing);
        int secondaryNeeded = target * recipe.secondaryPerOperation;
        int secondaryMissing = Math.max(0, secondaryNeeded - Rs2Bank.count(recipe.secondaryId));
        return secondaryMissing > 0 ? new Purchase(recipe.secondaryId, secondaryMissing) : null;
    }

    private boolean processPhase(long now) {
        ContinuousHerblorePhase phase = controller.getPhase();
        if (phaseWorker == null || workerPhase != phase) {
            phaseWorker = createWorker(phase);
            workerPhase = phase;
            if (!phaseWorker.validate()) {
                controller.stop("phase worker validation failed in " + phase);
                return true;
            }
        }
        detail = phase + " " + phaseWorker.getProcessedCount() + "/" + config.getContinuousQuantity();
        if (phaseWorker.isActivelyProcessing()) return true;
        if (phaseWorker.getProcessedCount() >= config.getContinuousQuantity()) {
            if (!depositInventory()) return true;
            lastEquipmentStatus = phaseWorker.getEquipmentStatus();
            phaseWorker = null;
            workerPhase = null;
            controller.succeedPhase(now);
            return true;
        }
        if (phaseWorker.hasRequiredItems()) {
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            return phaseWorker.process();
        }
        if (!phaseWorker.canContinueProcessing()) {
            controller.failPhase("phase supply exhausted", now);
            return true;
        }
        if (!ensureBankOpen()) return true;
        if (!phaseWorker.performBanking()) controller.failPhase("phase banking failed", now);
        return true;
    }

    private HerbloreProcessor createWorker(ContinuousHerblorePhase phase) {
        Mode mode = phase == ContinuousHerblorePhase.CLEAN_HERBS ? Mode.CLEAN_HERBS
                : phase == ContinuousHerblorePhase.MAKE_UNFINISHED ? Mode.UNFINISHED_POTIONS
                : Mode.FINISHED_POTIONS;
        return new HerbloreProcessor(mode, cleanMode(recipe.herb), unfinishedMode(recipe.herb),
                recipe.potion, config.isUseAmuletOfChemistry(), config.getHerbCleaningMode(),
                config.getHerbloreTurboLimit(), config.getHerbloreSleepMin(),
                config.getHerbloreSleepMax(), config.getHerbloreSleepTarget(),
                config.getReverseIngredientChance(), config.getBatchMicroBreakChance(),
                config.getBatchMicroBreakMinMs(), config.getBatchMicroBreakMaxMs(),
                config.getContinuousQuantity());
    }

    private boolean decant(long now) {
        detail = "Decanting to four doses";
        int inventoryContainers = inventoryPotionContainers();
        if (inventoryContainers != config.getContinuousQuantity()) {
            if (inventoryContainers > 0 && !depositInventory()) return true;
            if (!withdrawFinishedAsNotes(config.getContinuousQuantity())) return true;
        }
        HerbloreDecantAdapter.Result result = decanter.decantToFourDoses(recipe.potion.toString());
        if (!result.success) {
            controller.failPhase(result.reason, now);
            return true;
        }
        expectedOutputContainers = result.after.containers;
        if (!depositInventory()) return true;
        controller.succeedPhase(now);
        return true;
    }

    private boolean sell(long now) {
        detail = "Selling reconciled output";
        if (exchange.getActiveSlot() != null) {
            if (!ensureExchangeOpen()) return true;
            exchange.reconcileAndCollect();
            return true;
        }
        int itemId = inventoryPotionId();
        if (itemId < 0) {
            if (exchange.getTotalQuantityReconciled() >= expectedOutputContainers) {
                controller.succeedPhase(now);
                return true;
            }
            if (!withdrawFinishedAsNotes(expectedOutputContainers
                    - exchange.getTotalQuantityReconciled())) return true;
            itemId = inventoryPotionId();
            if (itemId < 0) {
                controller.failPhase("no reconciled potion output to sell", now);
                return true;
            }
        }
        int quantity = Rs2Inventory.itemQuantity(itemId);
        int guide = Math.max(1, Rs2GrandExchange.getPrice(itemId));
        int unitPrice = Math.max(config.getContinuousMinSellPrice(), (guide * 95) / 100);
        if (!ensureExchangeOpen()) return true;
        if (!exchange.placeSell(itemId, quantity, unitPrice)) {
            controller.failPhase("GE sell dispatch failed", now);
        }
        return true;
    }

    private boolean reconcile(long now) {
        detail = "Reconciling cycle accounting";
        if (!ensureBankOpen()) return true;
        int produced = bankFinishedContainers() - baselineFinishedContainers;
        if (exchange.getTotalQuantityReconciled() < expectedOutputContainers) {
            controller.stop("ambiguous sale quantity accounting");
            return true;
        }
        if (bankFinishedContainers() != baselineFinishedContainers) {
            controller.stop("cycle output was not fully liquidated");
            return true;
        }
        log.info("Continuous cycle reconciled: recipe={}, quantity={}, spent={}, revenue={}, net={}",
                recipe.potion, config.getContinuousQuantity(), controller.getSpent(),
                controller.getRevenue(), controller.getNetCost());
        exchange.resetCycleQuantity();
        controller.succeedPhase(now);
        return true;
    }

    private boolean ensureBankOpen() {
        if (Rs2Bank.isOpen()) return true;
        if (!Rs2Bank.isNearBank(10)) {
            Rs2GrandExchange.walkToGrandExchange();
            return false;
        }
        Rs2Bank.openBank();
        return sleepUntil(Rs2Bank::isOpen, 3000);
    }

    private boolean ensureExchangeOpen() {
        if (Rs2GrandExchange.isOpen()) return true;
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        Rs2GrandExchange.walkToGrandExchange();
        if (!Rs2GrandExchange.openExchange()) return false;
        return sleepUntil(Rs2GrandExchange::isOpen, 3000);
    }

    private boolean depositInventory() {
        if (!ensureBankOpen()) return false;
        Rs2Bank.depositAll();
        boolean deposited = sleepUntil(Rs2Inventory::isEmpty, 3000);
        Rs2Bank.setWithdrawAsItem();
        return deposited;
    }

    private boolean withdrawFinishedAsNotes(int wanted) {
        if (!ensureBankOpen()) return false;
        Rs2Bank.depositAll();
        Rs2Bank.setWithdrawAsNote();
        int remaining = wanted;
        for (int i = recipe.finishedDoseIds.length - 1; i >= 0 && remaining > 0; i--) {
            int amount = Math.min(remaining, Rs2Bank.count(recipe.finishedDoseIds[i]));
            if (amount > 0) {
                Rs2Bank.withdrawX(recipe.finishedDoseIds[i], amount);
                remaining -= amount;
            }
        }
        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.closeBank();
        return sleepUntil(() -> inventoryPotionContainers() == wanted, 3000);
    }

    private int bankFinishedContainers() {
        int total = 0;
        for (int id : recipe.finishedDoseIds) total += Math.max(0, Rs2Bank.count(id));
        return total;
    }

    private boolean hasPotionInInventory() { return inventoryPotionId() >= 0; }

    private int inventoryPotionContainers() {
        int total = 0;
        for (int id : recipe.finishedDoseIds) total += Math.max(0, Rs2Inventory.itemQuantity(id));
        return total;
    }

    private int inventoryPotionId() {
        for (int i = recipe.finishedDoseIds.length - 1; i >= 0; i--) {
            if (Rs2Inventory.hasItem(recipe.finishedDoseIds[i])) return recipe.finishedDoseIds[i];
        }
        return -1;
    }

    private CleanHerbMode cleanMode(Herb herb) {
        return CleanHerbMode.valueOf(herb.name());
    }

    private UnfinishedPotionMode unfinishedMode(Herb herb) {
        String prefix = herb == Herb.DWARF ? "DWARF_WEED" : herb.name();
        return UnfinishedPotionMode.valueOf(prefix + "_POTION_UNF");
    }

    @Override public boolean canContinueProcessing() {
        return controller.getPhase() != ContinuousHerblorePhase.STOPPED;
    }
    @Override public boolean isActivelyProcessing() {
        return phaseWorker != null && phaseWorker.isActivelyProcessing();
    }
    @Override public String getStatusMessage() { return "Continuous: " + controller.getPhase(); }
    @Override public int getProcessedCount() {
        return controller.getCompletedCycles() * config.getContinuousQuantity()
                + (phaseWorker == null ? 0 : phaseWorker.getProcessedCount());
    }
    @Override public String getBatchProgress() {
        return phaseWorker == null ? controller.getPhase().toString() : phaseWorker.getBatchProgress();
    }
    @Override public String getEquipmentStatus() {
        return phaseWorker == null ? (config.isUseAmuletOfChemistry() ? lastEquipmentStatus : "Disabled")
                : phaseWorker.getEquipmentStatus();
    }
    @Override public String getTaskDetail() {
        if (controller.getPhase() == ContinuousHerblorePhase.STOPPED) {
            return "Stopped: " + controller.getStopReason();
        }
        return detail + " | cycle " + (controller.getCompletedCycles() + 1)
                + (config.isContinuousUnlimitedCycles() ? "/unlimited" : "/" + config.getContinuousCycleLimit())
                + " | gp " + controller.getSpent() + "/" + controller.getRevenue();
    }
    @Override public int getBankProcessableCount() {
        if (phaseWorker != null) return phaseWorker.getBankProcessableCount();
        return recipe == null ? -1 : Math.min(Rs2Bank.count(recipe.grimyHerbId),
                Math.min(Rs2Bank.count(recipe.vialOfWaterId),
                        Rs2Bank.count(recipe.secondaryId) / recipe.secondaryPerOperation));
    }
    @Override public String getBankMaterialSummary() {
        if (phaseWorker != null) return phaseWorker.getBankMaterialSummary();
        return recipe == null ? "Awaiting recipe" : "grimy=" + Rs2Bank.count(recipe.grimyHerbId)
                + ", vials=" + Rs2Bank.count(recipe.vialOfWaterId)
                + ", secondary=" + Rs2Bank.count(recipe.secondaryId);
    }
    @Override public void refreshDiagnostics() {
        if (phaseWorker != null) {
            phaseWorker.refreshDiagnostics();
            lastEquipmentStatus = phaseWorker.getEquipmentStatus();
        }
    }
    @Override public void onGameMessage(String message) { if (phaseWorker != null) phaseWorker.onGameMessage(message); }

    private static final class Purchase {
        private final int itemId;
        private final int quantity;
        private Purchase(int itemId, int quantity) { this.itemId = itemId; this.quantity = quantity; }
    }
}
