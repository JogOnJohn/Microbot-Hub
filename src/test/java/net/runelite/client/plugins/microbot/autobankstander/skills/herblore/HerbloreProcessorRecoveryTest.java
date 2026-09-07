package net.runelite.client.plugins.microbot.autobankstander.skills.herblore;

import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.autobankstander.processing.BatchTransaction;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

/** Executable wiring assertions for terminal Herblore batch recovery. */
public final class HerbloreProcessorRecoveryTest {
    public static void main(String[] args) throws Exception {
        HerbloreProcessor processor = new HerbloreProcessor(
                Mode.FINISHED_POTIONS,
                CleanHerbMode.ANY_AND_ALL,
                UnfinishedPotionMode.ANY_AND_ALL,
                HerblorePotion.PRAYER,
                false);

        for (int recovery = 1; recovery <= 3; recovery++) {
            set(processor, "batchTransaction", failedTransaction(recovery));
            set(processor, "batchRetryCount", 2);
            expect(processor.recoverFromProcessingFailure(),
                    "recovery " + recovery + " should be allowed");
            expect(get(processor, "batchTransaction") == null,
                    "terminal transaction must be cleared before banking retry");
            expect((int) get(processor, "batchRetryCount") == 0,
                    "per-generation retry count must be reset");
        }

        set(processor, "batchTransaction", failedTransaction(4));
        expect(!processor.recoverFromProcessingFailure(),
                "fourth consecutive terminal batch recovery must stop");

        BatchTransaction interrupted = acknowledgedTransaction(5);
        set(processor, "batchTransaction", interrupted);
        set(processor, "batchRetryCount", 2);
        expect(processor.recoverAfterLogin(),
                "an interrupted in-flight batch should require banking reconciliation");
        expect(get(processor, "batchTransaction") == null,
                "login recovery must discard the stale pre-logout transaction");
        expect((int) get(processor, "batchRetryCount") == 0,
                "login recovery must clear pre-logout retries");
        System.out.println("HerbloreProcessorRecoveryTest PASSED");
    }

    private static BatchTransaction failedTransaction(long generation) {
        BatchTransaction transaction = new BatchTransaction(
                generation,
                new BatchTransaction.Observation(100, 14, 14, false, false),
                1, 5, 12);
        transaction.observe(new BatchTransaction.Observation(105, 14, 14, false, false));
        return transaction;
    }

    private static BatchTransaction acknowledgedTransaction(long generation) {
        BatchTransaction transaction = new BatchTransaction(
                generation,
                new BatchTransaction.Observation(100, 14, 14, false, false),
                1, 5, 12);
        transaction.observe(new BatchTransaction.Observation(101, 8, 8, true, false));
        return transaction;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
