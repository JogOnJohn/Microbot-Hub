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
        assertEquals(-11, GiantsFoundryState.calculateHeatChangeNeeded(
                Stage.GRINDSTONE, 626, new int[]{359, 640}));
    }

    @Test
    void requestsHeatingBeforeHammeringWouldCrossTheHeatBand()
    {
        assertEquals(7, GiantsFoundryState.calculateHeatChangeNeeded(
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
    void countsWorkstationActionsAvailableWithinTheBand()
    {
        assertEquals(9, GiantsFoundryState.countActionsAvailable(
                900, new int[]{692, 974}, Stage.TRIP_HAMMER));
        assertEquals(2, GiantsFoundryState.countActionsAvailable(
                620, new int[]{359, 640}, Stage.GRINDSTONE));
        assertEquals(0, GiantsFoundryState.countActionsAvailable(
                692, new int[]{692, 974}, Stage.TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.countActionsAvailable(
                500, null, Stage.TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.countActionsAvailable(
                500, new int[]{359, 640}, null));
    }
}
