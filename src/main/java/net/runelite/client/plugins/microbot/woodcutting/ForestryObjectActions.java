package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/** Action checks for predicates evaluated by client-thread query terminals. */
public final class ForestryObjectActions {
    private ForestryObjectActions() { }

    public static boolean hasAction(Rs2TileObjectModel model, String action) {
        if (model == null || action == null) return false;
        var composition = model.getObjectComposition();
        String[] actions = composition == null ? null : composition.getActions();
        if (actions == null) return false;
        for (String candidate : actions) {
            if (action.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }
}
