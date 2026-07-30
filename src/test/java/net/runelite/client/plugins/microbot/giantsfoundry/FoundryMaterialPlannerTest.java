package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.AlloyStrategy;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryMaterialPlannerTest
{
    @Test
    void selectsLevelAppropriateAlloys()
    {
        assertAutoPlan(15, SmithableBars.BRONZE_BAR, 14, SmithableBars.IRON_BAR, 14);
        assertAutoPlan(45, SmithableBars.IRON_BAR, 14, SmithableBars.STEEL_BAR, 14);
        assertAutoPlan(50, SmithableBars.STEEL_BAR, 14, SmithableBars.MITHRIL_BAR, 14);
        assertAutoPlan(70, SmithableBars.MITHRIL_BAR, 18, SmithableBars.ADAMANT_BAR, 10);
        assertAutoPlan(85, SmithableBars.ADAMANT_BAR, 19, SmithableBars.RUNE_BAR, 9);
    }

    @Test
    void economyModeAvoidsRuneAtHighLevels()
    {
        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config(AlloyStrategy.AUTO_ECONOMY), 99);

        assertTrue(result.isValid());
        assertEquals(SmithableBars.MITHRIL_BAR, result.getPlan().getFirst().getMetal());
        assertEquals(18, result.getPlan().getFirst().getQuantity());
        assertEquals(SmithableBars.ADAMANT_BAR, result.getPlan().getSecond().getMetal());
        assertEquals(10, result.getPlan().getSecond().getQuantity());
    }

    @Test
    void rejectsManualPlansThatDoNotTotalTwentyEight()
    {
        GiantsFoundryConfig config = new TestConfig(AlloyStrategy.MANUAL_BARS)
        {
            @Override
            public int firstBarAmount()
            {
                return 10;
            }

            @Override
            public int secondBarAmount()
            {
                return 10;
            }
        };

        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config, 45);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("exactly 28"));
    }

    @Test
    void rejectsMetalsAboveThePlayersLevel()
    {
        GiantsFoundryConfig config = new TestConfig(AlloyStrategy.MANUAL_BARS)
        {
            @Override
            public SmithableBars FirstBar()
            {
                return SmithableBars.MITHRIL_BAR;
            }
        };

        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config, 45);

        assertFalse(result.isValid());
        assertTrue(result.getError().contains("50 Smithing"));
    }

    @Test
    void convertsRecycledItemsToExactQuantities()
    {
        GiantsFoundryConfig config = new TestConfig(AlloyStrategy.MANUAL_ITEMS)
        {
            @Override
            public String firstItem()
            {
                return "Iron platebody";
            }

            @Override
            public String secondItem()
            {
                return "Steel platelegs";
            }

            @Override
            public int firstBarAmount()
            {
                return 16;
            }

            @Override
            public int secondBarAmount()
            {
                return 12;
            }
        };

        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config, 45);

        assertTrue(result.isValid());
        assertTrue(result.getPlan().isRecycledItems());
        assertEquals(4, result.getPlan().getFirst().getQuantity());
        assertEquals(4, result.getPlan().getFirst().getBarEquivalentPerItem());
        assertEquals(6, result.getPlan().getSecond().getQuantity());
        assertEquals(2, result.getPlan().getSecond().getBarEquivalentPerItem());
        assertEquals(28, result.getPlan().getTotalBarEquivalent());
    }

    @Test
    void rejectsUnknownOrIndivisibleRecycledItems()
    {
        assertFalse(FoundryMaterialPlanner.create(new TestConfig(AlloyStrategy.MANUAL_ITEMS)
        {
            @Override
            public String firstItem()
            {
                return "Iron dagger";
            }

            @Override
            public String secondItem()
            {
                return "Steel platebody";
            }
        }, 45).isValid());

        FoundryMaterialPlanner.PlanResult indivisible = FoundryMaterialPlanner.create(new TestConfig(AlloyStrategy.MANUAL_ITEMS)
        {
            @Override
            public String firstItem()
            {
                return "Iron platebody";
            }

            @Override
            public String secondItem()
            {
                return "Steel scimitar";
            }
        }, 45);
        assertFalse(indivisible.isValid());
        assertNotNull(indivisible.getError());
    }

    @Test
    void recognizesAdamantAndRuneEquipmentNames()
    {
        FoundryMaterialPlanner.ItemMaterial adamant = FoundryMaterialPlanner.parseItem("Adamant platebody");
        FoundryMaterialPlanner.ItemMaterial rune = FoundryMaterialPlanner.parseItem("Rune platelegs");

        assertNotNull(adamant);
        assertEquals(SmithableBars.ADAMANT_BAR, adamant.getMetal());
        assertEquals(4, adamant.getBarEquivalent());
        assertNotNull(rune);
        assertEquals(SmithableBars.RUNE_BAR, rune.getMetal());
        assertEquals(2, rune.getBarEquivalent());
    }

    @Test
    void reportsCraftsAndExactSupplyShortages()
    {
        FoundryMaterialPlan plan = FoundryMaterialPlanner.create(config(AlloyStrategy.AUTO_BEST), 50).getPlan();

        assertEquals(3, plan.getCraftsAvailable(50, 55));
        assertEquals(0, plan.getCraftsAvailable(13, 100));
        assertEquals("missing 1 Steel bar", plan.getSupplyShortage(13, 100));
        assertEquals("missing 4 Steel bar and 6 Mithril bar", plan.getSupplyShortage(10, 8));
        assertEquals(null, plan.getSupplyShortage(14, 14));
    }

    @Test
    void reportsShortagesAgainstAPartialRemainder()
    {
        FoundryMaterialPlan plan = FoundryMaterialPlanner.create(config(AlloyStrategy.AUTO_BEST), 50).getPlan();

        // resuming a partially filled crucible only needs the remaining quantities
        assertEquals(null, plan.getSupplyShortage(0, 5, 0, 5));
        assertEquals("missing 3 Mithril bar", plan.getSupplyShortage(20, 2, 0, 5));
    }

    private static void assertAutoPlan(int level, SmithableBars first, int firstAmount, SmithableBars second, int secondAmount)
    {
        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config(AlloyStrategy.AUTO_BEST), level);
        assertTrue(result.isValid());
        assertEquals(first, result.getPlan().getFirst().getMetal());
        assertEquals(firstAmount, result.getPlan().getFirst().getQuantity());
        assertEquals(second, result.getPlan().getSecond().getMetal());
        assertEquals(secondAmount, result.getPlan().getSecond().getQuantity());
        assertEquals(28, result.getPlan().getTotalBarEquivalent());
    }

    private static GiantsFoundryConfig config(AlloyStrategy strategy)
    {
        return new TestConfig(strategy);
    }

    private static class TestConfig implements GiantsFoundryConfig
    {
        private final AlloyStrategy strategy;

        private TestConfig(AlloyStrategy strategy)
        {
            this.strategy = strategy;
        }

        @Override
        public AlloyStrategy alloyStrategy()
        {
            return strategy;
        }
    }
}
