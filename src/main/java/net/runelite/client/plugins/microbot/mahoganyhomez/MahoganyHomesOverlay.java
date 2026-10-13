package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class MahoganyHomesOverlay extends OverlayPanel
{
	private final MahoganyHomesPlugin plugin;
	private final MahoganyHomesConfig config;

	@Inject
	MahoganyHomesOverlay(MahoganyHomesPlugin plugin, MahoganyHomesConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPreferredSize(new Dimension(240, 0));
		panelComponent.setPreferredSize(
			new Dimension(240, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.hideOverlay())
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Mahogany Homes")
			.color(Color.GREEN)
			.build());
		line("State", plugin.getError() == null ? plugin.getState().replace('_', ' ') : "ERROR");
		if (plugin.getError() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(plugin.getError()).leftColor(Color.RED).build());
		}

		MahoganyHomesData tier = plugin.getAssignedTier();
		String homeowner = plugin.getAssignedHomeowner();
		line("Homeowner", homeowner == null ? "None" : homeowner);
		line("Tier", tier == null ? "-" : tier.getName());
		line("Contract", homeowner == null ? "Unassigned" : plugin.isComplete() ? "Complete"
			: plugin.isContractValidated() ? "Verified" : plugin.isContractArrived() ? "At house" : "Travelling");
		if (homeowner != null && plugin.isContractValidated() && !plugin.isComplete())
		{
			line("Effects verified", Integer.toString(plugin.getCompletedEffects()));
			line("Planks needed", Integer.toString(plugin.getCurrentRequiredPlanks()));
			line("Steel bars needed", Integer.toString(plugin.getCurrentRequiredSteelBars()));
		}
		line("Homes completed", String.format("%,d", plugin.getSessionContracts()));
		line("Planks loose / sack", plugin.getLoosePlanks() + " / " + plugin.getStoredPlanks());
		line("Steel bars", Integer.toString(plugin.getSteelBars()));
		line("Contracts/hr", String.format("%,d", plugin.getContractsPerHour()));
		line("Runtime", formatRuntime(plugin.getSessionRuntimeMillis()));
		line("Construction XP", String.format("%,d", plugin.getConstructionXpGained()));
		line("XP/hr", String.format("%,d", plugin.getConstructionXpPerHour()));
		return super.render(graphics);
	}

	private void line(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.build());
	}

	private static String formatRuntime(long milliseconds)
	{
		long seconds = milliseconds / 1_000;
		return String.format("%02d:%02d:%02d", seconds / 3_600,
			seconds / 60 % 60, seconds % 60);
	}
}
