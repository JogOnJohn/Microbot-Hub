package net.runelite.client.plugins.microbot.sharedautomation.humanizer;

/** Inclusive millisecond range for a sampled delay or duration. */
public final class DelayRange
{
	private final long minimumMillis;
	private final long maximumMillis;

	public DelayRange(long minimumMillis, long maximumMillis)
	{
		if (minimumMillis < 0 || maximumMillis < minimumMillis || maximumMillis == Long.MAX_VALUE)
		{
			throw new IllegalArgumentException("Expected 0 <= minimum <= maximum < Long.MAX_VALUE");
		}

		this.minimumMillis = minimumMillis;
		this.maximumMillis = maximumMillis;
	}

	public long getMinimumMillis()
	{
		return minimumMillis;
	}

	public long getMaximumMillis()
	{
		return maximumMillis;
	}

	public long sampleMillis(RandomSource random)
	{
		if (random == null)
		{
			throw new IllegalArgumentException("random must not be null");
		}

		if (minimumMillis == maximumMillis)
		{
			return minimumMillis;
		}

		return random.nextLong(minimumMillis, maximumMillis + 1);
	}
}
