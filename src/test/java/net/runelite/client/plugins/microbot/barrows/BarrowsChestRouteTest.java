package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BarrowsChestRouteTest {
    private static final WorldPoint CHEST = new WorldPoint(3551, 9694, 0);

    @Test void acceptsAPathEndingNearTheChest() {
        List<WorldPoint> path = Arrays.asList(new WorldPoint(3551, 9685, 0), CHEST);
        assertEquals(path, BarrowsScript.chestRoutePath(path, null));
    }

    @Test void retainsTheChestTargetForAPartialRoute() {
        List<WorldPoint> path = Arrays.asList(new WorldPoint(3551, 9600, 0), new WorldPoint(3551, 9610, 0));
        assertEquals(path, BarrowsScript.chestRoutePath(path, Collections.singleton(CHEST)));
    }

    @Test void rejectsAnUnrelatedActiveRoute() {
        List<WorldPoint> path = Arrays.asList(new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0));
        assertTrue(BarrowsScript.chestRoutePath(path, Collections.singleton(path.get(1))).isEmpty());
    }

    @Test void rejectsMissingOrSingleTileRoutes() {
        assertTrue(BarrowsScript.chestRoutePath(null, Collections.singleton(CHEST)).isEmpty());
        assertTrue(BarrowsScript.chestRoutePath(Collections.singletonList(CHEST), null).isEmpty());
    }

    @Test void rejectsADifferentPlaneAndNullEndpoint() {
        assertTrue(BarrowsScript.chestRoutePath(Arrays.asList(CHEST, new WorldPoint(3551, 9694, 1)), null).isEmpty());
        assertTrue(BarrowsScript.chestRoutePath(Arrays.asList(CHEST, null), Collections.singleton(null)).isEmpty());
    }

    @Test void snapshotsTheActiveRoute() {
        List<WorldPoint> path = new ArrayList<>(Arrays.asList(new WorldPoint(3551, 9693, 0), CHEST));
        List<WorldPoint> snapshot = BarrowsScript.chestRoutePath(path, null);
        path.clear();
        assertEquals(2, snapshot.size());
    }
}
