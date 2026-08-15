package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Separate, restartable Herblore workflow. Each processing phase delegates to a
 * quantity-limited normal processor; GE and decant actions remain explicit and
 * are reconciled before the controller may advance.
 */
@Slf4j
public final class ContinuousHerbloreProcessor implements BankStandingProcessor {
    private static final long SALE_SHORT_BREAK_MIN_MILLIS = 3 * 60_000L;
    private static final long SALE_SHORT_BREAK_MAX_MILLIS = 5 * 60_000L;
    private static final long SALE_LOGOUT_BREAK_MILLIS = 15 * 60_000L;
    private static final long LOGIN_RETRY_MILLIS = 30_000L;

    private final ConfigData config;
    private final ContinuousHerblorePlan plan;
    private final ContinuousHerbloreController controller;
    private final HerbloreGrandExchangeAdapter exchange;
    private final HerbloreDecantAdapter decanter = new HerbloreDecantAdapter();
    private HerbloreRecipeMetadata recipe;
    private HerbloreProcessor phaseWorker;
    private ContinuousHerblorePhase workerPhase;
    private int baselineFinishedContainers;
    private int expectedOutputContainers;
    private String detail = "Awaiting precheck";
    private String lastEquipmentStatus = "Not checked";
    private int herbsCleaned;
    private int unfinishedPotionsMade;
    private int finishedPotionsMade;
    private int nextSaleCheckpoint;
    private int lastInterimCheckpoint;
    private int pendingInterimContainers;
    private ContinuousHerblorePhase activeSalePhase;
    private int salePhaseBaselineReconciled;
    private int salePhaseTarget;
    private SaleWaitStage saleWaitStage = SaleWaitStage.NONE;
    private long saleWaitUntil;
    private int saleProgressAtLogout;
    private int logoutReturnWorld;
    private long lastLoginAttemptAt;

    public ContinuousHerbloreProcessor(ConfigData config) {
        this.config = new ConfigData(config);
        this.plan = new ContinuousHerblorePlan(
                config.getContinuousCapitalReserve(), config.getContinuousMaxBuyPrice(),
                config.getContinuousMinSellPrice(), config.getContinuousRetryLimit(),
                config.getContinuousPhaseTimeoutSeconds() * 1000L,
                config.getContinuousStopLoss(), config.getContinuousCycleLimit(),
                config.isContinuousUnlimitedCycles(), config.isContinuousDecant(),
                true);
        this.controller = new ContinuousHerbloreController(plan, System.currentTimeMillis());
        this.exchange = new HerbloreGrandExchangeAdapter(controller);
    }

    @Override
    public boolean validate() {
        try {
            recipe = HerbloreRecipeMetadata.resolve(config.getFinishedPotion());
        } catch (RuntimeException ex) {
            detail = "Recipe metadata unavailable: " + ex.getMessage();
            log.info(detail);
            return false;
        }
        if (!recipe.hasCleanAndUnfinishedPhases() || !recipe.hasResolvedFinishedItems()) {
            detail = "Recipe is not a normal herb-to-potion shape";
            log.info("Continuous mode rejected {}: {}", config.getFinishedPotion(), detail);
            return false;
        }
        if (Rs2Player.getRealSkillLevel(Skill.HERBLORE) < recipe.level) {
            detail = "Herblore level " + recipe.level + " required";
            return false;
        }
        detail = recipe.potion + " x " + config.getContinuousQuantity();
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        requirements.add("Coins above reserve");
        requirements.add("Grimy " + recipe.herb.name().toLowerCase());
        requirements.add("Vials of water");
        requirements.add("Secondary ingredient " + recipe.secondaryId);
        return requirements;
    }

    /** The outer bank-stander state machine should hand control to this orchestrator. */
    @Override public boolean hasRequiredItems() { return true; }
    @Override public boolean performBanking() { return true; }

