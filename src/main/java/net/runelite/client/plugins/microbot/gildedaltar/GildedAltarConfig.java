package net.runelite.client.plugins.microbot.gildedaltar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("GildedAltar")
public interface GildedAltarConfig extends Config {

    @ConfigItem(
            keyName = "Guide",
            name = "How to use",
            description = "How to use the script",
            position = 0
    )
    default String GUIDE() {
        return "This only supports house advertisements. Use this script in w330";
    }

    @ConfigItem(
            keyName = "oneTickOffering",
            name = "One-tick offering",
            description = "Attempt to manually offer one bone every game tick. Takes precedence over Random lazy.",
            position = 1
    )
    default boolean oneTickOffering() {
        return false;
    }

    @ConfigItem(
            keyName = "randomLazyOffering",
            name = "Random lazy",
            description = "Use short one-tick bursts, then fall back to normal automatic offering for a while.",
            position = 2
    )
    default boolean randomLazyOffering() {
        return false;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "randomLazySpeedBoost",
            name = "Lazy speed boost %",
            description = "Approximate speed increase targeted by Random lazy. Burst timing is randomized per inventory.",
            position = 3
    )
    default int randomLazySpeedBoost() {
        return 20;
    }
}
