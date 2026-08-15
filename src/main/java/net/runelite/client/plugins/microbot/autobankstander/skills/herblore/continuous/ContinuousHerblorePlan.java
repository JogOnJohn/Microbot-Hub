package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

/** Immutable safety envelope for a continuous Herblore run. */
public final class ContinuousHerblorePlan {
    public final int capitalReserve;
    public final int maxBuyPrice;
    public final int minSellPrice;
    public final int retryLimit;
    public final long phaseTimeoutMillis;
    public final long stopLoss;
    public final int cycleLimit;
    public final boolean unlimitedCycles;
    public final boolean decantEnabled;
    public final boolean sellEnabled;

    public ContinuousHerblorePlan(int capitalReserve, int maxBuyPrice, int minSellPrice,
                                 int retryLimit, long phaseTimeoutMillis, long stopLoss,
                                 int cycleLimit, boolean unlimitedCycles,
                                 boolean decantEnabled, boolean sellEnabled) {
        if (capitalReserve < 0 || maxBuyPrice < 1 || minSellPrice < 1) {
            throw new IllegalArgumentException("capital and price limits must be positive");
        }
        if (retryLimit < 0 || phaseTimeoutMillis < 1 || stopLoss < 0) {
            throw new IllegalArgumentException("retry, timeout, and stop loss must be bounded");
        }
        if (!unlimitedCycles && cycleLimit < 1) {
            throw new IllegalArgumentException("bounded runs require at least one cycle");
        }
        this.capitalReserve = capitalReserve;
        this.maxBuyPrice = maxBuyPrice;
        this.minSellPrice = minSellPrice;
        this.retryLimit = retryLimit;
        this.phaseTimeoutMillis = phaseTimeoutMillis;
        this.stopLoss = stopLoss;
        this.cycleLimit = cycleLimit;
        this.unlimitedCycles = unlimitedCycles;
        this.decantEnabled = decantEnabled;
        this.sellEnabled = sellEnabled;
    }
}
