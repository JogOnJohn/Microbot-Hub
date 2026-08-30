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
    private static final int HOUSE_OPTIONS_WIDGET_ID = 7602207;
    private static final int CALL_SERVANT_WIDGET_ID = 24248342;
    private ConstructionState state = ConstructionState.Idle;
    private WorldPoint workingTile = null;
    private final ButlerTripTracker butlerTrip = new ButlerTripTracker();
    private long lastHouseRecoveryAttempt;
    private int houseRecoveryAttempts;

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
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                calculateState(config);
                switch (state) {
                    case Build:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        buildSpace(config, actionDelay);
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
        workingTile = null;
        state = ConstructionState.Idle;
        super.shutdown();
    }

    public void grabPlanksWhileWeBuild(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay){
        Rs2NpcModel butler = getButler();
        ButlerTripTracker.Action tripAction = butlerTrip.observe(
                Rs2Dialogue.isInDialogue(), butler != null, System.currentTimeMillis());
        if (tripAction == ButlerTripTracker.Action.WAIT) return;
        if (tripAction == ButlerTripTracker.Action.HANDLE_RETURN_DIALOGUE) {
            Microbot.log("Construction: Butler return dialogue detected");
            butler(config, actionDelay);
            return;
        }
        if (tripAction == ButlerTripTracker.Action.TALK_TO_RETURNED_BUTLER) {
            Microbot.log("Construction: Butler returned without dialogue; reopening it");
            if (butler != null && butler.click("Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000));
                butler(config, actionDelay);
            }
            return;
        }
        if (tripAction == ButlerTripTracker.Action.RETRY_DISPATCH) {
            Microbot.log("Construction: Butler trip timed out; allowing bounded recovery");
        }

        int plankCount = Rs2Inventory.count(config.selectedMode().getPlankItemId());
        if (butler == null) {
            if (plankCount <= Rs2Random.between(0, 18)) butler(config, actionDelay);
            return;
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

    private void buildSpace(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
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
                return;
        }

        if (space == null) return;
        if (space.click("Build")) {
            System.out.println("Interacted with build space: " + space.getId());
            sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
            System.out.println("Pressing key: " + buildKey);
            Rs2Keyboard.keyPress(buildKey); // Ensure this is the correct key for the selected build option
            sleepUntilOnClientThread(() -> spaceId != space.getId(), 2500);
            System.out.println("Built object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with build space: " + space.getId());
        }
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
        if (butlerTrip.isTripInProgress()) return;

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
                Rs2Keyboard.typeString("28");
                butlerTrip.dispatched(System.currentTimeMillis());
                Microbot.log("Construction: Butler bank trip dispatched");
                Rs2Keyboard.enter();
            } else if (hasDialogueOptionToUnnote()) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> !hasDialogueOptionToUnnote());
            } else if (hasPayButlerDialogue() || hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(400, 1000);
                if (hasDialogueOptionToPay()) {
                    Rs2Keyboard.keyPress('1');
                }
            } else if(hasDialogueRepeatLastTask()){
                butlerTrip.dispatched(System.currentTimeMillis());
                Microbot.log("Construction: Repeating Butler bank trip");
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
        boolean dialogueOpened = sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000));
        Microbot.log(dialogueOpened
                ? "Construction: Call Servant opened dialogue"
                : "Construction: Call Servant timed out waiting for dialogue");
        return dialogueOpened;
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
}
