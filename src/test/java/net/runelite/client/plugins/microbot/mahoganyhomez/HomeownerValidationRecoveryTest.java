package net.runelite.client.plugins.microbot.mahoganyhomez;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.mahoganyhomez.HomeownerValidationRecovery.Decision.*;

class HomeownerValidationRecoveryTest
{
    @Test void aTransientMissDoesNotFailOrForceAnotherWalk()
    {
        var guard = new HomeownerValidationRecovery();
        assertEquals(WAIT, guard.check(false, 100));
        assertEquals(WAIT, guard.check(false, 101));
        assertEquals(READY, guard.check(true, 102));
    }

    @Test void blockedOrMissingNpcRequiresBoundedApproachNotValidation()
    {
        var guard = new HomeownerValidationRecovery();
        assertEquals(WAIT, guard.check(false, 100));
        assertEquals(APPROACH, guard.check(false, 102));
        assertEquals(WAIT, guard.check(false, 103));
        assertEquals(APPROACH, guard.check(false, 108));
        assertEquals(APPROACH, guard.check(false, 129));
        assertEquals(EXPIRED, guard.check(false, 130));
        assertEquals(EXPIRED, guard.check(false, 140));
    }

    @Test void successAndNewAssignmentResetTheDeadline()
    {
        var guard = new HomeownerValidationRecovery();
        guard.check(false, 100);
        assertEquals(READY, guard.check(true, 129));
        assertEquals(WAIT, guard.check(false, 140));
        guard.reset();
        assertEquals(WAIT, guard.check(false, 200));
        assertEquals(EXPIRED, guard.check(false, 230));
    }
}
