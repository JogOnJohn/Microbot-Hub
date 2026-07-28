package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup("pestcontrol")
@ConfigInformation("Start near a boat of your combat level")
public interface PestControlConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "Travel, prayer, and activity settings",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Combat styles",
            description = "Ordered combat-style selection",
            position = 1
    )
    String combatStylesSection = "combatStylesSection";

    @ConfigSection(
            name = "Ranged loadout",
            description = "Ranged weapon and off-hand",
            position = 2
    )
    String rangedSection = "rangedSection";

    @ConfigSection(
            name = "Magic loadout",
            description = "Magic weapon, off-hand, and casting mode",
            position = 3
    )
    String magicSection = "magicSection";

    @ConfigSection(
            name = "Melee loadouts",
            description = "Independent stab, slash, and crush switches",
            position = 4
    )
    String meleeSection = "meleeSection";

    @ConfigSection(
            name = "Opening strategy",
            description = "Opening-side selection and weights",
            position = 5
    )
    String openingSection = "openingSection";

    @ConfigSection(
            name = "Special attacks",
            description = "Portal-only special attack settings",
            position = 6
    )
    String specialSection = "specialSection";

    @ConfigItem(
            keyName = "Alch in boat",
            name = "Alch while waiting",
            description = "Alch while waiting in the boat",
            position = 0,
            section = generalSection
    )
    default boolean alchInBoat() {
        return false;
    }

    @ConfigItem(
            keyName = "itemToAlch",
            name = "Item to alch",
            description = "Exact item name to alch",
            position = 1,
            section = generalSection
    )
    default String alchItem() {
        return "";
    }

    @ConfigItem(
            keyName = "QuickPrayer",
            name = "Enable QuickPrayer",
            description = "Enable quick prayer at the start of each round",
            position = 2,
            section = generalSection
    )
    default boolean quickPrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "World",
            name = "World",
            description = "Pest Control world",
            position = 3,
            section = generalSection
    )
    default int world() {
        return 344;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "activityRecoveryStart",
            name = "Activity recovery start",
            description = "Acquire a nearby combat target at this activity percentage",
            position = 4,
            section = generalSection
    )
    default int activityRecoveryStart() {
        return 40;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "activityRecoveryTarget",
            name = "Activity recovery target",
            description = "Resume portal strategy after activity recovers to this percentage",
            position = 5,
            section = generalSection
    )
    default int activityRecoveryTarget() {
        return 70;
    }

    @ConfigItem(
            keyName = "primaryCombatStyle",
            name = "Style 1 (main)",
            description = "Mandatory main style and fallback loadout",
            position = 0,
            section = combatStylesSection
    )
    default PestControlCombatStyle primaryCombatStyle() {
        return PestControlCombatStyle.RANGED;
    }

    @ConfigItem(
            keyName = "secondaryCombatStyle",
            name = "Style 2",
            description = "Optional second combat style",
            position = 1,
            section = combatStylesSection
    )
    default PestControlOptionalCombatStyle secondaryCombatStyle() {
        return PestControlOptionalCombatStyle.DISABLED;
    }

    @ConfigItem(
            keyName = "tertiaryCombatStyle",
            name = "Style 3",
            description = "Optional third combat style",
            position = 2,
            section = combatStylesSection
    )
    default PestControlOptionalCombatStyle tertiaryCombatStyle() {
        return PestControlOptionalCombatStyle.DISABLED;
    }

    @ConfigItem(
            keyName = "rangedWeapon",
            name = "Weapon",
            description = "Exact ranged weapon name",
            position = 0,
            section = rangedSection
    )
    default String rangedWeapon() {
        return "Adamant crossbow";
    }

    @ConfigItem(
            keyName = "rangedOffhand",
            name = "Off-hand",
            description = "Exact ranged off-hand name, or None for a two-handed/empty off-hand loadout",
            position = 1,
            section = rangedSection
    )
    default String rangedOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicWeapon",
            name = "Weapon",
            description = "Exact magic weapon name",
            position = 0,
            section = magicSection
    )
    default String magicWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicOffhand",
            name = "Off-hand",
            description = "Exact magic off-hand name, or None for a two-handed/empty off-hand loadout",
            position = 1,
            section = magicSection
    )
    default String magicOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicCastingMode",
            name = "Casting mode",
            description = "Powered staff attacks directly; autocast is prepared once at plugin start",
            position = 2,
            section = magicSection
    )
    default PestControlMagicMode magicCastingMode() {
        return PestControlMagicMode.POWERED_STAFF;
    }

    @ConfigItem(
            keyName = "magicAutocastSpell",
            name = "Autocast spell",
            description = "Spell remembered for the configured magic weapon",
            position = 3,
            section = magicSection
    )
    default Rs2CombatSpells magicAutocastSpell() {
        return Rs2CombatSpells.WIND_STRIKE;
    }

    @ConfigItem(
            keyName = "primaryMeleeStyle",
            name = "Default melee variant",
            description = "Melee variant used for Style 1 and ordinary activity combat",
            position = 0,
            section = meleeSection
    )
    default PestControlMeleeStyle primaryMeleeStyle() {
        return PestControlMeleeStyle.SLASH;
    }

    @ConfigItem(
            keyName = "stabWeapon",
            name = "Stab weapon",
            description = "Exact stab weapon name, or None to fall back to the default melee variant",
            position = 1,
            section = meleeSection
    )
    default String stabWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "stabOffhand",
            name = "Stab off-hand",
            description = "Exact stab off-hand name, or None",
            position = 2,
            section = meleeSection
    )
    default String stabOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "slashStabWeapon",
            name = "Slash weapon",
            description = "Exact slash weapon name; preserves the previous slash/stab configuration key",
            position = 3,
            section = meleeSection
    )
    default String slashStabWeapon() {
        return "Dragon scimitar";
    }

    @ConfigItem(
            keyName = "slashOffhand",
            name = "Slash off-hand",
            description = "Exact slash off-hand name, or None",
            position = 4,
            section = meleeSection
    )
    default String slashOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "crushWeapon",
            name = "Crush weapon",
            description = "Exact crush weapon name, or None to fall back to the default melee variant",
            position = 5,
            section = meleeSection
    )
    default String crushWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "crushOffhand",
            name = "Crush off-hand",
            description = "Exact crush off-hand name, or None",
            position = 6,
            section = meleeSection
    )
    default String crushOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "yellowMeleeStyle",
            name = "Yellow portal variant",
            description = "Melee variant selected for the yellow portal",
            position = 7,
            section = meleeSection
    )
    default PestControlMeleeStyle yellowMeleeStyle() {
        return PestControlMeleeStyle.SLASH;
    }

    @ConfigItem(
            keyName = "redMeleeStyle",
            name = "Red portal variant",
            description = "Melee variant selected for the red portal",
            position = 8,
            section = meleeSection
    )
    default PestControlMeleeStyle redMeleeStyle() {
        return PestControlMeleeStyle.CRUSH;
    }

    @ConfigItem(
            keyName = "openingMode",
            name = "Opening selection",
            description = "Use Style 1, equal random selection, or configured weights",
            position = 0,
            section = openingSection
    )
    default PestControlOpeningMode openingMode() {
        return PestControlOpeningMode.WEIGHTED_RANDOM;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "rangedOpeningWeight",
            name = "Purple (ranged) weight",
            description = "Relative Purple opening weight",
            position = 1,
            section = openingSection
    )
    default int rangedOpeningWeight() {
        return 55;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "magicOpeningWeight",
            name = "Blue (magic) weight",
            description = "Relative Blue opening weight",
            position = 2,
            section = openingSection
    )
    default int magicOpeningWeight() {
        return 23;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "meleeOpeningWeight",
            name = "Yellow (melee) weight",
            description = "Relative Yellow opening weight; Red is never an opening target",
            position = 3,
            section = openingSection
    )
    default int meleeOpeningWeight() {
        return 22;
    }

    @ConfigItem(
            keyName = "usePurpleSpecialAttack",
            name = "Use special (purple)",
            description = "Use the equipped weapon's special attack only while attacking Purple",
            position = 0,
            section = specialSection
    )
    default boolean usePurpleSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useBlueSpecialAttack",
            name = "Use special (blue)",
            description = "Use the equipped weapon's special attack only while attacking Blue",
            position = 1,
            section = specialSection
    )
    default boolean useBlueSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useYellowSpecialAttack",
            name = "Use special (yellow)",
            description = "Use the equipped weapon's special attack only while attacking Yellow",
            position = 2,
            section = specialSection
    )
    default boolean useYellowSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useRedSpecialAttack",
            name = "Use special (red)",
            description = "Use the equipped weapon's special attack only while attacking Red",
            position = 3,
            section = specialSection
    )
    default boolean useRedSpecialAttack() {
        return false;
    }
}
