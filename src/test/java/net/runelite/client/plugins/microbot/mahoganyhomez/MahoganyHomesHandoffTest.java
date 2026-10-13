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

    @Test void missingNpcLocationAndReachabilityCannotCancelRoute() {
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player, null, true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.interactionHandoffReady(player, npc, true, true, null));
        assertFalse(MahoganyHomesScript.isHomeownerReachable(player, npc, null));
        assertFalse(MahoganyHomesScript.isHomeownerReachable(null, npc, Set.of(npc)));
    }

    @Test void jessBedroomWorkCanHandOffBeforeBathroomAccessOnlyWhenReachable() {
        WorldPoint position = new WorldPoint(2615, 3294, 1);
        WorldPoint route = new WorldPoint(2615, 3292, 1);
        WorldPoint bed = new WorldPoint(2614, 3295, 1);
        WorldPoint access = new WorldPoint(2616, 3294, 1);
        var work = new MahoganyHomesScript.Work(4, 40175, bed, access);
        assertTrue(MahoganyHomesScript.furnitureHandoffReady(position, route, work, bed, 40175,
            true, true, Set.of(position, access)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(position, route, work, bed, 40175,
            true, true, Set.of(position)));
    }

    @Test void furnitureIdentityActionClickboxAndFloorMustRemainValid() {
        WorldPoint bed = new WorldPoint(3003, 3300, 0);
        var work = new MahoganyHomesScript.Work(4, 40175, bed, npc);
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, work, bed, 999,
            true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, work, player, 40175,
            true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, work, bed, 40175,
            false, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, work, bed, 40175,
            true, false, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, new WorldPoint(3003, 3300, 1), work,
            bed, 40175, true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(new WorldPoint(3000, 3300, 1), npc, work,
            bed, 40175, true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(new WorldPoint(2980, 3300, 0), npc, work,
            bed, 40175, true, true, Set.of(npc)));
        var wrongAccess = new MahoganyHomesScript.Work(4, 40175, bed, new WorldPoint(3003, 3300, 1));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, wrongAccess,
            bed, 40175, true, true, Set.of(wrongAccess.access)));
    }

    @Test void unmappedOrMissingWorkCannotCancelAndLostReachabilityRequiresApproach() {
        var work = new MahoganyHomesScript.Work(4, 40175, npc);
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, work, npc, 40175,
            true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, null, npc, 40175,
            true, true, Set.of(npc)));
        var mapped = new MahoganyHomesScript.Work(4, 40175, npc, npc);
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, mapped, npc, 40175,
            true, true, null));
        assertTrue(MahoganyHomesScript.furnitureHandoffReady(player, npc, mapped, npc, 40175,
            true, true, Set.of(npc)));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(player, npc, mapped, npc, 40175,
            true, true, Set.of(player)));
    }
}
