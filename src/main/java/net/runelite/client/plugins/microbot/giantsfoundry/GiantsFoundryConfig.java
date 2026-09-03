package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.AlloyStrategy;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.CoolingMethod;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.FoundryShopStrategy;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;

@ConfigGroup(GiantsFoundryConfig.GROUP)
@ConfigInformation(
        "Start inside Giants' Foundry after completing Sleeping Giants.<br />" +
        "Auto modes lock one alloy per sword. Best available uses an even 14/14 split; economical avoids rune and uses 18 mithril/10 adamant from level 70.<br />" +
        "The plugin stops cleanly rather than substituting metals when the current strategy runs out of supplies.<br />" +
        "Start with empty weapon and shield slots. Ice gloves are the default; a bucket or Smiths gloves (i) is also supported.<br />" +
        "Manual materials must provide exactly 28 bars. Recycled item amounts are bar equivalents, not item counts.<br />" +
        "Optional shop automation buys usable moulds in level order, then the Smiths outfit; consumables and other rewards are not bought."
)
public interface GiantsFoundryConfig extends Config
{
    String GROUP = "GiantsFoundry";

    @ConfigSection(
            name = "Materials",
            description = "Alloy and material settings",
            position = 1
    )
    String materialSection = "materialSection";

    @ConfigSection(
            name = "Requirements",
            description = "Preform retrieval requirements",
            position = 2
    )
    String requirementSection = "requirementSection";

    @ConfigSection(
            name = "Reward shop",
            description = "Automatic Foundry reputation purchases between swords",
            position = 3
    )
    String rewardSection = "rewardSection";

    @ConfigItem(
            keyName = "alloyStrategy",
            name = "Alloy strategy",
            description = "Automatically choose an alloy or use a validated manual setup",
            position = 0,
            section = materialSection
    )
    default AlloyStrategy alloyStrategy()
    {
        return AlloyStrategy.AUTO_BEST;
    }

    @ConfigItem(
            keyName = "FirstBar",
            name = "First metal",
            description = "First metal for manual bars",
            position = 1,
            section = materialSection
    )
    default SmithableBars FirstBar()
    {
        return SmithableBars.IRON_BAR;
    }

    @ConfigItem(
            keyName = "SecondBars",
            name = "Second metal",
            description = "Second metal for manual bars",
            position = 2,
            section = materialSection
    )
    default SmithableBars SecondBar()
    {
        return SmithableBars.STEEL_BAR;
    }

    @Range(min = 1, max = 27)
    @ConfigItem(
            keyName = "firstBarAmount",
            name = "First contribution",
            description = "Bars, or returned-bar equivalents for recycled items",
            position = 3,
            section = materialSection
    )
    default int firstBarAmount()
    {
        return 14;
    }

    @Range(min = 1, max = 27)
    @ConfigItem(
            keyName = "secondBarAmount",
            name = "Second contribution",
            description = "Both contributions must total exactly 28",
            position = 4,
            section = materialSection
    )
    default int secondBarAmount()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "firstItem",
            name = "First recycled item",
            description = "Full item name, used only by Manual recycled items",
            position = 5,
            section = materialSection
    )
    default String firstItem()
    {
        return "";
    }

    @ConfigItem(
            keyName = "secondItem",
            name = "Second recycled item",
            description = "Full item name, used only by Manual recycled items",
            position = 6,
            section = materialSection
    )
    default String secondItem()
    {
        return "";
    }

    @ConfigItem(
            keyName = "coolingMethod",
            name = "Cooling item",
            description = "Item used to collect a poured preform",
            position = 0,
            section = requirementSection
    )
    default CoolingMethod coolingMethod()
    {
        return CoolingMethod.ICE_GLOVES;
    }

    @ConfigItem(
            keyName = "shopStrategy",
            name = "Automatic purchases",
            description = "Buy level-usable moulds first, optionally followed by the Smiths outfit",
            position = 0,
            section = rewardSection
    )
    default FoundryShopStrategy shopStrategy()
    {
        return FoundryShopStrategy.MOULDS_THEN_OUTFIT;
    }
}
