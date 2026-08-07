package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class BlackjackOverlay extends OverlayPanel
{
    private static final int WINE_ID = 1993;
    private static final int NOTED_WINE_ID = 1994;

    private final BlackjackPlugin plugin;

    @Inject
    BlackjackOverlay(BlackjackPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BlackjackScript script = plugin.getScript();
        if (script == null)
        {
            return super.render(graphics);
        }
        panelComponent.setPreferredSize(new Dimension(250, 360));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Blackjack")
                .color(script.getState() == BlackjackState.ERROR ? Color.RED : Color.ORANGE)
                .build());

        addLine("State", script.getState().toString());
        addLine("State age", script.getStateAgeSeconds() + "s");
        addLine("Next", script.getNextAction());
        addLine("Observed", script.getLastOutcome());
        addLine("Target", script.getTargetDescription());
        addLine("Combat signal", script.isCombatSignal() ? "TARGETING" : "Clear");
        addLine("Thieving", Integer.toString(Rs2Player.getRealSkillLevel(Skill.THIEVING)));
        addLine("XP gained", Integer.toString(script.getXpGained()));
        addLine("Knockouts", Integer.toString(script.getSuccessfulKnockouts()));
        addLine("Failed KOs", Integer.toString(script.getFailedKnockouts()));
        addLine("Pickpockets", Integer.toString(script.getSuccessfulPickpockets()));
        addLine("Burst", script.getPicksThisKnockout() + "/2 (" + script.getPickpocketClicks() + " clicks)");
        addLine("Burst timeouts", Integer.toString(script.getBurstTimeouts()));
        addLine("Menu misses", Integer.toString(script.getKnockoutMenuMisses()));
        addLine("Reset probes", Integer.toString(script.getCombatResetRetries()));
        addLine("HP", String.format("%.0f%%", Rs2Player.getHealthPercentage()));
        addLine("Wine", Integer.toString(Rs2Inventory.count(WINE_ID)));
        addLine("Noted wine", Integer.toString(Rs2Inventory.count(NOTED_WINE_ID)));
        addLine("Runtime", script.getFormattedRuntime());

        if (!script.getStopReason().isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(script.getStopReason())
                    .leftColor(Color.RED)
                    .build());
        }

        addLine("Version", BlackjackPlugin.VERSION);
        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .build());
    }
}