    @Override
    public boolean process() {
        long now = System.currentTimeMillis();
        if (saleWaitStage == SaleWaitStage.LOGOUT_BREAK && Microbot.isLoggedIn()) {
            if (now < saleWaitUntil) {
                detail = "Sale offer resting offline; logging back out";
                Rs2Player.logout();
                return true;
            }
            saleWaitStage = SaleWaitStage.POST_LOGIN_CHECK;
        }
        if (saleWaitStage == SaleWaitStage.POST_LOGIN_CHECK) {
            return checkSaleAfterLogout(now);
        }
        if (!isSalePhase(controller.getPhase()) && controller.isPhaseTimedOut(now)) {
            exchange.abortAndCollect();
            if (workerPhase == controller.getPhase()) {
                phaseWorker = null;
                workerPhase = null;
            }
            controller.failPhase("phase timeout", now);
            log.info("Restarting continuous phase {} (retry {}/{})",
                    controller.getPhase(), controller.getPhaseRetries(), config.getContinuousRetryLimit());
            return true;
        }

        switch (controller.getPhase()) {
            case PRECHECK: return precheck(now);
            case ACQUIRE_INPUTS: return acquire(now);
            case CLEAN_HERBS:
            case MAKE_UNFINISHED:
            case MAKE_FINISHED: return processPhase(now);
            case INTERIM_DECANT: return decantInterim(now);
            case INTERIM_SELL: return sell(now);
            case OPTIONAL_DECANT: return decant(now);
            case OPTIONAL_SELL: return sell(now);
            case RECONCILE: return reconcile(now);
            case STOPPED: return true;
            default: return false;
        }
    }

    private boolean precheck(long now) {
        detail = "Precheck and baseline";
        if (!ensureBankOpen()) return true;
        boolean applyStartOverride = config.isContinuousStartOverride()
                && controller.getCompletedCycles() == 0;
        ContinuousHerblorePhase startPhase = applyStartOverride
                ? config.getContinuousStartPhase().getPhase()
                : ContinuousHerblorePhase.ACQUIRE_INPUTS;
        int existingFinished = bankFinishedContainers();
        expectedOutputContainers = config.getContinuousQuantity();
        if (startPhase == ContinuousHerblorePhase.OPTIONAL_DECANT
                || startPhase == ContinuousHerblorePhase.OPTIONAL_SELL) {
            if (existingFinished < expectedOutputContainers) {
                controller.stop("selected start phase requires " + expectedOutputContainers
                        + " finished potions, but bank has " + existingFinished);
                return true;
            }
            baselineFinishedContainers = existingFinished - expectedOutputContainers;
        } else {
            baselineFinishedContainers = existingFinished;
        }
        nextSaleCheckpoint = saleCheckpointStep();
        lastInterimCheckpoint = 0;
        if (config.isUseAmuletOfChemistry() && !recipe.chemistryEligible) {
            controller.stop("selected recipe is not chemistry eligible");
            return true;
        }
        log.info("Continuous start: override={}, phase={}, quantity={}, existingFinished={}, baseline={}",
                applyStartOverride, startPhase, expectedOutputContainers,
                existingFinished, baselineFinishedContainers);
        controller.beginAfterPrecheck(startPhase, now);
        return true;
    }

