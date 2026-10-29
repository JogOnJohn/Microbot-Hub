package net.runelite.client.plugins.microbot.housetab;

import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseTabPlannerTest {
    @Test
    void progressiveSelectionSortsByXpAndSkipsWatchtower() {
        assertEquals(HouseTablet.VARROCK_TELEPORT, HouseTabPlanner.resolveTablet(true, HouseTablet.TELEPORT_TO_HOUSE, 25));
        assertEquals(HouseTablet.CIVITAS_ILLA_FORTIS_TELEPORT, HouseTabPlanner.resolveTablet(true, HouseTablet.TELEPORT_TO_HOUSE, 58));
        assertEquals(HouseTablet.TELEPORT_TO_BOAT, HouseTabPlanner.resolveTablet(true, HouseTablet.TELEPORT_TO_HOUSE, 67));
    }

    @Test
    void nonProgressiveUsesConfiguredTablet() {
        assertEquals(HouseTablet.ARDOUGNE_TELEPORT, HouseTabPlanner.resolveTablet(false, HouseTablet.ARDOUGNE_TELEPORT, 99));
    }

    @Test
    void liveLecternIdsSupportExpectedTabletFamilies() {
        assertTrue(HouseTablet.TELEPORT_TO_BOAT.supportsLectern(37349));
        assertTrue(HouseTablet.TELEPORT_TO_BOAT.supportsLectern(13647));
        assertFalse(HouseTablet.TELEPORT_TO_BOAT.supportsLectern(13648));
        assertTrue(HouseTablet.TELEPORT_TO_HOUSE.supportsLectern(13648));
    }

    @Test
    void plannerFlagsMissingMaterials() {
        HouseTabSnapshot ready = snapshot(true, true, true);
        assertFalse(HouseTabPlanner.needsBankPrep(ready, true));
        assertEquals("", HouseTabPlanner.missingMaterials(ready, true));

        HouseTabSnapshot noClay = snapshot(false, true, true);
        assertTrue(HouseTabPlanner.needsBankPrep(noClay, true));
        assertEquals("Missing soft clay", HouseTabPlanner.missingMaterials(noClay, true));

        HouseTabSnapshot noRunes = snapshot(true, false, true);
        assertTrue(HouseTabPlanner.needsBankPrep(noRunes, true));
        assertTrue(HouseTabPlanner.missingMaterials(noRunes, true).startsWith("Missing runes:"));

        HouseTabSnapshot noStaff = snapshot(true, true, false);
        assertTrue(HouseTabPlanner.needsBankPrep(noStaff, true));
        assertEquals("Missing staff for Teleport to Boat", HouseTabPlanner.missingMaterials(noStaff, true));
        assertFalse(HouseTabPlanner.needsBankPrep(noStaff, false));
    }

    private HouseTabSnapshot snapshot(boolean hasClay, boolean hasRunes, boolean hasStaff) {
        return new HouseTabSnapshot(
                true,
                true,
                330,
                null,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                hasClay,
                false,
                hasClay ? 26 : 0,
                0,
                hasClay,
                hasRunes,
                hasStaff,
                HouseTablet.TELEPORT_TO_BOAT);
    }
}
