package net.runelite.client.plugins.microbot.giantsfoundry.enums;

public enum AlloyStrategy
{
    AUTO_BEST("Auto: best available"),
    AUTO_ECONOMY("Auto: economical"),
    MANUAL_BARS("Manual bars"),
    MANUAL_ITEMS("Manual recycled items");

    private final String displayName;

    AlloyStrategy(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
