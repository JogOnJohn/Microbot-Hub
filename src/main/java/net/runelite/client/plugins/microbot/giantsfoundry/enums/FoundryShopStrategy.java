package net.runelite.client.plugins.microbot.giantsfoundry.enums;

public enum FoundryShopStrategy
{
    DISABLED("Disabled"),
    MOULDS_ONLY("Usable moulds only"),
    MOULDS_THEN_OUTFIT("Usable moulds, then Smiths outfit");

    private final String displayName;

    FoundryShopStrategy(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
