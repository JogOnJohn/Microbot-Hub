package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.autobankstander.processing.BatchTransaction;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

/** Executable assertion that continuous mode forwards login recovery to its active worker. */
public final class ContinuousHerbloreProcessorRecoveryTest {
    public static void main(String[] args) throws Exception {
        ContinuousHerbloreProcessor continuous = new ContinuousHerbloreProcessor(new ConfigData());
        HerbloreProcessor worker = new HerbloreProcessor(
                Mode.FINISHED_POTIONS,
                CleanHerbMode.ANY_AND_ALL,
                UnfinishedPotionMode.ANY_AND_ALL,
                HerblorePotion.PRAYER,
                false);
        BatchTransaction interrupted = new BatchTransaction(
                7, new BatchTransaction.Observation(100, 14, 14, false, false), 1, 5, 12);
        interrupted.observe(new BatchTransaction.Observation(101, 8, 8, true, false));
        set(worker, "batchTransaction", interrupted);
        set(continuous, "phaseWorker", worker);
        set(continuous, "workerPhase", ContinuousHerblorePhase.MAKE_FINISHED);

        expect(continuous.recoverAfterLogin(),
                "continuous mode must request banking when its worker was interrupted");
        expect(get(worker, "batchTransaction") == null,
                "continuous recovery must discard the worker's stale transaction");
        expect(!continuous.recoverAfterLogin(),
                "recovery must not repeat after the worker transaction is cleared");
        System.out.println("ContinuousHerbloreProcessorRecoveryTest PASSED");
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
