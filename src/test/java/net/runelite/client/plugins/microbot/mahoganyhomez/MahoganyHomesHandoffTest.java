package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesHandoffTest {
    private final WorldPoint player = new WorldPoint(3000, 3300, 0);
    private final WorldPoint npc = new WorldPoint(3003, 3300, 0);

    @Test void visibleNpcBehindClosedDoorDoesNotCancelRoute() {
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player, npc, true, true, Set.of(player)));
        assertTrue(MahoganyHomesScript.interactionHandoffReady(player, npc, true, true, Set.of(player, npc)));
    }

    @Test void wrongFloorMissingActionAndOffscreenNpcDoNotCancelRoute() {
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player,
            new WorldPoint(3003, 3300, 1), true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player, npc, false, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player, npc, true, false, Set.of(npc)));
        assertFalse(MahoganyHomesScript.interactionHandoffReady(null, npc, true, true, Set.of(npc)));
    }
}
