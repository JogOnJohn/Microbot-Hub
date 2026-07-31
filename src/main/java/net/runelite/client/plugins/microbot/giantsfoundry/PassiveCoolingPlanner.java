package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;

final class PassiveCoolingPlanner
{
    static final int PASSIVE_DECAY_TICKS_PER_HEAT = 2;
    static final int WATERFALL_ACTION_TICKS = 2;
    static final int MINIMUM_SAVINGS_TICKS = 2;
    static final int MAXIMUM_WAIT_TICKS = 16;

    private PassiveCoolingPlanner()
    {
    }

    static Decision decide(
            int heatChange,
            int distanceToWaterfall,
            int waterfallToStageDistance,
            int distanceToStage,
            boolean running)
    {
        if (heatChange <= 0
                || distanceToWaterfall < 0
                || waterfallToStageDistance < 0
                || distanceToStage < 0)
        {
            return Decision.doNotWait();
        }

        int passiveWaitTicks = heatChange * PASSIVE_DECAY_TICKS_PER_HEAT;
        int directTravelTicks = travelTicks(distanceToStage, running);
        int waterfallTravelTicks = travelTicks(distanceToWaterfall, running)
                + WATERFALL_ACTION_TICKS
                + travelTicks(waterfallToStageDistance, running);
        int passiveRouteTicks = passiveWaitTicks + directTravelTicks;
        int savedTicks = waterfallTravelTicks - passiveRouteTicks;
        boolean shouldWait = passiveWaitTicks <= MAXIMUM_WAIT_TICKS
                && savedTicks >= MINIMUM_SAVINGS_TICKS;

        return Decision.of(
                shouldWait,
                passiveWaitTicks,
                passiveRouteTicks,
                waterfallTravelTicks,
                savedTicks);
    }

    private static int travelTicks(int distance, boolean running)
    {
        return running ? (int) Math.ceil(distance / 2d) : distance;
    }

    @Value(staticConstructor = "of")
    static class Decision
    {
        boolean wait;
        int passiveWaitTicks;
        int passiveRouteTicks;
        int waterfallRouteTicks;
        int savedTicks;

        static Decision doNotWait()
        {
            return Decision.of(false, 0, 0, 0, 0);
        }
    }
}
