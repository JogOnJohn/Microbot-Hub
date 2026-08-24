package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoHunterPlannerTest {
    @Test
    void derivesNormalBoxTrapLimit() {
        assertEquals(1, AutoHunterPlanner.normalBoxTrapLimit(19));
        assertEquals(2, AutoHunterPlanner.normalBoxTrapLimit(20));
        assertEquals(3, AutoHunterPlanner.normalBoxTrapLimit(40));
        assertEquals(4, AutoHunterPlanner.normalBoxTrapLimit(64));
        assertEquals(5, AutoHunterPlanner.normalBoxTrapLimit(80));
    }

    @Test
    void classifiesTrapActionsWithoutObjectIds() {
        assertEquals(AutoHunterPlanner.TrapState.CAUGHT,
                AutoHunterPlanner.classifyActions(new String[]{"Check", null, "Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.FAILED,
                AutoHunterPlanner.classifyActions(new String[]{"Reset", "Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.ACTIVE,
                AutoHunterPlanner.classifyActions(new String[]{"Dismantle"}));
        assertEquals(AutoHunterPlanner.TrapState.UNKNOWN,
                AutoHunterPlanner.classifyActions(new String[]{"Examine"}));
    }

    @Test
    void producesEightUniqueRingTiles() {
        WorldPoint center = new WorldPoint(3200, 3200, 0);
        assertEquals(8, AutoHunterPlanner.ring(center).stream().distinct().count());
        assertTrue(AutoHunterPlanner.ring(center).stream().noneMatch(center::equals));
    }

    @Test
    void repeatedFreshSpawnsOutrankOldDistantSpawns() {
        assertTrue(AutoHunterPlanner.spawnScore(3, 1_000, 2)
                > AutoHunterPlanner.spawnScore(2, 500_000, 10));
    }
}
