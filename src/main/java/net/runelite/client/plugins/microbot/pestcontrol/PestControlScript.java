package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
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
    boolean walkToCenter = false;
    private boolean wasInPestControl = false;
    private boolean pendingPostRoundRestore = false;
    private Portal selectedPortal = null;
    private String primaryWeaponName = null;
    private final Set<String> missingWeaponsLogged = new HashSet<>();
    private final Set<String> missingAttackOptionsLogged = new HashSet<>();
    private boolean missingPrimaryWeaponLogged = false;
    private String managedAttackOptionWeapon = null;
    private String managedAttackOptionName = null;
    private int managedAttackOptionIndex = -1;
    private long lastBoardingAttemptAt = 0L;
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

    private static final Set<Integer> BRAWLER_IDS = ImmutableSet.of(
            NpcID.BRAWLER,
            NpcID.BRAWLER_1736,
            NpcID.BRAWLER_1738,
            NpcID.BRAWLER_1737,
            NpcID.BRAWLER_1735
    );

    final int distanceToPortal = 8;
    private static final int PORTAL_CROWD_RADIUS = 12;
    private static final int PORTAL_MATCH_RADIUS = 5;
    private static final int SPINNER_PORTAL_RADIUS = 8;
    private static final int PORTAL_SWITCH_CROWD_MARGIN = 1;
    private static final long BOARDING_RETRY_MILLIS = 600L;
    public static final boolean DEBUG = false;

    public static List<Portal> portals = List.of(PURPLE, BLUE, RED, YELLOW);

    private void resetPortals() {
        for (Portal portal : portals) {
            portal.setHasShield(true);
        }
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
                from.getPlane()
        );
    }

    public boolean run(PestControlConfig config) {
        this.config = config;
        selectedPortal = null;
        pendingPostRoundRestore = false;
        primaryWeaponName = configuredPrimaryWeaponName();
        missingWeaponsLogged.clear();
        missingAttackOptionsLogged.clear();
        missingPrimaryWeaponLogged = false;
        managedAttackOptionWeapon = null;
        managedAttackOptionName = null;
        managedAttackOptionIndex = -1;
        lastBoardingAttemptAt = 0L;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                final boolean isInPestControl = isInPestControl();
                final boolean isInBoat = isInBoat();

                if (initialise && !isInPestControl && !isInBoat) {
                    Microbot.log("Initialising");
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
                        Microbot.log("Traveling to Pest Island");
                        Rs2Walker.walkTo(new WorldPoint(2667, 2653, 0));
                    }
                }
                if (isInPestControl) {
                    initialise = false;
                    wasInPestControl = true;
                    pendingPostRoundRestore = false;
                    lastBoardingAttemptAt = 0L;
                    if (!isQuickPrayerEnabled() && Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) != 0 && config.quickPrayer()) {
                        final Widget prayerOrb = Rs2Widget.getWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
                        if (prayerOrb != null) {
                            Microbot.getMouse().click(prayerOrb.getCanvasLocation());
                            sleep(1000, 1500);
                        }
                    }
                    if (!walkToCenter) {
                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                        WorldPoint worldPoint = WorldPoint.fromRegion(playerLoc.getRegionID(), 32, 17, playerLoc.getPlane());
                        if (playerLoc.distanceTo(worldPoint) <= 4) {
                            walkToCenter = true;
                        } else {
                            Rs2Walker.walkMiniMap(stepTowards(playerLoc, worldPoint, 14));
                            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                            return;
                        }
                    }

                    activateSpecialAttackIfReady();

                    if (Microbot.getClient().getLocalPlayer().isInteracting())
                        return;

                    var brawler = Microbot.getRs2NpcCache().query().withName("brawler").nearestOnClientThread();
                    if (brawler != null && brawler.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 3) {
                        brawler.click("Attack");
                        return;
                    }

                    if (isActivityLow()) {
                        if (attackPortals()) {
                            return;
                        }
                        Rs2NpcModel attackableNpc = nearestAttackablePest();
                        if (attackableNpc != null) {
                            attackableNpc.click("Attack");
                        }
                        return;
                    }


                    if (handleAttack(PestControlNpc.BRAWLER, 1)
                            || handleAttack(PestControlNpc.PORTAL, 1)
                            || handleAttack(PestControlNpc.SPINNER, 1)) {
                        return;
                    }

                    if (handleAttack(PestControlNpc.BRAWLER, 2)
                            || handleAttack(PestControlNpc.PORTAL, 2)
                            || handleAttack(PestControlNpc.SPINNER, 2)) {
                        return;
                    }
                    if (handleAttack(PestControlNpc.BRAWLER, 3)
                            || handleAttack(PestControlNpc.PORTAL, 3)
                            || handleAttack(PestControlNpc.SPINNER, 3)) {
                        return;
                    }
                    if (!attackPortals()) {
                        if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
                            Rs2NpcModel attackableNpc = Microbot.getRs2NpcCache().query()
                                    .where(n -> n.getNpc() != null && !n.getNpc().isDead() && n.getNpc().getCombatLevel() > 0)
                                    .nearestOnClientThread();
                            if (attackableNpc != null) attackableNpc.click("Attack");
                        }
                    }

                } else {
                    if (wasInPestControl) {
                        Rs2Walker.setTarget(null);
                        wasInPestControl = false;
                        pendingPostRoundRestore = true;
                        selectedPortal = null;
                        walkToCenter = false;
                        Microbot.log("Pest Control round ended; reboarding immediately");
                    }
                    if (!isInBoat && !initialise) {
                        boardBoat();
                        return;
                    }

                    resetPortals();
                    walkToCenter = false;
                    if (isInBoat) {
                        lastBoardingAttemptAt = 0L;
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
            } catch (Exception ex) {
                ex.printStackTrace();
                Microbot.log(ex.getMessage());
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isActivityLow() {
        Widget activity = Rs2Widget.getWidget(26738700); // 145 pixels = 100%
        Widget activityBar = activity == null ? null : activity.getChild(0);
        return activityBar != null && activityBar.getWidth() <= 20;
    }

    private Rs2NpcModel nearestAttackablePest() {
        return Microbot.getClientThread().invoke(() ->
                Microbot.getRs2NpcCache().query()
                        .where(n -> n.getNpc() != null
                                && !n.getNpc().isDead()
                                && n.getNpc().getCombatLevel() > 0)
                        .nearest());
    }

    private boolean boardBoat() {
        long now = System.currentTimeMillis();
        if (now - lastBoardingAttemptAt < BOARDING_RETRY_MILLIS) {
            return false;
        }
        lastBoardingAttemptAt = now;

        int combatLevel = Microbot.getClient().getLocalPlayer().getCombatLevel();
        int gangplankId = combatLevel >= 100
                ? ObjectID.GANGPLANK_25632
                : combatLevel >= 70
                ? ObjectID.GANGPLANK_25631
                : ObjectID.GANGPLANK_14315;
        return Microbot.getRs2TileObjectCache().query().interact(gangplankId);
    }

    public boolean isOutside() {
        WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        return playerLoc.distanceTo(new WorldPoint(2644, 2644, 0)) < 20;
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
        if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 100) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25630);
        } else if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 70) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25629);
        } else {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_14314);
        }
        sleepUntil(() -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) == null, 3000);

    }

    private boolean handleAttack(PestControlNpc npcType, int priority) {
        if (priority == 1) {
            if (config.Priority1() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        } else if (priority == 2) {
            if (config.Priority2() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        } else {
            if (config.Priority3() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        }

        return false;
    }

    private static boolean attackPortal(Rs2NpcModel npcPortal) {
        if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
            if (npcPortal == null) return false;
            NPCComposition npc = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getNpcDefinition(npcPortal.getId())).orElse(null);
            if (npc == null) return false;

            if (Arrays.stream(npc.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase("attack"))) {
                LocalPoint localPoint = npcPortal.getLocalLocation();
                if (localPoint != null && !Rs2Camera.isTileOnScreen(localPoint)) {
                    WorldPoint npcWp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                            npcPortal.getNpc().getWorldLocation()).orElse(null);
                    WorldPoint playerWp = Rs2Player.getWorldLocation();
                    if (npcWp != null && playerWp != null) {
                        int angle = (int) Math.toDegrees(Math.atan2(
                                npcWp.getY() - playerWp.getY(),
                                npcWp.getX() - playerWp.getX()));
                        if (angle < 0) angle += 360;
                        angle = (angle - 90) % 360;
                        if (angle < 0) angle += 360;
                        Rs2Camera.setAngle(angle, 40);
                    }
                }
                return npcPortal.click("Attack");
            } else {
                return false;
            }
        }
        return false;
    }


    private boolean attackPortals() {
        PortalTarget target = selectAdaptivePortalTarget();
        if (target == null) {
            restorePrimaryWeapon();
            return false;
        }

        if (selectedPortal != target.portal) {
            selectedPortal = target.portal;
            Microbot.log("Pest Control target: " + target.portal
                    + " portal (" + target.nearbyPlayers + " other players nearby)");
        }

        WorldPoint portalLocation = target.npc.getWorldLocation();
        if (portalLocation == null) {
            return false;
        }

        prepareWeaponForPortal(target.portal);

        Rs2NpcModel spinner = findSpinnerNear(portalLocation);
        if (spinner != null) {
            return spinner.click("Attack");
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && playerLocation.distanceTo(portalLocation) > distanceToPortal) {
            Rs2Walker.walkTo(portalLocation, 5);
            return true;
        }

        return attackPortal(target.npc);
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
            Widget styleText = Rs2Widget.getWidget(styleWidgets[index].getId() + 3);
            int score = scoreAttackOption(styleText == null ? null : styleText.getText(), desiredStyle);
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
        boolean selected = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex
                || Rs2Combat.setAttackStyle(styleWidgets[selectedIndex])
                && sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex, 2000);
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
     * Select only portals that currently expose an Attack action. Join the
     * largest player group to finish one portal at a time, retain the current
     * live target across small crowd fluctuations, and use purple as a tie-break.
     */
    private PortalTarget selectAdaptivePortalTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer == null) {
                return null;
            }

            List<Rs2NpcModel> attackablePortals = Microbot.getRs2NpcCache().query()
                    .withName("portal")
                    .where(this::hasAttackAction)
                    .toList();

            List<WorldPoint> otherPlayers = Microbot.getRs2PlayerCache().getStream()
                    .filter(player -> player.getPlayer() != localPlayer)
                    .map(player -> player.getWorldLocation())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            WorldPoint playerLocation = localPlayer.getWorldLocation();
            List<PortalTarget> targets = attackablePortals.stream()
                    .map(npc -> toPortalTarget(npc, otherPlayers, playerLocation))
                    .filter(Objects::nonNull)
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
        return composition != null && Arrays.stream(composition.getActions())
                .anyMatch(action -> action != null && action.equalsIgnoreCase("attack"));
    }

    private PortalTarget toPortalTarget(
            Rs2NpcModel npc,
            List<WorldPoint> otherPlayers,
            WorldPoint playerLocation) {
        WorldPoint location = npc.getWorldLocation();
        if (location == null) {
            return null;
        }

        Portal portal = portals.stream()
                .min(Comparator.comparingInt(candidate -> regionDistance(
                        location,
                        candidate.getRegionX(),
                        candidate.getRegionY())))
                .filter(candidate -> regionDistance(
                        location,
                        candidate.getRegionX(),
                        candidate.getRegionY()) <= PORTAL_MATCH_RADIUS)
                .orElse(null);
        if (portal == null) {
            return null;
        }

        int nearbyPlayers = (int) otherPlayers.stream()
                .filter(player -> regionDistance(
                        player,
                        portal.getRegionX(),
                        portal.getRegionY()) <= PORTAL_CROWD_RADIUS)
                .count();
        int distance = playerLocation == null
                ? Integer.MAX_VALUE
                : playerLocation.distanceTo(location);
        return new PortalTarget(portal, npc, nearbyPlayers, distance);
    }

    private Rs2NpcModel findSpinnerNear(WorldPoint portalLocation) {
        return Microbot.getRs2NpcCache().query()
                .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(spinner -> spinner.getNpc() != null
                        && !spinner.getNpc().isDead()
                        && spinner.getWorldLocation() != null
                        && spinner.getWorldLocation().distanceTo(portalLocation) <= SPINNER_PORTAL_RADIUS)
                .nearestOnClientThread(portalLocation, SPINNER_PORTAL_RADIUS);
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

        private PortalTarget(Portal portal, Rs2NpcModel npc, int nearbyPlayers, int distance) {
            this.portal = portal;
            this.npc = npc;
            this.nearbyPlayers = nearbyPlayers;
            this.distance = distance;
        }
    }

    private boolean attackSpinner() {
        for (int spinner : SPINNER_IDS) {
            if (Microbot.getRs2NpcCache().query().withId(spinner).interact("Attack")) {
                return true;
            }
        }
        return false;
    }

    private boolean attackBrawler() {
        for (int brawler : BRAWLER_IDS) {
            if (Microbot.getRs2NpcCache().query().withId(brawler).interact("Attack")) {
                return true;
            }
        }
        return false;
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
        walkToCenter = false;
        wasInPestControl = false;
        pendingPostRoundRestore = false;
        selectedPortal = null;
        lastBoardingAttemptAt = 0L;
        super.shutdown();
    }
}
