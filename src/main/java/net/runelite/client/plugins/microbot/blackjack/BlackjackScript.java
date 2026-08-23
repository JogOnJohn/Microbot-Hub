package net.runelite.client.plugins.microbot.blackjack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BlackjackScript extends Script
{
    private static final WorldArea SUPPORTED_HOUSE = new WorldArea(3357, 2991, 4, 5, 0);
    private static final WorldPoint HOUSE_CENTRE = new WorldPoint(3358, 2993, 0);
    private static final WorldPoint COMBAT_STAGING_TILE = new WorldPoint(3359, 2995, 0);
    private static final WorldPoint COMBAT_SAFE_TILE = new WorldPoint(3360, 2993, 0);
    private static final WorldPoint WINE_DOOR_INSIDE_TILE = COMBAT_SAFE_TILE;
    private static final WorldPoint WINE_DOOR_OUTSIDE_TILE = new WorldPoint(3362, 2993, 0);
    private static final WorldPoint WINE_MERCHANT_TILE = new WorldPoint(3359, 2990, 0);
    private static final String WINE_EXIT_OBJECT_NAME = "Curtain";

    private static final int WINE_ID = 1993;
    private static final int NOTED_WINE_ID = 1994;
    private static final int COINS_ID = 995;
    private static final int WINE_HEAL_AMOUNT = 11;
    private static final int WINE_EXCHANGE_COST = 5;
    private static final int EARLY_WINE_RESTOCK_HITPOINTS = 40;
    private static final int EARLY_WINE_RESTOCK_MAX_WINES = 1;

    private static final long FAILED_KNOCKOUT_RETRY_MS = 450;
    private static final long PICKPOCKET_BURST_TIMEOUT_MS = 2_800;
    private static final long KNOCKOUT_MENU_TIMEOUT_MS = 1_200;
    private static final long KNOCKOUT_CONFIRM_TIMEOUT_MS = 1_500;
    private static final int KNOCKOUT_DISPATCH_FALLBACK_MIN_MS = 550;
    private static final int KNOCKOUT_DISPATCH_FALLBACK_MAX_MS = 676;
    private static final long SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS = 2_400;
    private static final int KNOCKOUT_MENU_DWELL_MIN_MS = 180;
    private static final int KNOCKOUT_MENU_DWELL_MAX_MS = 261;
    private static final int FIRST_PICKPOCKET_DELAY_MIN_MS = 30;
    private static final int FIRST_PICKPOCKET_DELAY_MAX_MS = 71;
    private static final int PICKPOCKET_CLICK_DELAY_MIN_MS = 75;
    private static final int PICKPOCKET_CLICK_DELAY_MAX_MS = 126;
    private static final int BURST_WANDER_CHANCE_PERCENT = 82;
    private static final int BURST_WANDER_MAX_X = 8;
    private static final int BURST_WANDER_MAX_UP = 8;
    private static final int BURST_WANDER_MAX_DOWN = 4;
    private static final long INVENTORY_FULL_SIGNAL_MAX_AGE_MS = 1_000;
    private static final long ACTIVE_INTERACTION_GRACE_MS = 350;
    private static final long MISCLICK_MOVEMENT_WINDOW_MS = 800;
    private static final long DRINK_COOLDOWN_MS = 1_750;
    private static final long COMBAT_CLEAR_SETTLE_MS = 650;
    private static final long COMBAT_RESET_RETRY_MS = 2_500;
    private static final long FAILED_KNOCKOUT_RETALIATION_GRACE_MS = 1_800;
    private static final long SUSTAINED_NPC_ATTACK_MS = 3_000;
    private static final long CAMERA_PIVOT_COOLDOWN_MS = 1_000;
    private static final int CAMERA_PIVOT_THRESHOLD = 128;
    private static final int CAMERA_PIVOT_STEP = 192;
    private static final long CAMERA_PITCH_COOLDOWN_MS = 1_000;
    private static final long DOOR_INTERACTION_DELAY_MS = 650;
    private static final int TOP_DOWN_CAMERA_PITCH = 383;
    private static final int TOP_DOWN_CAMERA_TOLERANCE = 8;
    private static final long HUMANIZER_MOUSE_MIN_INTERVAL_MS = 45_000;
    private static final long HUMANIZER_MOUSE_MAX_INTERVAL_MS = 120_001;
    private static final long HUMANIZER_MISTAKE_MIN_INTERVAL_MS = 360_000;
    private static final long HUMANIZER_MISTAKE_MAX_INTERVAL_MS = 840_001;
    private static final long HUMANIZER_MICRO_BREAK_MIN_INTERVAL_MS = 540_000;
    private static final long HUMANIZER_MICRO_BREAK_MAX_INTERVAL_MS = 660_001;
    private static final long HUMANIZER_SMALL_BREAK_MIN_INTERVAL_MS = 1_200_000;
    private static final long HUMANIZER_SMALL_BREAK_MAX_INTERVAL_MS = 1_800_001;

    private enum KnockoutResult
    {
        NONE,
        PENDING,
        SUCCESS,
        FAILED
    }

    private enum Outcome
    {
        KNOCKOUT_SUCCESS,
        KNOCKOUT_FAILED,
        PICKPOCKET_SUCCESS,
        PICKPOCKET_FAILED,
        STUNNED,
        INVENTORY_FULL,
        WINE_EXCHANGED
    }

    private static final class MenuOptionTarget
    {
        private final Point clickPoint;
        private final Rectangle bounds;

        private MenuOptionTarget(Point clickPoint, Rectangle bounds)
        {
            this.clickPoint = clickPoint;
            this.bounds = bounds;
        }

        private boolean contains(Point point)
        {
            return point != null && bounds.contains(point.getX(), point.getY());
        }
    }

    private final ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();

    @Getter
    private volatile BlackjackState state = BlackjackState.STOPPED;
    @Getter
    private volatile String nextAction = "Enable plugin";
    @Getter
    private volatile String targetDescription = "None";
    @Getter
    private volatile String stopReason = "";
    @Getter
    private volatile int successfulKnockouts;
    @Getter
    private volatile int failedKnockouts;
    @Getter
    private volatile int successfulPickpockets;
    @Getter
    private volatile int picksThisKnockout;
    @Getter
    private volatile String lastOutcome = "None";
    @Getter
    private volatile boolean combatSignal;
    @Getter
    private volatile int knockoutMenuMisses;
    @Getter
    private volatile int burstTimeouts;
    @Getter
    private volatile int pickpocketClicks;
    @Getter
    private volatile int combatResetRetries;
    @Getter
    private volatile boolean wineRestockPending;
    @Getter
    private volatile int projectedWinesNeeded;
    @Getter
    private volatile String humanizerStatus = "Disabled";
    @Getter
    private volatile int humanizerEvents;

    private BlackjackConfig config;
    private Instant startTime;
    private int startXp;
    private int targetIndex = -1;
    private long stateEnteredAt;
    private long lastInteractionAt;
    private long lastDrinkAt;
    private long combatClearSince;
    private long npcInteractionSince;
    private long ignoreCombatUntil;
    private long knockoutMenuSelectAt;
    private long knockoutClickIssuedAt;
    private long knockoutBurstReleaseAt;
    private boolean knockoutFallbackReleased;
    private long pickpocketBurstStartedAt;
    private long nextPickpocketClickAt;
    private long lastPickpocketClickAt;
    private long nextKnockoutArmedAt;
    private long knockoutFailedAt;
    private boolean secondPickpocketInteractionSeen;
    private boolean secondPickpocketInteractionComplete;
    private long pendingInventoryFullAt;
    private long targetClearRecheckAt;
    private Point burstClickPoint;
    private boolean curtainCameraAdjusted;
    private boolean healingRequired;
    private long lastCameraPivotAt;
    private long lastCameraPitchAt;
    private WorldPoint lastCameraTargetLocation;
    private String targetClearReason = "None";
    private boolean restockAfterCombatReset;
    private boolean waitingForRestockKnockout;
    private boolean emergencyWineExit;
    private boolean wineInventoryPrepared;
    private int restockTargetWineCount;
    private long nextHumanizerMouseAt;
    private long humanizerMouseRecoverAt;
    private long nextHumanizerMistakeAt;
    private long humanizerMistakeSelectAt;
    private long humanizerMistakeRecoverAt;
    private long nextMicroBreakAt;
    private long nextSmallBreakAt;
    private long humanizerBreakUntil;
    private long knockoutAttemptReadyAt;
    private long knockoutRetryAt;
    private boolean humanizerMistakeClicked;
    private String humanizerMistakeOption = "Examine";
    private String humanizerBreakType = "None";
    private volatile KnockoutResult knockoutResult = KnockoutResult.NONE;
    private volatile boolean shutdownRequested;

    @Inject
    public BlackjackScript()
    {
    }

    public boolean run(BlackjackConfig config)
    {
        this.config = config;
        outcomes.clear();
        successfulKnockouts = 0;
        failedKnockouts = 0;
        successfulPickpockets = 0;
        picksThisKnockout = 0;
        lastOutcome = "None";
        combatSignal = false;
        knockoutMenuMisses = 0;
        burstTimeouts = 0;
        pickpocketClicks = 0;
        combatResetRetries = 0;
        wineRestockPending = false;
        projectedWinesNeeded = 0;
        humanizerStatus = config.humanizerEnabled() ? "Scheduled" : "Disabled";
        humanizerEvents = 0;
        targetIndex = -1;
        npcInteractionSince = 0;
        ignoreCombatUntil = 0;
        knockoutMenuSelectAt = 0;
        knockoutClickIssuedAt = 0;
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        nextPickpocketClickAt = 0;
        lastPickpocketClickAt = 0;
        nextKnockoutArmedAt = 0;
        knockoutFailedAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        pendingInventoryFullAt = 0;
        targetClearRecheckAt = 0;
        burstClickPoint = null;
        curtainCameraAdjusted = false;
        healingRequired = false;
        lastCameraPivotAt = 0;
        lastCameraPitchAt = 0;
        lastCameraTargetLocation = null;
        targetClearReason = "None";
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        long now = System.currentTimeMillis();
        nextHumanizerMouseAt = scheduleFromNow(now,
                HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        nextHumanizerMistakeAt = scheduleFromNow(now,
                HUMANIZER_MISTAKE_MIN_INTERVAL_MS, HUMANIZER_MISTAKE_MAX_INTERVAL_MS);
        nextMicroBreakAt = scheduleFromNow(now,
                HUMANIZER_MICRO_BREAK_MIN_INTERVAL_MS, HUMANIZER_MICRO_BREAK_MAX_INTERVAL_MS);
        nextSmallBreakAt = scheduleFromNow(now,
                HUMANIZER_SMALL_BREAK_MIN_INTERVAL_MS, HUMANIZER_SMALL_BREAK_MAX_INTERVAL_MS);
        humanizerMouseRecoverAt = 0;
        humanizerMistakeSelectAt = 0;
        humanizerMistakeRecoverAt = 0;
        humanizerBreakUntil = 0;
        knockoutAttemptReadyAt = 0;
        knockoutRetryAt = 0;
        humanizerMistakeClicked = false;
        humanizerMistakeOption = "Examine";
        humanizerBreakType = "None";
        knockoutResult = KnockoutResult.NONE;
        shutdownRequested = false;
        stopReason = "";
        startTime = Instant.now();
        startXp = currentThievingXp();
        transition(BlackjackState.STARTING, "Validate setup");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn())
                {
                    return;
                }

                processOutcomes();
                if (state == BlackjackState.ERROR || state == BlackjackState.STOPPED)
                {
                    return;
                }

                updateHealingRequirement();
                maintainTopDownCamera();

                if (handleWineRestockPriority())
                {
                    if (healingRequired && isCombatSafetyState()
                            && drinkWineIfReady("Heal while securing wine run"))
                    {
                        return;
                    }
                    executeState();
                    return;
                }

                if (healingRequired)
                {
                    if (isCombatSafetyState())
                    {
                        if (drinkWineIfReady("Heal while resetting combat"))
                        {
                            return;
                        }
                    }
                    else
                    {
                        if (state != BlackjackState.HEALING && state != BlackjackState.RESTOCKING_WINE)
                        {
                            transition(BlackjackState.HEALING, "Drink wine to configured HP");
                        }
                        executeState();
                        return;
                    }
                }

                if (state != BlackjackState.POSITIONING_COMBAT_RESET
                        && state != BlackjackState.ESCAPING_COMBAT
                        && state != BlackjackState.WAITING_FOR_COMBAT_CLEAR
                        && shouldEscapeCombat())
                {
                    transition(BlackjackState.POSITIONING_COMBAT_RESET, "Move to combat staging tile");
                }

                if (handleHumanizerPriority())
                {
                    executeState();
                    return;
                }

                executeState();
            }
            catch (Exception ex)
            {
                if (shutdownRequested || state == BlackjackState.STOPPED)
                {
                    return;
                }
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                fail("Unexpected script error");
            }
        }, 0, 35, TimeUnit.MILLISECONDS);
        return true;
    }

    private void executeState()
    {
        switch (state)
        {
            case STARTING:
            case VALIDATING:
                validateSetup();
                break;
            case RETURNING_TO_HOUSE:
                returnToHouse();
                break;
            case FINDING_TARGET:
                acquireTarget();
                break;
            case KNOCKING_OUT:
                knockOutTarget();
                break;
            case SELECTING_KNOCKOUT:
                selectKnockoutMenuEntry();
                break;
            case PICKPOCKETING:
                runPickpocketBurst();
                break;
            case WAITING_FOR_TARGET_CLEAR:
                waitForTargetClear();
                break;
            case HEALING:
                heal();
                break;
            case POSITIONING_COMBAT_RESET:
                positionCombatReset();
                break;
            case ESCAPING_COMBAT:
                escapeCombat();
                break;
            case WAITING_FOR_COMBAT_CLEAR:
                waitForCombatClear();
                break;
            case EXITING_FOR_WINE:
                exitHouseForWine();
                break;
            case SECURING_WINE_EXIT:
                secureWineExit();
                break;
            case RESTOCKING_WINE:
                restockWine();
                break;
            case RETURNING_WITH_WINE:
                returnWithWine();
                break;
            case SECURING_WINE_ENTRY:
                secureWineEntry();
                break;
            case HUMANIZER_MOUSE:
                recoverHumanizerMouse();
                break;
            case HUMANIZER_MISCLICK:
                runHumanizerMisclick();
                break;
            case HUMANIZER_BREAK:
                runHumanizerBreak();
                break;
            default:
                break;
        }
    }

    private void validateSetup()
    {
        transition(BlackjackState.VALIDATING, "Check level and equipment");
        int level = Rs2Player.getRealSkillLevel(Skill.THIEVING);
        if (level < 45)
        {
            fail("45 Thieving required");
            return;
        }

        if (!hasBlackjackEquipped())
        {
            if (Rs2Inventory.items(item -> item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("blackjack")).findAny().isPresent())
            {
                if (readyForInteraction(700))
                {
                    Rs2Inventory.interact(item -> item.getName() != null
                            && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"), "Wield");
                    lastInteractionAt = System.currentTimeMillis();
                }
                nextAction = "Equip blackjack";
                return;
            }
            fail("No blackjack equipped");
            return;
        }

        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            if (config.autoRestockWine())
            {
                transition(BlackjackState.RESTOCKING_WINE, "Restock jug of wine");
            }
            else
            {
                fail("Out of jug of wine");
            }
            return;
        }

        if (!isInsideHouse())
        {
            transition(BlackjackState.RETURNING_TO_HOUSE, "Return to marked house");
            return;
        }
        transition(BlackjackState.FINDING_TARGET, "Find level-appropriate target");
    }

    private void returnToHouse()
    {
        if (isInsideHouse())
        {
            transition(BlackjackState.FINDING_TARGET, "Find level-appropriate target");
            return;
        }
        if (readyForInteraction(1_000))
        {
            Rs2Walker.walkTo(HOUSE_CENTRE, 0);
            lastInteractionAt = System.currentTimeMillis();
        }
    }

    private void acquireTarget()
    {
        Rs2NpcModel target = findEligibleTarget();
        if (target == null)
        {
            targetIndex = -1;
            targetDescription = expectedTargetDescription();
            nextAction = "Waiting for pre-lured target";
            if (elapsedInState() > 30_000)
            {
                fail("No eligible target in marked house");
            }
            return;
        }

        targetIndex = target.getIndex();
        targetDescription = target.getName() + " (level " + target.getCombatLevel() + ")";
        maintainTargetCamera(target);
        transition(BlackjackState.KNOCKING_OUT, "Knock-Out target");
    }

    private void knockOutTarget()
    {
        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (knockoutResult == KnockoutResult.SUCCESS
                && picksThisKnockout < 2
                && nextKnockoutArmedAt == 0
                && target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS)
        {
            pickpocketBurstStartedAt = System.currentTimeMillis();
            transition(BlackjackState.PICKPOCKETING, "Continue current unconscious pickpocket burst");
            return;
        }

        maintainTargetCamera(target);

        if (System.currentTimeMillis() < knockoutAttemptReadyAt)
        {
            nextAction = "Pause before Knock-Out attempt";
            return;
        }

        if (nextKnockoutArmedAt != 0)
        {
            observeSecondPickpocketInteraction(target);
            if (!secondPickpocketInteractionSeen)
            {
                if (picksThisKnockout < 2
                        && System.currentTimeMillis() - nextKnockoutArmedAt
                        >= SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS)
                {
                    cancelSecondPickpocketPrearm("second pickpocket interaction was not observed");
                    transition(BlackjackState.PICKPOCKETING, "Resume unresolved pickpocket burst");
                    return;
                }
                if (picksThisKnockout >= 2 && allowSecondPickpocketInteractionFallback(target))
                {
                    // The confirmed second pickpocket can safely use the bounded interaction fallback.
                }
                else
                {
                    nextAction = "Wait for second pickpocket interaction";
                    return;
                }
            }
        }

        Point anchor = targetAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            nextAction = "Wait for safe NPC click point";
            return;
        }

        if (readyForInteraction(80))
        {
            if (!naturalMoveAndClick(target, anchor, true))
            {
                nextAction = "Wait for natural mouse";
                return;
            }
            burstClickPoint = anchor;
            long now = System.currentTimeMillis();
            lastInteractionAt = now;
            knockoutMenuSelectAt = now + randomKnockoutMenuDwell();
            transition(BlackjackState.SELECTING_KNOCKOUT, "Select Knock-Out from menu");
        }
    }

    private void selectKnockoutMenuEntry()
    {
        Rs2NpcModel target = currentTarget();
        if (nextKnockoutArmedAt != 0)
        {
            observeSecondPickpocketInteraction(target);
            if (picksThisKnockout >= 2
                    && secondPickpocketInteractionComplete
                    && isHumanizerEventDue(System.currentTimeMillis()))
            {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                burstClickPoint = null;
                transition(BlackjackState.FINDING_TARGET, "Yield completed burst to humanizer");
                return;
            }
        }

        Point menuPoint = findTargetMenuOptionPoint("Knock-Out");

        if (menuPoint != null)
        {
            if (nextKnockoutArmedAt != 0 && !secondPickpocketInteractionComplete)
            {
                nextAction = "Hold pre-armed Knock-Out for second pickpocket interaction";
                return;
            }
            if (nextKnockoutArmedAt != 0 && picksThisKnockout < 2)
            {
                nextAction = "Hold pre-armed Knock-Out for second pickpocket confirmation";
                return;
            }
            if (picksThisKnockout >= 2
                    && !secondPickpocketInteractionComplete
                    && !allowSecondPickpocketInteractionFallback(target))
            {
                nextAction = "Finish second pickpocket interaction";
                return;
            }
            if (System.currentTimeMillis() < knockoutMenuSelectAt)
            {
                nextAction = "Pause over Knock-Out menu";
                return;
            }
            log.info("Selecting Knock-Out menu point={} targetAnimation={} playerInteracting={}",
                    menuPoint,
                    target == null ? -1 : target.getAnimation(),
                    target != null && isPlayerInteractingWithTarget(target));
            if (!clickMenuPoint("Knock-Out", menuPoint))
            {
                recoverKnockoutMenu("menu click could not be verified");
                return;
            }
            long now = System.currentTimeMillis();
            lastInteractionAt = now;
            knockoutClickIssuedAt = now;
            knockoutBurstReleaseAt = now + randomBetween(
                    KNOCKOUT_DISPATCH_FALLBACK_MIN_MS,
                    KNOCKOUT_DISPATCH_FALLBACK_MAX_MS);
            knockoutFallbackReleased = false;
            pickpocketBurstStartedAt = 0;
            nextPickpocketClickAt = knockoutBurstReleaseAt;
            lastPickpocketClickAt = 0;
            pendingInventoryFullAt = 0;
            picksThisKnockout = 0;
            nextKnockoutArmedAt = 0;
            knockoutFailedAt = 0;
            knockoutRetryAt = 0;
            secondPickpocketInteractionSeen = false;
            secondPickpocketInteractionComplete = false;
            knockoutResult = KnockoutResult.PENDING;
            curtainCameraAdjusted = false;
            lastOutcome = "Knock-Out selected";
            log.info("Knock-Out dispatched: dispatchAtMs={} fallbackInMs={}",
                    knockoutClickIssuedAt, knockoutBurstReleaseAt - knockoutClickIssuedAt);
            transition(BlackjackState.PICKPOCKETING, "Spam pickpocket 1/2");
            return;
        }

        if (isCurtainMenuOpen())
        {
            log.info("Curtain blocked the Knock-Out menu; rotating camera once");
            lastOutcome = "Curtain blocked Knock-Out";
            waitForTargetClear("Curtain blocked Knock-Out", true);
            return;
        }

        if (elapsedInState() >= KNOCKOUT_MENU_TIMEOUT_MS)
        {
            recoverKnockoutMenu("menu entry was not available before timeout");
        }
    }

    private void runPickpocketBurst()
    {
        long now = System.currentTimeMillis();
        if (lastPickpocketClickAt > 0
                && now - lastPickpocketClickAt <= MISCLICK_MOVEMENT_WINDOW_MS
                && Rs2Player.isMoving())
        {
            log.info("Unexpected movement after pickpocket click; abandoning burst and re-aiming");
            lastOutcome = "Movement invalidated pickpocket anchor";
            waitForTargetClear("Unexpected movement", false);
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (knockoutResult == KnockoutResult.PENDING)
        {
            if (elapsedInState() >= KNOCKOUT_CONFIRM_TIMEOUT_MS)
            {
                knockoutMenuMisses++;
                lastOutcome = "Knock-Out command not confirmed";
                log.warn("Knock-Out command was not confirmed: targetAnimation={} playerInteracting={}",
                        target.getAnimation(), isPlayerInteractingWithTarget(target));
                transition(BlackjackState.KNOCKING_OUT, "Retry unconfirmed Knock-Out");
                return;
            }
            if (now < knockoutBurstReleaseAt)
            {
                nextAction = "Confirm Knock-Out dispatch";
                return;
            }
            if (!knockoutFallbackReleased)
            {
                knockoutFallbackReleased = true;
                if (pickpocketBurstStartedAt == 0)
                {
                    pickpocketBurstStartedAt = now;
                }
                log.warn("Knock-Out signal still pending after {}ms; releasing safety pickpocket burst",
                        knockoutDispatchAge(now));
            }
            nextAction = "Safety pickpocket while confirming Knock-Out";
        }

        if (knockoutResult == KnockoutResult.FAILED
                && knockoutFailedAt > 0
                && now >= knockoutRetryAt)
        {
            transition(BlackjackState.KNOCKING_OUT, "Retry failed Knock-Out");
            return;
        }

        if (picksThisKnockout >= 2)
        {
            armNextKnockout(target);
            transition(BlackjackState.KNOCKING_OUT, "Next Knock-Out armed");
            return;
        }

        if (consumeInventoryFullResetIfIdle())
        {
            return;
        }

        long burstAge = pickpocketBurstStartedAt == 0 ? 0 : now - pickpocketBurstStartedAt;
        if (burstAge >= PICKPOCKET_BURST_TIMEOUT_MS)
        {
            if (knockoutResult == KnockoutResult.SUCCESS
                    && target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS)
            {
                pickpocketBurstStartedAt = now;
                nextAction = "Finish current unconscious pickpocket burst";
                log.info("Extending pickpocket burst while target remains unconscious: confirmedPicks={}",
                        picksThisKnockout);
                return;
            }
            burstTimeouts++;
            lastOutcome = "Burst timeout: " + picksThisKnockout + "/2";
            log.warn("Pickpocket burst timed out after {} confirmed picks", picksThisKnockout);
            transition(BlackjackState.KNOCKING_OUT, "Recover burst timing");
            return;
        }

        if (System.currentTimeMillis() >= nextPickpocketClickAt && clickAnchoredTarget(target))
        {
            now = System.currentTimeMillis();
            if (pickpocketBurstStartedAt == 0)
            {
                pickpocketBurstStartedAt = now;
            }
            lastInteractionAt = now;
            lastPickpocketClickAt = now;
            nextPickpocketClickAt = now + randomPickpocketDelay(false);
            pickpocketClicks++;
            if (picksThisKnockout == 1 && nextKnockoutArmedAt == 0)
            {
                armNextKnockout(target);
                transition(BlackjackState.KNOCKING_OUT, "Pre-arm next Knock-Out after second pickpocket click");
                return;
            }
            nextAction = "Confirm pickpocket " + (picksThisKnockout + 1) + "/2";
        }
    }

    private void heal()
    {
        int hitpoints = currentHitpoints();
        if (hitpoints >= config.healToPercent())
        {
            healingRequired = false;
            pendingInventoryFullAt = 0;
            burstClickPoint = null;
            transition(BlackjackState.FINDING_TARGET, "Reacquire target after healing");
            return;
        }

        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            if (config.autoRestockWine())
            {
                transition(BlackjackState.RESTOCKING_WINE, "Restock jug of wine");
            }
            else
            {
                fail("Out of jug of wine");
            }
            return;
        }

        drinkWineIfReady("Heal to " + config.healToPercent() + " HP");
    }

    private void positionCombatReset()
    {
        burstClickPoint = null;
        WorldPoint location = Rs2Player.getWorldLocation();
        if (COMBAT_STAGING_TILE.equals(location))
        {
            Rs2NpcModel target = currentTarget();
            WorldPoint targetLocation = target == null ? null : target.getWorldLocation();
            if (targetLocation != null && targetLocation.getY() > COMBAT_SAFE_TILE.getY())
            {
                log.info("Combat reset staged: player={}, target={}, safespot={}",
                        location, targetLocation, COMBAT_SAFE_TILE);
                lastOutcome = "Target north of safespot";
                transition(BlackjackState.ESCAPING_COMBAT, "Run behind the bed");
            }
            else
            {
                nextAction = "Wait for target north of safespot";
            }
            return;
        }

        if (readyForInteraction(450))
        {
            Rs2Walker.walkFastCanvas(COMBAT_STAGING_TILE);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Walk to 3359,2995";
        }
    }

    private void escapeCombat()
    {
        burstClickPoint = null;
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location != null && location.equals(COMBAT_SAFE_TILE))
        {
            combatClearSince = 0;
            transition(BlackjackState.WAITING_FOR_COMBAT_CLEAR, "Wait for combat to clear");
            return;
        }

        if (readyForInteraction(450))
        {
            Rs2Walker.walkFastCanvas(COMBAT_SAFE_TILE);
            lastInteractionAt = System.currentTimeMillis();
        }
    }

    private void waitForCombatClear()
    {
        boolean targetingPlayer = isNpcTargetingPlayer();
        combatSignal = targetingPlayer;
        if (targetingPlayer)
        {
            combatClearSince = 0;
            if (elapsedInState() >= COMBAT_RESET_RETRY_MS)
            {
                combatResetRetries++;
                targetIndex = -1;
                npcInteractionSince = 0;
                ignoreCombatUntil = System.currentTimeMillis() + 1_000;
                lastOutcome = "Combat reset probe";
                if (wineRestockPending)
                {
                    restockAfterCombatReset = false;
                    waitingForRestockKnockout = true;
                    transition(BlackjackState.FINDING_TARGET, "Knock-Out target before wine run");
                }
                else
                {
                    transition(BlackjackState.FINDING_TARGET, "Probe Knock-Out after reset");
                }
                return;
            }
            nextAction = "Wait behind bed (" + getStateAgeSeconds() + "s)";
            return;
        }

        if (combatClearSince == 0)
        {
            combatClearSince = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - combatClearSince >= COMBAT_CLEAR_SETTLE_MS)
        {
            targetIndex = -1;
            lastOutcome = "Combat signal cleared";
            if (wineRestockPending)
            {
                restockAfterCombatReset = false;
                waitingForRestockKnockout = true;
                transition(BlackjackState.FINDING_TARGET, "Knock-Out target before wine run");
            }
            else
            {
                transition(BlackjackState.FINDING_TARGET, "Reacquire target");
            }
        }
    }

    private boolean handleWineRestockPriority()
    {
        int wines = Rs2Inventory.count(WINE_ID);
        int hitpoints = currentHitpoints();
        projectedWinesNeeded = winesNeededToReach(hitpoints, config.healToPercent());

        if (!config.autoRestockWine())
        {
            return false;
        }

        boolean emergency = wines <= EARLY_WINE_RESTOCK_MAX_WINES
                && hitpoints < EARLY_WINE_RESTOCK_HITPOINTS;
        boolean projectedDepletion = healingRequired
                && projectedWinesNeeded > 0
                && wines <= projectedWinesNeeded;
        if (!wineRestockPending && wines > 0 && !projectedDepletion && !emergency)
        {
            return false;
        }

        if (!wineRestockPending)
        {
            wineRestockPending = true;
            emergencyWineExit = emergency;
            restockTargetWineCount = Math.max(1,
                    wines + Rs2Inventory.count(ItemID.JUG_EMPTY) + Rs2Inventory.emptySlotCount());
            log.info("Wine restock latched: wines={}, needed={}, hp={}, targetWines={}, emergency={}",
                    wines, projectedWinesNeeded, hitpoints, restockTargetWineCount, emergencyWineExit);
        }
        else if (emergency)
        {
            emergencyWineExit = true;
        }

        if (isWineRestockState())
        {
            return true;
        }

        if (emergencyWineExit)
        {
            restockAfterCombatReset = false;
            waitingForRestockKnockout = false;
            transition(BlackjackState.EXITING_FOR_WINE, "Emergency exit: low wine reserve below minimum HP");
            return true;
        }

        if (waitingForRestockKnockout)
        {
            if (shouldEscapeCombat())
            {
                waitingForRestockKnockout = false;
                restockAfterCombatReset = true;
                transition(BlackjackState.POSITIONING_COMBAT_RESET, "Reset renewed combat before wine run");
            }
            return true;
        }

        if (restockAfterCombatReset || isCombatSafetyState() || isNpcTargetingPlayer())
        {
            restockAfterCombatReset = true;
            if (!isCombatSafetyState())
            {
                transition(BlackjackState.POSITIONING_COMBAT_RESET, "Reset combat before wine run");
            }
            return true;
        }

        transition(BlackjackState.EXITING_FOR_WINE, "Exit house for wine");
        return true;
    }

    static int winesNeededToReach(int hitpoints, int targetHitpoints)
    {
        int missingHitpoints = Math.max(0, targetHitpoints - hitpoints);
        return (missingHitpoints + WINE_HEAL_AMOUNT - 1) / WINE_HEAL_AMOUNT;
    }

    private boolean isWineRestockState()
    {
        return state == BlackjackState.EXITING_FOR_WINE
                || state == BlackjackState.SECURING_WINE_EXIT
                || state == BlackjackState.RESTOCKING_WINE
                || state == BlackjackState.RETURNING_WITH_WINE
                || state == BlackjackState.SECURING_WINE_ENTRY;
    }

    private void exitHouseForWine()
    {
        if (!isInsideHouse())
        {
            transition(BlackjackState.SECURING_WINE_EXIT, "Close east door behind player");
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        if (!WINE_DOOR_INSIDE_TILE.equals(location))
        {
            if (readyForInteraction(450))
            {
                Rs2Walker.walkFastCanvas(WINE_DOOR_INSIDE_TILE);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Walk to inside door tile";
            }
            return;
        }

        Rs2TileObjectModel closedDoor = findWineDoor("Open");
        if (closedDoor != null)
        {
            interactWithWineDoor(closedDoor, "Open", "Open east door");
            return;
        }

        if (findWineDoor("Close") != null && readyForInteraction(350))
        {
            Rs2Walker.walkFastCanvas(WINE_DOOR_OUTSIDE_TILE);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Step outside east door";
            return;
        }

        waitForWineDoor("Open east door");
    }

    private void secureWineExit()
    {
        if (isInsideHouse())
        {
            transition(BlackjackState.EXITING_FOR_WINE, "Finish leaving house");
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        if (!WINE_DOOR_OUTSIDE_TILE.equals(location))
        {
            if (readyForInteraction(450))
            {
                Rs2Walker.walkFastCanvas(WINE_DOOR_OUTSIDE_TILE);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Stand one tile outside east door";
            }
            return;
        }

        Rs2TileObjectModel openDoor = findWineDoor("Close");
        if (openDoor != null)
        {
            interactWithWineDoor(openDoor, "Close", "Close east door behind player");
            return;
        }

        if (findWineDoor("Open") != null)
        {
            transition(BlackjackState.RESTOCKING_WINE, "Prepare space and exchange wine notes");
            return;
        }

        waitForWineDoor("Confirm east door is closed");
    }

    private void restockWine()
    {
        if (isInsideHouse())
        {
            transition(BlackjackState.EXITING_FOR_WINE, "Return outside before restocking");
            return;
        }

        if (findWineDoor("Close") != null)
        {
            transition(BlackjackState.SECURING_WINE_EXIT, "Close east door before inventory changes");
            return;
        }

        int wines = Rs2Inventory.count(WINE_ID);
        if (wines >= restockTargetWineCount)
        {
            transition(BlackjackState.RETURNING_WITH_WINE, "Return to east door");
            return;
        }

        if (!wineInventoryPrepared)
        {
            int emptyJugs = Rs2Inventory.count(ItemID.JUG_EMPTY);
            if (emptyJugs > 0 && Rs2Inventory.dropAll(ItemID.JUG_EMPTY))
            {
                lastInteractionAt = System.currentTimeMillis();
                log.info("Cleared {} empty jugs outside the secured door for wine exchange", emptyJugs);
                nextAction = "Clear empty jugs outside secured door";
            }
            wineInventoryPrepared = true;
            return;
        }

        if (Rs2Inventory.emptySlotCount() == 0)
        {
            fail("No safe inventory space for wine");
            return;
        }

        int coins = Rs2Inventory.count(COINS_ID);
        if (coins < WINE_EXCHANGE_COST)
        {
            fail("Coins required to restock wine");
            return;
        }

        if (Rs2Inventory.hasItem(NOTED_WINE_ID))
        {
            exchangeNotedWine();
            return;
        }

        if (wines > 0)
        {
            restockTargetWineCount = wines;
            transition(BlackjackState.RETURNING_WITH_WINE, "Return with available wine");
            return;
        }

        buyWineFromFaisal();
    }

    private void returnWithWine()
    {
        if (Rs2Shop.isOpen())
        {
            Rs2Shop.closeShop();
            lastInteractionAt = System.currentTimeMillis();
            return;
        }

        if (isInsideHouse())
        {
            transition(BlackjackState.SECURING_WINE_ENTRY, "Close east door behind player");
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        if (!WINE_DOOR_OUTSIDE_TILE.equals(location))
        {
            if (readyForInteraction(450))
            {
                Rs2Walker.walkFastCanvas(WINE_DOOR_OUTSIDE_TILE);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Return to outside door tile";
            }
            return;
        }

        Rs2TileObjectModel closedDoor = findWineDoor("Open");
        if (closedDoor != null)
        {
            interactWithWineDoor(closedDoor, "Open", "Open east door to re-enter");
            return;
        }

        if (findWineDoor("Close") != null && readyForInteraction(350))
        {
            Rs2Walker.walkFastCanvas(WINE_DOOR_INSIDE_TILE);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Step back inside east door";
            return;
        }

        waitForWineDoor("Open east door to re-enter");
    }

    private void secureWineEntry()
    {
        if (!isInsideHouse())
        {
            transition(BlackjackState.RETURNING_WITH_WINE, "Finish re-entering house");
            return;
        }

        Rs2TileObjectModel openDoor = findWineDoor("Close");
        if (openDoor != null)
        {
            interactWithWineDoor(openDoor, "Close", "Close east door behind player");
            return;
        }

        if (findWineDoor("Open") == null)
        {
            waitForWineDoor("Confirm east door is closed");
            return;
        }

        wineRestockPending = false;
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        projectedWinesNeeded = winesNeededToReach(currentHitpoints(), config.healToPercent());
        lastOutcome = "Wine restock complete";
        if (currentHitpoints() < config.healToPercent())
        {
            healingRequired = true;
            transition(BlackjackState.HEALING, "Heal after secured wine run");
        }
        else
        {
            transition(BlackjackState.FINDING_TARGET, "Resume blackjacking");
        }
    }

    private Rs2TileObjectModel findWineDoor(String action)
    {
        return new Rs2TileObjectQueryable()
                .withName(WINE_EXIT_OBJECT_NAME)
                .where(object -> {
                    WorldPoint location = object.getWorldLocation();
                    return location != null
                            && location.distanceTo2D(WINE_DOOR_INSIDE_TILE) <= 1
                            && hasObjectAction(object, action);
                })
                .first();
    }

    private boolean hasObjectAction(Rs2TileObjectModel object, String action)
    {
        ObjectComposition composition = object.getObjectComposition();
        return composition != null
                && Arrays.stream(composition.getActions())
                        .filter(Objects::nonNull)
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(action));
    }

    private void interactWithWineDoor(Rs2TileObjectModel door, String action, String description)
    {
        if (!readyForInteraction(DOOR_INTERACTION_DELAY_MS))
        {
            return;
        }
        if (door.click(action))
        {
            lastInteractionAt = System.currentTimeMillis();
            nextAction = description;
        }
    }

    private void waitForWineDoor(String action)
    {
        nextAction = action;
        if (elapsedInState() > 12_000)
        {
            fail("East blackjack door could not be resolved");
        }
    }

    private void exchangeNotedWine()
    {
        if (Rs2Dialogue.hasSelectAnOption())
        {
            int option = Rs2Inventory.emptySlotCount() > 1 ? 2 : 1;
            Rs2Dialogue.keyPressForDialogueOption(option);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = option == 2 ? "Exchange all notes" : "Exchange one note";
            return;
        }

        Rs2NpcModel merchant = Microbot.getRs2NpcCache().query()
                .withName("Banknote Exchange Merchant")
                .nearestOnClientThread();
        if (merchant == null)
        {
            if (readyForInteraction(700))
            {
                Rs2Walker.walkTo(WINE_MERCHANT_TILE, 0);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Walk to recorded note merchant tile";
            }
            if (elapsedInState() > 30_000)
            {
                fail("Banknote Exchange Merchant not found");
            }
            return;
        }

        if (merchant.getDistanceFromPlayer() > 2)
        {
            if (readyForInteraction(700))
            {
                Rs2Walker.walkTo(merchant.getWorldLocation(), 1);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Walk to note merchant";
            }
            return;
        }

        if (readyForInteraction(700))
        {
            if (Rs2Inventory.use(NOTED_WINE_ID) && merchant.click())
            {
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Choose note quantity";
            }
        }
    }

    private void buyWineFromFaisal()
    {
        if (!Rs2Shop.isOpen())
        {
            if (readyForInteraction(900))
            {
                if (!Rs2Shop.openShop("Faisal", false))
                {
                    fail("Faisal's shop not found");
                    return;
                }
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Open Faisal's shop";
            }
            return;
        }

        if (readyForInteraction(700))
        {
            if (!Rs2Shop.buyItem("Jug of wine", "50"))
            {
                fail("Jug of wine unavailable in shop");
                return;
            }
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Buy jug of wine";
        }
    }

    private void processOutcomes()
    {
        Outcome outcome;
        while ((outcome = outcomes.poll()) != null)
        {
            switch (outcome)
            {
                case KNOCKOUT_SUCCESS:
                    if (state != BlackjackState.PICKPOCKETING || knockoutResult != KnockoutResult.PENDING)
                    {
                        log.debug("Ignoring stale knockout success in state {}", state);
                        break;
                    }
                    confirmKnockout("success signal");
                    break;
                case KNOCKOUT_FAILED:
                    if (state != BlackjackState.PICKPOCKETING || knockoutResult != KnockoutResult.PENDING)
                    {
                        log.debug("Ignoring stale knockout failure in state {}", state);
                        break;
                    }
                    knockoutResult = KnockoutResult.FAILED;
                    knockoutFailedAt = System.currentTimeMillis();
                    knockoutRetryAt = knockoutFailedAt + randomFailedKnockoutRetry();
                    nextPickpocketClickAt = knockoutFailedAt + randomPickpocketDelay(true);
                    knockoutBurstReleaseAt = 0;
                    knockoutFallbackReleased = false;
                    pickpocketBurstStartedAt = knockoutFailedAt;
                    failedKnockouts++;
                    lastOutcome = "Knock-Out failed";
                    ignoreCombatUntil = System.currentTimeMillis() + FAILED_KNOCKOUT_RETALIATION_GRACE_MS;
                    npcInteractionSince = 0;
                    nextAction = "Interrupt retaliation, then retry Knock-Out";
                    log.info("Knock-Out failure signal after {}ms; releasing retaliation pickpocket burst",
                            knockoutDispatchAge(knockoutFailedAt));
                    break;
                case PICKPOCKET_SUCCESS:
                    if (state == BlackjackState.PICKPOCKETING || isSecondPickpocketPrearmPending())
                    {
                        successfulPickpockets++;
                        picksThisKnockout++;
                        pendingInventoryFullAt = 0;
                        lastOutcome = "Pickpocket " + picksThisKnockout + "/2";
                        if (picksThisKnockout >= 2)
                        {
                            armNextKnockout(currentTarget());
                            nextAction = secondPickpocketInteractionSeen
                                    ? "Pre-arm Knock-Out during second pickpocket"
                                    : "Wait for second pickpocket interaction";
                        }
                    }
                    break;
                case PICKPOCKET_FAILED:
                case STUNNED:
                    if (state != BlackjackState.PICKPOCKETING && !isSecondPickpocketPrearmPending())
                    {
                        log.debug("Ignoring stale pickpocket failure/stun in state {}", state);
                        break;
                    }
                    if (isSecondPickpocketPrearmPending())
                    {
                        cancelSecondPickpocketPrearm("second pickpocket failed or stunned");
                        transition(BlackjackState.PICKPOCKETING, "Resume unresolved pickpocket burst");
                    }
                    nextAction = "Continue pickpocket attempts while stunned";
                    lastOutcome = "Pickpocket failed/stunned";
                    break;
                case INVENTORY_FULL:
                    if (state == BlackjackState.PICKPOCKETING && lastPickpocketClickAt > 0)
                    {
                        if (pendingInventoryFullAt == 0)
                        {
                            pendingInventoryFullAt = System.currentTimeMillis();
                        }
                        lastOutcome = "Full-inventory signal observed";
                        nextAction = "Finish active pickpocket or reset aggression";
                    }
                    break;
                case WINE_EXCHANGED:
                    nextAction = "Return to marked house";
                    break;
                default:
                    break;
            }
        }
    }

    public void onChatMessage(String rawMessage)
    {
        if (rawMessage == null)
        {
            return;
        }
        String message = rawMessage.toLowerCase(Locale.ROOT);
        if (message.contains("render them unconscious"))
        {
            outcomes.add(Outcome.KNOCKOUT_SUCCESS);
        }
        else if (message.contains("blow only glances"))
        {
            outcomes.add(Outcome.KNOCKOUT_FAILED);
        }
        else if (message.contains("you pick the") && message.contains("pocket"))
        {
            outcomes.add(Outcome.PICKPOCKET_SUCCESS);
        }
        else if (message.contains("fail to pick") && message.contains("pocket"))
        {
            outcomes.add(Outcome.PICKPOCKET_FAILED);
        }
        else if (message.contains("you've been stunned"))
        {
            outcomes.add(Outcome.STUNNED);
        }
        else if (message.contains("don't have enough inventory space")
                || message.contains("don't have enough space to do that"))
        {
            outcomes.add(Outcome.INVENTORY_FULL);
        }
        else if (message.contains("merchant converts your banknote"))
        {
            outcomes.add(Outcome.WINE_EXCHANGED);
        }
    }

    public void onOverheadTextChanged(NPC npc, String rawText)
    {
        if (npc == null || npc.getIndex() != targetIndex || rawText == null)
        {
            return;
        }

        String text = rawText.trim().toLowerCase(Locale.ROOT);
        if (text.matches("z{3,}.*"))
        {
            log.debug("Knock-Out success overhead observed: {}", rawText);
            outcomes.add(Outcome.KNOCKOUT_SUCCESS);
        }
        else if (text.contains("kill you for that"))
        {
            log.debug("Knock-Out failure overhead observed: {}", rawText);
            outcomes.add(Outcome.KNOCKOUT_FAILED);
        }
    }

    private Rs2NpcModel findEligibleTarget()
    {
        List<Rs2NpcModel> candidates = Microbot.getRs2NpcCache().query()
                .where(this::isEligibleTarget)
                .toListOnClientThread();
        return candidates.stream()
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);
    }

    private Rs2NpcModel currentTarget()
    {
        if (targetIndex < 0)
        {
            return findEligibleTarget();
        }
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getIndex() == targetIndex && isEligibleTarget(npc))
                .nearestOnClientThread();
    }

    private boolean isEligibleTarget(Rs2NpcModel npc)
    {
        if (npc == null || npc.getName() == null || npc.getWorldLocation() == null
                || !SUPPORTED_HOUSE.contains(npc.getWorldLocation()))
        {
            return false;
        }
        int thievingLevel = Rs2Player.getRealSkillLevel(Skill.THIEVING);
        if (thievingLevel < 55)
        {
            return npc.getName().equalsIgnoreCase("Bandit") && npc.getCombatLevel() == 41;
        }
        if (thievingLevel < 65)
        {
            return npc.getName().equalsIgnoreCase("Bandit") && npc.getCombatLevel() == 56;
        }
        return npc.getName().equalsIgnoreCase("Menaphite Thug");
    }

    private String expectedTargetDescription()
    {
        int level = Rs2Player.getRealSkillLevel(Skill.THIEVING);
        if (level < 55)
        {
            return "Bandit (level 41)";
        }
        if (level < 65)
        {
            return "Bandit (level 56)";
        }
        return "Menaphite Thug";
    }

    private boolean hasBlackjackEquipped()
    {
        return Rs2Equipment.isWearing(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"));
    }

    private boolean isInsideHouse()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && SUPPORTED_HOUSE.contains(location);
    }

    private boolean clickAnchoredTarget(Rs2NpcModel target)
    {
        if (Microbot.getClient().isMenuOpen())
        {
            return false;
        }
        Point anchor = burstWanderAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            burstClickPoint = null;
            nextAction = "Wait for safe NPC click point";
            return false;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current != null
                && Math.abs(current.getX() - anchor.getX()) <= 2
                && Math.abs(current.getY() - anchor.getY()) <= 2)
        {
            if (!isInsideTargetHull(target, current))
            {
                log.warn("Refusing target click outside NPC hull: expected={} actual={} targetIndex={}",
                        anchor, current, targetIndex);
                burstClickPoint = null;
                nextAction = "Reacquire safe NPC click point";
                return false;
            }
            Microbot.getMouse().click(current);
            burstClickPoint = anchor;
        }
        else
        {
            if (!naturalMoveAndClick(target, anchor, false))
            {
                nextAction = "Wait for natural mouse";
                return false;
            }
            burstClickPoint = anchor;
        }
        return true;
    }

    private boolean naturalMoveAndClick(Rs2NpcModel target, Point point, boolean rightClick)
    {
        if (Microbot.naturalMouse == null)
        {
            log.warn("Natural mouse unavailable; refusing direct point click");
            return false;
        }

        Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current == null)
        {
            current = point;
        }
        if (!isInsideTargetHull(target, current))
        {
            log.warn("Natural mouse ended outside NPC hull; refusing {} click: expected={} actual={} targetIndex={}",
                    rightClick ? "right" : "left", point, current, targetIndex);
            return false;
        }
        Microbot.getMouse().click(current, rightClick);
        return true;
    }

    private boolean isInsideTargetHull(Rs2NpcModel target, Point point)
    {
        return target != null && point != null
                && Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    NPC npc = target.getNpc();
                    Shape hull = npc == null ? null : npc.getConvexHull();
                    return hull != null && hull.contains(point.getX(), point.getY());
                }).orElse(false);
    }

    private void recoverKnockoutMenu(String reason)
    {
        knockoutMenuMisses++;
        burstClickPoint = null;
        knockoutMenuSelectAt = 0;
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        lastOutcome = "Knock-Out menu recovery";
        log.warn("Knock-Out menu recovery: {}. Clearing stale menu and reacquiring target hull.", reason);
        transition(BlackjackState.KNOCKING_OUT, "Recover Knock-Out menu");
    }

    private Point findTargetMenuOptionPoint(String option)
    {
        MenuOptionTarget menuTarget = findTargetMenuOption(option);
        return menuTarget == null ? null : menuTarget.clickPoint;
    }

    private MenuOptionTarget findTargetMenuOption(String option)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.getClient().isMenuOpen())
            {
                return null;
            }

            Menu menu = Microbot.getClient().getMenu();
            MenuEntry[] entries = menu.getMenuEntries();
            for (int i = entries.length - 1; i >= 0; i--)
            {
                MenuEntry entry = entries[i];
                if (!option.equalsIgnoreCase(entry.getOption()))
                {
                    continue;
                }
                NPC npc = entry.getNpc();
                if (npc == null || npc.getIndex() != targetIndex)
                {
                    continue;
                }

                int displayedRow = entries.length - i - 1;
                int rowTop = menu.getMenuY() + 19 + displayedRow * 15;
                Rectangle rowBounds = new Rectangle(
                        menu.getMenuX() + 2,
                        rowTop,
                        Math.max(1, menu.getMenuWidth() - 4),
                        15);
                int menuCentreX = menu.getMenuX() + menu.getMenuWidth() / 2;
                int maximumOffset = Math.min(24, Math.max(0, menu.getMenuWidth() / 2 - 12));
                int menuX = menuCentreX;
                if (maximumOffset >= 8)
                {
                    int sidewaysOffset = ThreadLocalRandom.current().nextInt(8, maximumOffset + 1);
                    menuX += ThreadLocalRandom.current().nextBoolean() ? sidewaysOffset : -sidewaysOffset;
                }
                return new MenuOptionTarget(new Point(menuX, rowTop + 7), rowBounds);
            }
            return null;
        }).orElse(null);
    }

    private boolean clickMenuPoint(String option, Point menuPoint)
    {
        if (Microbot.naturalMouse == null)
        {
            log.warn("Natural mouse unavailable; refusing direct menu click");
            return false;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current != null)
        {
            int sideways = ThreadLocalRandom.current().nextInt(6, 13)
                    * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            int waypointX = Math.max(2, Math.min(Microbot.getClient().getCanvasWidth() - 2,
                    (current.getX() + menuPoint.getX()) / 2 + sideways));
            int waypointY = Math.max(2, Math.min(Microbot.getClient().getCanvasHeight() - 2,
                    (current.getY() + menuPoint.getY()) / 2));
            Microbot.naturalMouse.moveTo(waypointX, waypointY);
        }
        Microbot.naturalMouse.moveTo(menuPoint.getX(), menuPoint.getY());

        Point actual = Microbot.getClient().getMouseCanvasPosition();
        MenuOptionTarget currentTarget = findTargetMenuOption(option);
        if (currentTarget == null)
        {
            log.warn("Menu option disappeared before click: option={} expected={} actual={} targetIndex={} targetAnimation={}",
                    option, menuPoint, actual, targetIndex, currentTargetAnimation());
            return false;
        }

        if (!currentTarget.contains(actual))
        {
            log.info("Correcting menu cursor before click: option={} expected={} actual={} bounds={}",
                    option, menuPoint, actual, currentTarget.bounds);
            Microbot.naturalMouse.moveTo(currentTarget.clickPoint.getX(), currentTarget.clickPoint.getY());
            actual = Microbot.getClient().getMouseCanvasPosition();
            currentTarget = findTargetMenuOption(option);
        }

        if (currentTarget == null || !currentTarget.contains(actual))
        {
            log.warn("Refusing unverified menu click: option={} expected={} actual={} bounds={} targetIndex={} targetAnimation={}",
                    option,
                    menuPoint,
                    actual,
                    currentTarget == null ? null : currentTarget.bounds,
                    targetIndex,
                    currentTargetAnimation());
            return false;
        }

        log.info("Clicking verified menu option: option={} expected={} actual={} bounds={} targetIndex={} targetAnimation={}",
                option, menuPoint, actual, currentTarget.bounds, targetIndex, currentTargetAnimation());
        Microbot.getMouse().click();
        return true;
    }

    private int currentTargetAnimation()
    {
        Rs2NpcModel target = currentTarget();
        return target == null ? -1 : target.getAnimation();
    }

    private boolean handleHumanizerPriority()
    {
        if (!config.humanizerEnabled())
        {
            humanizerStatus = "Disabled";
            if (isHumanizerState())
            {
                humanizerMouseRecoverAt = 0;
                humanizerMistakeSelectAt = 0;
                humanizerMistakeRecoverAt = 0;
                humanizerBreakUntil = 0;
                humanizerMistakeClicked = false;
                transition(BlackjackState.FINDING_TARGET, "Humanizer disabled");
                return true;
            }
            return false;
        }

        long now = System.currentTimeMillis();
        normalizeInterruptedHumanizer(now);
        if (isHumanizerState())
        {
            return false;
        }
        if (!isSafeHumanizerBoundary())
        {
            return false;
        }

        if (config.humanizerBreaks() && now >= nextSmallBreakAt)
        {
            startHumanizerBreak("Small break", randomBetween(60_000, 120_001));
            nextMicroBreakAt = scheduleFromNow(humanizerBreakUntil,
                    HUMANIZER_MICRO_BREAK_MIN_INTERVAL_MS, HUMANIZER_MICRO_BREAK_MAX_INTERVAL_MS);
            return true;
        }
        if (config.humanizerBreaks() && now >= nextMicroBreakAt)
        {
            startHumanizerBreak("Micro break", randomBetween(25_000, 35_001));
            return true;
        }
        if (config.randomMenuMistakes() && now >= nextHumanizerMistakeAt)
        {
            return startHumanizerMisclick();
        }
        if (config.randomMouseRecovery() && now >= nextHumanizerMouseAt)
        {
            return startHumanizerMouse();
        }

        humanizerStatus = "Scheduled";
        return false;
    }

    private boolean isHumanizerEventDue(long now)
    {
        if (!config.humanizerEnabled())
        {
            return false;
        }
        return (config.humanizerBreaks() && (now >= nextSmallBreakAt || now >= nextMicroBreakAt))
                || (config.randomMenuMistakes() && now >= nextHumanizerMistakeAt)
                || (config.randomMouseRecovery() && now >= nextHumanizerMouseAt);
    }

    private boolean isSafeHumanizerBoundary()
    {
        return state == BlackjackState.FINDING_TARGET
                && !healingRequired
                && !wineRestockPending
                && !Rs2Player.isMoving()
                && !isNpcTargetingPlayer()
                && currentHitpoints() >= config.healBelowPercent();
    }

    private boolean isHumanizerState()
    {
        return state == BlackjackState.HUMANIZER_MOUSE
                || state == BlackjackState.HUMANIZER_MISCLICK
                || state == BlackjackState.HUMANIZER_BREAK;
    }

    private boolean startHumanizerMouse()
    {
        if (Microbot.naturalMouse == null)
        {
            nextHumanizerMouseAt = scheduleFromNow(System.currentTimeMillis(),
                    HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
            return false;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        int canvasWidth = Microbot.getClient().getCanvasWidth();
        int canvasHeight = Microbot.getClient().getCanvasHeight();
        int originX = current == null ? canvasWidth / 2 : current.getX();
        int originY = current == null ? canvasHeight / 2 : current.getY();
        int dx = randomBetween(80, 221) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int dy = randomBetween(40, 161) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int x = Math.max(8, Math.min(canvasWidth - 8, originX + dx));
        int y = Math.max(8, Math.min(canvasHeight - 8, originY + dy));
        Microbot.naturalMouse.moveTo(x, y);
        humanizerMouseRecoverAt = System.currentTimeMillis() + randomBetween(250, 901);
        humanizerStatus = "Mouse wander";
        humanizerEvents++;
        transition(BlackjackState.HUMANIZER_MOUSE, "Recover mouse toward target");
        return true;
    }

    private void recoverHumanizerMouse()
    {
        if (System.currentTimeMillis() < humanizerMouseRecoverAt)
        {
            nextAction = "Pause after mouse wander";
            return;
        }

        Rs2NpcModel target = currentTarget();
        Point anchor = target == null ? null : targetAnchor(target, null);
        if (anchor != null && Microbot.naturalMouse != null)
        {
            Microbot.naturalMouse.moveTo(anchor.getX(), anchor.getY());
        }
        humanizerMouseRecoverAt = 0;
        nextHumanizerMouseAt = scheduleFromNow(System.currentTimeMillis(),
                HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        humanizerStatus = "Scheduled";
        transition(BlackjackState.FINDING_TARGET, "Resume after mouse recovery");
    }

    private boolean startHumanizerMisclick()
    {
        Rs2NpcModel target = currentTarget();
        Point anchor = target == null ? null : targetAnchor(target, null);
        if (anchor == null || !naturalMoveAndClick(target, anchor, true))
        {
            nextHumanizerMistakeAt = scheduleFromNow(System.currentTimeMillis(),
                    HUMANIZER_MISTAKE_MIN_INTERVAL_MS, HUMANIZER_MISTAKE_MAX_INTERVAL_MS);
            return false;
        }

        targetIndex = target.getIndex();
        targetDescription = target.getName() + " (level " + target.getCombatLevel() + ")";
        boolean lureMistake = config.includeLureMistakes()
                && ThreadLocalRandom.current().nextInt(100) < 25;
        humanizerMistakeOption = lureMistake ? "Lure" : "Examine";
        humanizerMistakeSelectAt = System.currentTimeMillis() + randomBetween(220, 601);
        humanizerMistakeRecoverAt = 0;
        humanizerMistakeClicked = false;
        humanizerStatus = "Menu mistake: " + humanizerMistakeOption;
        humanizerEvents++;
        transition(BlackjackState.HUMANIZER_MISCLICK,
                "Select mistaken " + humanizerMistakeOption + " option");
        return true;
    }

    private void runHumanizerMisclick()
    {
        long now = System.currentTimeMillis();
        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            finishHumanizerMisclick("Target moved during menu mistake");
            return;
        }

        if (!humanizerMistakeClicked)
        {
            Point menuPoint = findTargetMenuOptionPoint(humanizerMistakeOption);
            if (menuPoint == null && "Lure".equals(humanizerMistakeOption) && elapsedInState() >= 900)
            {
                humanizerMistakeOption = "Examine";
                humanizerStatus = "Menu mistake: Examine";
                menuPoint = findTargetMenuOptionPoint(humanizerMistakeOption);
            }
            if (menuPoint == null)
            {
                if (elapsedInState() >= 1_800)
                {
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    finishHumanizerMisclick("Mistake option unavailable");
                }
                return;
            }
            if (now < humanizerMistakeSelectAt)
            {
                nextAction = "Hover over mistaken " + humanizerMistakeOption;
                return;
            }
            if (!clickMenuPoint(humanizerMistakeOption, menuPoint))
            {
                return;
            }
            humanizerMistakeClicked = true;
            humanizerMistakeRecoverAt = now + randomBetween(650, 1_401);
            lastInteractionAt = now;
            lastOutcome = "Humanizer selected " + humanizerMistakeOption;
            nextAction = "Recover from " + humanizerMistakeOption + " mistake";
            return;
        }

        if (now < humanizerMistakeRecoverAt || Rs2Player.isMoving() || target.isMoving())
        {
            nextAction = "Wait for menu mistake recovery";
            if (elapsedInState() < 4_000)
            {
                return;
            }
        }
        if (Rs2Dialogue.isInDialogue())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        finishHumanizerMisclick("Recovered from " + humanizerMistakeOption);
    }

    private void finishHumanizerMisclick(String outcome)
    {
        humanizerMistakeClicked = false;
        humanizerMistakeSelectAt = 0;
        humanizerMistakeRecoverAt = 0;
        nextHumanizerMistakeAt = scheduleFromNow(System.currentTimeMillis(),
                HUMANIZER_MISTAKE_MIN_INTERVAL_MS, HUMANIZER_MISTAKE_MAX_INTERVAL_MS);
        burstClickPoint = null;
        targetIndex = -1;
        humanizerStatus = "Scheduled";
        lastOutcome = outcome;
        transition(BlackjackState.FINDING_TARGET, "Reacquire target after menu mistake");
    }

    private void startHumanizerBreak(String type, long duration)
    {
        humanizerBreakType = type;
        humanizerBreakUntil = System.currentTimeMillis() + duration;
        humanizerStatus = type;
        humanizerEvents++;
        transition(BlackjackState.HUMANIZER_BREAK, type);
    }

    private void runHumanizerBreak()
    {
        long now = System.currentTimeMillis();
        if (now < humanizerBreakUntil)
        {
            long seconds = Math.max(1, (humanizerBreakUntil - now + 999) / 1_000);
            nextAction = humanizerBreakType + " (" + seconds + "s)";
            return;
        }

        if ("Micro break".equals(humanizerBreakType))
        {
            nextMicroBreakAt = scheduleFromNow(now,
                    HUMANIZER_MICRO_BREAK_MIN_INTERVAL_MS, HUMANIZER_MICRO_BREAK_MAX_INTERVAL_MS);
        }
        else
        {
            nextSmallBreakAt = scheduleFromNow(now,
                    HUMANIZER_SMALL_BREAK_MIN_INTERVAL_MS, HUMANIZER_SMALL_BREAK_MAX_INTERVAL_MS);
        }
        humanizerBreakUntil = 0;
        humanizerBreakType = "None";
        humanizerStatus = "Scheduled";
        transition(BlackjackState.FINDING_TARGET, "Resume after humanizer break");
    }

    private void normalizeInterruptedHumanizer(long now)
    {
        if (state != BlackjackState.HUMANIZER_MOUSE && humanizerMouseRecoverAt > 0)
        {
            humanizerMouseRecoverAt = 0;
            nextHumanizerMouseAt = scheduleFromNow(now,
                    HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        }
        if (state != BlackjackState.HUMANIZER_MISCLICK
                && (humanizerMistakeSelectAt > 0 || humanizerMistakeRecoverAt > 0))
        {
            humanizerMistakeSelectAt = 0;
            humanizerMistakeRecoverAt = 0;
            humanizerMistakeClicked = false;
            nextHumanizerMistakeAt = scheduleFromNow(now,
                    HUMANIZER_MISTAKE_MIN_INTERVAL_MS, HUMANIZER_MISTAKE_MAX_INTERVAL_MS);
        }
        if (state != BlackjackState.HUMANIZER_BREAK && humanizerBreakUntil > 0)
        {
            humanizerBreakUntil = 0;
            humanizerBreakType = "None";
            nextMicroBreakAt = scheduleFromNow(now,
                    HUMANIZER_MICRO_BREAK_MIN_INTERVAL_MS, HUMANIZER_MICRO_BREAK_MAX_INTERVAL_MS);
            nextSmallBreakAt = scheduleFromNow(now,
                    HUMANIZER_SMALL_BREAK_MIN_INTERVAL_MS, HUMANIZER_SMALL_BREAK_MAX_INTERVAL_MS);
        }
    }

    private int randomKnockoutMenuDwell()
    {
        if (!config.humanizerEnabled())
        {
            return randomBetween(KNOCKOUT_MENU_DWELL_MIN_MS, KNOCKOUT_MENU_DWELL_MAX_MS);
        }
        int delay = randomBetween(160, 341);
        return ThreadLocalRandom.current().nextInt(100) < 7
                ? delay + randomBetween(50, 141)
                : delay;
    }

    private int randomPickpocketDelay(boolean firstClick)
    {
        int delay = firstClick
                ? randomBetween(FIRST_PICKPOCKET_DELAY_MIN_MS, FIRST_PICKPOCKET_DELAY_MAX_MS)
                : randomBetween(PICKPOCKET_CLICK_DELAY_MIN_MS, PICKPOCKET_CLICK_DELAY_MAX_MS);
        if (config.humanizerEnabled() && ThreadLocalRandom.current().nextInt(100) < 5)
        {
            delay += randomBetween(20, firstClick ? 61 : 81);
        }
        return delay;
    }

    private int randomFailedKnockoutRetry()
    {
        return config.humanizerEnabled()
                ? randomBetween(350, 751)
                : (int) FAILED_KNOCKOUT_RETRY_MS;
    }

    private int randomKnockoutAttemptDelay()
    {
        if (!config.humanizerEnabled())
        {
            return 80;
        }
        return picksThisKnockout >= 2
                ? randomBetween(25, 111)
                : randomBetween(70, 241);
    }

    private static int randomBetween(int minimumInclusive, int maximumExclusive)
    {
        return ThreadLocalRandom.current().nextInt(minimumInclusive, maximumExclusive);
    }

    private static long randomBetween(long minimumInclusive, long maximumExclusive)
    {
        return ThreadLocalRandom.current().nextLong(minimumInclusive, maximumExclusive);
    }

    private static long scheduleFromNow(long now, long minimumDelay, long maximumDelay)
    {
        return now + randomBetween(minimumDelay, maximumDelay);
    }

    private void maintainTargetCamera(Rs2NpcModel target)
    {
        if (target == null || target.getNpc() == null || target.getWorldLocation() == null)
        {
            return;
        }

        WorldPoint targetLocation = target.getWorldLocation();
        if (targetLocation.equals(lastCameraTargetLocation))
        {
            return;
        }
        if (COMBAT_SAFE_TILE.equals(targetLocation))
        {
            lastCameraTargetLocation = targetLocation;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCameraPivotAt < CAMERA_PIVOT_COOLDOWN_MS || Microbot.getClient().isMenuOpen())
        {
            return;
        }

        int targetYaw = Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(target.getNpc()));
        int currentYaw = Rs2Camera.getYaw();
        int delta = (targetYaw - currentYaw + 3_072) % 2_048 - 1_024;
        if (Math.abs(delta) >= CAMERA_PIVOT_THRESHOLD)
        {
            int pivot = Math.max(-CAMERA_PIVOT_STEP, Math.min(CAMERA_PIVOT_STEP, delta));
            Rs2Camera.setYaw((currentYaw + pivot + 2_048) % 2_048);
            lastCameraPivotAt = now;
            log.debug("Pivoting camera toward moved target: location={}, yaw={} -> {}",
                    targetLocation, currentYaw, (currentYaw + pivot + 2_048) % 2_048);
        }
        lastCameraTargetLocation = targetLocation;
    }

    private void maintainTopDownCamera()
    {
        long now = System.currentTimeMillis();
        if (now - lastCameraPitchAt < CAMERA_PITCH_COOLDOWN_MS || Microbot.getClient().isMenuOpen())
        {
            return;
        }
        if (Rs2Camera.getPitch() < TOP_DOWN_CAMERA_PITCH - TOP_DOWN_CAMERA_TOLERANCE)
        {
            Rs2Camera.setPitch(TOP_DOWN_CAMERA_PITCH);
            log.debug("Restoring top-down camera pitch: {}", TOP_DOWN_CAMERA_PITCH);
        }
        lastCameraPitchAt = now;
    }

    private Point targetAnchor(Rs2NpcModel target, Point preferred)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (target.getNpc() == null || target.getNpc().getLocalLocation() == null
                    || Microbot.getClient().getTopLevelWorldView() == null)
            {
                return null;
            }
            Point feet = Perspective.localToCanvas(
                    Microbot.getClient(),
                    target.getNpc().getLocalLocation(),
                    Microbot.getClient().getTopLevelWorldView().getPlane(),
                    0);
            Shape hull = target.getNpc().getConvexHull();
            if (feet == null || hull == null)
            {
                return null;
            }

            Rectangle bounds = hull.getBounds();
            int lowerBodyTop = bounds.y + Math.max(2, bounds.height * 7 / 10);
            int lowerBodyBottom = bounds.y + Math.max(3, bounds.height * 93 / 100);

            if (preferred != null
                    && preferred.getY() >= lowerBodyTop
                    && preferred.getY() <= lowerBodyBottom
                    && hull.contains(preferred.getX(), preferred.getY()))
            {
                return preferred;
            }

            Point currentMouse = Microbot.getClient().getMouseCanvasPosition();
            Point reference = preferred != null ? preferred : currentMouse;
            int referenceX = reference == null
                    ? bounds.x + bounds.width / 2
                    : Math.max(bounds.x + 2, Math.min(bounds.x + bounds.width - 3, reference.getX()));
            int referenceY = reference == null
                    ? lowerBodyTop + (lowerBodyBottom - lowerBodyTop) / 2
                    : Math.max(lowerBodyTop, Math.min(lowerBodyBottom, reference.getY()));

            Point nearest = null;
            long nearestDistanceSquared = Long.MAX_VALUE;
            int left = bounds.x + 2;
            int right = Math.max(left, bounds.x + bounds.width - 3);
            for (int y = lowerBodyTop; y <= lowerBodyBottom; y += 2)
            {
                for (int x = left; x <= right; x += 2)
                {
                    if (!hull.contains(x, y))
                    {
                        continue;
                    }
                    long dx = x - referenceX;
                    long dy = y - referenceY;
                    long distanceSquared = dx * dx + dy * dy;
                    if (distanceSquared < nearestDistanceSquared)
                    {
                        nearest = new Point(x, y);
                        nearestDistanceSquared = distanceSquared;
                    }
                }
            }
            if (nearest != null)
            {
                return nearest;
            }

            int[] yOffsets = {10, 13, 16, 19, 22, 25, 28};
            int[] xOffsets = {0, -3, 3, -5, 5};
            for (int yOffset : yOffsets)
            {
                for (int xOffset : xOffsets)
                {
                    Point candidate = new Point(feet.getX() + xOffset, feet.getY() - yOffset);
                    if (hull.contains(candidate.getX(), candidate.getY()))
                    {
                        return candidate;
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    private Point burstWanderAnchor(Rs2NpcModel target, Point preferred)
    {
        Point base = targetAnchor(target, preferred);
        if (base == null || preferred == null || config == null || !config.humanizerEnabled()
                || ThreadLocalRandom.current().nextInt(100) >= BURST_WANDER_CHANCE_PERCENT)
        {
            return base;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (target.getNpc() == null)
            {
                return base;
            }
            Shape hull = target.getNpc().getConvexHull();
            if (hull == null)
            {
                return base;
            }

            Rectangle bounds = hull.getBounds();
            int wanderTop = bounds.y + Math.max(2, bounds.height * 55 / 100);
            int wanderBottom = bounds.y + Math.max(3, bounds.height * 93 / 100);
            for (int attempt = 0; attempt < 12; attempt++)
            {
                int dx = randomBetween(-BURST_WANDER_MAX_X, BURST_WANDER_MAX_X + 1);
                int directionRoll = ThreadLocalRandom.current().nextInt(100);
                int dy;
                if (directionRoll < 55)
                {
                    dy = -randomBetween(2, BURST_WANDER_MAX_UP + 1);
                }
                else if (directionRoll < 90)
                {
                    dy = randomBetween(-2, 3);
                }
                else
                {
                    dy = randomBetween(1, BURST_WANDER_MAX_DOWN + 1);
                }

                Point candidate = new Point(base.getX() + dx, base.getY() + dy);
                if (candidate.getY() >= wanderTop
                        && candidate.getY() <= wanderBottom
                        && hull.contains(candidate.getX(), candidate.getY()))
                {
                    return candidate;
                }
            }
            return base;
        }).orElse(base);
    }

    private boolean isCurtainMenuOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.getClient().isMenuOpen())
            {
                return false;
            }
            MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            if (entries == null)
            {
                return false;
            }
            for (MenuEntry entry : entries)
            {
                String option = entry.getOption() == null ? "" : entry.getOption().trim();
                String menuTarget = entry.getTarget() == null
                        ? ""
                        : entry.getTarget().toLowerCase(Locale.ROOT);
                if ("Close".equalsIgnoreCase(option) && menuTarget.contains("curtain"))
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private void waitForTargetClear(String reason, boolean rotateCamera)
    {
        pendingInventoryFullAt = 0;
        burstClickPoint = null;
        lastPickpocketClickAt = 0;
        targetClearReason = reason;
        targetClearRecheckAt = System.currentTimeMillis() + (rotateCamera
                ? ThreadLocalRandom.current().nextInt(700, 1_101)
                : ThreadLocalRandom.current().nextInt(100, 251));

        if (rotateCamera && !curtainCameraAdjusted)
        {
            int magnitude = ThreadLocalRandom.current().nextInt(90, 151);
            int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            int yaw = (Rs2Camera.getYaw() + direction * magnitude + 2_048) % 2_048;
            Rs2Camera.setYaw(yaw);
            curtainCameraAdjusted = true;
            log.info("Rotating camera to clear curtain obstruction: yaw={}", yaw);
        }

        transition(BlackjackState.WAITING_FOR_TARGET_CLEAR,
                rotateCamera ? "Rotate camera, then wait for clear target" : "Wait for movement to stop");
    }

    private void waitForTargetClear()
    {
        if (Rs2Player.isMoving())
        {
            nextAction = "Wait for movement to stop";
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            targetIndex = -1;
            transition(BlackjackState.FINDING_TARGET, "Reacquire target after obstruction");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < targetClearRecheckAt)
        {
            return;
        }

        log.info("Retrying target after {}", targetClearReason);
        targetClearReason = "None";
        targetClearRecheckAt = 0;
        burstClickPoint = null;
        transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out after obstruction");
    }

    private boolean consumeInventoryFullResetIfIdle()
    {
        if (pendingInventoryFullAt == 0)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (knockoutResult == KnockoutResult.PENDING)
        {
            pendingInventoryFullAt = 0;
            nextAction = now < knockoutBurstReleaseAt
                    ? "Confirm Knock-Out dispatch"
                    : "Safety pickpocket while confirming Knock-Out";
            return false;
        }
        if (now - pendingInventoryFullAt > INVENTORY_FULL_SIGNAL_MAX_AGE_MS)
        {
            pendingInventoryFullAt = 0;
            return false;
        }

        if (isPickpocketAnimationActive())
        {
            return false;
        }
        if (isRecentInteractionWithTarget(now))
        {
            nextAction = "Confirm active pickpocket before reset";
            return true;
        }

        log.info("Idle full-inventory signal confirmed after {}/2 pickpockets; retrying Knock-Out",
                picksThisKnockout);
        pendingInventoryFullAt = 0;
        burstClickPoint = null;
        transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out after idle inventory interrupt");
        return true;
    }

    private boolean isRecentInteractionWithTarget(long now)
    {
        if (now - lastPickpocketClickAt > ACTIVE_INTERACTION_GRACE_MS)
        {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC))
            {
                return false;
            }
            return ((NPC) player.getInteracting()).getIndex() == targetIndex;
        }).orElse(false);
    }

    private boolean isPickpocketAnimationActive()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getAnimation() == AnimationID.HUMAN_PICKPOCKET;
        }).orElse(false);
    }

    private void confirmKnockout(String signal)
    {
        if (knockoutResult != KnockoutResult.PENDING)
        {
            return;
        }
        knockoutResult = KnockoutResult.SUCCESS;
        long confirmedAt = System.currentTimeMillis();
        knockoutFailedAt = 0;
        nextPickpocketClickAt = confirmedAt + randomPickpocketDelay(true);
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = confirmedAt;
        successfulKnockouts++;
        pendingInventoryFullAt = 0;
        lastOutcome = "Knock-Out succeeded";
        log.info("Knock-Out confirmed by {} after {}ms", signal, knockoutDispatchAge(confirmedAt));
        if (wineRestockPending && waitingForRestockKnockout)
        {
            waitingForRestockKnockout = false;
            restockAfterCombatReset = false;
            transition(BlackjackState.EXITING_FOR_WINE, "Guard secured; exit for wine");
            return;
        }
        nextAction = "Spam pickpocket " + (picksThisKnockout + 1) + "/2";
    }

    private long knockoutDispatchAge(long now)
    {
        return knockoutClickIssuedAt == 0 ? -1 : Math.max(0, now - knockoutClickIssuedAt);
    }

    private boolean isPlayerInteractingWithTarget(Rs2NpcModel target)
    {
        if (target == null)
        {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null
                    && player.getInteracting() instanceof NPC
                    && ((NPC) player.getInteracting()).getIndex() == target.getIndex();
        }).orElse(false);
    }

    private void armNextKnockout(Rs2NpcModel target)
    {
        if (nextKnockoutArmedAt != 0)
        {
            return;
        }

        nextKnockoutArmedAt = System.currentTimeMillis();
        secondPickpocketInteractionSeen = isPlayerInteractingWithTarget(target);
        secondPickpocketInteractionComplete = false;
        if (secondPickpocketInteractionSeen)
        {
            log.info("Second pickpocket interaction observed; pre-arming Knock-Out");
        }
    }

    private boolean isSecondPickpocketPrearmPending()
    {
        return nextKnockoutArmedAt != 0 && picksThisKnockout < 2;
    }

    private void cancelSecondPickpocketPrearm(String reason)
    {
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        nextKnockoutArmedAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        nextPickpocketClickAt = System.currentTimeMillis() + randomPickpocketDelay(false);
        log.info("Cancelling pre-armed Knock-Out: {}", reason);
    }

    private void observeSecondPickpocketInteraction(Rs2NpcModel target)
    {
        if (nextKnockoutArmedAt == 0 || secondPickpocketInteractionComplete)
        {
            return;
        }

        boolean interacting = isPlayerInteractingWithTarget(target);
        if (!secondPickpocketInteractionSeen)
        {
            if (interacting)
            {
                secondPickpocketInteractionSeen = true;
                log.info("Second pickpocket interaction observed; pre-arming Knock-Out");
            }
            return;
        }

        if (!interacting)
        {
            secondPickpocketInteractionComplete = true;
            log.info("Second pickpocket interaction completed; selecting Knock-Out");
        }
    }

    private boolean allowSecondPickpocketInteractionFallback(Rs2NpcModel target)
    {
        if (secondPickpocketInteractionComplete)
        {
            return true;
        }
        if (nextKnockoutArmedAt == 0
                || System.currentTimeMillis() - nextKnockoutArmedAt < SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS)
        {
            return false;
        }
        if (isPlayerInteractingWithTarget(target))
        {
            return false;
        }

        secondPickpocketInteractionComplete = true;
        log.warn("Second pickpocket interaction signal timed out after {}ms; player is idle, allowing Knock-Out fallback",
                System.currentTimeMillis() - nextKnockoutArmedAt);
        return true;
    }

    private boolean shouldEscapeCombat()
    {
        long now = System.currentTimeMillis();
        if (now < ignoreCombatUntil)
        {
            combatSignal = false;
            npcInteractionSince = 0;
            return false;
        }
        combatSignal = isNpcTargetingPlayer();
        if (!combatSignal)
        {
            npcInteractionSince = 0;
            return false;
        }
        if (npcInteractionSince == 0)
        {
            npcInteractionSince = now;
        }
        return now - npcInteractionSince >= SUSTAINED_NPC_ATTACK_MS;
    }

    private boolean isNpcTargetingPlayer()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null)
            {
                return false;
            }
            return Microbot.getRs2NpcCache().query().toList().stream()
                    .anyMatch(npc -> npc != null && npc.getNpc() != null
                            && npc.getNpc().getWorldLocation() != null
                            && SUPPORTED_HOUSE.contains(npc.getNpc().getWorldLocation())
                            && npc.getNpc().getInteracting() == player);
        }).orElse(false);
    }

    private void updateHealingRequirement()
    {
        int hitpoints = currentHitpoints();
        if (hitpoints < config.healBelowPercent() && !healingRequired)
        {
            healingRequired = true;
            log.info("Healing latched at {} HP; configured below={}, heal-to={}",
                    hitpoints, config.healBelowPercent(), config.healToPercent());
        }
        else if (hitpoints >= config.healToPercent() && healingRequired)
        {
            healingRequired = false;
            log.info("Healing target reached at {} HP", hitpoints);
        }
    }

    private boolean drinkWineIfReady(String action)
    {
        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastDrinkAt < DRINK_COOLDOWN_MS || !Rs2Inventory.interact(WINE_ID, "Drink"))
        {
            return false;
        }

        lastDrinkAt = now;
        lastInteractionAt = now;
        nextAction = action;
        return true;
    }

    private boolean isCombatSafetyState()
    {
        return state == BlackjackState.POSITIONING_COMBAT_RESET
                || state == BlackjackState.ESCAPING_COMBAT
                || state == BlackjackState.WAITING_FOR_COMBAT_CLEAR;
    }

    private int currentHitpoints()
    {
        return Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    private boolean readyForInteraction(long minimumDelay)
    {
        return System.currentTimeMillis() - lastInteractionAt >= minimumDelay;
    }

    private long elapsedInState()
    {
        return System.currentTimeMillis() - stateEnteredAt;
    }

    public long getStateAgeSeconds()
    {
        return Math.max(0, elapsedInState() / 1_000);
    }

    private void transition(BlackjackState newState, String action)
    {
        if (state != newState)
        {
            log.info("Blackjack state {} -> {} ({})", state, newState, action);
            if (newState == BlackjackState.KNOCKING_OUT)
            {
                knockoutAttemptReadyAt = System.currentTimeMillis() + randomKnockoutAttemptDelay();
            }
            state = newState;
            stateEnteredAt = System.currentTimeMillis();
        }
        nextAction = action;
        Microbot.status = "Blackjack: " + action;
    }

    private void fail(String reason)
    {
        stopReason = reason;
        log.error("Blackjack stopped: {}", reason);
        transition(BlackjackState.ERROR, "Disable plugin and correct setup");
    }

    private int currentThievingXp()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(Skill.THIEVING)).orElse(0);
    }

    public int getXpGained()
    {
        return Math.max(0, currentThievingXp() - startXp);
    }

    public String getFormattedRuntime()
    {
        if (startTime == null)
        {
            return "00:00:00";
        }
        Duration duration = Duration.between(startTime, Instant.now());
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void shutdown()
    {
        shutdownRequested = true;
        outcomes.clear();
        targetIndex = -1;
        knockoutResult = KnockoutResult.NONE;
        knockoutClickIssuedAt = 0;
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        combatSignal = false;
        burstClickPoint = null;
        wineRestockPending = false;
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        humanizerStatus = "Disabled";
        humanizerMouseRecoverAt = 0;
        humanizerMistakeSelectAt = 0;
        humanizerMistakeRecoverAt = 0;
        humanizerBreakUntil = 0;
        humanizerMistakeClicked = false;
        humanizerBreakType = "None";
        transition(BlackjackState.STOPPED, "Enable plugin");
        super.shutdown();
    }
}
