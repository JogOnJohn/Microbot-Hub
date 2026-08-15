package net.runelite.client.plugins.microbot.autobankstander.processing;

/**
 * Pure state machine for one inventory processing batch. The caller owns game
 * interaction; this class owns whether another dispatch is permitted.
 */
public final class BatchTransaction {
    public enum State {
        DISPATCHED,
        ACKNOWLEDGED,
        COMPLETED,
        FAILED
    }

    public static final class Observation {
        public final int tick;
        public final int primaryCount;
        public final int secondaryCount;
        public final boolean animating;
        public final boolean makeDialogueOpen;

        public Observation(int tick, int primaryCount, int secondaryCount,
                           boolean animating, boolean makeDialogueOpen) {
            this.tick = tick;
            this.primaryCount = primaryCount;
            this.secondaryCount = secondaryCount;
            this.animating = animating;
            this.makeDialogueOpen = makeDialogueOpen;
        }
    }

    private final long generation;
    private final int initialPrimaryCount;
    private final int secondaryPerOperation;
    private final int dispatchTick;
    private final int acknowledgementTimeoutTicks;
    private final int progressTimeoutTicks;
    private State state = State.DISPATCHED;
    private int lastProgressTick;
    private int completedOperations;
    private String failureReason = "";

    public BatchTransaction(long generation, Observation initial, int secondaryPerOperation,
                            int acknowledgementTimeoutTicks, int progressTimeoutTicks) {
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        if (initial == null) throw new IllegalArgumentException("initial observation is required");
        this.generation = generation;
        this.initialPrimaryCount = Math.max(0, initial.primaryCount);
        this.secondaryPerOperation = Math.max(1, secondaryPerOperation);
        this.dispatchTick = initial.tick;
        this.lastProgressTick = initial.tick;
        this.acknowledgementTimeoutTicks = Math.max(1, acknowledgementTimeoutTicks);
        this.progressTimeoutTicks = Math.max(1, progressTimeoutTicks);
    }

    public State observe(Observation observation) {
        if (observation == null || isTerminal()) return state;

        int progress = Math.max(0, initialPrimaryCount - observation.primaryCount);
        if (progress > completedOperations) {
            completedOperations = progress;
            lastProgressTick = observation.tick;
            state = State.ACKNOWLEDGED;
        } else if (state == State.DISPATCHED
                && (observation.animating || observation.makeDialogueOpen)) {
            state = State.ACKNOWLEDGED;
            lastProgressTick = observation.tick;
        }

        boolean inputsDepleted = observation.primaryCount <= 0
                || observation.secondaryCount < secondaryPerOperation;
        if (inputsDepleted) {
            state = State.COMPLETED;
        } else if (state == State.DISPATCHED
                && observation.tick - dispatchTick >= acknowledgementTimeoutTicks) {
            fail("start acknowledgement timeout");
        } else if (state == State.ACKNOWLEDGED
                && !observation.animating
                && !observation.makeDialogueOpen
                && observation.tick - lastProgressTick >= progressTimeoutTicks) {
            fail("batch progress timeout");
        }
        return state;
    }

    private void fail(String reason) {
        state = State.FAILED;
        failureReason = reason;
    }

    public long getGeneration() { return generation; }
    public State getState() { return state; }
    public int getCompletedOperations() { return completedOperations; }
    public String getFailureReason() { return failureReason; }
    public boolean isInFlight() { return state == State.DISPATCHED || state == State.ACKNOWLEDGED; }
    public boolean isTerminal() { return state == State.COMPLETED || state == State.FAILED; }
}
