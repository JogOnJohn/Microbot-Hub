package net.runelite.client.plugins.microbot.housetab;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.MenuEntry;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.housetab.enums.HOUSETABS_CONFIG;
import net.runelite.client.plugins.microbot.housetab.enums.HouseTabState;
import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import net.runelite.client.plugins.microbot.housetab.enums.TabletQuantityMode;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.awt.event.KeyEvent;

@Slf4j
public class HouseTabScript extends Script {
    /*
     * This script looks large because a "simple" house-tab loop is really many
     * smaller problems glued together:
     *
     * 1. Make sure we are logged in and on the right world.
     * 2. Decide which tablet to make and whether our loadout can make it.
     * 3. Get supplies from the bank or Grand Exchange area when needed.
     * 4. Return to Rimmington, unnote clay, and enter a usable house.
     * 5. Find the correct lectern, select the right widget, and craft tablets.
     * 6. Leave/re-enter houses and recover when hosted houses are bad/offline.
     *
     * The main loop is a state machine: it reads a snapshot, chooses one small
     * action, then returns so the next scheduler tick can observe the result.
     */

    // Object/widget ids are stable ids from the game client. They let the script
    // distinguish the Rimmington portal, inside-POH exit portal, advertisement
    // board, jewellery box, and each supported lectern type.
    private final int RIMMINGTON_PORTAL_OBJECT = 15478;
    private final int HOUSE_PORTAL_OBJECT = 4525;

    private final int HOUSE_ADVERTISEMENT_OBJECT = 29091;
    private final int ORNATE_JEWELLERY_BOX_OBJECT = 29156;
    private final List<Integer> GRAND_EXCHANGE_BOOTH_OBJECTS = List.of(10060, 10061, 30389);

    private final int HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE = 3407881;
    private final int ORNATE_JEWELLERY_BOX_GE_WIDGET = 0x024e_0006;
    private final int MAHOGANY_EAGLE_LECTERN_OBJECT = 13647;
    private final int MAHOGANY_DEMON_LECTERN_OBJECT = 13648;
    private final int MARBLE_LECTERN_OBJECT = 37349;

    private final Map<Integer, Integer> lecternToHouseTabButton = Map.of(
            MAHOGANY_EAGLE_LECTERN_OBJECT, 26411031,
            MAHOGANY_DEMON_LECTERN_OBJECT, 26411033,
            MARBLE_LECTERN_OBJECT, 26411033
    );

    // Constructor inputs: legacy route selector plus fallback friend-house names.
    private final HOUSETABS_CONFIG houseTabConfig;
    private final String[] playerHouses;

    // The scheduler runs the bot loop off the RuneLite client thread. Each loop
    // should do a small amount of work, then let the next tick observe results.
    private final ScheduledExecutorService scheduledExecutorService;

    // Run plan and progress tracking. These values feed the overlay and help
    // the script detect whether crafting/banking changed anything.
    private int lecternTabletWidgetId = 26411033;
    private HouseTablet selectedTablet = HouseTablet.TELEPORT_TO_HOUSE;
    private String stopReason = "";
    private String planSummary = "";
    private int startMagicXp = -1;
    private int startMagicLevel = -1;
    private int tabletsMade = 0;
    private int lastKnownOutputCount = 0;
    private boolean dumpedCurrentTabletInterface = false;
    private HouseTablet dumpedTabletInterfaceFor = null;
    private boolean skipVisitLastHouse = false;
    private int advertisedHouseSkipCount = 0;
    private boolean enteredAdvertisedHouse = false;
    private boolean hasSelectedAdvertisedHouse = false;
    private boolean currentHouseEnteredViaVisitLast = false;
    private TabletQuantityMode confirmedQuantityMode = null;
    private HouseTablet lastPreparedTablet = null;
    private int debugLoopCount = 0;

    // Pending flags are one-shot action guards. They prevent the scheduler from
    // issuing the same click every tick while the game client is still responding.
    private boolean phialsUnnotePending = false;
    private long phialsUnnoteAttemptedAt = 0;
    private boolean lecternStudyPending = false;
    private long lecternStudyAttemptedAt = 0;
    private boolean leaveHousePending = false;
    private long leaveHouseAttemptedAt = 0;

    // Hosted-house detection can be awkward: the POH scene is instanced, but
    // objects may stream in late. This assumption is set after strong evidence
    // that we entered a house, then cleared when normal-world evidence appears.
    private boolean assumeInsidePlayerHouse = false;

    // Timestamps are soft timeouts/cooldowns. They prevent repeated clicks while
    // a previous click, teleport, world hop, or UI action is still resolving.
    private long lastLecternCraftAttemptAt = 0;
    private long lastAdvertisementViewAttemptAt = 0;
    private long lastProgressivePrepLogAt = 0;
    private long lastWorldHopAttemptAt = 0;
    private long lastInsideHouseDetectedAt = 0;
    private int worldHopAttempts = 0;
    private int lastObservedMagicXp = -1;
    private int lastObservedUnnotedClay = -1;
    private long lastCraftProgressAt = 0;

    // Advertisement-board house rotation. Known-good hosts can be retried; bad
    // hosts are skipped for this run so the bot does not loop in the same house.
    private String currentAdvertisedHouseName = "";
    private final Set<String> blacklistedAdvertisedHouses = new HashSet<>();
    private final Set<String> knownGoodAdvertisedHouses = new HashSet<>();
    private long lastCraftGateLogAt = 0;
    private String lastCraftGateLogReason = "";
    private long lastLecternScrollAttemptAt = 0;
    private long lastAntibanActionAt = 0;
    private long nextCraftingAntibanAt = 0;

    // Hosted houses can stream objects in slowly. A missing lectern sample is
    // only trusted after several checks, otherwise we blacklist good hosts.
    private long noCompatibleLecternDetectedAt = 0;
    private int noCompatibleLecternSamples = 0;
    private HouseTabState currentState = HouseTabState.STARTING;
    private long lastStateChangedAt = System.currentTimeMillis();
    private String lastTransitionReason = "initialized";
    private HouseTabSnapshot lastSnapshot = null;
    private long lastDiagnosticDumpAt = 0;
    private String lastRecoveryReason = "";
    private String lastMaterialSummary = "";

    /*
     * Material helpers keep item-id details in one place. The game has separate
     * item ids for unnoted and noted soft clay, so the script constantly asks
     * "can I craft now?" and "can I unnote more?" separately.
     */
    private boolean hasSoftClay() {
        return Rs2Inventory.hasItem(1761);
    }

    private int unnotedSoftClayCount() {
        return Rs2Inventory.count(1761);
    }

    private boolean hasSoftClayNoted() {
        return Rs2Inventory.hasItem(1762);
    }

    private int notedSoftClayCount() {
        return Rs2Inventory.count(1762);
    }

    private boolean hasAnySoftClay() {
        return hasSoftClay() || hasSoftClayNoted();
    }

    private String materialDebug() {
        return "unnotedClay=" + unnotedSoftClayCount()
                + " notedClay=" + notedSoftClayCount()
                + " runes=" + hasRequiredRunes()
                + " staff=" + hasStaffFor(selectedTablet);
    }

    private String fastMaterialDebug() {
        return "unnotedClay=" + unnotedSoftClayCount()
                + " notedClay=" + notedSoftClayCount()
                + " staff=" + hasStaffFor(selectedTablet)
                + " lastPrepared=" + (lastPreparedTablet == null ? "none" : lastPreparedTablet.getName());
    }

