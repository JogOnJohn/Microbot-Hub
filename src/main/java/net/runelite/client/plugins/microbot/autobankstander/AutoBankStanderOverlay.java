package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class AutoBankStanderOverlay extends OverlayPanel {

    private final AutoBankStanderPlugin plugin;

    @Inject
    AutoBankStanderOverlay(AutoBankStanderPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        AutoBankStanderScript script = plugin.getScript();
        panelComponent.setPreferredSize(new Dimension(290, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Bank Stander v" + AutoBankStanderPlugin.version)
                .color(script.isRunning() ? Color.GREEN : Color.LIGHT_GRAY)
                .build());

        addLine("State", script.getStateName());
        addLine("Action", script.getLastAction());
        addLine("Task", script.getTaskName());

        BankStandingProcessor processor = script.getProcessor();
        if (processor != null) {
            addLine("Detail", processor.getTaskDetail());
            int processable = processor.getBankProcessableCount();
            addLine("Bank operations", processable < 0 ? "Unknown" : Integer.toString(processable));
            addLine("Materials", processor.getBankMaterialSummary());

            int processed = processor.getProcessedCount();
            addLine("Processed", processed < 0 ? "Unknown" : Integer.toString(processed));
            addLine("Batch", processor.getBatchProgress());
            addLine("Equipment", processor.getEquipmentStatus());
        }

        addLine("Loops", Long.toString(script.getLoopCount()));
        addLine("Runtime", formatRuntime(script));
        return super.render(graphics);
    }

    private void addLine(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "" : right)
                .build());
    }

    private String formatRuntime(AutoBankStanderScript script) {
        if (!script.isRunning() || script.getStartedAt() <= 0) return "00:00:00";
        long totalSeconds = Math.max(0, (System.currentTimeMillis() - script.getStartedAt()) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
