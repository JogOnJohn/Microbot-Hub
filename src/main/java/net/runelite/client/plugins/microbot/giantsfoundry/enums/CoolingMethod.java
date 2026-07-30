package net.runelite.client.plugins.microbot.giantsfoundry.enums;

public enum CoolingMethod
{
    ICE_GLOVES("Ice gloves"),
    BUCKET_OF_WATER("Bucket of water");

    private final String displayName;

    CoolingMethod(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
