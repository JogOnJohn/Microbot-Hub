package net.runelite.client.plugins.microbot.autobankstander;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.EnchantingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarsProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous.ContinuousHerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.FletchingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class AutoBankStanderScript extends Script {
    private static final String PLUGIN_HEARTBEAT_KEY = AutoBankStanderPlugin.class.getName();
    private volatile AutoBankStanderState state = AutoBankStanderState.INITIALIZING;
    private volatile ConfigData configData;
    private long stateStartTime = System.currentTimeMillis(); // remember when we started this state for timeout checking
    private volatile BankStandingProcessor processor;
    private AutoBankStanderPlugin plugin;
    private final AtomicLong loopCount = new AtomicLong();
    private volatile long startedAt;
    private volatile String lastAction = "Stopped";

    public boolean run(ConfigData configData) {
        this.configData = configData; // save the config data so we can use it later
        this.state = AutoBankStanderState.INITIALIZING; // reset state to beginning
        this.stateStartTime = System.currentTimeMillis(); // reset state timer
        this.startedAt = this.stateStartTime;
        this.loopCount.set(0);
        this.lastAction = "Starting";
        this.processor = null; // clear any existing processor
        log.info("Starting Auto Bank Stander script with config: {}", configData);
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                boolean readyToRun = super.run();
                ScriptHeartbeatRegistry.recordHeartbeat(PLUGIN_HEARTBEAT_KEY);
                loopCount.incrementAndGet();
                if (processor != null && Microbot.isLoggedIn()) {
                    processor.refreshDiagnostics();
                }
                if (!readyToRun) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2Player.isMoving()) {
                    log.info("Player is moving, waiting");
                    return;
                }

                boolean activeBatch = state == AutoBankStanderState.PROCESSING
                        && processor != null && processor.isActivelyProcessing();
                if (state != AutoBankStanderState.PROCESSING && Rs2Player.isAnimating()) {
                    log.info("Player is animating outside processing, waiting");
                    return;
                }

                long startTime = System.currentTimeMillis(); // remember when this loop started

                // state timeout protection
                if (System.currentTimeMillis() - stateStartTime > 30000
                        && state != AutoBankStanderState.PROCESSING && !activeBatch) {
                    log.info("State timeout after 30 seconds, resetting to INITIALIZING");
                    changeState(AutoBankStanderState.INITIALIZING);
                    return;
                }

                switch (state) {
                    case INITIALIZING: handleInitializing(); break; // handle the setup phase
                    case BANKING: handleBanking(); break; // handle banking operations
                    case PROCESSING: handleProcessing(); break; // handle the processing activity
                    case ERROR_RECOVERY: handleErrorRecovery(); break; // handle error situations
                }

                long endTime = System.currentTimeMillis(); // remember when this loop ended
                long totalTime = endTime - startTime; // calculate how long this loop took
                log.info("Total time for loop: {}ms", totalTime);
            } catch (Exception ex) {
                log.error("Error in main script loop: {}", ex.getMessage(), ex);
                changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery on exception
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleInitializing() {
        log.info("State: INITIALIZING");
        Microbot.status = "Initializing..."; // tell the user we are starting up
        lastAction = "Creating processor";
        
        // create the appropriate processor based on config data
        processor = createProcessor();
        if (processor == null) {
            log.info("Failed to create processor for skill: {}", configData.getSkill());
            shutdown(); // stop the plugin
            return;
        }
        
        // validate processor configuration
        if (!processor.validate()) {
            log.info("Processor validation failed");
            shutdown(); // stop the plugin
            return;
        }
        
        log.info("Initialization complete - switching to banking");
        changeState(AutoBankStanderState.BANKING); // switch to banking to get our supplies
    }

    private void handleBanking() {
        log.info("State: BANKING");
        Microbot.status = "Banking..."; // tell the user we are handling banking
        lastAction = "Banking";
        
        if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
            log.info("Not near bank - walking to bank");
            Rs2Bank.walkToBank(); // walk to the nearest bank
            return;
        }
        
        if (!Rs2Bank.isOpen()) { // if the bank interface isn't open yet
            log.info("Opening bank");
            Rs2Bank.openBank(); // click to open the bank
            boolean opened = sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank opens
            if (!opened) {
                log.info("Failed to open bank within timeout");
            }
            return;
        }
        
        // check if we already have all the required items
        if (processor.hasRequiredItems()) {
            log.info("Have all required items - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
            return;
        }
        
        // perform banking operations via processor
        boolean bankingSuccess = processor.performBanking();
        if (bankingSuccess) {
            log.info("Banking complete - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
        } else {
            log.info("Banking failed - no required items available, shutting down");
            Microbot.status = "No required items available";
            shutdown(); // stop the plugin
        }
    }

    private void handleProcessing() {
        log.info("State: PROCESSING");
        Microbot.status = processor.getStatusMessage(); // tell the user what we are doing
        lastAction = processor.getStatusMessage();
        
        // check if we can continue processing
        if (!processor.canContinueProcessing()) {
            log.info("Cannot continue processing - shutting down");
            shutdown(); // stop the plugin
            return;
        }
        
        // check if processor is actively making items - don't interrupt with banking
        if (processor.isActivelyProcessing()) {
            log.info("Processor is actively making items - waiting");
            return;
        }
        
        // check if we need to go back to banking
        if (!processor.hasRequiredItems()) {
            log.info("Missing required items - going back to banking");
            changeState(AutoBankStanderState.BANKING); // go back to banking to get more items
            return;
        }
        
        // perform the processing activity
        boolean processingSuccess = processor.process();
        if (!processingSuccess) {
            log.info("Processing failed - going to error recovery");
            changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery
        }
    }

    private void handleErrorRecovery() {
        log.info("State: ERROR_RECOVERY");
        Microbot.status = "Recovering from error..."; // tell the user we are fixing issues
        lastAction = "Error recovery";
        
        // check for timeout
        if (System.currentTimeMillis() - stateStartTime > 60000) { // if we've been stuck for more than 60 seconds
            log.info("State timeout - resetting to initializing");
            changeState(AutoBankStanderState.INITIALIZING); // go back to the beginning
            return;
        }
        
        // try to recover by going back to banking
        log.info("Attempting recovery - going to banking");
        changeState(AutoBankStanderState.BANKING); // try to recover by going to banking
    }

    private BankStandingProcessor createProcessor() {
        SkillType skill = configData.getSkill();
        log.info("Creating processor for skill: {}", skill);
        switch (skill) {
            case MAGIC:
                return createMagicProcessor();
            case HERBLORE:
                log.info("Creating herblore processor for mode: {}", configData.getHerbloreMode());
                if (configData.getHerbloreMode() == Mode.CONTINUOUS) {
                    return new ContinuousHerbloreProcessor(configData);
                }
                return new HerbloreProcessor(
                    configData.getHerbloreMode(),
                    configData.getCleanHerbMode(),
                    configData.getUnfinishedPotionMode(),
                    configData.getFinishedPotion(),
                    configData.isUseAmuletOfChemistry(),
                    configData.getHerbCleaningMode(),
                    configData.getHerbloreTurboLimit(),
                    configData.getHerbloreSleepMin(),
                    configData.getHerbloreSleepMax(),
                    configData.getHerbloreSleepTarget(),
                    configData.getReverseIngredientChance(),
                    configData.getBatchMicroBreakChance(),
                    configData.getBatchMicroBreakMinMs(),
                    configData.getBatchMicroBreakMaxMs()
                );
            case FLETCHING:
                log.info("Entering fletching processor creation");
                return createFletchingProcessor();
            default:
                log.info("Skill not yet implemented: {}", skill);
                return null;
        }
    }
    
    private BankStandingProcessor createMagicProcessor() {
        MagicMethod method = configData.getMagicMethod();
        switch (method) {
            case ENCHANTING:
                log.info("Creating enchanting processor for bolt type: {}", configData.getBoltType());
                return new EnchantingProcessor(configData.getBoltType());
            case LUNARS:
                log.info("Creating lunars processor");
                return new LunarsProcessor();
            case ALCHING:
                log.info("Alchemy processor not yet implemented");
                return null;
            case SUPERHEATING:
                log.info("Superheating processor not yet implemented");
                return null;
            default:
                log.info("Unknown magic method: {}", method);
                return null;
        }
    }
    
    private BankStandingProcessor createFletchingProcessor() {
        FletchingMode mode = configData.getFletchingMode();
        log.info("Creating fletching processor for mode: {}", mode);
        log.info("Fletching config details - dart: {}, bolt: {}, arrow: {}, javelin: {}, bow: {}, crossbow: {}, shield: {}", 
            configData.getDartType(), configData.getFletchingBoltType(), configData.getArrowType(), 
            configData.getJavelinType(), configData.getBowType(), configData.getCrossbowType(), configData.getShieldType());
        
        FletchingProcessor processor = new FletchingProcessor(
            mode,
            configData.getDartType(),
            configData.getFletchingBoltType(),
            configData.getArrowType(),
            configData.getJavelinType(),
            configData.getBowType(),
            configData.getCrossbowType(),
            configData.getShieldType()
        );
        
        log.info("Fletching processor created successfully");
        return processor;
    }

    // helper method to change state with timeout reset
    private void changeState(AutoBankStanderState newState) {
        if (newState != state) { // if we are actually changing to a different state
            log.info("State change: {} -> {}", state, newState);
            state = newState; // update our current state
            stateStartTime = System.currentTimeMillis(); // reset our timeout timer for the new state
            lastAction = "State " + newState;
        }
    }

    public void setPlugin(AutoBankStanderPlugin plugin) {
        this.plugin = plugin;
    }

    public void onGameMessage(String message) {
        BankStandingProcessor currentProcessor = processor;
        if (currentProcessor != null) {
            currentProcessor.onGameMessage(message);
        }
    }

    public String getStateName() {
        return isRunning() ? state.name() : "STOPPED";
    }

    public String getTaskName() {
        ConfigData current = configData;
        return current == null ? "Not configured" : current.toString();
    }

    public long getLoopCount() {
        return loopCount.get();
    }

    public long getStartedAt() {
        return startedAt;
    }

    public String getLastAction() {
        return lastAction;
    }

    public BankStandingProcessor getProcessor() {
        return processor;
    }

    @Override
    public void shutdown() {
        ScriptHeartbeatRegistry.remove(PLUGIN_HEARTBEAT_KEY);
        if (!isRunning()) {
            log.info("Script already shutdown, ignoring");
            return;
        }
        log.info("Shutting down Auto Bank Stander script");
        lastAction = "Stopped";
        super.shutdown(); // clean up the script properly
        
        // notify plugin to update panel state
        if (plugin != null) {
            plugin.updatePanelState();
        }
        
        log.info("Auto Bank Stander script shutdown complete");
    }
}
