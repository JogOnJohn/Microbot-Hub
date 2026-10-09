package net.runelite.client.plugins.microbot.nmz;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Singleton
public class NmzScript extends Script {

    private static final long OVERLOAD_DURATION_MS = 300000;
    private static final long OVERLOAD_READY_LEAD_MS = 8000;

    private enum RumblePreparationPhase {
        WAITING_FOR_ENTRY,
        ABSORPTION,
        OVERLOAD,
        ROCK_CAKE,
        COMPLETE
    }

    private NmzConfig config;
    private NmzPlugin plugin;

    public static boolean useOverload = false;

    public static int maxHealth = Rs2Random.between(2, 8);
    public static int minAbsorption = Rs2Random.between(100, 300);

    private WorldPoint center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);

    @Getter
    @Setter
    private static boolean hasSurge = false;
    private boolean initialized = false;
    private RumblePreparationPhase rumblePreparationPhase = RumblePreparationPhase.WAITING_FOR_ENTRY;
    private boolean wasOutsideNmz = true;
    private boolean observedLobbySinceStart;
    private boolean preparationEnteredFromLobby;
    private int initialAbsorptionTarget;
    private long nextLobbyVialLogAt;
    private long nextOverloadExpiryAt;
    private long nextMaintenanceOverloadAttemptAt;
    private long nextMaintenanceOverloadLogAt;
    private boolean maintenanceOverloadPending;
    private long lastCombatTime = 0;
    private boolean specialAttemptedForCurrentSurge;
    private boolean specialActionInFlight;
    private boolean specialAwaitingConsumption;
    private long nextSpecialAttemptAt;
    private String mainWeaponBeforeSpecial;
    private String offhandBeforeSpecial;
    @Getter
    private volatile String overlayState = "Starting";
    @Getter
    private volatile String overlayNextAction = "Initialize NMZ controller";
    @Getter
    private volatile String overlayLastAction = "None";
    @Getter
    private volatile String overlayPowerUp = "None";
    @Getter
    private volatile String overlaySpecial = "Inactive";
    @Getter
    private volatile long overlayActionGeneration;
    @Getter
    private volatile long overlayStateChangedAt = System.currentTimeMillis();
    private volatile long overlayStateHoldUntil;

    @Inject
    private Rs2TileObjectCache tileObjectCache;
    @Inject
    private Rs2NpcCache npcCache;
    @Inject
    private PrayerPotionScript prayerPotionScript;

    public boolean canStartNmz() {
        return Rs2Inventory.count("overload (4)") == config.overloadPotionAmount() ||
                (Rs2Inventory.hasItem("prayer potion") && config.togglePrayerPotions());
    }

    @Inject
    public NmzScript(NmzPlugin plugin, NmzConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    public boolean run() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        Rs2Antiban.setPlayStyle(PlayStyle.MODERATE);
        Rs2Antiban.activateAntiban();
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.actionCooldownChance = 0.00;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.80;


        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!initialized) {
                    WorldPoint playerLocation = Rs2Player.getWorldLocation();
                    if (playerLocation == null) return;
                    updateOverlayState("Initializing", "Detect NMZ location and setup");
                    initialized = true;
                    // Skip inventory setup and lobby walk if already inside the NMZ instance
                    boolean isInNmzInstance = playerLocation.getPlane() == 3 || playerLocation.getY() > 4500;
                    if (!isInNmzInstance) {
                        if (config.inventorySetupon()) {
                            if (config.inventorySetup() != null) {
                                var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                                if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                                    Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                                    if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                                        Microbot.log("Failed to load inventory setup");
                                        Microbot.stopPlugin(plugin);
                                        return;
                                    }
                                    Rs2Bank.closeBank();
                                }
                            }
                        }
                        Rs2Walker.walkTo(new WorldPoint(2609, 3114, 0), 5);
                    }
                }
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive && !isMaintenanceOverloadWindow()) return;
                Rs2Combat.setAutoRetaliate(true);
                boolean isOutsideNmz = isOutside();
                useOverload = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) == Microbot.getClient().getRealSkillLevel(Skill.RANGED) && config.overloadPotionAmount() > 0;
                if (isOutsideNmz) {
                    wasOutsideNmz = true;
                    observedLobbySinceStart = true;
                    rumblePreparationPhase = RumblePreparationPhase.WAITING_FOR_ENTRY;
                    nextOverloadExpiryAt = 0;
                    maintenanceOverloadPending = false;
                    updateOverlayState("NMZ lobby", "Prepare supplies or enter dream");
                    Rs2Walker.setTarget(null);
                    handleOutsideNmz();
                } else {
                    if (wasOutsideNmz || rumblePreparationPhase == RumblePreparationPhase.WAITING_FOR_ENTRY) {
                        beginRumblePreparation(wasOutsideNmz && observedLobbySinceStart);
                    }
                    wasOutsideNmz = false;
                    handleInsideNmz();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.deactivateAntiban();
        Rs2Antiban.resetAntibanSettings();
        initialized = false;
        wasOutsideNmz = true;
        observedLobbySinceStart = false;
        preparationEnteredFromLobby = false;
        rumblePreparationPhase = RumblePreparationPhase.WAITING_FOR_ENTRY;
        nextOverloadExpiryAt = 0;
        maintenanceOverloadPending = false;
    }

    public boolean isOutside() {
        WorldPoint loc = Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) return null;
            return Microbot.getClient().getLocalPlayer().getWorldLocation();
        });
        return loc != null && loc.distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
    }

    public void handleOutsideNmz() {
        boolean hasStartedDream = Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0;
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (!hasStartedDream) {
            startNmzDream();
        } else {
            final String overload = "Overload (4)";
            final String absorption = "Absorption (4)";
            storePotions(ObjectID.NZONE_BARREL_3, "overload", config.overloadPotionAmount());
            storePotions(ObjectID.NZONE_BARREL_4, "absorption", config.absorptionPotionAmount());
            handleStore();
            fetchOverloadPotions(ObjectID.NZONE_BARREL_3, overload, config.overloadPotionAmount());
            if (Rs2Inventory.hasItemAmount(overload, config.overloadPotionAmount())) {
                fetchPotions(ObjectID.NZONE_BARREL_4, absorption, config.absorptionPotionAmount());
            }
        }
        if (canStartNmz()) {
            consumeEmptyVial();
        } else {
            sleep(2000);
        }
    }

    public void handleInsideNmz() {
        if (handleRumblePreparation()) return;
        if (handleMaintenanceOverload()) return;

        updateOverlayIdle("Evaluate combat, power-ups and supplies");
        if (Rs2Player.isInCombat()) {
            lastCombatTime = System.currentTimeMillis();
        }
        Rs2Antiban.takeMicroBreakByChance();
        if (!Rs2Player.isInCombat() && System.currentTimeMillis() - lastCombatTime > 20000) {
            Rs2NpcModel closestNpc = npcCache.query().nearest();
            if (closestNpc != null) {
                updateOverlayAction("Attack " + closestNpc.getName(), "Wait for combat", 2500);
                if (closestNpc.click("Attack")) {
                    Rs2Antiban.actionCooldown();
                }
            }
        }
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        boolean usedPowerUp = useOrbs();
        useManualSpecialAfterSurge();
        if (!usedPowerUp && config.walkToCenter()) {
            walkToCenter();
        }
        manageSelfHarm();
        useAbsorptionPotion();
    }

    private void walkToCenter() {
        if (center.distanceTo(Rs2Player.getWorldLocation()) > 4) {
            updateOverlayAction("Walk to NMZ center", "Resume combat", 2500);
            Rs2Walker.walkTo(center, 6);
        }
    }

    public void startNmzDream() {
        // Set new center so that it is random for every time joining the dream
        center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
        Rs2NpcModel dominic = npcCache.query().withName("Dominic Onion").nearestOnClientThread();
        if (dominic != null) dominic.click("Dream");
        sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"));
        Rs2Widget.clickWidget("Previous:");
        sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"));
        Rs2Widget.clickWidget("Click here to continue");
        sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"));
        if (Rs2Widget.hasWidget("Agree to pay")) {
            Rs2Keyboard.typeString("1");
            Rs2Keyboard.enter();
        }
    }

    public boolean useOrbs() {
        if (config.useZapper() && interactWithObject(ObjectID.NZONE_POWERUP_ZAPPER)) return true;
        if (config.useReccurentDamage() && interactWithObject(ObjectID.NZONE_POWERUP_DAMAGEMULTIPLIER)) return true;
        return config.usePowerSurge() && interactWithObject(ObjectID.NZONE_POWERUP_SPECIALATTACK);
    }

    private void beginRumblePreparation(boolean enteredFromLobby) {
        if (config.togglePrayerPotions()) {
            rumblePreparationPhase = RumblePreparationPhase.COMPLETE;
            return;
        }
        preparationEnteredFromLobby = enteredFromLobby;
        nextOverloadExpiryAt = 0;
        maintenanceOverloadPending = false;
        initialAbsorptionTarget = Rs2Random.between(200, 300);
        rumblePreparationPhase = RumblePreparationPhase.ABSORPTION;
        maxHealth = 1;
        Microbot.log("NMZ preparation: " + (enteredFromLobby ? "lobby entry" : "mid-rumble startup")
                + ", absorption target=" + initialAbsorptionTarget);
        updateOverlayAction("Initial preparation", "Drink absorption potions", 1000);
    }

    private boolean handleRumblePreparation() {
        if (config.togglePrayerPotions() || rumblePreparationPhase == RumblePreparationPhase.COMPLETE) return false;

        switch (rumblePreparationPhase) {
            case ABSORPTION:
                prepareInitialAbsorption();
                return true;
            case OVERLOAD:
                prepareInitialOverload();
                return true;
            case ROCK_CAKE:
                prepareInitialRockCake();
                return true;
            case WAITING_FOR_ENTRY:
                beginRumblePreparation(false);
                return true;
            default:
                return false;
        }
    }

    private void prepareInitialAbsorption() {
        updateOverlayState("Initial preparation - absorption", "Reach " + initialAbsorptionTarget + " absorption");
        if (!switchToTab(InterfaceTab.INVENTORY, "initial absorption")) return;

        int absorption = Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
        int attempts = 0;
        while (absorption < initialAbsorptionTarget && attempts++ < 8 && Rs2Inventory.hasItem("absorption")) {
            int previousAbsorption = absorption;
            if (!Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("absorption"), "drink")) break;
            sleepUntil(() -> Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS) > previousAbsorption, 1800);
            absorption = Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
            if (absorption <= previousAbsorption) break;
        }

        if (absorption >= initialAbsorptionTarget || !Rs2Inventory.hasItem("absorption")) {
            Microbot.log("NMZ preparation: absorption complete at " + absorption + " after " + attempts + " attempt(s)");
            rumblePreparationPhase = RumblePreparationPhase.OVERLOAD;
            updateOverlayAction("Initial absorption ready", "Drink overload", 1000);
        } else {
            Microbot.log("NMZ preparation: absorption retry at " + absorption + "/" + initialAbsorptionTarget);
        }
    }

    private void prepareInitialOverload() {
        updateOverlayState("Initial preparation - overload", "Drink overload and wait for damage");
        if (config.overloadPotionAmount() <= 0 || !Rs2Inventory.hasItem("overload")) {
            Microbot.log("NMZ preparation: no overload configured or available; continuing to self-damage");
            rumblePreparationPhase = RumblePreparationPhase.ROCK_CAKE;
            return;
        }
        if (!preparationEnteredFromLobby
                && Microbot.getClient().getBoostedSkillLevel(Skill.RANGED)
                > Microbot.getClient().getRealSkillLevel(Skill.RANGED)) {
            Microbot.log("NMZ preparation: mid-rumble overload boost already active; continuing to self-damage");
            rumblePreparationPhase = RumblePreparationPhase.ROCK_CAKE;
            return;
        }
        if (!switchToTab(InterfaceTab.INVENTORY, "initial overload")) return;

        int hitpointsBefore = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int rangedBefore = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED);
        long overloadClickedAt = System.currentTimeMillis();
        if (!Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("overload"), "drink")) {
            Microbot.log("NMZ preparation: overload interaction not acknowledged; retrying");
            return;
        }

        sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < hitpointsBefore
                || Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) > rangedBefore, 3500);
        int currentHitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        boolean acknowledged = currentHitpoints < hitpointsBefore
                || Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) > rangedBefore;
        if (!acknowledged) {
            Microbot.log("NMZ preparation: overload produced no HP or stat change; retrying");
            return;
        }
        recordOverloadDose(overloadClickedAt, "initial");

        updateOverlayAction("Initial overload damage", "Wait for damage ticks to stop", 7000);
        long deadline = System.currentTimeMillis() + 12000;
        long lastDamageAt = System.currentTimeMillis();
        int lastHitpoints = currentHitpoints;
        while (Microbot.isLoggedIn()
                && !Thread.currentThread().isInterrupted()
                && !isOutside()
                && System.currentTimeMillis() < deadline
                && System.currentTimeMillis() - lastDamageAt < 2400) {
            sleep(200);
            int observedHitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (observedHitpoints < lastHitpoints) {
                lastHitpoints = observedHitpoints;
                lastDamageAt = System.currentTimeMillis();
            }
        }
        Microbot.log("NMZ preparation: overload damage complete at hp=" + lastHitpoints);
        rumblePreparationPhase = RumblePreparationPhase.ROCK_CAKE;
    }

    private boolean handleMaintenanceOverload() {
        if (config.togglePrayerPotions()
                || config.overloadPotionAmount() <= 0
                || !Rs2Inventory.hasItem("overload")) {
            maintenanceOverloadPending = false;
            return false;
        }

        long now = System.currentTimeMillis();
        int hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        boolean trackedExpiryApproaching = nextOverloadExpiryAt > 0
                && now >= nextOverloadExpiryAt - OVERLOAD_READY_LEAD_MS;
        boolean untrackedOverloadDue = nextOverloadExpiryAt == 0 && useOverload && hitpoints > 50;
        if (!maintenanceOverloadPending && !trackedExpiryApproaching && !untrackedOverloadDue) return false;

        maintenanceOverloadPending = true;
        updateOverlayState("Re-overload pending", "Hold Inventory and wait for HP restoration");
        if (!switchToTab(InterfaceTab.INVENTORY, "maintenance overload readiness")) return true;

        long readinessDeadline = Math.max(now + 3000,
                nextOverloadExpiryAt > 0 ? nextOverloadExpiryAt + 8000 : now + 8000);
        while (Microbot.isLoggedIn()
                && !Thread.currentThread().isInterrupted()
                && !isOutside()
                && System.currentTimeMillis() < readinessDeadline) {
            hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            boolean statsReset = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED)
                    <= Microbot.getClient().getRealSkillLevel(Skill.RANGED);
            boolean expectedExpiryReached = nextOverloadExpiryAt == 0
                    || System.currentTimeMillis() >= nextOverloadExpiryAt;
            if (hitpoints > 50 && (statsReset || expectedExpiryReached)) break;
            sleep(100);
        }

        hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (hitpoints <= 50) {
            logMaintenanceOverload("waiting for HP above 50; current hp=" + hitpoints);
            return true;
        }
        if (System.currentTimeMillis() < nextMaintenanceOverloadAttemptAt) return true;

        int hitpointsBefore = hitpoints;
        int rangedBefore = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED);
        long overloadClickedAt = System.currentTimeMillis();
        updateOverlayAction("Drink maintenance overload", "Verify dose and damage", 5000);
        if (!Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("overload"), "drink")) {
            nextMaintenanceOverloadAttemptAt = System.currentTimeMillis() + 2000;
            logMaintenanceOverload("drink interaction not acknowledged; retrying");
            return true;
        }

        sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < hitpointsBefore
                || Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) > rangedBefore, 3500);
        int currentHitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        boolean acknowledged = currentHitpoints < hitpointsBefore
                || Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) > rangedBefore;
        if (!acknowledged) {
            nextMaintenanceOverloadAttemptAt = System.currentTimeMillis() + 2000;
            logMaintenanceOverload("dose produced no HP or stat change; retrying");
            return true;
        }

        recordOverloadDose(overloadClickedAt, "maintenance");
        waitForOverloadDamageToStop(hitpointsBefore, currentHitpoints);
        maintenanceOverloadPending = false;
        switchToTab(InterfaceTab.PRAYER, "post-maintenance overload readiness");
        return true;
    }

    private boolean isMaintenanceOverloadWindow() {
        return maintenanceOverloadPending
                || (nextOverloadExpiryAt > 0
                && System.currentTimeMillis() >= nextOverloadExpiryAt - OVERLOAD_READY_LEAD_MS);
    }

    private void recordOverloadDose(long consumedAt, String context) {
        nextOverloadExpiryAt = consumedAt + OVERLOAD_DURATION_MS;
        nextMaintenanceOverloadAttemptAt = 0;
        nextMaintenanceOverloadLogAt = 0;
        Microbot.log("NMZ overload: " + context + " dose acknowledged; next expiry in 300s");
    }

    private void waitForOverloadDamageToStop(int hitpointsBeforeDose, int startingHitpoints) {
        updateOverlayAction("Maintenance overload damage", "Wait for damage ticks to stop", 12000);
        long deadline = System.currentTimeMillis() + 12000;
        int lastHitpoints = startingHitpoints;
        boolean damageStarted = startingHitpoints < hitpointsBeforeDose;
        long lastDamageAt = damageStarted ? System.currentTimeMillis() : 0;
        while (Microbot.isLoggedIn()
                && !Thread.currentThread().isInterrupted()
                && !isOutside()
                && System.currentTimeMillis() < deadline) {
            sleep(200);
            int observedHitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (observedHitpoints < lastHitpoints) {
                lastHitpoints = observedHitpoints;
                damageStarted = true;
                lastDamageAt = System.currentTimeMillis();
            }
            if (damageStarted && System.currentTimeMillis() - lastDamageAt >= 2400) break;
        }
        if (!damageStarted) {
            Microbot.log("NMZ overload: no maintenance damage observed before guard timeout; hp=" + lastHitpoints);
        }
        Microbot.log("NMZ overload: maintenance damage complete at hp=" + lastHitpoints);
    }

    private void logMaintenanceOverload(String message) {
        long now = System.currentTimeMillis();
        if (now < nextMaintenanceOverloadLogAt) return;
        Microbot.log("NMZ overload: " + message);
        nextMaintenanceOverloadLogAt = now + 10000;
    }

    private void prepareInitialRockCake() {
        int hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        updateOverlayState("Initial preparation - rock cake", "Guzzle continuously from " + hitpoints + " HP to 1 HP");
        if (hitpoints <= 1) {
            finishRumblePreparation();
            return;
        }
        if (!Rs2Inventory.hasItem(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE)) {
            Microbot.log("NMZ preparation: rock cake missing at hp=" + hitpoints);
            updateOverlayState("Initial preparation blocked", "Rock cake required to reach 1 HP");
            return;
        }
        if (!switchToTab(InterfaceTab.INVENTORY, "initial rock cake")) return;

        int attempts = 0;
        long deadline = System.currentTimeMillis() + 90000;
        while (Microbot.isLoggedIn()
                && !Thread.currentThread().isInterrupted()
                && !isOutside()
                && hitpoints > 1
                && attempts++ < 80
                && System.currentTimeMillis() < deadline) {
            int previousHitpoints = hitpoints;
            if (!Rs2Inventory.interact(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE, "guzzle")) break;
            sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < previousHitpoints, 1800);
            hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (hitpoints >= previousHitpoints) break;
        }

        Microbot.log("NMZ preparation: initial rock cake hp=" + hitpoints + " attempts=" + attempts);
        if (hitpoints <= 1) finishRumblePreparation();
    }

    private void finishRumblePreparation() {
        rumblePreparationPhase = RumblePreparationPhase.COMPLETE;
        maxHealth = Rs2Random.between(2, 4);
        minAbsorption = Rs2Random.between(100, 300);
        updateOverlayAction("Initial preparation complete", "Begin normal NMZ maintenance", 2000);
        Microbot.log("NMZ preparation: complete at hp="
                + Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS)
                + " absorption=" + Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS));
        switchToTab(InterfaceTab.PRAYER, "initial preparation complete");
        Rs2Antiban.moveMouseOffScreen(80);
    }

    public boolean interactWithObject(int objectId) {
        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj != null) {
            overlayPowerUp = obj.getName() == null ? String.valueOf(objectId) : obj.getName();
            updateOverlayAction("Activate " + overlayPowerUp, "Confirm power-up despawn", 4000);
            WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
            if (playerLoc != null && playerLoc.distanceTo(obj.getWorldLocation()) >= 15) {
                Rs2Walker.walkFastLocal(obj.getLocalLocation());
                sleepUntil(() -> {
                    WorldPoint loc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
                    return loc != null && loc.distanceTo(obj.getWorldLocation()) < 15;
                }, 10000);
            }
            if (!obj.click("Activate")) {
                Microbot.log("NMZ power-up: could not activate object " + objectId);
                return false;
            }
            // The despawn is the only common acknowledgement for all three power-ups.
            boolean acknowledged = sleepUntil(() -> tileObjectCache.query().withId(objectId).nearest() == null, 3000);
            Microbot.log("NMZ power-up: object " + objectId + (acknowledged ? " activated" : " activation timed out"));
            overlayPowerUp += acknowledged ? " (activated)" : " (timed out)";
            if (acknowledged) Rs2Antiban.actionCooldown();
            return acknowledged;
        }
        return false;
    }

    private void useManualSpecialAfterSurge() {
        if (specialAwaitingConsumption) {
            if (Rs2Combat.getSpecState() && hasSurge) return;
            if (Rs2Combat.getSpecState()) Rs2Combat.setSpecState(false);
            restoreMainEquipment();
            specialAwaitingConsumption = false;
            specialAttemptedForCurrentSurge = true;
        }
        if (!hasSurge) {
            specialAttemptedForCurrentSurge = false;
            specialActionInFlight = false;
            nextSpecialAttemptAt = 0;
            mainWeaponBeforeSpecial = null;
            offhandBeforeSpecial = null;
            overlaySpecial = "Waiting for Power Surge";
            return;
        }
        if (specialAttemptedForCurrentSurge || specialActionInFlight || prayerPotionScript.isActionInFlight()
                || System.currentTimeMillis() < nextSpecialAttemptAt) return;
        if (!config.useAncientMace()
                && (!config.useSpecWeapon() || config.specWeapon() == null || config.specWeapon().trim().isEmpty())) return;
        SpecialAttackWeaponEnum weapon = resolveConfiguredSpecialWeapon();
        if (weapon == null) {
            specialAttemptedForCurrentSurge = true;
            return;
        }
        if (!Rs2Inventory.hasItem(weapon.getName()) && !Rs2Equipment.isWearing(weapon.getName())) {
            Microbot.log("NMZ special: configured weapon unavailable: " + weapon.getName());
            specialAttemptedForCurrentSurge = true;
            return;
        }
        int specialEnergy = Rs2Combat.getSpecEnergy();
        if (specialEnergy < 1000) {
            overlaySpecial = weapon.getName() + " waiting at " + (specialEnergy / 10) + "%";
            return;
        }

        specialActionInFlight = true;
        snapshotMainEquipment();
        if (!switchToTab(InterfaceTab.INVENTORY, "special weapon")) {
            specialActionInFlight = false;
            nextSpecialAttemptAt = System.currentTimeMillis() + 3000;
            return;
        }
        if (!Rs2Equipment.isWearing(weapon.getName())) {
            overlaySpecial = "Equipping " + weapon.getName();
            updateOverlayAction("Equip " + weapon.getName(), "Verify equipment", 3500);
            Microbot.log("NMZ special: equipping " + weapon.getName() + " at 100% energy");
            boolean equipRequested = Rs2Inventory.wear(weapon.getName());
            boolean equipped = equipRequested && sleepUntil(() -> Rs2Equipment.isWearing(weapon.getName()), 3000);
            if (!equipped) {
                Microbot.log("NMZ special: equip not acknowledged for " + weapon.getName());
                specialActionInFlight = false;
                nextSpecialAttemptAt = System.currentTimeMillis() + 3000;
                return;
            }
        }

        if (!switchToTab(InterfaceTab.COMBAT, "special attack")) {
            specialActionInFlight = false;
            nextSpecialAttemptAt = System.currentTimeMillis() + 3000;
            return;
        }
        boolean armed = Rs2Combat.setSpecState(true, 1000);
        if (armed) {
            specialAwaitingConsumption = true;
            overlaySpecial = weapon.getName() + " armed; awaiting attack";
            updateOverlayAction("Arm " + weapon.getName() + " special", "Wait for special attack", 3000);
            Microbot.log("NMZ special: armed " + weapon.getName() + " once at 100% energy");
        } else {
            Microbot.log("NMZ special: could not arm " + weapon.getName() + "; delaying retry");
            nextSpecialAttemptAt = System.currentTimeMillis() + 3000;
        }
        specialActionInFlight = false;
    }

    private void snapshotMainEquipment() {
        if (mainWeaponBeforeSpecial != null || offhandBeforeSpecial != null) return;
        mainWeaponBeforeSpecial = configuredOrEquipped(config.mainWeapon(), EquipmentInventorySlot.WEAPON);
        offhandBeforeSpecial = configuredOrEquipped(config.offhand(), EquipmentInventorySlot.SHIELD);
        Microbot.log("NMZ special: restore target main=" + valueOrNone(mainWeaponBeforeSpecial)
                + " offhand=" + valueOrNone(offhandBeforeSpecial));
    }

    private String configuredOrEquipped(String configured, EquipmentInventorySlot slot) {
        if (configured != null && !configured.trim().isEmpty()) return configured.trim();
        Rs2ItemModel equipped = Rs2Equipment.get(slot);
        return equipped == null ? null : equipped.getName();
    }

    private void restoreMainEquipment() {
        overlaySpecial = "Restoring combat equipment";
        updateOverlayAction("Restore main equipment", "Verify weapon and off-hand", 4000);
        switchToTab(InterfaceTab.INVENTORY, "restore equipment");
        boolean mainRestored = restoreEquipmentItem(mainWeaponBeforeSpecial);
        boolean offhandRestored = restoreEquipmentItem(offhandBeforeSpecial);
        Microbot.log("NMZ special: restore " + (mainRestored && offhandRestored ? "acknowledged" : "incomplete")
                + " main=" + valueOrNone(mainWeaponBeforeSpecial) + " offhand=" + valueOrNone(offhandBeforeSpecial));
        switchToTab(InterfaceTab.PRAYER, "post-special prayer readiness");
        overlaySpecial = mainRestored && offhandRestored ? "Combat equipment restored" : "Equipment restore incomplete";
    }

    private boolean restoreEquipmentItem(String itemName) {
        if (itemName == null || itemName.isEmpty() || Rs2Equipment.isWearing(itemName)) return true;
        return Rs2Inventory.hasItem(itemName)
                && Rs2Inventory.wear(itemName)
                && sleepUntil(() -> Rs2Equipment.isWearing(itemName), 3000);
    }

    private String valueOrNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private boolean switchToTab(InterfaceTab tab, String reason) {
        boolean switched = Rs2Tab.switchTo(tab);
        if (!switched) Microbot.log("NMZ tab: failed to open " + tab.getName() + " for " + reason);
        return switched;
    }

    private SpecialAttackWeaponEnum resolveConfiguredSpecialWeapon() {
        if (config.useAncientMace()) return SpecialAttackWeaponEnum.ANCIENT_MACE;
        return findSpecialWeapon(config.specWeapon());
    }

    private SpecialAttackWeaponEnum findSpecialWeapon(String configuredName) {
        if (configuredName == null || configuredName.trim().isEmpty()) return null;
        return Arrays.stream(SpecialAttackWeaponEnum.values())
                .filter(weapon -> weapon.getName().equalsIgnoreCase(configuredName.trim()))
                .findFirst()
                .orElseGet(() -> {
                    Microbot.log("NMZ special: unsupported weapon '" + configuredName + "'");
                    return null;
                });
    }

    private void fetchOverloadPotions(int objectId, String itemName, int requiredAmount) {
        int currentAmount = Rs2Inventory.count(itemName);

        if (currentAmount == requiredAmount) return;

        int neededAmount = requiredAmount - currentAmount;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));

        if (Rs2Widget.hasWidget(widgetText)) {
            // Each potion has 4 doses, so request the correct number of doses
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString(Integer.toString(neededAmount * 4));
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }


    public void manageSelfHarm() {
        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentRangedLevel = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED);
        int realRangedLevel = Microbot.getClient().getRealSkillLevel(Skill.RANGED);
        boolean hasOverloadPotions = config.overloadPotionAmount() > 0;

        if (currentHP >= maxHealth
                && !useOverload
                && (!hasOverloadPotions || currentRangedLevel != realRangedLevel)) {
            maxHealth = 1;

            if (Rs2Inventory.hasItem(ItemID.DS2_ORB)) {
                updateOverlayAction("Use locator orb", "Check hitpoints", 1500);
                switchToTab(InterfaceTab.INVENTORY, "locator orb");
                Rs2Inventory.interact(ItemID.DS2_ORB, "feel");
                Rs2Antiban.actionCooldown();
            } else if (Rs2Inventory.hasItem(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE)) {
                updateOverlayAction("Guzzle rock cake", "Return to Prayer tab", 1500);
                switchToTab(InterfaceTab.INVENTORY, "rock cake");
                Rs2Inventory.interact(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE, "guzzle");
                Rs2Antiban.actionCooldown();
            }

            returnToPrayerTabIfSetupComplete("post-self-harm rapid heal readiness");
            Rs2Antiban.moveMouseOffScreen(80);

            if (currentHP == 1) {
                maxHealth = Rs2Random.between(2, 4);
            }
        }

        if (config.randomlyTriggerRapidHeal() && !isInitialInventorySetupPending()) {
            randomlyToggleRapidHeal();
        }
    }

    public void randomlyToggleRapidHeal() {
        if (Rs2Random.between(1, 27) == 2) {
            int hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
            Microbot.log("NMZ rapid heal: trigger at hp=" + hitpoints + " prayer=" + prayer);
            updateOverlayAction("Click Rapid Heal on/off", "Check hitpoints and rock cake", 2000);
            boolean prayerTabOpen = switchToTab(InterfaceTab.PRAYER, "rapid heal");
            boolean enabled = prayerTabOpen && Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, true, true);
            sleep(300, 600);
            boolean disabled = enabled && Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, false, true);
            Microbot.log("NMZ rapid heal: " + (enabled && disabled ? "cycle acknowledged" : "cycle not acknowledged"));
            if (enabled && disabled) {
                rockCakeToOneAfterRapidHeal();
            }
            switchToTab(InterfaceTab.PRAYER, "post-rapid-heal readiness");
            Rs2Antiban.moveMouseOffScreen(80);
        }
    }

    private void rockCakeToOneAfterRapidHeal() {
        int hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (hitpoints <= 1 || !Rs2Inventory.hasItem(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE)) return;
        if (!switchToTab(InterfaceTab.INVENTORY, "post-rapid-heal rock cake")) return;

        updateOverlayAction("Rock cake back to 1 HP", "Return to Prayer tab", 2500);
        int attempts = 0;
        while (hitpoints > 1 && attempts++ < 10) {
            int previousHitpoints = hitpoints;
            if (!Rs2Inventory.interact(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE, "guzzle")) break;
            sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < previousHitpoints, 1500);
            hitpoints = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        }
        Microbot.log("NMZ rapid heal: post-cycle rock cake hp=" + hitpoints + " attempts=" + attempts);
    }

    public void useAbsorptionPotion() {
        if (Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS) < minAbsorption && Rs2Inventory.hasItem("absorption")) {
            updateOverlayAction("Drink absorption potions", "Reach absorption target", 6000);
            if (!switchToTab(InterfaceTab.INVENTORY, "absorption potion")) return;
            for (int i = 0; i < Rs2Random.between(4, 8); i++) {
                Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("absorption"), "drink");
                sleep(600, 1000);
            }
            minAbsorption = Rs2Random.between(100, 300);
            returnToPrayerTabIfSetupComplete("post-absorption prayer readiness");
        }
    }

    private boolean isInitialInventorySetupPending() {
        return !config.togglePrayerPotions()
                && rumblePreparationPhase != RumblePreparationPhase.COMPLETE;
    }

    private void returnToPrayerTabIfSetupComplete(String reason) {
        if (isInitialInventorySetupPending()) {
            updateOverlayState("Initial inventory setup", "Finish overload, absorption and self-damage");
            return;
        }
        switchToTab(InterfaceTab.PRAYER, reason);
    }

    private void updateOverlayState(String state, String nextAction) {
        if (System.currentTimeMillis() < overlayStateHoldUntil) return;
        if (!state.equals(overlayState) || !nextAction.equals(overlayNextAction)) {
            overlayState = state;
            overlayNextAction = nextAction;
            overlayStateChangedAt = System.currentTimeMillis();
        }
    }

    private void updateOverlayIdle(String nextAction) {
        updateOverlayState("Idle - waiting for next action", nextAction);
    }

    private void updateOverlayAction(String action, String nextAction, long holdMillis) {
        overlayActionGeneration++;
        overlayLastAction = action;
        overlayState = action;
        overlayNextAction = nextAction;
        overlayStateChangedAt = System.currentTimeMillis();
        overlayStateHoldUntil = System.currentTimeMillis() + holdMillis;
    }

    private void storePotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;
        if (Rs2Inventory.get(itemName) == null) return;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Store");
        String storeWidgetText = "Store all your ";
        sleepUntil(() -> Rs2Widget.hasWidget(storeWidgetText));
        if (Rs2Widget.hasWidget(storeWidgetText)) {
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString("1");
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> !Rs2Inventory.hasItem(objectId));
            Rs2Inventory.dropAll(itemName);
        }
    }

    private void fetchPotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));
        if (Rs2Widget.hasWidget(widgetText)) {
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString(Integer.toString(requiredAmount * 4));
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }

    public void consumeEmptyVial() {
        boolean confirmationHidden = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget confirmation = Rs2Widget.getWidget(129, 6);
            return confirmation == null || confirmation.isHidden();
        }).orElse(true);
        if (confirmationHidden) {
            Rs2TileObjectModel vial = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_VIAL).nearest();
            if (vial == null || !vial.click("drink")) {
                updateOverlayState("NMZ lobby", "Wait for dream potion to become drinkable");
                logLobbyVialRetry("drink action unavailable; retrying after interface settles");
                sleep(2000, 4000);
                return;
            }
            nextLobbyVialLogAt = 0;
        }
        sleep(2000, 4000);
        Widget widget = Rs2Widget.getWidget(129, 6);
        if (widget == null) {
            updateOverlayState("NMZ lobby", "Wait for dream confirmation");
            logLobbyVialRetry("confirmation widget not available; retrying");
            return;
        }
        if (!Microbot.getClientThread().runOnClientThreadOptional(widget::isHidden).orElse(true)) {
            Rs2Widget.clickWidget(widget.getId());
            sleep(300);
            Rs2Widget.clickWidget(widget.getId());
        }
        sleep(2000, 4000);
    }

    private void logLobbyVialRetry(String message) {
        long now = System.currentTimeMillis();
        if (now < nextLobbyVialLogAt) return;
        Microbot.log("NMZ lobby vial: " + message);
        nextLobbyVialLogAt = now + 10000;
    }

    public void handleStore() {
        if (canStartNmz()) return;
        int overloadAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_3);
        int absorptionAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_4);

        // Varbits are in doses; config is in 4-dose potions. Keep the reserve at
        // approximately one overload for every three absorptions so a restock
        // cannot consume all available points on overloads alone.
        int overloadTarget = config.overloadPotionAmount();
        int absorptionTarget = Math.max(config.absorptionPotionAmount(), overloadTarget * 3);
        int overloadDosesNeeded = Math.max(0, overloadTarget * 4 - overloadAmt);
        int absorptionDosesNeeded = Math.max(0, absorptionTarget * 4 - absorptionAmt);

        if (overloadDosesNeeded == 0 && absorptionDosesNeeded == 0) return;

        // Each shop purchase gives one 4-dose potion (ceiling division)
        int overloadToBuy = (overloadDosesNeeded + 3) / 4;
        int absorptionToBuy = (absorptionDosesNeeded + 3) / 4;

        // NMZ reward shop costs: Overload 1,500 pts / Absorption 1,000 pts per 4-dose potion
        int totalCost = overloadToBuy * 1500 + absorptionToBuy * 1000;
        int nmzPoints = Microbot.getVarbitPlayerValue(VarPlayerID.NZONE_REWARDPOINTS);

        if (nmzPoints < totalCost) {
            Microbot.showMessage("BOT SHUTDOWN: Not enough points to buy potions (have " + nmzPoints + ", need " + totalCost + ")");
            Microbot.stopPlugin(plugin);
            return;
        }

        Rs2TileObjectModel chest = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_CHEST).nearest();
        if (chest == null) return;
        chest.click();
        sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418) || Rs2Bank.isBankPinWidgetVisible(), 10000);
        if (Rs2Bank.isBankPinWidgetVisible()) {
            try {
                Rs2Bank.handleBankPin(Encryption.decrypt(LoginManager.getActiveProfile().getBankPin()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418), 10000);
        }

        Widget benefitsBtn = Rs2Widget.getWidget(13500418);
        if (benefitsBtn == null) return;
        if (benefitsBtn.getSpriteId() != 813) {
            Rs2Widget.clickWidgetFast(benefitsBtn, 4, 4);
            sleepUntil(() -> {
                Widget btn = Rs2Widget.getWidget(13500418);
                return btn != null && btn.getSpriteId() == 813;
            }, 3000);
        }

        Microbot.log("NMZ restock: buying overload=" + overloadToBuy
                + " absorption=" + absorptionToBuy
                + " (target ratio 1:3, cost=" + totalCost + ")");

        // Buy absorptions first in each batch. If the interface closes or the
        // action is interrupted, the reserve cannot be left overload-heavy.
        while (overloadToBuy > 0 || absorptionToBuy > 0) {
            for (int i = 0; i < 3 && absorptionToBuy > 0; i++) {
                if (!buyRewardPotion(9, 9)) return;
                absorptionToBuy--;
            }
            if (overloadToBuy > 0) {
                if (!buyRewardPotion(6, 6)) return;
                overloadToBuy--;
            }
            if (overloadToBuy == 0) {
                while (absorptionToBuy > 0) {
                    if (!buyRewardPotion(9, 9)) return;
                    absorptionToBuy--;
                }
            }
        }
    }

    private boolean buyRewardPotion(int childIndex, int actionIndex) {
        Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
        if (nmzRewardShop == null || nmzRewardShop.getChild(childIndex) == null) return false;
        Rs2Widget.clickWidgetFast(nmzRewardShop.getChild(childIndex), actionIndex, 4);
        sleep(600, 1000);
        return true;
    }

}
