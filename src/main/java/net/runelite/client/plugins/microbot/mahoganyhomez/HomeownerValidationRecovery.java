package net.runelite.client.plugins.microbot.mahoganyhomez;

/** NPC movement or an arrival snapshot must not turn one missed probe into an error. */
final class HomeownerValidationRecovery
{
    static final int MAX_WAIT_TICKS = 30;
    enum Decision { READY, WAIT, APPROACH, EXPIRED }
    private int startedTick = -1;
    private int lastApproachTick = -1;

    Decision check(boolean reachable, int tick)
    {
        if (reachable)
        {
            reset();
            return Decision.READY;
        }
        if (startedTick < 0) startedTick = tick;
        if (tick - startedTick >= MAX_WAIT_TICKS) return Decision.EXPIRED;
        if (tick - startedTick >= 2 && (lastApproachTick < 0 || tick - lastApproachTick >= 6))
        {
            lastApproachTick = tick;
            return Decision.APPROACH;
        }
        return Decision.WAIT;
    }

    void reset()
    {
        startedTick = -1;
        lastApproachTick = -1;
    }
}
