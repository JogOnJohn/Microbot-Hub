package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.Set;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.SimplePolygon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesHandoffTest {
    @Test void jeffBookcaseUsesInteriorAccessInsteadOfRetryingFromOutsideEastWall() {
        var bookcase = MahoganyHomesContractData.JEFF.work.stream()
            .filter(work -> work.objectId == 39990).findFirst().orElseThrow();
        var outside = new WorldPoint(3243, 3454, 0);
        var northRoom = new WorldPoint(3242, 3452, 0);
        assertEquals(northRoom, bookcase.access);
        assertTrue(MahoganyHomesScript.needsWorkAccess(outside, bookcase, 0, false));
        assertTrue(MahoganyHomesScript.needsWorkAccess(outside, bookcase, 1, true));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(outside, northRoom, bookcase,
            bookcase.location, bookcase.objectId, true, true, Set.of(outside)));
        assertFalse(MahoganyHomesScript.needsWorkAccess(northRoom, bookcase, 1, true));
    }

    @Test void barbaraBedRetryReturnsInsideInsteadOfClickingFromTheNorthWall() {
        var bed = MahoganyHomesContractData.BARBARA.work.stream()
            .filter(work -> work.objectId == 40013).findFirst().orElseThrow();
        var outside = new WorldPoint(1748, 3540, 0);
        var inside = new WorldPoint(1748, 3537, 0);
        assertEquals(inside, bed.access);
        assertTrue(MahoganyHomesScript.needsWorkAccess(outside, bed, 0, false));
        assertTrue(MahoganyHomesScript.needsWorkAccess(outside, bed, 1, true));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(outside, inside, bed,
            bed.location, bed.objectId, true, true, Set.of(outside)));
        assertFalse(MahoganyHomesScript.needsWorkAccess(inside, bed, 1, true));
    }

    private final WorldPoint player = new WorldPoint(3000, 3300, 0);
    private final WorldPoint npc = new WorldPoint(3003, 3300, 0);

    @Test void jeffTableRequiresNorthRoomAccessBeforeClickingThroughTheDoor() {
        var table = MahoganyHomesContractData.JEFF.work.stream()
            .filter(work -> work.objectId == 39989).findFirst().orElseThrow();
        var southRoom = new WorldPoint(3238, 3449, 0);
        var northRoom = new WorldPoint(3242, 3452, 0);
        assertEquals(northRoom, table.access);
        assertTrue(MahoganyHomesScript.needsWorkAccess(southRoom, table, 0, false));
        assertTrue(MahoganyHomesScript.needsWorkAccess(southRoom, table, 1, true));
        assertFalse(MahoganyHomesScript.furnitureHandoffReady(southRoom, northRoom, table,
            table.location, table.objectId, true, true, Set.of(southRoom)));
        assertTrue(MahoganyHomesScript.furnitureHandoffReady(northRoom, northRoom, table,
            table.location, table.objectId, true, true, Set.of(northRoom)));
    }

    @Test void barbaraContainedSimplePolygonMustCountAsClickable() {
        var hull = new SimplePolygon(new int[]{808, 838, 838, 808},
            new int[]{446, 446, 508, 508}, 4);
        var viewport = new Rectangle(0, 0, 1635, 857);
        assertFalse(hull.intersects(viewport), "Reproduce RuneLite's contained-shape intersection bug");
        assertTrue(MahoganyHomesScript.clickboxVisible(hull, viewport));
        WorldPoint outsideNpcTile = new WorldPoint(1747, 3539, 0);
        WorldPoint npcTile = new WorldPoint(1747, 3538, 0);
        assertTrue(MahoganyHomesScript.interactionHandoffReady(outsideNpcTile, npcTile, true,
            MahoganyHomesScript.clickboxVisible(hull, viewport), Set.of(outsideNpcTile, npcTile)));
        assertFalse(MahoganyHomesScript.interactionHandoffReady(outsideNpcTile, npcTile, true,
            MahoganyHomesScript.clickboxVisible(hull, viewport), Set.of(outsideNpcTile)),
            "A visible NPC behind a closed crossing must still require travel");
    }

    @Test void clippedAndViewportEnclosingPolygonsAreVisibleButOffscreenShapesAreNot() {
        var viewport = new Rectangle(0, 0, 100, 100);
        var clipped = new SimplePolygon(new int[]{-10, 10, 10, -10}, new int[]{20, 20, 40, 40}, 4);
        var enclosing = new SimplePolygon(new int[]{-10, 110, 110, -10}, new int[]{-10, -10, 110, 110}, 4);
        var offscreen = new SimplePolygon(new int[]{110, 120, 120, 110}, new int[]{20, 20, 40, 40}, 4);
        assertTrue(MahoganyHomesScript.clickboxVisible(clipped, viewport));
        assertTrue(MahoganyHomesScript.clickboxVisible(enclosing, viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(offscreen, viewport));
    }

    @Test void boundingBoxOverlapAloneDoesNotPermitHandoff() {
        var viewport = new Rectangle(0, 0, 100, 100);
        var hull = new SimplePolygon(new int[]{80, 130, 130}, new int[]{130, 80, 130}, 3);
        assertTrue(hull.getBounds().intersects(viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(hull, viewport));
    }

    @Test void missingEmptyAndDegenerateShapesDoNotPermitHandoff() {
        var viewport = new Rectangle(0, 0, 100, 100);
        var line = new SimplePolygon(new int[]{10, 20}, new int[]{10, 20}, 2);
        assertFalse(MahoganyHomesScript.clickboxVisible(null, viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(new Rectangle(10, 10, 20, 20), null));
        assertFalse(MahoganyHomesScript.clickboxVisible(new Path2D.Double(), viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(new Rectangle(10, 10, 0, 10), viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(line, viewport));
        assertFalse(MahoganyHomesScript.clickboxVisible(new Rectangle(10, 10, 10, 10), new Rectangle()));
    }

    @Test void barbaraApproachMustCrossTheWallInsteadOfArrivingOneTileOutside() {
        WorldPoint outside = new WorldPoint(1746, 3538, 0);
        WorldPoint inside = new WorldPoint(1747, 3538, 0);
        WorldPoint homeowner = new WorldPoint(1748, 3538, 0);
        WorldPoint approach = MahoganyHomesScript.homeownerApproach(outside, homeowner,
            Set.of(inside, homeowner, new WorldPoint(1748, 3537, 0)));
        assertEquals(inside, approach);
        assertEquals(1, outside.distanceTo(approach));
        for (var state : new MahoganyHomesScript.State[]{MahoganyHomesScript.State.GO_TO_CONTRACT,
            MahoganyHomesScript.State.TURN_IN_CONTRACT}) {
            assertEquals(0, MahoganyHomesScript.arrivalDistanceFor(state));
            assertTrue(outside.distanceTo(approach) > MahoganyHomesScript.arrivalDistanceFor(state));
            assertFalse(MahoganyHomesScript.interactionHandoffReady(outside, homeowner,
                true, true, Set.of(outside)));
            assertTrue(MahoganyHomesScript.interactionHandoffReady(inside, homeowner,
                true, true, Set.of(inside, homeowner)));
        }
    }

    @Test void nonHomeownerRoutesKeepTheirExistingTolerance() {
        assertEquals(4, MahoganyHomesScript.arrivalDistanceFor(MahoganyHomesScript.State.GET_SETUP));
        assertEquals(4, MahoganyHomesScript.arrivalDistanceFor(MahoganyHomesScript.State.GET_CONTRACT));
        assertEquals(4, MahoganyHomesScript.arrivalDistanceFor(MahoganyHomesScript.State.DO_CONTRACT));
    }

    @Test void homeownerApproachUsesNpcTileWhenNoAdjacentAccessExists() {
        assertEquals(npc, MahoganyHomesScript.homeownerApproach(player, npc, Set.of(npc)));
        assertEquals(npc, MahoganyHomesScript.homeownerApproach(player, npc, Set.of()));
    }

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
