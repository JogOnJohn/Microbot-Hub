package net.runelite.client.plugins.microbot.sharedautomation.mouse;

/** Motion limits for a cursor intent. Distances are canvas pixels and time values are seconds. */
public final class MotionProfile
{
	public static final MotionProfile DEFAULT = new MotionProfile(750.0, 3200.0, 8.0, 0.05);

	private final double maximumSpeedPixelsPerSecond;
	private final double accelerationPixelsPerSecondSquared;
	private final double trackingGainPerSecond;
	private final double maximumStepSeconds;

	public MotionProfile(
		double maximumSpeedPixelsPerSecond,
		double accelerationPixelsPerSecondSquared,
		double trackingGainPerSecond,
		double maximumStepSeconds)
	{
		if (!isPositiveFinite(maximumSpeedPixelsPerSecond)
			|| !isPositiveFinite(accelerationPixelsPerSecondSquared)
			|| !isPositiveFinite(trackingGainPerSecond)
			|| !isPositiveFinite(maximumStepSeconds))
		{
			throw new IllegalArgumentException("Motion profile values must be finite and positive");
		}

		this.maximumSpeedPixelsPerSecond = maximumSpeedPixelsPerSecond;
		this.accelerationPixelsPerSecondSquared = accelerationPixelsPerSecondSquared;
		this.trackingGainPerSecond = trackingGainPerSecond;
		this.maximumStepSeconds = maximumStepSeconds;
	}

	public double getMaximumSpeedPixelsPerSecond()
	{
		return maximumSpeedPixelsPerSecond;
	}

	public double getAccelerationPixelsPerSecondSquared()
	{
		return accelerationPixelsPerSecondSquared;
	}

	public double getTrackingGainPerSecond()
	{
		return trackingGainPerSecond;
	}

	public double getMaximumStepSeconds()
	{
		return maximumStepSeconds;
	}

	private static boolean isPositiveFinite(double value)
	{
		return value > 0.0 && !Double.isInfinite(value) && !Double.isNaN(value);
	}
}
