package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmLanes;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState.*;
import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasSelectAnOption;

/**
 * TODO list:
 * -plants per hour
 * -check for seed dibber and spade inventory
 * -deposit sack
 * -test other plants
 * -move script into seperate folder
 */


public class TitheFarmingScript extends Script {

    final int FARM_DOOR = 27445;
    final String FERTILISER = "gricoller's fertiliser";

    public static List<TitheFarmPlant> plants = new ArrayList<>();


    public static TitheFarmState state = TitheFarmState.STARTING;

    public static int initialFruit = 0;
    public static int fruits = 0;

    public static final int WATERING_CANS_AMOUNT = 8;
    private List<TitheFarmPlant> routePlants = new ArrayList<>();

    public static final int DISTANCE_THRESHOLD_MINIMAP_WALK = 8;

    public static int gricollerCanCharges = -1;

    public static boolean init = true;

    private boolean allPlanted = false;
    // One ordered pass for planting/first water, two watering passes, then harvest.
    private int cropPass = 0;
    private int cropCursor = 0;
    private boolean breakLockHeldByTithe = false;
    private boolean loginReconciliationPending = false;
    private long loginReconciliationReadyAt = 0L;
    private CropAction cropAction = CropAction.IDLE;
    private int cropActionPlantIndex = Integer.MIN_VALUE;
    private long cropActionDeadline = 0L;

    private enum CropAction {
        IDLE,
        WAITING_FOR_SEED_SELECTION,
        WAITING_FOR_PLANT,
        WAITING_FOR_WATERING_CAN_SELECTION,
        WAITING_FOR_WATER,
        WAITING_FOR_HARVEST,
        WAITING_FOR_CLEAR
    }

