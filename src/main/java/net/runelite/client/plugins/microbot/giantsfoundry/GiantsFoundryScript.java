package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.CommissionType;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.CoolingMethod;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get;

@Slf4j
public class GiantsFoundryScript extends Script
{
    static final int CRUCIBLE = 44776;
    static final int MOULD_JIG = 44777;
    static final int LAVA_POOL = 44631;
    static final int WATERFALL = 44632;
    private static final int FOUNDRY_REGION = 13491;
    private static final int MAX_PROGRESS = 1000;
    private static final int FAST_HEAT_THRESHOLD = 180;
    private static final long ACTION_COOLDOWN_MS = 1200;
    private static final long SNAPSHOT_LOG_INTERVAL_MS = 5000;
    private static final long FULL_CRUCIBLE_GRACE_MS = 5000;
    private static final int TEMPERATURE_ACTION_TIMEOUT_MS = 45000;

    public static volatile State state = State.VALIDATING;
    private static volatile String status = "Starting";
    private static volatile String error = "";
    private static volatile String materialDescription = "Not resolved";

    private GiantsFoundryConfig config;
    private FoundryMaterialPlan materialPlan;
    private boolean inventoryPrepared;
    private boolean firstMaterialAdded;
    private boolean secondMaterialAdded;
    private boolean bonusClickConsumed;
    private boolean lastBonusActive;
    private boolean temperatureActionInProgress;
    private boolean errorLatched;
    private long lastActionAt;
    private long lastSnapshotLogAt;
    private long materialsCompleteAt;
    private State lastCalculatedState;

