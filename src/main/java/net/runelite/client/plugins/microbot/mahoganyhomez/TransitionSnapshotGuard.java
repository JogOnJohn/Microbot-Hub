package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

final class TransitionSnapshotGuard
{
	static final long MAX_WAIT_MILLIS = 15_000;

	enum Decision { READY, WAIT, UNAVAILABLE }

	private long waitingSince = -1;
	private long waitedMillis;
	private boolean recovered;

	Decision check(GameState gameState, WorldPoint position, long now)
	{
		recovered = false;
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING)
		{
			return Decision.UNAVAILABLE;
		}
		boolean usable = gameState == GameState.LOGGED_IN && position != null;
		long currentWait = waitingSince < 0 ? 0 : Math.max(0, now - waitingSince);
		// The allowance is cumulative per journey, not renewed by intermittent valid reads.
		if (waitedMillis + currentWait >= MAX_WAIT_MILLIS)
		{
			return Decision.UNAVAILABLE;
		}
		if (!usable)
		{
			if (waitingSince < 0) waitingSince = now;
			return Decision.WAIT;
		}
		if (waitingSince >= 0)
		{
			waitedMillis += currentWait;
			waitingSince = -1;
			recovered = true;
		}
		return Decision.READY;
	}

	boolean recovered()
	{
		return recovered;
	}
}
