package net.runelite.client.plugins.microbot.agility;

import net.runelite.client.plugins.microbot.agility.courses.ArdougneCourse;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ArdougneCourseTest
{
	@Test
	void earlyXpDoesNotRetryStartingWallWhileClimbing()
	{
		ArdougneCourse course = spy(new ArdougneCourse());
		doReturn(0).when(course).getCurrentObstacleIndex();
		try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class))
		{
			player.when(Rs2Player::isAnimating).thenReturn(true);
			assertFalse(course.shouldClickObstacle(101, 100));
			player.when(Rs2Player::isAnimating).thenReturn(false);
			assertTrue(course.shouldClickObstacle(101, 100));
		}
	}

	@Test
	void otherObstaclesRetainXpSpeedClick()
	{
		ArdougneCourse course = spy(new ArdougneCourse());
		doReturn(1).when(course).getCurrentObstacleIndex();
		try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class))
		{
			player.when(Rs2Player::isMoving).thenReturn(true);
			assertTrue(course.shouldClickObstacle(101, 100));
		}
	}
}
