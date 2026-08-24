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

    @ConfigSection(
            name = "Humanizer randomizer",
            description = "Bounded timing, mouse, mistake, and break variation",
            position = 2,
            closedByDefault = false
    )
    String humanizerSection = "humanizer";

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
                "Choose the pre-lured target below. Automatic mode selects level 41 Bandits until 55, level 56 Bandits until 70, then Menaphite Thugs. " +
                "Keep attack options hidden. The combat reset can temporarily drop and recover one full wine to unequip the blackjack. " +
                "For automatic restocking, carry noted wine and coins. The script isolates the target " +
                "behind the east door before clearing empty jugs and exchanging notes with the nearby merchant.";
    }

    @ConfigItem(
            keyName = "target",
            name = "Pickpocket target",
            description = "Target that has been pre-lured into the marked house",
            position = 1,
            section = setupSection
    )
    default BlackjackTarget target()
    {
        return BlackjackTarget.AUTO;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "healBelowPercent",
            name = "Heal below % HP",
            description = "Start drinking wine below this Hitpoints level",
            position = 0,
            section = suppliesSection
    )
    default int healBelowPercent()
    {
        return 40;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "healToPercent",
            name = "Heal to HP",
            description = "Keep drinking wine until this Hitpoints level is reached",
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
            description = "Leave before the remaining wines can no longer reach Heal to HP, then exchange noted wines or use Faisal",
            position = 2,
            section = suppliesSection
    )
    default boolean autoRestockWine()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dropWineForDisarmedReset",
            name = "Drop wine for combat reset",
            description = "Temporarily drop one full wine when the inventory is full so the blackjack can be unequipped, then recover it after re-equipping",
            position = 3,
            section = suppliesSection
    )
    default boolean dropWineForDisarmedReset()
    {
        return true;
    }

    @ConfigItem(
            keyName = "humanizerEnabled",
            name = "Enable humanizer",
            description = "Enable bounded timing variation, mouse recovery, menu mistakes, and scheduled breaks",
            position = 0,
            section = humanizerSection
    )
    default boolean humanizerEnabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "randomMouseRecovery",
            name = "Mouse wander and recover",
            description = "Occasionally move away from the target and naturally recover before continuing",
            position = 1,
            section = humanizerSection
    )
    default boolean randomMouseRecovery()
    {
        return true;
    }

    @ConfigItem(
            keyName = "randomMenuMistakes",
            name = "Random menu mistakes",
            description = "Rarely select another target option and recover before resuming",
            position = 2,
            section = humanizerSection
    )
    default boolean randomMenuMistakes()
    {
        return true;
    }

    @ConfigItem(
            keyName = "includeLureMistakes",
            name = "Include Lure mistakes",
            description = "Allow very rare Lure selections; this can move the pre-lured target",
            position = 3,
            section = humanizerSection
    )
    default boolean includeLureMistakes()
    {
        return true;
    }

    @ConfigItem(
            keyName = "humanizerBreaks",
            name = "Random breaks",
            description = "Take approximately 30-second breaks around every 10 minutes and 1-2 minute breaks every 20-30 minutes",
            position = 4,
            section = humanizerSection
    )
    default boolean humanizerBreaks()
    {
        return true;
    }
}
