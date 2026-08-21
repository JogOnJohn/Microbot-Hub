package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** The plugin-owned, single prayer-restoration controller. */
public class PrayerPotionScript extends Script {
    private static final int DRINK_THRESHOLD_PERCENT = 30;
    private volatile boolean actionInFlight;
    private volatile long nextActionAt;

    public boolean run(NmzConfig config) {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) return true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !config.togglePrayerPotions()) return;
                if (!isInsideNmz() || !isPrayerRestoreDue() || actionInFlight || System.currentTimeMillis() < nextActionAt) return;
                List<Rs2ItemModel> potions = Microbot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getPotions).orElse(null);
                if (potions == null || potions.isEmpty()) {
                    Microbot.log("NMZ prayer: no restoring potion available");
                    return;
                }
                for (Rs2ItemModel potion : potions) {
                    if (!isPrayerRestore(potion)) continue;
                    actionInFlight = true;
                    if (!Rs2Tab.switchTo(InterfaceTab.INVENTORY)) {
                        Microbot.log("NMZ prayer: could not open inventory for " + potion.getName());
                        actionInFlight = false;
                        nextActionAt = System.currentTimeMillis() + 3000;
                        break;
                    }
                    int prayerBefore = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                    Microbot.log("NMZ prayer: drinking " + potion.getName());
                    boolean initiated = Rs2Inventory.interact(potion, "drink");
                    boolean restored = initiated && sleepUntil(
                            () -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > prayerBefore,
                            2500
                    );
                    if (restored) {
                        Rs2Inventory.dropAll("Vial");
                    } else {
                        nextActionAt = System.currentTimeMillis() + 3000;
                        Microbot.log("NMZ prayer: drink was not acknowledged; delaying retry");
                    }
                    Rs2Tab.switchTo(InterfaceTab.PRAYER);
                    actionInFlight = false;
                    break;
                }
            } catch (Exception ex) {
                actionInFlight = false;
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean isPrayerRestoreDue() {
        int realPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        return realPrayer > 0 && (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / realPrayer <= DRINK_THRESHOLD_PERCENT;
    }

    public boolean isActionInFlight() { return actionInFlight; }

    private boolean isInsideNmz() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() != null
                        && Microbot.getClient().getLocalPlayer().getWorldLocation().getY() > 4500
        ).orElse(false);
    }

    private boolean isPrayerRestore(Rs2ItemModel potion) {
        String name = potion.getName().toLowerCase();
        return name.contains("prayer potion") || name.contains("super restore") || name.contains("moonlight potion");
    }

    @Override
    public void shutdown() {
        actionInFlight = false;
        nextActionAt = 0;
        super.shutdown();
    }
}
