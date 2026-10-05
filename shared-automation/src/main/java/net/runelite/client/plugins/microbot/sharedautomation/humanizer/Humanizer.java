package net.runelite.client.plugins.microbot.sharedautomation.humanizer;

/** Random timing and probability helpers; callers retain control of which actions are allowed. */
public final class Humanizer
{
	private static final long CHANCE_SCALE = 1_000_000_000L;
	private final RandomSource random;

	public Humanizer()
	{
		this(RandomSource.threadLocal());
	}

	public Humanizer(RandomSource random)
	{
		if (random == null)
		{
			throw new IllegalArgumentException("random must not be null");
		}
		this.random = random;
	}

	public long delayMillis(DelayRange range)
	{
		if (range == null)
		{
			throw new IllegalArgumentException("range must not be null");
		}
		return range.sampleMillis(random);
	}

	/** Returns whether an explicitly selected optional behavior should occur. */
	public boolean occurs(double probability)
	{
		if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0)
		{
			throw new IllegalArgumentException("probability must be between 0.0 and 1.0");
		}
		if (probability == 0.0)
		{
			return false;
		}
		if (probability == 1.0)
		{
			return true;
		}

		return random.nextLong(0, CHANCE_SCALE) < (long) (probability * CHANCE_SCALE);
	}
}
