package net.runelite.client.plugins.microbot.housetab;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;

final class HouseTabSnapshot {
    final boolean loggedIn;
    final boolean sceneReady;
    final int world;
    final WorldPoint location;
    final boolean atGrandExchange;
    final boolean nearRimmingtonAdvertisement;
    final boolean insidePlayerHouse;
    final boolean housePortalVisible;
    final boolean compatibleLecternVisible;
    final boolean lecternInterfaceOpen;
    final boolean craftingActive;
    final boolean hasUnnotedClay;
    final boolean hasNotedClay;
    final int unnotedClay;
    final int notedClay;
    final boolean hasAnySoftClay;
    final boolean hasRequiredRunes;
    final boolean hasStaff;
    final HouseTablet selectedTablet;

    HouseTabSnapshot(
            boolean loggedIn,
            boolean sceneReady,
            int world,
            WorldPoint location,
            boolean atGrandExchange,
            boolean nearRimmingtonAdvertisement,
            boolean insidePlayerHouse,
            boolean housePortalVisible,
            boolean compatibleLecternVisible,
            boolean lecternInterfaceOpen,
            boolean craftingActive,
            boolean hasUnnotedClay,
            boolean hasNotedClay,
            int unnotedClay,
            int notedClay,
            boolean hasAnySoftClay,
            boolean hasRequiredRunes,
            boolean hasStaff,
            HouseTablet selectedTablet) {
        this.loggedIn = loggedIn;
        this.sceneReady = sceneReady;
        this.world = world;
        this.location = location;
        this.atGrandExchange = atGrandExchange;
        this.nearRimmingtonAdvertisement = nearRimmingtonAdvertisement;
        this.insidePlayerHouse = insidePlayerHouse;
        this.housePortalVisible = housePortalVisible;
        this.compatibleLecternVisible = compatibleLecternVisible;
        this.lecternInterfaceOpen = lecternInterfaceOpen;
        this.craftingActive = craftingActive;
        this.hasUnnotedClay = hasUnnotedClay;
        this.hasNotedClay = hasNotedClay;
        this.unnotedClay = unnotedClay;
        this.notedClay = notedClay;
        this.hasAnySoftClay = hasAnySoftClay;
        this.hasRequiredRunes = hasRequiredRunes;
        this.hasStaff = hasStaff;
        this.selectedTablet = selectedTablet;
    }

    String compactDebug() {
        return "world=" + world
                + " loc=" + (location == null ? "unknown" : location)
                + " tablet=" + (selectedTablet == null ? "none" : selectedTablet.getName())
                + " ge=" + atGrandExchange
                + " rim=" + nearRimmingtonAdvertisement
                + " house=" + insidePlayerHouse
                + " portal=" + housePortalVisible
                + " lectern=" + compatibleLecternVisible
                + " iface=" + lecternInterfaceOpen
                + " crafting=" + craftingActive
                + " clay=" + unnotedClay + "/" + notedClay
                + " runes=" + hasRequiredRunes
                + " staff=" + hasStaff;
    }
}
