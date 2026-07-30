package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoundryStateResolverTest
{
    @Test
    void resolvesSetupStatesInObservedPriorityOrder()
    {
        assertEquals(State.GETTING_COMMISSION, calculate(0, false, false, false, false, false, false, 0));
        assertEquals(State.SELECTING_MOULD, calculate(0, false, false, false, true, false, false, 0));
        assertEquals(State.FILLING_CRUCIBLE, calculate(0, false, false, false, true, true, false, 0));
        assertEquals(State.POURING, calculate(0, false, false, true, true, true, false, 0));
        assertEquals(State.PICKING_UP_PREFORM, calculate(0, false, true, false, true, true, false, 0));
    }

    @Test
    void resolvesRefinementFromCurrentHeatRequirement()
    {
        assertEquals(State.WAITING, calculate(100, true, false, false, true, true, false, 0));
        assertEquals(State.HEATING, calculate(100, true, false, false, true, true, true, 25));
        assertEquals(State.COOLING_DOWN, calculate(100, true, false, false, true, true, true, -25));
        assertEquals(State.CRAFTING_WEAPON, calculate(100, true, false, false, true, true, true, 0));
    }

    @Test
    void completedSwordAlwaysHandsIn()
    {
        assertEquals(State.HANDING_IN, calculate(1000, true, true, true, false, false, true, 100, 50));
    }

    @Test
    void damagedPreformIsHandedInInsteadOfRetryingAStation()
    {
        assertEquals(State.HANDING_IN, calculate(250, true, false, false, true, true, true, 0, 0));
    }

    private static State calculate(
            int progress,
            boolean hasPreform,
            boolean canPickup,
            boolean canPour,
            boolean hasCommission,
            boolean hasMould,
            boolean hasStage,
            int heatChange)
    {
        return calculate(progress, hasPreform, canPickup, canPour, hasCommission, hasMould, hasStage, heatChange, 50);
    }

    private static State calculate(
            int progress,
            boolean hasPreform,
            boolean canPickup,
            boolean canPour,
            boolean hasCommission,
            boolean hasMould,
            boolean hasStage,
            int heatChange,
            int quality)
    {
        return FoundryStateResolver.calculate(new FoundryStateResolver.Facts(
                progress, hasPreform, canPickup, canPour, hasCommission, hasMould, hasStage, heatChange, quality));
    }
}
