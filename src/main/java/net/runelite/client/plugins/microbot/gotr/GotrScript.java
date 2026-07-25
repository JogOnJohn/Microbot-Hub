package net.runelite.client.plugins.microbot.gotr;

import com.google.common.collect.ImmutableList;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.gotr.data.CellType;
import net.runelite.client.plugins.microbot.gotr.data.GuardianPortalInfo;
import net.runelite.client.plugins.microbot.gotr.data.Mode;
import net.runelite.client.plugins.microbot.gotr.data.RuneType;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;


public class GotrScript extends Script {

    public static long totalTime = 0;
    public static boolean shouldMineGuardianRemains = true;
    public static final String rewardPointRegex = "Total elemental energy:[^>]+>([\\d,]+).*Total catalytic energy:[^>]+>([\\d,]+).";
    public static final Pattern rewardPointPattern = Pattern.compile(rewardPointRegex);

    public static boolean isInMiniGame = false;
    public static boolean isFirstPortal = true;
    public static final int portalId = ObjectID.PORTAL_43729;
    public static final int greatGuardianId = 11403;
    private static final int ACTIVE_GUARDIAN_PORTAL_ANIMATION = 9363;
    private static final int TALISMAN_ENDGAME_POWER = 90;
    private static final int PRE_ROUND_GUARDIAN_POWER = 10;
    private static final int ALTAR_ENTRY_START_TIMEOUT_MS = 12000;
    private static final int ALTAR_LOAD_TIMEOUT_MS = 10000;
    public static final Map<Integer, GuardianPortalInfo> guardianPortalInfo = new HashMap<>();
    public static Optional<Instant> nextGameStart = Optional.empty();
    public static Optional<Instant> timeSincePortal = Optional.empty();
    public static final Set<GameObject> guardians = new HashSet<>();
    public static final List<GameObject> activeGuardianPortals = new ArrayList<>();
    public static NPC greatGuardian;
    public static int elementalRewardPoints;
    public static int catalyticRewardPoints;
    public static GotrState state;
    public static volatile boolean needsOpeningCell = true;
    static GotrConfig config;
    String GUARDIAN_FRAGMENTS = "guardian fragments";
    String GUARDIAN_ESSENCE = "guardian essence";

    boolean initCheck = false;
    boolean optimizedEssenceLoop = false;
    private int lastLoggedFragmentStopCount = -1;
    private int lastObservedGuardiansPower = -1;

    static boolean useNpcContact = true;

    private static final class GuardianPortalCandidate {
        private final GameObject portal;
        private final GuardianPortalInfo info;
        private final boolean active;
        private final boolean hasTalisman;
        private final int distance;

        private GuardianPortalCandidate(
            GameObject portal,
            GuardianPortalInfo info,
            boolean active,
            boolean hasTalisman,
            int distance) {
            this.portal = portal;
            this.info = info;
            this.active = active;
            this.hasTalisman = hasTalisman;
            this.distance = distance;
        }

        private boolean requiresTalisman() {
            return !active && hasTalisman;
        }

        private String accessDescription() {
            if (active && hasTalisman) {
                return "ACTIVE+TALISMAN";
            }
            return requiresTalisman() ? "TALISMAN" : "ACTIVE";
        }
    }

    private final List<Integer> runeIds = ImmutableList.of(
            ItemID.NATURE_RUNE,
            ItemID.LAW_RUNE,
            ItemID.BODY_RUNE,
            ItemID.DUST_RUNE,
            ItemID.LAVA_RUNE,
            ItemID.STEAM_RUNE,
            ItemID.SMOKE_RUNE,
            ItemID.SOUL_RUNE,
            ItemID.WATER_RUNE,
            ItemID.AIR_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.FIRE_RUNE,
            ItemID.MIND_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.BLOOD_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.ASTRAL_RUNE,
            ItemID.MIST_RUNE,
            ItemID.MUD_RUNE,
            ItemID.WRATH_RUNE);

