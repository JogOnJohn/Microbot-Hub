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
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;
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
    private long lastActionAt;

    public boolean run(GiantsFoundryConfig config)
    {
        this.config = config;
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
        if (!validateRuntime())
        {
            return;
        }

        Rs2ItemModel weapon = get(EquipmentInventorySlot.WEAPON);
        boolean hasPreform = isPreform(weapon);
        int progress = GiantsFoundryState.getProgressAmount();

        if (progress >= MAX_PROGRESS)
        {
            handIn();
            return;
        }
        if (hasPreform)
        {
            handleRefinement();
            return;
        }
        if (canPickupPreform())
        {
            pickupPreform();
            return;
        }
        if (canPour())
        {
            pourCrucible();
            return;
        }
        if (!hasCommission())
        {
            getCommission();
            return;
        }
        if (!hasSelectedMould())
        {
            selectMould();
            return;
        }
        fillCrucible();
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
            Rs2Dialogue.clickOption("commission", "sword");
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

    private void fillCrucible()
    {
        int oreCount = GiantsFoundryState.getOreCount();
        if (!inventoryPrepared && oreCount > 0 && oreCount < FoundryMaterialPlanner.REQUIRED_BAR_EQUIVALENT)
        {
            setError("Crucible is partially filled; finish or empty it manually before resuming.");
            return;
        }
        if (!inventoryPrepared && !prepareMaterials())
        {
            return;
        }

        setState(State.FILLING_CRUCIBLE, "Loading crucible (" + oreCount + "/28)");
        if (!firstMaterialAdded)
        {
            firstMaterialAdded = addMaterial(materialPlan.getFirst());
            return;
        }
        if (!secondMaterialAdded)
        {
            secondMaterialAdded = addMaterial(materialPlan.getSecond());
            return;
        }
        if (!canPour())
        {
            setError("Materials were loaded but the crucible is not full; stopping to prevent material loss.");
        }
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

    private boolean addMaterial(FoundryMaterialPlan.Material material)
    {
        if (!Rs2Inventory.hasItemAmount(material.getName(), material.getQuantity()))
        {
            setError("Missing " + material.getQuantity() + " " + material.getName() + " from inventory.");
            return false;
        }
        int before = GiantsFoundryState.getOreCount();
        boolean interacted = materialPlan.isRecycledItems()
                ? addRecycledItems(material)
                : addBars(material);
        if (!interacted)
        {
            return false;
        }
        int expected = before + material.getBarEquivalentAmount();
        if (!sleepUntil(() -> GiantsFoundryState.getOreCount() >= expected || canPour(), 6000))
        {
            setError("Crucible count did not update after adding " + material.getName() + ".");
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

    private void handleRefinement()
    {
        Stage stage = GiantsFoundryState.getCurrentStage();
        if (stage == null)
        {
            setState(State.WAITING, "Waiting for the Foundry HUD");
            return;
        }
        int change = GiantsFoundryState.getHeatChangeNeeded();
        if (change > 0)
        {
            adjustTemperature(true, change);
            return;
        }
        if (change < 0)
        {
            adjustTemperature(false, -change);
            return;
        }
        craftWeapon(stage);
    }

    private void adjustTemperature(boolean heating, int change)
    {
        boolean fast = change >= FAST_HEAT_THRESHOLD;
        String action = heating
                ? (fast ? "Dunk-preform" : "Heat-preform")
                : (fast ? "Quench-preform" : "Cool-preform");
        setState(heating ? State.HEATING : State.COOLING_DOWN,
                (fast ? "Fast " : "Fine ") + (heating ? "heating" : "cooling") + " (" + change + ")");
        if (!actionReady() || Rs2Player.isMoving()
                || (Rs2Player.isAnimating(1200) && !temperatureActionInProgress))
        {
            return;
        }

        int objectId = heating ? LAVA_POOL : WATERFALL;
        if (!Microbot.getRs2TileObjectCache().query().interact(objectId, action))
        {
            setError("Could not start " + action + ".");
            return;
        }
        markAction();
        temperatureActionInProgress = true;
        GiantsFoundryState.heatingCoolingState.stop();
        GiantsFoundryState.heatingCoolingState.setup(fast, heating, action);
        GiantsFoundryState.heatingCoolingState.start(GiantsFoundryState.getHeatAmount());
        sleepUntil(() -> GiantsFoundryState.heatingCoolingState.getRemainingDuration() <= 1
                || GiantsFoundryState.getHeatChangeNeeded() == 0, 20000);
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
    }

    private void handIn()
    {
        setState(State.HANDING_IN, "Handing the sword to Kovac");
        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            sleep(400, 700);
            return;
        }
        if (GiantsFoundryState.getProgressAmount() < MAX_PROGRESS)
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
        if (!sleepUntil(() -> Rs2Dialogue.hasContinue() || GiantsFoundryState.getProgressAmount() < MAX_PROGRESS, 5000))
        {
            setError("Kovac did not acknowledge the completed sword.");
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
    }

    private void setState(State nextState, String nextStatus)
    {
        if (state != nextState || !status.equals(nextStatus))
        {
            log.info("Giants' Foundry: {} - {}", nextState, nextStatus);
        }
        state = nextState;
        status = nextStatus;
        if (nextState != State.ERROR)
        {
            error = "";
        }
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
        markAction();
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
        super.shutdown();
    }
}
