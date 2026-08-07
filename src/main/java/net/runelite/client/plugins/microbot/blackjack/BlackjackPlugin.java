package net.runelite.client.plugins.microbot.blackjack;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Blackjack",
        description = "Blackjacks a pre-lured Pollnivneach target",
        authors = {"JogOnJohn"},
        version = BlackjackPlugin.VERSION,
        minClientVersion = "2.1.0",
        tags = {"thieving", "blackjack", "pollnivneach"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BlackjackPlugin extends Plugin
{
    public static final String VERSION = "1.0.2";

    @Inject
    @Getter
    private BlackjackScript script;

    @Inject
    private BlackjackConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BlackjackOverlay overlay;

    @Provides
    BlackjackConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BlackjackConfig.class);
    }

    @Override
    protected void startUp()
    {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        script.onChatMessage(event.getMessage());
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event)
    {
        if (event.getActor() instanceof NPC)
        {
            script.onOverheadTextChanged((NPC) event.getActor(), event.getOverheadText());
        }
    }
}
