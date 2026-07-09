// filepath: c:\Users\marcu\IdeaProjects\microbot\runelite-client\src\main\java\net\runelite\client\plugins\microbot\kittentracker\KittenScript.java
package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;

public class KittenScript extends Script {

    private KittenPlugin kittenPlugin;

    public boolean run(KittenConfig config, KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) {
                }



            }
            catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleKittenNeeds(KittenConfig config) {
        if (config.kittenHungryOverlay() 
            && kittenPlugin.getAvailableKittenFoodId() != -1
            && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry()) {
            feedKitten();
        }
        if (config.kittenAttentionOverlay() 
            && Rs2Inventory.contains(ItemID.BALL_OF_WOOL)
            && (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeNeedingAttention()) {
            giveKittenAttention();
        }
    }

    private void feedKitten() {
        NPC kitten = kittenPlugin.getKittenFollower();
        int foodId = kittenPlugin.getAvailableKittenFoodId();
        if (kitten != null && foodId != -1) {
            Rs2Inventory.useItemOnNpc(foodId, kitten);
        }
        sleep(1000, 2000);
    }

    private void giveKittenAttention() {
        NPC kitten = kittenPlugin.getKittenFollower();
        if (kitten != null) {
            Rs2Inventory.useItemOnNpc(ItemID.BALL_OF_WOOL, kitten);
        }
        sleep(1000, 2000);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
