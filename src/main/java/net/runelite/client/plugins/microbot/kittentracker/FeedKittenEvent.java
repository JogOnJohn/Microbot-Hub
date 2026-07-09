package net.runelite.client.plugins.microbot.kittentracker;


import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Inject;


public class FeedKittenEvent implements BlockingEvent {
    private final KittenPlugin kittenPlugin;
    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate() {
        if (kittenPlugin.isKittenInteractionOnBackoff()) {
            return false;
        }
        int foodId = kittenPlugin.getAvailableKittenFoodId();
        long timeBeforeHungry = kittenPlugin.getTimeBeforeHungry();
        boolean valid = foodId != -1
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= timeBeforeHungry && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());
        if (valid) {
            Microbot.log("[KittenTracker] Feed event ready; food=" + kittenPlugin.describeKittenFood(foodId)
                    + ", foodId=" + foodId + ", timeBeforeHungryMs=" + timeBeforeHungry);
        }
        return valid;

    }

    @Override
    public boolean execute() {
        long start = System.currentTimeMillis();
        try {
            return doExecute(start);
        } catch (Exception ex) {
            // An exception escaping execute() skips every backoff path, and the blocking-event
            // manager re-validates immediately — a tight error loop firing several times a second
            // (seen live with "must be called on client thread"). Convert any unexpected failure
            // into the normal backoff so the loop can never happen again.
            Microbot.log("[KittenTracker] Feed event error: " + ex);
            kittenPlugin.startKittenInteractionBackoff("unexpected feed error: " + ex.getMessage());
            return true;
        }
    }

    private boolean doExecute(long start) {
        Microbot.log("[KittenTracker] Feed event executing");
        NPC kitten = kittenPlugin.getKittenFollower();
        int foodId = kittenPlugin.getAvailableKittenFoodId();
        if (kitten == null) {
            Microbot.log("[KittenTracker] Kitten follower not found for feed; calling follower before retry");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Feed fallback call follower result=" + callArrived
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
        }
        if (kitten == null || foodId == -1) {
            kittenPlugin.startKittenInteractionBackoff("kitten follower not found for feed (foodId=" + foodId + ")");
            return true;
        }
        boolean reachable = kittenPlugin.isKittenReachable(kitten);
        if (!reachable) {
            Microbot.log("[KittenTracker] Kitten not reachable for feed; calling follower before interaction");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Feed reachability call follower result=" + callArrived
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
            reachable = kitten != null && kittenPlugin.isKittenReachable(kitten);
            Microbot.log("[KittenTracker] Feed reachable after call=" + reachable
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
        }
        if (kitten == null || !reachable) {
            kittenPlugin.startKittenInteractionBackoff("kitten not reachable for feed after follower call");
            return true;
        }

        Microbot.log("[KittenTracker] Feeding kitten; food=" + kittenPlugin.describeKittenFood(foodId)
                + ", foodId=" + foodId);
        boolean clicked = Rs2Inventory.useItemOnNpc(foodId, kitten);
        Microbot.log("[KittenTracker] Feed interaction result=" + clicked
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        if (!clicked) {
            Microbot.log("[KittenTracker] Feed interaction failed; calling follower and retrying once");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Feed retry call follower result=" + callArrived
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
            if (kitten != null) {
                foodId = kittenPlugin.getAvailableKittenFoodId();
                if (foodId != -1) {
                    clicked = Rs2Inventory.useItemOnNpc(foodId, kitten);
                    Microbot.log("[KittenTracker] Feed retry interaction result=" + clicked
                            + ", food=" + kittenPlugin.describeKittenFood(foodId)
                            + ", foodId=" + foodId
                            + ", elapsedMs=" + (System.currentTimeMillis() - start));
                }
            }
        }
        if (!clicked) {
            kittenPlugin.startKittenInteractionBackoff("could not feed kitten after retry");
            return true;
        }

        // The use-item click "succeeds" even when the kitten can't be walked to — the hunger timer
        // resetting is the real proof the feed landed. Give it long enough for the walk + eat, and
        // back off when it never resets so the event doesn't re-fire dead clicks every cycle.
        Microbot.log("[KittenTracker] Waiting for hunger timer to update after feed");
        boolean timerUpdated = Global.sleepUntil(() -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry(), 8000);
        Microbot.log("[KittenTracker] Feed event complete; timerUpdated=" + timerUpdated
                + ", timeBeforeHungryMs=" + kittenPlugin.getTimeBeforeHungry()
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        if (!timerUpdated) {
            kittenPlugin.startKittenInteractionBackoff("hunger timer never reset after feed");
        }
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
