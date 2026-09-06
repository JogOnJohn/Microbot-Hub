package net.runelite.client.plugins.microbot.autobankstander.processing;

/** Bounded recovery budget for terminal batch failures. */
public final class BatchRecoveryPolicy {
    private final int maxRecoveries;
    private int recoveries;

    public BatchRecoveryPolicy(int maxRecoveries) {
        if (maxRecoveries < 1) throw new IllegalArgumentException("maxRecoveries must be positive");
        this.maxRecoveries = maxRecoveries;
    }

    public boolean tryAcquire() {
        if (recoveries >= maxRecoveries) return false;
        recoveries++;
        return true;
    }

    public void reset() {
        recoveries = 0;
    }

    public int getRecoveries() {
        return recoveries;
    }
}
