package net.runelite.client.plugins.microbot.autobankstander.processing;

/** Lightweight assertion test runnable without a JUnit dependency. */
public final class BatchTransactionTest {
    public static void main(String[] args) {
        acknowledgesProgressAndCompletesOnDepletion();
        suppressesRedispatchWhileInFlight();
        timesOutMissingAcknowledgement();
    }

    private static void acknowledgesProgressAndCompletesOnDepletion() {
        BatchTransaction transaction = transaction(1);
        expect(transaction.observe(observation(101, 14, false, true))
                == BatchTransaction.State.ACKNOWLEDGED, "dialogue should acknowledge dispatch");
        expect(transaction.observe(observation(103, 13, true, false))
                == BatchTransaction.State.ACKNOWLEDGED, "inventory progress should remain active");
        expect(transaction.getCompletedOperations() == 1, "one operation should be counted");
        expect(transaction.observe(observation(120, 0, false, false))
                == BatchTransaction.State.COMPLETED, "input depletion should complete the generation");
    }

    private static void suppressesRedispatchWhileInFlight() {
        BatchTransaction transaction = transaction(2);
        expect(transaction.isInFlight(), "newly dispatched generation must suppress another dispatch");
        transaction.observe(observation(101, 14, true, false));
        expect(transaction.isInFlight(), "acknowledged generation must suppress another dispatch");
        expect(transaction.getGeneration() == 2, "generation identity must remain stable");
    }

    private static void timesOutMissingAcknowledgement() {
        BatchTransaction transaction = transaction(3);
        expect(transaction.observe(observation(105, 14, false, false))
                == BatchTransaction.State.FAILED, "missing start acknowledgement should fail boundedly");
    }

    private static BatchTransaction transaction(long generation) {
        return new BatchTransaction(generation, observation(100, 14, false, false), 1, 5, 12);
    }

    private static BatchTransaction.Observation observation(int tick, int primary,
                                                            boolean animating, boolean dialogue) {
        return new BatchTransaction.Observation(tick, primary, 14, animating, dialogue);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
