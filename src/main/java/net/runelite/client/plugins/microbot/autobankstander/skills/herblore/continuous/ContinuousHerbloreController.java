package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

/**
 * Restartable continuous-mode state machine. Live adapters report phase
 * evidence into this controller; ambiguous accounting always stops the run.
 */
public final class ContinuousHerbloreController {
    private final ContinuousHerblorePlan plan;
    private ContinuousHerblorePhase phase = ContinuousHerblorePhase.PRECHECK;
    private long phaseStartedAt;
    private int phaseRetries;
    private int completedCycles;
    private long spent;
    private long revenue;
    private String stopReason = "";

    public ContinuousHerbloreController(ContinuousHerblorePlan plan, long now) {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        this.plan = plan;
        this.phaseStartedAt = now;
    }

    public boolean mayBuy(int unitPrice, int quantity, long availableCoins) {
        if (phase != ContinuousHerblorePhase.ACQUIRE_INPUTS || unitPrice < 1 || quantity < 1) return false;
        long proposed = (long) unitPrice * quantity;
        return unitPrice <= plan.maxBuyPrice && availableCoins - proposed >= plan.capitalReserve
                && !wouldExceedStopLoss(proposed, 0);
    }

    public boolean maySell(int unitPrice) {
        return (phase == ContinuousHerblorePhase.OPTIONAL_SELL
                || phase == ContinuousHerblorePhase.INTERIM_SELL)
                && plan.sellEnabled && unitPrice >= plan.minSellPrice;
    }

    public void recordPurchase(long actualSpend) {
        requirePhase(ContinuousHerblorePhase.ACQUIRE_INPUTS);
        if (actualSpend < 0) stop("ambiguous purchase accounting");
        else {
            spent += actualSpend;
            if (wouldExceedStopLoss(0, 0)) stop("stop loss reached");
        }
    }

    public void recordSale(long actualRevenue) {
        if (phase != ContinuousHerblorePhase.OPTIONAL_SELL
                && phase != ContinuousHerblorePhase.INTERIM_SELL) {
            throw new IllegalStateException("expected a sale phase, was " + phase);
        }
        if (actualRevenue < 0) stop("ambiguous sale accounting");
        else revenue += actualRevenue;
    }

    public void beginAfterPrecheck(ContinuousHerblorePhase requestedPhase, long now) {
        requirePhase(ContinuousHerblorePhase.PRECHECK);
        if (requestedPhase != ContinuousHerblorePhase.ACQUIRE_INPUTS
                && requestedPhase != ContinuousHerblorePhase.CLEAN_HERBS
                && requestedPhase != ContinuousHerblorePhase.MAKE_UNFINISHED
                && requestedPhase != ContinuousHerblorePhase.MAKE_FINISHED
                && requestedPhase != ContinuousHerblorePhase.OPTIONAL_DECANT
                && requestedPhase != ContinuousHerblorePhase.OPTIONAL_SELL) {
            throw new IllegalArgumentException("unsupported continuous start phase: " + requestedPhase);
        }
        phaseRetries = 0;
        transition(requestedPhase, now);
    }

    public void beginInterimSale(boolean decantFirst, long now) {
        requirePhase(ContinuousHerblorePhase.MAKE_FINISHED);
        phaseRetries = 0;
        transition(decantFirst ? ContinuousHerblorePhase.INTERIM_DECANT
                : ContinuousHerblorePhase.INTERIM_SELL, now);
    }

    public void succeedPhase(long now) {
        if (phase == ContinuousHerblorePhase.STOPPED) return;
        phaseRetries = 0;
        switch (phase) {
            case PRECHECK: transition(ContinuousHerblorePhase.ACQUIRE_INPUTS, now); break;
            case ACQUIRE_INPUTS: transition(ContinuousHerblorePhase.CLEAN_HERBS, now); break;
            case CLEAN_HERBS: transition(ContinuousHerblorePhase.MAKE_UNFINISHED, now); break;
            case MAKE_UNFINISHED: transition(ContinuousHerblorePhase.MAKE_FINISHED, now); break;
            case MAKE_FINISHED:
                transition(plan.decantEnabled ? ContinuousHerblorePhase.OPTIONAL_DECANT
                        : plan.sellEnabled ? ContinuousHerblorePhase.OPTIONAL_SELL
                        : ContinuousHerblorePhase.RECONCILE, now);
                break;
            case INTERIM_DECANT: transition(ContinuousHerblorePhase.INTERIM_SELL, now); break;
            case INTERIM_SELL: transition(ContinuousHerblorePhase.MAKE_FINISHED, now); break;
            case OPTIONAL_DECANT:
                transition(plan.sellEnabled ? ContinuousHerblorePhase.OPTIONAL_SELL
                        : ContinuousHerblorePhase.RECONCILE, now);
                break;
            case OPTIONAL_SELL: transition(ContinuousHerblorePhase.RECONCILE, now); break;
            case RECONCILE:
                completedCycles++;
                if (!plan.unlimitedCycles && completedCycles >= plan.cycleLimit) stop("cycle limit reached");
                else transition(ContinuousHerblorePhase.PRECHECK, now);
                break;
            default: break;
        }
    }

    public void failPhase(String reason, long now) {
        if (phase == ContinuousHerblorePhase.STOPPED) return;
        if (phaseRetries >= plan.retryLimit) {
            stop("retry limit reached in " + phase + ": " + reason);
            return;
        }
        phaseRetries++;
        phaseStartedAt = now;
    }

    public void checkTimeout(long now) {
        if (isPhaseTimedOut(now)) {
            failPhase("phase timeout", now);
        }
    }

    public boolean isPhaseTimedOut(long now) {
        return phase != ContinuousHerblorePhase.STOPPED
                && now - phaseStartedAt >= plan.phaseTimeoutMillis;
    }

    public void stop(String reason) {
        phase = ContinuousHerblorePhase.STOPPED;
        stopReason = reason == null ? "stopped" : reason;
    }

    private boolean wouldExceedStopLoss(long additionalSpend, long additionalRevenue) {
        return plan.stopLoss > 0 && (spent + additionalSpend) - (revenue + additionalRevenue) >= plan.stopLoss;
    }

    private void transition(ContinuousHerblorePhase next, long now) {
        phase = next;
        phaseStartedAt = now;
    }

    private void requirePhase(ContinuousHerblorePhase expected) {
        if (phase != expected) throw new IllegalStateException("expected " + expected + ", was " + phase);
    }

    public ContinuousHerblorePhase getPhase() { return phase; }
    public int getPhaseRetries() { return phaseRetries; }
    public int getCompletedCycles() { return completedCycles; }
    public long getSpent() { return spent; }
    public long getRevenue() { return revenue; }
    public long getNetCost() { return spent - revenue; }
    public String getStopReason() { return stopReason; }
}
