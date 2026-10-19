package net.runelite.client.plugins.microbot.mahoganyhomez;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginDescriptor.Default + "Mahogany Homes",
	description = "Prepares and completes verified Mahogany Homes contracts",
	tags = {"mahogany homes", "construction", "microbot"},
	enabledByDefault = false,
	isExternal = true,
	minClientVersion = "2.6.30",
	version = MahoganyHomesPlugin.version
)
public final class MahoganyHomesPlugin extends Plugin
{
	public static final String version = "1.0.17";

	@Inject
	Provider<MahoganyHomesScript> scriptProvider;

	private MahoganyHomesScript script;

	@Inject
	private MahoganyHomesConfig config;

	@Inject
	private MahoganyHomesOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	private volatile int sessionContracts;
	private final MahoganyHomesSessionStats sessionStats = new MahoganyHomesSessionStats();

	@Provides
	MahoganyHomesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MahoganyHomesConfig.class);
	}

	@Override
	protected void startUp()
	{
		script = newScript();
		sessionContracts = 0;
		sessionStats.reset();
		overlayManager.add(overlay);
		script.run(config);
	}

	MahoganyHomesScript newScript()
	{
		return scriptProvider.get();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		script.shutdown();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateConstructionXp();
		script.onGameTick(Microbot.getClient().getTickCount());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.CONSTRUCTION
			&& Microbot.getClient().getGameState() == GameState.LOGGED_IN
			&& Microbot.getClient().getRealSkillLevel(Skill.CONSTRUCTION) > 0)
		{
			sessionStats.observe(event.getXp());
		}
	}

	private void updateConstructionXp()
	{
		if (Microbot.getClient().getGameState() == GameState.LOGGED_IN
			&& Microbot.getClient().getRealSkillLevel(Skill.CONSTRUCTION) > 0)
		{
			sessionStats.observe(Microbot.getClient().getSkillExperience(Skill.CONSTRUCTION));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.DIALOG)
		{
			script.onAssignmentMessage(event.getMessage());
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			boolean alreadyComplete = script.isComplete();
			script.onGameMessage(event.getMessage());
			if (!alreadyComplete && script.isComplete())
			{
				sessionContracts++;
				net.runelite.client.plugins.microbot.util.walker.WebWalkLog.spInfo(
					"mahogany_homes_completed | count={}", sessionContracts);
			}
		}
	}

	public String getState()
	{
		return script.getStateName();
	}

	public String getError()
	{
		return script.getError();
	}

	public int getCompletedEffects()
	{
		return script.getCompletedEffects();
	}

	public int[] getHotspotValues()
	{
		return script.getHotspotValues();
	}

	public int getCurrentRequiredPlanks()
	{
		return script.getCurrentRequiredPlanks();
	}

	public int getCurrentRequiredSteelBars()
	{
		return script.getCurrentRequiredSteelBars();
	}

	public boolean isComplete()
	{
		return script.isComplete();
	}

	public String getAssignedHomeowner()
	{
		return script.getAssignedHomeowner();
	}

	public MahoganyHomesData getAssignedTier()
	{
		return script.getAssignedTier();
	}

	public boolean isContractArrived()
	{
		return script.isContractArrived();
	}

	public boolean isContractValidated()
	{
		return script.isContractValidated();
	}

	public WorldPoint getContractDestination()
	{
		return script.getContractDestination();
	}

	public int getSessionContracts()
	{
		return sessionContracts;
	}

	public int getLoosePlanks() { return script == null ? 0 : script.getLoosePlanks(); }
	public int getStoredPlanks() { return script == null ? 0 : script.getStoredPlanks(); }
	public int getSteelBars() { return script == null ? 0 : script.getSteelBars(); }

	public int getConstructionXpGained()
	{
		return sessionStats.gained();
	}

	public int getConstructionXpPerHour()
	{
		return sessionStats.perHour(sessionStats.gained());
	}

	public int getContractsPerHour()
	{
		return sessionStats.perHour(sessionContracts);
	}

	public long getSessionRuntimeMillis()
	{
		return sessionStats.runtimeMillis();
	}
}
