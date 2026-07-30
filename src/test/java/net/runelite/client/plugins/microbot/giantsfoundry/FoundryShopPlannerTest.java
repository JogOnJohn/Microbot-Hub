package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.FoundryShopStrategy;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FoundryShopPlannerTest
{
    @Test
    void followsLevelOrderAcrossTheLevelFiftyTransition()
    {
        Set<Integer> unlocked = new HashSet<>();

        FoundryShopPlanner.Purchase level49 = next(49, FoundryShopStrategy.MOULDS_ONLY, unlocked, new HashSet<>());
        assertEquals("Flamberge Blade", level49.getName());
        unlocked.add(level49.getUnlockVarbit());

        FoundryShopPlanner.Purchase level50 = next(50, FoundryShopStrategy.MOULDS_ONLY, unlocked, new HashSet<>());
        assertEquals("Stiletto Forte", level50.getName());
        unlocked.add(level50.getUnlockVarbit());
        assertEquals("Serpent Blade", next(50, FoundryShopStrategy.MOULDS_ONLY, unlocked, new HashSet<>()).getName());
    }

    @Test
    void waitsForHigherLevelMouldsInMouldOnlyMode()
    {
        Set<Integer> unlocked = new HashSet<>();
        unlocked.add(13921);
        unlocked.add(13916);
        unlocked.add(13922);

        assertNull(next(50, FoundryShopStrategy.MOULDS_ONLY, unlocked, new HashSet<>()));
    }

    @Test
    void buysOutfitAfterAllCurrentlyUsableMoulds()
    {
        Set<Integer> unlocked = new HashSet<>();
        unlocked.add(13921);
        unlocked.add(13916);
        unlocked.add(13922);
        Set<Integer> owned = new HashSet<>();

        assertEquals("Smiths boots", next(50, FoundryShopStrategy.MOULDS_THEN_OUTFIT, unlocked, owned).getName());
        owned.add(ItemID.SMITHS_BOOTS);
        assertEquals("Smiths gloves", next(50, FoundryShopStrategy.MOULDS_THEN_OUTFIT, unlocked, owned).getName());
    }

    @Test
    void prefersOutfitOnceCoreMouldsAreUnlocked()
    {
        Set<Integer> unlocked = new HashSet<>();
        unlocked.add(13921); // Flamberge Blade
        unlocked.add(13916); // Stiletto Forte
        unlocked.add(13922); // Serpent Blade
        unlocked.add(13917); // Defender Base
        unlocked.add(13927); // Defenders Tip
        unlocked.add(13923); // Claymore Blade
        Set<Integer> owned = new HashSet<>();

        // Serrated Tip (60) and Juggernaut Forte (61) are eligible, but with six core
        // moulds unlocked the outfit takes priority per the wiki strategy guide.
        assertEquals("Smiths boots", next(61, FoundryShopStrategy.MOULDS_THEN_OUTFIT, unlocked, owned).getName());

        owned.add(ItemID.SMITHS_BOOTS);
        owned.add(ItemID.SMITHS_GLOVES);
        owned.add(ItemID.SMITHS_TUNIC);
        owned.add(ItemID.SMITHS_TROUSERS);
        assertEquals("Serrated Tip", next(61, FoundryShopStrategy.MOULDS_THEN_OUTFIT, unlocked, owned).getName());
    }

    @Test
    void mouldOnlyModeIgnoresTheOutfitPriority()
    {
        Set<Integer> unlocked = new HashSet<>();
        unlocked.add(13921);
        unlocked.add(13916);
        unlocked.add(13922);
        unlocked.add(13917);
        unlocked.add(13927);
        unlocked.add(13923);

        assertEquals("Serrated Tip", next(61, FoundryShopStrategy.MOULDS_ONLY, unlocked, new HashSet<>()).getName());
    }

    @Test
    void disablesAllPurchases()
    {
        assertNull(next(99, FoundryShopStrategy.DISABLED, new HashSet<>(), new HashSet<>()));
    }

    private static FoundryShopPlanner.Purchase next(
            int level,
            FoundryShopStrategy strategy,
            Set<Integer> unlocked,
            Set<Integer> owned)
    {
        return FoundryShopPlanner.nextTarget(level, strategy, unlocked::contains, owned::contains);
    }
}
