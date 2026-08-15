package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerbCleaningMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

@ConfigGroup("AutoBankStander")
@ConfigInformation(
    "AIO Bank Standing plugin for various processing activities.<br>" +
    "Use the panel interface to configure options."
)
public interface AutoBankStanderConfig extends Config {

    @ConfigItem(
        keyName = "skill",
        name = "Skill",
        description = "Selected skill",
        hidden = true
    )
    default SkillType skill() {
        return SkillType.MAGIC;
    }

    @ConfigItem(
        keyName = "magicMethod",
        name = "Magic method",
        description = "Selected magic method",
        hidden = true
    )
    default MagicMethod magicMethod() {
        return MagicMethod.ENCHANTING;
    }

    @ConfigItem(
        keyName = "herbloreMode",
        name = "Herblore mode",
        description = "Selected herblore mode",
        hidden = true
    )
    default Mode herbloreMode() {
        return Mode.CLEAN_HERBS;
    }

    @ConfigItem(
        keyName = "boltType",
        name = "Bolt type",
        description = "Selected bolt type for enchanting",
        hidden = true
    )
    default BoltType boltType() {
        return BoltType.SAPPHIRE;
    }

    @ConfigItem(
        keyName = "cleanHerbMode",
        name = "Clean herb mode",
        description = "Selected clean herb mode",
        hidden = true
    )
    default CleanHerbMode cleanHerbMode() {
        return CleanHerbMode.ANY_AND_ALL;
    }

    @ConfigItem(
        keyName = "unfinishedPotionMode",
        name = "Unfinished potion mode",
        description = "Selected unfinished potion mode",
        hidden = true
    )
    default UnfinishedPotionMode unfinishedPotionMode() {
        return UnfinishedPotionMode.ANY_AND_ALL;
    }

    @ConfigItem(
        keyName = "finishedPotion",
        name = "Finished potion",
        description = "Selected finished potion type",
        hidden = true
    )
    default HerblorePotion finishedPotion() {
        return HerblorePotion.ANTIPOISON;
    }

    @ConfigItem(
        keyName = "useAmuletOfChemistry",
        name = "Use amulet of chemistry",
        description = "Whether to use amulet of chemistry",
        hidden = true
    )
    default boolean useAmuletOfChemistry() {
        return false;
    }

    @ConfigItem(
        keyName = "herbCleaningMode",
        name = "Herb cleaning mode",
        description = "Choose default, recorded serpentine, turbo, or random inventory cleaning."
    )
    default HerbCleaningMode herbCleaningMode() {
        return HerbCleaningMode.DEFAULT;
    }

    @ConfigItem(
        keyName = "herbloreTurboLimit",
        name = "Herblore turbo limit",
        description = "Auto-disable turbo after this many herbs cleaned (0 = no limit).",
        hidden = true
    )
    @Range(min = 0, max = 10000)
    default int herbloreTurboLimit() {
        return 0;
    }

    @ConfigItem(
        keyName = "herbloreSleepMin",
        name = "Herblore sleep min (ms)",
        description = "Lower bound for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 30, max = 1000)
    default int herbloreSleepMin() {
        return 60;
    }

    @ConfigItem(
        keyName = "herbloreSleepMax",
        name = "Herblore sleep max (ms)",
        description = "Upper bound for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 100, max = 2000)
    default int herbloreSleepMax() {
        return 300;
    }

    @ConfigItem(
        keyName = "herbloreSleepTarget",
        name = "Herblore sleep target (ms)",
        description = "Target (mean anchor) for Gaussian inter-batch sleep during cleaning.",
        hidden = true
    )
    @Range(min = 50, max = 1500)
    default int herbloreSleepTarget() {
        return 150;
    }

    @ConfigItem(
        keyName = "reverseIngredientChance",
        name = "Reverse ingredient chance",
        description = "Chance to select the secondary ingredient first when starting a potion batch."
    )
    @Range(min = 0, max = 100)
    default int reverseIngredientChance() {
        return 15;
    }

    @ConfigItem(
        keyName = "batchMicroBreakChance",
        name = "Batch micro-break chance",
        description = "Chance to pause briefly after a completed batch, before banking."
    )
    @Range(min = 0, max = 100)
    default int batchMicroBreakChance() {
        return 8;
    }

    @ConfigItem(
        keyName = "batchMicroBreakMinMs",
        name = "Micro-break minimum (ms)",
        description = "Shortest batch-boundary micro-break.",
        hidden = true
    )
    @Range(min = 250, max = 10000)
    default int batchMicroBreakMinMs() {
        return 700;
    }

    @ConfigItem(
        keyName = "batchMicroBreakMaxMs",
        name = "Micro-break maximum (ms)",
        description = "Longest batch-boundary micro-break.",
        hidden = true
    )
    @Range(min = 250, max = 15000)
    default int batchMicroBreakMaxMs() {
        return 1800;
    }

    @ConfigItem(keyName = "continuousQuantity", name = "Cycle quantity",
            description = "Potion operations per continuous cycle.", hidden = true)
    @Range(min = 1, max = 10000)
    default int continuousQuantity() { return 100; }

    @ConfigItem(keyName = "continuousCapitalReserve", name = "Capital reserve",
            description = "Coins that continuous mode must leave unspent.", hidden = true)
    @Range(min = 0, max = 2000000000)
    default int continuousCapitalReserve() { return 1000000; }

    @ConfigItem(keyName = "continuousMaxBuyPrice", name = "Maximum buy price",
            description = "Hard per-item GE buy ceiling.", hidden = true)
    @Range(min = 1, max = 2000000000)
    default int continuousMaxBuyPrice() { return 100000; }

    @ConfigItem(keyName = "continuousMinSellPrice", name = "Minimum sell price",
            description = "Hard per-item GE sell floor.", hidden = true)
    @Range(min = 1, max = 2000000000)
    default int continuousMinSellPrice() { return 1; }

    @ConfigItem(keyName = "continuousRetryLimit", name = "Phase retry limit",
            description = "Retries before a graceful stop.", hidden = true)
    @Range(min = 0, max = 10)
    default int continuousRetryLimit() { return 2; }

    @ConfigItem(keyName = "continuousPhaseTimeoutSeconds", name = "Phase timeout",
            description = "Timeout per restartable phase in seconds.", hidden = true)
    @Range(min = 15, max = 3600)
    default int continuousPhaseTimeoutSeconds() { return 180; }

    @ConfigItem(keyName = "continuousStopLoss", name = "Stop loss",
            description = "Maximum unrecovered spend before stopping (0 disables).", hidden = true)
    @Range(min = 0, max = 2000000000)
    default int continuousStopLoss() { return 1000000; }

    @ConfigItem(keyName = "continuousCycleLimit", name = "Cycle limit",
            description = "Number of cycles when unlimited mode is disabled.", hidden = true)
    @Range(min = 1, max = 10000)
    default int continuousCycleLimit() { return 1; }

    @ConfigItem(keyName = "continuousUnlimitedCycles", name = "Unlimited cycles",
            description = "Deliberately repeat until another safety bound stops the run.", hidden = true)
    default boolean continuousUnlimitedCycles() { return false; }

    @ConfigItem(keyName = "continuousDecant", name = "Decant to four doses",
            description = "Use Bob Barter after each finished-potion phase.", hidden = true)
    default boolean continuousDecant() { return true; }

    @ConfigItem(keyName = "continuousSell", name = "Sell output",
            description = "Continuous mode always sells its cycle output to fund the next cycle.", hidden = true)
    default boolean continuousSell() { return true; }
}
