package net.runelite.client.plugins.microbot.housetab.enums;

// Controls whether the script drains the current inventory or stops after one
// successful tablet craft. MAKE_ONE is useful for live testing.
public enum TabletQuantityMode {
    MAKE_ALL("Make all"),
    MAKE_ONE("Make one");

    private final String name;

    TabletQuantityMode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
