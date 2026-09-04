package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.construction.ConstructionConfig;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConstructionScript extends Script {

    private static final int DEFAULT_DELAY = 600;
    private static final int DEMON_BUTLER_CAPACITY = 26;
    private static final int OVERFLOW_BUILDS_BEFORE_COLLECTION = 2;
    private static final int HOUSE_OPTIONS_WIDGET_ID = 7602207;
    private static final int CALL_SERVANT_WIDGET_ID = 24248342;
    private ConstructionState state = ConstructionState.Idle;
    private WorldPoint workingTile = null;
    private final ButlerTripTracker butlerTrip = new ButlerTripTracker();
    private long lastHouseRecoveryAttempt;
    private int houseRecoveryAttempts;
    private volatile String lastAction = "Starting";
    private volatile String dialogueState = "None";
    private String lastLoggedDialogueState = "None";
    private volatile boolean butlerPresent;
    private volatile int plankCount;
    private volatile int freeSlots;
    private volatile OverflowStage overflowStage = OverflowStage.NONE;
    private int minimumPlanksDuringTrip;
    private int expectedOverflowPlanks;
    private int planksBeforeOverflowCollection;
    private int overflowBuildsRemaining;
    private boolean overflowCollectionObserved;
    private long overflowCollectionObservedAt;
    private long lastOverflowActionAt;

    private enum OverflowStage {
        NONE,
        BUILD_ONE,
        COLLECT,
        SEND_NEXT
    }

    // NOTE: For the arrays below, the first ID is the BUILD OBJECT ID, the second is the EMPTY OBJECT ID
    private static final List<Integer> OAK_DUNGEON_DOOR = List.of(13344, 15328);
    private static final List<Integer> OAK_LARDER = List.of(13566, 15403);
    private static final List<Integer> MAHOGANY_TABLE = List.of(13298, 15298);
    private static final List<Integer> MYTHICAL_CAPE_MOUNT = List.of(15394, 31986);

    public Rs2TileObjectModel getClosestTile(List<Integer> objIDs) {
        int[] ids = objIDs.stream().mapToInt(Integer::intValue).toArray();
        return Microbot.getRs2TileObjectCache().query().withIds(ids).nearest();
    }

    public Rs2NpcModel getButler() {
        return Microbot.getRs2NpcCache().query().withName("Demon butler").nearestOnClientThread();
    }

    public boolean hasDialogueOptionToUnnote() {
        return Rs2Widget.findWidget("Un-note", null) != null;
    }

    public boolean hasDialogueRepeatLastTask() { return Rs2Widget.hasWidget("Repeat last task?"); }

    public boolean hasPayButlerDialogue() {
        return Rs2Widget.findWidget("must render unto me the 10,000 coins that are due", null) != null;
    }

    public boolean hasDialogueOptionToPay() {
        return Rs2Widget.findWidget("Okay, here's 10,000 coins.", null) != null;
    }

    public boolean hasFurnitureInterfaceOpen() {
        Widget furnitureWidget = Rs2Widget.findWidget("Furniture", null);
        if (furnitureWidget != null) {
            System.out.println("Furniture interface is open.");
            return true;
        }
        System.out.println("Furniture interface is not open.");
        return false;
    }

    public boolean hasRemoveDoorInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveLarderInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveTableInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveCapeMountInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean run(net.runelite.client.plugins.microbot.construction.ConstructionConfig config) {
        int actionDelay = config.useCustomDelay() ? config.actionDelay() : DEFAULT_DELAY;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                updateDebugSnapshot(config);
                if (hasOverflowDialogue()) {
                    handleOverflowDialogue(config);
                    return;
                }
                if (overflowStage != OverflowStage.NONE) {
                    processOverflowCycle(config, actionDelay);
                    return;
                }
                if (Rs2Dialogue.isInDialogue()) {
                    dialogueState = getDialogueState();
                    butler(config, actionDelay);
                    return;
                }
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                calculateState(config);
                switch (state) {
                    case Build:
                        if (grabPlanksWhileWeBuild(config, actionDelay)) {
                            buildSpace(config, actionDelay);
                        }
                        break;
                    case Remove:
                        removeSpace(config, actionDelay);
                        break;
                    case Butler:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error in scheduled task: " + ex.getMessage());
            }
        }, 0, actionDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        butlerTrip.reset();
        overflowStage = OverflowStage.NONE;
        workingTile = null;
        state = ConstructionState.Idle;
        super.shutdown();
    }

    public boolean grabPlanksWhileWeBuild(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay){
        Rs2NpcModel butler = getButler();
        ButlerTripTracker.Action tripAction = butlerTrip.observe(
                Rs2Dialogue.isInDialogue(), butler != null, System.currentTimeMillis());
        if (tripAction == ButlerTripTracker.Action.WAIT) return true;
        if (tripAction == ButlerTripTracker.Action.HANDLE_RETURN_DIALOGUE) {
            logAction("Handling Butler return dialogue: " + getDialogueState());
            butler(config, actionDelay);
            return false;
        }
        if (tripAction == ButlerTripTracker.Action.TALK_TO_RETURNED_BUTLER) {
            logAction("Butler returned without dialogue; reopening it");
            if (butler != null && butler.click("Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000));
                butler(config, actionDelay);
            }
            return false;
        }
        if (tripAction == ButlerTripTracker.Action.CLICK_CURRENT_TILE) {
            WorldPoint currentTile = Rs2Player.getWorldLocation();
            logAction("Demon butler has not returned after 10 seconds; clicking current tile " + currentTile);
            if (currentTile == null || !Rs2Walker.walkFastCanvas(currentTile)) {
                logAction("Could not click current tile; continuing to wait without calling servant");
            }
            return false;
        }

        int plankCount = Rs2Inventory.count(config.selectedMode().getPlankItemId());
        if (butler == null) {
            if (plankCount <= Rs2Random.between(0, 18)) butler(config, actionDelay);
            return true;
        }
        sleepUntil(() -> {
            Rs2NpcModel current = getButler();
            return current != null && current.isInteractingWithPlayer();
        }, Rs2Random.between(750,1500));
        butler = getButler();
        if ((butler != null && butler.isInteractingWithPlayer())
                || plankCount <= Rs2Random.between(0, 18)) {
            butler(config, actionDelay);
        }
        return true;
    }

    private void calculateState(net.runelite.client.plugins.microbot.construction.ConstructionConfig config) {
        boolean hasRequiredPlanks;
        Rs2NpcModel butler = getButler();
        List<Integer> objectIDs = List.of(0);
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                objectIDs = OAK_DUNGEON_DOOR;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 10);
                break;
            case OAK_LARDER:
                objectIDs = OAK_LARDER;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 8);
                break;
            case MAHOGANY_TABLE:
                objectIDs = MAHOGANY_TABLE;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 6);
                break;
            default:
                return;
        }

        Rs2TileObjectModel closest = getClosestTile(objectIDs);
        if (closest == null) {
            setState(ConstructionState.Idle);
            returnToTheHouse();
            return;
        }
        houseRecoveryAttempts = 0;

        if (workingTile == null) {
            workingTile = closest.getWorldLocation();
        }

        Rs2TileObjectModel objOnWorkingTile = Microbot.getRs2TileObjectCache().query()
                .where(o -> o.getWorldLocation().equals(workingTile))
                .nearest();
        if (objOnWorkingTile == null || !objectIDs.contains(objOnWorkingTile.getId())) {
            closest = getClosestTile(objectIDs);
            if (closest == null) {
                setState(ConstructionState.Idle);
                returnToTheHouse();
                return;
            }
            workingTile = closest.getWorldLocation();
            objOnWorkingTile = Microbot.getRs2TileObjectCache().query()
                    .where(o -> o.getWorldLocation().equals(workingTile))
                    .nearest();
        }

        if (objOnWorkingTile == null) {
            setState(ConstructionState.Idle);
            returnToTheHouse();
        } else if (objOnWorkingTile.getId() == objectIDs.get(0)) {
            setState(ConstructionState.Remove);
        } else if (objOnWorkingTile.getId() == objectIDs.get(1) && hasRequiredPlanks) {
            setState(ConstructionState.Build);
        } else if (objOnWorkingTile.getId() == objectIDs.get(1) && butler != null) {
            setState(ConstructionState.Butler);
        } else if (!objectIDs.contains(objOnWorkingTile.getId())) {
            setState(ConstructionState.Idle);
            Microbot.getNotifier().notify("Looks like we are no longer in our house.");
            returnToTheHouse();
        }
    }

    private void returnToTheHouse(){
        long now = System.currentTimeMillis();
        if (now - lastHouseRecoveryAttempt < 5_000L) return;
        lastHouseRecoveryAttempt = now;
        houseRecoveryAttempts++;
        Microbot.log("Construction: Outside POH recovery attempt %d", houseRecoveryAttempts);

        Rs2TileObjectModel housePortal = Microbot.getRs2TileObjectCache().query().withName("Portal").nearestOnClientThread();
        if(housePortal != null){
            if(housePortal.click("Build mode")){
                boolean returned = sleepUntil(()-> Rs2Player.getWorldLocation() != null
                        && Rs2Player.getWorldLocation().getRegionX() == 29
                            && Rs2Player.getWorldLocation().getRegionY() == 89, Rs2Random.between(10000,20000));
                if (returned) {
                    houseRecoveryAttempts = 0;
                    workingTile = null;
                    Microbot.log("Construction: Returned to POH build mode");
                    sleep(2000,5000);
                } else {
                    Microbot.log("Construction: Timed out returning to POH");
                }
            }
        } else if (houseRecoveryAttempts >= 3) {
            Microbot.log("Construction: House portal unavailable after bounded retries; stopping");
            Microbot.getNotifier().notify("Can't find the house portal!");
            shutdown();
        } else {
            Microbot.log("Construction: House portal unavailable; retrying later");
        }
    }

    private void setState(ConstructionState nextState) {
        if (state != nextState) {
            Microbot.log("Construction state: %s -> %s", state, nextState);
            state = nextState;
        }
    }

    private boolean buildSpace(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
        Rs2TileObjectModel space = Microbot.getRs2TileObjectCache().query()
                .where(o -> o.getWorldLocation().equals(workingTile))
                .nearest();
        int spaceId = space != null ? space.getId() : -1;
        char buildKey = '1';

        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                buildKey = '1';
                break;
            case OAK_LARDER:
                buildKey = '2';
                break;
            case MAHOGANY_TABLE:
                buildKey = '6';
                break;
            default:
                return false;
        }

        if (space == null) return false;
        int planksBeforeBuild = Rs2Inventory.count(config.selectedMode().getPlankItemId());
        if (space.click("Build")) {
            System.out.println("Interacted with build space: " + space.getId());
            sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
            System.out.println("Pressing key: " + buildKey);
            Rs2Keyboard.keyPress(buildKey); // Ensure this is the correct key for the selected build option
            sleepUntilOnClientThread(() -> spaceId != space.getId(), 2500);
            System.out.println("Built object: " + config.selectedMode());
            return Rs2Inventory.count(config.selectedMode().getPlankItemId()) < planksBeforeBuild;
        } else {
            System.out.println("Failed to interact with build space: " + space.getId());
        }
        return false;
    }

    private void removeSpace(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
        Rs2TileObjectModel builtObject = Microbot.getRs2TileObjectCache().query()
                .where(o -> o.getWorldLocation().equals(workingTile))
                .nearest();
        int spaceId = builtObject != null ? builtObject.getId() : -1;

        if (builtObject == null) return;
        if(builtObject.getId() == 15328 || builtObject.getId() == 15403 || builtObject.getId() == 15298 || builtObject.getId() == 31986) return;

        if (builtObject.click("Remove")) {
            System.out.println("Interacted with remove option: " + builtObject.getId());
            sleepUntilOnClientThread(() -> hasRemoveInterfaceOpen(config), 2500);
            Rs2Keyboard.keyPress('1');
            sleepUntilOnClientThread(() -> spaceId != builtObject.getId(), 2500);
            System.out.println("Removed object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with remove option: " + builtObject.getId());
        }
    }

    private void butler(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
        var butler = getButler();
        if (butlerTrip.isTripInProgress() && !Rs2Dialogue.isInDialogue()) return;

        if (!Rs2Dialogue.isInDialogue() && (butler == null || !butler.isInteractingWithPlayer())) {
            if (!callServant()) return;
            butler = getButler();
        } else if (!Rs2Dialogue.isInDialogue() && butler != null) {
            if (!butler.click("Talk-to")
                    || !sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000))) return;
        }

        if (Rs2Dialogue.isInDialogue()) {
            sleep(500);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(400, 1000);
            if (Rs2Widget.findWidget("Go to the bank", null) != null) {
                if (butler == null) return;
                Rs2Inventory.useItemOnNpc(config.selectedMode().getPlankItemId() + 1, butler.getId());
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Dost thou wish me to exchange that certificate"));
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Select an option"));
                Rs2Keyboard.typeString("1");
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Enter amount:"));
                minimumPlanksDuringTrip = Rs2Inventory.count(config.selectedMode().getPlankItemId());
                Rs2Keyboard.typeString(Integer.toString(DEMON_BUTLER_CAPACITY));
                butlerTrip.dispatched(System.currentTimeMillis());
                logAction("Butler bank trip dispatched for " + DEMON_BUTLER_CAPACITY
                        + " planks (carrying " + minimumPlanksDuringTrip + ")");
                Rs2Keyboard.enter();
            } else if (hasDialogueOptionToUnnote()) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> !hasDialogueOptionToUnnote());
                butlerTrip.reset();
                logAction("Selected Butler un-note service");
            } else if (hasPayButlerDialogue() || hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(400, 1000);
                if (hasDialogueOptionToPay()) {
                    Rs2Keyboard.keyPress('1');
                }
            } else if(hasDialogueRepeatLastTask()){
                minimumPlanksDuringTrip = Rs2Inventory.count(config.selectedMode().getPlankItemId());
                butlerTrip.dispatched(System.currentTimeMillis());
                logAction("Repeating Butler bank trip (carrying " + minimumPlanksDuringTrip + ")");
                Rs2Keyboard.keyPress('1');
            }
        }
    }

    private boolean callServant() {
        if (butlerTrip.isTripInProgress()) return false;
        Rs2Tab.switchTo(InterfaceTab.SETTINGS);

        boolean houseOptionsAvailable = sleepUntil(() ->
                        Rs2Widget.isWidgetVisible(HOUSE_OPTIONS_WIDGET_ID)
                                || Rs2Widget.findWidget("House Options", null) != null,
                Rs2Random.between(2000, 5000));
        if (!houseOptionsAvailable) {
            Microbot.log("Construction: House Options did not appear");
            return false;
        }
        Widget houseOptions = Rs2Widget.getWidget(HOUSE_OPTIONS_WIDGET_ID);
        if (houseOptions == null) houseOptions = Rs2Widget.findWidget("House Options", null);
        if (houseOptions == null || !Rs2Widget.clickWidget(houseOptions)) {
            Microbot.log("Construction: Failed to click House Options");
            return false;
        }

        boolean callServantAvailable = sleepUntil(() ->
                        Rs2Widget.isWidgetVisible(CALL_SERVANT_WIDGET_ID)
                                || Rs2Widget.findWidget("Call Servant", null) != null,
                Rs2Random.between(2000, 5000));
        if (!callServantAvailable) {
            Microbot.log("Construction: Call Servant did not appear");
            return false;
        }
        Widget callServant = Rs2Widget.getWidget(CALL_SERVANT_WIDGET_ID);
        if (callServant == null) callServant = Rs2Widget.findWidget("Call Servant", null);
        if (callServant == null || !Rs2Widget.clickWidget(callServant)) {
            Microbot.log("Construction: Failed to click Call Servant");
            return false;
        }
        butlerTrip.servantRequested(System.currentTimeMillis());
        logAction("Call Servant request dispatched; suppressing duplicate calls");
        boolean dialogueOpened = sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000));
        Microbot.log(dialogueOpened
                ? "Construction: Call Servant opened dialogue"
                : "Construction: Call Servant timed out waiting for dialogue");
        return true;
    }

    private boolean hasOverflowDialogue() {
        return Rs2Dialogue.hasDialogueOption("Take them back to the bank")
                || Rs2Dialogue.hasDialogueOption("Thanks");
    }

    private void handleOverflowDialogue(ConstructionConfig config) {
        long now = System.currentTimeMillis();
        if (now - lastOverflowActionAt < 2_000L) return;
        lastOverflowActionAt = now;

        int currentPlanks = Rs2Inventory.count(config.selectedMode().getPlankItemId());
        if (overflowStage == OverflowStage.COLLECT) {
            overflowBuildsRemaining = 1;
            overflowCollectionObserved = false;
            overflowStage = OverflowStage.BUILD_ONE;
            logAction("Butler still has overflow after collection attempt; building one more before retrying");
        } else if (overflowStage == OverflowStage.SEND_NEXT) {
            overflowBuildsRemaining = 1;
            overflowCollectionObserved = false;
            overflowStage = OverflowStage.BUILD_ONE;
            logAction("Late overflow dialogue detected; building one more before retrying collection");
        } else {
            int delivered = Math.max(0, currentPlanks - minimumPlanksDuringTrip);
            expectedOverflowPlanks = Math.max(1, DEMON_BUTLER_CAPACITY - delivered);
            overflowBuildsRemaining = OVERFLOW_BUILDS_BEFORE_COLLECTION;
            overflowCollectionObserved = false;
            overflowStage = OverflowStage.BUILD_ONE;
            butlerTrip.reset();
            logAction("Inventory full; Butler is holding at least " + expectedOverflowPlanks
                    + " overflow planks. Dismissing dialogue to build twice");
        }

        if (!Rs2Dialogue.clickOption("Thanks")) {
            logAction("Could not select Thanks on overflow dialogue");
            return;
        }

        sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 3_000);
        dialogueState = "None";
    }

    private void processOverflowCycle(ConstructionConfig config, int actionDelay) {
        if (overflowStage == OverflowStage.BUILD_ONE) {
            if (Rs2Dialogue.isInDialogue()) return;
            calculateState(config);
            if (state == ConstructionState.Remove) {
                removeSpace(config, actionDelay);
                return;
            }
            if (state == ConstructionState.Build && buildSpace(config, actionDelay)) {
                overflowBuildsRemaining--;
                if (overflowBuildsRemaining > 0) {
                    logAction("Built first " + config.selectedMode() + " for overflow; "
                            + overflowBuildsRemaining + " more build needed before collection");
                } else {
                    planksBeforeOverflowCollection = Rs2Inventory.count(config.selectedMode().getPlankItemId());
                    overflowCollectionObserved = false;
                    overflowStage = OverflowStage.COLLECT;
                    logAction("Built enough space; collecting held overflow planks (planks="
                            + planksBeforeOverflowCollection + ", free=" + Rs2Inventory.emptySlotCount() + ")");
                }
            }
            return;
        }

        if (overflowStage == OverflowStage.COLLECT) {
            int currentPlanks = Rs2Inventory.count(config.selectedMode().getPlankItemId());
            if (currentPlanks > planksBeforeOverflowCollection) {
                if (!overflowCollectionObserved) {
                    overflowCollectionObserved = true;
                    overflowCollectionObservedAt = System.currentTimeMillis();
                    logAction("Received held planks; waiting for Butler dialogue to settle");
                }
                if (!Rs2Dialogue.isInDialogue()
                        && System.currentTimeMillis() - overflowCollectionObservedAt >= 1_500L) {
                    overflowStage = OverflowStage.SEND_NEXT;
                    logAction("Held overflow collection confirmed; sending Butler for the next load");
                }
                return;
            }
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> Rs2Inventory.count(config.selectedMode().getPlankItemId())
                        > planksBeforeOverflowCollection || hasOverflowDialogue(), 3_000);
                return;
            }
            if (Rs2Dialogue.isInDialogue()) return;

            long now = System.currentTimeMillis();
            if (now - lastOverflowActionAt < 3_000L) return;
            lastOverflowActionAt = now;
            Rs2NpcModel butler = getButler();
            if (butler == null) {
                logAction("Waiting for Butler to collect held overflow planks");
                return;
            }
            logAction("Talking to Butler to collect held overflow planks");
            if (butler.click("Talk-to")) {
                sleepUntil(() -> Rs2Dialogue.isInDialogue()
                        || Rs2Inventory.count(config.selectedMode().getPlankItemId())
                        > planksBeforeOverflowCollection, Rs2Random.between(2000, 5000));
            }
            return;
        }

        if (overflowStage == OverflowStage.SEND_NEXT) {
            butler(config, actionDelay);
            if (butlerTrip.isTripInProgress()) {
                overflowStage = OverflowStage.NONE;
                logAction("Overflow cycle complete; Butler dispatched for the next 26 planks");
            }
        }
    }

    private String getDialogueState() {
        if (hasOverflowDialogue()) return "Overflow planks";
        if (hasDialogueOptionToUnnote()) return "Un-note delivery";
        if (hasPayButlerDialogue() || hasDialogueOptionToPay()) return "Butler payment";
        if (hasDialogueRepeatLastTask()) return "Repeat last task";
        if (Rs2Widget.findWidget("Go to the bank", null) != null) return "Bank request";
        if (Rs2Dialogue.hasSelectAnOption()) return "Unknown options";
        return Rs2Dialogue.isInDialogue() ? "Continue" : "None";
    }

    private void updateDebugSnapshot(ConstructionConfig config) {
        butlerPresent = getButler() != null;
        plankCount = Rs2Inventory.count(config.selectedMode().getPlankItemId());
        freeSlots = Rs2Inventory.emptySlotCount();
        dialogueState = getDialogueState();
        if (butlerTrip.isTripInProgress() && "None".equals(dialogueState)) {
            minimumPlanksDuringTrip = Math.min(minimumPlanksDuringTrip, plankCount);
        }
        if (!dialogueState.equals(lastLoggedDialogueState)) {
            Microbot.log("Construction dialogue: %s -> %s (planks=%d, free=%d, overflow=%s)",
                    lastLoggedDialogueState, dialogueState, plankCount, freeSlots, overflowStage);
            lastLoggedDialogueState = dialogueState;
        }
    }

    private void logAction(String action) {
        if (action.equals(lastAction)) return;
        lastAction = action;
        Microbot.log("Construction: " + action);
    }

    private boolean hasRemoveInterfaceOpen(ConstructionConfig config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return hasRemoveDoorInterfaceOpen();
            case OAK_LARDER:
                return hasRemoveLarderInterfaceOpen();
            case MAHOGANY_TABLE:
                return hasRemoveTableInterfaceOpen();
            // case MYTHICAL_CAPE:
            // return hasRemoveCapeMountInterfaceOpen();
            default:
                return false;
        }
    }

    public ConstructionState getState() {
        return state;
    }

    public String getButlerFlow() { return butlerTrip.getStatus(System.currentTimeMillis()); }
    public String getOverflowFlow() {
        return overflowStage == OverflowStage.NONE
                ? overflowStage.name()
                : overflowStage.name() + " (>= " + expectedOverflowPlanks
                + " held, builds " + overflowBuildsRemaining + ")";
    }
    public String getDialogueStateForOverlay() { return dialogueState; }
    public String getLastAction() { return lastAction; }
    public boolean isButlerPresent() { return butlerPresent; }
    public int getPlankCount() { return plankCount; }
    public int getFreeSlots() { return freeSlots; }
}
