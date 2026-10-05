package net.runelite.client.plugins.microbot.sharedautomation.humanizer;

/** Repeating break schedule that will only start a due break at a caller-approved safe boundary. */
public final class SafeBoundaryBreakSchedule
{
	public enum Event
	{
		NONE,
		STARTED,
		ACTIVE,
		FINISHED
	}

	private final DelayRange interval;
	private final DelayRange duration;
	private final RandomSource random;
	private long nextBreakAtMillis;
	private long breakEndsAtMillis;
	private long lastObservedMillis;
	private boolean active;

	public SafeBoundaryBreakSchedule(
		long startTimeMillis,
		DelayRange interval,
		DelayRange duration,
		RandomSource random)
	{
		if (interval == null || duration == null || random == null)
		{
			throw new IllegalArgumentException("interval, duration, and random are required");
		}
		if (interval.getMinimumMillis() == 0 || duration.getMinimumMillis() == 0)
		{
			throw new IllegalArgumentException("break interval and duration must be positive");
		}

		this.interval = interval;
		this.duration = duration;
		this.random = random;
		this.lastObservedMillis = startTimeMillis;
		this.nextBreakAtMillis = addSaturated(startTimeMillis, interval.sampleMillis(random));
	}

	public Event poll(long nowMillis, boolean safeBoundary)
	{
		if (nowMillis < lastObservedMillis)
		{
			throw new IllegalArgumentException("nowMillis must be monotonic");
		}
		lastObservedMillis = nowMillis;

		if (active)
		{
			if (nowMillis < breakEndsAtMillis)
			{
				return Event.ACTIVE;
			}

			active = false;
			nextBreakAtMillis = addSaturated(nowMillis, interval.sampleMillis(random));
			return Event.FINISHED;
		}

		if (nowMillis >= nextBreakAtMillis && safeBoundary)
		{
			active = true;
			breakEndsAtMillis = addSaturated(nowMillis, duration.sampleMillis(random));
			return Event.STARTED;
		}

		return Event.NONE;
	}

	public boolean isActive()
	{
		return active;
	}

	public boolean isDue(long nowMillis)
	{
		return !active && nowMillis >= nextBreakAtMillis;
	}

	public long getRemainingMillis(long nowMillis)
	{
		return active ? Math.max(0, breakEndsAtMillis - nowMillis) : 0;
	}

	public long getNextBreakAtMillis()
	{
		return nextBreakAtMillis;
	}

	public void reset(long nowMillis)
	{
		if (nowMillis < lastObservedMillis)
		{
			throw new IllegalArgumentException("nowMillis must be monotonic");
		}
		lastObservedMillis = nowMillis;
		active = false;
		breakEndsAtMillis = 0;
		nextBreakAtMillis = addSaturated(nowMillis, interval.sampleMillis(random));
	}

	private static long addSaturated(long value, long amount)
	{
		if (amount > 0 && value > Long.MAX_VALUE - amount)
		{
			return Long.MAX_VALUE;
		}
		return value + amount;
	}
}
