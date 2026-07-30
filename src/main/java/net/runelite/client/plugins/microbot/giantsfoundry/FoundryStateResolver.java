package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;

final class FoundryStateResolver
{
    private FoundryStateResolver()
    {
    }

    static State calculate(Facts facts)
    {
        if (facts.hasPreform && facts.preformQuality <= 0)
        {
            return State.HANDING_IN;
        }
        if (facts.progress >= 1000)
        {
            return State.HANDING_IN;
        }
        if (facts.hasPreform)
        {
            if (!facts.hasStage)
            {
                return State.WAITING;
            }
            if (facts.heatChange > 0)
            {
                return State.HEATING;
            }
            if (facts.heatChange < 0)
            {
                return State.COOLING_DOWN;
            }
            return State.CRAFTING_WEAPON;
        }
        if (facts.canPickupPreform)
        {
            return State.PICKING_UP_PREFORM;
        }
        if (facts.canPour)
        {
            return State.POURING;
        }
        if (!facts.hasCommission)
        {
            return State.GETTING_COMMISSION;
        }
        if (!facts.hasSelectedMould)
        {
            return State.SELECTING_MOULD;
        }
        return State.FILLING_CRUCIBLE;
    }

    @Value
    static class Facts
    {
        int progress;
        boolean hasPreform;
        boolean canPickupPreform;
        boolean canPour;
        boolean hasCommission;
        boolean hasSelectedMould;
        boolean hasStage;
        int heatChange;
        int preformQuality;
    }
}
