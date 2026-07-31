package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;

public class GiantsFoundryOverlay extends OverlayPanel
{
    @Inject
    GiantsFoundryOverlay(GiantsFoundryPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.setPreferredSize(new Dimension(300, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Giants' Foundry v" + GiantsFoundryPlugin.version)
                .color(Color.GREEN)
                .build());

        addLine("State", GiantsFoundryScript.state == null ? "Starting" : GiantsFoundryScript.state.toString());
        addLine("Next action", GiantsFoundryScript.getStatus());
        addLine("Current craft", GiantsFoundryScript.getCurrentCraftDescription());
        addLine("Materials", GiantsFoundryScript.getMaterialDescription());
        addLine("Supplies", GiantsFoundryScript.getSupplyDescription());
        addLine("Next purchase", GiantsFoundryScript.getNextShopPurchase());

        addLine("Crafts completed", Integer.toString(GiantsFoundryScript.getSuccessfulCrafts()));
        addLine("Smithing",
                GiantsFoundryScript.getCurrentSmithingLevel()
                        + " (+" + GiantsFoundryScript.getSmithingLevelsGained() + ")");
        addLine("XP gained", formatNumber(GiantsFoundryScript.getSmithingXpGained()));
        addLine("Reputation",
                formatNumber(GiantsFoundryScript.getCurrentReputation())
                        + " (+" + formatNumber(GiantsFoundryScript.getReputationEarned())
                        + ", -" + formatNumber(GiantsFoundryScript.getReputationSpent()) + ")");
        addLine("Reward GP", formatNumber(GiantsFoundryScript.getRewardGp()));
        addLine("Material cost", formatNumber(GiantsFoundryScript.getMaterialCost()));
        addColoredLine("Net GP", formatNumber(GiantsFoundryScript.getNetGp()),
                GiantsFoundryScript.getNetGp() >= 0
                        ? ColorScheme.PROGRESS_COMPLETE_COLOR
                        : ColorScheme.PROGRESS_ERROR_COLOR);
        addLine("Runtime", formatDuration(GiantsFoundryScript.getSessionRuntimeMillis()));

        if (!GiantsFoundryScript.getError().isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Issue")
                    .right(GiantsFoundryScript.getError())
                    .rightColor(ColorScheme.PROGRESS_ERROR_COLOR)
                    .build());
        }

        StageSnapshot snapshot = StageSnapshot.capture();
        addLine("Stage", snapshot.stage);
        addLine("Required heat", snapshot.requiredHeat);
        addLine("Current heat", snapshot.currentHeat);
        addLine("Quality", GiantsFoundryScript.getCurrentQuality()
                + "/" + GiantsFoundryScript.getCurrentStartQuality());
        addLine("Progress", GiantsFoundryScript.getCurrentProgress() + "/1000");
        addLine("Crucible", snapshot.oreCount);
        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
    }

    private void addColoredLine(String left, String right, Color color)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .rightColor(color)
                .build());
    }

    private String formatNumber(long value)
    {
        return String.format("%,d", value);
    }

    private String formatDuration(long millis)
    {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static final class StageSnapshot
    {
        private final String stage;
        private final String requiredHeat;
        private final String currentHeat;
        private final String progress;
        private final String oreCount;

        private StageSnapshot(String stage, String requiredHeat, String currentHeat, String progress, String oreCount)
        {
            this.stage = stage;
            this.requiredHeat = requiredHeat;
            this.currentHeat = currentHeat;
            this.progress = progress;
            this.oreCount = oreCount;
        }

        private static StageSnapshot capture()
        {
            net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage current = GiantsFoundryState.getCurrentStage();
            return new StageSnapshot(
                    current == null ? "Not available" : current.getName(),
                    current == null ? "Not available" : current.getHeat().getName(),
                    GiantsFoundryState.getCurrentHeat().getName() + " (" + GiantsFoundryState.getHeatAmount() + ")",
                    GiantsFoundryState.getProgressAmount() + "/1000",
                    GiantsFoundryState.getOreCount() + "/28");
        }
    }
}
