package net.runelite.client.plugins.microbot.cgprep.antiban;

import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class CgPauseSafetyTest {
    @Test public void forcedCooldownWatchdogDoesNotReleaseSharedPause() throws Exception {
        boolean paused = Microbot.pauseAllScripts.get();
        boolean cooldown = Rs2AntibanSettings.actionCooldownActive;
        try {
            Microbot.pauseAllScripts.set(true);
            Rs2AntibanSettings.actionCooldownActive = true;
            CgAntiban antiban = new CgAntiban(message -> {});
            Field started = CgAntiban.class.getDeclaredField("cooldownStartedMs");
            started.setAccessible(true);
            started.setLong(antiban, 1L);
            antiban.releaseStuckCooldown(true);
            assertTrue(Microbot.pauseAllScripts.get());
            assertTrue(Rs2AntibanSettings.actionCooldownActive);
        } finally {
            Microbot.pauseAllScripts.set(paused);
            Rs2AntibanSettings.actionCooldownActive = cooldown;
        }
    }
}