    public void init(TitheFarmingConfig config) {
        TitheFarmLanes lane = config.Lanes();

        if (lane == TitheFarmLanes.Randomize) {
            lane = TitheFarmLanes.values()[Rs2Random.betweenInclusive(0, TitheFarmLanes.values().length - 1)];
        }

        switch (lane) {
            case LANE_1_2:
                // Wiki clockwise 20 route, anchored to the live plot-1 capture.
                // Coordinates are exact scene-min region tiles, not object centres.
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(39, 24, 1),
                        new TitheFarmPlant(34, 24, 2),
                        new TitheFarmPlant(39, 27, 3),
                        new TitheFarmPlant(34, 27, 4),
                        new TitheFarmPlant(39, 30, 5),
                        new TitheFarmPlant(34, 30, 6),
                        new TitheFarmPlant(39, 33, 7),
                        new TitheFarmPlant(34, 33, 8),
                        new TitheFarmPlant(34, 39, 9),
                        new TitheFarmPlant(34, 42, 10),
                        new TitheFarmPlant(34, 45, 11),
                        new TitheFarmPlant(34, 48, 12),
                        new TitheFarmPlant(39, 48, 13),
                        new TitheFarmPlant(39, 45, 14),
                        new TitheFarmPlant(39, 42, 15),
                        new TitheFarmPlant(39, 39, 16),
                        new TitheFarmPlant(44, 33, 17),
                        new TitheFarmPlant(44, 30, 18),
                        new TitheFarmPlant(44, 27, 19),
                        new TitheFarmPlant(44, 24, 20)));
                break;
            case LANE_2_3:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(35, 31, -2),
                        new TitheFarmPlant(35, 28, -1),
                        new TitheFarmPlant(35, 25, 0),
                        new TitheFarmPlant(40, 25, 1),
                        new TitheFarmPlant(45, 25, 2),
                        new TitheFarmPlant(40, 28, 3),
                        new TitheFarmPlant(45, 28, 4),
                        new TitheFarmPlant(40, 31, 5),
                        new TitheFarmPlant(45, 31, 6),
                        new TitheFarmPlant(40, 34, 7),
                        new TitheFarmPlant(45, 34, 8),
                        new TitheFarmPlant(40, 40, 9),
                        new TitheFarmPlant(45, 40, 10),
                        new TitheFarmPlant(40, 43, 11),
                        new TitheFarmPlant(45, 43, 12),
                        new TitheFarmPlant(40, 46, 13),
                        new TitheFarmPlant(45, 46, 14),
                        new TitheFarmPlant(40, 49, 15),
                        new TitheFarmPlant(45, 49, 16)));
                break;
            case LANE_3_4:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(40, 31, -2),
                        new TitheFarmPlant(40, 28, -1),
                        new TitheFarmPlant(40, 25, 0),
                        new TitheFarmPlant(45, 25, 1),
                        new TitheFarmPlant(50, 25, 2),
                        new TitheFarmPlant(45, 28, 3),
                        new TitheFarmPlant(50, 28, 4),
                        new TitheFarmPlant(45, 31, 5),
                        new TitheFarmPlant(50, 31, 6),
                        new TitheFarmPlant(45, 34, 7),
                        new TitheFarmPlant(50, 34, 8),
                        new TitheFarmPlant(45, 40, 9),
                        new TitheFarmPlant(50, 40, 10),
                        new TitheFarmPlant(45, 43, 11),
                        new TitheFarmPlant(50, 43, 12),
                        new TitheFarmPlant(45, 46, 13),
                        new TitheFarmPlant(50, 46, 14),
                        new TitheFarmPlant(45, 49, 15),
                        new TitheFarmPlant(50, 49, 16)));
                break;
            case LANE_4_5:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(45, 31, 0),
                        new TitheFarmPlant(45, 28, 1),
                        new TitheFarmPlant(45, 25, 2),
                        new TitheFarmPlant(50, 25, 3),
                        new TitheFarmPlant(55, 25, 4),
                        new TitheFarmPlant(50, 28, 5),
                        new TitheFarmPlant(55, 28, 6),
                        new TitheFarmPlant(50, 31, 7),
                        new TitheFarmPlant(55, 31, 8),
                        new TitheFarmPlant(50, 34, 9),
                        new TitheFarmPlant(55, 34, 10),
                        new TitheFarmPlant(50, 40, 11),
                        new TitheFarmPlant(55, 40, 12),
                        new TitheFarmPlant(50, 43, 13),
                        new TitheFarmPlant(55, 43, 14),
                        new TitheFarmPlant(50, 46, 15),
                        new TitheFarmPlant(55, 46, 16),
                        new TitheFarmPlant(50, 49, 17),
                        new TitheFarmPlant(55, 49, 18)));
                break;
        }

        if (lane != TitheFarmLanes.LANE_1_2) {
            // Legacy lane lists use centres one tile above/right of scene minima.
            plants.forEach(p -> { p.regionX--; p.regionY--; });
        }
        plants.sort(Comparator.comparingInt(TitheFarmPlant::getIndex));
        routePlants = new ArrayList<>(plants);
        selectActivePlants(config);
    }

    private void selectActivePlants(TitheFarmingConfig config) {
        int count = Math.min(routePlants.size(), Math.max(1, Math.min(20, config.plantsPerCycle())));
        plants = new ArrayList<>(routePlants.subList(0, count));
        Microbot.log("Tithe cycle configured for " + count + " plants");
    }


    public boolean run(TitheFarmingConfig config) {
        init = true;
        plants = new ArrayList<>();
        state = STARTING;
        allPlanted = false;
        cropPass = 0;
        cropCursor = 0;
        breakLockHeldByTithe = false;
        loginReconciliationPending = false;
        loginReconciliationReadyAt = 0L;
        clearCropAction();
        Microbot.log("Tithe farming script started");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    setBreakLock(false);
                    loginReconciliationPending = true;
                    loginReconciliationReadyAt = 0L;
                    return;
                }
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive()) return;
                TitheFarmPlant.beginLoopSnapshot();

                if (loginReconciliationPending) {
                    if (loginReconciliationReadyAt == 0L) {
                        loginReconciliationReadyAt = System.currentTimeMillis() + 3_000L;
                        return;
                    }
                    if (System.currentTimeMillis() < loginReconciliationReadyAt) return;

                    reconcileAfterLogin();
                    loginReconciliationPending = false;
                    loginReconciliationReadyAt = 0L;
                    return;
                }

                if (BreakHandlerV2State.getCurrentState() == BreakHandlerV2State.BREAK_REQUESTED
                        && isSafeBetweenCropCycles()) {
                    setBreakLock(false);
                    Microbot.status = "Waiting for Break Handler V2";
                    return;
                }

                if (init) {
                    state = STARTING;
                    plants = new ArrayList<>();
                    Rs2ItemModel rs2ItemSeed = Rs2Inventory.get(TitheFarmMaterial.getSeedForLevel().getFruitId());
                    initialFruit = rs2ItemSeed == null ? 0 : rs2ItemSeed.getQuantity();
                    init = false;
                    sleep(2000); //extra sleep to have the game initialize correctly
                }

                //Dialogue stuff only applicable if you enter for the first time
                if (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    sleep(400, 600);
                    return;
                }

                if (hasSelectAnOption()) {
                    Rs2Keyboard.keyPress('3');
                    sleep(1500, 1800);
                    return;
                }

                if (!isInMinigame() && !Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                    state = TitheFarmState.TAKE_SEEDS;
                }

                if (validateSeedsAndPatches() && isInMinigame()) {
                    state = TitheFarmState.LEAVE;
                }

                if (isSafeBetweenCropCycles()
                        && Rs2Inventory.hasItemAmount(TitheFarmMaterial.getSeedForLevel().getFruitId(), config.storeFruitThreshold())) {
                    depositSack();
                    return;
                }

                switch (state) {
                    case LEAVE:
                        if (!depositSack()) {
                            leave();
                        }
                        setBreakLock(false);
                    break;
                    case TAKE_SEEDS:
                        if (isInMinigame()) {
                            state = TitheFarmState.STARTING;
                        } else {
                            if (!Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                                takeSeeds();
                            }
                            if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                                enter();
                            }
                        }
                        break;
                    case STARTING:
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                        init(config);
                        validateInventory();
                        DropFertiliser();
                        validateRunEnergy();
                        if (state != RECHARING_RUN_ENERGY)
                            state = REFILL_WATERCANS;
                        break;
                    case RECHARING_RUN_ENERGY:
                        validateRunEnergy();
                        break;
                    case REFILL_WATERCANS:
                        refillWaterCans(config);
                        setBreakLock(false);
                        sleepGaussian(800, 200);
                        break;
                    case PLANTING_SEEDS:
                    case HARVEST:
                        setBreakLock(true);
                        coreLoop(config);
                        break;
                }

                if (config.enableDebugging() && plants.stream().anyMatch(x -> x.getGameObject() == null)) {
                    Microbot.log("There is an empty plant gameobject!");
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            } finally {
                TitheFarmPlant.endLoopSnapshot();
            }

        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        setBreakLock(false);
        clearCropAction();
        super.shutdown();
    }

    /**
     * ALL PRIVATE SCRIPT METHODS
     */

    private void coreLoop(TitheFarmingConfig config) {
        if (cropAction != CropAction.IDLE) {
            continueCropAction();
            return;
        }

        if (plants.isEmpty()) return;
        if (cropCursor >= plants.size()) {
            cropCursor = 0;
            cropPass++;
            allPlanted = true;
            if (cropPass == 3) state = HARVEST;
            if (cropPass > 3) {
                cropPass = 0;
                allPlanted = false;
                selectActivePlants(config);
                state = REFILL_WATERCANS;
                setBreakLock(false);
            }
            return;
        }

        TitheFarmPlant plant = plants.get(cropCursor);
        if (plant.getGameObject() == null) return;
        if (plant.isDead()) {
            if (cropPass == 0 || cropPass == 3) {
                if (clickPatch(plant)) beginCropAction(CropAction.WAITING_FOR_CLEAR, plant, 6_000L);
            } else {
                cropCursor++;
            }
            return;
        }
        // A failed crop is skipped for the remaining passes, never replanted
        // into a batch whose watering schedule has already started.
        if (cropPass > 0 && plant.isEmptyPatch()) {
            cropCursor++;
            return;
        }
        if (cropPass == 0 && plant.isWatered()) {
            cropCursor++;
            return;
        }
        if (cropPass == 1 && !plant.isStage1()) {
            if (plant.isStage2() || plant.isValidToHarvest()) cropCursor++;
            return;
        }
        if (cropPass == 2 && !plant.isStage2()) {
            if (plant.isValidToHarvest()) cropCursor++;
            return;
        }
        if (cropPass == 3 && !plant.isValidToHarvest()) return;

        WorldPoint corePlayerLoc = playerLocation();
        if (corePlayerLoc == null) return;
        Rs2TileObjectModel plantModel = plant.getGameObject();
        if (plantModel == null) {
            return;
        }

        if (plantModel.getWorldLocation().distanceTo2D(corePlayerLoc) > DISTANCE_THRESHOLD_MINIMAP_WALK
                && !Rs2Camera.isTileOnScreen(plantModel.getLocalLocation())) {
            WorldPoint w = WorldPoint.fromRegion(corePlayerLoc.getRegionID(),
                    plant.regionX,
                    plant.regionY,
                    Microbot.getClient().getTopLevelWorldView().getPlane());
            Rs2Walker.walkMiniMap(w, 1);
            return;
        }

        if (!Rs2Camera.isTileOnScreen(plantModel.getLocalLocation())) {
            Rs2Camera.turnTo(plantModel);
            return;
        }

        if (plant.isEmptyPatch() && !allPlanted) { //start planting seeds
            if (useInventoryItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                beginCropAction(CropAction.WAITING_FOR_SEED_SELECTION, plant, 2_000L);
            }
            return;
        }

        if (plant.isValidToWater()) {
            if (clickPatch(plant, "water")) {
                beginCropAction(CropAction.WAITING_FOR_WATER, plant, 6_000L);
            }
            return;
        }


        if (plant.isValidToHarvest()) {
            if (clickPatch(plant, "harvest")) {
                beginCropAction(CropAction.WAITING_FOR_HARVEST, plant, 6_000L);
            }
        }
    }

    private void continueCropAction() {
        TitheFarmPlant plant = plants.stream()
                .filter(p -> p.getIndex() == cropActionPlantIndex)
                .findFirst()
                .orElse(null);

        if (plant == null || plant.getGameObject() == null) {
            if (cropActionTimedOut()) clearCropAction();
            return;
        }

        switch (cropAction) {
            case WAITING_FOR_SEED_SELECTION:
                if (Rs2Inventory.isItemSelected()) {
                    if (clickPatch(plant)) {
                        beginCropAction(CropAction.WAITING_FOR_PLANT, plant, 6_000L);
                    } else {
                        clearCropAction();
                    }
                } else if (cropActionTimedOut()) {
                    clearCropAction();
                }
                break;
            case WAITING_FOR_PLANT:
                if (plant.isValidToWater()) {
                    // Water immediately after the seedling appears. This skips the
                    // dibber animation without issuing another action prematurely.
                    if (useInventoryItem(TitheFarmMaterial.getWateringCan())) {
                        beginCropAction(CropAction.WAITING_FOR_WATERING_CAN_SELECTION, plant, 2_000L);
                    } else {
                        clearCropAction();
                    }
                } else if (cropActionTimedOut()) {
                    clearCropAction();
                }
                break;
            case WAITING_FOR_WATERING_CAN_SELECTION:
                if (Rs2Inventory.isItemSelected()) {
                    if (clickPatch(plant)) {
                        beginCropAction(CropAction.WAITING_FOR_WATER, plant, 6_000L);
                    } else {
                        clearCropAction();
                    }
                } else if (cropActionTimedOut()) {
                    clearCropAction();
                }
                break;
            case WAITING_FOR_WATER:
                if (plant.isWatered()) {
                    plant.setPlanted(Instant.now());
                    cropCursor++;
                    clearCropAction();
                } else if (cropActionTimedOut()) {
                    clearCropAction();
                }
                break;
            case WAITING_FOR_HARVEST:
            case WAITING_FOR_CLEAR:
                if (plant.isEmptyPatch()) {
                    cropCursor++;
                    clearCropAction();
                } else if (cropActionTimedOut()) {
                    clearCropAction();
                }
                break;
            default:
                clearCropAction();
                break;
        }
    }

    private void beginCropAction(CropAction action, TitheFarmPlant plant, long timeoutMillis) {
        cropAction = action;
        cropActionPlantIndex = plant.getIndex();
        cropActionDeadline = System.currentTimeMillis() + timeoutMillis;
    }

    private boolean cropActionTimedOut() {
        return System.currentTimeMillis() >= cropActionDeadline;
    }

    private void clearCropAction() {
        cropAction = CropAction.IDLE;
        cropActionPlantIndex = Integer.MIN_VALUE;
        cropActionDeadline = 0L;
    }

    private void reconcileAfterLogin() {
        setBreakLock(false);
        clearCropAction();
        plants = new ArrayList<>();
        allPlanted = false;
        cropPass = 0;
        cropCursor = 0;
        gricollerCanCharges = -1;
        state = isInMinigame() ? STARTING : TAKE_SEEDS;
        Microbot.log("Tithe farm login recovery: cleared stale patch cycle; state=" + state);
    }

        // Helper method to validate inventory items
        private void validateInventory() {
            if (!Rs2Inventory.hasItem(ItemID.DIBBER) || !Rs2Inventory.hasItem(ItemID.SPADE)) {
                Microbot.showMessage("You need a seed dibber and a spade in your inventory!");
                shutdown();
            }
            if (!Rs2Inventory.hasItemAmount("watering can", WATERING_CANS_AMOUNT) && !Rs2Inventory.hasItem(ItemID.ZEAH_WATERINGCAN)) {
                Microbot.showMessage("You need at least 8 watering can(8) or a Gricoller's can!");
                shutdown();
            }
        }

