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
        assertEquals(DroKbdScript.attackOptionIfSupported(source),
                DroKbdScript.attackOptionIfSupported(copy));
        boolean supportsAttackOption = java.util.Arrays.stream(InventorySetup.class.getMethods())
                .anyMatch(method -> method.getName().equals("getAttackOption"));
        assertEquals(supportsAttackOption ? "Always right-click" : null,
                DroKbdScript.attackOptionIfSupported(copy));
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
        assertNull(DroKbdScript.attackOptionIfSupported(copy));
        assertNull(copy.getRune_pouch());
        assertNull(copy.getBoltPouch());
        assertNull(copy.getQuiver());
    }

    private static InventorySetup setup(String attackOption) {
        return DroKbdScript.createCompatibleSetup(new Object[] {
                new ArrayList<>(), new ArrayList<>(), null, null, null,
                Collections.emptyMap(), "KBD", "Keep saved loadout", Color.RED, true,
                Color.BLUE, true, false, 2, true, 11235 }, attackOption);
    }
}
