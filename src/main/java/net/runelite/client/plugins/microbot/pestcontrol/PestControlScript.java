package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.pestcontrol.Portal;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isQuickPrayerEnabled;
import static net.runelite.client.plugins.pestcontrol.Portal.*;

public class PestControlScript extends Script {

    boolean initialise = true;
    boolean openingSideReached = false;
    private boolean wasInPestControl = false;
    private boolean pendingPostRoundRestore = false;
    private boolean autoRetaliateConfirmedOff = false;
    private boolean autoRetaliateDisableLogged = false;
    private Portal selectedPortal = null;
    private Portal openingPortal = null;
    private String primaryWeaponName = null;
    private final Set<String> missingWeaponsLogged = new HashSet<>();
    private final Set<String> missingAttackOptionsLogged = new HashSet<>();
    private boolean missingPrimaryWeaponLogged = false;
    private String managedAttackOptionWeapon = null;
    private String managedAttackOptionName = null;
    private int managedAttackOptionIndex = -1;
    private long lastBoardingAttemptAt = 0L;
    private boolean boardingAttemptPending = false;
    PestControlConfig config;
    private final PestControlPlugin plugin;

    @Inject
    public PestControlScript(PestControlPlugin plugin, PestControlConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    private static final Set<Integer> SPINNER_IDS = ImmutableSet.of(
            NpcID.SPINNER,
            NpcID.SPINNER_1710,
            NpcID.SPINNER_1711,
            NpcID.SPINNER_1712,
            NpcID.SPINNER_1713
    );

    private static final int PORTAL_CROWD_RADIUS = 12;
    private static final int PORTAL_MATCH_RADIUS = 5;
    private static final int SPINNER_PORTAL_RADIUS = 8;
    private static final int PORTAL_SWITCH_CROWD_MARGIN = 1;
    private static final int PEST_CONTROL_CENTER_REGION_COORD = 32;
    private static final int SAFE_STAGING_OFFSET = 5;
    private static final int STAGING_ARRIVAL_DISTANCE = 2;
    private static final int RANGED_ENGAGEMENT_DISTANCE = 6;
    private static final int MELEE_ENGAGEMENT_DISTANCE = 1;
    private static final int MINIMAP_STEP_DISTANCE = 14;
    private static final int ACTIVITY_MAINTENANCE_PERCENT = 60;
    private static final long MOVEMENT_RETRY_MILLIS = 750L;
    private static final long ATTACK_RETRY_MILLIS = 600L;
    private static final long WATCHDOG_IDLE_MILLIS = 6_000L;
    private static final long WATCHDOG_LOG_INTERVAL_MILLIS = 6_000L;
    private static final long BOARDING_RETRY_MILLIS = 600L;
    private static final long BOARDING_CONFIRM_TIMEOUT_MILLIS = 3_000L;
    public static final boolean DEBUG = false;

    public static List<Portal> portals = List.of(PURPLE, BLUE, RED, YELLOW);

    private RuntimeState runtimeState = RuntimeState.STOPPED;
    private String runtimeDetail = "";
    private long stateEnteredAt = 0L;
    private long lastProgressAt = 0L;
    private long lastWatchdogLogAt = 0L;
    private long lastMovementCommandAt = 0L;
    private long lastAttackCommandAt = 0L;
    private WorldPoint lastProgressLocation = null;
    private boolean quickPrayerHandled = false;

    private enum RuntimeState {
        INITIALISING,
        TRAVELLING,
        REQUEUE,
        BOAT,
        OPENING_SIDE,
        PREPOSITION_PORTAL,
        WAITING_FOR_PORTAL,
        CHASE_PORTAL,
        KILL_SPINNER,
        ATTACK_PORTAL,
        ACTIVITY_FALLBACK,
        HOLDING_COMBAT,
        ERROR,
        STOPPED
    }

    private void resetPortals() {
        for (Portal portal : portals) {
            portal.setHasShield(true);
        }
    }

    private void transitionTo(RuntimeState state, String detail) {
        String normalizedDetail = detail == null ? "" : detail;
        if (runtimeState != state || !runtimeDetail.equals(normalizedDetail)) {
            runtimeState = state;
            runtimeDetail = normalizedDetail;
            stateEnteredAt = System.currentTimeMillis();
            lastWatchdogLogAt = 0L;
            lastAttackCommandAt = 0L;
            Microbot.log("Pest Control state: " + state
                    + (normalizedDetail.isEmpty() ? "" : " - " + normalizedDetail));
        }
        Microbot.status = state + (normalizedDetail.isEmpty() ? "" : ": " + normalizedDetail);
    }

    private void observeProgress(WorldPoint location) {
        if (location == null) {
            return;
        }
        if (lastProgressLocation == null || lastProgressLocation.distanceTo(location) >= 2) {
            lastProgressLocation = location;
            lastProgressAt = System.currentTimeMillis();
        }
    }

    private void runWatchdog(WorldPoint location) {
        observeProgress(location);
        if (runtimeState != RuntimeState.OPENING_SIDE
                && runtimeState != RuntimeState.PREPOSITION_PORTAL
                && runtimeState != RuntimeState.CHASE_PORTAL
                && runtimeState != RuntimeState.KILL_SPINNER
                && runtimeState != RuntimeState.ATTACK_PORTAL
                && runtimeState != RuntimeState.ACTIVITY_FALLBACK) {
            return;
        }
        if (Rs2Player.isMoving() || isPlayerInteracting()) {
            lastProgressAt = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        if (lastProgressAt == 0L) {
            lastProgressAt = now;
        }
        if (now - lastProgressAt < WATCHDOG_IDLE_MILLIS
                || now - lastWatchdogLogAt < WATCHDOG_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastWatchdogLogAt = now;
        Microbot.log("Pest Control watchdog recovery: state=" + runtimeState
                + (runtimeDetail.isEmpty() ? "" : " detail=" + runtimeDetail)
                + " idleMs=" + (now - lastProgressAt)
                + " stateMs=" + (now - stateEnteredAt));
        Rs2Walker.clearWalkingRoute("pest-control:watchdog-" + runtimeState.name().toLowerCase(Locale.ROOT));
        lastMovementCommandAt = 0L;
        lastAttackCommandAt = 0L;
        lastProgressAt = now;
    }

    private static boolean isPlayerInteracting() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getInteracting() != null;
        }).orElse(false);
    }

