package net.runelite.client.plugins.microbot.construction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionPipelinePolicyTest {
    @Test
    void collectsOverflowAfterOneBuildForEverySupportedMode() {
        assertEquals(1, ConstructionScript.overflowBuildsBeforeCollection(
                ConstructionConfig.ConstructionMode.OAK_LARDER));
        assertEquals(1, ConstructionScript.overflowBuildsBeforeCollection(
                ConstructionConfig.ConstructionMode.OAK_DUNGEON_DOOR));
        assertEquals(1, ConstructionScript.overflowBuildsBeforeCollection(
                ConstructionConfig.ConstructionMode.MAHOGANY_TABLE));
    }
}
