package net.runelite.client.plugins.microbot.sharedautomation.mouse;

import java.awt.Point;

/**
 * Smoothly follows a changing cursor target. It only moves the cursor; it never clicks or validates game targets.
 * Calls should be made from one consistent thread, normally a plugin's client-tick handler.
 */
public final class MouseIntentController
{
	private final MousePort mouse;
	private final MotionProfile profile;
	private String owner;
	private int priority;
	private MouseTarget target;
	private long previousTickNanos;
	private boolean hasPreviousTick;
	private double velocityX;
	private double velocityY;

	public MouseIntentController(MousePort mouse)
	{
		this(mouse, MotionProfile.DEFAULT);
	}

	public MouseIntentController(MousePort mouse, MotionProfile profile)
	{
		if (mouse == null || profile == null)
		{
			throw new IllegalArgumentException("mouse and profile are required");
		}
		this.mouse = mouse;
		this.profile = profile;
	}

	/**
	 * Requests cursor ownership. An existing owner may update its projected target; a different owner may only
	 * preempt it with a strictly higher priority.
	 */
	public boolean request(String requestedOwner, int requestedPriority, MouseTarget requestedTarget)
	{
		if (requestedOwner == null || requestedOwner.trim().isEmpty() || requestedTarget == null)
		{
			throw new IllegalArgumentException("owner and target are required");
		}

		if (requestedOwner.equals(owner))
		{
			priority = requestedPriority;
			target = requestedTarget;
			return true;
		}

		if (owner != null && requestedPriority <= priority)
		{
			return false;
		}

		owner = requestedOwner;
		priority = requestedPriority;
		target = requestedTarget;
		resetMotion();
		return true;
	}

	/** Advances the cursor toward the target sampled for this frame. */
	public boolean tick()
	{
		return tick(System.nanoTime());
	}

	/** Advances the cursor using the supplied monotonic timestamp, useful for deterministic callers. */
	public boolean tick(long nowNanos)
	{
		if (owner == null)
		{
			return false;
		}

		Point targetPoint = target.projectCurrentPoint();
		if (targetPoint == null)
		{
			cancel();
			return false;
		}

		Point current = mouse.getPosition();
		if (current == null)
		{
			resetMotion();
			previousTickNanos = nowNanos;
			hasPreviousTick = true;
			return true;
		}

		if (!hasPreviousTick)
		{
			previousTickNanos = nowNanos;
			hasPreviousTick = true;
			return true;
		}
		if (nowNanos <= previousTickNanos)
		{
			velocityX = 0.0;
			velocityY = 0.0;
			previousTickNanos = nowNanos;
			return true;
		}

		double elapsedSeconds = Math.min(
			(nowNanos - previousTickNanos) / 1_000_000_000.0,
			profile.getMaximumStepSeconds());
		previousTickNanos = nowNanos;

		double deltaX = targetPoint.x - current.x;
		double deltaY = targetPoint.y - current.y;
		if (Math.abs(deltaX) < 1.0 && Math.abs(deltaY) < 1.0)
		{
			velocityX = 0.0;
			velocityY = 0.0;
			return true;
		}

		velocityX = approachVelocity(velocityX, desiredVelocity(deltaX), elapsedSeconds);
		velocityY = approachVelocity(velocityY, desiredVelocity(deltaY), elapsedSeconds);

		double stepX = limitStep(velocityX * elapsedSeconds, deltaX);
		double stepY = limitStep(velocityY * elapsedSeconds, deltaY);
		int nextX = (int) Math.round(current.x + stepX);
		int nextY = (int) Math.round(current.y + stepY);

		if (nextX != current.x || nextY != current.y)
		{
			mouse.moveTo(nextX, nextY);
		}
		return true;
	}

	public boolean cancel(String requestedOwner)
	{
		if (requestedOwner == null || !requestedOwner.equals(owner))
		{
			return false;
		}
		cancel();
		return true;
	}

	public void cancel()
	{
		owner = null;
		target = null;
		priority = 0;
		resetMotion();
	}

	public boolean isActive()
	{
		return owner != null;
	}

	public String getOwner()
	{
		return owner;
	}

	public int getPriority()
	{
		return priority;
	}

	private double desiredVelocity(double delta)
	{
		double desired = delta * profile.getTrackingGainPerSecond();
		return Math.max(-profile.getMaximumSpeedPixelsPerSecond(),
			Math.min(profile.getMaximumSpeedPixelsPerSecond(), desired));
	}

	private double approachVelocity(double currentVelocity, double desiredVelocity, double elapsedSeconds)
	{
		double maximumChange = profile.getAccelerationPixelsPerSecondSquared() * elapsedSeconds;
		double change = desiredVelocity - currentVelocity;
		return currentVelocity + Math.max(-maximumChange, Math.min(maximumChange, change));
	}

	private static double limitStep(double step, double remainingDistance)
	{
		if (Math.signum(step) == Math.signum(remainingDistance) && Math.abs(step) > Math.abs(remainingDistance))
		{
			return remainingDistance;
		}
		return step;
	}

	private void resetMotion()
	{
		previousTickNanos = 0;
		hasPreviousTick = false;
		velocityX = 0.0;
		velocityY = 0.0;
	}
}
