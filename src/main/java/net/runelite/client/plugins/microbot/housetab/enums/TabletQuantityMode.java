package net.runelite.client.plugins.microbot.housetab.enums;

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
