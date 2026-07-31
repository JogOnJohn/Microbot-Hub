package net.runelite.client.plugins.microbot.giantsfoundry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveCoolingPlannerTest
{
    @Test
    void waitsWhenPassiveCoolingBeatsTheWaterfallRoundTrip()
    {
        PassiveCoolingPlanner.Decision decision =
                PassiveCoolingPlanner.decide(6, 15, 14, 4, true);

        assertTrue(decision.isWait());
        assertEquals(12, decision.getPassiveWaitTicks());
        assertEquals(14, decision.getPassiveRouteTicks());
        assertEquals(17, decision.getWaterfallRouteTicks());
        assertEquals(3, decision.getSavedTicks());
    }

    @Test
    void usesWaterfallWhenPassiveCoolingWouldTakeTooLong()
    {
        PassiveCoolingPlanner.Decision decision =
                PassiveCoolingPlanner.decide(9, 15, 14, 4, true);

        assertFalse(decision.isWait());
        assertEquals(18, decision.getPassiveWaitTicks());
    }

    @Test
    void requiresAUsefulSavingBeforeWaiting()
    {
        PassiveCoolingPlanner.Decision decision =
                PassiveCoolingPlanner.decide(4, 15, 10, 12, true);

        assertFalse(decision.isWait());
        assertEquals(14, decision.getPassiveRouteTicks());
        assertEquals(15, decision.getWaterfallRouteTicks());
    }

    @Test
    void accountsForWalkingInsteadOfRunning()
    {
        PassiveCoolingPlanner.Decision decision =
                PassiveCoolingPlanner.decide(6, 15, 14, 4, false);

        assertTrue(decision.isWait());
        assertEquals(16, decision.getPassiveRouteTicks());
        assertEquals(31, decision.getWaterfallRouteTicks());
    }

    @Test
    void rejectsInvalidOrNonCoolingInputs()
    {
        assertFalse(PassiveCoolingPlanner.decide(0, 15, 14, 4, true).isWait());
        assertFalse(PassiveCoolingPlanner.decide(5, -1, 14, 4, true).isWait());
    }
}
