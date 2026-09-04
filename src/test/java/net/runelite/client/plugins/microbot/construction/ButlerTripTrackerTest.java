package net.runelite.client.plugins.microbot.construction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButlerTripTrackerTest {
    private final ButlerTripTracker tracker = new ButlerTripTracker();

    @Test
    void suppressesCallsUntilButlerReturns() {
        tracker.dispatched(1_000L);
        assertTrue(tracker.isTripInProgress());
        assertEquals(ButlerTripTracker.Action.WAIT, tracker.observe(true, true, 1_500L));
        assertEquals(ButlerTripTracker.Action.WAIT, tracker.observe(false, false, 2_000L));
        assertEquals(ButlerTripTracker.Action.WAIT, tracker.observe(false, false, 3_000L));
        assertEquals(ButlerTripTracker.Action.TALK_TO_RETURNED_BUTLER,
                tracker.observe(false, true, 4_000L));
        assertTrue(tracker.isTripInProgress());
        tracker.reset();
        assertFalse(tracker.isTripInProgress());
    }

    @Test
    void recognizesReturnDialogue() {
        tracker.dispatched(1_000L);
        tracker.observe(false, false, 2_000L);
        assertEquals(ButlerTripTracker.Action.HANDLE_RETURN_DIALOGUE,
                tracker.observe(true, true, 3_000L));
        assertTrue(tracker.isTripInProgress());
    }

    @Test
    void suppressesDuplicateCallsAsSoonAsServantWidgetIsClicked() {
        tracker.servantRequested(1_000L);
        assertTrue(tracker.isTripInProgress());
        assertEquals(ButlerTripTracker.Action.WAIT, tracker.observe(false, false, 2_000L));
        assertEquals(ButlerTripTracker.Action.HANDLE_RETURN_DIALOGUE,
                tracker.observe(true, true, 3_000L));
        assertTrue(tracker.isTripInProgress());
    }

    @Test
    void clicksCurrentTileInsteadOfRedispatchingWhenServantCannotReturn() {
        tracker.dispatched(1_000L);
        assertEquals(ButlerTripTracker.Action.CLICK_CURRENT_TILE,
                tracker.observe(false, true, 1_000L + ButlerTripTracker.BLOCKED_RETURN_GRACE_MS));
        assertTrue(tracker.isTripInProgress());
        assertEquals(ButlerTripTracker.Action.WAIT,
                tracker.observe(false, false, 20_000L));
    }

    @Test
    void requestsOnlyOneTileClickWhileWaitingForReturn() {
        tracker.dispatched(1_000L);
        tracker.observe(false, false, 2_000L);
        assertEquals(ButlerTripTracker.Action.CLICK_CURRENT_TILE,
                tracker.observe(false, false, 1_000L + ButlerTripTracker.BLOCKED_RETURN_GRACE_MS));
        assertEquals(ButlerTripTracker.Action.WAIT,
                tracker.observe(false, false, 60_000L));
        assertTrue(tracker.isTripInProgress());
    }
}
