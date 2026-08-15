package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import java.util.Arrays;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;

/** Item-ID and ratio metadata used by every continuous Herblore phase. */
public final class HerbloreRecipeMetadata {
    public final HerblorePotion potion;
    public final Herb herb;
    public final int level;
    public final int grimyHerbId;
    public final int cleanHerbId;
    public final int vialOfWaterId;
    public final int unfinishedId;
    public final int secondaryId;
    public final int secondaryPerOperation;
    public final int[] finishedDoseIds;
    public final boolean chemistryEligible;

    private HerbloreRecipeMetadata(HerblorePotion potion, Herb herb, int[] finishedDoseIds) {
        this.potion = potion;
        this.herb = herb;
        this.level = potion.level;
        this.grimyHerbId = herb == null ? -1 : herb.grimy;
        this.cleanHerbId = herb == null ? -1 : herb.clean;
        this.vialOfWaterId = ItemID.VIAL_WATER;
        this.unfinishedId = potion.unfinished;
        this.secondaryId = potion.secondary;
        this.secondaryPerOperation = secondaryRatio(potion.secondary);
        this.finishedDoseIds = finishedDoseIds;
        this.chemistryEligible = herb != null && finishedDoseIds[2] > 0 && finishedDoseIds[3] > 0;
    }

    public static HerbloreRecipeMetadata resolve(HerblorePotion potion) {
        if (potion == null || Microbot.getRs2ItemManager() == null) {
            throw new IllegalArgumentException("potion and item manager are required");
        }
        Herb herb = Arrays.stream(Herb.values())
                .filter(candidate -> candidate.unfinished == potion.unfinished)
                .findFirst().orElse(null);
        int[] doseIds = new int[4];
        for (int dose = 1; dose <= 4; dose++) {
            doseIds[dose - 1] = Microbot.getRs2ItemManager().getItemId(potion + "(" + dose + ")");
        }
        return new HerbloreRecipeMetadata(potion, herb, doseIds);
    }

    public boolean hasCleanAndUnfinishedPhases() {
        return herb != null;
    }

    public boolean hasResolvedFinishedItems() {
        return Arrays.stream(finishedDoseIds).allMatch(id -> id > 0);
    }

    public static int secondaryRatio(int secondaryId) {
        if (secondaryId == ItemID.PRIF_CRYSTAL_SHARD_CRUSHED) return 4;
        if (secondaryId == ItemID.SNAKEBOSS_SCALE) return 20;
        if (secondaryId == ItemID.LAVA_SHARD) return 4;
        if (secondaryId == ItemID.AMYLASE) return 4;
        return 1;
    }
}
