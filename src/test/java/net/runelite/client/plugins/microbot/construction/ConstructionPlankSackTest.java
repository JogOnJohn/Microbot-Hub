package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionPlankSackTest {

    @Test
    void availableIncludesMatchingStoredPlanksOnlyWhenCarried() {
        assertEquals(31, ConstructionPlankSack.available(3, 28, true));
        assertEquals(3, ConstructionPlankSack.available(3, 28, false));
    }

    @Test
    void fillsOnlyWhenCarriedWithLoosePlanksAndCapacity() {
        assertTrue(ConstructionPlankSack.shouldFill(10, 18, true));
        assertFalse(ConstructionPlankSack.shouldFill(0, 18, true));
        assertFalse(ConstructionPlankSack.shouldFill(10, 28, true));
        assertFalse(ConstructionPlankSack.shouldFill(10, 18, false));
    }

    @Test
    void mapsSupportedModesToTheirPlankTypeVarbits() {
        assertEquals(VarbitID.PLANK_SACK_OAK, ConstructionPlankSack.varbitFor(
                ConstructionConfig.ConstructionMode.OAK_LARDER));
        assertEquals(VarbitID.PLANK_SACK_OAK, ConstructionPlankSack.varbitFor(
                ConstructionConfig.ConstructionMode.OAK_DUNGEON_DOOR));
        assertEquals(VarbitID.PLANK_SACK_MAHOGANY, ConstructionPlankSack.varbitFor(
                ConstructionConfig.ConstructionMode.MAHOGANY_TABLE));
    }
}
