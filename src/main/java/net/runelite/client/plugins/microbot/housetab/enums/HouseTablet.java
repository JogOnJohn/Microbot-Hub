package net.runelite.client.plugins.microbot.housetab.enums;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public enum HouseTablet {
    VARROCK_TELEPORT("Varrock teleport", ItemID.POH_TABLET_VARROCKTELEPORT, 25, 35.0, LecternFamily.EAGLE, 0x0193_0015,
            Map.of(Runes.AIR, 3, Runes.FIRE, 1, Runes.LAW, 1), List.of(Runes.AIR, Runes.FIRE)),
    LUMBRIDGE_TELEPORT("Lumbridge teleport", ItemID.POH_TABLET_LUMBRIDGETELEPORT, 31, 41.0, LecternFamily.EAGLE, 0x0193_0017,
            Map.of(Runes.AIR, 3, Runes.EARTH, 1, Runes.LAW, 1), List.of(Runes.AIR, Runes.EARTH)),
    FALADOR_TELEPORT("Falador teleport", ItemID.POH_TABLET_FALADORTELEPORT, 37, 48.0, LecternFamily.EAGLE, 0x0193_0018,
            Map.of(Runes.AIR, 3, Runes.WATER, 1, Runes.LAW, 1), List.of(Runes.AIR, Runes.WATER)),
    TELEPORT_TO_HOUSE("Teleport to house", ItemID.POH_TABLET_TELEPORTTOHOUSE, 40, 30.0, LecternFamily.BOTH, 0x0193_0019,
            Map.of(Runes.AIR, 1, Runes.EARTH, 1, Runes.LAW, 1), List.of(Runes.AIR, Runes.EARTH)),
    CAMELOT_TELEPORT("Camelot teleport", ItemID.POH_TABLET_CAMELOTTELEPORT, 45, 55.5, LecternFamily.EAGLE, 0x0193_001a,
            Map.of(Runes.AIR, 5, Runes.LAW, 1), List.of(Runes.AIR)),
    KOUREND_CASTLE_TELEPORT("Kourend castle teleport", ItemID.POH_TABLET_KOURENDTELEPORT, 48, 58.0, LecternFamily.EAGLE, 0x0193_001b,
            Map.of(Runes.FIRE, 1, Runes.WATER, 1, Runes.LAW, 2), List.of(Runes.FIRE, Runes.WATER)),
    ARDOUGNE_TELEPORT("Ardougne teleport", ItemID.POH_TABLET_ARDOUGNETELEPORT, 51, 61.0, LecternFamily.EAGLE, 0x0193_001d,
            Map.of(Runes.WATER, 2, Runes.LAW, 2), List.of(Runes.WATER)),
    CIVITAS_ILLA_FORTIS_TELEPORT("Civitas illa fortis teleport", ItemID.POH_TABLET_FORTISTELEPORT, 54, 64.0, LecternFamily.EAGLE, 0x0193_001f,
            Map.of(Runes.EARTH, 1, Runes.FIRE, 1, Runes.LAW, 2), List.of(Runes.EARTH, Runes.FIRE)),
    WATCHTOWER_TELEPORT("Watchtower teleport", ItemID.POH_TABLET_WATCHTOWERTELEPORT, 58, 68.0, LecternFamily.EAGLE, 0x0193_0021,
            Map.of(Runes.EARTH, 2, Runes.LAW, 2), List.of(Runes.EARTH), false);

    private final String name;
    private final int itemId;
    private final int magicLevel;
    private final double magicXp;
    private final LecternFamily lecternFamily;
    private final int widgetId;
    private final Map<Runes, Integer> runeRequirements;
    private final List<Runes> preferredStaffRunes;
    private final boolean progressive;

    HouseTablet(String name, int itemId, int magicLevel, double magicXp, LecternFamily lecternFamily, int widgetId,
                Map<Runes, Integer> runeRequirements, List<Runes> preferredStaffRunes) {
        this(name, itemId, magicLevel, magicXp, lecternFamily, widgetId, runeRequirements, preferredStaffRunes, true);
    }

    HouseTablet(String name, int itemId, int magicLevel, double magicXp, LecternFamily lecternFamily, int widgetId,
                Map<Runes, Integer> runeRequirements, List<Runes> preferredStaffRunes, boolean progressive) {
        this.name = name;
        this.itemId = itemId;
        this.magicLevel = magicLevel;
        this.magicXp = magicXp;
        this.lecternFamily = lecternFamily;
        this.widgetId = widgetId;
        this.runeRequirements = runeRequirements;
        this.preferredStaffRunes = preferredStaffRunes;
        this.progressive = progressive;
    }

    public String getName() {
        return name;
    }

    public int getItemId() {
        return itemId;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public double getMagicXp() {
        return magicXp;
    }

    public int getWidgetId() {
        return widgetId;
    }

    public boolean hasKnownWidget() {
        return widgetId > 0;
    }

    public Map<Runes, Integer> getRuneRequirements() {
        return runeRequirements;
    }

    public List<Runes> getPreferredStaffRunes() {
        return preferredStaffRunes;
    }

    public boolean isProgressive() {
        return progressive;
    }

    public boolean supportsLectern(int objectId) {
        return lecternFamily.supports(objectId);
    }

    public static HouseTablet highestXpForLevel(int magicLevel) {
        return Arrays.stream(values())
                .filter(tablet -> tablet.progressive && tablet.magicLevel <= magicLevel)
                .max(Comparator.comparingDouble(HouseTablet::getMagicXp))
                .orElse(VARROCK_TELEPORT);
    }

    @Override
    public String toString() {
        return name;
    }

    private enum LecternFamily {
        EAGLE,
        DEMON,
        BOTH;

        private boolean supports(int objectId) {
            if (objectId == 37349) {
                return this == EAGLE || this == BOTH;
            }

            if (objectId == ObjectID.POH_LECTERN_8) {
                return true;
            }

            if (objectId == ObjectID.POH_LECTERN_6) {
                return this == EAGLE || this == BOTH;
            }

            return false;
        }
    }
}
