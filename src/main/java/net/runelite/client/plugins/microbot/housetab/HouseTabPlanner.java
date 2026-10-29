package net.runelite.client.plugins.microbot.housetab;

import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Map;
import java.util.stream.Collectors;

/*
 * Planner is intentionally pure: it answers "what should we make?" and "what
 * are we missing?" from a snapshot, without clicking anything. That makes the
 * decision logic easier to test mentally and keeps side effects in the script.
 */
final class HouseTabPlanner {
    private HouseTabPlanner() {
    }

    static HouseTablet resolveTablet(boolean progressive, HouseTablet configuredTablet, int magicLevel) {
        // Progressive mode always chooses the available tablet with the best
        // Magic XP. Non-progressive mode respects the user's selected tablet.
        return progressive ? HouseTablet.highestXpForLevel(magicLevel) : configuredTablet;
    }

    static boolean needsBankPrep(HouseTabSnapshot snapshot, boolean useCombinationStaff) {
        // Null snapshots happen while the game scene is not readable yet. Treat
        // that as "needs prep" so the caller takes a safe setup path.
        if (snapshot == null) {
            return true;
        }
        if (!snapshot.hasAnySoftClay || !snapshot.hasRequiredRunes) {
            return true;
        }
        return useCombinationStaff && !snapshot.hasStaff;
    }

    static String missingMaterials(HouseTabSnapshot snapshot, boolean useCombinationStaff) {
        // This string is shown in logs/overlay, so make it explain the first
        // blocking requirement rather than returning a generic failure.
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
