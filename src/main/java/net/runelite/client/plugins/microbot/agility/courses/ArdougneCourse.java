package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class ArdougneCourse implements AgilityCourseHandler
{
	@Override
	public boolean shouldClickObstacle(final int currentXp, final int lastXp)
	{
		// The starting wall awards XP before its climb has fully finished. Do not
		// treat that early XP as permission to click the same wall a second time.
		if (getCurrentObstacleIndex() == 0 && (Rs2Player.isMoving() || Rs2Player.isAnimating()))
		{
			return false;
		}
		return AgilityCourseHandler.super.shouldClickObstacle(currentXp, lastXp);
	}

	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2673, 3298, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_PLANK),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_2, -1, 3318, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_3, -1, 3310, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCROSSING),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_4)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 90;
	}

	@Override
	public int getLootDistance() {
		return 2;
	}
}
