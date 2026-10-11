package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.function.LongSupplier;

final class MahoganyHomesSessionStats
{
	private final LongSupplier clock;
	private boolean initialized;
	private int startingXp;
	private int currentXp;
	private long startedAt;

	MahoganyHomesSessionStats()
	{
		this(System::nanoTime);
	}

	MahoganyHomesSessionStats(LongSupplier clock)
	{
		this.clock = clock;
	}

	synchronized void reset()
	{
		initialized = false;
		startingXp = currentXp = 0;
	}

	synchronized void observe(int xp)
	{
		if (xp < 0) return;
		if (!initialized)
		{
			startingXp = currentXp = xp;
			startedAt = clock.getAsLong();
			initialized = true;
		}
		else
		{
			currentXp = Math.max(currentXp, xp);
		}
	}

	synchronized int gained()
	{
		return currentXp - startingXp;
	}

	synchronized long runtimeMillis()
	{
		return initialized ? Math.max(0, (clock.getAsLong() - startedAt) / 1_000_000L) : 0;
	}

	synchronized int perHour(int count)
	{
		if (!initialized || count <= 0) return 0;
		long rate = Math.max(0, count) * 3_600_000L / Math.max(1_000, runtimeMillis());
		return (int) Math.min(Integer.MAX_VALUE, rate);
	}
}
