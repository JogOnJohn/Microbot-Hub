package net.runelite.client.plugins.microbot.microhunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("AutoHunter")
@ConfigInformation("Red-chinchompa box traps only. Builds and maintains a compact five-dot trap layout.")
public interface AutoHunterConfig extends Config {
    @ConfigItem(
            position = 1,
            keyName = "huntingRadius",
            name = "Hunting radius",
            description = "Maximum distance from the start tile for traps and spawn candidates"
    )
    @Range(min = 2, max = 12)
    default int huntingRadius() {
        return 6;
    }

    @ConfigItem(
            position = 2,
            keyName = "useSpawnRing",
            name = "Center on best spawn",
            description = "Center the five-dot layout on the best observed red-chinchompa spawn tile"
    )
    default boolean useSpawnRing() {
        return false;
    }

    @ConfigItem(
            position = 3,
            keyName = "humanizerEnabled",
            name = "Humanizer",
            description = "Use short varied reaction delays and occasional idle mouse wandering"
    )
    default boolean humanizerEnabled() {
        return true;
    }
}
