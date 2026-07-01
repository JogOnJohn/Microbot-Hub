package net.runelite.client.plugins.microbot.housetab;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.housetab.enums.HOUSETABS_CONFIG;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "HouseTab",
        description = "Microbot HouseTab plugin",
        tags = {"microbot", "magic", "moneymaking"},
        version = HouseTabPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class HouseTabPlugin extends Plugin {
    public static final String version = "1.0.50";

    @Inject
    private HouseTabConfig config;

    @Provides
    HouseTabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseTabConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseTabOverlay houseTabOverlay;

    private HouseTabScript houseTabScript;
    private int loggedInTicks = 0;
    private boolean overlayAdded = false;
    private long startupAt = 0;

    @Override
    protected void startUp() throws AWTException {
        startupAt = System.currentTimeMillis();
        Microbot.log("HouseTabPlugin: startUp invoked; script will wait for stable logged-in game state.");
    }

    private void startScriptIfLoggedIn() {
        if (Microbot.getClient().getGameState() != GameState.LOGGED_IN || !Microbot.isLoggedIn()) {
            loggedInTicks = 0;
            return;
        }
        if (Microbot.getClient().getLocalPlayer() == null) {
            loggedInTicks = 0;
            Microbot.log("HouseTabPlugin: login detected, waiting for local player before script start.");
            return;
        }
        if (Microbot.getClient().getLocalPlayer().getWorldLocation() == null) {
            loggedInTicks = 0;
            Microbot.log("HouseTabPlugin: login detected, waiting for player world location before script start.");
            return;
        }
        if (System.currentTimeMillis() - startupAt < 12000) {
            loggedInTicks = 0;
            return;
        }
        if (loggedInTicks < 20) {
            loggedInTicks++;
            return;
        }
        if (!overlayAdded && overlayManager != null) {
            overlayManager.add(houseTabOverlay);
            overlayAdded = true;
        }
        if (houseTabScript != null && houseTabScript.isRunning()) {
            return;
        }
        if (houseTabScript != null && !houseTabScript.getStopReason().isBlank()) {
            return;
        }
        Microbot.log("HouseTabPlugin: logged in, creating fresh script instance.");
        houseTabScript = new HouseTabScript(HOUSETABS_CONFIG.FRIENDS_HOUSE,
                new String[]{"xGrace", "workless", "Lego Batman", "Batman 321", "Batman Chest"});
        boolean started = houseTabScript.run(config);
        Microbot.log("HouseTabPlugin: script run returned " + started);
    }

    protected void shutDown() {
        Microbot.log("HouseTabPlugin: shutDown invoked.");
        if (houseTabScript != null) {
            houseTabScript.shutdown();
        }
        if (overlayManager != null && overlayAdded) {
            overlayManager.remove(houseTabOverlay);
            overlayAdded = false;
        }
        loggedInTicks = 0;
    }

    HouseTabScript getHouseTabScript() {
        return houseTabScript;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains("That player is offline")) {
            Microbot.showMessage("Player is offline.");
            if (houseTabScript != null) {
                houseTabScript.handlePlayerHouseOffline(config.useAdvertisementBoard());
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        startScriptIfLoggedIn();
    }
}
