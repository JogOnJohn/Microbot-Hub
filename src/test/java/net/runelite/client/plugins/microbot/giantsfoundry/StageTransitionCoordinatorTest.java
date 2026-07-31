package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTransitionCoordinatorTest
{
    @Test
    void invalidatesSnapshotsWhenAStageChanges()
    {
        StageTransitionCoordinator coordinator = new StageTransitionCoordinator();
        assertNull(coordinator.observe(20, Stage.TRIP_HAMMER));
        long oldGeneration = coordinator.getGeneration();

        StageTransitionCoordinator.Transition transition =
                coordinator.observe(200, Stage.GRINDSTONE);

        assertEquals(Stage.TRIP_HAMMER, transition.getPreviousStage());
        assertEquals(Stage.GRINDSTONE, transition.getNextStage());
        assertFalse(coordinator.isCurrent(oldGeneration));
        assertTrue(coordinator.isCurrent(transition.getGeneration()));
        assertEquals(transition, coordinator.getPending(transition.getGeneration()));
    }

    @Test
    void acknowledgementCannotClearANewerTransition()
    {
        StageTransitionCoordinator coordinator = new StageTransitionCoordinator();
        coordinator.observe(20, Stage.TRIP_HAMMER);
        StageTransitionCoordinator.Transition first =
                coordinator.observe(200, Stage.GRINDSTONE);
        StageTransitionCoordinator.Transition second =
                coordinator.observe(400, Stage.POLISHING_WHEEL);

        coordinator.acknowledge(first.getGeneration());

        assertEquals(second, coordinator.getPending(second.getGeneration()));
    }

    @Test
    void progressResetInvalidatesPendingWork()
    {
        StageTransitionCoordinator coordinator = new StageTransitionCoordinator();
        coordinator.observe(20, Stage.TRIP_HAMMER);
        StageTransitionCoordinator.Transition transition =
                coordinator.observe(200, Stage.GRINDSTONE);

        coordinator.observe(0, null);

        assertFalse(coordinator.isCurrent(transition.getGeneration()));
        assertNull(coordinator.getPending(transition.getGeneration()));
    }

    @Test
    void progressEventsInvalidateWorkWithoutGuessingTheStage()
    {
        StageTransitionCoordinator coordinator = new StageTransitionCoordinator();
        coordinator.observe(20, Stage.TRIP_HAMMER);
        long oldGeneration = coordinator.getGeneration();

        coordinator.publishProgress(40);

        assertFalse(coordinator.isCurrent(oldGeneration));
        assertNull(coordinator.getPending(coordinator.getGeneration()));
        assertNull(coordinator.observe(40, Stage.TRIP_HAMMER));
    }
}
