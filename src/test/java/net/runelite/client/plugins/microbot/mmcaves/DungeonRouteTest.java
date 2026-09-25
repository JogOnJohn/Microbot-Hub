package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mmcaves.enums.DungeonRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonRouteTest {
    @Test
    void holeTwoMatchesRecordedEntranceAndCheckTile() {
        DungeonRoute route = DungeonRoute.HOLE_2;
        assertTrue(route.isMapped());
        assertEquals(28772, route.holeId());
        assertEquals(new WorldPoint(2509, 9173, 1), route.waypoints().get(0));
        assertEquals(new WorldPoint(2572, 9168, 1), route.checkTile());
        assertTrue(route.waypoints().size() > 10);
        for (int i = 1; i < route.waypoints().size(); i++) {
            assertTrue(route.waypoints().get(i - 1).distanceTo(route.waypoints().get(i)) <= 13,
                    "Waypoint gap " + i + " exceeds the nearby-click range");
        }
    }

    @Test
    void otherHolesCannotNavigateWithoutRecordedPaths() {
        for (DungeonRoute route : DungeonRoute.values()) {
            if (route == DungeonRoute.HOLE_2) continue;
            assertFalse(route.isMapped());
            assertTrue(route.waypoints().isEmpty());
        }
    }
}
