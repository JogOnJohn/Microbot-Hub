package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.AlloyStrategy;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

public final class FoundryMaterialPlanner
{
    public static final int REQUIRED_BAR_EQUIVALENT = 28;

    private FoundryMaterialPlanner()
    {
    }

    public static PlanResult create(GiantsFoundryConfig config, int smithingLevel)
    {
        if (smithingLevel < SmithableBars.IRON_BAR.getLevelNeeded())
        {
            return PlanResult.error("Giants' Foundry requires at least 15 Smithing.");
        }

        switch (config.alloyStrategy())
        {
            case AUTO_BEST:
                return PlanResult.success(autoPlan(smithingLevel, true));
            case AUTO_ECONOMY:
                return PlanResult.success(autoPlan(smithingLevel, false));
            case MANUAL_BARS:
                return manualBars(config, smithingLevel);
            case MANUAL_ITEMS:
                return manualItems(config, smithingLevel);
            default:
                return PlanResult.error("Unsupported material strategy.");
        }
    }

    private static FoundryMaterialPlan autoPlan(int level, boolean bestAvailable)
    {
        if (level >= SmithableBars.RUNE_BAR.getLevelNeeded() && bestAvailable)
        {
            return barPlan(SmithableBars.ADAMANT_BAR, 14, SmithableBars.RUNE_BAR, 14);
        }
        if (level >= SmithableBars.ADAMANT_BAR.getLevelNeeded())
        {
            return bestAvailable
                    ? barPlan(SmithableBars.MITHRIL_BAR, 14, SmithableBars.ADAMANT_BAR, 14)
                    : barPlan(SmithableBars.MITHRIL_BAR, 18, SmithableBars.ADAMANT_BAR, 10);
        }
        if (level >= SmithableBars.MITHRIL_BAR.getLevelNeeded())
        {
            return barPlan(SmithableBars.STEEL_BAR, 14, SmithableBars.MITHRIL_BAR, 14);
        }
        if (level >= SmithableBars.STEEL_BAR.getLevelNeeded())
        {
            return barPlan(SmithableBars.IRON_BAR, 14, SmithableBars.STEEL_BAR, 14);
        }
        return barPlan(SmithableBars.BRONZE_BAR, 14, SmithableBars.IRON_BAR, 14);
    }

    private static PlanResult manualBars(GiantsFoundryConfig config, int level)
    {
        SmithableBars first = config.FirstBar();
        SmithableBars second = config.SecondBar();
        String validation = validateMetals(first, config.firstBarAmount(), second, config.secondBarAmount(), level);
        return validation == null
                ? PlanResult.success(barPlan(first, config.firstBarAmount(), second, config.secondBarAmount()))
                : PlanResult.error(validation);
    }

    private static PlanResult manualItems(GiantsFoundryConfig config, int level)
    {
        ItemMaterial first = parseItem(config.firstItem());
        ItemMaterial second = parseItem(config.secondItem());
        if (first == null || second == null)
        {
            return PlanResult.error("Use full smithable item names, for example 'Iron platebody'.");
        }

        String validation = validateMetals(first.metal, config.firstBarAmount(), second.metal, config.secondBarAmount(), level);
        if (validation != null)
        {
            return PlanResult.error(validation);
        }
        if (config.firstBarAmount() % first.barEquivalent != 0 || config.secondBarAmount() % second.barEquivalent != 0)
        {
            return PlanResult.error("Each recycled-item contribution must divide evenly by that item's returned bar value.");
        }

        FoundryMaterialPlan.Material firstMaterial = new FoundryMaterialPlan.Material(
                config.firstItem().trim(), first.metal, config.firstBarAmount() / first.barEquivalent, first.barEquivalent);
        FoundryMaterialPlan.Material secondMaterial = new FoundryMaterialPlan.Material(
                config.secondItem().trim(), second.metal, config.secondBarAmount() / second.barEquivalent, second.barEquivalent);
        return PlanResult.success(new FoundryMaterialPlan(firstMaterial, secondMaterial, true));
    }

