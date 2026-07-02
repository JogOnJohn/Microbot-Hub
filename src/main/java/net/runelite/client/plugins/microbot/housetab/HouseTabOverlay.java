package net.runelite.client.plugins.microbot.housetab;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class HouseTabOverlay extends OverlayPanel {
    private final HouseTabPlugin plugin;

    @Inject
    HouseTabOverlay(HouseTabPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            HouseTabScript script = plugin.getHouseTabScript();
            if (!Microbot.isLoggedIn() || Microbot.getClient().getLocalPlayer() == null || script == null) {
                panelComponent.setPreferredSize(new Dimension(260, 80));
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Micro HouseTab V" + HouseTabPlugin.version)
                        .color(Color.GREEN)
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status")
                        .right(Microbot.isLoggedIn() ? "Waiting for scene" : "Waiting for login")
                        .build());
                return super.render(graphics);
            }
            int currentXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
            int currentLevel = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
            int startXp = script.getStartMagicXp();
            int startLevel = script.getStartMagicLevel();
            int xpGained = startXp >= 0 ? Math.max(0, currentXp - startXp) : 0;
            int levelsGained = startLevel >= 0 ? Math.max(0, currentLevel - startLevel) : 0;

            panelComponent.setPreferredSize(new Dimension(280, 290));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro HouseTab V" + HouseTabPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Task")
                    .right(script.getPlanSummary())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State")
                    .right(script.getCurrentState().getLabel())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Magic")
                    .right(currentLevel + " (+" + levelsGained + ")")
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP gained")
                    .right(String.valueOf(xpGained))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Tablets")
                    .right(String.valueOf(script.getTabletsMade()))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Clay")
                    .right(String.valueOf(script.getUnnotedClayCount()))
                    .build());
            if (!script.getCurrentHost().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Host")
                        .right(script.getCurrentHost())
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State time")
                    .right((script.getMillisInCurrentState() / 1000) + "s")
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status")
                    .right(Microbot.status)
                    .build());
            if (!script.getLastRecoveryReason().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Recovery")
                        .right(script.getLastRecoveryReason())
                        .build());
            }
            if (!script.getLastMaterialSummary().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Materials")
                        .right(script.getLastMaterialSummary())
                        .build());
            }

            if (!script.getStopReason().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stop")
                        .right(script.getStopReason())
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
