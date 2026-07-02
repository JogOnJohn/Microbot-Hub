package net.runelite.client.plugins.microbot.housetab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import net.runelite.client.plugins.microbot.housetab.enums.TabletQuantityMode;

@ConfigGroup(HouseTabConfig.GROUP)
public interface HouseTabConfig extends Config {

    String GROUP = "HouseTab";

    @ConfigItem(
            keyName = "Progressive",
            name = "Progressive",
            description = "Automatically make the highest Magic XP teleport tablet for your current Magic level.",
            position = 0
    )
    default boolean progressive()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Tablet",
            name = "Tablet",
            description = "Choose the tablet to make when Progressive is disabled.",
            position = 1
    )
    default HouseTablet tablet()
    {
        return HouseTablet.TELEPORT_TO_HOUSE;
    }

    @ConfigItem(
            keyName = "QuantityMode",
            name = "Quantity",
            description = "Make all available tablets or stop after making one tablet.",
            position = 2
    )
    default TabletQuantityMode quantityMode()
    {
        return TabletQuantityMode.MAKE_ALL;
    }

    @ConfigItem(
            keyName = "UseCombinationStaff",
            name = "Use combination staff",
            description = "Prefer a staff that supplies the selected tablet's elemental runes.",
            position = 3
    )
    default boolean useCombinationStaff()
    {
        return true;
    }

    @ConfigItem(
            keyName = "AllowRuneFallback",
            name = "Allow rune fallback",
            description = "Use inventory/rune pouch runes when staff mode is disabled, or for runes not covered by the equipped staff.",
            position = 4
    )
    default boolean allowRuneFallback()
    {
        return true;
    }

    @ConfigItem(
            keyName = "BuyMissingStaff",
            name = "Buy missing staff",
            description = "Reserved for GE buying support. Currently the script stops if the required staff is missing.",
            position = 5
    )
    default boolean buyMissingStaff()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ProgressiveBankTab",
            name = "Progressive bank tab",
            description = "Bank tab to open before progressive staff setup. 0 is the main tab; 1 is bank tab 1.",
            position = 6
    )
    default int progressiveBankTab()
    {
        return 1;
    }

    @ConfigItem(
            keyName = "TargetWorld",
            name = "Target world",
            description = "World required before starting. Use 0 to disable the world check.",
            position = 7
    )
    default int targetWorld()
    {
        return 330;
    }

    @ConfigItem(
            keyName = "DebugWidgetDump",
            name = "Debug widget dump",
            description = "Log the tablet creation interface widget children once when the lectern interface opens.",
            position = 8
    )
    default boolean debugWidgetDump()
    {
        return false;
    }

    @ConfigItem(
            keyName = "DebugDiagnostics",
            name = "Debug diagnostics",
            description = "Log state snapshots, key object visibility, and material summaries for live debugging.",
            position = 9
    )
    default boolean debugDiagnostics()
    {
        return false;
    }

    @ConfigItem(
            keyName = "UseLastHouse",
            name = "Use last house",
            description = "Use the portal's last friend house behavior when possible, falling back to Player Name if prompted.",
            position = 10
    )
    default boolean useLastHouse()
    {
        return true;
    }

    @ConfigItem(
            keyName = "UseAdvertisementBoard",
            name = "Use advertisement board",
            description = "Try the Rimmington house advertisement board before using the portal friend-house flow.",
            position = 11
    )
    default boolean useAdvertisementBoard()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Advertised Houses",
            name = "Advertised houses",
            description = "Optional comma-separated advertised house names to prefer. If none match, the first listed house is used.",
            position = 12
    )
    default String advertisedHouses()
    {
        return "";
    }

    @ConfigItem(
            keyName = "OwnHouse",
            name = "Own house",
            description = "Use your own house",
            position = 13
    )
    default boolean ownHouse()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Player Name",
            name = "Player Name",
            description = "Fallback friend house name to type when the portal asks for a name.",
            position = 14
    )
    default String housePlayerName()
    {
        return "";
    }
}
