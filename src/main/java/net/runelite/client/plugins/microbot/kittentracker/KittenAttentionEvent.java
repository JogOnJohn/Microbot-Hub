package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import javax.inject.Inject;

public class KittenAttentionEvent implements BlockingEvent
{
    private final KittenPlugin kittenPlugin;
    @Inject
    public KittenAttentionEvent(KittenPlugin kittenPlugin)
    {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate()
    {
        if (kittenPlugin.isKittenInteractionOnBackoff()) {
            return false;
        }
        long timeBeforeAttention = kittenPlugin.getTimeBeforeNeedingAttention();
        boolean valid = (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= timeBeforeAttention && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());
        if (valid) {
            Microbot.log("[KittenTracker] Attention event ready; timeBeforeAttentionMs=" + timeBeforeAttention);
        }
        return valid;
    }

    @Override
    public boolean execute()
    {
        long start = System.currentTimeMillis();
        Microbot.log("[KittenTracker] Attention event executing");
        NPC kitten = kittenPlugin.getKittenFollower();
        if (kitten == null) {
            Microbot.log("[KittenTracker] Kitten follower not found for attention; calling follower before retry");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Attention fallback call follower result=" + callArrived
                    + ", elapsedMs="
                    + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
        }
        if (kitten == null) {
            kittenPlugin.startKittenInteractionBackoff("kitten follower not found for attention");
            return true;
        }
        boolean reachable = kittenPlugin.isKittenReachable(kitten);
        if (!reachable) {
            Microbot.log("[KittenTracker] Kitten not reachable for attention; calling follower before interaction");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Attention reachability call follower result=" + callArrived
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
            reachable = kitten != null && kittenPlugin.isKittenReachable(kitten);
            Microbot.log("[KittenTracker] Attention reachable after call=" + reachable
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
        }
        if (kitten == null || !reachable) {
            kittenPlugin.startKittenInteractionBackoff("kitten not reachable for attention after follower call");
            return true;
        }

        // The menu click on an NPC "succeeds" even when the kitten can't actually be walked to —
        // the Stroke dialogue appearing is the only real proof the interaction landed. Treat a
        // missing dialogue as a failed attempt: one verified follower-call retry, then back off.
        boolean stroked = interactAndStroke(kitten, start);
        if (!stroked) {
            Microbot.log("[KittenTracker] Stroke dialogue never appeared; calling follower and retrying once");
            boolean callArrived = kittenPlugin.callFollowerToPlayer();
            Microbot.log("[KittenTracker] Attention retry call follower result=" + callArrived
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            kitten = kittenPlugin.getKittenFollower();
            stroked = kitten != null && interactAndStroke(kitten, start);
        }
        if (!stroked) {
            kittenPlugin.startKittenInteractionBackoff("stroke interaction never produced the dialogue");
            return true;
        }

        Microbot.log("[KittenTracker] Waiting for attention timer to update");
        boolean timerUpdated = Global.sleepUntil(() -> (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeNeedingAttention(),10000);
        Microbot.log("[KittenTracker] Attention event complete; timerUpdated=" + timerUpdated
                + ", timeBeforeAttentionMs=" + kittenPlugin.getTimeBeforeNeedingAttention()
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        return true;
    }

    /**
     * Click Interact and drive the Stroke dialogue. Only returns true when the dialogue actually
     * appeared and Stroke was clicked — the ground truth that the kitten was really interacted with.
     */
    private boolean interactAndStroke(NPC kitten, long start)
    {
        Microbot.log("[KittenTracker] Interacting with kitten for attention");
        boolean clicked = new Rs2NpcModel(kitten).click("Interact");
        Microbot.log("[KittenTracker] Attention interaction result=" + clicked
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        if (!clicked) {
            return false;
        }
        boolean hasStrokeOption = Rs2Dialogue.sleepUntilHasDialogueOption("Stroke");
        Microbot.log("[KittenTracker] Stroke dialogue option present=" + hasStrokeOption
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        if (!hasStrokeOption) {
            return false;
        }
        boolean clickedStroke = Rs2Dialogue.clickOption("Stroke");
        Microbot.log("[KittenTracker] Stroke dialogue click result=" + clickedStroke
                + ", elapsedMs=" + (System.currentTimeMillis() - start));
        return clickedStroke;
    }

    @Override
    public BlockingEventPriority priority()
    {
        return BlockingEventPriority.NORMAL;
    }
}
