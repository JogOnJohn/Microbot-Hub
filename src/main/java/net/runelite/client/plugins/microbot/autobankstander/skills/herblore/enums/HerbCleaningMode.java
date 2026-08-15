package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums;

public enum HerbCleaningMode {
    DEFAULT("Default"),
    RECORDED_SERPENTINE("Recorded serpentine"),
    TURBO("Turbo"),
    RANDOM("Random");

    private final String displayName;

    HerbCleaningMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
