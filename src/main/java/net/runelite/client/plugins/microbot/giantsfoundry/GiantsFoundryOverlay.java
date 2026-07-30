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
        panelComponent.setPreferredSize(new Dimension(260, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Giants' Foundry v" + GiantsFoundryPlugin.version)
                .color(Color.GREEN)
                .build());

        addLine("State", GiantsFoundryScript.state == null ? "Starting" : GiantsFoundryScript.state.toString());
        addLine("Status", GiantsFoundryScript.getStatus());
        addLine("Materials", GiantsFoundryScript.getMaterialDescription());

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
        addLine("Progress", snapshot.progress);
        addLine("Crucible", snapshot.oreCount);
        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
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
