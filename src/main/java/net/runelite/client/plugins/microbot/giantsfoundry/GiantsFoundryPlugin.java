package net.runelite.client.plugins.microbot.giantsfoundry;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GiantsFoundry",
        description = "Microbot giants foundry plugin",
        tags = {"minigame", "microbot", "smithing"},
        version = GiantsFoundryPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GiantsFoundryPlugin extends Plugin {

    public static final String version = "1.4.0";

    @Inject
    private GiantsFoundryConfig config;

    @Provides
    GiantsFoundryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantsFoundryConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GiantsFoundryOverlay giantsFoundryOverlay;

    private final GiantsFoundryScript giantsFoundryScript = new GiantsFoundryScript();

    @Override
    protected void startUp() throws AWTException {
        previousHeat = GiantsFoundryState.getHeatAmount();
        if (overlayManager != null) {
            overlayManager.add(giantsFoundryOverlay);
        }
        giantsFoundryScript.run(config);
    }

    // previous heat varbit value, used to filter out passive heat decay.
    private int previousHeat = 0;
    private static final int VARBIT_HEAT = 13948;
    private static final int VARBIT_PROGRESS = 13949;
    // stage derived from the last progress varbit update, for same-tick flip detection
    private Stage eventStage;

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {


        // start the heating state-machine when the varbit updates
        // if heat varbit updated and the user clicked, start the state-machine
        if (event.getVarbitId() == VARBIT_HEAT)
        {
            // ignore passive heat decay, one heat per two ticks
            if (event.getValue() - previousHeat != -1)
            {

                GiantsFoundryState.heatingCoolingState.onTick();
            }
            previousHeat = event.getValue();
        }
        else if (event.getVarbitId() == VARBIT_PROGRESS)
        {
            handleProgressChanged(event.getValue());
        }
    }

    /**
     * Detects a workstation-stage flip on the same game tick it happens and lets the
     * script interrupt the old station immediately. The 300ms polled interrupt loses
     * the race against the grindstone's 2-tick swing (-5 quality per loss).
     */
    private void handleProgressChanged(int progress)
    {
        if (progress <= 0)
        {
            // hand-in or reset, not a stage transition
            eventStage = null;
            return;
        }
        State state = GiantsFoundryScript.state;
        if (state != State.CRAFTING_WEAPON && state != State.HEATING && state != State.COOLING_DOWN)
        {
            eventStage = GiantsFoundryState.getCurrentStage(progress);
            return;
        }
        Stage stage = GiantsFoundryState.getCurrentStage(progress);
        if (stage != null && eventStage != null && stage != eventStage)
        {
            giantsFoundryScript.onStageFlip(stage);
        }
        if (stage != null)
        {
            eventStage = stage;
        }
    }



    protected void shutDown() {
        giantsFoundryScript.shutdown();
        overlayManager.remove(giantsFoundryOverlay);
    }
}
