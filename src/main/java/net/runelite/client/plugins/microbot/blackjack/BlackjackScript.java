package net.runelite.client.plugins.microbot.blackjack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.Shape;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    private static final int WINE_ID = 1993;
    private static final int NOTED_WINE_ID = 1994;
    private static final int COINS_ID = 995;

    private static final long FAILED_KNOCKOUT_RETRY_MS = 450;
    private static final long PICKPOCKET_BURST_TIMEOUT_MS = 2_800;
    private static final long KNOCKOUT_MENU_TIMEOUT_MS = 1_200;
    private static final int KNOCKOUT_MENU_DWELL_MIN_MS = 180;
    private static final int KNOCKOUT_MENU_DWELL_MAX_MS = 261;
    private static final int FIRST_PICKPOCKET_DELAY_MIN_MS = 90;
    private static final int FIRST_PICKPOCKET_DELAY_MAX_MS = 141;
    private static final int PICKPOCKET_CLICK_DELAY_MIN_MS = 150;
    private static final int PICKPOCKET_CLICK_DELAY_MAX_MS = 206;
    private static final int NEXT_KNOCKOUT_DELAY_MIN_MS = 650;
    private static final int NEXT_KNOCKOUT_DELAY_MAX_MS = 901;
    private static final long DRINK_COOLDOWN_MS = 1_750;
    private static final long COMBAT_CLEAR_SETTLE_MS = 650;
    private static final long COMBAT_RESET_RETRY_MS = 2_500;
    private static final long FAILED_KNOCKOUT_RETALIATION_GRACE_MS = 1_800;
    private static final long SUSTAINED_NPC_ATTACK_MS = 3_000;

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
    private long knockoutReadyAt;
    private long nextPickpocketClickAt;
    private Point burstClickPoint;
    private volatile KnockoutResult knockoutResult = KnockoutResult.NONE;
    private volatile boolean shutdownRequested;
    private BlackjackState stateBeforeHealing = BlackjackState.FINDING_TARGET;

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
        targetIndex = -1;
        npcInteractionSince = 0;
        ignoreCombatUntil = 0;
        knockoutMenuSelectAt = 0;
        knockoutReadyAt = 0;
        nextPickpocketClickAt = 0;
        burstClickPoint = null;
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

                if (state != BlackjackState.POSITIONING_COMBAT_RESET
                        && state != BlackjackState.ESCAPING_COMBAT
                        && state != BlackjackState.WAITING_FOR_COMBAT_CLEAR
                        && shouldEscapeCombat())
                {
                    transition(BlackjackState.POSITIONING_COMBAT_RESET, "Move to combat staging tile");
                }

                if (shouldHeal() && state != BlackjackState.HEALING && state != BlackjackState.RESTOCKING_WINE
                        && state != BlackjackState.POSITIONING_COMBAT_RESET
                        && state != BlackjackState.ESCAPING_COMBAT
                        && state != BlackjackState.WAITING_FOR_COMBAT_CLEAR)
                {
                    stateBeforeHealing = state;
                    transition(BlackjackState.HEALING, "Drink wine");
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
            case RESTOCKING_WINE:
                restockWine();
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
        transition(BlackjackState.KNOCKING_OUT, "Knock-Out target");
    }

    private void knockOutTarget()
    {
        if (System.currentTimeMillis() < knockoutReadyAt)
        {
            nextAction = "Wait for bandit to stand";
            return;
        }

        if (Rs2Player.isStunned())
        {
            nextAction = "Wait for stun";
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        Point anchor = targetAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            nextAction = "Wait for safe NPC click point";
            return;
        }

        if (readyForInteraction(80))
        {
            Microbot.getMouse().click(anchor, true);
            burstClickPoint = anchor;
            long now = System.currentTimeMillis();
            lastInteractionAt = now;
            knockoutReadyAt = 0;
            knockoutMenuSelectAt = now + ThreadLocalRandom.current().nextInt(
                    KNOCKOUT_MENU_DWELL_MIN_MS, KNOCKOUT_MENU_DWELL_MAX_MS);
            transition(BlackjackState.SELECTING_KNOCKOUT, "Select Knock-Out from menu");
        }
    }

    private void selectKnockoutMenuEntry()
    {
        Point menuPoint = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.getClient().isMenuOpen())
            {
                return null;
            }

            Menu menu = Microbot.getClient().getMenu();
            MenuEntry[] entries = menu.getMenuEntries();
            for (int i = entries.length - 1; i >= 0; i--)
            {
                MenuEntry entry = entries[i];
                if (!"Knock-Out".equalsIgnoreCase(entry.getOption()))
                {
                    continue;
                }
                NPC npc = entry.getNpc();
                if (npc != null && npc.getIndex() != targetIndex)
                {
                    continue;
                }

                int displayedRow = entries.length - i - 1;
                return new Point(
                        menu.getMenuX() + menu.getMenuWidth() / 2,
                        menu.getMenuY() + 19 + displayedRow * 15 + 7);
            }
            return null;
        }).orElse(null);

        if (menuPoint != null)
        {
            if (System.currentTimeMillis() < knockoutMenuSelectAt)
            {
                nextAction = "Pause over Knock-Out menu";
                return;
            }
            Microbot.getMouse().click(menuPoint);
            long now = System.currentTimeMillis();
            lastInteractionAt = now;
            nextPickpocketClickAt = now + ThreadLocalRandom.current().nextInt(
                    FIRST_PICKPOCKET_DELAY_MIN_MS, FIRST_PICKPOCKET_DELAY_MAX_MS);
            picksThisKnockout = 0;
            knockoutResult = KnockoutResult.PENDING;
            burstClickPoint = null;
            lastOutcome = "Knock-Out selected";
            transition(BlackjackState.PICKPOCKETING, "Spam pickpocket 1/2");
            return;
        }

        if (elapsedInState() >= KNOCKOUT_MENU_TIMEOUT_MS)
        {
            knockoutMenuMisses++;
            lastOutcome = "Knock-Out menu missed";
            log.warn("Knock-Out menu entry was not available; retrying right-click");
            transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out menu");
        }
    }

    private void runPickpocketBurst()
    {
        if (knockoutResult == KnockoutResult.FAILED && elapsedInState() >= FAILED_KNOCKOUT_RETRY_MS)
        {
            transition(BlackjackState.KNOCKING_OUT, "Retry failed Knock-Out");
            return;
        }

        if (Rs2Player.isStunned())
        {
            nextAction = "Wait for stun; keep burst armed";
            return;
        }

        if (picksThisKnockout >= 2)
        {
            scheduleNextKnockout();
            transition(BlackjackState.KNOCKING_OUT, "Pre-armed next Knock-Out");
            return;
        }

        if (elapsedInState() >= PICKPOCKET_BURST_TIMEOUT_MS)
        {
            burstTimeouts++;
            lastOutcome = "Burst timeout: " + picksThisKnockout + "/2";
            log.warn("Pickpocket burst timed out after {} confirmed picks", picksThisKnockout);
            transition(BlackjackState.KNOCKING_OUT, "Recover burst timing");
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (System.currentTimeMillis() >= nextPickpocketClickAt && clickAnchoredTarget(target))
        {
            long now = System.currentTimeMillis();
            lastInteractionAt = now;
            nextPickpocketClickAt = now + ThreadLocalRandom.current().nextInt(
                    PICKPOCKET_CLICK_DELAY_MIN_MS, PICKPOCKET_CLICK_DELAY_MAX_MS);
            pickpocketClicks++;
            nextAction = "Confirm pickpocket " + (picksThisKnockout + 1) + "/2";
        }
    }

    private void heal()
    {
        if (Rs2Player.getHealthPercentage() >= config.healToPercent())
        {
            transition(resumeStateAfterHealing(), "Resume blackjack loop");
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

        long now = System.currentTimeMillis();
        if (now - lastDrinkAt >= DRINK_COOLDOWN_MS && Rs2Inventory.interact(WINE_ID, "Drink"))
        {
            lastDrinkAt = now;
            lastInteractionAt = now;
            nextAction = "Wait for wine heal";
        }
    }

    private BlackjackState resumeStateAfterHealing()
    {
        if (stateBeforeHealing == BlackjackState.SELECTING_KNOCKOUT
                || stateBeforeHealing == BlackjackState.PICKPOCKETING)
        {
            return BlackjackState.FINDING_TARGET;
        }
        return stateBeforeHealing;
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
                transition(BlackjackState.FINDING_TARGET, "Probe Knock-Out after reset");
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
            transition(BlackjackState.FINDING_TARGET, "Reacquire target");
        }
    }

    private void restockWine()
    {
        if (Rs2Inventory.count(WINE_ID) > 0 && isInsideHouse())
        {
            transition(BlackjackState.FINDING_TARGET, "Resume with fresh wine");
            return;
        }

        if (Rs2Inventory.count(WINE_ID) > 0)
        {
            if (Rs2Shop.isOpen())
            {
                Rs2Shop.closeShop();
            }
            transition(BlackjackState.RETURNING_TO_HOUSE, "Return to marked house");
            return;
        }

        if (Rs2Inventory.getEmptySlots() == 0)
        {
            fail("No space for wine; retaining empty jugs for blackjack safety");
            return;
        }

        if (!Rs2Inventory.hasItem(COINS_ID))
        {
            fail("Coins required to restock wine");
            return;
        }

        if (Rs2Inventory.hasItem(NOTED_WINE_ID))
        {
            exchangeNotedWine();
            return;
        }

        buyWineFromFaisal();
    }

    private void exchangeNotedWine()
    {
        if (Rs2Dialogue.hasSelectAnOption())
        {
            int option = Rs2Inventory.getEmptySlots() > 1 ? 2 : 1;
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
            fail("Banknote Exchange Merchant not found");
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
            Rs2Inventory.useItemOnNpc(NOTED_WINE_ID, merchant.getNpc());
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Choose note quantity";
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
                    knockoutResult = KnockoutResult.SUCCESS;
                    successfulKnockouts++;
                    lastOutcome = "Knock-Out succeeded";
                    nextAction = "Confirm pickpocket " + (picksThisKnockout + 1) + "/2";
                    break;
                case KNOCKOUT_FAILED:
                    if (state != BlackjackState.PICKPOCKETING || knockoutResult != KnockoutResult.PENDING)
                    {
                        log.debug("Ignoring stale knockout failure in state {}", state);
                        break;
                    }
                    knockoutResult = KnockoutResult.FAILED;
                    failedKnockouts++;
                    lastOutcome = "Knock-Out failed";
                    ignoreCombatUntil = System.currentTimeMillis() + FAILED_KNOCKOUT_RETALIATION_GRACE_MS;
                    npcInteractionSince = 0;
                    nextAction = "Interrupt retaliation, then retry Knock-Out";
                    break;
                case PICKPOCKET_SUCCESS:
                    if (state == BlackjackState.PICKPOCKETING)
                    {
                        successfulPickpockets++;
                        picksThisKnockout++;
                        lastOutcome = "Pickpocket " + picksThisKnockout + "/2";
                        if (picksThisKnockout >= 2)
                        {
                            scheduleNextKnockout();
                            transition(BlackjackState.KNOCKING_OUT, "Pre-armed next Knock-Out");
                        }
                    }
                    break;
                case PICKPOCKET_FAILED:
                case STUNNED:
                    if (state != BlackjackState.PICKPOCKETING)
                    {
                        log.debug("Ignoring stale pickpocket failure/stun in state {}", state);
                        break;
                    }
                    nextAction = "Wait for stun; keep loop armed";
                    lastOutcome = "Pickpocket failed/stunned";
                    break;
                case INVENTORY_FULL:
                    if (state == BlackjackState.PICKPOCKETING)
                    {
                        log.info("Full-inventory interrupt confirmed after {}/2 pickpockets; retrying Knock-Out",
                                picksThisKnockout);
                        lastOutcome = "Full-inventory interrupt confirmed";
                        knockoutReadyAt = 0;
                        transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out after interrupt");
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
        Point anchor = targetAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            burstClickPoint = null;
            nextAction = "Wait for safe NPC click point";
            return false;
        }
        if (burstClickPoint != null && burstClickPoint.equals(anchor))
        {
            Microbot.getMouse().click();
        }
        else
        {
            Microbot.getMouse().click(anchor);
            burstClickPoint = anchor;
        }
        return true;
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
            if (preferred != null && hull.contains(preferred.getX(), preferred.getY()))
            {
                return preferred;
            }

            int[] yOffsets = {17, 20, 23, 26, 29, 32, 35};
            int[] xOffsets = {0, -3, 3, -6, 6};
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

    private boolean shouldHeal()
    {
        return Rs2Player.getHealthPercentage() < config.healBelowPercent();
    }

    private boolean readyForInteraction(long minimumDelay)
    {
        return System.currentTimeMillis() - lastInteractionAt >= minimumDelay;
    }

    private long elapsedInState()
    {
        return System.currentTimeMillis() - stateEnteredAt;
    }

    private void scheduleNextKnockout()
    {
        if (knockoutReadyAt == 0)
        {
            knockoutReadyAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(
                    NEXT_KNOCKOUT_DELAY_MIN_MS, NEXT_KNOCKOUT_DELAY_MAX_MS);
        }
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
        combatSignal = false;
        burstClickPoint = null;
        transition(BlackjackState.STOPPED, "Enable plugin");
        super.shutdown();
    }
}