    private static WorldPoint stepTowards(WorldPoint from, WorldPoint to, int maxStep) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        if (chebyshev <= maxStep) {
            return to;
        }
        double scale = (double) maxStep / chebyshev;
        return new WorldPoint(
                from.getX() + (int) Math.round(dx * scale),
                from.getY() + (int) Math.round(dy * scale),
                from.getPlane());
    }

    private boolean moveToward(WorldPoint playerLocation, WorldPoint target, int arrivalDistance) {
        if (playerLocation == null || target == null || playerLocation.distanceTo(target) <= arrivalDistance) {
            return false;
        }

        observeProgress(playerLocation);
        long now = System.currentTimeMillis();
        if (!Rs2Player.isMoving() || now - lastMovementCommandAt >= MOVEMENT_RETRY_MILLIS) {
            WorldPoint clickTarget = stepTowards(playerLocation, target, MINIMAP_STEP_DISTANCE);
            if (Rs2Walker.walkMiniMap(clickTarget)) {
                lastMovementCommandAt = now;
            }
        }
        return true;
    }

    private boolean claimAttackCommand() {
        long now = System.currentTimeMillis();
        if (now - lastAttackCommandAt < ATTACK_RETRY_MILLIS) {
            return false;
        }
        lastAttackCommandAt = now;
        return true;
    }

    private boolean dispatchAttack(Rs2NpcModel target) {
        if (target == null || !claimAttackCommand()) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                target.getNpc() != null
                        && !target.getNpc().isDead()
                        && hasAttackAction(target)
                        && target.click("Attack")
        ).orElse(false);
    }

    private boolean dispatchPortalAttack(Rs2NpcModel target) {
        if (target == null || !claimAttackCommand()) {
            return false;
        }
        return attackPortal(target);
    }

    private Portal chooseOpeningPortal() {
        int rangedWeight = Math.max(0, Math.min(100, config.rangedOpeningWeight()));
        if (Rs2Random.between(0, 100) < rangedWeight) {
            return PURPLE;
        }

        // Red can never be the first portal to become vulnerable.
        Portal[] otherPortals = {BLUE, YELLOW};
        return otherPortals[Rs2Random.between(0, otherPortals.length)];
    }

    private boolean moveToOpeningSide() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || openingPortal == null) {
            return false;
        }

        WorldPoint target = safeRangedPortalLocation(openingPortal, playerLocation);
        transitionTo(RuntimeState.OPENING_SIDE, openingPortal + " portal");
        if (playerLocation.distanceTo(target) <= STAGING_ARRIVAL_DISTANCE) {
            openingSideReached = true;
            Microbot.log("Pest Control staged safely behind " + openingPortal + " portal");
            return false;
        }

        return moveToward(playerLocation, target, STAGING_ARRIVAL_DISTANCE);
    }

    private boolean confirmAutoRetaliateOff() {
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1) {
            if (!autoRetaliateConfirmedOff) {
                Microbot.log("Pest Control confirmed Auto Retaliate is OFF");
            }
            autoRetaliateConfirmedOff = true;
            return true;
        }

        if (!autoRetaliateDisableLogged) {
            Microbot.log("Pest Control disabling Auto Retaliate");
            autoRetaliateDisableLogged = true;
        }

        autoRetaliateConfirmedOff = Rs2Combat.setAutoRetaliate(false)
                && Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1;
        if (autoRetaliateConfirmedOff) {
            Microbot.log("Pest Control confirmed Auto Retaliate is OFF");
        }
        return autoRetaliateConfirmedOff;
    }

    public boolean run(PestControlConfig config) {
        this.config = config;
        resetPortals();
        selectedPortal = null;
        openingPortal = null;
        openingSideReached = false;
        pendingPostRoundRestore = false;
        autoRetaliateConfirmedOff = false;
        autoRetaliateDisableLogged = false;
        primaryWeaponName = configuredPrimaryWeaponName();
        missingWeaponsLogged.clear();
        missingAttackOptionsLogged.clear();
        missingPrimaryWeaponLogged = false;
        managedAttackOptionWeapon = null;
        managedAttackOptionName = null;
        managedAttackOptionIndex = -1;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        runtimeState = RuntimeState.STOPPED;
        runtimeDetail = "";
        stateEnteredAt = 0L;
        lastProgressAt = System.currentTimeMillis();
        lastWatchdogLogAt = 0L;
        lastMovementCommandAt = 0L;
        lastAttackCommandAt = 0L;
        lastProgressLocation = null;
        quickPrayerHandled = false;
        transitionTo(RuntimeState.INITIALISING, "starting script");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    transitionTo(RuntimeState.INITIALISING, "waiting for login");
                    return;
                }
                if (!super.run()) return;
                if (!confirmAutoRetaliateOff()) {
                    transitionTo(RuntimeState.INITIALISING, "disabling Auto Retaliate");
                    return;
                }

                final boolean isInPestControl = isInPestControl();
                final boolean isInBoat = isInBoat();
                if (isInPestControl) {
                    handleRoundTick();
                } else {
                    handleLobbyTick(isInBoat);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                transitionTo(RuntimeState.ERROR,
                        ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleRoundTick() {
        initialise = false;
        if (!wasInPestControl) {
            autoRetaliateConfirmedOff = false;
            autoRetaliateDisableLogged = false;
            openingPortal = chooseOpeningPortal();
            openingSideReached = false;
            selectedPortal = null;
            quickPrayerHandled = false;
            lastProgressLocation = null;
            lastProgressAt = System.currentTimeMillis();
            lastMovementCommandAt = 0L;
            lastAttackCommandAt = 0L;
            Microbot.log("Pest Control opening side: " + openingPortal + " portal");
        }

        wasInPestControl = true;
        if (!confirmAutoRetaliateOff()) {
            transitionTo(RuntimeState.INITIALISING, "confirming Auto Retaliate OFF");
            return;
        }

        pendingPostRoundRestore = false;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        handleQuickPrayerOnce();
        activateSpecialAttackIfReady();

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        PortalTarget portalTarget = selectAdaptivePortalTarget();
        if (portalTarget != null) {
            openingSideReached = true;
            handlePortalTarget(portalTarget, playerLocation);
            runWatchdog(playerLocation);
            return;
        }

        // Portal switches are temporary. As soon as no portal is attackable,
        // return to the configured primary weapon/style before doing anything else.
        restorePrimaryWeapon();
        applyPrimaryAttackMode();

        if (!openingSideReached && moveToOpeningSide()) {
            runWatchdog(playerLocation);
            return;
        }

        int activityPercent = getActivityPercent();
        Portal lastShieldedPortal = soleShieldedPortal();
        boolean activityNeedsMaintenance = activityPercent >= 0
                && activityPercent <= ACTIVITY_MAINTENANCE_PERCENT;
        if (lastShieldedPortal != null
                && !activityNeedsMaintenance
                && moveToLastShieldedPortal(lastShieldedPortal, playerLocation)) {
            runWatchdog(playerLocation);
            return;
        }

        if (attackSpinner()) {
            runWatchdog(playerLocation);
            return;
        }

        if (activityNeedsMaintenance) {
            if (isPlayerInteracting()) {
                transitionTo(RuntimeState.ACTIVITY_FALLBACK, "maintaining activity");
                runWatchdog(playerLocation);
                return;
            }
            Rs2NpcModel attackableNpc = nearestAttackablePest();
            maintainActivityWith(attackableNpc, playerLocation);
            runWatchdog(playerLocation);
            return;
        }

        if (isPlayerInteracting()) {
            transitionTo(RuntimeState.HOLDING_COMBAT, "finishing current fallback target");
            return;
        }

        if (lastShieldedPortal != null) {
            transitionTo(RuntimeState.WAITING_FOR_PORTAL,
                    "pre-positioned for " + lastShieldedPortal + " shield drop");
            return;
        }

        transitionTo(RuntimeState.WAITING_FOR_PORTAL, "staged near " + openingPortal);
    }

    private Portal soleShieldedPortal() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Portal> shieldedPortals = portals.stream()
                    .filter(portal -> portal.hasShield)
                    .collect(Collectors.toList());
            return shieldedPortals.size() == 1 ? shieldedPortals.get(0) : null;
        }).orElse(null);
    }

    private boolean moveToLastShieldedPortal(Portal portal, WorldPoint playerLocation) {
        if (portal == null || playerLocation == null) {
            return false;
        }

        WorldPoint target = safeRangedPortalLocation(portal, playerLocation);
        if (playerLocation.distanceTo(target) <= STAGING_ARRIVAL_DISTANCE) {
            return false;
        }

        transitionTo(RuntimeState.PREPOSITION_PORTAL, portal + " shield pending");
        return moveToward(playerLocation, target, STAGING_ARRIVAL_DISTANCE);
    }

    private void handleLobbyTick(boolean isInBoat) {
        if (wasInPestControl) {
            Rs2Walker.clearWalkingRoute("pest-control:round-ended");
            wasInPestControl = false;
            pendingPostRoundRestore = true;
            selectedPortal = null;
            openingPortal = null;
            openingSideReached = false;
            quickPrayerHandled = false;
            lastMovementCommandAt = 0L;
            lastAttackCommandAt = 0L;
            boardingAttemptPending = false;
            resetPortals();
            Microbot.log("Pest Control round ended; reboarding immediately");
        }

        if (initialise && !isInBoat) {
            transitionTo(RuntimeState.INITIALISING, "checking Pest Control island");
            if (Rs2Player.getWorld() != config.world()) {
                Microbot.hopToWorld(config.world());
                sleepUntil(() -> Rs2Player.getWorld() == config.world(), 7000);
            }

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation != null
                    && playerLocation.getRegionID() == 10537
                    && Rs2Player.getWorld() == config.world()) {
                initialise = false;
            } else {
                transitionTo(RuntimeState.TRAVELLING, "Pest Control island");
                Rs2Walker.walkTo(new WorldPoint(2667, 2653, 0));
                return;
            }
        }

        if (!isInBoat && !initialise) {
            transitionTo(RuntimeState.REQUEUE,
                    boardingAttemptPending ? "waiting for boat entry" : "boarding now");
            boardBoat();
            return;
        }

        resetPortals();
        openingSideReached = false;
        if (isInBoat) {
            transitionTo(RuntimeState.BOAT, "waiting for launch");
            lastBoardingAttemptAt = 0L;
            boardingAttemptPending = false;
            if (pendingPostRoundRestore) {
                restorePrimaryWeapon();
                applyPrimaryAttackMode();
                pendingPostRoundRestore = false;
            }
            if (config.alchInBoat() && !config.alchItem().equalsIgnoreCase("")) {
                Rs2Magic.alch(config.alchItem());
                sleep(Rs2Random.between(1600, 1800));
            }
        }
    }

    private void handleQuickPrayerOnce() {
        if (quickPrayerHandled) {
            return;
        }
        quickPrayerHandled = true;
        int prayerLevel = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER)).orElse(0);
        if (isQuickPrayerEnabled()
                || prayerLevel == 0
                || !config.quickPrayer()) {
            return;
        }

        Rs2Widget.clickWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
    }

    private int getActivityPercent() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(
                    InterfaceID.PestStatusOverlay.ACTIVITY_CONTAINER);
            Widget progress = Microbot.getClient().getWidget(
                    InterfaceID.PestStatusOverlay.ACTIVITY_BAR);
            Widget containerBar = container == null ? null : container.getChild(0);
            Widget progressBar = progress == null ? null : progress.getChild(0);
            if (containerBar == null || progressBar == null || containerBar.getWidth() <= 0) {
                return -1;
            }
            return Math.max(0, Math.min(100, (int) Math.round(
                    100.0 * progressBar.getWidth() / containerBar.getWidth())));
        }).orElse(-1);
    }

    private Rs2NpcModel nearestAttackablePest() {
        return Microbot.getClientThread().invoke(() ->
                Microbot.getRs2NpcCache().query()
                        .where(n -> n.getNpc() != null
                                && !n.getNpc().isDead()
                                && !"Brawler".equalsIgnoreCase(n.getNpc().getName())
                                && n.getNpc().getCombatLevel() > 0
                                && hasAttackAction(n))
                        .nearest());
    }

    private void maintainActivityWith(Rs2NpcModel target, WorldPoint playerLocation) {
        WorldPoint targetLocation = Microbot.getClientThread().runOnClientThreadOptional(() ->
                target == null || target.getNpc() == null || target.getNpc().isDead()
                        ? null
                        : target.getWorldLocation()
        ).orElse(null);
        if (targetLocation == null) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "no attackable pest nearby");
            return;
        }

        int engagementDistance = engagementDistance(config.primaryCombatStyle());
        if (playerLocation == null
                || playerLocation.distanceTo(targetLocation) > engagementDistance) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "approaching activity target");
            moveToward(playerLocation, targetLocation, engagementDistance);
            return;
        }

        transitionTo(RuntimeState.ACTIVITY_FALLBACK, "attacking activity target");
        dispatchAttack(target);
    }

    private boolean boardBoat() {
        long now = System.currentTimeMillis();
        if (boardingAttemptPending) {
            if (now - lastBoardingAttemptAt < BOARDING_CONFIRM_TIMEOUT_MILLIS) {
                return false;
            }
            boardingAttemptPending = false;
            Microbot.log("Pest Control boat entry was not observed; retrying gangplank");
        }
        if (now - lastBoardingAttemptAt < BOARDING_RETRY_MILLIS) {
            return false;
        }
        lastBoardingAttemptAt = now;

        int combatLevel = getCombatLevel();
        int gangplankId = combatLevel >= 100
                ? ObjectID.GANGPLANK_25632
                : combatLevel >= 70
                ? ObjectID.GANGPLANK_25631
                : ObjectID.GANGPLANK_14315;
        boolean dispatched = Microbot.getRs2TileObjectCache().query().interact(gangplankId);
        if (dispatched) {
            boardingAttemptPending = true;
        }
        return dispatched;
    }

    public boolean isOutside() {
        WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        return playerLoc != null && playerLoc.distanceTo(new WorldPoint(2644, 2644, 0)) < 20;
    }

    public boolean isInBoat() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) != null
        ).orElse(false);
    }

    public boolean isInPestControl() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BLUE_SHIELD) != null
        ).orElse(false);
    }

    public void exitBoat() {
        int combatLevel = getCombatLevel();
        if (combatLevel >= 100) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25630);
        } else if (combatLevel >= 70) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25629);
        } else {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_14314);
        }
        sleepUntil(() -> !isInBoat(), 3000);

    }

    private static int getCombatLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? 0 : player.getCombatLevel();
        }).orElse(0);
    }

    private static boolean attackPortal(Rs2NpcModel npcPortal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (npcPortal == null || npcPortal.getNpc() == null || npcPortal.getNpc().isDead()) {
                return false;
            }
            NPCComposition npc = Microbot.getClient().getNpcDefinition(npcPortal.getId());
            if (npc == null) {
                return false;
            }

            String[] actions = npc.getActions();
            if (actions != null
                    && Arrays.stream(actions).anyMatch(x -> x != null && x.equalsIgnoreCase("attack"))) {
                // Rs2NpcModel.click uses the actor's LocalPoint for camera rotation, so it
                // remains valid inside the Pest Control instance and can preempt a pest.
                return npcPortal.click("Attack");
            }
            return false;
        }).orElse(false);
    }


    private void handlePortalTarget(PortalTarget target, WorldPoint playerLocation) {
        if (selectedPortal != target.portal) {
            selectedPortal = target.portal;
            Microbot.log("Pest Control target: " + target.portal
                    + " portal (" + target.nearbyPlayers + " other players nearby)");
        }

        if (playerLocation == null) {
            transitionTo(RuntimeState.ERROR, "player location unavailable");
            return;
        }
        prepareWeaponForPortal(target.portal);

        int engagementDistance = engagementDistanceForPortal(target.portal);
        WorldPoint engagementLocation = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? safeRangedPortalLocation(target.portal, playerLocation)
                : logicalPortalLocation(target.portal, playerLocation);
        int arrivalDistance = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? STAGING_ARRIVAL_DISTANCE
                : MELEE_ENGAGEMENT_DISTANCE;
        if (playerLocation.distanceTo(engagementLocation) > arrivalDistance) {
            transitionTo(RuntimeState.CHASE_PORTAL, target.portal + " portal");
            moveToward(playerLocation, engagementLocation, arrivalDistance);
            return;
        }

        if (isInteractingWithSpinnerNear(target.portal)) {
            transitionTo(RuntimeState.KILL_SPINNER, target.portal + " portal");
            return;
        }

        Rs2NpcModel spinner = findSpinnerNear(target.portal);
        if (spinner != null) {
            transitionTo(RuntimeState.KILL_SPINNER, target.portal + " portal");
            if (isInteractingWith(spinner.getNpc())) {
                return;
            }
            dispatchAttack(spinner);
            return;
        }

        transitionTo(RuntimeState.ATTACK_PORTAL, target.portal + " portal");
        if (isInteractingWithPortal(target.portal)) {
            return;
        }

        // The chat message can arrive a tick before the NPC composition gains
        // its Attack action. Stay on the portal instead of falling back to a pest.
        if (!target.attackActionAvailable) {
            return;
        }

        dispatchPortalAttack(target.npc);
    }

    private void prepareWeaponForPortal(Portal portal) {
        String configuredWeapon = configuredWeaponForPortal(portal);
        PortalAttackMode attackMode = attackModeForPortal(portal);

        if (isPrimaryFallback(configuredWeapon)) {
            restorePrimaryWeapon();
            applyPrimaryAttackMode();
            return;
        }

        if (equipConfiguredWeapon(configuredWeapon.trim())) {
            applyAttackMode(attackMode);
        } else {
            restorePrimaryWeapon();
            applyPrimaryAttackMode();
        }
    }

    private String configuredWeaponForPortal(Portal portal) {
        switch (portal) {
            case PURPLE:
                return config.rangedWeapon();
            case BLUE:
                return config.magicWeapon();
            case YELLOW:
                return config.slashStabWeapon();
            case RED:
                return config.crushWeapon();
            default:
                return "None";
        }
    }

    private int engagementDistanceForPortal(Portal portal) {
        String configuredWeapon = configuredWeaponForPortal(portal);
        if (isPrimaryFallback(configuredWeapon)) {
            return engagementDistance(config.primaryCombatStyle());
        }
        switch (portal) {
            case PURPLE:
                return RANGED_ENGAGEMENT_DISTANCE;
            case BLUE:
                return RANGED_ENGAGEMENT_DISTANCE;
            case YELLOW:
            case RED:
            default:
                return MELEE_ENGAGEMENT_DISTANCE;
        }
    }

    private static int engagementDistance(PestControlCombatStyle style) {
        return style == PestControlCombatStyle.MELEE
                ? MELEE_ENGAGEMENT_DISTANCE
                : RANGED_ENGAGEMENT_DISTANCE;
    }

    private static PortalAttackMode attackModeForPortal(Portal portal) {
        switch (portal) {
            case PURPLE:
                return PortalAttackMode.RAPID;
            case YELLOW:
                return PortalAttackMode.SLASH_STAB;
            case RED:
                return PortalAttackMode.CRUSH;
            default:
                return PortalAttackMode.PRESERVE;
        }
    }

    private boolean equipConfiguredWeapon(String weaponName) {
        if (isWeaponEquipped(weaponName)) {
            return true;
        }

        capturePrimaryWeapon();
        if (Rs2Inventory.interact(weaponName, "Wield", true)
                && sleepUntil(() -> isWeaponEquipped(weaponName), 2000)) {
            missingWeaponsLogged.remove(normalizeWeaponName(weaponName));
            return true;
        }

        String normalizedWeapon = normalizeWeaponName(weaponName);
        if (missingWeaponsLogged.add(normalizedWeapon)) {
            Microbot.log("Pest Control portal weapon not found in inventory: " + weaponName);
        }
        return false;
    }

    private void restorePrimaryWeapon() {
        capturePrimaryWeapon();
        String equippedWeapon = getEquippedWeaponName();
        if (primaryWeaponName != null && primaryWeaponName.equalsIgnoreCase(equippedWeapon)) {
            return;
        }

        if (primaryWeaponName == null || primaryWeaponName.isEmpty()) {
            if (isConfiguredPortalWeapon(equippedWeapon) && !missingPrimaryWeaponLogged) {
                Microbot.log("Pest Control could not identify the primary weapon before switching");
                missingPrimaryWeaponLogged = true;
            }
            return;
        }

        if (!isConfiguredPortalWeapon(equippedWeapon)) {
            return;
        }

        if (Rs2Inventory.interact(primaryWeaponName, "Wield", true)
                && sleepUntil(() -> isWeaponEquipped(primaryWeaponName), 2000)) {
            missingPrimaryWeaponLogged = false;
        } else if (!missingPrimaryWeaponLogged) {
            Microbot.log("Pest Control primary weapon not found in inventory: " + primaryWeaponName);
            missingPrimaryWeaponLogged = true;
        }
    }

    private void capturePrimaryWeapon() {
        if (primaryWeaponName != null && !primaryWeaponName.isEmpty()) {
            return;
        }

        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equippedWeapon == null || equippedWeapon.getName() == null) {
            return;
        }

        String equippedName = equippedWeapon.getName().trim();
        if (!equippedName.isEmpty() && !isConfiguredPortalWeapon(equippedName)) {
            primaryWeaponName = equippedName;
            Microbot.log("Pest Control primary weapon: " + primaryWeaponName
                    + " (" + config.primaryCombatStyle() + ")");
        }
    }

    private String configuredPrimaryWeaponName() {
        String configuredWeapon;
        switch (config.primaryCombatStyle()) {
            case RANGED:
                configuredWeapon = config.rangedWeapon();
                break;
            case MAGIC:
                configuredWeapon = config.magicWeapon();
                break;
            case MELEE:
                configuredWeapon = !isPrimaryFallback(config.slashStabWeapon())
                        ? config.slashStabWeapon()
                        : config.crushWeapon();
                break;
            default:
                configuredWeapon = null;
                break;
        }
        return isPrimaryFallback(configuredWeapon) ? null : configuredWeapon.trim();
    }

    private void applyPrimaryAttackMode() {
        if (config.primaryCombatStyle() == PestControlCombatStyle.RANGED) {
            applyAttackMode(PortalAttackMode.RAPID);
        }
    }

    private void applyAttackMode(PortalAttackMode attackMode) {
        switch (attackMode) {
            case RAPID:
                selectAttackOption("Rapid");
                break;
            case SLASH_STAB:
                if (!selectAttackOption("Slash")) {
                    selectAttackOption("Stab");
                }
                break;
            case CRUSH:
                selectAttackOption("Crush");
                break;
            case PRESERVE:
            default:
                break;
        }
    }

    private boolean selectAttackOption(String desiredStyle) {
        String equippedWeapon = getEquippedWeaponName();
        if (equippedWeapon.equalsIgnoreCase(managedAttackOptionWeapon)
                && desiredStyle.equalsIgnoreCase(managedAttackOptionName)
                && Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == managedAttackOptionIndex) {
            return true;
        }

        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000)) {
            return false;
        }

        WidgetInfo[] styleWidgets = {
                WidgetInfo.COMBAT_STYLE_ONE,
                WidgetInfo.COMBAT_STYLE_TWO,
                WidgetInfo.COMBAT_STYLE_THREE,
                WidgetInfo.COMBAT_STYLE_FOUR
        };
        int selectedIndex = -1;
        int selectedScore = 0;
        for (int index = 0; index < styleWidgets.length; index++) {
            int styleTextId = styleWidgets[index].getId() + 3;
            String styleText = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget widget = Microbot.getClient().getWidget(styleTextId);
                return widget == null ? null : widget.getText();
            }).orElse(null);
            int score = scoreAttackOption(styleText, desiredStyle);
            if (score > selectedScore) {
                selectedIndex = index;
                selectedScore = score;
            }
        }

        if (selectedIndex < 0) {
            String missingKey = normalizeWeaponName(equippedWeapon) + ":" + desiredStyle.toLowerCase(Locale.ROOT);
            if (missingAttackOptionsLogged.add(missingKey)) {
                Microbot.log("Pest Control could not find " + desiredStyle
                        + " combat option for " + equippedWeapon);
            }
            return false;
        }

        int expectedIndex = selectedIndex;
        boolean selected = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex;
        if (!selected) {
            WidgetInfo selectedStyleWidget = styleWidgets[selectedIndex];
            boolean clickDispatched = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Rs2Combat.setAttackStyle(selectedStyleWidget)
            ).orElse(false);
            selected = clickDispatched
                    && sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex, 2000);
        }
        if (selected) {
            managedAttackOptionWeapon = equippedWeapon;
            managedAttackOptionName = desiredStyle;
            managedAttackOptionIndex = expectedIndex;
            missingAttackOptionsLogged.remove(normalizeWeaponName(equippedWeapon)
                    + ":" + desiredStyle.toLowerCase(Locale.ROOT));
        }
        return selected;
    }

    static int scoreAttackOption(String widgetText, String desiredStyle) {
        if (widgetText == null || desiredStyle == null) {
            return 0;
        }

        String lineSeparatedText = widgetText.replaceAll("(?i)<br\\s*/?>", "\n");
        String[] lines = Text.removeTags(lineSeparatedText).split("\\R");
        String desired = desiredStyle.trim().toLowerCase(Locale.ROOT);
        int score = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim().toLowerCase(Locale.ROOT);
            if (line.equals(desired)) {
                score = Math.max(score, index == 0 ? 100 : 50);
            } else if (line.contains(desired)) {
                score = Math.max(score, index == 0 ? 75 : 25);
            }
            if (line.contains("strength xp")) {
                score += 10;
            }
        }
        return score;
    }

    private boolean isConfiguredPortalWeapon(String weaponName) {
        if (weaponName == null || weaponName.isEmpty()) {
            return false;
        }
        return configuredPortalWeapons().stream()
                .anyMatch(configured -> configured.equalsIgnoreCase(weaponName));
    }

    private List<String> configuredPortalWeapons() {
        return Arrays.asList(
                        config.rangedWeapon(),
                        config.magicWeapon(),
                        config.slashStabWeapon(),
                        config.crushWeapon())
                .stream()
                .filter(weapon -> !isPrimaryFallback(weapon))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private static boolean isPrimaryFallback(String weaponName) {
        return weaponName == null
                || weaponName.trim().isEmpty()
                || weaponName.trim().equalsIgnoreCase("None");
    }

    private static String getEquippedWeaponName() {
        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return equippedWeapon == null || equippedWeapon.getName() == null
                ? ""
                : equippedWeapon.getName().trim();
    }

    private static boolean isWeaponEquipped(String weaponName) {
        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return equippedWeapon != null
                && equippedWeapon.getName() != null
                && equippedWeapon.getName().equalsIgnoreCase(weaponName);
    }

    private static String normalizeWeaponName(String weaponName) {
        return weaponName == null ? "" : weaponName.trim().toLowerCase(Locale.ROOT);
    }

    private enum PortalAttackMode {
        RAPID,
        SLASH_STAB,
        CRUSH,
        PRESERVE
    }

    /**
     * Select portals that expose an Attack action or whose shield-drop game
     * message has arrived. Join the largest player group to finish one portal
     * at a time, retain the current live target across small crowd fluctuations,
     * and use purple as a tie-break.
     */
    private PortalTarget selectAdaptivePortalTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer == null) {
                return null;
            }

            List<Rs2NpcModel> visiblePortals = Microbot.getRs2NpcCache().query()
                    .withName("portal")
                    .where(npc -> npc.getNpc() != null && !npc.getNpc().isDead())
                    .toList();

            List<WorldPoint> otherPlayers = Microbot.getRs2PlayerCache().getStream()
                    .filter(player -> player.getPlayer() != localPlayer)
                    .map(player -> player.getWorldLocation())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            List<PortalTarget> targets = portals.stream()
                    .map(portal -> toPortalTarget(
                            portal,
                            visiblePortals.stream()
                                    .filter(npc -> matchesPortal(npc, portal))
                                    .findFirst()
                                    .orElse(null),
                            otherPlayers,
                            playerLocation))
                    .filter(this::isPortalReady)
                    .collect(Collectors.toList());

            PortalTarget crowdLeader = targets.stream()
                    .max(Comparator
                            .comparingInt((PortalTarget target) -> target.nearbyPlayers)
                            .thenComparingInt(target -> target.portal == PURPLE ? 1 : 0)
                            .thenComparingInt(target -> -target.distance))
                    .orElse(null);
            if (crowdLeader == null || selectedPortal == null) {
                return crowdLeader;
            }

            PortalTarget currentTarget = targets.stream()
                    .filter(target -> target.portal == selectedPortal)
                    .findFirst()
                    .orElse(null);
            if (currentTarget != null
                    && currentTarget.nearbyPlayers + PORTAL_SWITCH_CROWD_MARGIN
                    >= crowdLeader.nearbyPlayers) {
                return currentTarget;
            }
            return crowdLeader;
        }).orElse(null);
    }

    private boolean hasAttackAction(Rs2NpcModel npc) {
        if (npc == null || npc.getNpc() == null || npc.getNpc().isDead()) {
            return false;
        }
        NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
        String[] actions = composition == null ? null : composition.getActions();
        return actions != null && Arrays.stream(actions)
                .anyMatch(action -> action != null && action.equalsIgnoreCase("attack"));
    }

    private boolean isPortalReady(PortalTarget target) {
        Widget hitpoints = target.portal.getHitPoints();
        String text = hitpoints == null ? null : hitpoints.getText();
        if ("0".equals(Text.removeTags(text == null ? "" : text).trim())) {
            return false;
        }
        if (target.attackActionAvailable) {
            return true;
        }
        if (target.portal.hasShield) {
            return false;
        }
        return true;
    }

    private boolean matchesPortal(Rs2NpcModel npc, Portal portal) {
        WorldPoint location = npc == null ? null : npc.getWorldLocation();
        return location != null
                && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                <= PORTAL_MATCH_RADIUS;
    }

    private PortalTarget toPortalTarget(
            Portal portal,
            Rs2NpcModel npc,
            List<WorldPoint> otherPlayers,
            WorldPoint playerLocation) {
        boolean attackActionAvailable = hasAttackAction(npc);
        if (attackActionAvailable) {
            // Keep shield tracking resilient when the script starts mid-round or
            // a shield-drop chat message is missed.
            portal.setHasShield(false);
        }
        int nearbyPlayers = (int) otherPlayers.stream()
                .filter(player -> regionDistance(
                        player,
                        portal.getRegionX(),
                        portal.getRegionY()) <= PORTAL_CROWD_RADIUS)
                .count();
        int distance = playerLocation == null
                ? Integer.MAX_VALUE
                : regionDistance(playerLocation, portal.getRegionX(), portal.getRegionY());
        return new PortalTarget(portal, npc, nearbyPlayers, distance, attackActionAvailable);
    }

    private Rs2NpcModel findSpinnerNear(Portal portal) {
        return Microbot.getRs2NpcCache().query()
                .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(spinner -> spinner.getNpc() != null
                        && !spinner.getNpc().isDead()
                        && spinner.getWorldLocation() != null
                        && regionDistance(
                        spinner.getWorldLocation(),
                        portal.getRegionX(),
                        portal.getRegionY()) <= SPINNER_PORTAL_RADIUS)
                .nearestOnClientThread();
    }

    private static WorldPoint logicalPortalLocation(Portal portal, WorldPoint playerLocation) {
        return WorldPoint.fromRegion(
                playerLocation.getRegionID(),
                portal.getRegionX(),
                portal.getRegionY(),
                playerLocation.getPlane());
    }

    private static WorldPoint safeRangedPortalLocation(Portal portal, WorldPoint playerLocation) {
        int dx = portal.getRegionX() - PEST_CONTROL_CENTER_REGION_COORD;
        int dy = portal.getRegionY() - PEST_CONTROL_CENTER_REGION_COORD;
        int span = Math.max(Math.abs(dx), Math.abs(dy));
        int offsetX = span == 0 ? 0 : (int) Math.round((double) dx * SAFE_STAGING_OFFSET / span);
        int offsetY = span == 0 ? 0 : (int) Math.round((double) dy * SAFE_STAGING_OFFSET / span);
        int regionX = Math.max(0, Math.min(63, portal.getRegionX() + offsetX));
        int regionY = Math.max(0, Math.min(63, portal.getRegionY() + offsetY));
        return WorldPoint.fromRegion(
                playerLocation.getRegionID(),
                regionX,
                regionY,
                playerLocation.getPlane());
    }

    private static boolean isInteractingWith(NPC npc) {
        if (npc == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getInteracting() == npc;
        }).orElse(false);
    }

    private static boolean isInteractingWithPortal(Portal portal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC)) {
                return false;
            }

            NPC target = (NPC) player.getInteracting();
            WorldPoint location = target.getWorldLocation();
            return location != null
                    && target.getName() != null
                    && target.getName().equalsIgnoreCase("portal")
                    && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                    <= PORTAL_MATCH_RADIUS;
        }).orElse(false);
    }

    private static boolean isInteractingWithSpinnerNear(Portal portal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC)) {
                return false;
            }

            NPC target = (NPC) player.getInteracting();
            WorldPoint location = target.getWorldLocation();
            return location != null
                    && SPINNER_IDS.contains(target.getId())
                    && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                    <= SPINNER_PORTAL_RADIUS;
        }).orElse(false);
    }

    private static boolean isInteractingWithSpinner() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null
                    && player.getInteracting() instanceof NPC
                    && SPINNER_IDS.contains(((NPC) player.getInteracting()).getId());
        }).orElse(false);
    }

    private static int regionDistance(WorldPoint point, int regionX, int regionY) {
        return Math.max(
                Math.abs(point.getRegionX() - regionX),
                Math.abs(point.getRegionY() - regionY));
    }

    private static final class PortalTarget {
        private final Portal portal;
        private final Rs2NpcModel npc;
        private final int nearbyPlayers;
        private final int distance;
        private final boolean attackActionAvailable;

        private PortalTarget(
                Portal portal,
                Rs2NpcModel npc,
                int nearbyPlayers,
                int distance,
                boolean attackActionAvailable) {
            this.portal = portal;
            this.npc = npc;
            this.nearbyPlayers = nearbyPlayers;
            this.distance = distance;
            this.attackActionAvailable = attackActionAvailable;
        }
    }

    private boolean attackSpinner() {
        if (isInteractingWithSpinner()) {
            transitionTo(RuntimeState.KILL_SPINNER, "nearby Spinner");
            return true;
        }
        Rs2NpcModel spinner = Microbot.getRs2NpcCache().query()
                .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(npc -> npc.getNpc() != null
                        && !npc.getNpc().isDead()
                        && npc.getDistanceFromPlayer() <= SPINNER_PORTAL_RADIUS)
                .nearestOnClientThread();
        if (spinner == null) {
            return false;
        }
        transitionTo(RuntimeState.KILL_SPINNER, "nearby Spinner");
        if (isInteractingWith(spinner.getNpc())) {
            return true;
        }
        dispatchAttack(spinner);
        return true;
    }

    private void activateSpecialAttackIfReady() {
        Optional<SpecialAttackWeaponEnum> specialAttackWeapon = getEquippedSpecialAttackWeapon();
        if (specialAttackWeapon.isEmpty() || !hasCombatTarget()) {
            return;
        }

        int configuredEnergyRequired = config.specialAttackPercentage() * 10;
        if (configuredEnergyRequired <= 0) {
            return;
        }

        int energyRequired = Math.max(configuredEnergyRequired, specialAttackWeapon.get().getEnergyRequired());
        Rs2Combat.setSpecState(true, energyRequired);
    }

    private Optional<SpecialAttackWeaponEnum> getEquippedSpecialAttackWeapon() {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (weapon == null || weapon.getName() == null) {
            return Optional.empty();
        }

        String weaponName = weapon.getName().toLowerCase(Locale.ROOT);
        return Arrays.stream(SpecialAttackWeaponEnum.values())
                .sorted(Comparator.comparingInt((SpecialAttackWeaponEnum specWeapon) -> specWeapon.getName().length()).reversed())
                .filter(specWeapon -> weaponName.contains(specWeapon.getName()))
                .findFirst();
    }

    private boolean hasCombatTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null) {
                return false;
            }

            Actor target = player.getInteracting();
            return target != null && !target.isDead();
        }).orElse(false);
    }

    @Override
    public void shutdown() {
        Microbot.log("Pest control about to shutdown");
        initialise = true;
        openingSideReached = false;
        wasInPestControl = false;
        pendingPostRoundRestore = false;
        autoRetaliateConfirmedOff = false;
        autoRetaliateDisableLogged = false;
        selectedPortal = null;
        openingPortal = null;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        super.shutdown();
    }
}
