package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.microhunter.AutoHunterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AutoChinScript extends Script {
    public enum State {
        IDLE,
        MOVING,
        WAITING_FOR_CONFIRMATION,
        BREAK_PENDING,
        STOPPED
    }

    private enum Action {
        CHECK("Check"),
        RESET("Reset"),
        TAKE("Take"),
        LAY("Lay");

        private final String menuAction;

        Action(String menuAction) {
            this.menuAction = menuAction;
        }
    }

    private static final long ACTION_TIMEOUT_MS = 6_000;
    private static final long SCENE_BASELINE_MS = 10_000;
    private static final long SPAWN_EXPIRY_MS = 600_000;
    private static final String TARGET_NAME = "Red chinchompa";

    private final Set<WorldPoint> managedTiles = ConcurrentHashMap.newKeySet();
    private final Map<WorldPoint, SpawnObservation> spawnObservations = new ConcurrentHashMap<>();
    private final Map<WorldPoint, String> observedTrapSignatures = new ConcurrentHashMap<>();
    private volatile State currentState = State.IDLE;
    private volatile String nextAction = "Initialising";
    private volatile String stopReason = "";
    private volatile WorldPoint bestSpawnTile;
    private volatile WorldPoint bestRingTile;
    private volatile String spawnSummary = "none";
    private volatile int catches;
    private volatile int resets;
    private volatile int activeTraps;
    private volatile int trapLimit = 1;
    private volatile int huntingRadius = 6;
    private volatile PendingAction pending;
    private volatile WorldPoint moveTarget;
    private WorldPoint startTile;
    private long baselineUntil;
    private long nextRingEvaluationAt;

    public boolean run(AutoHunterConfig config) {
        resetSession();
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> pulse(config),
                0, 250, TimeUnit.MILLISECONDS);
        Microbot.log("AutoHunter started: source-only red-chin state machine; no existing traps adopted");
        return true;
    }

    private void resetSession() {
        managedTiles.clear();
        spawnObservations.clear();
        observedTrapSignatures.clear();
        currentState = State.IDLE;
        nextAction = "Waiting for client state";
        stopReason = "";
        bestSpawnTile = null;
        bestRingTile = null;
        spawnSummary = "none";
        catches = 0;
        resets = 0;
        activeTraps = 0;
        pending = null;
        moveTarget = null;
        startTile = Rs2Player.getWorldLocation();
        baselineUntil = System.currentTimeMillis() + SCENE_BASELINE_MS;
        nextRingEvaluationAt = 0;
    }

    private void pulse(AutoHunterConfig config) {
        try {
            if (!Microbot.isLoggedIn() || !super.run()) return;
            if (currentState == State.STOPPED) return;

            if (startTile == null) {
                startTile = Rs2Player.getWorldLocation();
                baselineUntil = System.currentTimeMillis() + SCENE_BASELINE_MS;
            }

            huntingRadius = Math.max(1, config.huntingRadius());
            trapLimit = AutoHunterPlanner.normalBoxTrapLimit(Rs2Player.getRealSkillLevel(Skill.HUNTER));
            expireSpawnObservations();
            updateSpawnRing(config);
            activeTraps = countActiveManagedTraps();

            if (pending != null) {
                confirmOrTimeoutPending();
                return;
            }

            if (Rs2Inventory.emptySlotCount() == 0) {
                stopSafely("Inventory full; catches are never dropped or banked automatically");
                return;
            }

            if (BreakHandlerScript.breakIn > 0 && BreakHandlerScript.breakIn <= 60) {
                transition(State.BREAK_PENDING, "Break pending; manual trap recovery required");
                return;
            }
            if (currentState == State.BREAK_PENDING) transition(State.IDLE, "Break window cleared");

            if (moveTarget != null) {
                handleMoveTarget();
                return;
            }

            if (interactWithManagedTrap(AutoHunterPlanner.TrapState.CAUGHT, Action.CHECK)) return;
            if (interactWithManagedTrap(AutoHunterPlanner.TrapState.FAILED, Action.RESET)) return;
            if (recoverFallenManagedTrap()) return;
            if (layMissingManagedTrap()) return;
            if (managedTiles.size() < trapLimit) prepareNewTrap(config);
            else transition(State.IDLE, "Monitoring owned traps");
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }
    }

    private boolean interactWithManagedTrap(AutoHunterPlanner.TrapState targetState, Action action) {
        for (WorldPoint tile : managedTiles) {
            Rs2TileObjectModel trap = trapAt(tile);
            if (trap == null || classify(trap) != targetState) continue;
            if (trap.click(action.menuAction)) {
                beginPending(action, tile, trapSignature(trap));
                return true;
            }
        }
        return false;
    }

    private boolean recoverFallenManagedTrap() {
        for (WorldPoint tile : managedTiles) {
            if (hasAnyObjectAt(tile)) continue;
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).interact("Take")) {
                beginPending(Action.TAKE, tile, "ground-item");
                return true;
            }
        }
        return false;
    }

    private boolean layMissingManagedTrap() {
        if (!Rs2Inventory.contains(ItemID.BOX_TRAP)) return false;
        for (WorldPoint tile : managedTiles) {
            if (!hasAnyObjectAt(tile)
                    && Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).count() == 0) {
                moveTarget = tile;
                transition(State.MOVING, "Return to owned trap tile " + tile);
                return true;
            }
        }
        return false;
    }

    private void prepareNewTrap(AutoHunterConfig config) {
        if (!Rs2Inventory.contains(ItemID.BOX_TRAP)) {
            transition(State.IDLE, "Need a box trap in inventory");
            return;
        }
        WorldPoint target = config.useSpawnRing() ? bestRingTile : Rs2Player.getWorldLocation();
        if (target == null) {
            transition(State.IDLE, "Waiting for a verified spawn-ring candidate");
            return;
        }
        if (!isSafePlacementTile(target, false)) {
            transition(State.IDLE, "Current placement tile is occupied or unreachable");
            return;
        }
        moveTarget = target;
        transition(State.MOVING, "Move to lay box trap at " + target);
    }

    private void handleMoveTarget() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (!player.equals(moveTarget)) {
            Rs2Walker.walkTo(moveTarget, 0);
            return;
        }
        WorldPoint layTile = moveTarget;
        moveTarget = null;
        if (!isSafePlacementTile(layTile, managedTiles.contains(layTile))
                || !Rs2Inventory.contains(ItemID.BOX_TRAP)) {
            transition(State.IDLE, "Lay tile became unavailable");
            return;
        }
        if (Rs2Inventory.interact(ItemID.BOX_TRAP, "Lay")) {
            beginPending(Action.LAY, layTile, "empty");
        } else {
            transition(State.IDLE, "Lay interaction was not dispatched");
        }
    }

    private void beginPending(Action action, WorldPoint tile, String beforeSignature) {
        pending = new PendingAction(action, tile, beforeSignature, Rs2Inventory.count(), System.currentTimeMillis());
        transition(State.WAITING_FOR_CONFIRMATION, action + " dispatched at " + tile);
        Microbot.log("AutoHunter action: " + action + " dispatched at " + tile);
    }

    private void confirmOrTimeoutPending() {
        PendingAction action = pending;
        if (action == null) return;
        Rs2TileObjectModel object = trapAt(action.tile);
        String currentSignature = trapSignature(object);
        boolean inventoryChanged = Rs2Inventory.count() != action.inventoryCount;
        boolean objectChanged = !currentSignature.equals(action.beforeSignature);
        boolean confirmed;
        switch (action.action) {
            case LAY:
                confirmed = object != null;
                break;
            case TAKE:
                confirmed = inventoryChanged || Microbot.getRs2TileItemCache().query()
                        .withId(ItemID.BOX_TRAP).within(action.tile, 0).count() == 0;
                break;
            default:
                confirmed = inventoryChanged || objectChanged;
        }

        if (confirmed) {
            if (action.action == Action.LAY) managedTiles.add(action.tile);
            if (action.action == Action.CHECK) catches++;
            if (action.action == Action.RESET) resets++;
            pending = null;
            transition(State.IDLE, action.action + " confirmed at " + action.tile);
            Microbot.log("AutoHunter action: " + action.action + " confirmed at " + action.tile);
        } else if (System.currentTimeMillis() - action.startedAt >= ACTION_TIMEOUT_MS) {
            pending = null;
            transition(State.IDLE, action.action + " timed out at " + action.tile);
            Microbot.log("AutoHunter action: " + action.action + " bounded timeout at " + action.tile);
        }
    }

    private Rs2TileObjectModel trapAt(WorldPoint tile) {
        return Microbot.getRs2TileObjectCache().query().within(tile, 0)
                .where(object -> classify(object) != AutoHunterPlanner.TrapState.UNKNOWN).first();
    }

    private boolean hasAnyObjectAt(WorldPoint tile) {
        return Microbot.getRs2TileObjectCache().query().within(tile, 0).count() > 0;
    }

    private AutoHunterPlanner.TrapState classify(Rs2TileObjectModel object) {
        if (object == null || object.getObjectComposition() == null) return AutoHunterPlanner.TrapState.UNKNOWN;
        return AutoHunterPlanner.classifyActions(object.getObjectComposition().getActions());
    }

    private String trapSignature(Rs2TileObjectModel object) {
        if (object == null) return "none";
        String[] actions = object.getObjectComposition() == null
                ? null : object.getObjectComposition().getActions();
        return object.getId() + ":" + classify(object) + ":" + Arrays.toString(actions);
    }

    private int countActiveManagedTraps() {
        int count = 0;
        for (WorldPoint tile : managedTiles) {
            Rs2TileObjectModel trap = trapAt(tile);
            String signature = trapSignature(trap);
            String previous = observedTrapSignatures.put(tile, signature);
            if (!signature.equals(previous)) {
                Microbot.log("AutoHunter trap: " + tile + " -> " + signature);
            }
            if (trap != null) count++;
        }
        return count;
    }

    public void onNpcSpawned(NPC npc) {
        if (npc == null || !TARGET_NAME.equalsIgnoreCase(npc.getName()) || startTile == null) return;
        WorldPoint tile = npc.getWorldLocation();
        if (tile == null || tile.getPlane() != startTile.getPlane()) return;
        if (tile.distanceTo(startTile) > huntingRadius || System.currentTimeMillis() < baselineUntil) return;
        SpawnObservation observation = spawnObservations.compute(tile, (ignored, existing) -> {
            if (existing == null) return new SpawnObservation(1, System.currentTimeMillis());
            existing.appearances++;
            existing.lastSeen = System.currentTimeMillis();
            return existing;
        });
        Microbot.log("AutoHunter spawn: " + tile + " appearances=" + observation.appearances);
    }

    private void expireSpawnObservations() {
        long cutoff = System.currentTimeMillis() - SPAWN_EXPIRY_MS;
        spawnObservations.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoff);
    }

    private void updateSpawnRing(AutoHunterConfig config) {
        WorldPoint player = Rs2Player.getWorldLocation();
        long now = System.currentTimeMillis();
        if (now < nextRingEvaluationAt) return;
        nextRingEvaluationAt = now + 2_000;
        Map.Entry<WorldPoint, SpawnObservation> best = spawnObservations.entrySet().stream()
                .filter(entry -> entry.getValue().appearances >= 2)
                .filter(entry -> startTile == null || entry.getKey().distanceTo(startTile) <= config.huntingRadius())
                .max(Comparator.comparingDouble(entry -> AutoHunterPlanner.spawnScore(
                        entry.getValue().appearances, now - entry.getValue().lastSeen,
                        player.distanceTo(entry.getKey())))).orElse(null);
        bestSpawnTile = best == null ? null : best.getKey();
        spawnSummary = best == null ? "none" : bestSpawnTile + " x" + best.getValue().appearances
                + " score=" + Math.round(AutoHunterPlanner.spawnScore(best.getValue().appearances,
                now - best.getValue().lastSeen, player.distanceTo(bestSpawnTile)));
        bestRingTile = bestSpawnTile == null ? null : AutoHunterPlanner.ring(bestSpawnTile).stream()
                .filter(tile -> isSafePlacementTile(tile, false))
                .min(Comparator.comparingInt(player::distanceTo)).orElse(null);
    }

    private boolean isSafePlacementTile(WorldPoint tile, boolean allowManagedTile) {
        if (tile == null || (!allowManagedTile && managedTiles.contains(tile))) return false;
        if (startTile == null || tile.getPlane() != startTile.getPlane()
                || tile.distanceTo(startTile) > huntingRadius) return false;
        if (hasAnyObjectAt(tile)) return false;
        Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
        if (Rs2Player.getPlayers(player -> tile.equals(player.getWorldLocation())
                && (localPlayer == null || player.getId() != localPlayer.getId())).findAny().isPresent()) return false;
        return Rs2Tile.isWalkable(tile) && Rs2Walker.canReach(tile);
    }

    private void stopSafely(String reason) {
        stopReason = reason;
        pending = null;
        moveTarget = null;
        transition(State.STOPPED, reason);
        Microbot.log("AutoHunter stopped: " + reason);
    }

    private void transition(State state, String action) {
        if (currentState != state || !nextAction.equals(action)) {
            Microbot.log("AutoHunter state: " + currentState + " -> " + state + "; " + action);
        }
        currentState = state;
        nextAction = action;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        managedTiles.clear();
        spawnObservations.clear();
        observedTrapSignatures.clear();
        pending = null;
        moveTarget = null;
    }

    public State getCurrentState() { return currentState; }
    public String getNextAction() { return nextAction; }
    public String getStopReason() { return stopReason; }
    public int getManagedTrapCount() { return managedTiles.size(); }
    public int getActiveTrapCount() { return activeTraps; }
    public int getTrapLimit() { return trapLimit; }
    public int getCatches() { return catches; }
    public int getResets() { return resets; }
    public WorldPoint getBestSpawnTile() { return bestSpawnTile; }
    public WorldPoint getBestRingTile() { return bestRingTile; }
    public String getSpawnSummary() { return spawnSummary; }

    private static final class PendingAction {
        private final Action action;
        private final WorldPoint tile;
        private final String beforeSignature;
        private final int inventoryCount;
        private final long startedAt;

        private PendingAction(Action action, WorldPoint tile, String beforeSignature,
                              int inventoryCount, long startedAt) {
            this.action = action;
            this.tile = tile;
            this.beforeSignature = beforeSignature;
            this.inventoryCount = inventoryCount;
            this.startedAt = startedAt;
        }
    }

    private static final class SpawnObservation {
        private int appearances;
        private long lastSeen;

        private SpawnObservation(int appearances, long lastSeen) {
            this.appearances = appearances;
            this.lastSeen = lastSeen;
        }
    }
}