    private static String validateMetals(SmithableBars first, int firstAmount, SmithableBars second, int secondAmount, int level)
    {
        if (first == null || second == null || first == second)
        {
            return "Select two different metals.";
        }
        if (firstAmount <= 0 || secondAmount <= 0 || firstAmount + secondAmount != REQUIRED_BAR_EQUIVALENT)
        {
            return "Material contributions must be positive and total exactly 28 bars.";
        }
        int requiredLevel = Math.max(first.getLevelNeeded(), second.getLevelNeeded());
        if (level < requiredLevel)
        {
            return "The selected alloy requires " + requiredLevel + " Smithing.";
        }
        return null;
    }

    private static FoundryMaterialPlan barPlan(SmithableBars first, int firstAmount, SmithableBars second, int secondAmount)
    {
        return new FoundryMaterialPlan(
                new FoundryMaterialPlan.Material(first.getName(), first, firstAmount, 1),
                new FoundryMaterialPlan.Material(second.getName(), second, secondAmount, 1),
                false);
    }

    static ItemMaterial parseItem(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return null;
        }
        String normalized = itemName.trim().toLowerCase(Locale.ROOT);
        Optional<SmithableBars> metal = Arrays.stream(SmithableBars.values())
                .filter(bar -> normalized.startsWith(equipmentPrefix(bar) + " "))
                .findFirst();
        if (!metal.isPresent())
        {
            return null;
        }

        Integer returnedBars = Arrays.stream(RecyclableType.values())
                .sorted(Comparator.comparingInt((RecyclableType type) -> type.suffix.length()).reversed())
                .filter(type -> normalized.endsWith(type.suffix))
                .map(type -> type.returnedBars)
                .findFirst()
                .orElse(null);
        return returnedBars == null ? null : new ItemMaterial(metal.get(), returnedBars);
    }

    private static String equipmentPrefix(SmithableBars metal)
    {
        switch (metal)
        {
            case ADAMANT_BAR:
                return "adamant";
            case RUNE_BAR:
                return "rune";
            default:
                return metal.getName().replace(" bar", "").toLowerCase(Locale.ROOT);
        }
    }

    static final class ItemMaterial
    {
        private final SmithableBars metal;
        private final int barEquivalent;

        private ItemMaterial(SmithableBars metal, int barEquivalent)
        {
            this.metal = metal;
            this.barEquivalent = barEquivalent;
        }

        SmithableBars getMetal()
        {
            return metal;
        }

        int getBarEquivalent()
        {
            return barEquivalent;
        }
    }

    private enum RecyclableType
    {
        PLATEBODY("platebody", 4),
        PLATELEGS("platelegs", 2),
        PLATESKIRT("plateskirt", 2),
        KITESHIELD("kiteshield", 2),
        CHAINBODY("chainbody", 2),
        BATTLEAXE("battleaxe", 2),
        WARHAMMER("warhammer", 2),
        TWO_HANDED_SWORD("2h sword", 2),
        LONGSWORD("longsword", 1),
        SCIMITAR("scimitar", 1),
        FULL_HELM("full helm", 1),
        SQ_SHIELD("sq shield", 1),
        CLAWS("claws", 1);

        private final String suffix;
        private final int returnedBars;

        RecyclableType(String suffix, int returnedBars)
        {
            this.suffix = suffix;
            this.returnedBars = returnedBars;
        }
    }

    public static final class PlanResult
    {
        private final FoundryMaterialPlan plan;
        private final String error;

        private PlanResult(FoundryMaterialPlan plan, String error)
        {
            this.plan = plan;
            this.error = error;
        }

        static PlanResult success(FoundryMaterialPlan plan)
        {
            return new PlanResult(plan, null);
        }

        static PlanResult error(String error)
        {
            return new PlanResult(null, error);
        }

        public boolean isValid()
        {
            return plan != null;
        }

        public FoundryMaterialPlan getPlan()
        {
            return plan;
        }

        public String getError()
        {
            return error;
        }
    }
}