    private void initializeGuardianPortalInfo() {
        guardianPortalInfo.clear();
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_AIR, new GuardianPortalInfo("AIR", 1, ItemID.AIR_RUNE, ItemID.PORTAL_TALISMAN_AIR, 4353, RuneType.ELEMENTAL, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_MIND, new GuardianPortalInfo("MIND", 2, ItemID.MIND_RUNE, ItemID.PORTAL_TALISMAN_MIND, 4354, RuneType.CATALYTIC, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_WATER, new GuardianPortalInfo("WATER", 5, ItemID.WATER_RUNE, ItemID.PORTAL_TALISMAN_WATER, 4355, RuneType.ELEMENTAL, CellType.MEDIUM, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_EARTH, new GuardianPortalInfo("EARTH", 9, ItemID.EARTH_RUNE, ItemID.PORTAL_TALISMAN_EARTH, 4356, RuneType.ELEMENTAL, CellType.STRONG, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_FIRE, new GuardianPortalInfo("FIRE", 14, ItemID.FIRE_RUNE, ItemID.PORTAL_TALISMAN_FIRE, 4357, RuneType.ELEMENTAL, CellType.OVERCHARGED, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_BODY, new GuardianPortalInfo("BODY", 20, ItemID.BODY_RUNE, ItemID.PORTAL_TALISMAN_BODY, 4358, RuneType.CATALYTIC, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_COSMIC, new GuardianPortalInfo("COSMIC", 27, ItemID.COSMIC_RUNE, ItemID.PORTAL_TALISMAN_COSMIC, 4359, RuneType.CATALYTIC, CellType.MEDIUM, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.LOST_CITY.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_CHAOS, new GuardianPortalInfo("CHAOS", 35, ItemID.CHAOS_RUNE, ItemID.PORTAL_TALISMAN_CHAOS, 4360, RuneType.CATALYTIC, CellType.MEDIUM, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_NATURE, new GuardianPortalInfo("NATURE", 44, ItemID.NATURE_RUNE, ItemID.PORTAL_TALISMAN_NATURE, 4361, RuneType.CATALYTIC, CellType.STRONG, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_LAW, new GuardianPortalInfo("LAW", 54, ItemID.LAW_RUNE, ItemID.PORTAL_TALISMAN_LAW, 4362, RuneType.CATALYTIC, CellType.STRONG, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.TROLL_STRONGHOLD.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_DEATH, new GuardianPortalInfo("DEATH", 65, ItemID.DEATH_RUNE, ItemID.PORTAL_TALISMAN_DEATH, 4363, RuneType.CATALYTIC, CellType.OVERCHARGED, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.MOURNINGS_END_PART_II.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_BLOOD, new GuardianPortalInfo("BLOOD", 77, ItemID.BLOOD_RUNE, ItemID.PORTAL_TALISMAN_BLOOD, 4364, RuneType.CATALYTIC, CellType.OVERCHARGED, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.SINS_OF_THE_FATHER.getState(Microbot.getClient())).orElse(null)));
    }

    public boolean run(GotrConfig config) {
        this.config = config;
        // Static (and singleton-instance) state persists for the whole JVM session and leaks
        // across plugin disable/re-enable (see docs/PLUGIN_DEBUGGING_NOTES.md §5). Reset it here
        // so a restart behaves like a first start instead of inheriting a stale state machine.
        shouldMineGuardianRemains = true;
        isInMiniGame = false;
        isFirstPortal = true;
        state = null;
        nextGameStart = Optional.empty();
        timeSincePortal = Optional.empty();
        elementalRewardPoints = 0;
        catalyticRewardPoints = 0;
        useNpcContact = true;
        initCheck = false;
        optimizedEssenceLoop = false;
        lastLoggedFragmentStopCount = -1;
        lastObservedGuardiansPower = -1;
        needsOpeningCell = true;
        guardians.clear();
        activeGuardianPortals.clear();
        greatGuardian = null;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                // Region and object caches can retain the previous scene while an altar is
                // loading. Do not run main-arena actions against that stale state.
                if (Microbot.getClient().getGameState() != GameState.LOGGED_IN
                    || Microbot.getClient().getLocalPlayer() == null) {
                    return;
                }
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (!initCheck) {
                    initializeGuardianPortalInfo();
                    if (!Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR)) {
                        Microbot.log("Lunar spellbook not found...disabling npc contact");
                        useNpcContact = false;
                    }
                    log("GOTR mass config | maxFragments=" + config.maxFragmentAmount()
                        + " | maxEssenceBeforePortal=" + config.maxAmountEssence()
                        + " | mode=" + config.Mode());
                    initCheck = true;
                }

                if (!Rs2Inventory.hasItem("pickaxe") && !Rs2Equipment.isWearing("pickaxe")) {
                    log("You need to have a pickaxe before you can participate in this minigame.");
                    return;
                }

                checkPouches(Rs2Inventory.anyPouchUnknown(), 1500, 300);

                //IS INSIDE THE MINIGAME
                int timeToStart = 0;
                if (nextGameStart.isPresent()) {
                    timeToStart = ((int) ChronoUnit.SECONDS.between(Instant.now(), nextGameStart.get()));
                }

                //Repair colossal pouch asap to avoid disintegrate completely
                if (Rs2Inventory.hasItem("colossal pouch") && Rs2Inventory.hasDegradedPouch()) {
                    if (!repairPouches()) {
                        return;
                    }
                }

                GotrScript.isInMiniGame = !isOutsideBarrier() && isInMainRegion();


                if (isInMiniGame) {
                    updateRoundCompletionState();

                    if (waitingForGameToStart(timeToStart)) return;

                    // Redeem the previous altar trip before chasing a portal. Mass worlds can
                    // advance quickly, and delaying stones/cells until after the huge mine loses
                    // contribution time and can leave weak barriers unattended.
                    if (powerUpGreatGuardian()) return;
                    if (repairCells()) return;

                    // Bank crafted runes immediately after contributing. Waiting for a portal can
                    // otherwise send the player back to fragments with a rune stack occupying a
                    // useful inventory slot.
                    if (depositRunesIntoPool()) return;
                    if (usePortal()) return;
                    // Mine huge guardian remains.
                    if (mineHugeGuardianRemain()) return;

                    // Restock after portal handling; taking cells first can unnecessarily delay a
                    // portal trip.
                    if (!Rs2Inventory.hasItem("Uncharged cell") && !isInLargeMine() && !isInHugeMine()) {
                        takeUnchargedCells();
                        return;
                    }

                    if (!shouldMineGuardianRemains) {
                        //Create fragments into whatever
                        if (isOutOfFragments()) return;

                        //deposit runes
                        if (depositRunesIntoPool()) return;

                        if (fillPouches()) {
                            craftGuardianEssences();
                            return;
                        }
                        if (!Rs2Inventory.isFull() && !optimizedEssenceLoop) {
                            if (leaveLargeMine()) return;

                            if (state == GotrState.CRAFT_GUARDIAN_ESSENCE && (Rs2Player.isAnimating() || Rs2Player.isMoving())) return;

                            if (craftGuardianEssences()) return;

                        } else if (Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
                            if (leaveLargeMine()) return;
                            if (enterAltar()) return;
                        }
                    } else {
                        if (ensureOpeningCellBeforeMining()) return;

                        int fragmentTarget = config.maxFragmentAmount();
                        if (getGuardiansPower() > 70) {
                            int batchCapacity = Rs2Inventory.emptySlotCount()
                                + Rs2Inventory.getRemainingCapacityInPouches();
                            fragmentTarget = Math.min(
                                fragmentTarget,
                                Rs2Random.between(
                                    Math.max(1, batchCapacity),
                                    Math.max(1, batchCapacity) + 3));
                        }
                        if (stopMiningAtFragmentTarget(fragmentTarget)) return;
                        mineGuardianRemains();
                    }
                    return;
                }


                //IS NOT IN THE MINIGAME

                if (craftRunes()) return;

                if (enterMinigame()) return;

                if (waitForMinigameToStart()) return;


                long endTime = System.currentTimeMillis();
                totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Something went wrong in the GOTR Script: " + ex.getMessage() + ". If the script is stuck, please contact us on discord with this log.");
                ex.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean waitingForGameToStart(int timeToStart) {
        if (isInHugeMine()) return false;

        if (getStartTimer() > Rs2Random.randomGaussian(35, Rs2Random.between(1, 5)) || getStartTimer() == -1 || timeToStart > 10) {

            // A round just ended (or hasn't started yet) and this path runs instead of the
            // craft branch — bank any crafted runes into the pool before prepping for the next
            // game, so we never carry runes over.
            if (depositRunesIntoPool()) return true;

            // Only take cells if we don't already have them
            if (!Rs2Inventory.hasItem("Uncharged cell")) {
                // If in large mine and need cells, leave first
                if (isInLargeMine()) {
                    if (leaveLargeMine()) return true;
                }
                takeUnchargedCells();
                return true;
            }

            if (ensureOpeningCellBeforeMining()) return true;

            repairPouches();
    
            if (!shouldMineGuardianRemains) return true;

            // The countdown path used to bypass the configured cap and continue mining until
            // roughly 35 seconds before the round, commonly leaving 120+ fragments for a target
            // of 100. Enforce and interrupt at the same target used by the regular mining path.
            if (stopMiningAtFragmentTarget(config.maxFragmentAmount())) return true;

            mineGuardianRemains();
            return true;
        }
        return false;
    }

    private boolean stopMiningAtFragmentTarget(int fragmentTarget) {
        int fragmentCount = Rs2Inventory.itemQuantity(GUARDIAN_FRAGMENTS);
        if (fragmentCount < fragmentTarget) {
            return false;
        }

        shouldMineGuardianRemains = false;
        if (fragmentCount != lastLoggedFragmentStopCount) {
            log("Fragment target reached: " + fragmentCount + "/" + fragmentTarget
                + "; stopping mining.");
            lastLoggedFragmentStopCount = fragmentCount;
        }
        // Clicking the large-mine exit immediately interrupts the persistent mining action.
        if (isInLargeMine()) {
            leaveLargeMine();
        }
        return true;
    }

    private boolean repairCells() {
        Rs2ItemModel cell = Rs2Inventory.get(CellType.PoweredCellList().stream().mapToInt(i -> i).toArray());
        if (cell == null || !isInMainRegion() || !isInMiniGame() || isInLargeMine() || isInHugeMine()) {
            return false;
        }
        int cellTier = CellType.GetCellTier(cell.getId());
        // Identify the shield pylons by object id (CellType.GetShieldTier knows them all and
        // returns -1 for anything else). The previous filter matched on a name containing
        // "cell_tile", but the real pylon objects aren't named that, so the query always came
        // back empty — yet the method still returned true unconditionally below. That made the
        // main loop short-circuit at `if (repairCells()) return;` on every tick whenever a
        // powered cell was held, leaving the bot standing idle until the next game start. Match
        // by id, and only claim the tick when we actually place/use a cell.
        List<Rs2TileObjectModel> shieldCells = Microbot.getRs2TileObjectCache().query()
            .where(o -> CellType.GetShieldTier(o.getId()) >= 0)
            .toListOnClientThread();

        // Build an inactive barrier or upgrade the lowest barrier first. If every barrier is at
        // least as strong as the held cell, use it on an active barrier for healing.
        Rs2TileObjectModel cellToUse = shieldCells.stream()
            .filter(o -> CellType.GetShieldTier(o.getId()) < cellTier)
            .min(Comparator.comparingInt(o -> CellType.GetShieldTier(o.getId())))
            .orElseGet(() -> shieldCells.stream()
                .filter(o -> CellType.GetShieldTier(o.getId()) > 0)
                .findFirst()
                .orElse(null));
        if (cellToUse == null) {
            return false;
        }

        int shieldTier = CellType.GetShieldTier(cellToUse.getId());
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint barrierLocation = cellToUse.getWorldLocation();
        if (playerLocation == null || barrierLocation == null) {
            return false;
        }

        // Travel and placement are separate states. Only start the consumption timeout once the
        // player is close enough for the interaction itself, rather than charging travel time
        // against the six-second validation window.
        if (playerLocation.distanceTo(barrierLocation) > 8) {
            if (!Rs2Player.isMoving()) {
                log("Walking into cell range at " + barrierLocation);
                Rs2Walker.walkFastCanvas(barrierLocation);
            }
            return true;
        }
        if (Rs2Player.isMoving()) {
            return true;
        }

        int cellsBefore = countPoweredCells();
        if (!cellToUse.click("Place-cell")) {
            return false;
        }

        log("Placing tier " + cellTier + " cell on tier " + shieldTier
            + " barrier at " + cellToUse.getWorldLocation());
        boolean consumed = Global.sleepUntil(() -> countPoweredCells() < cellsBefore, 6000);
        if (!consumed) {
            log("Cell interaction did not consume a powered cell; allowing a retry.");
        }
        return consumed;
    }

    private boolean powerUpGreatGuardian() {
        if (!Rs2Inventory.hasItem("guardian stone") || isInLargeMine() || isInHugeMine()) {
            return false;
        }

        Rs2NpcModel guardian = Microbot.getRs2NpcCache().query().withName("The great guardian").nearest();
        if (guardian == null) {
            return false;
        }

        if (!Rs2Npc.canWalkTo(guardian.getNpc(), 10)) {
            Rs2Walker.walkTo(guardian.getWorldLocation(), 8);
            return true;
        }

        state = GotrState.POWERING_UP;
        if (!guardian.click("power-up")) {
            return false;
        }

        log("Powering up the great guardian...");
        int stonesBefore = Rs2Inventory.count("guardian stone");
        Global.sleepUntil(Rs2Player::isAnimating, 3000);
        Global.sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        sleep(Rs2Random.randomGaussian(Rs2Random.between(1000, 2000), Rs2Random.between(100, 300)));

        // Only hold the tick if we actually consumed a stone; otherwise let the loop continue.
        return Rs2Inventory.count("guardian stone") < stonesBefore;
    }


    private void takeUnchargedCells() {

        if (!Rs2Inventory.hasItem("Uncharged cell")) {
            // Drop one guardian essence if inventory is full
            if (Rs2Inventory.isFull()) {
                if (Rs2Inventory.drop(ItemID.GUARDIAN_ESSENCE)) {
                    Microbot.log("Dropped one Guardian essence to make space for Uncharged cell");
                }
            }

            interactObject(ObjectID.UNCHARGED_CELLS_43732, "Take-10");
            log("Taking uncharged cells...");
            Rs2Player.waitForAnimation();
        }
    }

    private static boolean hasPoweredCell() {
        return countPoweredCells() > 0;
    }

    private static boolean hasWeakOpeningCell() {
        return Rs2Inventory.hasItem(ItemID.WEAK_CELL);
    }

    private static int countPoweredCells() {
        return CellType.PoweredCellList().stream()
            .mapToInt(Rs2Inventory::count)
            .sum();
    }

    private boolean ensureOpeningCellBeforeMining() {
        if (!needsOpeningCell) {
            return false;
        }
        if (hasWeakOpeningCell()) {
            needsOpeningCell = false;
            log("Weak opening cell secured; mining may begin.");
            return false;
        }
        if (isInLargeMine()) {
            leaveLargeMine();
            return true;
        }

        // Claim the tick even if the interaction cannot be dispatched. The verified pickup
        // remains outstanding and will be retried instead of allowing mining to start without it.
        takeWeakCell();
        return true;
    }

    private boolean takeWeakCell() {
        if (hasWeakOpeningCell() || Rs2Inventory.isFull()) {
            return false;
        }
        // A cell crafted after the close dialogue can linger briefly and is removed by round
        // cleanup. Do not mistake it for the table-sourced weak opening cell or begin mining;
        // hold the gate until it clears, then take and verify the weak cell.
        if (hasPoweredCell()) {
            return false;
        }

        int cellsBefore = countPoweredCells();
        if (!interactObject(ObjectID.WEAK_CELLS)) {
            return false;
        }

        log("Taking a weak cell for the opening barrier...");
        if (!Global.sleepUntil(() -> countPoweredCells() > cellsBefore, 4000)) {
            log("Weak cell interaction did not add a cell; allowing a retry.");
        }
        return true;
    }

    private boolean usePortal() {
        if (!isInHugeMine() && Microbot.getClient().hasHintArrow() && Rs2Inventory.count() < config.maxAmountEssence()) {
            if (leaveLargeMine()) return true;
            Rs2Walker.walkFastCanvas(Microbot.getClient().getHintArrowPoint());
            sleepUntil(Rs2Player::isMoving);
            Microbot.getRs2TileObjectCache().query().within(Microbot.getClient().getHintArrowPoint(), 0).interact();
            log("Found a portal spawn...interacting with it...");
            Rs2Player.waitForWalking();
            sleepUntil(() -> isInHugeMine());
            sleepUntil(() -> getGuardiansPower() > 0);
            return true;
        }
        return false;
    }

    private boolean depositRunesIntoPool() {
        if (!config.shouldDepositRunes()
                || !Rs2Inventory.hasItem(runeIds.stream().mapToInt(i -> i).toArray())
                || isInLargeMine() || isInHugeMine()) {
            return false;
        }
        if (Rs2Player.isMoving()) return true;
        // Walk-first interaction, but only claim the tick when the pool actually exists — otherwise
        // return false so we never lock the loop standing around holding runes. Dropped the old
        // !isFull / !optimizedEssenceLoop guards: they skipped exactly the end-of-round case, where
        // a full inventory of crafted runes would otherwise never be deposited and carried into the
        // next round.
        Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query().withId(ObjectID.DEPOSIT_POOL).nearest();
        if (pool == null) return false;
        if (interactObject(pool, null)) {
            log("Deposit runes into pool...");
            sleep(600, 2400);
        }
        return true;
    }

    private boolean enterAltar() {
        List<GuardianPortalCandidate> availableAltars = getAvailableAltarCandidates();
        GuardianPortalCandidate selectedAltar = availableAltars.stream().findFirst().orElse(null);
        if (selectedAltar != null && !Rs2Player.isMoving()) {
            GameObject availableAltar = selectedAltar.portal;
            GuardianPortalInfo portalInfo = selectedAltar.info;
            RuneType preferredRuneType = getPreferredRuneType(
                config.Mode(), elementalRewardPoints, catalyticRewardPoints);
            boolean preferredFallback = preferredRuneType != null
                && portalInfo.getRuneType() != preferredRuneType;
            String eligibleCandidates = availableAltars.stream()
                .map(candidate -> candidate.info.getName()
                    + "(" + candidate.info.getRuneType()
                    + ",RC" + candidate.info.getRequiredLevel()
                    + "," + candidate.info.getCellType()
                    + ",access=" + candidate.accessDescription()
                    + ",distance=" + candidate.distance + ")")
                .collect(Collectors.joining(", "));
            int talismansBefore = Rs2Inventory.count(portalInfo.getTalismanId());
            log("Entering " + portalInfo.getName() + " altar"
                + " | mode=" + config.Mode()
                + " | live energy=" + elementalRewardPoints + "/" + catalyticRewardPoints
                + " | preferred=" + (preferredRuneType == null ? "EITHER" : preferredRuneType)
                + " | selected=" + portalInfo.getRuneType()
                + " | fallback=" + preferredFallback
                + " | access=" + selectedAltar.accessDescription()
                + " | eligible=[" + eligibleCandidates + "]"
                + " | RC=" + Microbot.getClient().getBoostedSkillLevel(Skill.RUNECRAFT)
                + "/" + portalInfo.getRequiredLevel()
                + " | cell=" + portalInfo.getCellType()
                + " | talismans=" + talismansBefore);
            if (!Rs2GameObject.interact(availableAltar, "Enter")) {
                log("Could not dispatch Enter on " + portalInfo.getName() + " guardian.");
                return false;
            }
            state = GotrState.ENTER_ALTAR;
            boolean entryWaitCompleted = Global.sleepUntil(() ->
                hasAltarTransitionStarted(portalInfo, talismansBefore)
                    || !isGuardianPortalAccessible(availableAltar.getId(), portalInfo.getTalismanId()),
                ALTAR_ENTRY_START_TIMEOUT_MS);
            boolean transitionStarted = hasAltarTransitionStarted(portalInfo, talismansBefore);
            boolean portalExpired = entryWaitCompleted
                && !transitionStarted
                && !isGuardianPortalAccessible(
                    availableAltar.getId(), portalInfo.getTalismanId());
            boolean entered = transitionStarted && Global.sleepUntil(() ->
                Microbot.getClient().getGameState() == GameState.LOGGED_IN
                    && !isInMainRegion(),
                ALTAR_LOAD_TIMEOUT_MS);
            int talismansAfter = Rs2Inventory.count(portalInfo.getTalismanId());
            if (portalExpired) {
                log("Portal expired before entry for " + portalInfo.getName()
                    + "; reselecting immediately"
                    + " | access=" + selectedAltar.accessDescription()
                    + " | talismans=" + talismansBefore + "->" + talismansAfter);
            } else if (!transitionStarted) {
                log("Entry did not start for " + portalInfo.getName()
                    + " | access=" + selectedAltar.accessDescription()
                    + " | talismans=" + talismansBefore + "->" + talismansAfter);
            } else if (!entered) {
                log("Altar load verification timed out for " + portalInfo.getName()
                    + " | access=" + selectedAltar.accessDescription()
                    + " | talismans=" + talismansBefore + "->" + talismansAfter);
            } else {
                log("Entered " + portalInfo.getName()
                    + " | access=" + selectedAltar.accessDescription()
                    + " | talismans=" + talismansBefore + "->" + talismansAfter);
                sleep(Rs2Random.randomGaussian(1000, 300));
            }

            return true;
        }
        return false;
    }

    private boolean craftGuardianEssences() {
        if (interactObject(ObjectID.WORKBENCH_43754)) {
            state = GotrState.CRAFT_GUARDIAN_ESSENCE;
            sleep(Rs2Random.randomGaussian(Rs2Random.between(600, 900), Rs2Random.between(150, 300)));
            log("Crafting guardian essences...");
            return true;
        }
       return false;
    }

    private boolean leaveLargeMine() {
        if (isInLargeMine()) {
            interactObject(ObjectID.RUBBLE_43726);
            Rs2Player.waitForAnimation();
            log("Leaving large mine...");
            state = GotrState.LEAVING_LARGE_MINE;
            return true;
        }
        return false;
    }

    private boolean fillPouches() {
        if (Rs2Inventory.isFull() && Rs2Inventory.anyPouchEmpty() && getGuardiansPower() < 90) {
            Rs2Inventory.fillPouches();
            sleep(Rs2Random.randomGaussian(600, 300));
            return true;
        }
        return false;
    }

    private boolean isOutOfFragments() {
        if ((!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS) && !Rs2Inventory.isFull()) || (getTimeSincePortal() > 85 && !Rs2Inventory.hasItem(GUARDIAN_ESSENCE))) {
            shouldMineGuardianRemains = true;
            if(!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS))
                log("Memorize that we no longer have guardian fragments...");

            return true;
        }
        shouldMineGuardianRemains = false;
        return false;
    }

    private boolean craftRunes() {
        if (!isInMainRegion() && isInMiniGame()) {
            Rs2TileObjectModel rcAltar = findRcAltar();
            if (rcAltar != null) {
                if (Rs2Player.isMoving()) return true;
                if (Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
                    state = GotrState.CRAFTING_RUNES;
                    optimizedEssenceLoop = false;
                    int essenceBefore = Rs2Inventory.count(GUARDIAN_ESSENCE);
                    if (interactObject(rcAltar, null)) {
                        log("Crafting " + essenceBefore + " inventory essence on altar "
                            + rcAltar.getId() + " before emptying pouches.");
                        Global.sleepUntil(
                            () -> Rs2Inventory.count(GUARDIAN_ESSENCE) < essenceBefore,
                            5000);
                        sleep(Rs2Random.randomGaussian(350, 150));
                    }
                } else if (Rs2Inventory.anyPouchFull() && !Rs2Inventory.isFull()) {
                    // Craft the carried essence first. A consumed portal talisman leaves one free
                    // slot; emptying a pouch into that slot before the first craft only extracts a
                    // single essence and repeats the full-inventory cycle unnecessarily.
                    Rs2Inventory.emptyPouches();
                    Rs2Inventory.waitForInventoryChanges(5000);
                    sleep(Rs2Random.randomGaussian(350, 150));
                } else if (!Rs2Player.isMoving()) {
                    state = GotrState.LEAVING_ALTAR;
                    Rs2TileObjectModel rcPortal = findPortalToLeaveAltar();
                    if (interactObject(rcPortal, null)) {
                        log("Leaving the altar...");
                        sleepUntilTrue(GotrScript::isInMainRegion,100,10000);
                        sleep(Rs2Random.randomGaussian(750, 150));
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean waitForMinigameToStart() {
        if (!isInMainRegion()) {
            Rs2TileObjectModel rcPortal = findPortalToLeaveAltar();
            if (rcPortal != null && interactObject(rcPortal, null)) {
                state = GotrState.LEAVING_ALTAR;
                return true;
            }
        }
        resetPlugin();
        if (state != GotrState.WAITING) {
            state = GotrState.WAITING;
            log("Make sure to start the script near the minigame barrier.");
            interactObject(ObjectID.BARRIER_43849, "Peek");
        }
        return state == GotrState.WAITING;
    }

    private static boolean enterMinigame() {
        if (interactObject(ObjectID.BARRIER_43700, "quick-pass")) {
            Rs2Player.waitForWalking();
            state = GotrState.ENTER_GAME;
            GotrScript.shouldMineGuardianRemains = true;
            log("Entering game...");
            return true;
        }
        return false;
    }

    private void checkPouches(boolean anyPouchUnknown, int mean, int stddev) {
        if (anyPouchUnknown) {
            Rs2Inventory.checkPouches();
            sleep(Rs2Random.randomGaussian(mean, stddev));
        }
    }

    private boolean mineHugeGuardianRemain() {
        if (isInHugeMine()) {
            if (getGuardiansPower() == 0) {
                repairPouches();
                leaveHugeMine();
                optimizedEssenceLoop = false;
                return false;
            }
            if (!Rs2Inventory.isFull()) {
                if (!Rs2Player.isAnimating()) {
                    interactObject(ObjectID.HUGE_GUARDIAN_REMAINS);
                    Rs2Player.waitForAnimation();
                    if (!Rs2Player.isAnimating())
                        interactObject(ObjectID.HUGE_GUARDIAN_REMAINS);
                }
            } else {
                if (Rs2Inventory.allPouchesFull()) {
                    if(Rs2Inventory.hasItem("guardian stone"))
                        optimizedEssenceLoop = true;
                    leaveHugeMine();
                } else {
                    Rs2Inventory.fillPouches();
                    sleep(Rs2Random.randomGaussian(Rs2Random.between(600, 1200), Rs2Random.between(100, 300)));
                    if (!Rs2Inventory.isFull()) {
                        interactObject(ObjectID.HUGE_GUARDIAN_REMAINS);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void mineGuardianRemains() {
        if (Microbot.getClient().hasHintArrow())
            return;
        if (Rs2Inventory.isFull()) {
            shouldMineGuardianRemains = false;
            return;
        }
        state = GotrState.MINE_LARGE_GUARDIAN_REMAINS;
        if (isInHugeMine()) {
            leaveHugeMine();
            return;
        }
        if (Rs2Player.getSkillRequirement(Skill.AGILITY, 56) && getTimeSincePortal() < 85 && !Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
            if (!isInLargeMine() && !isInHugeMine() && (!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS) || getStartTimer() == -1)) {
                WorldPoint largeMineApproach = new WorldPoint(3632, 9503, 0);
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation == null || playerLocation.distanceTo(largeMineApproach) > 20) {
                    // This is a short local arena move. The web walker can classify active
                    // guardian portals as route doors and enter an altar during round-end prep.
                    Rs2Walker.walkFastCanvas(largeMineApproach);
                    return;
                }

                log("Traveling to large mine...");
                interactObject(ObjectID.RUBBLE_43724);
                if (sleepUntil(Rs2Player::isAnimating)) {
                    sleepUntil(GotrScript::isInLargeMine);
                    if (isInLargeMine()) {
                        sleep(Rs2Random.randomGaussian(Rs2Random.between(2000, 2400), Rs2Random.between(100, 300)));
                        log("Interacting with large guardian remains...");
                        interactObject(ObjectID.LARGE_GUARDIAN_REMAINS);
                        sleepGaussian(1200, 150);
                    }
                }
                sleepGaussian(600, 150);
            } else {
                if (!Rs2Player.isAnimating() && getStartTimer() != -1) {
                    if (Rs2Equipment.isWearing("dragon pickaxe")) {
                        Rs2Combat.setSpecState(true, 1000);
                    }
                    checkPouches(Rs2Random.between(1, 20) == 2, Rs2Random.between(100, 600), Rs2Random.between(100, 300));

                    repairPouches();
                    interactObject(ObjectID.LARGE_GUARDIAN_REMAINS);
                    sleepGaussian(1200, 150);
                }
            }
        } else {
            //guardian parts
            if (!Rs2Player.isAnimating() && getStartTimer() != -1) {
                if(isInLargeMine()) {
                    leaveLargeMine();
                }
                if (Rs2Equipment.isWearing("dragon pickaxe")) {
                    Rs2Combat.setSpecState(true, 1000);
                }
                repairPouches();
                interactObject(ObjectID.GUARDIAN_PARTS_43716);
                sleepGaussian(1200, 150);
                // we can assume that if the player is mining within the startTimer range, he will get enough guardian remains for the game
                shouldMineGuardianRemains = false;
            }
        }
    }

    private void leaveHugeMine() {
        interactObject(38044);
        log("Leave huge mine...");
        Global.sleepUntil(() -> !isInHugeMine(), 5000);

    }

    private static boolean repairPouches() {
        if (!useNpcContact) {
            repairWithCordelia();
            return true;
        }
        if (Rs2Inventory.hasDegradedPouch()) {
            return Rs2Magic.repairPouchesWithLunar();
        }
        return false;
    }

    /**
     * Repair pouch by talking to cordelia
     * make sure to have the repair unlocked for 25 pearls
     */
    private static void repairWithCordelia() {
        if (!Rs2Inventory.hasDegradedPouch()) return;
        if (!Rs2Inventory.hasItem(ItemID.ABYSSAL_PEARLS)) return;
        Rs2NpcModel pouchRepairNpc = Microbot.getRs2NpcCache().query().withId(NpcID.APPRENTICE_CORDELIA_12180).nearest();
        if (pouchRepairNpc == null) return;
        if (!Rs2Npc.hasAction(pouchRepairNpc.getId(), "Repair")) return;
        if (!Rs2Npc.canWalkTo(pouchRepairNpc.getNpc(), 10)) return;
        if (!pouchRepairNpc.click("Repair")) return;

        Microbot.log("Repairing pouches...");

        Global.sleepUntil(() -> {
            Rs2Dialogue.clickContinue();
            return !Rs2Inventory.hasDegradedPouch();
        }, 10000);

    }

    @Override
    public void shutdown() {
        state = null;
        super.shutdown();
    }

    public static boolean isOutsideBarrier() {
        int outsideBarrierY = 9482;
        return Rs2Player.getWorldLocation().getY() <= outsideBarrierY
                && Rs2Player.getWorldLocation().getRegionID() == 14484;
    }

    public  static boolean isInLargeMine() {
        int largeMineX = 3637;
        return Rs2Player.getWorldLocation().getRegionID() == 14484
                && Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().getX()) >= largeMineX;
    }

    public  boolean isInHugeMine() {
        int hugeMineX = 3594;
        return Rs2Player.getWorldLocation().getRegionID() == 14484
                && Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().getX()) <= hugeMineX;
    }

    public static boolean isGuardianPortal(GameObject gameObject) {
        return guardianPortalInfo.containsKey(gameObject.getId());
    }

    public ItemManager getItemManager() {
        return Microbot.getItemManager();
    }

    public boolean isInMiniGame() {
        int parentWidgetId = 48889857;
        Widget elementalRuneWidget = Microbot.getClient().getWidget(parentWidgetId);
        return elementalRuneWidget != null;
    }

    public static boolean isInMainRegion() {
        if (Microbot.getClient().getLocalPlayer() == null) {
            return false;
        }
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null && playerLocation.getRegionID() == 14484;
    }

    public static int getStartTimer() {
        Widget timerWidget = Rs2Widget.getWidget(48889861);
        if (timerWidget != null) {
            String timer = timerWidget.getText();
            if (timer == null) return -1;
            // Split the timer string into minutes and seconds
            String[] timeParts = timer.split(":");

            // Ensure there are two parts (minutes and seconds)
            if (timeParts.length == 2) {
                int minutes = Integer.parseInt(timeParts[0]);
                int seconds = Integer.parseInt(timeParts[1]);

                // Convert the timer to total seconds
                int totalSeconds = (minutes * 60) + seconds;
                return totalSeconds;
            }
        }
        return -1;
    }

    public static int getTimeSincePortal() {
        if(getStartTimer() == -1) {
            return -1;
        }
        int firstPortalTimeAdjustment = isFirstPortal ? 40 : 0;
        return timeSincePortal.map(instant -> (int) ChronoUnit.SECONDS.between(instant, Instant.now())-firstPortalTimeAdjustment).orElse(-1);

    }

    public static List<GameObject> getAvailableAltars() {
        return getAvailableAltarCandidates().stream()
            .map(candidate -> candidate.portal)
            .collect(Collectors.toList());
    }

    private static List<GuardianPortalCandidate> getAvailableAltarCandidates() {
        int elementalPoints = Microbot.getVarbitValue(VarbitID.GOTR_ELEMENTAL_EARNED_THIS_GAME);
        int catalyticPoints = Microbot.getVarbitValue(VarbitID.GOTR_CATALYTIC_EARNED_THIS_GAME);
        elementalRewardPoints = elementalPoints;
        catalyticRewardPoints = catalyticPoints;
        int runecraftLevel = Microbot.getClient().getBoostedSkillLevel(Skill.RUNECRAFT);
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        List<GuardianPortalCandidate> availableAltars = Rs2GameObject.getGameObjects().stream()
            .filter(portal -> guardianPortalInfo.containsKey(portal.getId()))
            .map(portal -> {
                GuardianPortalInfo portalInfo = guardianPortalInfo.get(portal.getId());
                boolean active = isActiveGuardianPortal(portal);
                boolean hasTalisman = Rs2Inventory.hasItem(portalInfo.getTalismanId());
                WorldPoint portalLocation = portal.getWorldLocation();
                int distance = playerLocation == null || portalLocation == null
                    ? Integer.MAX_VALUE
                    : playerLocation.distanceTo(portalLocation);
                return new GuardianPortalCandidate(
                    portal, portalInfo, active, hasTalisman, distance);
            })
            .filter(candidate ->
                candidate.info.getRequiredLevel() <= runecraftLevel
                    && candidate.info.getQuestState() == QuestState.FINISHED
                    && (candidate.active || candidate.hasTalisman))
            .collect(Collectors.toList());

        RuneType preferredRuneType = getPreferredRuneType(
            config.Mode(), elementalPoints, catalyticPoints);
        Comparator<GuardianPortalCandidate> preferredRuneFirst = Comparator.comparingInt(
            candidate -> preferredRuneType == null
                || candidate.info.getRuneType() == preferredRuneType ? 0 : 1);
        Comparator<GuardianPortalCandidate> strongestCellFirst = Comparator
            .comparingInt((GuardianPortalCandidate candidate) ->
                candidate.info.getCellType().ordinal())
            .reversed();
        Comparator<GuardianPortalCandidate> talismanAccessFirst = Comparator.comparingInt(
            candidate -> candidate.requiresTalisman() ? 0 : 1);

        // Eligibility is applied before preference. A low-level account therefore falls back to
        // the best portal it can actually enter when the lower-energy side is unavailable. During
        // the last part of a round, consume a useful talisman on the preferred side before cell
        // strength; otherwise retain it until it improves or ties the normal altar choice.
        Comparator<GuardianPortalCandidate> comparator;
        if (config.Mode() == Mode.POINTS) {
            comparator = strongestCellFirst
                .thenComparing(preferredRuneFirst)
                .thenComparing(talismanAccessFirst);
        } else if (getGuardiansPower() >= TALISMAN_ENDGAME_POWER) {
            comparator = preferredRuneFirst
                .thenComparing(talismanAccessFirst)
                .thenComparing(strongestCellFirst);
        } else {
            comparator = preferredRuneFirst
                .thenComparing(strongestCellFirst)
                .thenComparing(talismanAccessFirst);
        }

        return availableAltars.stream()
            .sorted(comparator
                .thenComparingInt(candidate -> candidate.distance)
                .thenComparingInt(candidate -> candidate.portal.getId()))
            .collect(Collectors.toList());
    }

    static RuneType getPreferredRuneType(Mode mode, int elementalPoints, int catalyticPoints) {
        if (mode == Mode.ELEMENTAL) {
            return RuneType.ELEMENTAL;
        }
        if (mode == Mode.CATALYTIC) {
            return RuneType.CATALYTIC;
        }
        if (elementalPoints < catalyticPoints) {
            return RuneType.ELEMENTAL;
        }
        if (catalyticPoints < elementalPoints) {
            return RuneType.CATALYTIC;
        }
        return null;
    }

    private static boolean isActiveGuardianPortal(GameObject portal) {
        if (!(portal.getRenderable() instanceof DynamicObject)) {
            return false;
        }
        DynamicObject dynamicObject = (DynamicObject) portal.getRenderable();
        return dynamicObject.getAnimation() != null
            && dynamicObject.getAnimation().getId() == ACTIVE_GUARDIAN_PORTAL_ANIMATION;
    }

    private static boolean hasAltarTransitionStarted(
        GuardianPortalInfo portalInfo,
        int talismansBefore) {
        return Microbot.getClient().getGameState() == GameState.LOADING
            || !isInMainRegion()
            || Rs2Inventory.count(portalInfo.getTalismanId()) < talismansBefore;
    }

    private static boolean isGuardianPortalAccessible(int portalId, int talismanId) {
        if (Rs2Inventory.hasItem(talismanId)) {
            return true;
        }
        return Rs2GameObject.getGameObjects().stream()
            .filter(portal -> portal.getId() == portalId)
            .anyMatch(GotrScript::isActiveGuardianPortal);
    }

    private static int getGuardiansPower() {
        return readGuardiansPower().orElse(0);
    }

    private static OptionalInt readGuardiansPower() {
        Widget pWidget = Rs2Widget.getWidget(48889874);
        if (pWidget == null) {
            return OptionalInt.empty();
        }

        Matcher matcher = Pattern.compile("(\\d+)%").matcher(pWidget.getText());
        return matcher.find()
            ? OptionalInt.of(Integer.parseInt(matcher.group(1)))
            : OptionalInt.empty();
    }

    private void updateRoundCompletionState() {
        OptionalInt observedPower = readGuardiansPower();
        if (!observedPower.isPresent()) {
            return;
        }

        int currentPower = observedPower.getAsInt();
        // A plugin/client restart can restore the script in the middle of a live round without
        // replaying the "rift becomes active" chat event. In that case the default opening-cell
        // gate must not send the player back to the weak-cell table. The same 0 -> positive
        // transition closes the prep window if the start chat event was missed.
        if (currentPower > PRE_ROUND_GUARDIAN_POWER
            && (lastObservedGuardiansPower < 0 || lastObservedGuardiansPower == 0)
            && needsOpeningCell) {
            needsOpeningCell = false;
            log("Active round detected at " + currentPower
                + "%; opening-cell preparation window closed.");
        }
        // 100% is still a live contribution window. Only the subsequent reset to 0 marks the
        // round boundary when the post-rift chat event was not observed.
        if (lastObservedGuardiansPower >= 100 && currentPower == 0) {
            needsOpeningCell = true;
            log("Guardian power reset to 0%; opening cell required before the next mining pass.");
        }
        lastObservedGuardiansPower = currentPower;
    }

    public static void resetPlugin() {
        guardians.clear();
        activeGuardianPortals.clear();
        greatGuardian = null;
        Microbot.getClient().clearHintArrow();
    }

    /**
     * Walk-first object interaction.
     *
     * <p>The migrated Queryable API ({@code cache.query().interact(id, action)}) resolves
     * {@code nearestReachable()} and clicks at the player's current tile — it does NOT walk into
     * range. Legacy {@code Rs2GameObject.interact(id, action)} auto-walked when the target was
     * more than 51 tiles away. After the query-API migration GOTR lost that auto-walk, so any
     * interaction issued while out of range silently no-ops every tick and the bot just stands
     * there (see docs/PLUGIN_DEBUGGING_NOTES.md §3). This restores the legacy behaviour: web-walk
     * when far, hand off to the game's click-to-walk once close.
     */
    private static boolean interactObject(int id) {
        return interactObject(id, null);
    }

    private static boolean interactObject(int id, String action) {
        return interactObject(Microbot.getRs2TileObjectCache().query().withId(id).nearest(), action);
    }

    private static boolean interactObject(Rs2TileObjectModel obj, String action) {
        if (obj == null) return false;
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        WorldPoint objLoc = obj.getWorldLocation();
        if (playerLoc != null && objLoc != null && playerLoc.distanceTo(objLoc) > 51) {
            log("Object " + obj.getId() + " is " + playerLoc.distanceTo(objLoc) + " tiles away, walking into range...");
            Rs2Walker.walkTo(objLoc);
            return false;
        }
        // In click range: drop any lingering web-walk target so the game's click-to-walk drives
        // the final approach, then interact.
        Rs2Walker.setTarget(null);
        return (action == null || action.isEmpty()) ? obj.click() : obj.click(action);
    }

    public static Rs2TileObjectModel findRcAltar() {
        return Microbot.getRs2TileObjectCache().query().withIds(
                ObjectID.ALTAR_34760, ObjectID.ALTAR_34761, ObjectID.ALTAR_34762, ObjectID.ALTAR_34763, ObjectID.ALTAR_34764,
                ObjectID.ALTAR_34765, ObjectID.ALTAR_34766, ObjectID.ALTAR_34767, ObjectID.ALTAR_34768, ObjectID.ALTAR_34769, ObjectID.ALTAR_34770,
                ObjectID.ALTAR_34771, ObjectID.ALTAR_34772, ObjectID.ALTAR_43479).nearest();
    }

    public static Rs2TileObjectModel findPortalToLeaveAltar() {
        return Microbot.getRs2TileObjectCache().query().withIds(
                ObjectID.PORTAL_34748, ObjectID.PORTAL_34749, ObjectID.PORTAL_34750, ObjectID.PORTAL_34751, ObjectID.PORTAL_34752,
                ObjectID.PORTAL_34753, ObjectID.PORTAL_34754, ObjectID.PORTAL_34755, ObjectID.PORTAL_34756, ObjectID.PORTAL_34757, ObjectID.PORTAL_34758,
                ObjectID.PORTAL_34758, ObjectID.PORTAL_34759, ObjectID.PORTAL_43478).nearest();
    }
    public static boolean leaveMinigame() {
        GotrScript.isInMiniGame = !isOutsideBarrier() && isInMainRegion(); 
        if (!isInMiniGame) {
            return true;    // Already outside the minigame, successfully left     
        }
        if(isInLargeMine()) {
            interactObject(ObjectID.RUBBLE_43726);
            Rs2Player.waitForAnimation();
            sleepUntil(()-> !isInLargeMine());
            if (isInLargeMine()){
                log("Failed to leave large mine, retrying...");
                return false;
            }

        }
        interactObject(ObjectID.BARRIER_43700, "quick-pass");
        Rs2Player.waitForWalking();
        sleepUntil( ()-> {return !(!isOutsideBarrier() && isInMainRegion());}, 200);
        GotrScript.isInMiniGame  = !isOutsideBarrier() && isInMainRegion();
        return !GotrScript.isInMiniGame;// Successfully left the minigame
    }
}
