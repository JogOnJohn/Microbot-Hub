package net.runelite.client.plugins.microbot.sharedautomation.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothCameraRotationTest
{
	@Test
	void wrapsAcrossNorthUsingTheShortestPath()
	{
		SmoothCameraRotation rotation = new SmoothCameraRotation();
		assertTrue(rotation.start(2000, 48, 0, 1_000_000_000L));
		assertEquals(2000, rotation.sample(0));
		assertEquals(0, rotation.sample(500_000_000L));
		assertEquals(48, rotation.sample(1_000_000_000L));
		assertNull(rotation.sample(1_020_000_000L));

		assertTrue(rotation.start(48, 2000, 2_000_000_000L, 1_000_000_000L));
		assertEquals(0, rotation.sample(2_500_000_000L));
		assertEquals(2000, rotation.sample(3_000_000_000L));
	}

	@Test
	void usesSmoothstepAndDoesNotRestartAnActiveTurn()
	{
		SmoothCameraRotation rotation = new SmoothCameraRotation();
		assertTrue(rotation.start(0, 512, 0, 1_000_000_000L));
		assertEquals(80, rotation.sample(250_000_000L));
		assertFalse(rotation.start(80, 1024, 250_000_000L, 1_000_000_000L));
		assertEquals(256, rotation.sample(500_000_000L));
		assertEquals(512, rotation.sample(1_000_000_000L));
		assertFalse(rotation.isActive());
	}

	@Test
	void supportsOtherAngleDomainsAndNoOpTurns()
	{
		SmoothCameraRotation rotation = new SmoothCameraRotation(360);
		assertTrue(rotation.start(350, 10, 0, 1_000_000_000L));
		assertEquals(0, rotation.sample(500_000_000L));
		assertEquals(10, rotation.sample(1_000_000_000L));

		assertTrue(rotation.start(370, 10, 2_000_000_000L, 1_000_000_000L));
		assertFalse(rotation.isActive());
		assertNull(rotation.sample(2_000_000_000L));
	}

	@Test
	void cancellationStopsFurtherSamples()
	{
		SmoothCameraRotation rotation = new SmoothCameraRotation();
		assertTrue(rotation.start(0, 512, 0, 1_000_000_000L));
		rotation.cancel();
		assertNull(rotation.sample(500_000_000L));
	}
}
