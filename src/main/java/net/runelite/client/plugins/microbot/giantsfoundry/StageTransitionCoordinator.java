package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;

final class StageTransitionCoordinator
{
    @Value
    static class Transition
    {
        long generation;
        Stage previousStage;
        Stage nextStage;
        int progress;
    }

    private long generation;
    private int observedProgress = -1;
    private Stage observedStage;
    private Transition pending;

    synchronized void publishProgress(int progress)
    {
        if (observedProgress != progress)
        {
            observedProgress = progress;
            generation++;
        }
        if (progress <= 0)
        {
            observedStage = null;
            pending = null;
        }
    }

    synchronized Transition observe(int progress, Stage stage)
    {
        publishProgress(progress);
        if (progress <= 0 || stage == null)
        {
            observedStage = null;
            pending = null;
            return null;
        }
        if (observedStage == null)
        {
            observedStage = stage;
            return null;
        }
        if (observedStage == stage)
        {
            return null;
        }

        Stage previous = observedStage;
        observedStage = stage;
        pending = new Transition(++generation, previous, stage, progress);
        return pending;
    }

    synchronized Transition getPending(long expectedGeneration)
    {
        return pending != null && pending.getGeneration() == expectedGeneration ? pending : null;
    }

    synchronized void acknowledge(long expectedGeneration)
    {
        if (pending != null && pending.getGeneration() == expectedGeneration)
        {
            pending = null;
        }
    }

    synchronized boolean isCurrent(long expectedGeneration)
    {
        return generation == expectedGeneration;
    }

    synchronized long getGeneration()
    {
        return generation;
    }

    synchronized void reset()
    {
        generation++;
        observedProgress = -1;
        observedStage = null;
        pending = null;
    }
}
