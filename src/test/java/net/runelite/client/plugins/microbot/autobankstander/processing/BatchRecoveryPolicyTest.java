package net.runelite.client.plugins.microbot.autobankstander.processing;

/** Executable assertions for bounded batch recovery. */
public final class BatchRecoveryPolicyTest {
    public static void main(String[] args) {
        BatchRecoveryPolicy policy = new BatchRecoveryPolicy(3);
        expect(policy.tryAcquire(), "first recovery should be allowed");
        expect(policy.tryAcquire(), "second recovery should be allowed");
        expect(policy.tryAcquire(), "third recovery should be allowed");
        expect(!policy.tryAcquire(), "fourth consecutive recovery must stop");
        policy.reset();
        expect(policy.tryAcquire(), "a successful new banked batch should reset the budget");
        System.out.println("BatchRecoveryPolicyTest PASSED");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
