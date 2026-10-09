package net.runelite.client.plugins.microbot.sharedautomation.camera;

/**
 * Samples a non-blocking, shortest-path, smoothstep camera rotation. The caller owns the camera API and tick loop.
 */
public final class SmoothCameraRotation
{
	public static final int RUNELITE_ANGLE_UNITS = 2048;

	private final int angleUnits;
	private boolean active;
	private int startYaw;
	private int deltaYaw;
	private long startedAtNanos;
	private long durationNanos;

	public SmoothCameraRotation()
	{
		this(RUNELITE_ANGLE_UNITS);
	}

	/** Creates a rotation for a wrapping angle domain with an even number of units. */
	public SmoothCameraRotation(int angleUnits)
	{
		if (angleUnits < 2 || (angleUnits & 1) != 0)
		{
			throw new IllegalArgumentException("angleUnits must be an even value of at least 2");
		}
		this.angleUnits = angleUnits;
	}

	/**
	 * Starts a turn if one is not already active. Both timestamps and duration are nanoseconds from a monotonic
	 * clock such as {@link System#nanoTime()}.
	 *
	 * @return false if an existing turn owns the controller; true if this request was accepted
	 */
	public synchronized boolean start(int fromYaw, int targetYaw, long nowNanos, long durationNanos)
	{
		if (durationNanos <= 0)
		{
			throw new IllegalArgumentException("durationNanos must be positive");
		}
		if (active)
		{
			return false;
		}

		startYaw = Math.floorMod(fromYaw, angleUnits);
		int normalizedTarget = Math.floorMod(targetYaw, angleUnits);
		long halfTurn = angleUnits / 2L;
		deltaYaw = (int) Math.floorMod((long) normalizedTarget - startYaw + halfTurn, (long) angleUnits) - (int) halfTurn;
		startedAtNanos = nowNanos;
		this.durationNanos = durationNanos;
		active = deltaYaw != 0;
		return true;
	}

	/** Returns the next yaw for this frame, or null when no turn is active. */
	public synchronized Integer sample(long nowNanos)
	{
		if (!active)
		{
			return null;
		}

		double progress = Math.max(0.0, Math.min(1.0,
			(nowNanos - startedAtNanos) / (double) durationNanos));
		double eased = progress * progress * (3.0 - 2.0 * progress);
		if (progress >= 1.0)
		{
			active = false;
		}

		long sampledYaw = startYaw + Math.round(deltaYaw * eased);
		return (int) Math.floorMod(sampledYaw, (long) angleUnits);
	}

	public synchronized boolean isActive()
	{
		return active;
	}

	public synchronized void cancel()
	{
		active = false;
	}
}
