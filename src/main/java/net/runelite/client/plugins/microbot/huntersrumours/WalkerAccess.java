package net.runelite.client.plugins.microbot.huntersrumours;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

final class WalkerAccess
{
	private volatile WorldPoint requestedTarget;
	private volatile String currentStatus = "IDLE";

	static WalkerAccess bind()
	{
		return new WalkerAccess();
	}

	boolean available()
	{
		return true;
	}

	boolean walkTo(WorldPoint point)
	{
		return travel(point);
	}

	boolean nearestBank()
	{
		BankLocation bank = Rs2Bank.getNearestBank();
		return bank != null && travel(bank.getWorldPoint());
	}

	private boolean travel(WorldPoint target)
	{
		if (target == null) return false;
		WorldPoint activeTarget = Rs2Walker.getCurrentTarget();
		if (activeTarget != null && !activeTarget.equals(requestedTarget)) return false;
		requestedTarget = target;
		currentStatus = "MOVING";
		while (!Thread.currentThread().isInterrupted() && target.equals(requestedTarget))
		{
			WalkerState state = Rs2Walker.walkWithBankedTransportsAndState(target, 3, false);
			if (!target.equals(requestedTarget)) return false;
			if (state == WalkerState.ARRIVED)
			{
				currentStatus = "ARRIVED";
				return true;
			}
			if (state == WalkerState.EXIT || state == WalkerState.UNREACHABLE)
			{
				currentStatus = "BLOCKED";
				return false;
			}
			try
			{
				Thread.sleep(150);
			}
			catch (InterruptedException exception)
			{
				Thread.currentThread().interrupt();
				currentStatus = "CANCELLED";
				return false;
			}
		}
		currentStatus = "CANCELLED";
		return false;
	}

	String status()
	{
		return currentStatus;
	}

	void cancel()
	{
		WorldPoint activeTarget = Rs2Walker.getCurrentTarget();
		if (requestedTarget != null && requestedTarget.equals(activeTarget))
			Rs2Walker.clearWalkingRoute("hunters-rumours:cancel-owned-route");
		requestedTarget = null;
		currentStatus = "CANCELLED";
	}
}