    private boolean acquire(long now) {
        detail = "Acquiring bounded inputs";
        if (exchange.getActiveSlot() != null) {
            if (!ensureExchangeOpen()) return true;
            exchange.reconcileAndCollect();
            return true;
        }
        if (!ensureBankOpen()) return true;

        Purchase need = nextPurchase();
        if (need == null) {
            exchange.resetCycleQuantity();
            controller.succeedPhase(now);
            return true;
        }

        long bankCoins = Rs2Bank.count(ItemID.COINS);
        long inventoryCoins = Rs2Inventory.itemQuantity(ItemID.COINS);
        long available = bankCoins + inventoryCoins;
        int guide = Math.max(1, Rs2GrandExchange.getPrice(need.itemId));
        int unitPrice = Math.min(config.getContinuousMaxBuyPrice(),
                Math.max(1, (int) Math.min(Integer.MAX_VALUE, (guide * 105L + 99L) / 100L)));
        long budget = (long) unitPrice * need.quantity;
        if (!controller.mayBuy(unitPrice, need.quantity, available)) {
            controller.stop("purchase exceeds reserve, buy ceiling, or stop loss");
            return true;
        }
        int withdraw = (int) Math.min(Integer.MAX_VALUE, Math.max(0, budget - inventoryCoins));
        if (withdraw > 0) Rs2Bank.withdrawX(ItemID.COINS, withdraw);
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 2000) || !ensureExchangeOpen()) return true;
        if (!exchange.placeBuy(need.itemId, need.quantity, unitPrice,
                Rs2Inventory.itemQuantity(ItemID.COINS))) {
            controller.failPhase("GE buy dispatch failed", now);
        }
        return true;
    }

    private Purchase nextPurchase() {
        int target = config.getContinuousQuantity();
        int grimyMissing = Math.max(0, target - Rs2Bank.count(recipe.grimyHerbId));
        if (grimyMissing > 0) return new Purchase(recipe.grimyHerbId, grimyMissing);
        int vialMissing = Math.max(0, target - Rs2Bank.count(recipe.vialOfWaterId));
        if (vialMissing > 0) return new Purchase(recipe.vialOfWaterId, vialMissing);
        int secondaryNeeded = target * recipe.secondaryPerOperation;
        int secondaryMissing = Math.max(0, secondaryNeeded - Rs2Bank.count(recipe.secondaryId));
        return secondaryMissing > 0 ? new Purchase(recipe.secondaryId, secondaryMissing) : null;
    }

    private boolean processPhase(long now) {
        ContinuousHerblorePhase phase = controller.getPhase();
        if (phaseWorker == null || workerPhase != phase) {
            phaseWorker = createWorker(phase);
            workerPhase = phase;
            if (!phaseWorker.validate()) {
                controller.stop("phase worker validation failed in " + phase);
                return true;
            }
        }
        detail = phase + " " + phaseWorker.getProcessedCount() + "/" + config.getContinuousQuantity();
        if (phaseWorker.isActivelyProcessing()) return true;
        int processed = phaseWorker.getProcessedCount();
        if (phase == ContinuousHerblorePhase.MAKE_FINISHED
                && config.isContinuousIntervalSelling()
                && processed >= nextSaleCheckpoint
                && processed < config.getContinuousQuantity()) {
            if (!depositInventory()) return true;
            pendingInterimContainers = processed - lastInterimCheckpoint;
            lastInterimCheckpoint = processed;
            int step = saleCheckpointStep();
            while (nextSaleCheckpoint <= processed) nextSaleCheckpoint += step;
            clearSalePhase();
            log.info("Pausing finished-potion production for interim sale: produced={}, tranche={}, next={}",
                    processed, pendingInterimContainers, nextSaleCheckpoint);
            controller.beginInterimSale(config.isContinuousDecant(), now);
            return true;
        }
        if (phaseWorker.getProcessedCount() >= config.getContinuousQuantity()) {
            if (!depositInventory()) return true;
            int completed = phaseWorker.getProcessedCount();
            if (phase == ContinuousHerblorePhase.CLEAN_HERBS) herbsCleaned += completed;
            else if (phase == ContinuousHerblorePhase.MAKE_UNFINISHED) unfinishedPotionsMade += completed;
            else if (phase == ContinuousHerblorePhase.MAKE_FINISHED) finishedPotionsMade += completed;
            lastEquipmentStatus = phaseWorker.getEquipmentStatus();
            phaseWorker = null;
            workerPhase = null;
            controller.succeedPhase(now);
            return true;
        }
        if (phaseWorker.hasRequiredItems()) {
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            return phaseWorker.process();
        }
        if (!phaseWorker.canContinueProcessing()) {
            controller.failPhase("phase supply exhausted", now);
            return true;
        }
        if (!ensureBankOpen()) return true;
        if (!phaseWorker.performBanking()) controller.failPhase("phase banking failed", now);
        return true;
    }

    private HerbloreProcessor createWorker(ContinuousHerblorePhase phase) {
        Mode mode = phase == ContinuousHerblorePhase.CLEAN_HERBS ? Mode.CLEAN_HERBS
                : phase == ContinuousHerblorePhase.MAKE_UNFINISHED ? Mode.UNFINISHED_POTIONS
                : Mode.FINISHED_POTIONS;
        return new HerbloreProcessor(mode, cleanMode(recipe.herb), unfinishedMode(recipe.herb),
                recipe.potion, config.isUseAmuletOfChemistry(), config.getHerbCleaningMode(),
                config.getHerbloreTurboLimit(), config.getHerbloreSleepMin(),
                config.getHerbloreSleepMax(), config.getHerbloreSleepTarget(),
                config.getReverseIngredientChance(), config.getBatchMicroBreakChance(),
                config.getBatchMicroBreakMinMs(), config.getBatchMicroBreakMaxMs(),
                config.getContinuousQuantity());
    }

    private boolean decant(long now) {
        detail = "Decanting to four doses";
        int wanted = Math.max(1, config.getContinuousQuantity() - lastInterimCheckpoint);
        int inventoryContainers = inventoryPotionContainers();
        if (inventoryContainers != wanted) {
            if (inventoryContainers > 0 && !depositInventory()) return true;
            if (!withdrawFinishedAsNotes(wanted)) return true;
        }
        HerbloreDecantAdapter.Result result = decanter.decantToFourDoses(recipe.potion.toString());
        if (!result.success) {
            controller.failPhase(result.reason, now);
            return true;
        }
        expectedOutputContainers -= result.before.containers - result.after.containers;
        if (!depositInventory()) return true;
        controller.succeedPhase(now);
        return true;
    }

    private boolean decantInterim(long now) {
        detail = "Decanting interim sale tranche";
        int inventoryContainers = inventoryPotionContainers();
        if (inventoryContainers != pendingInterimContainers) {
            if (inventoryContainers > 0 && !depositInventory()) return true;
            if (!withdrawFinishedAsNotes(pendingInterimContainers)) return true;
        }
        HerbloreDecantAdapter.Result result = decanter.decantToFourDoses(recipe.potion.toString());
        if (!result.success) {
            controller.failPhase(result.reason, now);
            return true;
        }
        expectedOutputContainers -= result.before.containers - result.after.containers;
        pendingInterimContainers = result.after.containers;
        if (!depositInventory()) return true;
        controller.succeedPhase(now);
        return true;
    }

    private boolean sell(long now) {
        ContinuousHerblorePhase phase = controller.getPhase();
        prepareSalePhase(phase);
        detail = phase == ContinuousHerblorePhase.INTERIM_SELL
                ? "Selling interim production tranche" : "Selling reconciled output";
        if (exchange.getActiveSlot() != null) {
            return handleActiveSaleOffer(now);
        }
        int soldThisPhase = exchange.getTotalQuantityReconciled() - salePhaseBaselineReconciled;
        if (soldThisPhase >= salePhaseTarget) {
            controller.succeedPhase(now);
            clearSalePhase();
            return true;
        }

        int itemId = inventoryPotionId();
        if (itemId < 0) {
            if (!withdrawFinishedAsNotes(salePhaseTarget - soldThisPhase)) return true;
            itemId = inventoryPotionId();
            if (itemId < 0) {
                controller.failPhase("no reconciled potion output to sell", now);
                return true;
            }
        }
        int quantity = Rs2Inventory.itemQuantity(itemId);
        int guide = Math.max(1, Rs2GrandExchange.getPrice(itemId));
        int unitPrice = config.isContinuousUseFixedSellPrice()
                ? config.getContinuousFixedSellPrice()
                : Math.max(config.getContinuousMinSellPrice(), (guide * 95) / 100);
        if (!ensureExchangeOpen()) return true;
        if (!exchange.placeSell(itemId, quantity, unitPrice)) {
            controller.failPhase("GE sell dispatch failed", now);
        } else {
            beginShortSaleBreak(now);
        }
        return true;
    }

    private void prepareSalePhase(ContinuousHerblorePhase phase) {
        if (activeSalePhase == phase) return;
        activeSalePhase = phase;
        salePhaseBaselineReconciled = exchange.getTotalQuantityReconciled();
        salePhaseTarget = phase == ContinuousHerblorePhase.INTERIM_SELL
                ? pendingInterimContainers
                : Math.max(0, expectedOutputContainers - salePhaseBaselineReconciled);
        resetSaleWait();
        log.info("Prepared continuous sale phase {}: target={}, alreadyReconciled={}",
                phase, salePhaseTarget, salePhaseBaselineReconciled);
    }

    private boolean handleActiveSaleOffer(long now) {
        if (saleWaitStage == SaleWaitStage.NONE) beginShortSaleBreak(now);
        if (saleWaitStage == SaleWaitStage.SHORT_BREAK && now < saleWaitUntil) {
            detail = "Sale offer resting for " + remainingMinutes(now) + "m";
            return true;
        }
        if (saleWaitStage == SaleWaitStage.SHORT_BREAK) {
            if (!ensureExchangeOpen()) return true;
            if (exchange.isActiveOfferComplete()) {
                exchange.reconcileAndCollect();
                resetSaleWait();
                return true;
            }
            int progress = exchange.getActiveCompletedQuantity();
            if (progress < 0) {
                controller.stop("ambiguous GE sale progress before logout break");
                return true;
            }
            saleProgressAtLogout = progress;
            logoutReturnWorld = Microbot.getClient() == null ? 0 : Microbot.getClient().getWorld();
            Rs2GrandExchange.closeExchange();
            saleWaitStage = SaleWaitStage.LOGOUT_BREAK;
            saleWaitUntil = now + SALE_LOGOUT_BREAK_MILLIS;
            detail = "Sale still pending; logged out for 15 minutes";
            log.info("GE sale still pending after short break; quantitySold={}, logging out until {}",
                    saleProgressAtLogout, saleWaitUntil);
            Rs2Player.logout();
            return true;
        }
        return true;
    }

    private boolean checkSaleAfterLogout(long now) {
        detail = "Checking sale after 15-minute logout";
        if (!ensureExchangeOpen()) return true;
        if (exchange.isActiveOfferComplete()) {
            exchange.reconcileAndCollect();
            resetSaleWait();
            return true;
        }
        int progress = exchange.getActiveCompletedQuantity();
        if (progress < 0) {
            controller.stop("ambiguous GE sale progress after logout break");
            Rs2GrandExchange.closeExchange();
            Rs2Player.logout();
            return true;
        }
        if (progress > saleProgressAtLogout) {
            log.info("GE sale progressed during logout break: {} -> {}; continuing bounded waits",
                    saleProgressAtLogout, progress);
            beginShortSaleBreak(now);
            return true;
        }

        log.info("GE sale made no progress during logout break; aborting offer and stopping gracefully");
        if (!exchange.abortAndCollect()) return true;
        Rs2GrandExchange.closeExchange();
        controller.stop("sale made no progress during 15-minute logout break");
        detail = "Sale stalled; stopped and logged out";
        Rs2Player.logout();
        return true;
    }

    private void beginShortSaleBreak(long now) {
        long duration = ThreadLocalRandom.current().nextLong(
                SALE_SHORT_BREAK_MIN_MILLIS, SALE_SHORT_BREAK_MAX_MILLIS + 1);
        saleWaitStage = SaleWaitStage.SHORT_BREAK;
        saleWaitUntil = now + duration;
        log.info("All current sale stock is offered; taking a {}ms sale break", duration);
    }

    private long remainingMinutes(long now) {
        return Math.max(1L, (saleWaitUntil - now + 59_999L) / 60_000L);
    }

    private void resetSaleWait() {
        saleWaitStage = SaleWaitStage.NONE;
        saleWaitUntil = 0;
        saleProgressAtLogout = 0;
        lastLoginAttemptAt = 0;
    }

    private void clearSalePhase() {
        activeSalePhase = null;
        salePhaseBaselineReconciled = 0;
        salePhaseTarget = 0;
        pendingInterimContainers = 0;
        resetSaleWait();
    }

    private boolean reconcile(long now) {
        detail = "Reconciling cycle accounting";
        if (!ensureBankOpen()) return true;
        int produced = bankFinishedContainers() - baselineFinishedContainers;
        if (exchange.getTotalQuantityReconciled() < expectedOutputContainers) {
            controller.stop("ambiguous sale quantity accounting");
            return true;
        }
        if (bankFinishedContainers() != baselineFinishedContainers) {
            controller.stop("cycle output was not fully liquidated");
            return true;
        }
        log.info("Continuous cycle reconciled: recipe={}, quantity={}, spent={}, revenue={}, net={}",
                recipe.potion, config.getContinuousQuantity(), controller.getSpent(),
                controller.getRevenue(), controller.getNetCost());
        exchange.resetCycleQuantity();
        controller.succeedPhase(now);
        return true;
    }

    private int saleCheckpointStep() {
        if (!config.isContinuousIntervalSelling()) return config.getContinuousQuantity();
        return Math.max(1, (int) Math.ceil(config.getContinuousQuantity()
                * (config.getContinuousSellIntervalPercent() / 100.0)));
    }

    private boolean isSalePhase(ContinuousHerblorePhase phase) {
        return phase == ContinuousHerblorePhase.INTERIM_SELL
                || phase == ContinuousHerblorePhase.OPTIONAL_SELL;
    }

    @Override
    public boolean shouldProcessWhileLoggedOut() {
        return saleWaitStage == SaleWaitStage.LOGOUT_BREAK;
    }

    @Override
    public boolean shouldStopWhileLoggedOut() {
        return controller.getPhase() == ContinuousHerblorePhase.STOPPED;
    }

    @Override
    public void processWhileLoggedOut() {
        long now = System.currentTimeMillis();
        if (saleWaitStage != SaleWaitStage.LOGOUT_BREAK) return;
        if (now < saleWaitUntil) {
            detail = "Offline sale break: " + remainingMinutes(now) + "m remaining";
            return;
        }
        if (now - lastLoginAttemptAt < LOGIN_RETRY_MILLIS) return;
        lastLoginAttemptAt = now;
        int world = logoutReturnWorld > 0 ? logoutReturnWorld : Login.getRandomWorld(true);
        detail = "Logging in to check pending sale";
        log.info("15-minute GE sale break complete; attempting login to world {}", world);
        new Login(world);
    }

    private boolean ensureBankOpen() {
        if (Rs2Bank.isOpen()) return true;
        if (!Rs2Bank.isNearBank(10)) {
            Rs2GrandExchange.walkToGrandExchange();
            return false;
        }
        Rs2Bank.openBank();
        return sleepUntil(Rs2Bank::isOpen, 3000);
    }

    private boolean ensureExchangeOpen() {
        if (Rs2GrandExchange.isOpen()) return true;
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        Rs2GrandExchange.walkToGrandExchange();
        if (!Rs2GrandExchange.openExchange()) return false;
        return sleepUntil(Rs2GrandExchange::isOpen, 3000);
    }

    private boolean depositInventory() {
        if (!ensureBankOpen()) return false;
        Rs2Bank.depositAll();
        boolean deposited = sleepUntil(Rs2Inventory::isEmpty, 3000);
        Rs2Bank.setWithdrawAsItem();
        return deposited;
    }

    private boolean withdrawFinishedAsNotes(int wanted) {
        if (!ensureBankOpen()) return false;
        Rs2Bank.depositAll();
        Rs2Bank.setWithdrawAsNote();
        int remaining = wanted;
        for (int i = recipe.finishedDoseIds.length - 1; i >= 0 && remaining > 0; i--) {
            int amount = Math.min(remaining, Rs2Bank.count(recipe.finishedDoseIds[i]));
            if (amount > 0) {
                Rs2Bank.withdrawX(recipe.finishedDoseIds[i], amount);
                remaining -= amount;
            }
        }
        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.closeBank();
        return sleepUntil(() -> inventoryPotionContainers() == wanted, 3000);
    }

    private int bankFinishedContainers() {
        int total = 0;
        for (int id : recipe.finishedDoseIds) total += Math.max(0, Rs2Bank.count(id));
        return total;
    }

    private boolean hasPotionInInventory() { return inventoryPotionId() >= 0; }

    private int inventoryPotionContainers() {
        int total = 0;
        for (int id : recipe.finishedDoseIds) total += Math.max(0, Rs2Inventory.itemQuantity(id));
        return total;
    }

    private int inventoryPotionId() {
        for (int i = recipe.finishedDoseIds.length - 1; i >= 0; i--) {
            if (Rs2Inventory.hasItem(recipe.finishedDoseIds[i])) return recipe.finishedDoseIds[i];
        }
        return -1;
    }

    private CleanHerbMode cleanMode(Herb herb) {
        return CleanHerbMode.valueOf(herb.name());
    }

    private UnfinishedPotionMode unfinishedMode(Herb herb) {
        String prefix = herb == Herb.DWARF ? "DWARF_WEED" : herb.name();
        return UnfinishedPotionMode.valueOf(prefix + "_POTION_UNF");
    }

    @Override public boolean canContinueProcessing() {
        return controller.getPhase() != ContinuousHerblorePhase.STOPPED;
    }
    @Override public boolean isActivelyProcessing() {
        return phaseWorker != null && phaseWorker.isActivelyProcessing();
    }
    @Override public String getStatusMessage() { return "Continuous: " + controller.getPhase(); }
    @Override public int getProcessedCount() {
        return controller.getCompletedCycles() * config.getContinuousQuantity()
                + (phaseWorker == null ? 0 : phaseWorker.getProcessedCount());
    }
    @Override public int getHerbsCleanedCount() {
        return herbsCleaned + activePhaseProgress(ContinuousHerblorePhase.CLEAN_HERBS);
    }
    @Override public int getUnfinishedPotionCount() {
        return unfinishedPotionsMade + activePhaseProgress(ContinuousHerblorePhase.MAKE_UNFINISHED);
    }
    @Override public int getFinishedPotionCount() {
        return finishedPotionsMade + activePhaseProgress(ContinuousHerblorePhase.MAKE_FINISHED);
    }
    @Override public int getPotionsSoldCount() { return exchange.getLifetimeSoldQuantity(); }
    @Override public int getCompletedCycleCount() { return controller.getCompletedCycles(); }
    @Override public long getCoinsSpent() { return controller.getSpent(); }
    @Override public long getCoinsRevenue() { return controller.getRevenue(); }

    private int activePhaseProgress(ContinuousHerblorePhase phase) {
        return workerPhase == phase && phaseWorker != null ? phaseWorker.getProcessedCount() : 0;
    }
    @Override public String getBatchProgress() {
        return phaseWorker == null ? controller.getPhase().toString() : phaseWorker.getBatchProgress();
    }
    @Override public String getEquipmentStatus() {
        return phaseWorker == null ? (config.isUseAmuletOfChemistry() ? lastEquipmentStatus : "Disabled")
                : phaseWorker.getEquipmentStatus();
    }
    @Override public String getTaskDetail() {
        if (controller.getPhase() == ContinuousHerblorePhase.STOPPED) {
            return "Stopped: " + controller.getStopReason();
        }
        return detail + " | cycle " + (controller.getCompletedCycles() + 1)
                + (config.isContinuousUnlimitedCycles() ? "/unlimited" : "/" + config.getContinuousCycleLimit())
                + " | gp " + controller.getSpent() + "/" + controller.getRevenue();
    }
    @Override public int getBankProcessableCount() {
        if (phaseWorker != null) return phaseWorker.getBankProcessableCount();
        return recipe == null ? -1 : Math.min(Rs2Bank.count(recipe.grimyHerbId),
                Math.min(Rs2Bank.count(recipe.vialOfWaterId),
                        Rs2Bank.count(recipe.secondaryId) / recipe.secondaryPerOperation));
    }
    @Override public String getBankMaterialSummary() {
        if (phaseWorker != null) return phaseWorker.getBankMaterialSummary();
        return recipe == null ? "Awaiting recipe" : "grimy=" + Rs2Bank.count(recipe.grimyHerbId)
                + ", vials=" + Rs2Bank.count(recipe.vialOfWaterId)
                + ", secondary=" + Rs2Bank.count(recipe.secondaryId);
    }
    @Override public void refreshDiagnostics() {
        if (phaseWorker != null) {
            phaseWorker.refreshDiagnostics();
            lastEquipmentStatus = phaseWorker.getEquipmentStatus();
        }
    }
    @Override public void onGameMessage(String message) { if (phaseWorker != null) phaseWorker.onGameMessage(message); }

    private enum SaleWaitStage {
        NONE,
        SHORT_BREAK,
        LOGOUT_BREAK,
        POST_LOGIN_CHECK
    }

    private static final class Purchase {
        private final int itemId;
        private final int quantity;
        private Purchase(int itemId, int quantity) { this.itemId = itemId; this.quantity = quantity; }
    }
}
