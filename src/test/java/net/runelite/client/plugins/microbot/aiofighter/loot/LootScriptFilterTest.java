package net.runelite.client.plugins.microbot.aiofighter.loot;

import java.lang.reflect.Method;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LootScriptFilterTest {
    private final AIOFighterConfig config = mock(AIOFighterConfig.class);
    private final GroundItem item = mock(GroundItem.class);

    private boolean filter(String name) throws Exception {
        Method method = LootScript.class.getDeclaredMethod(name, GroundItem.class, AIOFighterConfig.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, item, config);
    }

    @Test
    void worldSpawnFilterRespectsToggleAndOwnership() throws Exception {
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
        assertFalse(filter("isSkippedWorldSpawn"));
        when(config.toggleSkipWorldSpawns()).thenReturn(true);
        assertTrue(filter("isSkippedWorldSpawn"));
        when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);
        assertFalse(filter("isSkippedWorldSpawn"));
    }

    @Test
    void unsetCenterDoesNotRestrictLoot() throws Exception {
        assertFalse(filter("isOutsideFightArea"));
        when(config.centerLocation()).thenReturn(new WorldPoint(0, 0, 0));
        assertFalse(filter("isOutsideFightArea"));
    }

    @Test
    void radiusIncludesBoundaryButRejectsOutsideAndOtherPlane() throws Exception {
        when(config.centerLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(config.attackRadius()).thenReturn(5);
        when(item.getLocation()).thenReturn(new WorldPoint(3205, 3200, 0));
        assertFalse(filter("isOutsideFightArea"));
        when(item.getLocation()).thenReturn(new WorldPoint(3206, 3200, 0));
        assertTrue(filter("isOutsideFightArea"));
        when(item.getLocation()).thenReturn(new WorldPoint(3200, 3200, 1));
        assertTrue(filter("isOutsideFightArea"));
    }

    @Test
    void missingLootLocationIsRejectedWhenCenterIsSet() throws Exception {
        when(config.centerLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        assertTrue(filter("isOutsideFightArea"));
    }
}
