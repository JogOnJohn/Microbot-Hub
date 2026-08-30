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
        assertFalse(tracker.isTripInProgress());
    }

    @Test
    void recognizesReturnDialogue() {
        tracker.dispatched(1_000L);
        tracker.observe(false, false, 2_000L);
        assertEquals(ButlerTripTracker.Action.HANDLE_RETURN_DIALOGUE,
                tracker.observe(true, true, 3_000L));
    }

    @Test
    void retriesWhenDispatchNeverCompletes() {
        tracker.dispatched(1_000L);
        assertEquals(ButlerTripTracker.Action.RETRY_DISPATCH,
                tracker.observe(false, true, 1_000L + ButlerTripTracker.DEPARTURE_TIMEOUT_MS));
        assertFalse(tracker.isTripInProgress());
    }

    @Test
    void retriesWhenReturnIsMissedForTooLong() {
        tracker.dispatched(1_000L);
        tracker.observe(false, false, 2_000L);
        assertEquals(ButlerTripTracker.Action.RETRY_DISPATCH,
                tracker.observe(false, false, 2_000L + ButlerTripTracker.RETURN_TIMEOUT_MS));
    }
}
