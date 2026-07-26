package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("pestcontrol")
@ConfigInformation("Start near a boat of your combat level")
public interface PestControlConfig extends Config {
    @ConfigItem(
            keyName = "Alch in boat",
            name = "Alch while waiting",
            description = "Alch while waiting",
            position = 2
    )
    default boolean alchInBoat() {
        return false;
    }

    @ConfigItem(
            keyName = "itemToAlch",
            name = "Item to alch",
            description = "Item to alch",
            position = 3
    )
    default String alchItem() {
        return "";
    }

    @ConfigItem(
            keyName = "QuickPrayer",
            name = "Enable QuickPrayer",
            description = "Enables quick prayer",
            position = 4
    )
    default boolean quickPrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "World",
            name = "World",
            description = "Pest Control world",
            position = 5
    )

    default int world() {
        return 344;
    }

    @ConfigItem(
            keyName = "primaryCombatStyle",
            name = "Primary combat style",
            description = "Combat style used whenever a portal weapon is set to None",
            position = 6
    )
    default PestControlCombatStyle primaryCombatStyle() {
        return PestControlCombatStyle.RANGED;
    }

    @ConfigItem(
            keyName = "rangedWeapon",
            name = "Ranged weapon (purple)",
            description = "Exact weapon name for the ranged-weak purple portal, or None to use the primary weapon",
            position = 7
    )
    default String rangedWeapon() {
        return "Adamant crossbow";
    }

    @ConfigItem(
            keyName = "usePurpleSpecialAttack",
            name = "Use special (purple)",
            description = "Use the equipped weapon's special attack while attacking the purple portal",
            position = 8
    )
    default boolean usePurpleSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "magicWeapon",
            name = "Magic weapon (blue)",
            description = "Exact weapon name for the magic-weak blue portal, or None to use the primary weapon",
            position = 9
    )
    default String magicWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "useBlueSpecialAttack",
            name = "Use special (blue)",
            description = "Use the equipped weapon's special attack while attacking the blue portal",
            position = 10
    )
    default boolean useBlueSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "slashStabWeapon",
            name = "Slash/stab weapon (yellow)",
            description = "Exact weapon name for the slash/stab-weak yellow portal, or None to use the primary weapon",
            position = 11
    )
    default String slashStabWeapon() {
        return "Dragon scimitar";
    }

    @ConfigItem(
            keyName = "useYellowSpecialAttack",
            name = "Use special (yellow)",
            description = "Use the equipped weapon's special attack while attacking the yellow portal",
            position = 12
    )
    default boolean useYellowSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "crushWeapon",
            name = "Crush weapon (red)",
            description = "Exact weapon name for the crush-weak red portal, or None to use the primary weapon",
            position = 13
    )
    default String crushWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "useRedSpecialAttack",
            name = "Use special (red)",
            description = "Use the equipped weapon's special attack while attacking the red portal",
            position = 14
    )
    default boolean useRedSpecialAttack() {
        return false;
    }

    @Range(
            min = 0,
            max = 100
    )
    @ConfigItem(
            keyName = "rangedOpeningWeight",
            name = "Ranged-side opening weight",
            description = "Chance to stage near the ranged-weak purple portal at the start of each round; other sides share the remainder",
            position = 15
    )
    default int rangedOpeningWeight() {
        return 55;
    }
}
