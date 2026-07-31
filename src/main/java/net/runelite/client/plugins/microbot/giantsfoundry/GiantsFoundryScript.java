package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
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
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
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
    private static final long GAME_TICK_MS = 600;
    private static final int PASSIVE_COOLING_TIMEOUT_GRACE_TICKS = 4;
    private static final int VARP_FOUNDRY_REPUTATION = 3436;

    public static volatile State state = State.VALIDATING;
    private static volatile String status = "Starting";
    private static volatile String error = "";
    private static volatile String materialDescription = "Not resolved";
    private static volatile String supplyDescription = "Checking at the Foundry bank";
    private static volatile String currentCraftDescription = "No active commission";
    private static volatile String nextShopPurchase = "None";
    private static volatile int currentProgress;
    private static volatile int currentQuality;
    private static volatile int currentStartQuality;
    private static volatile int currentSmithingLevel;
    private static volatile int smithingLevelsGained;
    private static volatile int smithingXpGained;
    private static volatile int successfulCrafts;
    private static volatile int currentReputation;
    private static volatile int reputationEarned;
    private static volatile int reputationSpent;
    private static volatile long materialCost;
    private static volatile long sessionStartedAt;

    private GiantsFoundryConfig config;
    private FoundryMaterialPlan materialPlan;
    private boolean inventoryPrepared;
    private boolean firstMaterialAdded;
    private boolean secondMaterialAdded;
    private boolean bonusClickConsumed;
    private boolean lastBonusActive;
    private boolean temperatureActionInProgress;
    private boolean errorLatched;
    private boolean cycleCostRecorded;
    private boolean cycleCompletionRecorded;
    private boolean supplySnapshotKnown;
    private int sessionStartSmithingLevel = -1;
    private int sessionStartSmithingXp = -1;
    private int cyclePlanLevel;
    private long lastActionAt;
    private long lastSnapshotLogAt;
    private long materialsCompleteAt;
    private State lastCalculatedState;
    private Stage lastObservedStage;
    private boolean interruptForStageChange;
    private boolean passiveCoolingWaitActive;
    private boolean passiveCoolingWaitTimedOut;
    private int passiveCoolingStartHeat;
    private long passiveCoolingWaitStartedAt;
    private long passiveCoolingWaitDeadlineAt;
    private Stage passiveCoolingWaitStage;

    public boolean run(GiantsFoundryConfig config)
    {
        this.config = config;
        clearError();
        resetSessionMetrics();
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
            catch (Throwable throwable)
            {
                if (throwable instanceof ThreadDeath)
                {
                    throw (ThreadDeath) throwable;
                }
                if (throwable instanceof VirtualMachineError)
                {
                    throw (VirtualMachineError) throwable;
                }
                setError("Unexpected error: " + safeMessage(throwable));
                log.error("Giants' Foundry tick failed", throwable);
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
        updateLiveMetrics(snapshot);
        detectStageChange(snapshot);
        if (snapshot.oreCount > 0)
        {
            recordCycleMaterialCost();
        }
        State calculatedState = FoundryStateResolver.calculate(snapshot.toFacts());
        logSnapshot(calculatedState, snapshot);
        if (calculatedState != State.COOLING_DOWN)
        {
            finishPassiveCoolingWait(snapshot.heat, "temperature entered the working range");
            resetPassiveCoolingWait();
        }

        switch (calculatedState)
        {
            case HANDING_IN:
                handIn(snapshot);
                break;
            case HEATING:
                adjustTemperature(true, snapshot.heatChange, snapshot.stage);
                break;
            case COOLING_DOWN:
                adjustTemperature(false, -snapshot.heatChange, snapshot.stage);
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
                if (!handleRewardShop())
                {
                    getCommission();
                }
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
        if (materialPlan == null && !resolveMaterialPlan(level))
        {
            return false;
        }
        initializeSessionMetrics(level);
        updateShopTarget(level);
        return true;
    }

    private boolean resolveMaterialPlan(int level)
    {
        FoundryMaterialPlanner.PlanResult result = FoundryMaterialPlanner.create(config, level);
        if (!result.isValid())
        {
            setError(result.getError());
            return false;
        }
        materialPlan = result.getPlan();
        materialDescription = materialPlan.getDescription();
        cyclePlanLevel = level;
        log.info("Giants' Foundry: locked material plan at Smithing {}: {}", level, materialDescription);
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

    private boolean handleRewardShop()
    {
        int level = Rs2Player.getRealSkillLevel(Skill.SMITHING);
        FoundryShopPlanner.Purchase purchase = findNextShopPurchase(level);
        nextShopPurchase = purchase == null
                ? "None"
                : purchase.getName() + " (" + purchase.getCost() + " rep)";
        if (purchase == null || getFoundryReputation() < purchase.getCost())
        {
            return false;
        }

        setState(State.BUYING_REWARDS, "Buying " + purchase.getName());
        if (!FoundryRewardShop.isOpen())
        {
            if (!actionReady())
            {
                return true;
            }
            if (!FoundryRewardShop.open())
            {
                setError("Could not open Kovac's reward shop.");
                return true;
            }
            markAction();
            if (!sleepUntil(FoundryRewardShop::isOpen, 5000))
            {
                setError("Kovac's reward shop did not open.");
            }
            return true;
        }
        if (!actionReady())
        {
            return true;
        }

        int pointsBefore = getFoundryReputation();
        if (!FoundryRewardShop.purchase(purchase))
        {
            setError("Could not purchase " + purchase.getName() + " from Kovac.");
            return true;
        }
        markAction();
        boolean confirmed = sleepUntil(() -> purchaseCompleted(purchase)
                || getFoundryReputation() < pointsBefore, 5000);
        int pointsAfter = getFoundryReputation();
        log.info("Giants' Foundry shop result: item={} confirmed={} reputation={}->{}",
                purchase.getName(), confirmed, pointsBefore, pointsAfter);
        if (!confirmed)
        {
            setError("Could not confirm purchasing " + purchase.getName() + ".");
            return true;
        }
        reputationSpent += Math.max(0, pointsBefore - pointsAfter);
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleepUntil(() -> !FoundryRewardShop.isOpen(), 3000);
        updateShopTarget(level);
        return true;
    }

    private FoundryShopPlanner.Purchase findNextShopPurchase(int level)
    {
        return FoundryShopPlanner.nextTarget(
                level,
                config.shopStrategy(),
                varbit -> Microbot.getVarbitValue(varbit) > 0,
                this::ownsReward);
    }

    private void updateShopTarget(int level)
    {
        FoundryShopPlanner.Purchase purchase = findNextShopPurchase(level);
        nextShopPurchase = purchase == null
                ? "None"
                : purchase.getName() + " (" + purchase.getCost() + " rep)";
    }

    private boolean purchaseCompleted(FoundryShopPlanner.Purchase purchase)
    {
        return purchase.isMould()
                ? Microbot.getVarbitValue(purchase.getUnlockVarbit()) > 0
                : ownsReward(purchase.getItemId());
    }

    private boolean ownsReward(int itemId)
    {
        if (itemId == ItemID.SMITHS_GLOVES
                && (Rs2Bank.hasItem(ItemID.SMITHS_GLOVES_I)
                || Rs2Inventory.hasItem(ItemID.SMITHS_GLOVES_I)
                || Rs2Equipment.isWearing(ItemID.SMITHS_GLOVES_I)))
        {
            return true;
        }
        return Rs2Bank.hasItem(itemId) || Rs2Inventory.hasItem(itemId) || Rs2Equipment.isWearing(itemId);
    }

    private int getFoundryReputation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarpValue(VARP_FOUNDRY_REPUTATION)).orElse(0);
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

        if (inventoryPrepared && snapshot.oreCount == 0
                && !hasRemainingMaterials(snapshot, firstLoaded, secondLoaded))
        {
            log.warn("Giants' Foundry: prepared-material flag was stale with an empty crucible; reopening the bank");
            inventoryPrepared = false;
            prepareMaterials();
            return;
        }

        if (!inventoryPrepared)
        {
            if (hasRemainingMaterials(snapshot, firstLoaded, secondLoaded))
            {
                if (!supplySnapshotKnown)
                {
                    refreshSupplySnapshot(false);
                }
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
        int firstSupply = availableSupply(materialPlan.getFirst());
        int secondSupply = availableSupply(materialPlan.getSecond());
        updateSupplySnapshot(firstSupply, secondSupply);
        String shortage = materialPlan.getSupplyShortage(firstSupply, secondSupply);
        if (shortage != null)
        {
            Rs2Bank.closeBank();
            stopForSupplies("Stopped: current level strategy is " + materialDescription + ", " + shortage + ".");
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
            int refreshedFirst = availableSupply(materialPlan.getFirst());
            int refreshedSecond = availableSupply(materialPlan.getSecond());
            updateSupplySnapshot(refreshedFirst, refreshedSecond);
            Rs2Bank.closeBank();
            stopForSupplies("Stopped: the bank could not provide " + materialDescription + ".");
            return false;
        }
        if (!Rs2Bank.closeBank())
        {
            setError("Could not close the bank after withdrawing materials.");
            return false;
        }
        inventoryPrepared = true;
        recordCycleMaterialCost();
        updateSupplySnapshot(
                availableSupply(materialPlan.getFirst()),
                availableSupply(materialPlan.getSecond()));
        return true;
    }

    private int availableSupply(FoundryMaterialPlan.Material material)
    {
        return Rs2Bank.count(material.getName(), true) + Rs2Inventory.count(material.getName());
    }

    private void refreshSupplySnapshot(boolean depositInventory)
    {
        if (!Rs2Bank.openBank())
        {
            return;
        }
        if (depositInventory)
        {
            Rs2Bank.depositAll();
        }
        updateSupplySnapshot(
                availableSupply(materialPlan.getFirst()),
                availableSupply(materialPlan.getSecond()));
        Rs2Bank.closeBank();
    }

    private void updateSupplySnapshot(int firstSupply, int secondSupply)
    {
        supplySnapshotKnown = true;
        int crafts = materialPlan.getCraftsAvailable(firstSupply, secondSupply);
        supplyDescription = materialPlan.getFirst().getName() + " " + firstSupply
                + " | " + materialPlan.getSecond().getName() + " " + secondSupply
                + " (" + crafts + " crafts)";
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

    private void adjustTemperature(boolean heating, int change, Stage stage)
    {
        boolean fast = change >= FAST_HEAT_THRESHOLD;
        String action = heating
                ? (fast ? "Dunk-preform" : "Heat-preform")
                : (fast ? "Quench-preform" : "Cool-preform");
        setState(heating ? State.HEATING : State.COOLING_DOWN,
                (fast ? "Fast " : "Fine ") + (heating ? "heating" : "cooling") + " (" + change + ")");
        if (heating)
        {
            resetPassiveCoolingWait();
        }
        else if (shouldWaitForPassiveCooling(change, stage))
        {
            return;
        }
        if (!interruptForStageChange && (!actionReady() || Rs2Player.isMoving()))
        {
            return;
        }

        finishPassiveCoolingWait(GiantsFoundryState.getHeatAmount(), "waterfall route became faster");
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
        // Let residual temperature momentum settle before the next decision.
        markAction();
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

    private boolean shouldWaitForPassiveCooling(int change, Stage stage)
    {
        // Waiting is only safe after a temperature-tool action. At a workstation,
        // the current animation may keep changing heat until another interaction interrupts it.
        if (!temperatureActionInProgress
                || stage == null
                || change >= FAST_HEAT_THRESHOLD
                || BonusWidget.isActive()
                || interruptForStageChange
                || Rs2Player.isMoving())
        {
            return false;
        }
        if (passiveCoolingWaitStage != null && passiveCoolingWaitStage != stage)
        {
            resetPassiveCoolingWait();
        }
        if (passiveCoolingWaitTimedOut)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Rs2TileObjectModel waterfall = Microbot.getRs2TileObjectCache().query()
                .withId(WATERFALL)
                .nearestOnClientThread();
        if (playerLocation == null || waterfall == null || waterfall.getWorldLocation() == null)
        {
            return false;
        }

        PassiveCoolingPlanner.Decision decision = PassiveCoolingPlanner.decide(
                change,
                playerLocation.distanceTo(waterfall.getWorldLocation()),
                stage.getDistanceToWaterfall(),
                playerLocation.distanceTo(stage.getLocation()),
                GiantsFoundryState.isPlayerRunning());
        if (!decision.isWait())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (passiveCoolingWaitActive && now >= passiveCoolingWaitDeadlineAt)
        {
            log.info("Giants' Foundry action: passive cooling timed out stage={} heat={}->{} elapsedMs={}; "
                            + "using the waterfall",
                    stage,
                    passiveCoolingStartHeat,
                    GiantsFoundryState.getHeatAmount(),
                    now - passiveCoolingWaitStartedAt);
            passiveCoolingWaitActive = false;
            passiveCoolingWaitTimedOut = true;
            return false;
        }

        if (!passiveCoolingWaitActive)
        {
            passiveCoolingWaitActive = true;
            passiveCoolingWaitStage = stage;
            passiveCoolingStartHeat = GiantsFoundryState.getHeatAmount();
            passiveCoolingWaitStartedAt = now;
            passiveCoolingWaitDeadlineAt = now
                    + (decision.getPassiveWaitTicks() + PASSIVE_COOLING_TIMEOUT_GRACE_TICKS) * GAME_TICK_MS;
            log.info("Giants' Foundry action: passive cooling stage={} heat={} change={} waitTicks={} "
                            + "passiveRouteTicks={} waterfallRouteTicks={} savedTicks={} position={}",
                    stage,
                    passiveCoolingStartHeat,
                    change,
                    decision.getPassiveWaitTicks(),
                    decision.getPassiveRouteTicks(),
                    decision.getWaterfallRouteTicks(),
                    decision.getSavedTicks(),
                    playerLocation);
        }
        setState(State.COOLING_DOWN,
                "Waiting for passive cooling (" + decision.getPassiveWaitTicks() + " ticks)");
        return true;
    }

    private void finishPassiveCoolingWait(int heat, String reason)
    {
        if (!passiveCoolingWaitActive)
        {
            return;
        }
        log.info("Giants' Foundry action result: passive cooling heat={}->{} elapsedMs={} reason={}",
                passiveCoolingStartHeat,
                heat,
                System.currentTimeMillis() - passiveCoolingWaitStartedAt,
                reason);
        passiveCoolingWaitActive = false;
    }

    private void resetPassiveCoolingWait()
    {
        passiveCoolingWaitActive = false;
        passiveCoolingWaitTimedOut = false;
        passiveCoolingStartHeat = 0;
        passiveCoolingWaitStartedAt = 0;
        passiveCoolingWaitDeadlineAt = 0;
        passiveCoolingWaitStage = null;
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

        if (!interruptForStageChange && (Rs2Player.isMoving() || !actionReady()))
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
        else if (Rs2Player.isAnimating(1200)
                && !temperatureActionInProgress
                && !interruptForStageChange)
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

    private void handIn(FoundrySnapshot snapshot)
    {
        boolean damaged = snapshot.hasPreform && snapshot.preformQuality <= 0;
        setState(State.HANDING_IN, damaged ? "Returning damaged sword to Kovac" : "Handing the sword to Kovac");
        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            sleep(400, 700);
            completeHandInIfAcknowledged(snapshot, damaged);
            return;
        }
        if (!damaged && snapshot.progress < MAX_PROGRESS)
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
        completeHandInIfAcknowledged(snapshot, damaged);
    }

    private boolean completeHandInIfAcknowledged(FoundrySnapshot snapshot, boolean damaged)
    {
        boolean preformRemoved = !isPreform(get(EquipmentInventorySlot.WEAPON));
        boolean progressReset = !damaged
                && snapshot.progress >= MAX_PROGRESS
                && GiantsFoundryState.getProgressAmount() < MAX_PROGRESS;
        if (!preformRemoved && !progressReset)
        {
            return false;
        }

        log.info("Giants' Foundry hand-in acknowledged: preformRemoved={} progressReset={}",
                preformRemoved, progressReset);
        recordCompletedCraft(snapshot, damaged);
        GiantsFoundryState.reset();
        resetCycle();
        return true;
    }

    private void recordCompletedCraft(FoundrySnapshot snapshot, boolean damaged)
    {
        if (cycleCompletionRecorded || damaged || snapshot.progress < MAX_PROGRESS)
        {
            return;
        }
        cycleCompletionRecorded = true;
        successfulCrafts++;
        reputationEarned += Math.max(0, snapshot.preformQuality);
        log.info("Giants' Foundry craft complete: sessionCraft={} quality={}/{} xpGained={} materialCost={} netGp={}",
                successfulCrafts,
                snapshot.preformQuality,
                snapshot.preformStartQuality,
                smithingXpGained,
                materialCost,
                getNetGp());
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
        int heatChange = GiantsFoundryState.calculateHeatChangeNeeded(
                currentStage, heat, currentHeatRange);
        if (suppressHeatTopUp(heatChange, currentStage, heat, currentHeatRange))
        {
            heatChange = 0;
        }
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
                heatChange,
                oreCounts,
                GiantsFoundryState.totalOreCount(oreCounts),
                Rs2Inventory.count(materialPlan.getFirst().getName()),
                Rs2Inventory.count(materialPlan.getSecond().getName()),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                String.valueOf(Rs2Player.getWorldLocation()));
    }

    /**
     * Avoids a proactive top-up when the current heat supports enough remaining
     * workstation actions. This prevents immediate second sips after momentum settles.
     */
    private boolean suppressHeatTopUp(int heatChange, Stage stage, int heat, int[] range)
    {
        if (heatChange == 0 || stage == null || range == null || range.length < 2)
        {
            return false;
        }
        if (heat <= range[0] || heat >= range[1])
        {
            return false;
        }
        int actionsLeft = GiantsFoundryState.getActionsLeftInStage();
        if (actionsLeft <= 0)
        {
            return false;
        }
        return GiantsFoundryState.countActionsAvailable(heat, range, stage)
                >= Math.min(actionsLeft, 3);
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
        interruptForStageChange = false;
    }

    /**
     * A stage flip invalidates the previous workstation immediately. The scheduler
     * retains sole ownership of interactions, but bypasses movement and cooldown
     * guards once so it can redirect before another wrong-stage action lands.
     */
    private void detectStageChange(FoundrySnapshot snapshot)
    {
        if (snapshot.stage == null)
        {
            if (snapshot.progress <= 0)
            {
                lastObservedStage = null;
                interruptForStageChange = false;
            }
            return;
        }
        if (lastObservedStage != null && snapshot.stage != lastObservedStage)
        {
            log.info("Giants' Foundry: stage changed {} -> {}; scheduling immediate workstation interrupt",
                    lastObservedStage, snapshot.stage);
            lastActionAt = 0;
            interruptForStageChange = true;
        }
        lastObservedStage = snapshot.stage;
    }

    private void initializeSessionMetrics(int level)
    {
        int xp = getSmithingXp();
        if (sessionStartSmithingXp < 0)
        {
            sessionStartSmithingXp = xp;
            sessionStartSmithingLevel = level;
            currentSmithingLevel = level;
            sessionStartedAt = System.currentTimeMillis();
        }
        currentSmithingLevel = level;
        currentReputation = getFoundryReputation();
        smithingLevelsGained = Math.max(0, level - sessionStartSmithingLevel);
        smithingXpGained = Math.max(0, xp - sessionStartSmithingXp);
    }

    private int getSmithingXp()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(Skill.SMITHING)).orElse(0);
    }

    private void updateLiveMetrics(FoundrySnapshot snapshot)
    {
        initializeSessionMetrics(Rs2Player.getRealSkillLevel(Skill.SMITHING));
        currentProgress = snapshot.progress;
        currentQuality = snapshot.preformQuality;
        currentStartQuality = snapshot.preformStartQuality;
        currentReputation = getFoundryReputation();
        CommissionType first = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_1_VARBIT));
        CommissionType second = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_2_VARBIT));
        currentCraftDescription = first == CommissionType.NONE || second == CommissionType.NONE
                ? "No active commission"
                : readableCommission(first) + " " + readableCommission(second);
    }

    private String readableCommission(CommissionType type)
    {
        String value = type.name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void recordCycleMaterialCost()
    {
        if (cycleCostRecorded || materialPlan == null)
        {
            return;
        }
        long cost = materialValue(materialPlan.getFirst()) + materialValue(materialPlan.getSecond());
        materialCost += Math.max(0, cost);
        cycleCostRecorded = true;
        log.info("Giants' Foundry material accounting: planLevel={} plan={} cycleCost={} totalCost={}",
                cyclePlanLevel, materialDescription, cost, materialCost);
    }

    private long materialValue(FoundryMaterialPlan.Material material)
    {
        int itemId = Rs2ItemManager.getItemIdByName(material.getName(), false);
        if (itemId <= 0)
        {
            return 0;
        }
        int price = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getItemManager().getItemPrice(itemId)).orElse(0);
        return (long) Math.max(0, price) * material.getQuantity();
    }

    private void stopForSupplies(String message)
    {
        Microbot.log("Giants' Foundry: " + message);
        log.warn("Giants' Foundry: {}", message);
        state = State.OUT_OF_SUPPLIES;
        status = message;
        error = "";
        if (mainScheduledFuture != null)
        {
            mainScheduledFuture.cancel(false);
        }
    }

    private void resetSessionMetrics()
    {
        sessionStartSmithingLevel = -1;
        sessionStartSmithingXp = -1;
        currentSmithingLevel = 0;
        smithingLevelsGained = 0;
        smithingXpGained = 0;
        successfulCrafts = 0;
        currentReputation = 0;
        reputationEarned = 0;
        reputationSpent = 0;
        materialCost = 0;
        currentProgress = 0;
        currentQuality = 0;
        currentStartQuality = 0;
        currentCraftDescription = "No active commission";
        supplyDescription = "Checking at the Foundry bank";
        nextShopPurchase = "None";
        supplySnapshotKnown = false;
        sessionStartedAt = System.currentTimeMillis();
    }

    private void resetCycle()
    {
        materialPlan = null;
        materialDescription = "Resolving current level strategy";
        supplySnapshotKnown = false;
        supplyDescription = "Checking current strategy supplies";
        inventoryPrepared = false;
        firstMaterialAdded = false;
        secondMaterialAdded = false;
        bonusClickConsumed = false;
        lastBonusActive = false;
        temperatureActionInProgress = false;
        cycleCostRecorded = false;
        cycleCompletionRecorded = false;
        materialsCompleteAt = 0;
        lastObservedStage = null;
        interruptForStageChange = false;
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

    private static String safeMessage(Throwable throwable)
    {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
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

    public static String getSupplyDescription()
    {
        return supplyDescription;
    }

    public static String getCurrentCraftDescription()
    {
        return currentCraftDescription;
    }

    public static String getNextShopPurchase()
    {
        return nextShopPurchase;
    }

    public static int getCurrentProgress()
    {
        return currentProgress;
    }

    public static int getCurrentQuality()
    {
        return currentQuality;
    }

    public static int getCurrentStartQuality()
    {
        return currentStartQuality;
    }

    public static int getCurrentSmithingLevel()
    {
        return currentSmithingLevel;
    }

    public static int getSmithingLevelsGained()
    {
        return smithingLevelsGained;
    }

    public static int getSmithingXpGained()
    {
        return smithingXpGained;
    }

    public static int getSuccessfulCrafts()
    {
        return successfulCrafts;
    }

    public static int getCurrentReputation()
    {
        return currentReputation;
    }

    public static int getReputationEarned()
    {
        return reputationEarned;
    }

    public static int getReputationSpent()
    {
        return reputationSpent;
    }

    public static long getRewardGp()
    {
        return (long) smithingXpGained * 2;
    }

    public static long getMaterialCost()
    {
        return materialCost;
    }

    public static long getNetGp()
    {
        return getRewardGp() - materialCost;
    }

    public static long getSessionRuntimeMillis()
    {
        return Math.max(0, System.currentTimeMillis() - sessionStartedAt);
    }

    @Override
    public void shutdown()
    {
        GiantsFoundryState.reset();
        GiantsFoundryState.heatingCoolingState.stop();
        resetPassiveCoolingWait();
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
