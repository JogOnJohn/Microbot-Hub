package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

final class ConstructionPlankSack {

    static final int CAPACITY = 28;

    private ConstructionPlankSack() {
    }

    static boolean isCarried() {
        return Rs2Inventory.contains(ItemID.PLANK_SACK);
    }

    static int count(ConstructionConfig.ConstructionMode mode) {
        return isCarried() ? Microbot.getVarbitValue(varbitFor(mode)) : 0;
    }

    static int total() {
        if (!isCarried()) return 0;
        return Microbot.getVarbitValue(VarbitID.PLANK_SACK_PLAIN)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_OAK)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_TEAK)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_MAHOGANY)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_CAMPHOR)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_IRONWOOD)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_ROSEWOOD);
    }

    static int freeCapacity() {
        return Math.max(0, CAPACITY - total());
    }

    static int available(int loose, int stored, boolean carried) {
        return loose + (carried ? stored : 0);
    }

    static boolean shouldFill(int loose, int totalStored, boolean carried) {
        return carried && loose > 0 && totalStored < CAPACITY;
    }

    static boolean fill() {
        return Rs2Inventory.interact(ItemID.PLANK_SACK, "Fill");
    }

    static boolean empty() {
        return Rs2Inventory.interact(ItemID.PLANK_SACK, "Empty");
    }

    static int varbitFor(ConstructionConfig.ConstructionMode mode) {
        switch (mode) {
            case OAK_LARDER:
            case OAK_DUNGEON_DOOR:
                return VarbitID.PLANK_SACK_OAK;
            case MAHOGANY_TABLE:
                return VarbitID.PLANK_SACK_MAHOGANY;
            default:
                throw new IllegalArgumentException("Unsupported construction mode: " + mode);
        }
    }
}
