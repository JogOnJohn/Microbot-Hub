package net.runelite.client.plugins.microbot.drokbd;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroKbdInventorySetupTest {
    @Test void preservesAttackOptionAndSetupPreferences() {
        InventorySetup source = setup("Always right-click");
        InventorySetup copy = DroKbdScript.sanitizeTripSetup(source);
        assertEquals(source.getAttackOption(), copy.getAttackOption());
        assertEquals(source.getName(), copy.getName());
        assertEquals(source.getNotes(), copy.getNotes());
        assertEquals(source.getSpellBook(), copy.getSpellBook());
        assertEquals(source.getIconID(), copy.getIconID());
        assertEquals(source.isFilterBank(), copy.isFilterBank());
        assertNotSame(source.getInventory(), copy.getInventory());
        assertNotSame(source.getEquipment(), copy.getEquipment());
    }

    @Test void retainsAnUnsetAttackOptionAndAbsentPouches() {
        InventorySetup copy = DroKbdScript.sanitizeTripSetup(setup(null));
        assertNull(copy.getAttackOption());
        assertNull(copy.getRune_pouch());
        assertNull(copy.getBoltPouch());
        assertNull(copy.getQuiver());
    }

    private static InventorySetup setup(String attackOption) {
        return new InventorySetup(new ArrayList<>(), new ArrayList<>(), null, null, null,
                Collections.emptyMap(), "KBD", "Keep saved loadout", Color.RED, true,
                Color.BLUE, true, false, 2, true, 11235, attackOption);
    }
}
