package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackjackTargetVisibilityTest
{
    @Test
    void recoveryZoomsOutGraduallyAndStopsAtMinimum()
    {
        assertEquals(663, BlackjackScript.zoomForHiddenTarget(736));
        assertEquals(597, BlackjackScript.zoomForHiddenTarget(663));
        assertEquals(200, BlackjackScript.zoomForHiddenTarget(210));
        assertEquals(200, BlackjackScript.zoomForHiddenTarget(200));
    }
}
