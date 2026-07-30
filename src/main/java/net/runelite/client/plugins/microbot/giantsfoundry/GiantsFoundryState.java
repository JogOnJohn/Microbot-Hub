package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Heat;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage.*;

public class GiantsFoundryState {
    private static final int TOOL_HEAT_SAFETY_MARGIN = 5;
    // heat and progress are from 0-1000
    private static final int VARBIT_HEAT = 13948;
    private static final int VARBIT_PROGRESS = 13949;
    private static final int VARBIT_PREFORM_QUALITY = 13939;
    private static final int VARBIT_PREFORM_START_QUALITY = 13950;

    private static final int VARBIT_BRONZE_COUNT = 13931;
    private static final int VARBIT_IRON_COUNT = 13932;
    private static final int VARBIT_STEEL_COUNT = 13933;
    private static final int VARBIT_MITHRIL_COUNT = 13934;
    private static final int VARBIT_ADAMANT_COUNT = 13935;
    private static final int VARBIT_RUNE_COUNT = 13936;
    public static final int VARBIT_FORTE_SELECTED = 13910;
    public static final int VARBIT_BLADE_SELECTED = 13911;
    public static final int VARBIT_TIP_SELECTED = 13912;

    // 0 - load bars
    // 1 - set mould
    // 2 - collect preform
    // 3 -
    static final int VARBIT_GAME_STAGE = 13914;

    private static final int WIDGET_HEAT_PARENT = 49414153;
    private static final int WIDGET_LOW_HEAT_PARENT = 49414163;
    private static final int WIDGET_MED_HEAT_PARENT = 49414164;
    private static final int WIDGET_HIGH_HEAT_PARENT = 49414165;

    static final int WIDGET_PROGRESS_PARENT = 49414219;
    // children with type 3 are stage boxes
    // every 11th child is a sprite

    private static final int SPRITE_ID_TRIP_HAMMER = 4442;
    private static final int SPRITE_ID_GRINDSTONE = 4443;
    private static final int SPRITE_ID_POLISHING_WHEEL = 4444;

    @Setter
    @Getter
    private boolean enabled;

    private static final List<Stage> stages = new ArrayList<>();
    private static double heatRangeRatio = 0;

    public static void reset() {
        stages.clear();
        heatRangeRatio = 0;
    }

    public static int getOreCount() {
        return totalOreCount(getOreCounts());
    }

    public static int getOreCount(SmithableBars metal) {
        if (metal == null) {
            return 0;
        }
        return Microbot.getVarbitValue(getOreCountVarbit(metal));
    }

    public static int[] getOreCounts() {
        return new int[]{
                Microbot.getVarbitValue(VARBIT_BRONZE_COUNT),
                Microbot.getVarbitValue(VARBIT_IRON_COUNT),
                Microbot.getVarbitValue(VARBIT_STEEL_COUNT),
                Microbot.getVarbitValue(VARBIT_MITHRIL_COUNT),
                Microbot.getVarbitValue(VARBIT_ADAMANT_COUNT),
                Microbot.getVarbitValue(VARBIT_RUNE_COUNT)
        };
    }

    static int totalOreCount(int... counts) {
        int total = 0;
        for (int count : counts) {
            total += Math.max(0, count);
        }
        return total;
    }

    private static int getOreCountVarbit(SmithableBars metal) {
        switch (metal) {
            case BRONZE_BAR:
                return VARBIT_BRONZE_COUNT;
            case IRON_BAR:
                return VARBIT_IRON_COUNT;
            case STEEL_BAR:
                return VARBIT_STEEL_COUNT;
            case MITHRIL_BAR:
                return VARBIT_MITHRIL_COUNT;
            case ADAMANT_BAR:
                return VARBIT_ADAMANT_COUNT;
            case RUNE_BAR:
                return VARBIT_RUNE_COUNT;
            default:
                throw new IllegalArgumentException("Unsupported Foundry metal: " + metal);
        }
    }

    public static int getHeatAmount() {
        return Microbot.getVarbitValue(VARBIT_HEAT);
    }

    public static int getProgressAmount() {
        return Microbot.getVarbitValue(VARBIT_PROGRESS);
    }

    public static int getPreformQuality() {
        return Microbot.getVarbitValue(VARBIT_PREFORM_QUALITY);
    }

    public static int getPreformStartQuality() {
        return Microbot.getVarbitValue(VARBIT_PREFORM_START_QUALITY);
    }

    public static int getGameStage() {
        return Microbot.getVarbitValue(VARBIT_GAME_STAGE);
    }

    public static double getHeatRangeRatio() {
        if (heatRangeRatio == 0) {
            Widget heatWidget = Rs2Widget.getWidget(WIDGET_HEAT_PARENT);
            Widget medHeat = Rs2Widget.getWidget(WIDGET_MED_HEAT_PARENT);
            if (medHeat == null || heatWidget == null || heatWidget.getWidth() <= 0 || medHeat.getWidth() <= 0) {
                return 0;
            }

            heatRangeRatio = medHeat.getWidth() / (double) heatWidget.getWidth();
        }

        return heatRangeRatio;
    }

