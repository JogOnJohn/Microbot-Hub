package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Value;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;

@Value
public class FoundryMaterialPlan
{
    Material first;
    Material second;
    boolean recycledItems;

    public int getTotalBarEquivalent()
    {
        return first.getBarEquivalentAmount() + second.getBarEquivalentAmount();
    }

    public String getDescription()
    {
        return first.getDescription() + " + " + second.getDescription();
    }

    public int getCraftsAvailable(int firstSupply, int secondSupply)
    {
        if (firstSupply < 0 || secondSupply < 0 || first.getQuantity() <= 0 || second.getQuantity() <= 0)
        {
            return 0;
        }
        return Math.min(firstSupply / first.getQuantity(), secondSupply / second.getQuantity());
    }

    public String getSupplyShortage(int firstSupply, int secondSupply)
    {
        int firstMissing = Math.max(0, first.getQuantity() - firstSupply);
        int secondMissing = Math.max(0, second.getQuantity() - secondSupply);
        if (firstMissing == 0 && secondMissing == 0)
        {
            return null;
        }
        if (firstMissing > 0 && secondMissing > 0)
        {
            return "missing " + firstMissing + " " + first.getName()
                    + " and " + secondMissing + " " + second.getName();
        }
        Material missing = firstMissing > 0 ? first : second;
        int amount = firstMissing > 0 ? firstMissing : secondMissing;
        return "missing " + amount + " " + missing.getName();
    }

    @Value
    public static class Material
    {
        String name;
        SmithableBars metal;
        int quantity;
        int barEquivalentPerItem;

        public int getBarEquivalentAmount()
        {
            return quantity * barEquivalentPerItem;
        }

        public String getDescription()
        {
            return quantity + " " + name + (barEquivalentPerItem == 1 ? "" : " (" + getBarEquivalentAmount() + " bars)");
        }
    }
}
