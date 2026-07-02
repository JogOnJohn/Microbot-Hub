package net.runelite.client.plugins.microbot.housetab;

import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Map;
import java.util.stream.Collectors;

final class HouseTabPlanner {
    private HouseTabPlanner() {
    }

    static HouseTablet resolveTablet(boolean progressive, HouseTablet configuredTablet, int magicLevel) {
        return progressive ? HouseTablet.highestXpForLevel(magicLevel) : configuredTablet;
    }

    static boolean needsBankPrep(HouseTabSnapshot snapshot, boolean useCombinationStaff) {
        if (snapshot == null) {
            return true;
        }
        if (!snapshot.hasAnySoftClay || !snapshot.hasRequiredRunes) {
            return true;
        }
        return useCombinationStaff && !snapshot.hasStaff;
    }

    static String missingMaterials(HouseTabSnapshot snapshot, boolean useCombinationStaff) {
        if (snapshot == null) {
            return "No game-state snapshot available";
        }
        if (!snapshot.hasAnySoftClay) {
            return "Missing soft clay";
        }
        if (!snapshot.hasRequiredRunes) {
            Map<Runes, Integer> requirements = snapshot.selectedTablet.getRuneRequirements();
            return "Missing runes: " + requirements.entrySet().stream()
                    .map(entry -> entry.getKey().name().toLowerCase() + " x" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
        if (useCombinationStaff && !snapshot.hasStaff) {
            return "Missing staff for " + snapshot.selectedTablet.getName();
        }
        return "";
    }
}
