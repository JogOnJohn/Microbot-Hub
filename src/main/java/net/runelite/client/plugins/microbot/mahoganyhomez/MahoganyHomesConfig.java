package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MahoganyHomesConfig.GROUP)
public interface MahoganyHomesConfig extends Config
{
	String GROUP = "mahoganyhomes";

	@ConfigItem(
		keyName = "contractTier",
		name = "Contract level",
		description = "Materials to prepare when no current contract is detected",
		position = 0
	)
	default MahoganyHomesData contractTier()
	{
		return MahoganyHomesData.EXPERT;
	}

	@ConfigItem(
		keyName = "useAstralContact",
		name = "Use Astral Contact",
		description = "Get contracts from Amy using Lunar Astral Contact. Carry contact runes (or a rune pouch) and teleport items for the walker.",
		position = 1
	)
	default boolean useAstralContact()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideOverlay",
		name = "Hide overlay",
		description = "Hide the Mahogany Homes metrics overlay",
		position = 2
	)
	default boolean hideOverlay()
	{
		return false;
	}

    @ConfigItem(keyName = "interactionAwareTurnIn", name = "Interaction-aware hand-in",
        description = "Let a compatible walker approach and speak to the homeowner with confirmed input handoff", position = 3)
    default boolean interactionAwareTurnIn() { return false; }

    @ConfigItem(keyName = "interactionAwareFurniture", name = "Interaction-aware mapped furniture",
        description = "Let a compatible walker approach mapped furniture and confirm its repair state change", position = 4)
    default boolean interactionAwareFurniture() { return false; }
}