    public static int[] getLowHeatRange() {
        return new int[]{
                (int) ((1 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((1 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static int[] getMedHeatRange() {
        return new int[]{
                (int) ((3 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((3 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static int[] getHighHeatRange() {
        return new int[]{
                (int) ((5 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((5 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static List<Stage> getStages() {
        Widget progressParent = Rs2Widget.getWidget(WIDGET_PROGRESS_PARENT);
        if (progressParent == null || progressParent.getChildren() == null) {
            return new ArrayList<>(stages);
        }

        List<Stage> visibleStages = new ArrayList<>();
        for (Widget child : progressParent.getChildren()) {
            switch (child.getSpriteId()) {
                case SPRITE_ID_TRIP_HAMMER:
                    visibleStages.add(TRIP_HAMMER);
                    break;
                case SPRITE_ID_GRINDSTONE:
                    visibleStages.add(GRINDSTONE);
                    break;
                case SPRITE_ID_POLISHING_WHEEL:
                    visibleStages.add(POLISHING_WHEEL);
                    break;
            }
        }

        if (!visibleStages.isEmpty()) {
            stages.clear();
            stages.addAll(visibleStages);
        }

        return new ArrayList<>(stages);
    }

    public static Rs2TileObjectModel getStageObject(Stage stage) {
        switch (stage) {
            case TRIP_HAMMER:
                return Microbot.getRs2TileObjectCache().query().withName("trip hammer").nearestOnClientThread();
            case GRINDSTONE:
                return Microbot.getRs2TileObjectCache().query().withName("grindstone").nearestOnClientThread();
            case POLISHING_WHEEL:
                return Microbot.getRs2TileObjectCache().query().withName("polishing wheel").nearestOnClientThread();
        }
        return null;
    }

    public static Stage getCurrentStage() {
        return getCurrentStage(getProgressAmount());
    }

    static Stage getCurrentStage(int progress) {
        List<Stage> currentStages = getStages();
        if (currentStages.isEmpty()) {
            return null;
        }
        int index = (int) (progress / 1000d * currentStages.size());
        if (index < 0 || index >= currentStages.size()) {
            return null;
        }

        return currentStages.get(index);
    }

    public static Heat getCurrentHeat() {
        int heat = getHeatAmount();

        int[] low = getLowHeatRange();
        if (heat > low[0] && heat < low[1]) {
            return Heat.LOW;
        }

        int[] med = getMedHeatRange();
        if (heat > med[0] && heat < med[1]) {
            return Heat.MED;
        }

        int[] high = getHighHeatRange();
        if (heat > high[0] && heat < high[1]) {
            return Heat.HIGH;
        }

        return Heat.NONE;
    }

    /**
     * Get the amount of progress each stage needs
     */
    public static double getProgressPerStage() {
        int stageCount = getStages().size();
        return stageCount == 0 ? 0 : 1000d / stageCount;
    }

    public static int getActionsLeftInStage() {
        int progress = getProgressAmount();
        double progressPerStage = getProgressPerStage();
        Stage current = getCurrentStage();
        if (progressPerStage <= 0 || current == null) {
            return 0;
        }
        double progressTillNext = progressPerStage - progress % progressPerStage;
        return (int) Math.ceil(progressTillNext / current.getProgressPerAction());
    }

    public static Heat getHeatStage()
    {
        if (getCurrentStage() == null) return Heat.NONE;

        return getCurrentStage().getHeat();

}

    public static int getHeatChangeNeeded()
    {
        Stage currentStage = getCurrentStage();
        if (currentStage == null) return 0;
        return calculateHeatChangeNeeded(currentStage, getHeatAmount(), getHeatRange(currentStage));
    }

    static int calculateHeatChangeNeeded(Stage stage, int heat, int[] range)
    {
        if (stage == null || range == null || range.length < 2)
        {
            return 0;
        }
        if (heat < range[0])
        {
            return range[0] - heat;
        }
        if (heat > range[1])
        {
            return range[1] - heat;
        }

        int toolHeatChange = stage.getHeatChange();
        if (toolHeatChange > 0)
        {
            int safeUpperBound = range[1] - toolHeatChange - TOOL_HEAT_SAFETY_MARGIN;
            return heat > safeUpperBound ? safeUpperBound - heat : 0;
        }
        if (toolHeatChange < 0)
        {
            int safeLowerBound = range[0] + Math.abs(toolHeatChange) + TOOL_HEAT_SAFETY_MARGIN;
            return heat < safeLowerBound ? safeLowerBound - heat : 0;
        }
        return 0;
    }


    public static int[] getCurrentHeatRange() {
        return getHeatRange(getCurrentStage());
    }

    static int[] getHeatRange(Stage stage) {
        if (stage == null) return new int[]{0, 0};
        switch (stage) {
            case POLISHING_WHEEL:
                return getLowHeatRange();
            case GRINDSTONE:
                return getMedHeatRange();
            case TRIP_HAMMER:
                return getHighHeatRange();
            default:
                return new int[]{0, 0};
        }
    }

    /**
     * Get the amount of current stage actions that can be
     * performed before the heat drops too high or too low to
     * continue
     */
    public static int getActionsForHeatLevel() {
        Heat heatStage = getCurrentHeat();
        Stage stage = getCurrentStage();
        if (stage == null) return 0;
        if (heatStage != stage.getHeat()) {
            // not the right heat to start with
            return 0;
        }

        return countActionsAvailable(getHeatAmount(), getCurrentHeatRange(), stage);
    }

    /**
     * How many consecutive actions the stage tool can perform from the given heat
     * before the tool's own heat change pushes it out of the working band.
     */
    static int countActionsAvailable(int heat, int[] range, Stage stage) {
        if (stage == null || range == null || range.length < 2) {
            return 0;
        }
        int actions = 0;
        int current = heat;
        while (current > range[0] && current < range[1]) {
            actions++;
            current += stage.getHeatChange();
        }
        return actions;
    }

    public static boolean isPlayerRunning()
    {
        return Microbot.getClient().getVarpValue(173) == 1;
    }

    public static HeatActionStateMachine heatingCoolingState = new HeatActionStateMachine();
}
