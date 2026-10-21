package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.mahoganyhomez.TransitionSnapshotGuard.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesTransitionTest
{
	@Test void finalFurnitureEffectMustReleaseItsInteractionBeforeHandIn()
	{
		assertFalse(MahoganyHomesScript.workReadyToFinish(true, true));
		assertFalse(MahoganyHomesScript.workReadyToFinish(false, false));
		assertTrue(MahoganyHomesScript.workReadyToFinish(true, false));
	}

	@Test void handInCannotAdvanceBeforeReleasingPendingInteraction()
	{
		assertFalse(MahoganyHomesScript.turnInReadyToFinish(true, false, true));
		assertFalse(MahoganyHomesScript.turnInReadyToFinish(true, true, false));
		assertFalse(MahoganyHomesScript.turnInReadyToFinish(false, false, false));
		assertTrue(MahoganyHomesScript.turnInReadyToFinish(true, false, false));
	}

	private final WorldPoint position = new WorldPoint(3000, 3300, 0);

	@Test void nullPlayerAfterPoolWaitsWithoutDeclaringArrival()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, null, 100));
		assertFalse(guard.recovered());
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 700));
		assertTrue(guard.recovered());
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 1300));
		assertFalse(guard.recovered(), "An unchanged valid position must not renew progress");
	}

	@Test void loadingWithStalePositionCannotArriveOrInteract()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		assertEquals(WAIT, guard.check(GameState.LOADING, position, 100));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 700));
		assertTrue(guard.recovered());
	}

	@Test void persistentNullAndLoadingHaveFiniteAllowance()
	{
		for (GameState state : new GameState[]{GameState.LOGGED_IN, GameState.LOADING})
		{
			TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
			assertEquals(WAIT, guard.check(state, null, 0));
			assertEquals(WAIT, guard.check(state, null, 14_999));
			assertEquals(UNAVAILABLE, guard.check(state, null, 15_000));
			assertEquals(UNAVAILABLE, guard.check(GameState.LOGGED_IN, position, 15_001));
		}
	}

	@Test void intermittentSnapshotsCannotRenewTheBudget()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		assertEquals(WAIT, guard.check(GameState.LOADING, null, 0));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 10_000));
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, null, 20_000));
		assertEquals(UNAVAILABLE, guard.check(GameState.LOGGED_IN, position, 25_000));
	}

	@Test void logoutConnectionLossAndUnknownStateAreNotTransientLoading()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.CONNECTION_LOST,
			GameState.HOPPING, null})
		{
			assertEquals(UNAVAILABLE, new TransitionSnapshotGuard().check(state, position, 0));
		}
	}

	@Test void blockedMovingAndUnrequestedRoutesCannotArrive()
	{
		assertFalse(MahoganyHomesScript.hasTravelArrived(true, "BLOCKED"));
		assertFalse(MahoganyHomesScript.hasTravelArrived(true, "MOVING"));
		assertFalse(MahoganyHomesScript.hasTravelArrived(false, "ARRIVED"));
		assertTrue(MahoganyHomesScript.hasTravelArrived(true, "ARRIVED"));
	}

	@Test void scheduledBreakDoesNotConsumeTheSceneWaitBudget()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		assertEquals(WAIT, guard.check(GameState.LOADING, null, 0, 100));
		guard.suspend();
		long afterBreak = 26 * 60 * 1000;
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, afterBreak, 101));
		assertFalse(guard.sessionRecovered());
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, afterBreak + 600, 102));
		assertTrue(guard.sessionRecovered());
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, afterBreak + 1200, 103));
		assertFalse(guard.sessionRecovered(), "Reset owned execution only once per pause");
	}

	@Test void repeatedReadsOfOneTickCannotFinishLoginRecovery()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		guard.suspend();
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 0, 5));
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 100, 5));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 600, 6));
	}

	@Test void loadingBetweenResumeSamplesRequiresAnotherStablePair()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		guard.suspend();
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 0, 5));
		assertEquals(WAIT, guard.check(GameState.LOADING, null, 600, 6));
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 1200, 7));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 1800, 8));
	}

	@Test void missingSceneAfterBreakStillTimesOut()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		guard.suspend();
		assertEquals(WAIT, guard.check(GameState.LOGIN_SCREEN, null, 0, 0));
		assertEquals(UNAVAILABLE, guard.check(GameState.LOGGED_IN, null, 15_000, 1));
	}

	@Test void repeatedPauseChecksCannotPoisonTheNextResume()
	{
		TransitionSnapshotGuard guard = new TransitionSnapshotGuard();
		guard.suspend();
		guard.suspend();
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 100_000, 10));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 100_600, 11));
		guard.suspend();
		assertEquals(WAIT, guard.check(GameState.LOGGED_IN, position, 200_000, 12));
		assertEquals(READY, guard.check(GameState.LOGGED_IN, position, 200_600, 13));
		assertTrue(guard.sessionRecovered());
	}

	@Test void missingClientCannotStartOrCompleteTravel()
	{
		MahoganyHomesScript script = new MahoganyHomesScript();
		assertDoesNotThrow(() -> script.onState(MahoganyHomesScript.State.TRAVEL));
		assertNotNull(script.getError());
		assertTrue(script.getError().contains("unavailable"));
		assertFalse(script.isContractArrived());
	}

	@Test void breakLogoutCannotLatchMissingSceneErrorInHomes()
	{
		var previous = net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State.getCurrentState();
		try
		{
			net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State.setState(
				net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State.LOGGED_OUT);
			MahoganyHomesScript script = new MahoganyHomesScript();
			assertDoesNotThrow(() -> script.onState(MahoganyHomesScript.State.TURN_IN_CONTRACT));
			assertNull(script.getError(), "Expected break logout must not become a fatal route error");
			assertFalse(script.isContractArrived());
		}
		finally
		{
			net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State.setState(previous);
		}
	}
}
