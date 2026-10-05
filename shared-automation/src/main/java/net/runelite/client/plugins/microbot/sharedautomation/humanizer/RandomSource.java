package net.runelite.client.plugins.microbot.sharedautomation.humanizer;

import java.util.concurrent.ThreadLocalRandom;

/** Supplies bounded random values while allowing callers to inject a deterministic source. */
@FunctionalInterface
public interface RandomSource
{
	long nextLong(long originInclusive, long boundExclusive);

	static RandomSource threadLocal()
	{
		return (origin, bound) -> ThreadLocalRandom.current().nextLong(origin, bound);
	}
}
