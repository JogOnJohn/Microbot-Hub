package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GiantsFoundryStateTest
{
    @Test
    void totalsEveryCrucibleMetal()
    {
        assertEquals(28, GiantsFoundryState.totalOreCount(0, 14, 14, 0, 0, 0));
        assertEquals(28, GiantsFoundryState.totalOreCount(0, 0, 0, 18, 10, 0));
    }

    @Test
    void ignoresNegativeUnavailableValues()
    {
        assertEquals(14, GiantsFoundryState.totalOreCount(-1, 14, 0, 0, 0, 0));
    }

    @Test
    void requestsCoolingBeforeGrindingWouldCrossTheHeatBand()
    {
        assertEquals(-6, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.GRINDSTONE, 626, new int[]{359, 640}));
    }

    @Test
    void requestsHeatingBeforeHammeringWouldCrossTheHeatBand()
    {
        assertEquals(2, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.TRIP_HAMMER, 720, new int[]{692, 974}));
    }

    @Test
    void leavesComfortablyInRangeHeatAlone()
    {
        assertEquals(0, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.POLISHING_WHEEL, 150, new int[]{25, 307}));
    }

    @Test
    void heatsWhenCompletelyBelowTheRequiredBand()
    {
        assertEquals(359, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.GRINDSTONE, 0, new int[]{359, 640}));
    }

    @Test
    void coolsWhenCompletelyAboveTheRequiredBand()
    {
        assertEquals(-7, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.TRIP_HAMMER, 981, new int[]{692, 974}));
    }

    @Test
    void countsToolActionsAvailableWithinTheBand()
    {
        // hammer cools 25 per hit from 900: 900, 875, 850, ... stays above 692 for 9 hits
        assertEquals(9, GiantsFoundryState.countActionsAvailable(900, new int[]{692, 974}, Stage.TRIP_HAMMER));
        // grindstone heats 15 per hit from 620: 620 and 635 are in range, 650 is not
        assertEquals(2, GiantsFoundryState.countActionsAvailable(620, new int[]{359, 640}, Stage.GRINDSTONE));
        // out of band means no actions
        assertEquals(0, GiantsFoundryState.countActionsAvailable(692, new int[]{692, 974}, Stage.TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.countActionsAvailable(500, null, Stage.TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.countActionsAvailable(500, new int[]{359, 640}, null));
    }
}
