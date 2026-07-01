package net.runelite.client.plugins.microbot.housetab;

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
import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;
import net.runelite.client.plugins.microbot.housetab.enums.TabletQuantityMode;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
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

public class HouseTabScript extends Script {
    private final int RIMMINGTON_PORTAL_OBJECT = 15478;
    private final int HOUSE_PORTAL_OBJECT = 4525;

    private final int HOUSE_ADVERTISEMENT_OBJECT = 29091;
    private final int ORNATE_JEWELLERY_BOX_OBJECT = 29156;
    private final List<Integer> GRAND_EXCHANGE_BOOTH_OBJECTS = List.of(10060, 10061, 30389);

    private final int HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE = 3407881;
    private final int ORNATE_JEWELLERY_BOX_GE_WIDGET = 0x024e_0006;

    private final Map<Integer, Integer> lecternToHouseTabButton = Map.of(
            ObjectID.POH_LECTERN_6, 26411031,
            ObjectID.POH_LECTERN_8, 26411033
    );

    private final HOUSETABS_CONFIG houseTabConfig;
    private final String[] playerHouses;

    private final ScheduledExecutorService scheduledExecutorService;

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
    private TabletQuantityMode confirmedQuantityMode = null;
    private int lecternCraftActions = 0;
    private HouseTablet lastPreparedTablet = null;
    private int debugLoopCount = 0;
    private boolean phialsUnnotePending = false;
    private long phialsUnnoteAttemptedAt = 0;
    private boolean lecternStudyPending = false;
    private long lecternStudyAttemptedAt = 0;
    private long lastLecternCraftAttemptAt = 0;
    private long lastAdvertisementViewAttemptAt = 0;
    private long lastWorldHopAttemptAt = 0;
    private int worldHopAttempts = 0;
    private int lastObservedMagicXp = -1;
    private int lastObservedUnnotedClay = -1;
    private long lastCraftProgressAt = 0;
    private String currentAdvertisedHouseName = "";
    private final Set<String> blacklistedAdvertisedHouses = new HashSet<>();
    private long lastCraftGateLogAt = 0;
    private String lastCraftGateLogReason = "";

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

    private boolean hasStaffFor(HouseTablet tablet) {
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
                || (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(itemId, 1));
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
        stopReason = reason;
        Microbot.status = reason;
        Microbot.log("HouseTab stopped: " + reason);
        shutdown();
    }

    public void stopFromPlugin(String reason) {
        stop(reason);
    }

    public void handlePlayerHouseOffline(boolean useAdvertisementBoard) {
        if (!useAdvertisementBoard) {
            stop("Configured player house is offline");
            return;
        }
        skipVisitLastHouse = true;
        Microbot.status = "Last house offline; selecting a fresh advertised house";
        Microbot.log("HouseTab: last house offline, falling back to house advertisement board.");
    }

    private void updatePlanSummary(HouseTabConfig config) {
        planSummary = (config.progressive() ? "Progressive: " : "Tablet: ")
                + selectedTablet.getName()
                + " | XP " + selectedTablet.getMagicXp()
                + " | Qty " + config.quantityMode();
    }

    private void updateTabletCount() {
        int current = Rs2Inventory.count(selectedTablet.getItemId());
        if (current > lastKnownOutputCount) {
            tabletsMade += current - lastKnownOutputCount;
        }
        lastKnownOutputCount = current;
    }

    private void resetTracking() {
        startMagicXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        startMagicLevel = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
        tabletsMade = 0;
        lastKnownOutputCount = Rs2Inventory.count(selectedTablet.getItemId());
        stopReason = "";
        dumpedCurrentTabletInterface = false;
        dumpedTabletInterfaceFor = null;
        confirmedQuantityMode = null;
        lecternCraftActions = 0;
        lastPreparedTablet = null;
        phialsUnnotePending = false;
        phialsUnnoteAttemptedAt = 0;
        lecternStudyPending = false;
        lecternStudyAttemptedAt = 0;
        lastLecternCraftAttemptAt = 0;
        lastWorldHopAttemptAt = 0;
        worldHopAttempts = 0;
        lastObservedMagicXp = startMagicXp;
        lastObservedUnnotedClay = unnotedSoftClayCount();
        lastCraftProgressAt = 0;
        currentAdvertisedHouseName = "";
        lastCraftGateLogAt = 0;
        lastCraftGateLogReason = "";
    }