    public boolean run(GiantsFoundryConfig config)
    {
        this.config = config;
        clearError();
        resetCycle();
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
        log.info("Giants' Foundry: mouse activity intensity set to MODERATE");
        setState(State.VALIDATING, "Validating configuration");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn())
                {
                    return;
                }
                tick();
            }
            catch (Exception ex)
            {
                setError("Unexpected error: " + safeMessage(ex));
                log.error("Giants' Foundry tick failed", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        if (errorLatched)
        {
            return;
        }
        if (!validateRuntime())
        {
            return;
        }

        FoundrySnapshot snapshot = captureSnapshot();
        State calculatedState = FoundryStateResolver.calculate(snapshot.toFacts());
        logSnapshot(calculatedState, snapshot);

        switch (calculatedState)
        {
            case HANDING_IN:
                handIn(snapshot.hasPreform && snapshot.preformQuality <= 0);
                break;
            case HEATING:
                adjustTemperature(true, snapshot.heatChange);
                break;
            case COOLING_DOWN:
                adjustTemperature(false, -snapshot.heatChange);
                break;
            case CRAFTING_WEAPON:
                craftWeapon(snapshot.stage);
                break;
            case WAITING:
                setState(State.WAITING, "Waiting for the Foundry HUD");
                break;
            case PICKING_UP_PREFORM:
                pickupPreform();
                break;
            case POURING:
                pourCrucible();
                break;
            case GETTING_COMMISSION:
                getCommission();
                break;
            case SELECTING_MOULD:
                selectMould();
                break;
            case FILLING_CRUCIBLE:
                fillCrucible(snapshot);
                break;
            default:
                setError("No action is available for calculated state " + calculatedState + ".");
                break;
        }
    }

    private boolean validateRuntime()
    {
        if (Rs2Player.getQuestState(Quest.SLEEPING_GIANTS) != QuestState.FINISHED)
        {
            setError("Complete Sleeping Giants before starting the plugin.");
            return false;
        }
        if (Rs2Player.getWorldLocation() == null || Rs2Player.getWorldLocation().getRegionID() != FOUNDRY_REGION)
        {
            setError("Start inside Giants' Foundry.");
            return false;
        }

        Rs2ItemModel weapon = get(EquipmentInventorySlot.WEAPON);
        Rs2ItemModel shield = get(EquipmentInventorySlot.SHIELD);
        if ((weapon != null && !isPreform(weapon)) || shield != null)
        {
            setError("Empty the weapon and shield slots before starting.");
            return false;
        }

        if (!isPreform(weapon) && !hasCoolingRequirement())
        {
            setError(config.coolingMethod() == CoolingMethod.ICE_GLOVES
                    ? "Equip ice gloves or Smiths gloves (i)."
                    : "Keep a bucket of water in the inventory.");
            return false;
        }

        int level = Rs2Player.getRealSkillLevel(Skill.SMITHING);
        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config, level);
        if (!result.isValid())
        {
            setError(result.getError());
            return false;
        }
        materialPlan = result.getPlan();
        materialDescription = materialPlan.getDescription();
        return true;
    }

    private boolean hasCoolingRequirement()
    {
        if (Rs2Equipment.isWearing(ItemID.SMITHS_GLOVES_I))
        {
            return true;
        }
        if (config.coolingMethod() == CoolingMethod.ICE_GLOVES)
        {
            return Rs2Equipment.isWearing(ItemID.ICE_GLOVES);
        }
        return true;
    }

    private boolean isPreform(Rs2ItemModel item)
    {
        return item != null && (item.getId() == ItemID.PREFORM || item.getName().equalsIgnoreCase("preform"));
    }

    public boolean hasCommission()
    {
        CommissionType type1 = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_1_VARBIT));
        CommissionType type2 = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_2_VARBIT));
        return type1 != CommissionType.NONE && type2 != CommissionType.NONE;
    }

    private void getCommission()
    {
        setState(State.GETTING_COMMISSION, "Requesting commission");
        GiantsFoundryState.reset();
        resetCycle();
        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return;
        }
        if (Rs2Dialogue.hasSelectAnOption())
        {
            if (!actionReady())
            {
                return;
            }
            boolean selected = Rs2Dialogue.clickOption("Yes", "commission", "sword");
            log.info("Giants' Foundry action: commission dialogue option selected={}", selected);
            if (selected)
            {
                markAction();
                sleepUntil(() -> hasCommission() || !Rs2Dialogue.hasSelectAnOption(), 3000);
            }
            return;
        }
        if (!actionReady())
        {
            return;
        }
        var kovac = Microbot.getRs2NpcCache().query().withName("kovac").nearestOnClientThread();
        if (kovac == null || !kovac.click("Commission"))
        {
            setError("Kovac is not available for a commission.");
            return;
        }
        markAction();
        sleepUntil(this::hasCommission, 5000);
    }

    private boolean hasSelectedMould()
    {
        return Microbot.getVarbitValue(GiantsFoundryState.VARBIT_BLADE_SELECTED) > 0
                && Microbot.getVarbitValue(GiantsFoundryState.VARBIT_TIP_SELECTED) > 0
                && Microbot.getVarbitValue(GiantsFoundryState.VARBIT_FORTE_SELECTED) > 0;
    }

    private void selectMould()
    {
        setState(State.SELECTING_MOULD, "Selecting best available moulds");
        if (!actionReady())
        {
            return;
        }
        if (!Microbot.getRs2TileObjectCache().query().withId(MOULD_JIG).interact())
        {
            setError("Could not open the mould jig.");
            return;
        }
        markAction();
        if (!sleepUntil(() -> Rs2Widget.findWidget("Forte", null) != null, 5000))
        {
            setError("Mould selection interface did not open.");
            return;
        }

        if (!selectMouldTab("Forte") || !selectMouldTab("Blades") || !selectMouldTab("Tips"))
        {
            setError("Could not select a mould for every sword section.");
            return;
        }
        Widget setMould = Rs2Widget.getWidget(47054854);
        if (setMould == null)
        {
            setError("Set mould button is unavailable.");
            return;
        }
        Microbot.getMouse().click(setMould.getBounds());
        sleepUntil(this::hasSelectedMould, 5000);
    }

    private boolean selectMouldTab(String tabName)
    {
        Widget tab = Rs2Widget.findWidget(tabName, null);
        if (tab == null)
        {
            return false;
        }
        Microbot.getMouse().click(tab.getBounds());
        sleep(400, 700);
        return MouldHelper.selectBest();
    }

    public boolean canPour()
    {
        ObjectComposition composition = Rs2GameObject.findObjectComposition(CRUCIBLE);
        return composition != null && composition.getName().toLowerCase().contains("(full)");
    }

    private void fillCrucible(FoundrySnapshot snapshot)
    {
        String materialProblem = getMaterialProblem(snapshot.oreCounts);
        if (materialProblem != null)
        {
            setError(materialProblem);
            return;
        }

        int firstLoaded = snapshot.oreCounts[materialPlan.getFirst().getMetal().ordinal()];
        int secondLoaded = snapshot.oreCounts[materialPlan.getSecond().getMetal().ordinal()];
        firstMaterialAdded = firstLoaded >= materialPlan.getFirst().getBarEquivalentAmount();
        secondMaterialAdded = secondLoaded >= materialPlan.getSecond().getBarEquivalentAmount();

        if (!inventoryPrepared)
        {
            if (hasRemainingMaterials(snapshot, firstLoaded, secondLoaded))
            {
                inventoryPrepared = true;
                log.info("Giants' Foundry: resumed material load at {}/28 with required inventory present", snapshot.oreCount);
            }
            else if (snapshot.oreCount > 0)
            {
                setError("Crucible contains " + snapshot.oreCount
                        + "/28 bars but the inventory does not contain the planned remainder ("
                        + materialDescription + ").");
                return;
            }
            else
            {
                prepareMaterials();
                return;
            }
        }

        setState(State.FILLING_CRUCIBLE, "Loading crucible (" + snapshot.oreCount + "/28)");
        if (!firstMaterialAdded)
        {
            firstMaterialAdded = addMaterial(materialPlan.getFirst(), firstLoaded);
            return;
        }
        if (!secondMaterialAdded)
        {
            secondMaterialAdded = addMaterial(materialPlan.getSecond(), secondLoaded);
            return;
        }

        if (snapshot.canPour)
        {
            materialsCompleteAt = 0;
            return;
        }
        if (materialsCompleteAt == 0)
        {
            materialsCompleteAt = System.currentTimeMillis();
            log.info("Giants' Foundry: all planned materials observed; waiting for the crucible Pour action");
            return;
        }
        if (System.currentTimeMillis() - materialsCompleteAt >= FULL_CRUCIBLE_GRACE_MS)
        {
            setError("All planned materials were loaded (" + snapshot.describeMetals()
                    + ") but the crucible did not expose Pour.");
        }
    }

    private String getMaterialProblem(int[] oreCounts)
    {
        SmithableBars firstMetal = materialPlan.getFirst().getMetal();
        SmithableBars secondMetal = materialPlan.getSecond().getMetal();
        for (SmithableBars metal : SmithableBars.values())
        {
            int count = oreCounts[metal.ordinal()];
            if (count <= 0)
            {
                continue;
            }
            if (metal != firstMetal && metal != secondMetal)
            {
                return "Crucible contains unplanned " + metal.getName() + " (" + count + ").";
            }
            int planned = metal == firstMetal
                    ? materialPlan.getFirst().getBarEquivalentAmount()
                    : materialPlan.getSecond().getBarEquivalentAmount();
            if (count > planned)
            {
                return "Crucible contains " + count + " " + metal.getName()
                        + " but the plan allows " + planned + ".";
            }
        }
        return null;
    }

    private boolean hasRemainingMaterials(FoundrySnapshot snapshot, int firstLoaded, int secondLoaded)
    {
        int firstRequired = remainingItemCount(materialPlan.getFirst(), firstLoaded);
        int secondRequired = remainingItemCount(materialPlan.getSecond(), secondLoaded);
        return firstRequired >= 0 && secondRequired >= 0
                && snapshot.firstInventoryCount >= firstRequired
                && snapshot.secondInventoryCount >= secondRequired;
    }

    private int remainingItemCount(FoundryMaterialPlan.Material material, int loadedBars)
    {
        int remainingBars = material.getBarEquivalentAmount() - loadedBars;
        if (remainingBars < 0 || remainingBars % material.getBarEquivalentPerItem() != 0)
        {
            return -1;
        }
        return remainingBars / material.getBarEquivalentPerItem();
    }

    private boolean prepareMaterials()
    {
        setState(State.PREPARING_MATERIALS, "Preparing " + materialDescription);
        if (!actionReady())
        {
            return false;
        }
        if (!Rs2Bank.openBank())
        {
            setError("Could not open the Foundry bank chest.");
            return false;
        }
        if (!Rs2Bank.depositAll())
        {
            setError("Could not clear the inventory before withdrawing materials.");
            return false;
        }
        if (config.coolingMethod() == CoolingMethod.BUCKET_OF_WATER
                && !Rs2Equipment.isWearing(ItemID.SMITHS_GLOVES_I)
                && !Rs2Bank.withdrawDeficit(ItemID.BUCKET_OF_WATER, 1))
        {
            setError("No bucket of water is available in the bank.");
            return false;
        }
        if (!withdrawMaterial(materialPlan.getFirst()) || !withdrawMaterial(materialPlan.getSecond()))
        {
            setError("The bank does not contain the configured Foundry materials.");
            return false;
        }
        if (!Rs2Bank.closeBank())
        {
            setError("Could not close the bank after withdrawing materials.");
            return false;
        }
        inventoryPrepared = true;
        return true;
    }

    private boolean withdrawMaterial(FoundryMaterialPlan.Material material)
    {
        return Rs2Bank.withdrawDeficit(material.getName(), material.getQuantity(), true)
                && sleepUntil(() -> Rs2Inventory.hasItemAmount(material.getName(), material.getQuantity()), 3000);
    }

    private boolean addMaterial(FoundryMaterialPlan.Material material, int loadedBars)
    {
        int remainingQuantity = remainingItemCount(material, loadedBars);
        if (remainingQuantity <= 0)
        {
            return remainingQuantity == 0;
        }
        if (!Rs2Inventory.hasItemAmount(material.getName(), remainingQuantity))
        {
            setError("Missing " + remainingQuantity + " " + material.getName() + " from inventory.");
            return false;
        }
        FoundryMaterialPlan.Material remainder = new FoundryMaterialPlan.Material(
                material.getName(), material.getMetal(), remainingQuantity, material.getBarEquivalentPerItem());
        int beforeMetal = GiantsFoundryState.getOreCount(material.getMetal());
        int beforeInventory = Rs2Inventory.count(material.getName());
        log.info("Giants' Foundry action: add material={} quantity={} loadedBefore={} inventoryBefore={}",
                material.getName(), remainingQuantity, beforeMetal, beforeInventory);
        boolean interacted = materialPlan.isRecycledItems()
                ? addRecycledItems(remainder)
                : addBars(remainder);
        if (!interacted)
        {
            return false;
        }
        markAction();
        int expectedMetal = beforeMetal + remainder.getBarEquivalentAmount();
        boolean confirmed = sleepUntil(() -> GiantsFoundryState.getOreCount(material.getMetal()) >= expectedMetal
                || Rs2Inventory.count(material.getName()) <= beforeInventory - remainingQuantity
                || canPour(), 6000);
        int afterMetal = GiantsFoundryState.getOreCount(material.getMetal());
        int afterInventory = Rs2Inventory.count(material.getName());
        log.info("Giants' Foundry action result: material={} confirmed={} loadedAfter={} inventoryAfter={} canPour={}",
                material.getName(), confirmed, afterMetal, afterInventory, canPour());
        if (!confirmed)
        {
            setError("Could not confirm adding " + material.getName()
                    + " (metal " + beforeMetal + "->" + afterMetal
                    + ", inventory " + beforeInventory + "->" + afterInventory + ").");
            return false;
        }
        return true;
    }

    private boolean addBars(FoundryMaterialPlan.Material material)
    {
        if (!Microbot.getRs2TileObjectCache().query().interact(CRUCIBLE, "Fill"))
        {
            setError("Could not interact with the crucible.");
            return false;
        }
        if (!sleepUntil(() -> Rs2Widget.findWidget("What metal would you like to add?", null) != null, 5000))
        {
            setError("Crucible metal selection did not appear.");
            return false;
        }
        Rs2Keyboard.keyPress(getKeyFromBar(material.getMetal()));
        return true;
    }

    private boolean addRecycledItems(FoundryMaterialPlan.Material material)
    {
        if (!Rs2Inventory.use(material.getName())
                || !Microbot.getRs2TileObjectCache().query().withId(CRUCIBLE).interact())
        {
            setError("Could not use " + material.getName() + " on the crucible.");
            return false;
        }
        if (!sleepUntil(() -> Rs2Widget.findWidget("How many would you like to add?", null) != null, 5000))
        {
            setError("Recycled-item quantity prompt did not appear.");
            return false;
        }
        Rs2Keyboard.keyPress('3');
        sleep(300, 500);
        Rs2Keyboard.typeString(Integer.toString(material.getQuantity()));
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return true;
    }

    private void pourCrucible()
    {
        setState(State.POURING, "Pouring the preform");
        if (!actionReady())
        {
            return;
        }
        if (!Microbot.getRs2TileObjectCache().query().interact(CRUCIBLE, "Pour"))
        {
            setError("Could not pour the crucible.");
            return;
        }
        markAction();
        sleepUntil(() -> !canPour(), 10000);
    }

    private boolean canPickupPreform()
    {
        if (canPour())
        {
            return false;
        }
        ObjectComposition composition = Rs2GameObject.findObjectComposition(MOULD_JIG);
        return composition != null && composition.getName().toLowerCase().contains("poured metal");
    }

    private void pickupPreform()
    {
        setState(State.PICKING_UP_PREFORM, "Collecting the preform");
        if (config.coolingMethod() == CoolingMethod.BUCKET_OF_WATER
                && !Rs2Equipment.isWearing(ItemID.SMITHS_GLOVES_I)
                && !Rs2Inventory.hasItem(ItemID.BUCKET_OF_WATER))
        {
            setError("A bucket of water is required to collect the preform.");
            return;
        }
        if (!actionReady())
        {
            return;
        }
        if (!Microbot.getRs2TileObjectCache().query().interact(MOULD_JIG, "Pick-up"))
        {
            setError("Could not collect the poured preform.");
            return;
        }
        markAction();
        sleepUntil(() -> isPreform(get(EquipmentInventorySlot.WEAPON)), 5000);
    }

    private void adjustTemperature(boolean heating, int change)
    {
        boolean fast = change >= FAST_HEAT_THRESHOLD;
        String action = heating
                ? (fast ? "Dunk-preform" : "Heat-preform")
                : (fast ? "Quench-preform" : "Cool-preform");
        setState(heating ? State.HEATING : State.COOLING_DOWN,
                (fast ? "Fast " : "Fine ") + (heating ? "heating" : "cooling") + " (" + change + ")");
        if (!actionReady() || Rs2Player.isMoving())
        {
            return;
        }

        int objectId = heating ? LAVA_POOL : WATERFALL;
        int startHeat = GiantsFoundryState.getHeatAmount();
        int startQuality = GiantsFoundryState.getPreformQuality();
        int[] heatRange = GiantsFoundryState.getCurrentHeatRange();
        int actionsLeft = GiantsFoundryState.getActionsLeftInStage();
        if (!Microbot.getRs2TileObjectCache().query().interact(objectId, action))
        {
            setError("Could not start " + action + ".");
            return;
        }
        markAction();
        temperatureActionInProgress = true;
        GiantsFoundryState.heatingCoolingState.stop();
        GiantsFoundryState.heatingCoolingState.setup(fast, heating, action);
        GiantsFoundryState.heatingCoolingState.start(startHeat);
        log.info("Giants' Foundry action: temperature action={} heat={} change={} range={}-{} actionsLeft={} "
                        + "plannedTicks={} predictedHeat={} goalInRange={} overshooting={} interruptingAnimation={}",
                action,
                startHeat,
                change,
                heatRange[0],
                heatRange[1],
                actionsLeft,
                GiantsFoundryState.heatingCoolingState.getEstimatedDuration(),
                GiantsFoundryState.heatingCoolingState.getPredictedHeat(),
                GiantsFoundryState.heatingCoolingState.isGoalInRange(),
                GiantsFoundryState.heatingCoolingState.isOverShooting(),
                Rs2Player.isAnimating());

        boolean completed = sleepUntil(
                () -> GiantsFoundryState.heatingCoolingState.getRemainingDuration() <= 1,
                TEMPERATURE_ACTION_TIMEOUT_MS);
        log.info("Giants' Foundry action result: temperature action={} completed={} remainingTicks={} heat={}->{} "
                        + "quality={}->{} heatChangeNeeded={}",
                action,
                completed,
                GiantsFoundryState.heatingCoolingState.getRemainingDuration(),
                startHeat,
                GiantsFoundryState.getHeatAmount(),
                startQuality,
                GiantsFoundryState.getPreformQuality(),
                GiantsFoundryState.getHeatChangeNeeded());
        if (!completed)
        {
            setError(action + " did not reach its calculated stop point.");
        }
    }

    private void craftWeapon(Stage stage)
    {
        setState(State.CRAFTING_WEAPON, stage.getName() + " preform");
        boolean bonusActive = BonusWidget.isActive();
        if (!bonusActive)
        {
            bonusClickConsumed = false;
        }
        else if (!lastBonusActive)
        {
            bonusClickConsumed = false;
        }
        lastBonusActive = bonusActive;

        if (Rs2Player.isMoving() || !actionReady())
        {
            return;
        }
        if (bonusActive)
        {
            if (bonusClickConsumed)
            {
                return;
            }
        }
        else if (Rs2Player.isAnimating(1200) && !temperatureActionInProgress)
        {
            return;
        }
        Rs2TileObjectModel object = GiantsFoundryState.getStageObject(stage);
        int beforeProgress = GiantsFoundryState.getProgressAmount();
        int beforeQuality = GiantsFoundryState.getPreformQuality();
        log.info("Giants' Foundry action: station={} progress={} quality={}",
                stage, beforeProgress, beforeQuality);
        if (object == null || !object.click())
        {
            setError("Could not interact with the " + stage.getName().toLowerCase() + " station.");
            return;
        }
        if (bonusActive)
        {
            bonusClickConsumed = true;
        }
        markAction();
        temperatureActionInProgress = false;
        boolean confirmed = sleepUntil(() -> Rs2Player.isMoving()
                || Rs2Player.isAnimating()
                || GiantsFoundryState.getProgressAmount() > beforeProgress
                || GiantsFoundryState.getPreformQuality() <= 0, 3000);
        log.info("Giants' Foundry action result: station={} confirmed={} progress={}->{} quality={}->{}",
                stage,
                confirmed,
                beforeProgress,
                GiantsFoundryState.getProgressAmount(),
                beforeQuality,
                GiantsFoundryState.getPreformQuality());
        if (!confirmed)
        {
            setError("Could not confirm starting the " + stage.getName().toLowerCase() + " station.");
        }
    }

    private void handIn(boolean damaged)
    {
        setState(State.HANDING_IN, damaged ? "Returning damaged sword to Kovac" : "Handing the sword to Kovac");
        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            sleep(400, 700);
            if (!isPreform(get(EquipmentInventorySlot.WEAPON)))
            {
                GiantsFoundryState.reset();
                resetCycle();
            }
            return;
        }
        if (!damaged && GiantsFoundryState.getProgressAmount() < MAX_PROGRESS)
        {
            GiantsFoundryState.reset();
            resetCycle();
            return;
        }
        if (!actionReady())
        {
            return;
        }
        var kovac = Microbot.getRs2NpcCache().query().withName("kovac").nearestOnClientThread();
        if (kovac == null || !kovac.click("Hand-in"))
        {
            setError("Could not hand the completed sword to Kovac.");
            return;
        }
        markAction();
        boolean acknowledged = sleepUntil(() -> Rs2Dialogue.hasContinue()
                || !isPreform(get(EquipmentInventorySlot.WEAPON))
                || (!damaged && GiantsFoundryState.getProgressAmount() < MAX_PROGRESS), 5000);
        if (!acknowledged)
        {
            setError("Kovac did not acknowledge the completed sword.");
            return;
        }
        if (!isPreform(get(EquipmentInventorySlot.WEAPON)))
        {
            GiantsFoundryState.reset();
            resetCycle();
        }
    }

    public static char getKeyFromBar(net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars bar)
    {
        net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars[] bars =
                net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars.values();
        for (int i = 0; i < bars.length; i++)
        {
            if (bars[i] == bar)
            {
                return (char) ('1' + i);
            }
        }
        return 'x';
    }

    private FoundrySnapshot captureSnapshot()
    {
        Rs2ItemModel weapon = get(EquipmentInventorySlot.WEAPON);
        int progress = GiantsFoundryState.getProgressAmount();
        int heat = GiantsFoundryState.getHeatAmount();
        Stage currentStage = GiantsFoundryState.getCurrentStage(progress);
        int[] currentHeatRange = GiantsFoundryState.getHeatRange(currentStage);
        int[] oreCounts = GiantsFoundryState.getOreCounts();
        return new FoundrySnapshot(
                progress,
                heat,
                GiantsFoundryState.getPreformQuality(),
                GiantsFoundryState.getPreformStartQuality(),
                GiantsFoundryState.getGameStage(),
                isPreform(weapon),
                canPickupPreform(),
                canPour(),
                hasCommission(),
                hasSelectedMould(),
                currentStage,
                GiantsFoundryState.calculateHeatChangeNeeded(currentStage, heat, currentHeatRange),
                oreCounts,
                GiantsFoundryState.totalOreCount(oreCounts),
                Rs2Inventory.count(materialPlan.getFirst().getName()),
                Rs2Inventory.count(materialPlan.getSecond().getName()),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                String.valueOf(Rs2Player.getWorldLocation()));
    }

    private void logSnapshot(State calculatedState, FoundrySnapshot snapshot)
    {
        long now = System.currentTimeMillis();
        if (calculatedState == lastCalculatedState && now - lastSnapshotLogAt < SNAPSHOT_LOG_INTERVAL_MS)
        {
            return;
        }
        log.info("Giants' Foundry observation: calculated={} gameStage={} progress={} heat={} quality={}/{} heatChange={} "
                        + "stage={} ore={}/28 metals=[{}] commission={} mould={} preform={} canPour={} "
                        + "canPickup={} inventory=[{}={}, {}={}] moving={} animating={} position={}",
                calculatedState,
                snapshot.gameStage,
                snapshot.progress,
                snapshot.heat,
                snapshot.preformQuality,
                snapshot.preformStartQuality,
                snapshot.heatChange,
                snapshot.stage == null ? "none" : snapshot.stage,
                snapshot.oreCount,
                snapshot.describeMetals(),
                snapshot.hasCommission,
                snapshot.hasSelectedMould,
                snapshot.hasPreform,
                snapshot.canPour,
                snapshot.canPickupPreform,
                materialPlan.getFirst().getName(),
                snapshot.firstInventoryCount,
                materialPlan.getSecond().getName(),
                snapshot.secondInventoryCount,
                snapshot.moving,
                snapshot.animating,
                snapshot.position);
        lastCalculatedState = calculatedState;
        lastSnapshotLogAt = now;
    }

    private boolean actionReady()
    {
        return System.currentTimeMillis() - lastActionAt >= ACTION_COOLDOWN_MS;
    }

    private void markAction()
    {
        lastActionAt = System.currentTimeMillis();
    }

    private void resetCycle()
    {
        inventoryPrepared = false;
        firstMaterialAdded = false;
        secondMaterialAdded = false;
        bonusClickConsumed = false;
        lastBonusActive = false;
        temperatureActionInProgress = false;
        materialsCompleteAt = 0;
    }

    private void setState(State nextState, String nextStatus)
    {
        if (state != nextState)
        {
            log.info("Giants' Foundry transition: {} -> {} ({})", state, nextState, nextStatus);
        }
        state = nextState;
        status = nextStatus;
    }

    private void setError(String message)
    {
        if (!message.equals(error))
        {
            Microbot.log("Giants' Foundry: " + message);
            log.warn("Giants' Foundry: {}", message);
        }
        error = message;
        state = State.ERROR;
        status = message;
        errorLatched = true;
        markAction();
    }

    private void clearError()
    {
        error = "";
        errorLatched = false;
    }

    private static String safeMessage(Exception exception)
    {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public static String getStatus()
    {
        return status;
    }

    public static String getError()
    {
        return error;
    }

    public static String getMaterialDescription()
    {
        return materialDescription;
    }

    @Override
    public void shutdown()
    {
        GiantsFoundryState.reset();
        GiantsFoundryState.heatingCoolingState.stop();
        resetCycle();
        clearError();
        super.shutdown();
    }

    private static final class FoundrySnapshot
    {
        private final int progress;
        private final int heat;
        private final int preformQuality;
        private final int preformStartQuality;
        private final int gameStage;
        private final boolean hasPreform;
        private final boolean canPickupPreform;
        private final boolean canPour;
        private final boolean hasCommission;
        private final boolean hasSelectedMould;
        private final Stage stage;
        private final int heatChange;
        private final int[] oreCounts;
        private final int oreCount;
        private final int firstInventoryCount;
        private final int secondInventoryCount;
        private final boolean moving;
        private final boolean animating;
        private final String position;

        private FoundrySnapshot(
                int progress,
                int heat,
                int preformQuality,
                int preformStartQuality,
                int gameStage,
                boolean hasPreform,
                boolean canPickupPreform,
                boolean canPour,
                boolean hasCommission,
                boolean hasSelectedMould,
                Stage stage,
                int heatChange,
                int[] oreCounts,
                int oreCount,
                int firstInventoryCount,
                int secondInventoryCount,
                boolean moving,
                boolean animating,
                String position)
        {
            this.progress = progress;
            this.heat = heat;
            this.preformQuality = preformQuality;
            this.preformStartQuality = preformStartQuality;
            this.gameStage = gameStage;
            this.hasPreform = hasPreform;
            this.canPickupPreform = canPickupPreform;
            this.canPour = canPour;
            this.hasCommission = hasCommission;
            this.hasSelectedMould = hasSelectedMould;
            this.stage = stage;
            this.heatChange = heatChange;
            this.oreCounts = oreCounts;
            this.oreCount = oreCount;
            this.firstInventoryCount = firstInventoryCount;
            this.secondInventoryCount = secondInventoryCount;
            this.moving = moving;
            this.animating = animating;
            this.position = position;
        }

        private FoundryStateResolver.Facts toFacts()
        {
            return new FoundryStateResolver.Facts(
                    progress,
                    hasPreform,
                    canPickupPreform,
                    canPour,
                    hasCommission,
                    hasSelectedMould,
                    stage != null,
                    heatChange,
                    preformQuality);
        }

        private String describeMetals()
        {
            StringBuilder description = new StringBuilder();
            SmithableBars[] metals = SmithableBars.values();
            for (int i = 0; i < metals.length; i++)
            {
                if (i > 0)
                {
                    description.append(", ");
                }
                description.append(metals[i].name()).append('=').append(oreCounts[i]);
            }
            return description.toString();
        }
    }
}
