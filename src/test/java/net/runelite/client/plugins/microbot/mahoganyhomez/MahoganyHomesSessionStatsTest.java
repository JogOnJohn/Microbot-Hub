package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MahoganyHomesSessionStatsTest
{
	@Test
	public void firstLoggedInSampleIsBaselineNotAccountXp()
	{
		AtomicLong clock = new AtomicLong();
		MahoganyHomesSessionStats stats = new MahoganyHomesSessionStats(clock::get);
		clock.set(60_000_000_000L);
		assertEquals(0, stats.runtimeMillis());
		stats.observe(1_250_000);
		assertEquals(0, stats.gained());
		clock.addAndGet(60_000_000_000L);
		stats.observe(1_251_000);
		assertEquals(1_000, stats.gained());
		assertEquals(60_000, stats.perHour(stats.gained()));
		assertEquals(120, stats.perHour(2));
	}

	@Test
	public void repeatedAndTransientLowerSamplesDoNotChangeGains()
	{
		MahoganyHomesSessionStats stats = new MahoganyHomesSessionStats(() -> 0);
		stats.observe(1000);
		stats.observe(1500);
		stats.observe(1500);
		stats.observe(0);
		stats.observe(-1);
		assertEquals(500, stats.gained());
	}

	@Test
	public void resetStartsFreshSessionAndLevelOneZeroXpIsValid()
	{
		MahoganyHomesSessionStats stats = new MahoganyHomesSessionStats(() -> 0);
		stats.observe(1000);
		stats.observe(1500);
		stats.reset();
		assertEquals(0, stats.gained());
		assertEquals(0, stats.perHour(10));
		stats.observe(0);
		stats.observe(100);
		assertEquals(100, stats.gained());
	}

	@Test
	public void rateDoesNotOverflowOrDivideByZero()
	{
		MahoganyHomesSessionStats stats = new MahoganyHomesSessionStats(() -> 0);
		stats.observe(0);
		stats.observe(Integer.MAX_VALUE);
		assertEquals(Integer.MAX_VALUE, stats.perHour(stats.gained()));
		assertEquals(0, stats.perHour(-1));
	}
}
