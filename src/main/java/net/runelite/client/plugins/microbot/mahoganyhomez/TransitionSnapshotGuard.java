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
	private boolean suspended;
	private boolean sessionRecovered;
	private int resumeTick = -1;
	private int resumeSamples;

	void suspend()
	{
		suspended = true;
		waitingSince = -1;
		waitedMillis = 0;
		resumeTick = -1;
		resumeSamples = 0;
		recovered = sessionRecovered = false;
	}

	Decision check(GameState gameState, WorldPoint position, long now, int tick)
	{
		sessionRecovered = false;
		if (!suspended) return check(gameState, position, now);
		recovered = false;
		if (waitingSince < 0) waitingSince = now;
		if (now - waitingSince >= MAX_WAIT_MILLIS) return Decision.UNAVAILABLE;
		if (gameState != GameState.LOGGED_IN || position == null)
		{
			resumeSamples = 0;
			resumeTick = -1;
			return Decision.WAIT;
		}
		if (tick >= 0 && tick != resumeTick)
		{
			resumeTick = tick;
			resumeSamples++;
		}
		if (resumeSamples < 2) return Decision.WAIT;
		suspended = false;
		waitingSince = -1;
		waitedMillis = 0;
		recovered = sessionRecovered = true;
		return Decision.READY;
	}

	boolean sessionRecovered()
	{
		return sessionRecovered;
	}

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