    private boolean hasStaffFor(HouseTablet tablet) {
        // A preferred staff is useful only when it covers every elemental rune
        // that the selected tablet can get from a staff. Law runes still come
        // from inventory or rune pouch.
        if (tablet.getPreferredStaffRunes().isEmpty()) return false;
        int requiredCoverage = tablet.getPreferredStaffRunes().size();
        return Arrays.stream(Rs2Staff.values())
                .filter(staff -> staffCoverage(staff, tablet) == requiredCoverage)
                .map(Rs2Staff::getItemID)
                .anyMatch(id -> Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id));
    }

    private int staffCoverage(Rs2Staff staff, HouseTablet tablet) {
        return (int) tablet.getPreferredStaffRunes().stream()
                .filter(staff.getRunes()::contains)
                .count();
    }

    private List<Rs2Staff> rankedStavesFor(HouseTablet tablet, boolean allowPartial) {
        // Sort strongest coverage first. This lets progressive mode equip the
        // best available staff before falling back to loose runes when allowed.
        int requiredCoverage = tablet.getPreferredStaffRunes().size();
        return Arrays.stream(Rs2Staff.values())
                .filter(staff -> staff != Rs2Staff.NONE)
                .filter(staff -> {
                    int coverage = staffCoverage(staff, tablet);
                    return coverage > 0 && (allowPartial || coverage == requiredCoverage);
                })
                .sorted(Comparator
                        .comparingInt((Rs2Staff staff) -> staffCoverage(staff, tablet))
                        .reversed()
                        .thenComparing(Rs2Staff::name))
                .collect(Collectors.toList());
    }

    private boolean hasStaffAvailable(Rs2Staff staff) {
        int itemId = staff.getItemID();
        return Rs2Equipment.isWearing(itemId)
                || Rs2Inventory.hasItem(itemId)
                || (Rs2Bank.isOpen() && hasBankStaffItem(itemId));
    }

    private boolean hasBankStaffItem(int itemId) {
        // The bank cache is faster when available, but direct hasBankItem is a
        // useful fallback if the cache read throws or is not populated.
        if (!Rs2Bank.isOpen()) {
            return false;
        }
        try {
            return Rs2Bank.bankItems().stream()
                    .anyMatch(item -> item.getId() == itemId && item.getQuantity() >= 1);
        } catch (Exception ex) {
            return Rs2Bank.hasBankItem(itemId, 1);
        }
    }

    private String bankStaffDebug(int itemId) {
        if (!Rs2Bank.isOpen()) {
            return "bankClosed";
        }
        try {
            return Rs2Bank.bankItems().stream()
                    .filter(item -> item.getId() == itemId)
                    .findFirst()
                    .map(item -> "bankCache=true qty=" + item.getQuantity() + " name=" + item.getName())
                    .orElse("bankCache=false hasBankItem=" + Rs2Bank.hasBankItem(itemId, 1));
        } catch (Exception ex) {
            return "bankError=" + ex.getClass().getSimpleName() + " hasBankItem=" + Rs2Bank.hasBankItem(itemId, 1);
        }
    }

    private Rs2Staff equippedStaffFor(HouseTablet tablet) {
        return rankedStavesFor(tablet, false).stream()
                .filter(staff -> Rs2Equipment.isWearing(staff.getItemID()))
                .findFirst()
                .orElse(Rs2Staff.NONE);
    }

    private Rs2Staff bestAvailableStaffFor(HouseTablet tablet, boolean allowPartial) {
        return rankedStavesFor(tablet, allowPartial).stream()
                .filter(this::hasStaffAvailable)
                .findFirst()
                .orElse(Rs2Staff.NONE);
    }

    private boolean isBestAvailableStaffEquipped(HouseTablet tablet, boolean allowPartial) {
        Rs2Staff bestStaff = bestAvailableStaffFor(tablet, allowPartial);
        return bestStaff != Rs2Staff.NONE && Rs2Equipment.isWearing(bestStaff.getItemID());
    }

    private void stop(String reason) {
        // Stop is a terminal state for this script instance. The plugin will not
        // restart the same stopped script until it is toggled/recreated.
        stopReason = reason;
        transitionTo(HouseTabState.STOPPED, reason);
        Microbot.status = reason;
        Microbot.log("HouseTab stopped: " + reason);
        shutdown();
    }

    public void stopFromPlugin(String reason) {
        stop(reason);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public void handlePlayerHouseOffline(boolean useAdvertisementBoard) {
        // RuneLite reports offline hosted houses through chat. If the ad board
        // is allowed, this is recoverable: skip visit-last and choose a fresh host.
        if (!useAdvertisementBoard) {
            stop("Configured player house is offline");
            return;
        }
        skipVisitLastHouse = true;
        Microbot.status = "Last house offline; selecting a fresh advertised house";
        Microbot.log("HouseTab: last house offline, falling back to house advertisement board.");
    }

    private void updatePlanSummary(HouseTabConfig config) {
        // Short overlay text explaining what the current run intends to make.
        planSummary = (config.progressive() ? "Progressive: " : "Tablet: ")
                + selectedTablet.getName()
                + " | XP " + selectedTablet.getMagicXp()
                + " | Qty " + config.quantityMode();
    }

    private void updateTabletCount() {
        // Count output growth instead of assuming every click creates a tablet.
        // This keeps the overlay accurate when the client lags or a craft fails.
        int current = Rs2Inventory.count(selectedTablet.getItemId());
        if (current > lastKnownOutputCount) {
            tabletsMade += current - lastKnownOutputCount;
        }
        lastKnownOutputCount = current;
    }

    private void resetTracking() {
        // A new script run starts with clean counters and pending-action flags.
        // Forgetting these flags is a common source of "it immediately retries
        // the previous action" bugs after plugin restart.
        setupAntiban();
        startMagicXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        startMagicLevel = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
        tabletsMade = 0;
        lastKnownOutputCount = Rs2Inventory.count(selectedTablet.getItemId());
        stopReason = "";
        dumpedCurrentTabletInterface = false;
        dumpedTabletInterfaceFor = null;
        confirmedQuantityMode = null;
        lastPreparedTablet = null;
        phialsUnnotePending = false;
        phialsUnnoteAttemptedAt = 0;
        lecternStudyPending = false;
        lecternStudyAttemptedAt = 0;
        leaveHousePending = false;
        leaveHouseAttemptedAt = 0;
        assumeInsidePlayerHouse = false;
        currentHouseEnteredViaVisitLast = false;
        lastLecternCraftAttemptAt = 0;
        lastProgressivePrepLogAt = 0;
        lastWorldHopAttemptAt = 0;
        worldHopAttempts = 0;
        lastObservedMagicXp = startMagicXp;
        lastObservedUnnotedClay = unnotedSoftClayCount();
        lastCraftProgressAt = 0;
        lastAntibanActionAt = 0;
        nextCraftingAntibanAt = 0;
        currentAdvertisedHouseName = "";
        lastCraftGateLogAt = 0;
        lastCraftGateLogReason = "";
        lastSnapshot = null;
        lastDiagnosticDumpAt = 0;
        lastRecoveryReason = "";
        lastMaterialSummary = "";
        noCompatibleLecternDetectedAt = 0;
        noCompatibleLecternSamples = 0;
    }

    private void dumpTabletWidgetsOnce(HouseTabConfig config) {
        // Widget ids can move between RuneLite/client revisions. This optional
        // dump is a developer aid for finding the correct button ids without
        // adding permanent noisy logs.
        if (!config.debugWidgetDump()) return;
        Widget root = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(InterfaceID.TeletabsCraftIf.UNIVERSE)).orElse(null);
        if (root == null) return;
        if (dumpedTabletInterfaceFor == selectedTablet) return;
        dumpedCurrentTabletInterface = false;
        if (dumpedCurrentTabletInterface) return;
        dumpedCurrentTabletInterface = true;
        dumpedTabletInterfaceFor = selectedTablet;

        Microbot.log("HouseTab widget dump: TeletabsCraftIf");
        for (int child = 0; child <= 0x28; child++) {
            int childId = child;
            int widgetId = InterfaceID.TeletabsCraftIf.UNIVERSE + child;
            String widgetDetails = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget widget = Microbot.getClient().getWidget(widgetId);
                if (widget == null) return null;
                return String.format(
                        "widget=0x%08x child=%d hidden=%s name='%s' text='%s' sprite=%d bounds=%s",
                        widgetId,
                        childId,
                        widget.isHidden(),
                        widget.getName(),
                        widget.getText(),
                        widget.getSpriteId(),
                        widget.getBounds());
            }).orElse(null);
            if (widgetDetails != null) {
                Microbot.log(widgetDetails);
            }
        }
    }

    public HouseTablet getSelectedTablet() {
        return selectedTablet;
    }

    public HouseTabState getCurrentState() {
        return currentState;
    }

    public String getLastTransitionReason() {
        return lastTransitionReason;
    }

    public long getMillisInCurrentState() {
        return Math.max(0, System.currentTimeMillis() - lastStateChangedAt);
    }

    public String getCurrentHost() {
        return currentAdvertisedHouseName == null ? "" : currentAdvertisedHouseName;
    }

    public String getLastRecoveryReason() {
        return lastRecoveryReason;
    }

    public String getLastMaterialSummary() {
        return lastMaterialSummary;
    }

    public int getUnnotedClayCount() {
        return unnotedSoftClayCount();
    }

    public String getSnapshotDebug() {
        // Used by overlays/debug tools to inspect the latest state decision
        // without exposing every internal field.
        return lastSnapshot == null ? "No snapshot" : lastSnapshot.compactDebug();
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public String getStopReason() {
        return stopReason;
    }

    public int getStartMagicXp() {
        return startMagicXp;
    }

    public int getStartMagicLevel() {
        return startMagicLevel;
    }

    public int getTabletsMade() {
        return tabletsMade;
    }

    private void transitionTo(HouseTabState nextState, String reason) {
        // All state changes go through here so client.log tells a clean story:
        // previous state, next state, and the reason for the transition.
        if (nextState == null) {
            return;
        }
        if (currentState == nextState && reason.equals(lastTransitionReason)) {
            return;
        }
        HouseTabState previousState = currentState;
        currentState = nextState;
        lastTransitionReason = reason;
        lastStateChangedAt = System.currentTimeMillis();
        Microbot.status = nextState.getLabel();
        Microbot.log("HouseTab state: " + previousState.getLabel()
                + " -> " + nextState.getLabel()
                + " (" + reason + ")");
    }

    private HouseTabSnapshot snapshot() {
        // Take one consistent picture of the world for this loop. After this,
        // state decisions should use the snapshot rather than repeatedly reading
        // live state that may change halfway through the decision.
        boolean loggedIn = Microbot.isLoggedIn();
        boolean sceneReady = isGameSceneReady();
        WorldPoint location = null;
        int world = -1;
        if (loggedIn && sceneReady) {
            try {
                world = Microbot.getClient().getWorld();
                location = Microbot.getClient().getLocalPlayer().getWorldLocation();
            } catch (Exception ignored) {
            }
        }

        boolean compatibleLecternVisible = sceneReady && getHouseLectern() != null;
        if (compatibleLecternVisible) {
            // Seeing a compatible lectern proves this host is not bad, so clear
            // any earlier "no lectern" suspicion.
            resetNoLecternEvidence();
        }
        boolean atGrandExchange = sceneReady && isAtGrandExchange();
        boolean nearRimmington = sceneReady && isNearRimmingtonAdvertisementByPosition();
        boolean housePortalVisible = sceneReady && hasVisibleHousePortal();
        // This updates pending exit state before insideHouse is calculated. If a
        // previous portal click succeeded, the missing portal clears stale POH
        // assumptions before the current snapshot is frozen.
        updateLeaveHousePending(housePortalVisible);
        boolean insideHouse = sceneReady && (compatibleLecternVisible || isInsidePlayerHouse());
        boolean lecternInterfaceOpen = sceneReady && hasLecternInterfaceOpen();
        boolean craftingActive = sceneReady && isTabletCraftingActive();
        HouseTabSnapshot current = new HouseTabSnapshot(
                loggedIn,
                sceneReady,
                world,
                location,
                atGrandExchange,
                nearRimmington,
                insideHouse,
                housePortalVisible,
                compatibleLecternVisible,
                lecternInterfaceOpen,
                craftingActive,
                hasSoftClay(),
                hasSoftClayNoted(),
                unnotedSoftClayCount(),
                notedSoftClayCount(),
                hasAnySoftClay(),
                hasRequiredRunes(),
                hasStaffFor(selectedTablet),
                selectedTablet);
        // Keep the latest snapshot for overlay/debug calls. The state machine
        // still uses the local current variable so one loop remains consistent.
        lastSnapshot = current;
        return current;
    }

    private void maybeDumpDiagnostics(HouseTabConfig config, HouseTabSnapshot current, boolean force) {
        // Diagnostics are rate-limited so debug mode can stay on during live runs
        // without flooding client.log every scheduler tick.
        if (!force && !config.debugDiagnostics()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastDiagnosticDumpAt < 15000) {
            return;
        }
        lastDiagnosticDumpAt = now;
        Microbot.log("HouseTab diagnostics: state=" + currentState.getLabel()
                + " reason=" + lastTransitionReason
                + " recovery=" + lastRecoveryReason
                + " materials=" + lastMaterialSummary
                + " knownGoodHosts=" + knownGoodAdvertisedHouses
                + " blacklistedHosts=" + blacklistedAdvertisedHouses
                + " " + (current == null ? "snapshot=null" : current.compactDebug()));
    }

    private boolean ensureStaffEquipped(HouseTablet tablet) {
        // Equipping from inventory is cheaper than opening a bank. Bank handling
        // happens elsewhere when the script is deliberately in setup mode.
        if (tablet.getPreferredStaffRunes().isEmpty()) return true;
        List<Rs2Staff> staves = rankedStavesFor(tablet, false);
        boolean alreadyEquipped = staves.stream()
                .map(Rs2Staff::getItemID)
                .anyMatch(Rs2Equipment::isWearing);
        if (alreadyEquipped) return true;

        return staves.stream()
                .map(Rs2Staff::getItemID)
                .filter(Rs2Inventory::hasItem)
                .findFirst()
                .map(Rs2Inventory::wield)
                .orElse(false);
    }

    private boolean depositMismatchedWeaponFor(HouseTablet tablet, boolean allowPartial) {
        // If the weapon slot is occupied by the wrong item, free it before trying
        // to withdraw/equip the best staff for the target tablet.
        if (!Rs2Bank.isOpen()) return false;

        Rs2Staff bestStaff = bestAvailableStaffFor(tablet, allowPartial);
        if (bestStaff == Rs2Staff.NONE) {
            Microbot.log("HouseTab: no replacement staff available; keeping current weapon equipped.");
            return false;
        }

        if (!Rs2Equipment.isWearing(bestStaff.getItemID()) && Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null) {
            Microbot.log("HouseTab: unequipping current weapon before progressive staff setup for " + tablet.getName());
            Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
            sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null, 3000);
            return true;
        }
        return false;
    }

    private boolean equippedStaffProvides(Runes rune) {
        // Staffs provide unlimited elemental runes while equipped. This helper
        // answers "does my weapon slot already cover this rune requirement?"
        // before the script spends inventory space withdrawing loose runes.
        return Arrays.stream(Rs2Staff.values())
                .filter(staff -> staff != Rs2Staff.NONE)
                .filter(staff -> staff.getRunes().contains(rune))
                .map(Rs2Staff::getItemID)
                .anyMatch(Rs2Equipment::isWearing);
    }

    private int inventoryRuneCount(Runes rune) {
        // Combo runes count as either component rune. For example, a mud rune can
        // satisfy earth or water. Counting them here makes hasRune() match the
        // way the game accepts rune costs.
        int count = Rs2Inventory.itemQuantity(rune.getItemId());
        for (Runes comboRune : Runes.getComboRunes(rune)) {
            count += Rs2Inventory.itemQuantity(comboRune.getItemId());
        }
        return count;
    }

    private boolean hasRune(Runes rune, int amount) {
        // Rune pouch state is cached by the client, so refresh it before asking
        // whether pouch runes cover a requirement.
        if (Rs2Inventory.hasRunePouch()) {Rs2RunePouch.fullUpdate();}
        if (equippedStaffProvides(rune)) return true;
        return inventoryRuneCount(rune) >= amount || (Rs2Inventory.hasRunePouch() && Rs2RunePouch.contains(rune));
    }

    private boolean hasRequiredRunes() {
        return selectedTablet.getRuneRequirements().entrySet().stream()
                .allMatch(entry -> hasRune(entry.getKey(), entry.getValue()));
    }

    private String missingRuneDebug() {
        // This is deliberately verbose because it appears only when setup fails.
        // It tells us whether the missing rune was expected from inventory, staff,
        // or rune pouch.
        return selectedTablet.getRuneRequirements().entrySet().stream()
                .filter(entry -> !hasRune(entry.getKey(), entry.getValue()))
                .map(entry -> entry.getKey().name().toLowerCase()
                        + " need=" + entry.getValue()
                        + " inv=" + inventoryRuneCount(entry.getKey())
                        + " staff=" + equippedStaffProvides(entry.getKey()))
                .collect(Collectors.joining(", "));
    }

    private boolean hasValidProgressiveLoadout(HouseTabConfig config) {
        // Progressive mode can change tablet choice as Magic level rises, so the
        // current loadout must be revalidated against the newly selected tablet.
        if (!config.progressive()) {
            return true;
        }
        if (!hasSoftClay() || !hasRequiredRunes()) {
            return false;
        }
        return !config.useCombinationStaff() || isBestAvailableStaffEquipped(selectedTablet, false);
    }

    private boolean ensureHouseReturnTabsFromBank() {
        // The GE setup route returns to Rimmington by breaking a house tablet
        // outside. Without one, the script would strand itself at the GE.
        if (Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            return true;
        }
        if (!Rs2Bank.isOpen() || !Rs2Bank.hasBankItem(ItemID.POH_TABLET_TELEPORTTOHOUSE, 1)) {
            stop("Missing house tablet to return from GE");
            return false;
        }
        if (!Rs2Bank.withdrawAll(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            stop("Unable to withdraw house tablets");
            return false;
        }
        return sleepUntil(() -> Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE), 3000);
    }

    private boolean ensureRequiredRunesFromBank() {
        // Only withdraw runes that are not already covered by staff/inventory/
        // rune pouch. This keeps the inventory as open as possible for clay.
        if (!Rs2Bank.isOpen()) {
            return hasRequiredRunes();
        }

        for (Map.Entry<Runes, Integer> entry : selectedTablet.getRuneRequirements().entrySet()) {
            Runes rune = entry.getKey();
            int amount = entry.getValue();
            if (hasRune(rune, amount)) {
                continue;
            }
            int itemId = rune.getItemId();
            if (!Rs2Bank.hasBankItem(itemId, amount)) {
                Microbot.log("HouseTab: missing rune from bank setup: " + missingRuneDebug());
                stop("Missing " + rune.name().toLowerCase() + " runes for " + selectedTablet.getName());
                return false;
            }
            if (!Rs2Bank.withdrawAll(itemId)) {
                stop("Unable to withdraw " + rune.name().toLowerCase() + " runes");
                return false;
            }
            int requiredAmount = amount;
            sleepUntil(() -> hasRune(rune, requiredAmount), 3000);
        }

        return hasRequiredRunes();
    }

    private boolean ensureSoftClayFromBank() {
        // Prefer noted clay because Phials can unnote it near the Rimmington
        // portal, which avoids repeated bank trips.
        if (hasAnySoftClay()) {
            return true;
        }
        if (!Rs2Bank.isOpen()) {
            return false;
        }
        if (Rs2Bank.hasBankItem(1762, 1)) {
            if (!Rs2Bank.withdrawAll(1762)) {
                stop("Unable to withdraw noted soft clay");
                return false;
            }
            return sleepUntil(this::hasSoftClayNoted, 3000);
        }
        if (Rs2Bank.hasBankItem(1761, 1)) {
            if (!Rs2Bank.withdrawAll(1761)) {
                stop("Unable to withdraw soft clay");
                return false;
            }
            return sleepUntil(this::hasSoftClay, 3000);
        }

        stop("Missing soft clay");
        return false;
    }

    private void depositCraftedTeleportStacksForProgressive() {
        // When progressive mode upgrades to a higher-XP tablet, bank older output
        // stacks so inventory slots are available for the next setup.
        if (!Rs2Bank.isOpen()) {
            return;
        }

        for (HouseTablet tablet : HouseTablet.values()) {
            if (tablet == HouseTablet.TELEPORT_TO_HOUSE) {
                continue;
            }
            if (Rs2Inventory.hasItem(tablet.getItemId())) {
                Microbot.log("HouseTab: banking crafted " + tablet.getName() + " stack before progressive setup.");
                Rs2Bank.depositAll(tablet.getItemId());
                sleep(250, 450);
            }
        }
    }

    private HouseTablet resolveSelectedTablet(HouseTabConfig config) {
        // Centralize tablet selection so both classic and progressive flows use
        // the same rule.
        int magicLevel = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
        return HouseTabPlanner.resolveTablet(config.progressive(), config.tablet(), magicLevel);
    }

    private boolean hasRequiredStaffOrFallback(HouseTabConfig config) {
        // Combination-staff setup is intentionally strict: if enabled, we prefer
        // a staff that fully covers the tablet's elemental requirements. Loose
        // runes are handled separately by hasRequiredRunes().
        if (!config.useCombinationStaff()) return true;
        if (isBestAvailableStaffEquipped(selectedTablet, false)) return true;
        if (!Rs2Bank.isOpen() && hasStaffFor(selectedTablet) && ensureStaffEquipped(selectedTablet)) return true;
        if (!Rs2Bank.isOpen()) {
            Microbot.log("HouseTab: staff setup skipped bank scan because bank is closed.");
        }

        if (Rs2Bank.isOpen()) {
            List<Rs2Staff> staves = rankedStavesFor(selectedTablet, false);
            Microbot.log("HouseTab: " + staves.size() + " full staff candidates for " + selectedTablet.getName());
            for (Rs2Staff staff : staves) {
                // Candidate logs make bank-cache issues visible. If the bot says
                // "missing staff" while a staff is in bank, these lines show what
                // the script actually saw.
                Microbot.log("HouseTab: staff candidate "
                        + staff.name()
                        + "#" + staff.getItemID()
                        + " " + bankStaffDebug(staff.getItemID())
                        + " inv=" + Rs2Inventory.hasItem(staff.getItemID())
                        + " worn=" + Rs2Equipment.isWearing(staff.getItemID()));
            }
            for (Rs2Staff staff : staves) {
                if (Rs2Equipment.isWearing(staff.getItemID())) {
                    return true;
                }
                if (Rs2Inventory.hasItem(staff.getItemID()) && Rs2Inventory.wield(staff.getItemID())) {
                    // Inventory-first path avoids unnecessary bank operations if
                    // the staff was already withdrawn earlier.
                    sleepUntil(() -> Rs2Equipment.isWearing(staff.getItemID()), 3000);
                    return Rs2Equipment.isWearing(staff.getItemID());
                }
                if (hasBankStaffItem(staff.getItemID())) {
                    if (Rs2Bank.withdrawAndEquip(staff.getItemID())) {
                        // Preferred path: one helper call withdraws and equips.
                        sleepUntil(() -> Rs2Equipment.isWearing(staff.getItemID()), 3000);
                        if (Rs2Equipment.isWearing(staff.getItemID())) {
                            return true;
                        }
                    }
                    if (Rs2Bank.withdrawOne(staff.getItemID())) {
                        // Fallback path: some bank/equipment helpers fail on
                        // specific clients, so withdraw then wield manually.
                        sleepUntil(() -> Rs2Inventory.hasItem(staff.getItemID()), 3000);
                        if (Rs2Inventory.wield(staff.getItemID())) {
                            sleepUntil(() -> Rs2Equipment.isWearing(staff.getItemID()), 3000);
                            return Rs2Equipment.isWearing(staff.getItemID());
                        }
                    }
                }
            }
        }

        if (config.buyMissingStaff()) {
            Microbot.log("HouseTab GE staff buying is configured but not implemented yet.");
        }
        Microbot.showMessage("Missing staff for " + selectedTablet.getName());
        stop("Missing staff for " + selectedTablet.getName());
        return false;
    }

    private boolean needsProgressiveBankPrep(HouseTabConfig config) {
        // Full prep check used when correctness matters more than speed.
        if (!config.progressive()) {
            return false;
        }
        if (!hasAnySoftClay() || !hasRequiredRunes()) {
            return true;
        }
        if (!hasSoftClay() && hasSoftClayNoted()) {
            return false;
        }
        if (config.useCombinationStaff() && !hasStaffFor(selectedTablet)) {
            return true;
        }
        return false;
    }

    private boolean needsProgressiveBankPrepFast(HouseTabConfig config) {
        // Fast prep check used in hot loop paths to avoid extra bank/cache work.
        if (!config.progressive()) {
            return false;
        }
        if (!hasAnySoftClay() || !hasRequiredRunes()) {
            return true;
        }
        if (!hasSoftClay() && hasSoftClayNoted()) {
            return false;
        }
        return config.useCombinationStaff() && !hasStaffFor(selectedTablet);
    }

    private void enterHouseForProgressivePrep(HouseTabConfig config) {
        // Some GE routes depend on objects inside a hosted house, such as an
        // ornate jewellery box. If we are outside and need setup, enter a house
        // first so we can use that route to the GE.
        Microbot.status = "Entering house for GE setup";
        long now = System.currentTimeMillis();
        if (now - lastProgressivePrepLogAt > 5000) {
            lastProgressivePrepLogAt = now;
            Microbot.log("HouseTabScript: progressive prep needed outside house; entering house before GE travel. "
                    + fastMaterialDebug());
        }

        if (config.useAdvertisementBoard()) {
            enterAdvertisedHouse(config, false);
            return;
        }

        if (config.ownHouse()) {
            if (Microbot.getRs2TileObjectCache().query().interact(ObjectID.POH_RIMMINGTON_PORTAL, "Home")) {
                assumeInsidePlayerHouse = true;
                sleep(800, 1200);
            }
            return;
        }

        if (Microbot.getRs2TileObjectCache().query().interact(ObjectID.POH_RIMMINGTON_PORTAL, "Friend's house")) {
            assumeInsidePlayerHouse = true;
            sleepUntil(() -> Rs2Widget.hasWidget("Enter name"), 5000);
            if (!config.housePlayerName().isBlank() && Rs2Widget.hasWidget(config.housePlayerName())) {
                Rs2Widget.clickWidget(config.housePlayerName());
                sleep(800, 1200);
            } else if (!config.housePlayerName().isBlank() && Rs2Widget.hasWidget("Enter name")) {
                Rs2Keyboard.typeString(config.housePlayerName());
                Rs2Keyboard.enter();
                sleep(1000, 1800);
            } else if (Rs2Widget.hasWidget("Enter name")) {
                stop("Friend house name prompt opened with no fallback name configured");
            }
        }
    }

    private boolean isAtGrandExchange() {
        // Position or booth evidence is enough to know GE setup can begin. When
        // true, clear any stale inside-house assumption.
        boolean atGrandExchange = isNearGrandExchangeByPosition() || hasVisibleGrandExchangeBankBooth();
        if (atGrandExchange) {
            assumeInsidePlayerHouse = false;
        }
        return atGrandExchange;
    }

    private boolean isNearRimmingtonAdvertisementByPosition() {
        // This is a coarse coordinate box around the Rimmington house portal and
        // advertisement board. It is used as "outside and near setup area" proof.
        try {
            WorldPoint location = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return location.getPlane() == 0
                    && location.getX() >= 2944
                    && location.getX() <= 2958
                    && location.getY() >= 3206
                    && location.getY() <= 3226;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isInsidePlayerHouse() {
        // Prefer object evidence first. A POH portal, compatible lectern, or
        // jewellery box is strong proof that we are inside a player-owned house.
        boolean hasHouseObjectEvidence = false;
        try {
            hasHouseObjectEvidence = Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null
                    || Microbot.getRs2TileObjectCache().query().withId(ORNATE_JEWELLERY_BOX_OBJECT).nearest() != null
                    || Microbot.getRs2TileObjectCache().query().withIds(lecternToHouseTabButton.keySet().stream().mapToInt(Integer::intValue).toArray()).nearest() != null;
            if (hasHouseObjectEvidence) {
                assumeInsidePlayerHouse = true;
                if (lastInsideHouseDetectedAt == 0) {
                    lastInsideHouseDetectedAt = System.currentTimeMillis();
                }
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return false;
            }
            WorldPoint location = Microbot.getClient().getLocalPlayer().getWorldLocation();
            boolean inside = location != null && (location.getX() >= 10000 || location.getY() >= 10000);
            if (inside && lastInsideHouseDetectedAt == 0) {
                lastInsideHouseDetectedAt = System.currentTimeMillis();
            }
            if (!inside && location != null && assumeInsidePlayerHouse && !hasHouseObjectEvidence) {
                // Recent stall fix: after leaving a house the old assumption can
                // outlive the portal object. Normal-world coordinates plus no POH
                // objects mean the assumption is stale and must be cleared.
                assumeInsidePlayerHouse = false;
                lastInsideHouseDetectedAt = 0;
                resetNoLecternEvidence();
            }
            return inside;
        } catch (Exception ex) {
            return assumeInsidePlayerHouse;
        }
    }

    private boolean hasVisibleHousePortal() {
        return Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null;
    }

    private boolean recoverBadAdvertisedHouseIfNeeded(HouseTabConfig config, boolean hasCompatibleLectern) {
        // Hosted houses can load slowly, so one missing-lectern sample is not
        // enough to blacklist a host. This method waits for repeated evidence
        // before leaving and selecting the next advertised house.
        if (!config.useAdvertisementBoard() || hasCompatibleLectern) {
            if (hasCompatibleLectern) {
                resetNoLecternEvidence();
            }
            return false;
        }

        if (hasLecternInterfaceOpen()
                || Microbot.isGainingExp
                || isTabletCraftingActive()
                || lecternStudyPending
                || leaveHousePending) {
            // Do not judge the house while another action is in progress. For
            // example, the lectern interface opening means the host is fine even
            // if object queries are temporarily empty.
            return false;
        }

        boolean hasHouseEvidence = hasVisibleHousePortal() || isInsidePlayerHouse();
        if (!hasHouseEvidence) {
            resetNoLecternEvidence();
            return false;
        }

        // Object cache visibility can flicker just after entering a hosted PoH.
        // Treat "no lectern" as real only after repeated samples over a short window.
        if (lastInsideHouseDetectedAt == 0) {
            lastInsideHouseDetectedAt = System.currentTimeMillis();
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastInsideHouseDetectedAt < 8000) {
            return true;
        }

        if (noCompatibleLecternDetectedAt == 0) {
            noCompatibleLecternDetectedAt = now;
            noCompatibleLecternSamples = 1;
            log.debug("HouseTab: first no-compatible-lectern sample in advertised house.");
            return true;
        }

        noCompatibleLecternSamples++;
        if (noCompatibleLecternSamples >= 4 && now - noCompatibleLecternDetectedAt > 5000) {
            leaveBadAdvertisedHouse();
        }
        return true;
    }

    private void resetNoLecternEvidence() {
        // Any strong house/lectern signal invalidates previous "bad host" samples.
        noCompatibleLecternDetectedAt = 0;
        noCompatibleLecternSamples = 0;
    }

    private boolean hasLecternInterfaceOpen() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getWidget(InterfaceID.TeletabsCraftIf.UNIVERSE) != null).orElse(false);
    }

    private boolean isNearGrandExchangeByPosition() {
        try {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return false;
            }
            WorldPoint location = Microbot.getClient().getLocalPlayer().getWorldLocation();
            if (location == null) {
                return false;
            }
            return location.getPlane() == 0
                    && location.getX() >= 3120
                    && location.getX() <= 3195
                    && location.getY() >= 3440
                    && location.getY() <= 3525;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasVisibleGrandExchangeBankBooth() {
        try {
            for (int boothId : GRAND_EXCHANGE_BOOTH_OBJECTS) {
                if (Microbot.getRs2TileObjectCache().query().withId(boothId).nearest() != null) {
                    return true;
                }
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }

    private boolean isGameSceneReady() {
        try {
            return Microbot.getClient().getLocalPlayer() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean openGrandExchangeBank() {
        // Use nearby GE booth objects directly instead of walking/searching. This
        // method assumes earlier state has already proved we are at the GE.
        if (Rs2Bank.isOpen()) {
            return true;
        }

        Microbot.status = "Opening GE bank";
        for (int boothId : GRAND_EXCHANGE_BOOTH_OBJECTS) {
            Rs2TileObjectModel booth = Microbot.getRs2TileObjectCache().query()
                    .withId(boothId)
                    .nearest();
            if (booth == null) {
                continue;
            }

            Microbot.log("HouseTab: opening GE bank using booth " + boothId);
            if (Microbot.getRs2TileObjectCache().query().interact(boothId, "Bank")
                    && sleepUntil(Rs2Bank::isOpen, 5000)) {
                return true;
            }
        }

        Microbot.log("HouseTab: GE booth fast open failed.");
        return false;
    }

    private boolean travelToGrandExchangeFromHouse() {
        // The fast progressive route uses a hosted-house jewellery box. There are
        // two possible UI paths: direct "Grand Exchange" action, or opening the
        // teleport menu and pressing the GE hotkey.
        if (isAtGrandExchange()) {
            return true;
        }
        transitionTo(HouseTabState.GO_GE, "using house jewellery box");

        Rs2TileObjectModel box = Microbot.getRs2TileObjectCache().query()
                .withId(ORNATE_JEWELLERY_BOX_OBJECT)
                .nearest();
        if (box == null) {
            Microbot.log("HouseTabScript: no ornate jewellery box found for GE travel.");
            return false;
        }

        Microbot.status = "Using jewellery box to GE";
        Microbot.log("HouseTabScript: using ornate jewellery box Grand Exchange action.");
        if (Microbot.getRs2TileObjectCache().query().interact(ORNATE_JEWELLERY_BOX_OBJECT, "Grand Exchange")) {
            assumeInsidePlayerHouse = false;
            if (sleepUntil(this::isAtGrandExchange, 12000)) {
                return true;
            }
            Microbot.log("HouseTabScript: Grand Exchange jewellery-box action clicked, but GE arrival was not detected yet.");
            return true;
        }

        if (Microbot.getClient().getWidget(ORNATE_JEWELLERY_BOX_GE_WIDGET) == null) {
            if (!Microbot.getRs2TileObjectCache().query().interact(ORNATE_JEWELLERY_BOX_OBJECT, "Teleport Menu")) {
                Microbot.getRs2TileObjectCache().query().interact(ORNATE_JEWELLERY_BOX_OBJECT, "Teleport");
            }
            sleepUntilOnClientThread(() -> Microbot.getClient().getWidget(ORNATE_JEWELLERY_BOX_GE_WIDGET) != null, 5000);
        }

        if (Microbot.getClient().getWidget(ORNATE_JEWELLERY_BOX_GE_WIDGET) == null) {
            return false;
        }

        Rs2Keyboard.keyPress('l');
        return sleepUntil(this::isAtGrandExchange, 10000);
    }

    private boolean prepareProgressiveLoadoutAtGrandExchange(HouseTabConfig config) {
        // GE setup is a contained phase: open bank, deposit clutter, equip staff,
        // withdraw clay/runes/return tablets, then break a house tablet back to
        // Rimmington. Each step returns false so the next tick can retry safely.
        if (!config.progressive() || !isAtGrandExchange()) {
            return false;
        }
        transitionTo(HouseTabState.BANK_SETUP, "preparing " + selectedTablet.getName());
        Microbot.status = "HouseTab GE setup: " + selectedTablet.getName();
        Microbot.log("HouseTab: progressive GE setup for " + selectedTablet.getName());

        if (!openGrandExchangeBank()) {
            Microbot.log("HouseTab: failed to open GE bank.");
            return false;
        }
        sleepUntil(Rs2Bank::isOpen, 5000);
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        if (config.progressiveBankTab() >= 0) {
            Rs2Bank.openTab(config.progressiveBankTab());
            sleep(250, 450);
        }

        depositCraftedTeleportStacksForProgressive();

        Rs2Bank.depositAllExcept(
                ItemID.POH_TABLET_TELEPORTTOHOUSE,
                ItemID.COINS,
                ItemID.LAWRUNE,
                1762);
        sleep(600, 900);

        if (!ensureGrandExchangeBankOpen("staff setup after inventory deposit")) {
            return false;
        }

        if (depositMismatchedWeaponFor(selectedTablet, false)) {
            if (!ensureGrandExchangeBankOpen("staff setup after unequipping old weapon")) {
                return false;
            }
            Rs2Bank.depositAllExcept(
                    ItemID.POH_TABLET_TELEPORTTOHOUSE,
                    ItemID.COINS,
                    ItemID.LAWRUNE,
                    1762);
            sleep(500, 750);
            if (!ensureGrandExchangeBankOpen("staff setup after depositing old weapon")) {
                return false;
            }
        }
        if (!ensureSoftClayFromBank()) {
            return false;
        }
        if (!hasRequiredStaffOrFallback(config)) {
            return false;
        }
        if (!ensureHouseReturnTabsFromBank() || !ensureRequiredRunesFromBank()) {
            return false;
        }

        lastPreparedTablet = selectedTablet;
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        return returnToHousePortalFromGrandExchange();
    }

    private boolean ensureGrandExchangeBankOpen(String reason) {
        // Bank can close as side effect of equipment changes. Reopen it before
        // continuing setup instead of assuming the previous call is still valid.
        if (Rs2Bank.isOpen()) {
            return true;
        }
        Microbot.log("HouseTab: bank closed before " + reason + "; reopening GE bank.");
        if (!openGrandExchangeBank() || !sleepUntil(Rs2Bank::isOpen, 5000)) {
            Microbot.log("HouseTab: failed to reopen GE bank before " + reason + ".");
            return false;
        }
        return true;
    }

    private boolean returnToHousePortalFromGrandExchange() {
        // Return outside the house portal, not inside a house. That gives the
        // normal Rimmington/Phials/ad-board loop a known starting point.
        transitionTo(HouseTabState.RETURN_RIMMINGTON, "breaking house tablet from GE");
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        }
        if (!Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            stop("Missing house tablet to return from GE");
            return false;
        }
        transitionPause("returning from GE");
        if (!Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Outside")) {
            Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break");
        }
        assumeInsidePlayerHouse = false;
        return sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null, 10000);
    }

    public HouseTabScript(HOUSETABS_CONFIG houseTabConfig, String[] playerHouses) {
        // One worker thread is enough because every loop is sequential. Running
        // multiple HouseTab ticks at once would race its pending flags.
        this.houseTabConfig = houseTabConfig;
        this.playerHouses = playerHouses;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    private boolean lookForHouseAdvertisementObject() {
        return lookForHouseAdvertisementObject(true);
    }

    private boolean lookForHouseAdvertisementObject(boolean requireUnnotedClay) {
        // Opening the ad board is only useful when we are outside a house and,
        // for normal crafting, already have unnoted clay ready to use.
        Widget houseAdvertisementPanel = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if ((requireUnnotedClay && !hasSoftClay())
                || Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null) {
            return false;
        }
        if (houseAdvertisementPanel != null) {
            transitionTo(HouseTabState.SELECT_ADVERTISED_HOUSE, "advertisement board is open");
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastAdvertisementViewAttemptAt < 2500) {
            return false;
        }

        boolean success = Microbot.getRs2TileObjectCache().query()
                .interact(HOUSE_ADVERTISEMENT_OBJECT, "View");
        if (success) {
            transitionTo(HouseTabState.OPEN_ADVERTISEMENT_BOARD, "viewing house advertisement board");
            lastAdvertisementViewAttemptAt = now;
            transitionPause("opening house advertisement");
        }
        return success;
    }

    private boolean visitLastAdvertisedHouse() {
        return visitLastAdvertisedHouse(true);
    }

    private boolean enterAdvertisedHouse(HouseTabConfig config, boolean requireUnnotedClay) {
        // Hosted-house entry has three paths:
        // 1. Use the already-open board.
        // 2. Try the board's Visit-last shortcut.
        // 3. Open the board and choose a listed host.
        if (!config.useAdvertisementBoard()) {
            return false;
        }
        transitionTo(HouseTabState.ENTER_HOUSE, "using advertised house flow");
        Widget houseAdvertisementPanel = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (houseAdvertisementPanel != null) {
            return lookForPlayerHouse(config, requireUnnotedClay);
        }
        if (config.useLastHouse() && visitLastAdvertisedHouse(requireUnnotedClay)) {
            return true;
        }
        boolean boardOpenOrOpened = lookForHouseAdvertisementObject(requireUnnotedClay);
        return lookForPlayerHouse(config, requireUnnotedClay) || boardOpenOrOpened;
    }

    private boolean visitLastAdvertisedHouse(boolean requireUnnotedClay) {
        // Visit-last is fast, but only safe after we have successfully selected
        // an advertised house earlier in the run.
        if (skipVisitLastHouse || !hasSelectedAdvertisedHouse) {
            return false;
        }
        if ((requireUnnotedClay && !hasSoftClay())
                || Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null) {
            return false;
        }

        boolean success = physicallySelectVisitLastHouse();
        if (success) {
            sleepUntilOnClientThread(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null, 16000);
        }
        boolean enteredHouse = Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null;
        if (enteredHouse) {
            transitionTo(HouseTabState.WAIT_FOR_HOUSE_SCENE, "visit-last entered house");
            skipVisitLastHouse = false;
            advertisedHouseSkipCount = 0;
            enteredAdvertisedHouse = true;
            hasSelectedAdvertisedHouse = true;
            currentHouseEnteredViaVisitLast = true;
            assumeInsidePlayerHouse = true;
            lastInsideHouseDetectedAt = System.currentTimeMillis();
        }
        return enteredHouse;
    }

    private boolean physicallySelectVisitLastHouse() {
        // The Visit-last option is a right-click menu entry on the board object,
        // so this path uses clickbox/menu coordinates rather than a simple widget.
        Rs2TileObjectModel board = Microbot.getRs2TileObjectCache().query()
                .withId(HOUSE_ADVERTISEMENT_OBJECT)
                .nearest();
        if (board == null) {
            return false;
        }
        if (!Rs2Camera.isTileOnScreen(board.getLocalLocation())) {
            Rs2Camera.turnTo(board.getLocalLocation());
            sleep(250, 500);
        }

        Point clickPoint = getObjectClickPoint(board);
        if (clickPoint == null) {
            return false;
        }

        Microbot.log("HouseTab: right-clicking House Advertisement to select Visit-last.");
        moveMouseNaturallyTo(clickPoint);
        sleep(220, 520);
        Microbot.getMouse().click(clickPoint, true);
        sleep(260, 620);
        sleepUntilOnClientThread(() -> Microbot.getClient().isMenuOpen(), 2000);
        if (!Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().isMenuOpen()).orElse(false)) {
            return false;
        }

        Point menuPoint = getMenuEntryClickPoint("Visit-last", "House Advertisement");
        if (menuPoint == null) {
            Microbot.log("HouseTab: Visit-last was not visible in the right-click menu.");
            skipVisitLastHouse = true;
            return false;
        }

        sleep(240, 680);
        moveMouseNaturallyTo(menuPoint);
        sleep(80, 180);
        if (!Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().isMenuOpen()).orElse(false)) {
            Microbot.log("HouseTab: Visit-last menu closed before selection; falling back to advertisement board.");
            return false;
        }
        Microbot.getMouse().click(menuPoint);
        transitionPause("visit-last selected");
        return true;
    }

    private void moveMouseNaturallyTo(Point target) {
        try {
            // Microbot.getMouse().move(...) dispatches direct canvas events. Use the
            // natural mouse engine for visible movement before the right-click/menu click.
            Microbot.naturalMouse.moveTo(target.getX(), target.getY());
        } catch (Exception ex) {
            slowDirectMouseMove(target);
        }
    }

    private void slowDirectMouseMove(Point target) {
        Microbot.getMouse().move(new Point(target.getX() + Rs2Random.between(-18, 18), target.getY() + Rs2Random.between(-18, 18)));
        sleep(120, 260);
        Microbot.getMouse().move(target);
    }

    private Point getObjectClickPoint(Rs2TileObjectModel object) {
        // Prefer the object's clickbox center. Canvas location can be less
        // accurate for large/rotated scene objects.
        java.awt.Shape clickbox = Microbot.getClientThread().runOnClientThreadOptional(object::getClickbox).orElse(null);
        if (clickbox != null) {
            java.awt.Rectangle bounds = clickbox.getBounds();
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }
        return Microbot.getClientThread().runOnClientThreadOptional(object::getCanvasLocation).orElse(null);
    }

    private Point getMenuEntryClickPoint(String option, String target) {
        // RuneLite stores menu entries bottom-to-top visually, so the visual row
        // is derived from the reverse index.
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenuEntries();
            int menuX = Microbot.getClient().getMenuX();
            int menuY = Microbot.getClient().getMenuY();
            int menuWidth = Microbot.getClient().getMenuWidth();
            int menuHeight = Microbot.getClient().getMenuHeight();
            int entryHeight = 15;
            int headerHeight = 18;

            for (int i = entries.length - 1; i >= 0; i--) {
                MenuEntry entry = entries[i];
                String entryOption = stripTags(entry.getOption());
                String entryTarget = stripTags(entry.getTarget());
                if (!option.equalsIgnoreCase(entryOption) || !entryTarget.toLowerCase().contains(target.toLowerCase())) {
                    continue;
                }

                int visualRow = entries.length - i - 1;
                int y = menuY + headerHeight + visualRow * entryHeight + entryHeight / 2;
                int x = menuX + Math.max(16, Math.min(menuWidth - 16, menuWidth / 2));
                if (x <= menuX || x >= menuX + menuWidth || y <= menuY + headerHeight || y >= menuY + menuHeight) {
                    return null;
                }
                return new Point(x, y);
            }
            return null;
        }).orElse(null);
    }

    private String stripTags(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]*>", "");
    }

    private String normalizeHouseName(String value) {
        // Board rows include RuneLite color/format tags. Normalize before
        // comparing host names from config, known-good cache, or blacklist.
        return stripTags(value).trim();
    }

    private String getAdvertisedHouseName(Widget houseAdvertisementNameWidget, int index) {
        if (houseAdvertisementNameWidget == null || houseAdvertisementNameWidget.getChildren() == null) {
            return "";
        }
        Widget nameWidget = houseAdvertisementNameWidget.getChild(index);
        return nameWidget == null ? "" : normalizeHouseName(nameWidget.getText());
    }

    private boolean isAdvertisedHouseBlacklisted(String houseName) {
        // Blacklist is per script run, not persistent. A host can be bad because
        // of temporary conditions, so do not save this across plugin restarts.
        return houseName != null
                && !houseName.isBlank()
                && blacklistedAdvertisedHouses.contains(houseName.toLowerCase());
    }

    private boolean isAdvertisedHouseKnownGood(String houseName) {
        // Known-good is also per run. It is a small optimization: once a host
        // proves it has the right lectern, prefer it until it fails.
        return houseName != null
                && !houseName.isBlank()
                && knownGoodAdvertisedHouses.contains(houseName.toLowerCase());
    }

    private void markCurrentAdvertisedHouseKnownGood() {
        if (currentAdvertisedHouseName == null || currentAdvertisedHouseName.isBlank()) {
            return;
        }
        if (knownGoodAdvertisedHouses.add(currentAdvertisedHouseName.toLowerCase())) {
            Microbot.log("HouseTab: marked advertised house '" + currentAdvertisedHouseName + "' as lectern-compatible.");
        }
    }

    private void blacklistCurrentAdvertisedHouse(String reason) {
        if (currentAdvertisedHouseName == null || currentAdvertisedHouseName.isBlank()) {
            return;
        }
        blacklistedAdvertisedHouses.add(currentAdvertisedHouseName.toLowerCase());
        knownGoodAdvertisedHouses.remove(currentAdvertisedHouseName.toLowerCase());
        Microbot.log("HouseTab: blacklisted advertised house '" + currentAdvertisedHouseName + "' for this run: " + reason);
    }

    private String[] getAdvertisedHouseNames(HouseTabConfig config) {
        // Optional user preference list. Empty means "use first visible unblocked
        // host", which is useful when any public lectern house is acceptable.
        String configured = config.advertisedHouses();
        if (configured == null || configured.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toArray(String[]::new);
    }

    private boolean lookForPlayerHouse(HouseTabConfig config) {
        return lookForPlayerHouse(config, true);
    }

    private boolean lookForPlayerHouse(HouseTabConfig config, boolean requireUnnotedClay) {
        // The advertisement board uses parallel child lists: one area for names
        // and one for Enter House buttons. The same index links a name row to its
        // corresponding button.
        Widget houseAdvertisementNameWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (houseAdvertisementNameWidget == null || houseAdvertisementNameWidget.getChildren() == null) return false;
        if (requireUnnotedClay && !hasSoftClay())
            return false;
        if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null)
            return false;

        int enterHouseButtonHeight = 21;
        int houseIndexToJoin = -1;

        for (int i = 0; i < houseAdvertisementNameWidget.getChildren().length; i++) {
            // Known-good hosts win because they previously showed a compatible
            // lectern this run.
            String houseName = getAdvertisedHouseName(houseAdvertisementNameWidget, i);
            if (isAdvertisedHouseBlacklisted(houseName)) {
                continue;
            }
            if (isAdvertisedHouseKnownGood(houseName)) {
                houseIndexToJoin = i;
                break;
            }
        }

        String[] preferredHouses = getAdvertisedHouseNames(config);
        if (houseIndexToJoin < 0 && advertisedHouseSkipCount == 0 && preferredHouses.length > 0) {
            // Only prefer configured hosts before we start skipping bad houses.
            // Once recovery begins, progress matters more than preference.
            for (int i = 0; i < houseAdvertisementNameWidget.getChildren().length; i++) {
                Widget child = houseAdvertisementNameWidget.getChild(i);
                if (child == null) continue;
                String houseName = normalizeHouseName(child.getText());
                if (isAdvertisedHouseBlacklisted(houseName)) {
                    continue;
                }
                if (Arrays.stream(preferredHouses).anyMatch(x -> houseName.equalsIgnoreCase(x))) {
                    houseIndexToJoin = i;
                    break;
                }
            }
        }

        Widget mainWindow = Microbot.getClient().getWidget(3407879);
        if (mainWindow == null) return false;
        int HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE = 3407891;
        Widget houseAdvertisementEnterHouseWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE);
        if (houseAdvertisementEnterHouseWidget == null) return false;
        List<Integer> visibleEnterHouseRows = new ArrayList<>();
        if (houseAdvertisementEnterHouseWidget.getChildren() != null) {
            for (int i = 0; i < houseAdvertisementEnterHouseWidget.getChildren().length; i++) {
                Widget child = houseAdvertisementEnterHouseWidget.getChild(i);
                if (child != null && child.getActions() != null && Arrays.stream(child.getActions()).anyMatch("Enter House"::equalsIgnoreCase)) {
                    String houseName = getAdvertisedHouseName(houseAdvertisementNameWidget, i);
                    if (isAdvertisedHouseBlacklisted(houseName)) {
                        continue;
                    }
                    visibleEnterHouseRows.add(i);
                }
            }
            visibleEnterHouseRows.sort(Comparator.comparingInt(index -> {
                Widget child = houseAdvertisementEnterHouseWidget.getChild(index);
                return child == null || child.getBounds() == null ? Integer.MAX_VALUE : child.getBounds().y;
            }));
        }
        if (houseIndexToJoin < 0 && !visibleEnterHouseRows.isEmpty()) {
            // Fallback: choose the first visible, non-blacklisted Enter House row.
            houseIndexToJoin = visibleEnterHouseRows.get(0);
        }
        if (houseIndexToJoin < 0) {
            // If the board is open but no row is usable, close it. Reopening on
            // the next loop refreshes the widget tree and listing order.
            Microbot.log("HouseTabScript: advertisement board open but no visible Enter House rows; reopening board next loop.");
            closeHouseAdvertisementInterface();
            return true;
        }
        Widget enterHouseButton = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin);
        currentAdvertisedHouseName = getAdvertisedHouseName(houseAdvertisementNameWidget, houseIndexToJoin);
        int buttonRelativeY = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin).getRelativeY() + enterHouseButtonHeight;
        if (buttonRelativeY > (mainWindow.getScrollY() + mainWindow.getHeight())) {
            // The target row exists but is below the visible scroll viewport.
            transitionTo(HouseTabState.SELECT_ADVERTISED_HOUSE, "scrolling to advertised host " + currentAdvertisedHouseName);
            keepExecuteUntil(() -> {
                // Scroll inside the board panel, not the game world. The random
                // point keeps the scroll target inside the main board bounds.
                int x = (int) mainWindow.getBounds().getCenterX() + Rs2Random.between(-50, 50);
                int y = (int) mainWindow.getBounds().getCenterY() + Rs2Random.between(-50, 50);
                Microbot.getMouse().scrollDown(new Point(x, y));
            }, () -> buttonRelativeY <= (mainWindow.getScrollY() + mainWindow.getHeight()), 500);
            return true;
        } else {
            transitionTo(HouseTabState.SELECT_ADVERTISED_HOUSE, "clicking advertised host " + currentAdvertisedHouseName);
            transitionPause("selecting advertised house");
            Microbot.getMouse()
                    .click(enterHouseButton.getCanvasLocation());
            sleepUntilOnClientThread(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null, 18000);
            if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null || isInsidePlayerHouse()) {
                // The inside-house portal is the strongest sign that entry
                // completed and the POH scene loaded.
                transitionTo(HouseTabState.WAIT_FOR_HOUSE_SCENE, "entered host " + currentAdvertisedHouseName);
                skipVisitLastHouse = false;
                enteredAdvertisedHouse = true;
                hasSelectedAdvertisedHouse = true;
                currentHouseEnteredViaVisitLast = false;
                assumeInsidePlayerHouse = true;
                lastInsideHouseDetectedAt = System.currentTimeMillis();
                Microbot.log("HouseTabScript: entered advertised house"
                        + (currentAdvertisedHouseName.isBlank() ? "." : " hosted by " + currentAdvertisedHouseName + "."));
                transitionPause("entered advertised house");
            } else {
                sleepUntilOnClientThread(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null || isInsidePlayerHouse(), 4000);
                if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null || isInsidePlayerHouse()) {
                    transitionTo(HouseTabState.WAIT_FOR_HOUSE_SCENE, "entered host after slow scene load " + currentAdvertisedHouseName);
                    skipVisitLastHouse = false;
                    enteredAdvertisedHouse = true;
                    hasSelectedAdvertisedHouse = true;
                    currentHouseEnteredViaVisitLast = false;
                    assumeInsidePlayerHouse = true;
                    lastInsideHouseDetectedAt = System.currentTimeMillis();
                    Microbot.log("HouseTabScript: entered advertised house after slow scene load"
                            + (currentAdvertisedHouseName.isBlank() ? "." : " hosted by " + currentAdvertisedHouseName + "."));
                    transitionPause("entered advertised house");
                    return true;
                }
                blacklistCurrentAdvertisedHouse("entry timed out");
                // A timed-out row may be offline/full/bad. Skip it for this run
                // and let the next loop pick another listing.
                advertisedHouseSkipCount++;
                skipVisitLastHouse = true;
                hasSelectedAdvertisedHouse = false;
                closeHouseAdvertisementInterface();
                Microbot.log("HouseTabScript: advertised house entry timed out; will try next listing. skipCount=" + advertisedHouseSkipCount);
            }
            sleep(2000, 3000);
            return true;
        }
    }

    private void closeHouseAdvertisementInterface() {
        // Escape closes the board cleanly. Resetting lastAdvertisementViewAttemptAt
        // lets the next loop reopen it immediately instead of waiting for the
        // normal click throttle.
        if (Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE) == null) {
            return;
        }
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleepUntilOnClientThread(() -> Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE) == null, 1500);
        lastAdvertisementViewAttemptAt = 0;
    }

    private Integer getHouseLectern() {
        // Search every supported lectern object, but accept only a lectern that
        // can craft the currently selected tablet.
        Rs2TileObjectModel lectern = null;
        for (Integer id : lecternToHouseTabButton.keySet()) {
            lectern = Microbot.getRs2TileObjectCache().query().withId(id).nearest();
            if (lectern != null && selectedTablet.supportsLectern(lectern.getId())) break;
            // A lectern can exist but be the wrong family for the selected tablet
            // (for example eagle-only vs demon-only). Treat that as no lectern.
            lectern = null;
        }
        if (lectern != null) {
            // Prefer the tablet enum's known widget id. The map fallback exists
            // for older code paths where button id was inferred from lectern type.
            lecternTabletWidgetId = selectedTablet.hasKnownWidget()
                    ? selectedTablet.getWidgetId()
                    : lecternToHouseTabButton.get(lectern.getId());
            return lectern.getId();
        }

        return null;
    }

    private void lookForLectern(HouseTabConfig config) {
        // Finding a lectern is separate from opening it. Object queries tell us
        // whether the house is suitable; widget queries tell us whether "Study"
        // succeeded and the crafting interface is open.
        if (getHouseLectern() == null) {
            lecternStudyPending = false;
            lecternStudyAttemptedAt = 0;
            if (hasLecternInterfaceOpen() || Microbot.isGainingExp || isTabletCraftingActive()) {
                return;
            }
            if (recoverBadAdvertisedHouseIfNeeded(config, false)) {
                return;
            }
            transitionTo(HouseTabState.FIND_LECTERN, "no compatible lectern found");
            stop("No compatible lectern found for " + selectedTablet.getName());
            return;
        }
        markCurrentAdvertisedHouseKnownGood();
        if (!hasSoftClay() || Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null || isTabletCraftingActive())
            // Do not study the lectern without clay, while outside at the board,
            // or while a previous craft is still active.
            return;

        Widget houseTabInterface = Microbot.getClient().getWidget(lecternTabletWidgetId);
        if (houseTabInterface != null) {
            lecternStudyPending = false;
            lecternStudyAttemptedAt = 0;
            return;
        }

        // Do not require the house portal to be visible here. Large or awkward hosted
        // houses can have a valid lectern in cache while the portal is off-scene.
        if (currentState == HouseTabState.OPEN_LECTERN
                && System.currentTimeMillis() - lastStateChangedAt > 30000
                && !hasLecternInterfaceOpen()
                && !isTabletCraftingActive()) {
            leaveBadAdvertisedHouse("lectern did not open after repeated attempts");
            return;
        }

        if (lecternStudyPending && System.currentTimeMillis() - lecternStudyAttemptedAt < 8000) {
            // The click was sent already. Wait for the interface instead of
            // issuing another Study click every scheduler tick.
            transitionTo(HouseTabState.OPEN_LECTERN, "waiting for lectern interface");
            log.debug("HouseTabScript: waiting for lectern interface.");
            return;
        }
        lecternStudyPending = false;
        lecternStudyAttemptedAt = 0;

        transitionTo(HouseTabState.OPEN_LECTERN, "studying compatible lectern");
        Microbot.log("HouseTabScript: studying lectern.");
        boolean success = Microbot.getRs2TileObjectCache().query().withIds(lecternToHouseTabButton.keySet().stream().mapToInt(Integer::intValue).toArray()).interact("Study");
        if (success) {
            lecternStudyPending = true;
            lecternStudyAttemptedAt = System.currentTimeMillis();
        }
    }

    private boolean isTabletCraftingActive() {
        // Crafting cannot be detected from one signal reliably. The script uses:
        // recent lectern click, XP/clay progress, and the crafting animation.
        // This prevents leaving the house during the final tablet or during lag.
        long now = System.currentTimeMillis();
        int unnotedClay = unnotedSoftClayCount();
        boolean recentCraftProgress = now - lastCraftProgressAt < 1800;
        boolean animationActive = isTabletCraftingAnimationActive();
        long sinceCraftClick = now - lastLecternCraftAttemptAt;
        long sinceCraftProgress = now - lastCraftProgressAt;
        String reason;
        boolean active;
        if (unnotedClay <= 0) {
            // Zero clay usually means done, but there is a tiny window where the
            // final craft animation/progress has not fully settled.
            active = recentCraftProgress && animationActive;
            reason = active ? "zero-clay-recent-progress-animation" : "zero-clay-finished";
            logCraftGate(reason, active, unnotedClay, sinceCraftClick, sinceCraftProgress, animationActive);
            return active;
        }
        if (sinceCraftClick < 3000) {
            active = true;
            reason = "recent-lectern-click";
        } else if (recentCraftProgress) {
            active = true;
            reason = "recent-xp-or-clay-progress";
        } else {
            active = animationActive;
            reason = animationActive ? "animation-active" : "not-crafting";
        }
        logCraftGate(reason, active, unnotedClay, sinceCraftClick, sinceCraftProgress, animationActive);
        return active;
    }

    private boolean shouldWaitForFinalTabletCraft() {
        // When the final clay is consumed, wait briefly for the final animation
        // to resolve before clicking the house portal.
        long now = System.currentTimeMillis();
        int unnotedClay = unnotedSoftClayCount();
        boolean animationActive = isTabletCraftingAnimationActive();
        long sinceCraftClick = now - lastLecternCraftAttemptAt;
        long sinceCraftProgress = now - lastCraftProgressAt;
        boolean wait = unnotedClay <= 0 && animationActive && sinceCraftProgress < 1200;
        logCraftGate(wait ? "final-animation-grace" : "zero-clay-ready-to-leave",
                wait, unnotedClay, sinceCraftClick, sinceCraftProgress, animationActive);
        return wait;
    }

    private boolean isTabletCraftingAnimationActive() {
        // Animation reads must happen on the client thread. The helper returns
        // false instead of throwing if the player is not readable.
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() != null
                        && Microbot.getClient().getLocalPlayer().getAnimation() == 4068).orElse(false);
    }

    private void refreshCraftProgress() {
        // XP increase or clay decrease proves real progress. That timestamp is
        // later used by isTabletCraftingActive() to bridge short animation gaps.
        int currentMagicXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        int currentUnnotedClay = unnotedSoftClayCount();
        if ((lastObservedMagicXp >= 0 && currentMagicXp > lastObservedMagicXp)
                || (lastObservedUnnotedClay >= 0 && currentUnnotedClay < lastObservedUnnotedClay)) {
            lastCraftProgressAt = System.currentTimeMillis();
            updateTabletCount();
            if (currentUnnotedClay <= 3) {
                log.debug("HouseTab craft progress: xp=" + lastObservedMagicXp + "->" + currentMagicXp
                        + " clay=" + lastObservedUnnotedClay + "->" + currentUnnotedClay
                        + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
            }
        }
        lastObservedMagicXp = currentMagicXp;
        lastObservedUnnotedClay = currentUnnotedClay;
    }

    private void logCraftGate(String reason, boolean active, int unnotedClay, long sinceCraftClick, long sinceCraftProgress, boolean animationActive) {
        // Craft-gate logs are noisy, so only log when the reason changes or at a
        // low frequency. These logs explain "why didn't it leave yet?"
        if (unnotedClay > 3 && active) {
            return;
        }
        long now = System.currentTimeMillis();
        if (reason.equals(lastCraftGateLogReason) && now - lastCraftGateLogAt < 2500) {
            return;
        }
        lastCraftGateLogReason = reason;
        lastCraftGateLogAt = now;
        log.debug("HouseTab craft gate: active=" + active
                + " reason=" + reason
                + " clay=" + unnotedClay
                + " sinceClickMs=" + sinceCraftClick
                + " sinceProgressMs=" + sinceCraftProgress
                + " animation=" + animationActive
                + " gainingXp=" + Microbot.isGainingExp
                + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
    }

    public void createHouseTablet(HouseTabConfig config) {
        // This method assumes the lectern interface is open. It makes sure the
        // desired tablet and quantity are visible/selected, then clicks Confirm.
        if (!selectedTablet.hasKnownWidget()) {
            stop("Missing widget id for " + selectedTablet.getName());
            return;
        }

        dumpTabletWidgetsOnce(config);

        Widget houseTabInterface = Microbot.getClient().getWidget(lecternTabletWidgetId);
        if (houseTabInterface == null) return;
        lecternStudyPending = false;
        lecternStudyAttemptedAt = 0;
        if (!hasSoftClay() || Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null)
            return;
        if (!ensureSelectedTabletVisible()) {
            transitionTo(HouseTabState.SELECT_TABLET_WIDGET, "scrolling to " + selectedTablet.getName());
            return;
        }
        if (!ensureQuantityMode(config.quantityMode())) {
            transitionTo(HouseTabState.SELECT_TABLET_WIDGET, "selecting quantity " + config.quantityMode());
            return;
        }

        if (config.quantityMode() == TabletQuantityMode.MAKE_ONE) {
            // Test mode: make one tablet, verify output/clay/XP changed, then stop.
            int outputCount = Rs2Inventory.count(selectedTablet.getItemId());
            int clayCount = Rs2Inventory.count(1761);
            Microbot.getMouse().click(houseTabInterface.getCanvasLocation());
            sleep(1000, 2000);
            Rs2Widget.clickWidget(InterfaceID.TeletabsCraftIf.CONFIRM);
            sleepUntilOnClientThread(() -> Rs2Inventory.count(selectedTablet.getItemId()) > outputCount
                    || Rs2Inventory.count(1761) < clayCount
                    || Microbot.isGainingExp, 10000);
            updateTabletCount();
            stop("Made one " + selectedTablet.getName());
            return;
        }

        if (isTabletCraftingActive()) {
            // If crafting already started, do not click the widget again.
            transitionTo(HouseTabState.CRAFT_TABLETS, "tablet crafting already active");
            return;
        }
        lastLecternCraftAttemptAt = System.currentTimeMillis();
        transitionTo(HouseTabState.CRAFT_TABLETS, "selecting " + selectedTablet.getName());
        Microbot.log("HouseTabScript: selecting " + selectedTablet.getName() + " on lectern.");
        Microbot.getMouse().click(houseTabInterface.getCanvasLocation());
        sleep(250, 500);
        Rs2Widget.clickWidget(InterfaceID.TeletabsCraftIf.CONFIRM);
        nextCraftingAntibanAt = System.currentTimeMillis() + Rs2Random.between(7000, 16000);
        updateTabletCount();
    }

    private boolean ensureSelectedTabletVisible() {
        // The tablet list can scroll. Widget bounds tell us whether the selected
        // tablet button is inside the visible viewport.
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget target = Microbot.getClient().getWidget(lecternTabletWidgetId);
            Widget viewport = Microbot.getClient().getWidget(InterfaceID.TeletabsCraftIf.TABLETS_INNER);
            if (target == null || viewport == null || target.isHidden() || viewport.isHidden()) {
                return false;
            }

            java.awt.Rectangle targetBounds = target.getBounds();
            java.awt.Rectangle viewportBounds = viewport.getBounds();
            if (targetBounds == null || viewportBounds == null || targetBounds.width <= 0 || targetBounds.height <= 0) {
                return false;
            }

            boolean visible = targetBounds.y >= viewportBounds.y
                    && targetBounds.y + targetBounds.height <= viewportBounds.y + viewportBounds.height;
            if (visible) {
                return true;
            }

            long now = System.currentTimeMillis();
            if (now - lastLecternScrollAttemptAt < 450) {
                // Do not spam scroll events; wait for the UI to respond.
                return false;
            }
            lastLecternScrollAttemptAt = now;

            int x = (int) viewportBounds.getCenterX() + Rs2Random.between(-35, 35);
            int y = (int) viewportBounds.getCenterY() + Rs2Random.between(-35, 35);
            Point scrollPoint = new Point(x, y);
            if (targetBounds.y + targetBounds.height > viewportBounds.y + viewportBounds.height) {
                Microbot.log("HouseTabScript: scrolling lectern tablet list down for " + selectedTablet.getName() + ".");
                Microbot.getMouse().scrollDown(scrollPoint);
            } else {
                Microbot.log("HouseTabScript: scrolling lectern tablet list up for " + selectedTablet.getName() + ".");
                Microbot.getMouse().scrollUp(scrollPoint);
            }
            return false;
        }).orElse(false);
    }

    private boolean ensureQuantityMode(TabletQuantityMode quantityMode) {
        // RuneLite widgets can expose text through either name or text depending
        // on client revision, so quantityWidgetHasText checks both.
        if (confirmedQuantityMode == quantityMode) {
            return true;
        }
        if (isQuantityModeSelected(quantityMode)) {
            confirmedQuantityMode = quantityMode;
            return true;
        }

        int widgetId = getQuantityWidgetId(quantityMode);
        Microbot.log("HouseTab: selecting lectern quantity " + quantityMode);
        Rs2Widget.clickWidget(widgetId);
        sleep(300, 600);
        confirmedQuantityMode = quantityMode;
        return true;
    }

    private int getQuantityWidgetId(TabletQuantityMode quantityMode) {
        return quantityMode == TabletQuantityMode.MAKE_ONE
                ? InterfaceID.TeletabsCraftIf.MAKE_1
                : InterfaceID.TeletabsCraftIf.MAKE_ALL;
    }

    private boolean isQuantityModeSelected(TabletQuantityMode quantityMode) {
        int widgetId = getQuantityWidgetId(quantityMode);
        String expected = quantityMode == TabletQuantityMode.MAKE_ONE ? "1" : "All";
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(widgetId);
            if (widget == null || widget.isHidden()) return false;
            if (quantityWidgetHasText(widget, expected)) {
                return true;
            }
            if (widget.getChildren() == null) {
                return false;
            }
            return Arrays.stream(widget.getChildren())
                    .filter(child -> child != null && !child.isHidden())
                    .anyMatch(child -> quantityWidgetHasText(child, expected));
        }).orElse(false);
    }

    private boolean quantityWidgetHasText(Widget widget, String expected) {
        return expected.equalsIgnoreCase(stripTags(widget.getName()))
                || expected.equalsIgnoreCase(stripTags(widget.getText()));
    }

    private void maybeCraftingAntiban(HouseTabSnapshot current) {
        // Antiban is deliberately opportunistic. It runs only after the current
        // loop has confirmed crafting is still active and all state-changing
        // conditions have been checked first.
        if (current == null
                || !current.craftingActive
                || !current.insidePlayerHouse
                || !current.hasUnnotedClay
                || !canRunAntibanNow()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextCraftingAntibanAt) {
            return;
        }

        nextCraftingAntibanAt = now + Rs2Random.between(8000, 22000);
        if (Rs2Random.between(1, 100) > 35) {
            return;
        }

        lastAntibanActionAt = now;
        log.debug("HouseTabScript: crafting antiban mouse movement while waiting at lectern.");
        // Use the shared Rs2Antiban helper rather than raw camera/click actions.
        // This keeps behavior consistent with other Microbot scripts and avoids
        // interfering with the lectern widget.
        Rs2Antiban.moveMouseRandomly();
    }

    private void transitionPause(String reason) {
        // Short human-ish pause after state-changing actions. This is separate
        // from crafting antiban and can include small camera/mouse variation.
        int pause = Rs2Random.between(320, 860);
        if (Rs2Random.between(1, 100) <= 8) {
            pause += Rs2Random.between(500, 1100);
        }
        log.debug("HouseTabScript: transition pause after " + reason + " for " + pause + "ms.");
        sleep(pause, pause + 80);
        if (!isSensitiveMouseTransition(reason) && Rs2Random.between(1, 100) <= 12) {
            moveMouseRandomlyNatural();
        }
        if (Rs2Random.between(1, 100) <= 5) {
            Rs2Camera.setAngle(Rs2Random.between(0, 359), 30);
        }
        if (!isSensitiveMouseTransition(reason)) {
            maybeAntibanTransition(reason);
        }
    }

    private boolean isSensitiveMouseTransition(String reason) {
        if (reason == null) {
            return false;
        }
        return reason.equals("visit-last selected")
                || reason.equals("opening house advertisement")
                || reason.equals("selecting advertised house")
                || reason.equals("entered advertised house");
    }

    private void moveMouseRandomlyNatural() {
        Point target = new Point(Rs2Random.between(120, 720), Rs2Random.between(120, 460));
        try {
            Microbot.naturalMouse.moveTo(target.getX(), target.getY());
        } catch (Exception ex) {
            Microbot.getMouse().move(target);
        }
    }

    private void setupAntiban() {
        // HouseTab uses construction-style antiban defaults, then lowers the
        // chances so antiban remains subtle around UI-heavy lectern actions.
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyConstructionSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.08;
        Rs2AntibanSettings.microBreakChance = 0.02;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.06;
    }

    private void maybeAntibanAfterAction(String reason) {
        // Run the heavier shared antiban only at action boundaries, not during
        // every scheduler tick.
        if (!canRunAntibanNow()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAntibanActionAt < 4500) {
            return;
        }
        lastAntibanActionAt = now;
        log.debug("HouseTabScript: antiban action boundary after " + reason + ".");
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    private void maybeAntibanTransition(String reason) {
        if (!canRunAntibanNow() || Rs2Random.between(1, 100) > 10) {
            return;
        }
        log.debug("HouseTabScript: antiban transition variation after " + reason + ".");
        Rs2Antiban.moveMouseRandomly();
    }

    private boolean canRunAntibanNow() {
        // Never run antiban while menus/widgets/pending interactions are active.
        // That is how we avoid breaking state changes or stealing focus.
        return Microbot.isLoggedIn()
                && !Rs2AntibanSettings.actionCooldownActive
                && !Rs2AntibanSettings.microBreakActive
                && !Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().isMenuOpen()).orElse(false)
                && !hasLecternInterfaceOpen()
                && !phialsUnnotePending;
    }

    public void leaveHouse() {
        // Only leave when the inventory is finished and crafting is not active.
        // The portal must be visible; otherwise this method waits for the normal
        // outside-house flow to take over.
        boolean hasClay = hasSoftClay();
        boolean craftingActive = isTabletCraftingActive();
        boolean portalVisible = Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null;
        if (hasClay || craftingActive || !portalVisible) {
            if (!hasClay && unnotedSoftClayCount() <= 3) {
                Microbot.log("HouseTabScript: leaveHouse blocked. hasClay=" + hasClay
                        + " craftingActive=" + craftingActive
                        + " portalVisible=" + portalVisible
                        + " clay=" + unnotedSoftClayCount());
            }
            return;
        }

        Microbot.log("HouseTabScript: leaveHouse clicking portal. clay=" + unnotedSoftClayCount()
                + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
        leaveHousePortal();
    }

    private void leaveBadAdvertisedHouse() {
        leaveBadAdvertisedHouse("no nearby compatible lectern");
    }

    private void leaveBadAdvertisedHouse(String reason) {
        // Bad advertised houses are recoverable. Mark/skip the current host and
        // get back outside so the next loop can choose a different listing.
        lastRecoveryReason = reason + " at " + (currentAdvertisedHouseName == null || currentAdvertisedHouseName.isBlank()
                ? "advertised house"
                : currentAdvertisedHouseName);
        transitionTo(HouseTabState.RECOVER_BAD_HOUSE, lastRecoveryReason);
        advertisedHouseSkipCount++;
        enteredAdvertisedHouse = false;
        hasSelectedAdvertisedHouse = false;
        skipVisitLastHouse = true;
        Microbot.status = "No nearby lectern; trying advertised house #" + (advertisedHouseSkipCount + 1);
        Microbot.log("HouseTab: advertised house failed lectern check (" + reason + "), trying next listing.");
        if (currentHouseEnteredViaVisitLast) {
            Microbot.log("HouseTab: not blacklisting '" + currentAdvertisedHouseName
                    + "' because the failed entry came from Visit-last and may be an own-house/misclick scene.");
        } else {
            blacklistCurrentAdvertisedHouse(reason);
        }
        currentHouseEnteredViaVisitLast = false;

        if (!teleportToHousePortal()) {
            leaveHousePortal();
        }
    }

    private boolean teleportToHousePortal() {
        // Breaking a house tablet with "Outside" is the cleanest way to escape a
        // bad hosted house because it lands near the Rimmington portal/board.
        if (!Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            Microbot.log("HouseTab: no house tablet available for bad-house recovery; falling back to portal.");
            return false;
        }
        Microbot.log("HouseTab: breaking house tablet to recover from bad advertised house.");
        if (!Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Outside")) {
            Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break");
        }
        assumeInsidePlayerHouse = false;
        lastInsideHouseDetectedAt = 0;
        maybeAntibanAfterAction("bad-house teleport");
        return sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null, 10000);
    }

    private void updateLeaveHousePending(boolean portalVisible) {
        // Portal clicks unload the POH scene asynchronously. Once the portal is
        // gone, consider the exit resolved and clear stale inside-house state.
        if (!leaveHousePending) {
            return;
        }
        if (!portalVisible) {
            leaveHousePending = false;
            leaveHouseAttemptedAt = 0;
            assumeInsidePlayerHouse = false;
            lastInsideHouseDetectedAt = 0;
            resetNoLecternEvidence();
        }
    }

    private boolean leaveHousePortal() {
        // Leave-house has two click paths: direct object interaction first, then
        // a physical click fallback if menu invocation fails.
        transitionTo(HouseTabState.LEAVE_HOUSE, "clicking house portal");
        long now = System.currentTimeMillis();
        if (leaveHousePending) {
            if (!hasVisibleHousePortal()) {
                updateLeaveHousePending(false);
                transitionPause("leaving house");
                maybeAntibanAfterAction("leaving house");
                return true;
            }
            if (now - leaveHouseAttemptedAt < 9000) {
                log.debug("HouseTabScript: waiting for previous house portal click to resolve.");
                return false;
            }
            Microbot.log("HouseTabScript: house portal exit did not resolve; retrying.");
            leaveHousePending = false;
        }

        Rs2TileObjectModel portal = Microbot.getRs2TileObjectCache().query()
                .withId(HOUSE_PORTAL_OBJECT)
                .nearest();
        if (portal == null) {
            updateLeaveHousePending(false);
            return true;
        }

        try {
            sleep(180, 420);
            if (Microbot.getRs2TileObjectCache().query().interact(HOUSE_PORTAL_OBJECT, "Enter")) {
                // Portal exit is asynchronous: the click lands first, then the scene unloads.
                // Track that pending click so the next script tick waits instead of spam-clicking.
                leaveHousePending = true;
                leaveHouseAttemptedAt = System.currentTimeMillis();
            }
            if (leaveHousePending
                    && sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null, 8000)) {
                updateLeaveHousePending(false);
                transitionPause("leaving house");
                maybeAntibanAfterAction("leaving house");
                return true;
            }
        } catch (Exception ex) {
            Microbot.log("HouseTab: portal menu invoke failed, falling back to physical click.");
        }

        Point clickPoint = getObjectClickPoint(portal);
        if (clickPoint == null) {
            clickPoint = portal.getCanvasLocation();
        }
        if (clickPoint == null) {
            return false;
        }

        Microbot.getMouse().click(clickPoint);
        leaveHousePending = true;
        leaveHouseAttemptedAt = System.currentTimeMillis();
        boolean leftHouse = sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null, 8000);
        if (leftHouse) {
            updateLeaveHousePending(false);
            transitionPause("leaving house");
            maybeAntibanAfterAction("leaving house");
        }
        return leftHouse;
    }

    public boolean unnoteClay() {
        // Phials converts noted soft clay into unnoted clay outside the Rimmington
        // portal. This lets the script do many house trips without banking.
        transitionTo(HouseTabState.UNNOTE_CLAY, "using Phials");
        if (hasSoftClay()) {
            phialsUnnotePending = false;
            phialsUnnoteAttemptedAt = 0;
            return false;
        }
        if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() == null) {
            Microbot.log("HouseTabScript: not at Rimmington house advertisement, skipping Phials unnote.");
            return false;
        }
        boolean phialsWidgetOpen = Microbot.getClient().getWidget(14352385) != null;
        if (!phialsWidgetOpen && phialsUnnotePending && System.currentTimeMillis() - phialsUnnoteAttemptedAt < 8000) {
            Microbot.log("HouseTabScript: waiting for Phials unnote dialogue.");
            return true;
        }
        if (phialsUnnotePending && System.currentTimeMillis() - phialsUnnoteAttemptedAt >= 8000) {
            Microbot.log("HouseTabScript: Phials unnote did not complete; retrying.");
            phialsUnnotePending = false;
            phialsUnnoteAttemptedAt = 0;
        }
        if (!phialsWidgetOpen) {
            Microbot.log("HouseTabScript: attempting one Phials unnote interaction.");
            if (!Rs2Inventory.use(1762)) {
                Microbot.log("HouseTabScript: failed to select noted soft clay; will retry next loop.");
                return true;
            }
            sleep(250, 400);
            var phials = Microbot.getRs2NpcCache().query().withName("Phials").nearest();
            if (phials == null) {
                Microbot.log("HouseTabScript: Phials not found; will retry next loop.");
                return true;
            }
            if (!phials.click("Use")) {
                Microbot.log("HouseTabScript: Phials click failed; will retry next loop.");
                return true;
            }
            phialsUnnotePending = true;
            phialsUnnoteAttemptedAt = System.currentTimeMillis();
            Microbot.log("HouseTabScript: used noted soft clay on Phials.");
            return true;
        }

        if (phialsWidgetOpen) {
            Microbot.log("HouseTabScript: selecting Phials unnote inventory option.");
            Rs2Keyboard.keyPress('3');
            phialsUnnotePending = true;
            phialsUnnoteAttemptedAt = System.currentTimeMillis();
            sleepUntil(() -> hasSoftClay() || Microbot.getClient().getWidget(14352385) == null, 2000);
            transitionPause("Phials unnote");
            return true;
        }
        return false;
    }

    public boolean run(HouseTabConfig config) {
        // Main scheduled loop. Each pass validates safety, records a heartbeat,
        // refreshes progress, builds a snapshot, then delegates to progressive or
        // classic tablet logic. Most actions return immediately and are observed
        // on a later tick.
        Microbot.log("HouseTabScript: scheduling main loop. progressive=" + config.progressive()
                + ", configuredTablet=" + config.tablet().getName()
                + ", staffMode=" + config.useCombinationStaff()
                + ", bankTab=" + config.progressiveBankTab());
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            long loopStartedAt = System.currentTimeMillis();
            try {
                debugLoopCount++;
                boolean shouldLogLoop = debugLoopCount <= 10 || debugLoopCount % 25 == 0;
                if (!Microbot.isLoggedIn()) {
                    // Login is controlled by other plugins/break handler. HouseTab
                    // just waits here rather than trying to click login screens.
                    transitionTo(HouseTabState.VALIDATE_LOGIN, "waiting for Microbot login");
                    if (shouldLogLoop) log.debug("HouseTabScript: waiting for login. loop=" + debugLoopCount);
                    return;
                }
                if (!isGameSceneReady()) {
                    // The game can be logged in while local player/scene objects
                    // are still null, especially right after hopping or login.
                    transitionTo(HouseTabState.VALIDATE_LOGIN, "waiting for local player scene");
                    if (shouldLogLoop) log.debug("HouseTabScript: waiting for game scene. loop=" + debugLoopCount);
                    return;
                }
                if (!ensureTargetWorld(config.targetWorld())) {
                    return;
                }
                if ((Rs2AntibanSettings.actionCooldownActive || Rs2AntibanSettings.microBreakActive)
                        && !canBypassAntibanWait()) {
                    // Respect antiban pauses unless we are in a state where
                    // waiting would itself cause spam/recovery problems.
                    return;
                }
                ScriptHeartbeatRegistry.recordHeartbeat(this.getClass().getName());
                // Re-resolve every loop because progressive mode can change the
                // target tablet immediately after a Magic level-up.
                selectedTablet = resolveSelectedTablet(config);
                updatePlanSummary(config);
                if (startMagicXp < 0 || startMagicLevel < 0) {
                    // First successful scene-ready loop initializes run counters.
                    resetTracking();
                    Microbot.log("HouseTabScript: tracking initialized. selected=" + selectedTablet.getName()
                            + ", magicLevel=" + startMagicLevel
                            + ", magicXp=" + startMagicXp);
                }
                refreshCraftProgress();
                HouseTabSnapshot current = snapshot();
                // The overlay and diagnostics display this string; it is also
                // useful when the script stops due to missing resources.
                lastMaterialSummary = HouseTabPlanner.missingMaterials(current, config.useCombinationStaff());
                maybeDumpDiagnostics(config, current, false);
                if (config.progressive()) {
                    runProgressiveLoop(config, current, shouldLogLoop);
                    return;
                }
                runTabletCraftingLoop(config, current, shouldLogLoop);
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            } finally {
                long elapsed = System.currentTimeMillis() - loopStartedAt;
                if (elapsed > 5000) {
                    log.debug("HouseTabScript: loop=" + debugLoopCount + " took " + elapsed + "ms.");
                }
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean canBypassAntibanWait() {
        // UI/action-resolution states are allowed to keep ticking during antiban
        // cooldowns so pending clicks can be observed and cleared.
        return currentState == HouseTabState.WAIT_FOR_HOUSE_SCENE
                || currentState == HouseTabState.OPEN_LECTERN
                || currentState == HouseTabState.SELECT_TABLET_WIDGET
                || currentState == HouseTabState.CRAFT_TABLETS
                || lecternStudyPending
                || leaveHousePending
                || phialsUnnotePending;
    }

    private boolean ensureTargetWorld(int targetWorld) {
        // World hopping is treated as a state-machine gate. If we are on the
        // wrong world, do not run the crafting logic until the hop settles.
        if (targetWorld <= 0) {
            return true;
        }
        int currentWorld = Microbot.getClient().getWorld();
        if (currentWorld == targetWorld) {
            worldHopAttempts = 0;
            lastWorldHopAttemptAt = 0;
            return true;
        }

        Microbot.status = "Hopping to world " + targetWorld + " from " + currentWorld;
        transitionTo(HouseTabState.VALIDATE_WORLD, "hopping from world " + currentWorld + " to " + targetWorld);
        long now = System.currentTimeMillis();
        if (now - lastWorldHopAttemptAt < 15000) {
            return false;
        }

        worldHopAttempts++;
        lastWorldHopAttemptAt = now;
        Microbot.log("HouseTabScript: wrong world " + currentWorld + "; hopping to " + targetWorld
                + " attempt=" + worldHopAttempts);
        boolean hopStarted = Microbot.hopToWorld(targetWorld);
        if (!hopStarted) {
            Microbot.log("HouseTabScript: hop to world " + targetWorld + " did not start; will retry.");
            return false;
        }

        sleepUntilOnClientThread(() -> Microbot.getClient().getWorld() == targetWorld, 12000);
        return false;
    }

    private void runProgressiveLoop(HouseTabConfig config, HouseTabSnapshot current, boolean shouldLogLoop) {
        // Progressive mode can change the selected tablet as Magic level rises.
        // If the current loadout no longer fits, route through GE setup before
        // returning to normal crafting.
        if (shouldLogLoop) {
            log.debug("HouseTabScript: progressive tick=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " " + current.compactDebug());
        }
        boolean insidePlayerHouse = current.insidePlayerHouse;
        boolean atGrandExchange = current.atGrandExchange;
        boolean progressiveBankPrepNeeded = atGrandExchange
                ? needsProgressiveBankPrep(config)
                : HouseTabPlanner.needsBankPrep(current, config.useCombinationStaff());
        boolean validProgressiveLoadout = atGrandExchange
                ? hasValidProgressiveLoadout(config)
                : !progressiveBankPrepNeeded;
        if (shouldLogLoop) {
            log.debug("HouseTabScript: progressive loop=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " atGE=" + atGrandExchange
                    + " insideHouse=" + insidePlayerHouse
                    + " validLoadout=" + validProgressiveLoadout
                    + " prepNeeded=" + progressiveBankPrepNeeded
                    + " " + (atGrandExchange ? materialDebug() : fastMaterialDebug()));
        }
        if (atGrandExchange) {
            if (!validProgressiveLoadout || !Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
                transitionTo(HouseTabState.BANK_SETUP, "progressive loadout requires bank setup");
                Microbot.log("HouseTabScript: GE progressive loadout needs bank prep.");
                prepareProgressiveLoadoutAtGrandExchange(config);
            } else {
                transitionTo(HouseTabState.RETURN_RIMMINGTON, "progressive loadout ready");
                Microbot.log("HouseTabScript: GE progressive loadout already valid; returning to house portal.");
                returnToHousePortalFromGrandExchange();
            }
            return;
        }
        if (progressiveBankPrepNeeded && insidePlayerHouse && current.hasUnnotedClay && current.craftingActive) {
            // Do not interrupt an active inventory just because the next tablet
            // tier will need setup. Finish the current craft first.
            transitionTo(HouseTabState.CRAFT_TABLETS, "finishing current inventory before setup");
            Microbot.status = "Finishing current inventory before progressive setup";
            updateTabletCount();
            return;
        }
        if (progressiveBankPrepNeeded && insidePlayerHouse) {
            transitionTo(HouseTabState.GO_GE, HouseTabPlanner.missingMaterials(current, config.useCombinationStaff()));
            Microbot.status = "Travelling to GE for progressive setup";
            if (travelToGrandExchangeFromHouse()) {
                return;
            }
            Microbot.log("HouseTabScript: progressive prep needed inside house but no jewellery box route was available; leaving to find another advertised house.");
            leaveHousePortal();
            return;
        }
        if (progressiveBankPrepNeeded) {
            transitionTo(HouseTabState.ENTER_HOUSE, "entering house for progressive setup");
            enterHouseForProgressivePrep(config);
            return;
        }

        runTabletCraftingLoop(config, current, shouldLogLoop);
    }

    private void runTabletCraftingLoop(HouseTabConfig config, HouseTabSnapshot current, boolean shouldLogLoop) {
        // Shared crafting loop for classic and progressive once the loadout is
        // valid. It decides between unnoting, entering a house, opening lectern,
        // crafting, leaving, or stopping for missing materials.
        if (shouldLogLoop) {
            log.debug("HouseTabScript: tablet crafting loop=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " " + current.compactDebug());
        }
        boolean hasCompatibleLectern = current.compatibleLecternVisible;
        boolean isInHouse = current.insidePlayerHouse;
        if (currentState == HouseTabState.WAIT_FOR_HOUSE_SCENE
                && isInHouse
                && System.currentTimeMillis() - lastStateChangedAt > 30000
                && !hasCompatibleLectern
                && !current.lecternInterfaceOpen
                && !current.craftingActive) {
            // If a hosted house loads but no compatible lectern ever appears,
            // do not idle forever. Leave and try another host.
            leaveBadAdvertisedHouse("house scene loaded without compatible lectern");
            return;
        }
        if (recoverBadAdvertisedHouseIfNeeded(config, hasCompatibleLectern)) {
            return;
        }
        if (!isInHouse && !current.hasUnnotedClay && current.hasNotedClay) {
            transitionTo(HouseTabState.UNNOTE_CLAY, "need unnoted soft clay");
            Microbot.status = "Unnoting soft clay";
            if (unnoteClay()) {
                return;
            }
        }
        if (unnotedSoftClayCount() <= 3) {
            log.debug("HouseTabScript: low-clay classic loop. loop=" + debugLoopCount
                    + " insideHouse=" + isInHouse
                    + " clay=" + unnotedSoftClayCount()
                    + " output=" + Rs2Inventory.count(selectedTablet.getItemId())
                    + " gainingXp=" + Microbot.isGainingExp);
        }
        if (!hasRequiredStaffOrFallback(config)) {
            return;
        }
        if (isInHouse && !current.hasUnnotedClay) {
            // No clay inside the house means the current inventory is done. Wait
            // for any final craft animation, then leave.
            if (shouldWaitForFinalTabletCraft()) {
                transitionTo(HouseTabState.CRAFT_TABLETS, "waiting for final craft animation");
                return;
            }
            transitionTo(HouseTabState.LEAVE_HOUSE, "inventory finished");
            Microbot.status = "Leaving house";
            Microbot.log("HouseTabScript: classic no-clay exit before xp gate. loop=" + debugLoopCount
                    + " clay=" + unnotedSoftClayCount()
                    + " output=" + Rs2Inventory.count(selectedTablet.getItemId())
                    + " gainingXp=" + Microbot.isGainingExp);
            leaveHousePortal();
            return;
        }
        if (!current.hasAnySoftClay || !current.hasRequiredRunes) {
            if (current.hasAnySoftClay) {
                Microbot.log("HouseTab: missing classic runes: " + missingRuneDebug());
            }
            stop(HouseTabPlanner.missingMaterials(current, config.useCombinationStaff()));
            return;
        }
        if (Microbot.isGainingExp) {
            // XP gain is a broad "crafting is happening" signal. Avoid clicking
            // anything while the client reports active XP gain.
            transitionTo(HouseTabState.CRAFT_TABLETS, "gaining Magic XP");
            return;
        }

        Rs2Player.toggleRunEnergy(true);
        if (Microbot.getClient().getEnergy() < 3000 && !Rs2Widget.hasWidget("Teleport to House") && Microbot.getRs2TileObjectCache().query().withIds(ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION).nearest() != null) {
            Microbot.getRs2TileObjectCache().query().withIds(ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION).interact("drink");
            return;
        }

        if (isInHouse) {
            // Inside a suitable house: either wait for active crafting, open the
            // lectern, select the tablet, or leave if the inventory is done.
            advertisedHouseSkipCount = 0;
            enteredAdvertisedHouse = false;
            if (current.craftingActive) {
                transitionTo(HouseTabState.CRAFT_TABLETS, "crafting active");
                updateTabletCount();
                maybeCraftingAntiban(current);
                return;
            }
            transitionTo(hasCompatibleLectern ? HouseTabState.OPEN_LECTERN : HouseTabState.FIND_LECTERN,
                    hasCompatibleLectern ? "compatible lectern visible" : "inside house without compatible lectern");
            lookForLectern(config);
            createHouseTablet(config);
            leaveHouse();
        } else if (config.useAdvertisementBoard()
                && current.housePortalVisible) {
            // Portal visible while our house-state detector says "outside" is
            // inconsistent, so route through recovery rather than crafting.
            transitionTo(HouseTabState.RECOVER_BAD_HOUSE, "portal visible without house state");
            leaveBadAdvertisedHouse();
        } else {
            // Outside-house flow: unnote clay if needed, then enter a hosted,
            // own, or friend house depending on config.
            if (unnoteClay()) {
                return;
            }
            if (config.useAdvertisementBoard()) {
                enterAdvertisedHouse(config, true);
                return;
            }
            if (enterAdvertisedHouse(config, true)) {
                return;
            }
            if (config.ownHouse()) {
                if (Microbot.getRs2TileObjectCache().query().interact(ObjectID.POH_RIMMINGTON_PORTAL, "Home")) {
                    sleep(800, 1200);
                }
                return;
            }
            if (Microbot.getRs2TileObjectCache().query().interact(ObjectID.POH_RIMMINGTON_PORTAL, "Friend's house")) {
                sleepUntil(() -> Rs2Widget.hasWidget("Enter name"), 5000);
                if (!config.housePlayerName().isBlank() && Rs2Widget.hasWidget(config.housePlayerName())) {
                    Rs2Widget.clickWidget(config.housePlayerName());
                    sleep(800, 1200);
                } else {
                    if (!config.housePlayerName().isBlank() && Rs2Widget.hasWidget("Enter name")) {
                        Rs2Keyboard.typeString(config.housePlayerName());
                        Rs2Keyboard.enter();
                        sleep(1000, 1800);
                    } else if (Rs2Widget.hasWidget("Enter name")) {
                        stop("Friend house name prompt opened with no fallback name configured");
                    }
                }
            }
        }
    }

    public ScheduledFuture<?> keepExecuteUntil(Runnable callback, BooleanSupplier awaitedCondition, int time) {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (awaitedCondition.getAsBoolean()) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
                return;
            }
            callback.run();
        }, 0, time, TimeUnit.MILLISECONDS);
        return scheduledFuture;
    }
}