// Helper method to validate run energy and patches
        private void validateRunEnergy() {
            if (Microbot.getClient().getEnergy() < 4000 && hasAllEmptyPatches() && state != RECHARING_RUN_ENERGY) {
                state = RECHARING_RUN_ENERGY;
                Microbot.log("Recharging run energy...");
                Rs2Inventory.useRestoreEnergyItem();
            } else if (state == RECHARING_RUN_ENERGY && Microbot.getClient().getEnergy() >= 4000) {
                state = STARTING;
            }
        }

        private boolean validateSeedsAndPatches() {
            if (!Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                return true;
            }
            return false;
        }



    private static boolean clickPatch(TitheFarmPlant plant) {
        Rs2TileObjectModel model = plant.getGameObject();
        return model != null && model.click();
    }

    private static boolean clickPatch(TitheFarmPlant plant, String action) {
        Rs2TileObjectModel model = plant.getGameObject();
        return model != null && model.click(action);
    }

    private static boolean useInventoryItem(String itemName) {
        return Rs2Inventory.interact(itemName, "Use");
    }

    private static boolean useInventoryItem(int itemId) {
        return Rs2Inventory.interact(itemId, "Use");
    }

    private static boolean interactWithObject(int id, String action) {
        Rs2TileObjectModel model = Microbot.getRs2TileObjectCache().query()
                .withId(id)
                .nearest();
        if (model == null) {
            Microbot.log("Object id " + id + " not in scene");
            return false;
        }
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc != null && playerLoc.distanceTo(model.getWorldLocation()) > 51) {
            Microbot.log("Object id " + id + " is " + playerLoc.distanceTo(model.getWorldLocation()) + " tiles away, walking...");
            Rs2Walker.walkTo(model.getWorldLocation());
            return false;
        }
        return action == null ? model.click() : model.click(action);
    }

    private static void DropFertiliser() {
        if (Rs2Inventory.hasItem("Gricoller's fertiliser")) {
            Rs2Inventory.drop("Gricoller's fertiliser");
        }
    }

    private void refillWaterCans(TitheFarmingConfig config) {
        if (TitheFarmMaterial.hasGricollersCan()) {
            checkGricollerCharges();
            sleepUntil(() -> gricollerCanCharges != -1);
            if (gricollerCanCharges < config.gricollerCanRefillThreshold()) {
                walkToBarrel();
                Rs2Inventory.interact(ItemID.ZEAH_WATERINGCAN, "Use");
                Rs2TileObjectModel barrel = Microbot.getRs2TileObjectCache().query()
                        .withName("Water barrel")
                        .nearestOnClientThread();
                if (barrel != null) barrel.click();
                sleepUntil(Rs2Player::isAnimating, 10000);
            } else {
                state = PLANTING_SEEDS;
            }
        } else if (TitheFarmMaterial.hasWateringCanToBeFilled()) {
            walkToBarrel();
            Rs2Inventory.interact(TitheFarmMaterial.getWateringCanToBeFilled(), "Use");
            Rs2TileObjectModel barrel = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.WATER_BARREL1)
                    .nearest();
            if (barrel != null) barrel.click();
            sleepUntil(() -> Rs2Inventory.hasItemAmount(ItemID.WATERING_CAN_8, WATERING_CANS_AMOUNT), 60000);
        } else {
            state = PLANTING_SEEDS;
        }
    }

    private void walkToBarrel() {
        Rs2TileObjectModel barrel = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.WATER_BARREL1)
                .nearest();
        if (barrel == null) return;
        WorldPoint barrelLoc = barrel.getWorldLocation();
        WorldPoint barrelPlayerLoc = playerLocation();
        if (barrelPlayerLoc == null) return;
        if (barrelLoc.distanceTo2D(barrelPlayerLoc) > DISTANCE_THRESHOLD_MINIMAP_WALK) {
            Rs2Walker.walkMiniMap(barrelLoc, 1);
            sleepUntil(Rs2Player::isMoving);
        }
        sleepUntil(() -> {
            WorldPoint loc = playerLocation();
            return loc != null && barrelLoc.distanceTo2D(loc) < DISTANCE_THRESHOLD_MINIMAP_WALK;
        });
    }

    private static WorldPoint playerLocation() {
        return Microbot.getClientThread().invoke(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            return localPlayer == null ? null : localPlayer.getWorldLocation();
        });
    }

    private void checkGricollerCharges() {
        gricollerCanCharges = -1;
        Rs2Inventory.interact(ItemID.ZEAH_WATERINGCAN, "check");
    }

    private void takeSeeds() {
        if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
            Rs2Inventory.drop(TitheFarmMaterial.getSeedForLevel().getName());
            sleep(400, 600);
        }
        interactWithObject(ObjectID.TITHE_PLANT_SEED_TABLE, null);
        boolean result = Rs2Widget.sleepUntilHasWidget(TitheFarmMaterial.getSeedForLevel().getName());
        if (!result) return;
        Rs2Keyboard.keyPress(TitheFarmMaterial.getSeedForLevel().getOption());
        sleep(1000);
        Rs2Keyboard.typeString("9".repeat(Rs2Random.betweenInclusive(4, 8)));
        sleep(600);
        Rs2Keyboard.enter();
        sleepUntil(() -> Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName()));
    }

    private void enter() {
        interactWithObject(FARM_DOOR, null);
        sleepUntil(this::isInMinigame);
    }

    private boolean depositSack() {
        if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getFruitId())) {
            Microbot.log("Storing fruits into sack for experience...");
            Rs2TileObjectModel sack = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.TITHE_SACK_OF_FRUIT_EMPTY)
                    .nearest();
            if (sack == null) {
                Microbot.log("Sack not found in scene");
                return true;
            }
            WorldPoint sackLoc = sack.getWorldLocation();
            WorldPoint playerLoc = playerLocation();
            if (playerLoc == null) return true;
            if (sackLoc.distanceTo2D(playerLoc) > DISTANCE_THRESHOLD_MINIMAP_WALK) {
                Rs2Walker.walkMiniMap(sackLoc, 1);
                sleepUntil(Rs2Player::isMoving);
                sleepUntil(() -> {
                    WorldPoint loc = playerLocation();
                    return loc != null && sackLoc.distanceTo2D(loc) < DISTANCE_THRESHOLD_MINIMAP_WALK;
                });
            }
            sack.click();
            Rs2Player.waitForAnimation();
            return true;
        }
        return false;
    }

    private void leave() {
        interactWithObject(FARM_DOOR, null);
        sleepUntil(() -> !Rs2Inventory.hasItem(FERTILISER), 8000);
    }

    private boolean hasAllEmptyPatches() {
        return plants.stream().allMatch(TitheFarmPlant::isEmptyPatch);
    }

    private boolean isSafeBetweenCropCycles() {
        return !isInMinigame()
                || (!plants.isEmpty()
                && plants.stream().allMatch(plant -> plant.getGameObject() != null)
                && plants.stream().allMatch(TitheFarmPlant::isEmptyPatch));
    }

    private void setBreakLock(boolean locked) {
        if (locked) {
            BreakHandlerScript.setLockState(true);
            breakLockHeldByTithe = true;
        } else if (breakLockHeldByTithe) {
            BreakHandlerScript.setLockState(false);
            breakLockHeldByTithe = false;
        }
    }

    private boolean isInMinigame() {
        return Rs2Widget.getWidget(15794178) != null;
    }
}
