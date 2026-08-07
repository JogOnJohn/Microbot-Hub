package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(BlackjackConfig.GROUP)
public interface BlackjackConfig extends Config
{
    String GROUP = "blackjack";

    @ConfigSection(
            name = "Setup",
            description = "Blackjack setup requirements",
            position = 0,
            closedByDefault = false
    )
    String setupSection = "setup";

    @ConfigSection(
            name = "Supplies",
            description = "Healing and restocking",
            position = 1,
            closedByDefault = false
    )
    String suppliesSection = "supplies";

    @ConfigItem(
            keyName = "guide",
            name = "Requirements",
            description = "How to prepare the first-pass blackjack script",
            position = 0,
            section = setupSection
    )
    default String guide()
    {
        return "Requires 45 Thieving, an equipped blackjack, coins, and a pre-lured target in the supported Pollnivneach house. " +
                "The script selects level 41 Bandits until 55, level 56 Bandits until 65, then Menaphite Thugs. " +
                "Keep attack options hidden. Jug of wine healing and the nearby note exchange/shop restock route are supported.";
    }

    @Range(min = 10, max = 90)
    @ConfigItem(
            keyName = "healBelowPercent",
            name = "Heal below",
            description = "Start drinking wine below this hitpoints percentage",
            position = 0,
            section = suppliesSection
    )
    default int healBelowPercent()
    {
        return 40;
    }

    @Range(min = 20, max = 100)
    @ConfigItem(
            keyName = "healToPercent",
            name = "Heal to",
            description = "Stop drinking wine at this hitpoints percentage",
            position = 1,
            section = suppliesSection
    )
    default int healToPercent()
    {
        return 75;
    }

    @ConfigItem(
            keyName = "autoRestockWine",
            name = "Auto-restock wine",
            description = "Exchange noted wines first, otherwise buy wines from Faisal when possible",
            position = 2,
            section = suppliesSection
    )
    default boolean autoRestockWine()
    {
        return true;
    }
}
