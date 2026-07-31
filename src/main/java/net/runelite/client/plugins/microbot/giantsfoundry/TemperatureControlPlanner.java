package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;

final class TemperatureControlPlanner
{
    private static final int RANGE_MARGIN = 12;
    private static final int TARGET_TOLERANCE = 10;
    private static final int MIN_BRAKE_DISTANCE = 75;
    private static final int BRAKE_REACTION_ALLOWANCE = 24;
    private static final int INTERACTION_DELAY_TICKS = 1;

    private TemperatureControlPlanner()
    {
    }

    @Value
    static class Plan
    {
        int safeMinimum;
        int safeMaximum;
        int desiredArrivalHeat;
        int desiredDepartureHeat;
        int predictedArrivalHeat;
        int travelTicks;
        int brakeDistance;
        boolean handoff;
        boolean downshift;
        boolean passedSafeBand;
    }

    static Plan plan(
            Stage stage,
            int[] range,
            int actionsLeft,
            int heat,
            int distanceToStage,
            boolean running,
            int observedStep,
            boolean actionHeating,
            boolean actionFast)
    {
        if (stage == null || range == null || range.length < 2)
        {
            return new Plan(0, 0, heat, heat, heat, 0, MIN_BRAKE_DISTANCE,
                    false, false, false);
        }

        int operationalMinimum = range[0] + 1 + Math.max(0, -stage.getHeatChange());
        int operationalMaximum = range[1] - 1 - Math.max(0, stage.getHeatChange());
        int targetMinimum = range[0] + RANGE_MARGIN + Math.max(0, -stage.getHeatChange());
        int targetMaximum = range[1] - RANGE_MARGIN - Math.max(0, stage.getHeatChange());
        if (targetMinimum > targetMaximum)
        {
            int midpoint = (range[0] + range[1]) / 2;
            targetMinimum = midpoint;
            targetMaximum = midpoint;
        }

        int usefulActions = Math.max(1, actionsLeft);
        int desiredArrival;
        if (stage.isCooling())
        {
            desiredArrival = Math.min(
                    targetMaximum,
                    targetMinimum + usefulActions * Math.abs(stage.getHeatChange()));
        }
        else
        {
            desiredArrival = Math.max(
                    targetMinimum,
                    targetMaximum - usefulActions * stage.getHeatChange());
        }

        int travelTicks = travelTicks(distanceToStage, running) + INTERACTION_DELAY_TICKS;
        int travelDecay = (int) Math.ceil(travelTicks / 2d);
        int actionMomentum = Math.max(0, observedStep) * INTERACTION_DELAY_TICKS;
        int desiredDeparture = clamp(desiredArrival
                + travelDecay
                + (actionHeating ? -actionMomentum : actionMomentum));
        int predictedArrival = clamp(heat
                - travelDecay
                + (actionHeating ? actionMomentum : -actionMomentum));
        int projectedNextArrival = clamp(predictedArrival
                + (actionHeating ? Math.max(0, observedStep) : -Math.max(0, observedStep)));
        boolean reachedTarget = actionHeating
                ? predictedArrival >= desiredArrival - TARGET_TOLERANCE
                    || projectedNextArrival >= desiredArrival
                : predictedArrival <= desiredArrival + TARGET_TOLERANCE
                    || projectedNextArrival <= desiredArrival;
        boolean currentSafe = heat >= operationalMinimum && heat <= operationalMaximum;
        boolean arrivalSafe = predictedArrival >= operationalMinimum
                && predictedArrival <= operationalMaximum;
        boolean predictedPassed = actionHeating
                ? predictedArrival > operationalMaximum
                : predictedArrival < operationalMinimum;
        boolean emergencyHandoff = currentSafe && predictedPassed;
        boolean handoff = (!actionFast && reachedTarget && arrivalSafe) || emergencyHandoff;

        int brakeDistance = Math.max(
                MIN_BRAKE_DISTANCE,
                Math.max(0, observedStep) * 3 + BRAKE_REACTION_ALLOWANCE);
        boolean downshift = actionFast
                && observedStep > 0
                && !handoff
                && Math.abs(desiredDeparture - heat) <= brakeDistance;
        boolean passedSafeBand = actionHeating
                ? heat > operationalMaximum
                : heat < operationalMinimum;

        return new Plan(
                operationalMinimum,
                operationalMaximum,
                desiredArrival,
                desiredDeparture,
                predictedArrival,
                travelTicks,
                brakeDistance,
                handoff,
                downshift,
                passedSafeBand);
    }

    private static int travelTicks(int distance, boolean running)
    {
        int boundedDistance = Math.max(0, distance);
        return running
                ? (int) Math.ceil(boundedDistance / 2d)
                : boundedDistance;
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(1000, value));
    }
}