    private void dumpTabletWidgetsOnce(HouseTabConfig config) {
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

    private boolean ensureStaffEquipped(HouseTablet tablet) {
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

    private void depositMismatchedWeaponFor(HouseTablet tablet, boolean allowPartial) {
        if (!Rs2Bank.isOpen()) return;

        Rs2Staff bestStaff = bestAvailableStaffFor(tablet, allowPartial);
        if (bestStaff == Rs2Staff.NONE) {
            Microbot.log("HouseTab: no replacement staff available; keeping current weapon equipped.");
            return;
        }

        if (!Rs2Equipment.isWearing(bestStaff.getItemID()) && Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null) {
            Microbot.log("HouseTab: unequipping current weapon before progressive staff setup for " + tablet.getName());
            Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
            sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null, 3000);
        }
    }

    private boolean equippedStaffProvides(Runes rune) {
        return Rs2Magic.findStavesByRunes(List.of(rune)).stream()
                .map(Rs2Staff::getItemID)
                .anyMatch(Rs2Equipment::isWearing);
    }

    private boolean hasRune(Runes rune, int amount) {
        if (Rs2Inventory.hasRunePouch()) {Rs2RunePouch.fullUpdate();}
        if (equippedStaffProvides(rune)) return true;
        return Rs2Inventory.count(rune.getItemId()) >= amount || (Rs2Inventory.hasRunePouch() && Rs2RunePouch.contains(rune));
    }

    private boolean hasRequiredRunes() {
        return selectedTablet.getRuneRequirements().entrySet().stream()
                .allMatch(entry -> hasRune(entry.getKey(), entry.getValue()));
    }

    private boolean hasValidProgressiveLoadout(HouseTabConfig config) {
        if (!config.progressive()) {
            return true;
        }
        if (!hasSoftClay() || !hasRequiredRunes()) {
            return false;
        }
        return !config.useCombinationStaff() || isBestAvailableStaffEquipped(selectedTablet, false);
    }

    private boolean ensureHouseReturnTabsFromBank() {
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
                stop("Missing " + rune.name().toLowerCase() + " runes for " + selectedTablet.getName());
                return false;
            }
            if (!Rs2Bank.withdrawX(itemId, amount)) {
                stop("Unable to withdraw " + rune.name().toLowerCase() + " runes");
                return false;
            }
            int requiredAmount = amount;
            sleepUntil(() -> hasRune(rune, requiredAmount), 3000);
        }

