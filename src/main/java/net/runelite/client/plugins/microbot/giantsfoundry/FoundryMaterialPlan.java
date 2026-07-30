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
