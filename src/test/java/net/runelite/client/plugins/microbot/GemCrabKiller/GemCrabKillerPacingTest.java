package net.runelite.client.plugins.microbot.GemCrabKiller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemCrabKillerPacingTest {
    private Object invoke(GemCrabKillerScript script, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = GemCrabKillerScript.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(script, args);
    }

    private Instant field(GemCrabKillerScript script, String name) throws Exception {
        Field field = GemCrabKillerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Instant) field.get(script);
    }

    @Test void resetRetainsReactionDelayAndClearsRetryState() throws Exception {
        GemCrabKillerScript script = new GemCrabKillerScript();
        Instant before = Instant.now();
        invoke(script, "resetPacing", new Class<?>[0]);
        Instant after = Instant.now();
        Instant attackAt = field(script, "nextAttackAttemptAt");
        assertFalse(attackAt.isBefore(before.plusMillis(600)));
        assertFalse(attackAt.isAfter(after.plusMillis(1900)));
        assertEquals(Instant.EPOCH, field(script, "nextCaveInteractionAt"));
        assertEquals(Instant.EPOCH, field(script, "nextWalkAttemptAt"));
        assertNull(field(script, "respawnReactionAt"));
        assertNull(field(script, "respawnTimeoutAt"));
    }

    @Test void futureOrAbsentDeadlineDoesNotPermitInteraction() throws Exception {
        GemCrabKillerScript script = new GemCrabKillerScript();
        assertEquals(false, invoke(script, "isReady", new Class<?>[]{Instant.class}, Instant.now().plusSeconds(60)));
        assertEquals(false, invoke(script, "isReady", new Class<?>[]{Instant.class}, (Object) null));
        assertEquals(true, invoke(script, "isReady", new Class<?>[]{Instant.class}, Instant.EPOCH));
    }
}