        return hasRequiredRunes();
    }

    private void depositCraftedTeleportStacksForProgressive() {
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
        if (!config.progressive()) return config.tablet();
        int magicLevel = Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
        return HouseTablet.highestXpForLevel(magicLevel);
    }

    private boolean hasRequiredStaffOrFallback(HouseTabConfig config) {
        if (!config.useCombinationStaff()) return true;
        if (isBestAvailableStaffEquipped(selectedTablet, false)) return true;
        if (!Rs2Bank.isOpen() && hasStaffFor(selectedTablet) && ensureStaffEquipped(selectedTablet)) return true;

        if (Rs2Bank.isOpen()) {
            List<Rs2Staff> staves = rankedStavesFor(selectedTablet, false);
            Microbot.log("HouseTab: staff candidates for " + selectedTablet.getName() + ": "
                    + staves.stream()
                    .map(staff -> staff.name()
                            + "#" + staff.getItemID()
                            + " bank=" + Rs2Bank.hasBankItem(staff.getItemID(), 1)
                            + " inv=" + Rs2Inventory.hasItem(staff.getItemID())
                            + " worn=" + Rs2Equipment.isWearing(staff.getItemID()))
                    .collect(Collectors.joining(", ")));
            for (Rs2Staff staff : staves) {
                if (Rs2Equipment.isWearing(staff.getItemID())) {
                    return true;
                }
                if (Rs2Inventory.hasItem(staff.getItemID()) && Rs2Inventory.wield(staff.getItemID())) {
                    sleepUntil(() -> Rs2Equipment.isWearing(staff.getItemID()), 3000);
                    return Rs2Equipment.isWearing(staff.getItemID());
                }
                if (Rs2Bank.hasBankItem(staff.getItemID(), 1)) {
                    if (Rs2Bank.withdrawAndEquip(staff.getItemID())) {
                        sleepUntil(() -> Rs2Equipment.isWearing(staff.getItemID()), 3000);
                        if (Rs2Equipment.isWearing(staff.getItemID())) {
                            return true;
                        }
                    }
                    if (Rs2Bank.withdrawOne(staff.getItemID())) {
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
        Microbot.status = "Entering house for GE setup";
        Microbot.log("HouseTabScript: progressive prep needed outside house; entering house before GE travel. "
                + materialDebug());

        if (!hasSoftClay()) {
            if (hasSoftClayNoted()) {
                Microbot.log("HouseTabScript: progressive prep has noted clay but no unnoted clay; unnoting once before house entry.");
                unnoteClay();
                return;
            }
            stop("Missing soft clay for progressive setup");
            return;
        }

        if (config.useAdvertisementBoard()) {
            if (config.useLastHouse() && visitLastAdvertisedHouse()) {
                return;
            }
            lookForHouseAdvertisementObject();
            lookForPlayerHouse(config);
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
        if (isNearGrandExchangeByPosition()) {
            return true;
        }

        try {
            return Microbot.getRs2TileObjectCache().query()
                    .withNameContains("Grand Exchange booth")
                    .within(30)
                    .nearest() != null;
        } catch (Exception ex) {
            Microbot.log("HouseTab: GE detection skipped while scene cache is not ready.");
            return false;
        }
    }

    private boolean isNearRimmingtonAdvertisementByPosition() {
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
        try {
            return Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null
                    || Microbot.getRs2TileObjectCache().query().withId(ORNATE_JEWELLERY_BOX_OBJECT).nearest() != null
                    || getHouseLectern() != null;
        } catch (Exception ex) {
            Microbot.log("HouseTabScript: house scene detection skipped while client thread is busy.");
            return false;
        }
    }

    private boolean isNearGrandExchangeByPosition() {
        WorldPoint location = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getWorldLocation();
        }).orElse(null);
        if (location == null) {
            return false;
        }
        return location.getPlane() == 0
                && location.getX() >= 3150
                && location.getX() <= 3175
                && location.getY() >= 3475
                && location.getY() <= 3505;
    }

    private boolean isGameSceneReady() {
        try {
            return Microbot.getClient().getLocalPlayer() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean openGrandExchangeBank() {
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
        if (isAtGrandExchange()) {
            return true;
        }

        if (Microbot.getClient().getWidget(ORNATE_JEWELLERY_BOX_GE_WIDGET) == null) {
            Rs2TileObjectModel box = Microbot.getRs2TileObjectCache().query()
                    .withId(ORNATE_JEWELLERY_BOX_OBJECT)
                    .nearest();
            if (box == null) {
                return false;
            }
            if (!Microbot.getRs2TileObjectCache().query().interact(ORNATE_JEWELLERY_BOX_OBJECT, "Teleport")) {
                Microbot.getRs2TileObjectCache().query().interact(ORNATE_JEWELLERY_BOX_OBJECT, "Rub");
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
        if (!config.progressive() || !isAtGrandExchange()) {
            return false;
        }
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

        depositMismatchedWeaponFor(selectedTablet, false);
        depositCraftedTeleportStacksForProgressive();

        Rs2Bank.depositAllExcept(
                ItemID.POH_TABLET_TELEPORTTOHOUSE,
                ItemID.COINS,
                ItemID.LAWRUNE,
                1762);
        sleep(600, 900);

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

    private boolean returnToHousePortalFromGrandExchange() {
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
        return sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null, 10000);
    }

    public HouseTabScript(HOUSETABS_CONFIG houseTabConfig, String[] playerHouses) {
        this.houseTabConfig = houseTabConfig;
        this.playerHouses = playerHouses;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    private void lookForHouseAdvertisementObject() {
        Widget houseAdvertisementPanel = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (!hasSoftClay() || houseAdvertisementPanel != null || Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null)
            return;

        long now = System.currentTimeMillis();
        if (now - lastAdvertisementViewAttemptAt < 2500) {
            return;
        }

        boolean success = Microbot.getRs2TileObjectCache().query()
                .interact(HOUSE_ADVERTISEMENT_OBJECT, "View");
        if (success) {
            lastAdvertisementViewAttemptAt = now;
            transitionPause("opening house advertisement");
        }
    }

    private boolean visitLastAdvertisedHouse() {
        if (skipVisitLastHouse || !hasSelectedAdvertisedHouse) {
            return false;
        }
        if (!hasSoftClay() || Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null) {
            return false;
        }

        boolean success = physicallySelectVisitLastHouse();
        if (success) {
            sleepUntilOnClientThread(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null, 8000);
        }
        boolean enteredHouse = Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null;
        if (enteredHouse) {
            skipVisitLastHouse = false;
            advertisedHouseSkipCount = 0;
            enteredAdvertisedHouse = true;
            hasSelectedAdvertisedHouse = true;
        }
        return enteredHouse;
    }

    private boolean physicallySelectVisitLastHouse() {
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
        Microbot.getMouse().move(clickPoint);
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
        Microbot.getMouse().click(menuPoint);
        transitionPause("visit-last selected");
        return true;
    }

    private Point getObjectClickPoint(Rs2TileObjectModel object) {
        java.awt.Shape clickbox = Microbot.getClientThread().runOnClientThreadOptional(object::getClickbox).orElse(null);
        if (clickbox != null) {
            java.awt.Rectangle bounds = clickbox.getBounds();
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }
        return Microbot.getClientThread().runOnClientThreadOptional(object::getCanvasLocation).orElse(null);
    }

    private Point getMenuEntryClickPoint(String option, String target) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenuEntries();
            int menuX = Microbot.getClient().getMenuX();
            int menuY = Microbot.getClient().getMenuY();
            int menuWidth = Microbot.getClient().getMenuWidth();
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
                int x = menuX + menuWidth / 2;
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
        return houseName != null
                && !houseName.isBlank()
                && blacklistedAdvertisedHouses.contains(houseName.toLowerCase());
    }

    private void blacklistCurrentAdvertisedHouse(String reason) {
        if (currentAdvertisedHouseName == null || currentAdvertisedHouseName.isBlank()) {
            return;
        }
        blacklistedAdvertisedHouses.add(currentAdvertisedHouseName.toLowerCase());
        Microbot.log("HouseTab: blacklisted advertised house '" + currentAdvertisedHouseName + "' for this run: " + reason);
    }

    private String[] getAdvertisedHouseNames(HouseTabConfig config) {
        String configured = config.advertisedHouses();
        if (configured == null || configured.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toArray(String[]::new);
    }

    private void lookForPlayerHouse(HouseTabConfig config) {
        Widget houseAdvertisementNameWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (houseAdvertisementNameWidget == null || houseAdvertisementNameWidget.getChildren() == null) return;
        if (!hasSoftClay())
            return;
        if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null)
            return;

        int enterHouseButtonHeight = 21;
        int houseIndexToJoin = -1;

        String[] preferredHouses = getAdvertisedHouseNames(config);
        if (advertisedHouseSkipCount == 0 && preferredHouses.length > 0) {
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
        if (mainWindow == null) return;
        int HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE = 3407891;
        Widget houseAdvertisementEnterHouseWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE);
        if (houseAdvertisementEnterHouseWidget == null) return;
        if (houseIndexToJoin < 0 && houseAdvertisementEnterHouseWidget.getChildren() != null) {
            int visibleEnterHouseRows = 0;
            for (int i = 0; i < houseAdvertisementEnterHouseWidget.getChildren().length; i++) {
                Widget child = houseAdvertisementEnterHouseWidget.getChild(i);
                if (child != null && child.getActions() != null && Arrays.stream(child.getActions()).anyMatch("Enter House"::equalsIgnoreCase)) {
                    String houseName = getAdvertisedHouseName(houseAdvertisementNameWidget, i);
                    if (isAdvertisedHouseBlacklisted(houseName)) {
                        continue;
                    }
                    if (visibleEnterHouseRows >= advertisedHouseSkipCount) {
                        houseIndexToJoin = i;
                        break;
                    }
                    visibleEnterHouseRows++;
                }
            }
        }
        if (houseIndexToJoin < 0) {
            stop("No advertised house entry is visible");
            return;
        }
        Widget enterHouseButton = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin);
        currentAdvertisedHouseName = getAdvertisedHouseName(houseAdvertisementNameWidget, houseIndexToJoin);
        int buttonRelativeY = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin).getRelativeY() + enterHouseButtonHeight;
        if (buttonRelativeY > (mainWindow.getScrollY() + mainWindow.getHeight())) {
            keepExecuteUntil(() -> {
                int x = (int) mainWindow.getBounds().getCenterX() + Rs2Random.between(-50, 50);
                int y = (int) mainWindow.getBounds().getCenterY() + Rs2Random.between(-50, 50);
                Microbot.getMouse().scrollDown(new Point(x, y));
            }, () -> buttonRelativeY <= (mainWindow.getScrollY() + mainWindow.getHeight()), 500);
        } else {
            transitionPause("selecting advertised house");
            Microbot.getMouse()
                    .click(enterHouseButton.getCanvasLocation());
            sleepUntilOnClientThread(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null, 10000);
            if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null) {
                skipVisitLastHouse = false;
                enteredAdvertisedHouse = true;
                hasSelectedAdvertisedHouse = true;
                Microbot.log("HouseTabScript: entered advertised house"
                        + (currentAdvertisedHouseName.isBlank() ? "." : " hosted by " + currentAdvertisedHouseName + "."));
                transitionPause("entered advertised house");
            } else {
                advertisedHouseSkipCount++;
                skipVisitLastHouse = true;
                Microbot.log("HouseTabScript: advertised house entry timed out; will try next listing. skipCount=" + advertisedHouseSkipCount);
            }
            sleep(2000, 3000);
        }
    }

    private Integer getHouseLectern() {
        Rs2TileObjectModel lectern = null;
        for (Integer id : lecternToHouseTabButton.keySet()) {
            lectern = Microbot.getRs2TileObjectCache().query().withId(id).nearest();
            if (lectern != null && selectedTablet.supportsLectern(lectern.getId())) break;
            lectern = null;
        }
        if (lectern != null) {
            lecternTabletWidgetId = selectedTablet.hasKnownWidget()
                    ? selectedTablet.getWidgetId()
                    : lecternToHouseTabButton.get(lectern.getId());
            return lectern.getId();
        }

        return null;
    }

    public void lookForLectern() {
        if (getHouseLectern() == null) {
            lecternStudyPending = false;
            lecternStudyAttemptedAt = 0;
            stop("No compatible lectern found for " + selectedTablet.getName());
            return;
        }
        if (!hasSoftClay() || Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null || isTabletCraftingActive())
            return;

        Widget houseTabInterface = Microbot.getClient().getWidget(lecternTabletWidgetId);
        if (houseTabInterface != null) {
            lecternStudyPending = false;
            lecternStudyAttemptedAt = 0;
            return;
        }
        if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null) return;

        if (lecternStudyPending && System.currentTimeMillis() - lecternStudyAttemptedAt < 8000) {
            Microbot.log("HouseTabScript: waiting for lectern interface.");
            return;
        }

        Microbot.log("HouseTabScript: studying lectern.");
        boolean success = Microbot.getRs2TileObjectCache().query().withIds(lecternToHouseTabButton.keySet().stream().mapToInt(Integer::intValue).toArray()).interact("Study");
        if (success) {
            lecternStudyPending = true;
            lecternStudyAttemptedAt = System.currentTimeMillis();
        }
    }

    private boolean isTabletCraftingActive() {
        long now = System.currentTimeMillis();
        int unnotedClay = unnotedSoftClayCount();
        boolean recentCraftProgress = now - lastCraftProgressAt < 1800;
        boolean animationActive = isTabletCraftingAnimationActive();
        long sinceCraftClick = now - lastLecternCraftAttemptAt;
        long sinceCraftProgress = now - lastCraftProgressAt;
        String reason;
        boolean active;
        if (unnotedClay <= 0) {
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
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() != null
                        && Microbot.getClient().getLocalPlayer().getAnimation() == 4068).orElse(false);
    }

    private void refreshCraftProgress() {
        int currentMagicXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        int currentUnnotedClay = unnotedSoftClayCount();
        if ((lastObservedMagicXp >= 0 && currentMagicXp > lastObservedMagicXp)
                || (lastObservedUnnotedClay >= 0 && currentUnnotedClay < lastObservedUnnotedClay)) {
            lastCraftProgressAt = System.currentTimeMillis();
            updateTabletCount();
            if (currentUnnotedClay <= 3) {
                Microbot.log("HouseTab craft progress: xp=" + lastObservedMagicXp + "->" + currentMagicXp
                        + " clay=" + lastObservedUnnotedClay + "->" + currentUnnotedClay
                        + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
            }
        }
        lastObservedMagicXp = currentMagicXp;
        lastObservedUnnotedClay = currentUnnotedClay;
    }

    private void logCraftGate(String reason, boolean active, int unnotedClay, long sinceCraftClick, long sinceCraftProgress, boolean animationActive) {
        if (unnotedClay > 3 && active) {
            return;
        }
        long now = System.currentTimeMillis();
        if (reason.equals(lastCraftGateLogReason) && now - lastCraftGateLogAt < 2500) {
            return;
        }
        lastCraftGateLogReason = reason;
        lastCraftGateLogAt = now;
        Microbot.log("HouseTab craft gate: active=" + active
                + " reason=" + reason
                + " clay=" + unnotedClay
                + " sinceClickMs=" + sinceCraftClick
                + " sinceProgressMs=" + sinceCraftProgress
                + " animation=" + animationActive
                + " gainingXp=" + Microbot.isGainingExp
                + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
    }

    public void createHouseTablet(HouseTabConfig config) {
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
        if (!ensureQuantityMode(config.quantityMode())) {
            return;
        }

        if (config.quantityMode() == TabletQuantityMode.MAKE_ONE) {
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
            return;
        }
        lastLecternCraftAttemptAt = System.currentTimeMillis();
        Microbot.log("HouseTabScript: selecting " + selectedTablet.getName() + " on lectern.");
        Microbot.getMouse().click(houseTabInterface.getCanvasLocation());
        sleep(250, 500);
        Rs2Widget.clickWidget(InterfaceID.TeletabsCraftIf.CONFIRM);
        maybeLecternAntiban();
        updateTabletCount();
    }

    private boolean ensureQuantityMode(TabletQuantityMode quantityMode) {
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

    private void maybeLecternAntiban() {
        lecternCraftActions++;
        if (Rs2Random.between(1, 100) <= 18) {
            sleep(250, 850);
        }
        if (lecternCraftActions % Rs2Random.between(4, 8) == 0) {
            Microbot.getMouse().move(new Point(Rs2Random.between(120, 720), Rs2Random.between(120, 460)));
        }
        if (Rs2Random.between(1, 100) <= 8) {
            Rs2Camera.setAngle(Rs2Random.between(0, 359), 30);
        }
    }

    private void transitionPause(String reason) {
        int pause = Rs2Random.between(320, 860);
        if (Rs2Random.between(1, 100) <= 8) {
            pause += Rs2Random.between(500, 1100);
        }
        Microbot.log("HouseTabScript: transition pause after " + reason + " for " + pause + "ms.");
        sleep(pause, pause + 80);
        if (Rs2Random.between(1, 100) <= 12) {
            Microbot.getMouse().move(new Point(Rs2Random.between(120, 720), Rs2Random.between(120, 460)));
        }
        if (Rs2Random.between(1, 100) <= 5) {
            Rs2Camera.setAngle(Rs2Random.between(0, 359), 30);
        }
    }

    public void leaveHouse() {
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
        if (Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null) {
            return;
        }

        advertisedHouseSkipCount++;
        enteredAdvertisedHouse = false;
        hasSelectedAdvertisedHouse = false;
        skipVisitLastHouse = true;
        Microbot.status = "No nearby lectern; trying advertised house #" + (advertisedHouseSkipCount + 1);
        Microbot.log("HouseTab: advertised house had no nearby compatible lectern, trying next listing.");
        blacklistCurrentAdvertisedHouse("no nearby compatible lectern");

        if (!teleportToHousePortal()) {
            leaveHousePortal();
        }
    }

    private boolean teleportToHousePortal() {
        if (!Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
            Microbot.log("HouseTab: no house tablet available for bad-house recovery; falling back to portal.");
            return false;
        }
        Microbot.log("HouseTab: breaking house tablet to recover from bad advertised house.");
        if (!Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Outside")) {
            Rs2Inventory.interact(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break");
        }
        return sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_ADVERTISEMENT_OBJECT).nearest() != null, 10000);
    }

    private boolean leaveHousePortal() {
        Rs2TileObjectModel portal = Microbot.getRs2TileObjectCache().query()
                .withId(HOUSE_PORTAL_OBJECT)
                .nearest();
        if (portal == null) {
            return true;
        }

        try {
            sleep(180, 420);
            if (Microbot.getRs2TileObjectCache().query().interact(HOUSE_PORTAL_OBJECT, "Enter")
                    && sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null, 8000)) {
                transitionPause("leaving house");
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
        boolean leftHouse = sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() == null, 8000);
        if (leftHouse) {
            transitionPause("leaving house");
        }
        return leftHouse;
    }

    public boolean unnoteClay() {
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
            sleep(300, 380);
            phialsUnnotePending = false;
            phialsUnnoteAttemptedAt = 0;
            transitionPause("Phials unnote");
            return true;
        }
        return false;
    }

    public boolean run(HouseTabConfig config) {
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
                    if (shouldLogLoop) Microbot.log("HouseTabScript: waiting for login. loop=" + debugLoopCount);
                    return;
                }
                if (!isGameSceneReady()) {
                    if (shouldLogLoop) Microbot.log("HouseTabScript: waiting for game scene. loop=" + debugLoopCount);
                    return;
                }
                if (!ensureTargetWorld(config.targetWorld())) {
                    return;
                }
                ScriptHeartbeatRegistry.recordHeartbeat(this.getClass().getName());
                selectedTablet = resolveSelectedTablet(config);
                updatePlanSummary(config);
                if (startMagicXp < 0 || startMagicLevel < 0) {
                    resetTracking();
                    Microbot.log("HouseTabScript: tracking initialized. selected=" + selectedTablet.getName()
                            + ", magicLevel=" + startMagicLevel
                            + ", magicXp=" + startMagicXp);
                }
                refreshCraftProgress();
                if (config.progressive()) {
                    runProgressiveLoop(config, shouldLogLoop);
                    return;
                }
                runClassicLoop(config, shouldLogLoop);
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            } finally {
                long elapsed = System.currentTimeMillis() - loopStartedAt;
                if (elapsed > 5000) {
                    Microbot.log("HouseTabScript: loop=" + debugLoopCount + " took " + elapsed + "ms.");
                }
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean ensureTargetWorld(int targetWorld) {
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

    private void runProgressiveLoop(HouseTabConfig config, boolean shouldLogLoop) {
        if (shouldLogLoop) {
            Microbot.log("HouseTabScript: progressive tick=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " atRimmington=" + isNearRimmingtonAdvertisementByPosition()
                    + " hasUnnotedClay=" + hasSoftClay()
                    + " hasNotedClay=" + hasSoftClayNoted());
        }
        boolean insidePlayerHouse = isInsidePlayerHouse();
        if (insidePlayerHouse && !hasSoftClay()) {
            if (shouldWaitForFinalTabletCraft()) {
                return;
            }
            Microbot.status = "Leaving house";
            Microbot.log("HouseTabScript: progressive exit branch leaving house. loop=" + debugLoopCount
                    + " clay=" + unnotedSoftClayCount()
                    + " noted=" + notedSoftClayCount()
                    + " output=" + Rs2Inventory.count(selectedTablet.getItemId()));
            leaveHousePortal();
            return;
        }
        if (!insidePlayerHouse && !hasSoftClay() && hasSoftClayNoted()) {
            Microbot.status = "Unnoting soft clay";
            if (unnoteClay()) {
                return;
            }
        }
        if (hasSoftClay()
                && !insidePlayerHouse) {
            Microbot.status = "Entering advertised house";
            if (config.useAdvertisementBoard()) {
                if (config.useLastHouse() && visitLastAdvertisedHouse()) {
                    return;
                }
                lookForHouseAdvertisementObject();
                lookForPlayerHouse(config);
                return;
            }
        }
        if (insidePlayerHouse && hasSoftClay()) {
            runClassicLoop(config, shouldLogLoop);
            return;
        }

        boolean atGrandExchange = isAtGrandExchange();
        boolean validProgressiveLoadout = hasValidProgressiveLoadout(config);
        boolean progressiveBankPrepNeeded = needsProgressiveBankPrep(config);
        if (shouldLogLoop) {
            Microbot.log("HouseTabScript: progressive loop=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " atGE=" + atGrandExchange
                    + " validLoadout=" + validProgressiveLoadout
                    + " prepNeeded=" + progressiveBankPrepNeeded
                    + " " + materialDebug());
        }
        if (atGrandExchange) {
            if (!validProgressiveLoadout || !Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE)) {
                Microbot.log("HouseTabScript: GE progressive loadout needs bank prep.");
                prepareProgressiveLoadoutAtGrandExchange(config);
            } else {
                Microbot.log("HouseTabScript: GE progressive loadout already valid; returning to house portal.");
                returnToHousePortalFromGrandExchange();
            }
            return;
        }
        if (!insidePlayerHouse && !hasSoftClay() && hasSoftClayNoted()) {
            if (unnoteClay()) {
                return;
            }
        }
        if (progressiveBankPrepNeeded
                && insidePlayerHouse
                && travelToGrandExchangeFromHouse()) {
            return;
        }
        if (progressiveBankPrepNeeded) {
            enterHouseForProgressivePrep(config);
            return;
        }
        runClassicLoop(config, shouldLogLoop);
    }

    private void runClassicLoop(HouseTabConfig config, boolean shouldLogLoop) {
        if (shouldLogLoop) {
            Microbot.log("HouseTabScript: classic loop=" + debugLoopCount
                    + " selected=" + selectedTablet.getName()
                    + " " + materialDebug());
        }
        if (unnotedSoftClayCount() <= 3) {
            Microbot.log("HouseTabScript: low-clay classic loop. loop=" + debugLoopCount
                    + " insideHouse=" + (getHouseLectern() != null)
                    + " clay=" + unnotedSoftClayCount()
                    + " output=" + Rs2Inventory.count(selectedTablet.getItemId())
                    + " gainingXp=" + Microbot.isGainingExp);
        }
        if (!hasRequiredStaffOrFallback(config)) {
            return;
        }
        boolean isInHouse = getHouseLectern() != null;
        if (isInHouse && !hasSoftClay()) {
            if (shouldWaitForFinalTabletCraft()) {
                return;
            }
            Microbot.status = "Leaving house";
            Microbot.log("HouseTabScript: classic no-clay exit before xp gate. loop=" + debugLoopCount
                    + " clay=" + unnotedSoftClayCount()
                    + " output=" + Rs2Inventory.count(selectedTablet.getItemId())
                    + " gainingXp=" + Microbot.isGainingExp);
            leaveHousePortal();
            return;
        }
        if (!hasAnySoftClay() || !hasRequiredRunes()) {
            stop(!hasAnySoftClay() ? "Missing soft clay" : "Missing runes for " + selectedTablet.getName());
            return;
        }
        if (Microbot.isGainingExp) return;

        Rs2Player.toggleRunEnergy(true);
        if (Microbot.getClient().getEnergy() < 3000 && !Rs2Widget.hasWidget("Teleport to House") && Microbot.getRs2TileObjectCache().query().withIds(ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION).nearest() != null) {
            Microbot.getRs2TileObjectCache().query().withIds(ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION).interact("drink");
            return;
        }

        if (isInHouse) {
            advertisedHouseSkipCount = 0;
            enteredAdvertisedHouse = false;
            if (isTabletCraftingActive()) {
                updateTabletCount();
                return;
            }
            lookForLectern();
            createHouseTablet(config);
            leaveHouse();
        } else if (config.useAdvertisementBoard()
                && enteredAdvertisedHouse
                && Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_OBJECT).nearest() != null) {
            leaveBadAdvertisedHouse();
        } else {
            if (unnoteClay()) {
                return;
            }
            if (config.useAdvertisementBoard()) {
                if (config.useLastHouse() && visitLastAdvertisedHouse()) {
                    return;
                }
                lookForHouseAdvertisementObject();
                lookForPlayerHouse(config);
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
                if (config.useLastHouse() && !Rs2Widget.hasWidget("Enter name")) {
                    sleep(800, 1200);
                } else if (!config.housePlayerName().isBlank() && Rs2Widget.hasWidget(config.housePlayerName())) {
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
