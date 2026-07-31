package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperatureControlPlannerTest
{
    @Test
    void aimsHighForCoolingWorkstations()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.TRIP_HAMMER,
                new int[]{708, 957},
                10,
                949,
                14,
                true,
                15,
                true,
                false);

        assertEquals(945, plan.getDesiredArrivalHeat());
        assertEquals(934, plan.getDesiredDepartureHeat());
        assertTrue(plan.isHandoff());
    }

    @Test
    void aimsLowForTheHeatingWorkstation()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.GRINDSTONE,
                new int[]{375, 624},
                20,
                390,
                7,
                true,
                8,
                true,
                false);

        assertEquals(387, plan.getDesiredArrivalHeat());
        assertEquals(382, plan.getDesiredDepartureHeat());
        assertTrue(plan.isHandoff());
    }

    @Test
    void downshiftsFastActionsBeforeTheTarget()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.TRIP_HAMMER,
                new int[]{708, 957},
                10,
                830,
                14,
                true,
                32,
                true,
                true);

        assertFalse(plan.isHandoff());
        assertTrue(plan.isDownshift());
        assertEquals(120, plan.getBrakeDistance());
    }

    @Test
    void detectsAnUnsafePassedTarget()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.POLISHING_WHEEL,
                new int[]{42, 291},
                15,
                20,
                10,
                true,
                40,
                false,
                false);

        assertTrue(plan.isPassedSafeBand());
        assertFalse(plan.isHandoff());
    }

    @Test
    void handsOffAtAUsableBoundaryInsteadOfReversing()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.GRINDSTONE,
                new int[]{375, 624},
                20,
                381,
                4,
                true,
                15,
                false,
                false);

        assertTrue(plan.isHandoff());
        assertFalse(plan.isPassedSafeBand());
        assertEquals(364, plan.getPredictedArrivalHeat());
    }

    @Test
    void handsOffBeforeTheNextAcceleratedTickWouldOvershoot()
    {
        TemperatureControlPlanner.Plan plan = TemperatureControlPlanner.plan(
                Stage.POLISHING_WHEEL,
                new int[]{42, 291},
                15,
                250,
                4,
                true,
                40,
                true,
                false);

        assertTrue(plan.isHandoff());
        assertFalse(plan.isPassedSafeBand());
        assertEquals(288, plan.getPredictedArrivalHeat());
    }
}
