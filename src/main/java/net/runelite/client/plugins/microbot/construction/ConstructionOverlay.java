package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.plugins.microbot.construction.ConstructionPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ConstructionOverlay extends OverlayPanel {

    private final net.runelite.client.plugins.microbot.construction.ConstructionPlugin plugin;

    @Inject
    public ConstructionOverlay(ConstructionPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Construction Script")
                .color(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getState().toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Butler flow:")
                .right(plugin.getButlerFlow())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Butler:")
                .right(plugin.isButlerPresent()
                        ? "Present (" + plugin.getButlerDistance() + " tiles)"
                        : "Absent")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Overflow:")
                .right(plugin.getOverflowFlow())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dialogue:")
                .right(plugin.getDialogueState())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Planks / free:")
                .right(plugin.getPlankCount() + " / " + plugin.getFreeSlots())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Last:")
                .right(plugin.getLastAction())
                .build());

        return super.render(graphics);
    }
}
