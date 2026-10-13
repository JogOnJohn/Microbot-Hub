package net.runelite.client.plugins.microbot.mahoganyhomez;

/** A reachable homeowner is not proof that their repair varbits have loaded. */
final class ContractLayoutReadiness
{
	static final int MAX_WAIT_TICKS = 60;
	enum Decision { READY, WAIT, EXPIRED }
	private int startedTick = -1;

	Decision check(boolean inHouse, boolean layoutLoaded, int tick)
	{
		if (inHouse && layoutLoaded)
		{
			reset();
			return Decision.READY;
		}
		if (startedTick < 0) startedTick = tick;
		return tick - startedTick >= MAX_WAIT_TICKS ? Decision.EXPIRED : Decision.WAIT;
	}

	boolean pending() { return startedTick >= 0; }
	void reset() { startedTick = -1; }
}
