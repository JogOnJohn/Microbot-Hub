package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.FoundryShopStrategy;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

final class FoundryShopPlanner
{
    /**
     * The wiki strategy guide values the Smiths outfit (~15% rate gain) above the tail
     * of the mould list (~3-5%), so once this many shop moulds are unlocked the outfit
     * takes priority and the remaining moulds are bought afterwards.
     */
    static final int CORE_MOULDS_BEFORE_OUTFIT = 6;

    private static final List<Purchase> MOULDS = Arrays.asList(
            Purchase.mould("Flamberge Blade", 48, 300, 13921),
            Purchase.mould("Stiletto Forte", 50, 300, 13916),
            Purchase.mould("Serpent Blade", 50, 300, 13922),
            Purchase.mould("Defender Base", 51, 300, 13917),
            Purchase.mould("Defenders Tip", 52, 300, 13927),
            Purchase.mould("Claymore Blade", 59, 350, 13923),
            Purchase.mould("Serrated Tip", 60, 350, 13928),
            Purchase.mould("Juggernaut Forte", 61, 350, 13918),
            Purchase.mould("Needle Point", 69, 400, 13929),
            Purchase.mould("Chopper Forte +1", 70, 400, 13919),
            Purchase.mould("Fleur de Blade", 71, 400, 13924),
            Purchase.mould("Spiker!", 79, 450, 13920),
            Purchase.mould("Choppa!", 80, 450, 13925),
            Purchase.mould("The Point!", 81, 450, 13930));

    private static final List<Purchase> OUTFIT = Arrays.asList(
            Purchase.reward("Smiths boots", 3_500, ItemID.SMITHS_BOOTS),
            Purchase.reward("Smiths gloves", 3_500, ItemID.SMITHS_GLOVES),
            Purchase.reward("Smiths tunic", 4_000, ItemID.SMITHS_TUNIC),
            Purchase.reward("Smiths trousers", 4_000, ItemID.SMITHS_TROUSERS));

    private FoundryShopPlanner()
    {
    }

    static Purchase nextTarget(
            int smithingLevel,
            FoundryShopStrategy strategy,
            IntPredicate mouldUnlocked,
            IntPredicate rewardOwned)
    {
        if (strategy == null || strategy == FoundryShopStrategy.DISABLED)
        {
            return null;
        }

        Purchase nextMould = null;
        int unlockedMoulds = 0;
        for (Purchase purchase : MOULDS)
        {
            if (mouldUnlocked.test(purchase.getUnlockVarbit()))
            {
                unlockedMoulds++;
            }
            else if (nextMould == null && purchase.getRequiredLevel() <= smithingLevel)
            {
                nextMould = purchase;
            }
        }

        if (strategy == FoundryShopStrategy.MOULDS_ONLY)
        {
            return nextMould;
        }

        Purchase nextOutfit = null;
        for (Purchase purchase : OUTFIT)
        {
            if (!rewardOwned.test(purchase.getItemId()))
            {
                nextOutfit = purchase;
                break;
            }
        }

        if (nextOutfit != null && unlockedMoulds >= CORE_MOULDS_BEFORE_OUTFIT)
        {
            return nextOutfit;
        }
        return nextMould != null ? nextMould : nextOutfit;
    }

    @Value
    static class Purchase
    {
        String name;
        int requiredLevel;
        int cost;
        int unlockVarbit;
        int itemId;
        boolean mould;

        private static Purchase mould(String name, int level, int cost, int unlockVarbit)
        {
            return new Purchase(name, level, cost, unlockVarbit, -1, true);
        }

        private static Purchase reward(String name, int cost, int itemId)
        {
            return new Purchase(name, 0, cost, -1, itemId, false);
        }
    }
}
