package net.runelite.client.plugins.microbot.autobankstander.skills.herblore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processing.BatchTransaction;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerbCleaningMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class HerbloreProcessor implements BankStandingProcessor {
    
    private Mode mode;
    private CleanHerbMode cleanHerbMode;
    private UnfinishedPotionMode unfinishedPotionMode;
    private HerblorePotion finishedPotion;
    private boolean useAmuletOfChemistry;

    // cleaning tuning (from recovered HerbloreScript)
    private final HerbCleaningMode herbCleaningMode;
    private final int turboHerbLimit;
    private final int sleepMin;
    private final int sleepMax;
    private final int sleepTarget;
    private boolean turboActive;
    private int turboHerbsCleanedCount = 0;
    private final Random sleepRandom = new Random();
    private final int reverseIngredientChance;
    private final int batchMicroBreakChance;
    private final int batchMicroBreakMinMs;
    private final int batchMicroBreakMaxMs;

    private static final int[] RECORDED_SERPENTINE_SLOTS = {
        0, 1, 5, 4, 8, 9, 13, 12, 16, 17, 21, 20, 24, 25,
        26, 27, 23, 22, 18, 19, 15, 14, 10, 11, 7, 6, 2, 3
    };

    // processing state
    private Herb currentHerb;
    private Herb currentHerbForUnfinished;
    private HerblorePotion currentPotion;
    private int withdrawnAmount;
    private boolean amuletBroken = false;
    private int lastAmuletItemId = -1;
    private volatile int amuletCharges = -1;
    private volatile int chemistryProcCount = 0;
    private volatile String amuletStatus = "Disabled";

    // overlay/debug diagnostics
    private volatile int bankProcessableCount = -1;
    private volatile String bankMaterialSummary = "Awaiting bank scan";
    private volatile int processedCount = 0;
    private final int operationLimit;
    private volatile int batchSize = 0;
    private volatile int batchProcessed = 0;
    private int batchPrimaryItemId = -1;
    private int batchAccounted = 0;
    private String lastLoggedBankSummary = "";
    private static final int BATCH_ACK_TIMEOUT_TICKS = 5;
    private static final int BATCH_PROGRESS_TIMEOUT_TICKS = 12;
    private static final int MAX_BATCH_RETRIES = 2;
    private long batchGeneration = 0;
    private BatchTransaction batchTransaction;
    private int batchRetryCount = 0;
    private int batchSecondaryItemId = -1;
    private int batchSecondaryRatio = 1;

    private static final Pattern CHEMISTRY_CHECK_PATTERN = Pattern.compile(
            "^Your amulet of chemistry has (\\d+) charges? left\\.$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEMISTRY_USED_PATTERN = Pattern.compile(
            "^Your amulet of chemistry helps you create a \\d+-dose potion\\. It has (\\d+|one) charges? left\\.$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEMISTRY_BREAK_PATTERN = Pattern.compile(
            "^Your amulet of chemistry helps you create a \\d+-dose potion\\. It then crumbles to dust\\.$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALCHEMIST_CHECK_PATTERN = Pattern.compile(
            "^Your Alchemist's amulet has (\\d+) charges? left\\.$", Pattern.CASE_INSENSITIVE);

    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode,
                           HerblorePotion finishedPotion, boolean useAmuletOfChemistry) {
        this(mode, cleanHerbMode, unfinishedPotionMode, finishedPotion, useAmuletOfChemistry,
                HerbCleaningMode.DEFAULT, 0, 60, 300, 150, 15, 8, 700, 1800);
    }

    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode,
                           HerblorePotion finishedPotion, boolean useAmuletOfChemistry,
                           HerbCleaningMode herbCleaningMode, int turboHerbLimit,
                           int sleepMin, int sleepMax, int sleepTarget,
                           int reverseIngredientChance, int batchMicroBreakChance,
                           int batchMicroBreakMinMs, int batchMicroBreakMaxMs) {
        this.mode = mode;
        this.cleanHerbMode = cleanHerbMode;
        this.unfinishedPotionMode = unfinishedPotionMode;
        this.finishedPotion = finishedPotion;
        this.useAmuletOfChemistry = useAmuletOfChemistry;
        this.herbCleaningMode = herbCleaningMode == null ? HerbCleaningMode.DEFAULT : herbCleaningMode;
        this.turboHerbLimit = turboHerbLimit;
        this.sleepMin = sleepMin;
        this.sleepMax = sleepMax;
        this.sleepTarget = sleepTarget;
        this.reverseIngredientChance = clampPercent(reverseIngredientChance);
        this.batchMicroBreakChance = clampPercent(batchMicroBreakChance);
        this.batchMicroBreakMinMs = Math.max(250, Math.min(batchMicroBreakMinMs, batchMicroBreakMaxMs));
        this.batchMicroBreakMaxMs = Math.max(this.batchMicroBreakMinMs, batchMicroBreakMaxMs);
        this.operationLimit = 0;
        this.turboActive = this.herbCleaningMode == HerbCleaningMode.TURBO;
        this.withdrawnAmount = 0;
    }

    /** Creates a phase worker that cannot consume more than the requested number of operations. */
    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode,
                            HerblorePotion finishedPotion, boolean useAmuletOfChemistry,
                            HerbCleaningMode herbCleaningMode, int turboHerbLimit,
                            int sleepMin, int sleepMax, int sleepTarget,
                            int reverseIngredientChance, int batchMicroBreakChance,
                            int batchMicroBreakMinMs, int batchMicroBreakMaxMs,
                            int operationLimit) {
        this.mode = mode;
        this.cleanHerbMode = cleanHerbMode;
        this.unfinishedPotionMode = unfinishedPotionMode;
        this.finishedPotion = finishedPotion;
        this.useAmuletOfChemistry = useAmuletOfChemistry;
        this.herbCleaningMode = herbCleaningMode == null ? HerbCleaningMode.DEFAULT : herbCleaningMode;
        this.turboHerbLimit = Math.max(0, turboHerbLimit);
        this.sleepMin = Math.max(1, sleepMin);
        this.sleepMax = Math.max(this.sleepMin, sleepMax);
        this.sleepTarget = Math.max(this.sleepMin, Math.min(this.sleepMax, sleepTarget));
        this.reverseIngredientChance = clampPercent(reverseIngredientChance);
        this.batchMicroBreakChance = clampPercent(batchMicroBreakChance);
        this.batchMicroBreakMinMs = Math.max(0, batchMicroBreakMinMs);
        this.batchMicroBreakMaxMs = Math.max(this.batchMicroBreakMinMs, batchMicroBreakMaxMs);
        this.operationLimit = Math.max(0, operationLimit);
        this.turboActive = this.herbCleaningMode == HerbCleaningMode.TURBO;
        this.withdrawnAmount = 0;
    }
    
    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        log.info("Herblore level: {}", level);
        log.info("Selected mode: {}", mode);
        
        if (mode == Mode.FINISHED_POTIONS && finishedPotion != null) {
            if (level < finishedPotion.level) {
                log.info("Insufficient herblore level for {}: need {}, have {}", 
                    finishedPotion.name(), finishedPotion.level, level);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        
        switch (mode) {
            case CLEAN_HERBS:
                requirements.add("Grimy herbs");
                break;
            case UNFINISHED_POTIONS:
                requirements.add("Clean herbs");
                requirements.add("Vials of water");
                break;
            case FINISHED_POTIONS:
                if (finishedPotion != null) {
                    if (isSuperCombat(finishedPotion)) {
                        requirements.add("Torstol");
                        requirements.add("Super attack potions");
                        requirements.add("Super strength potions");
                        requirements.add("Super defence potions");
                    } else {
                        requirements.add("Unfinished " + finishedPotion.name() + " potions");
                        requirements.add("Secondary ingredient");
                    }
                }
                if (useAmuletOfChemistry) {
                    requirements.add("Amulet of chemistry");
                }
                break;
        }
        
        return requirements;
    }
    
    @Override
    public boolean hasRequiredItems() {
        switch (mode) {
            case CLEAN_HERBS:
                return Rs2Inventory.hasItem("grimy");
            case UNFINISHED_POTIONS:
                return currentHerbForUnfinished != null && 
                       Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
                       Rs2Inventory.hasItem(ItemID.VIAL_WATER);
            case FINISHED_POTIONS:
                if (currentPotion == null) return false;
                if (useAmuletOfChemistry && !isSupportedAmuletWorn()) return false;
                if (isSuperCombat(currentPotion)) {
                    return Rs2Inventory.hasItem(ItemID.TORSTOL) && 
                           Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK) &&
                           Rs2Inventory.hasItem(ItemID._4DOSE2STRENGTH) && 
                           Rs2Inventory.hasItem(ItemID._4DOSE2DEFENSE);
                } else {
                    return Rs2Inventory.hasItem(currentPotion.unfinished) && 
                           Rs2Inventory.hasItem(currentPotion.secondary);
                }
        }
        return false;
    }
    
    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }
        
        log.info("Depositing all items");
        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 3000);
        
        switch (mode) {
            case CLEAN_HERBS:
                return bankForCleanHerbs();
            case UNFINISHED_POTIONS:
                return bankForUnfinishedPotions();
            case FINISHED_POTIONS:
                return bankForFinishedPotions();
        }
        
        return false;
    }
    
    @Override
    public boolean process() {
        switch (mode) {
            case CLEAN_HERBS:
                return processCleanHerbs();
            case UNFINISHED_POTIONS:
                return processUnfinishedPotions();
            case FINISHED_POTIONS:
                return processFinishedPotions();
        }
        
        return false;
    }

    @Override
    public boolean isActivelyProcessing() {
        if (batchTransaction == null) return false;
        BatchTransaction.State previous = batchTransaction.getState();
        BatchTransaction.State current = batchTransaction.observe(currentBatchObservation());
        refreshProcessingProgress();
        if (current != previous) {
            log.info("Batch generation {} transitioned {} -> {} (progress {}/{})",
                    batchTransaction.getGeneration(), previous, current,
                    batchTransaction.getCompletedOperations(), batchSize);
        }
        if (current == BatchTransaction.State.COMPLETED && previous != current) {
            maybeTakeBatchBoundaryBreak();
        }
        if (current == BatchTransaction.State.FAILED) {
            log.info("Batch generation {} failed: {} (retry {}/{})",
                    batchTransaction.getGeneration(), batchTransaction.getFailureReason(),
                    batchRetryCount, MAX_BATCH_RETRIES);
        }
        return batchTransaction.isInFlight();
    }

    @Override
    public void refreshDiagnostics() {
        refreshProcessingProgress();
        refreshBankMaterialDiagnostics();
        refreshAmuletStatus();
    }

    @Override
    public int getBankProcessableCount() {
        return bankProcessableCount;
    }

    @Override
    public String getBankMaterialSummary() {
        return bankMaterialSummary;
    }

    @Override
    public int getProcessedCount() {
        return processedCount;
    }

    @Override
    public String getBatchProgress() {
        if (batchSize <= 0) return "Awaiting batch";
        String transaction = batchTransaction == null ? "ready"
                : "g" + batchTransaction.getGeneration() + " " + batchTransaction.getState();
        return batchProcessed + " / " + batchSize + " | " + transaction;
    }

    @Override
    public String getEquipmentStatus() {
        return amuletStatus + " | procs " + chemistryProcCount;
    }

    @Override
    public String getTaskDetail() {
        switch (mode) {
            case CLEAN_HERBS:
                return (currentHerb == null ? cleanHerbMode.toString() : currentHerb.name())
                        + " | " + herbCleaningMode;
            case UNFINISHED_POTIONS:
                return currentHerbForUnfinished == null
                        ? unfinishedPotionMode.toString()
                        : currentHerbForUnfinished.name();
            case FINISHED_POTIONS:
                return finishedPotion == null ? "No potion selected" : finishedPotion.toString();
            default:
                return mode.toString();
        }
    }

    @Override
    public void onGameMessage(String message) {
        if (message == null || message.isEmpty()) return;

        Matcher check = CHEMISTRY_CHECK_PATTERN.matcher(message);
        Matcher used = CHEMISTRY_USED_PATTERN.matcher(message);
        Matcher alchemistCheck = ALCHEMIST_CHECK_PATTERN.matcher(message);

        if (check.matches()) {
            amuletCharges = Integer.parseInt(check.group(1));
            log.info("Amulet of chemistry charge check: {} remaining", amuletCharges);
        } else if (used.matches()) {
            amuletCharges = parseChargeCount(used.group(1));
            chemistryProcCount++;
            log.info("Amulet of chemistry proc: {} charges remaining ({} procs this run)",
                    amuletCharges, chemistryProcCount);
        } else if (CHEMISTRY_BREAK_PATTERN.matcher(message).matches()) {
            amuletCharges = 0;
            amuletBroken = true;
            chemistryProcCount++;
            log.info("Amulet of chemistry depleted after proc; replacement required ({} procs this run)",
                    chemistryProcCount);
        } else if (alchemistCheck.matches()) {
            amuletCharges = Integer.parseInt(alchemistCheck.group(1));
            log.info("Alchemist's amulet charge check: {} remaining", amuletCharges);
        } else if (message.toLowerCase().contains("alchemist's amulet")
                && message.toLowerCase().contains("helps you create")) {
            chemistryProcCount++;
            log.info("Alchemist's amulet proc observed ({} procs this run)", chemistryProcCount);
        }

        refreshAmuletStatus();
    }
    
    @Override
    public boolean canContinueProcessing() {
        if (operationLimit > 0 && processedCount >= operationLimit) return false;
        switch (mode) {
            case CLEAN_HERBS:
                // can continue if we have grimy herbs in inventory OR more in bank
                return Rs2Inventory.hasItem("grimy") || findHerb() != null;
            case UNFINISHED_POTIONS:
                // can continue if we have ingredients in inventory OR more in bank
                return (currentHerbForUnfinished != null && 
                        Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
                        Rs2Inventory.hasItem(ItemID.VIAL_WATER)) || 
                       findHerbForUnfinished() != null;
            case FINISHED_POTIONS:
                // can continue if we have ingredients in inventory OR more in bank
                return hasRequiredItems() || findPotion() != null;
        }
        return false;
    }
    
    @Override
    public String getStatusMessage() {
        switch (mode) {
            case CLEAN_HERBS:
                return "Cleaning herbs...";
            case UNFINISHED_POTIONS:
                return "Making unfinished potions...";
            case FINISHED_POTIONS:
                return "Making finished potions...";
        }
        
        return "Processing herblore...";
    }
    
    private boolean bankForCleanHerbs() {
        currentHerb = findHerb();
        if (currentHerb == null) {
            log.info("No more herbs available");
            return false;
        }
        
        int bankCount = Rs2Bank.count(currentHerb.grimy);
        log.info("Withdrawing up to 28 grimy {} ({} detected in bank)", currentHerb.name(), bankCount);
        int amount = Math.min(28, remainingOperationLimit());
        Rs2Bank.withdrawX(currentHerb.grimy, amount);
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(currentHerb.grimy), 3000);
        if (!withdrawn) {
            log.info("Failed to withdraw grimy herbs");
            return false;
        }

        initializeBatchTracking(currentHerb.grimy, Rs2Inventory.itemQuantity(currentHerb.grimy));
        return true;
    }
    
    private boolean bankForUnfinishedPotions() {
        currentHerbForUnfinished = findHerbForUnfinished();
        if (currentHerbForUnfinished == null) {
            log.info("No more herbs or vials available");
            return false;
        }
        
        int herbCount = Rs2Bank.count(currentHerbForUnfinished.clean);
        int vialCount = Rs2Bank.count(ItemID.VIAL_WATER);
        withdrawnAmount = Math.min(Math.min(Math.min(herbCount, vialCount), 14), remainingOperationLimit());
        
        log.info("Withdrawing {} clean herbs and vials", withdrawnAmount);
        Rs2Bank.withdrawX(currentHerbForUnfinished.clean, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID.VIAL_WATER, withdrawnAmount);
        
        boolean withdrawn = sleepUntil(() -> 
            Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
            Rs2Inventory.hasItem(ItemID.VIAL_WATER), 3000);
        
        if (!withdrawn) {
            log.info("Failed to withdraw unfinished ingredients");
            return false;
        }

        initializeBatchTracking(currentHerbForUnfinished.clean,
                Rs2Inventory.itemQuantity(currentHerbForUnfinished.clean));
        return true;
    }
    
    private boolean bankForFinishedPotions() {
        if (useAmuletOfChemistry && !isSupportedAmuletWorn()) {
            if (!checkAndEquipAmulet()) {
                log.info("Chemistry amulet is required but no charged supported amulet is available");
                return false;
            }
            amuletBroken = false;
        }
        
        currentPotion = findPotion();
        if (currentPotion == null) {
            log.info("No more ingredients for selected potion");
            return false;
        }
        
        if (isSuperCombat(currentPotion)) {
            return bankForSuperCombat();
        } else if (usesStackableSecondary(currentPotion)) {
            return bankForStackableSecondary();
        } else {
            return bankForRegularPotion();
        }
    }
    
    private boolean bankForSuperCombat() {
        int torstolCount = Rs2Bank.count(ItemID.TORSTOL);
        int superAttackCount = Rs2Bank.count(ItemID._4DOSE2ATTACK);
        int superStrengthCount = Rs2Bank.count(ItemID._4DOSE2STRENGTH);
        int superDefenceCount = Rs2Bank.count(ItemID._4DOSE2DEFENSE);
        
        withdrawnAmount = Math.min(Math.min(Math.min(Math.min(Math.min(torstolCount, superAttackCount),
                                                   superStrengthCount), superDefenceCount), 7), remainingOperationLimit());
        
        log.info("Withdrawing {} of each super combat ingredient", withdrawnAmount);
        Rs2Bank.withdrawX(ItemID.TORSTOL, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2ATTACK, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2STRENGTH, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2DEFENSE, withdrawnAmount);
        
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(ItemID.TORSTOL) &&
                               Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK), 3000);
        if (withdrawn) initializeBatchTracking(ItemID.TORSTOL, Rs2Inventory.itemQuantity(ItemID.TORSTOL));
        return withdrawn;
    }
    
    private boolean bankForStackableSecondary() {
        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished);
        int secondaryCount = Rs2Bank.count(currentPotion.secondary);
        
        int secondaryRatio = getStackableSecondaryRatio(currentPotion);
        withdrawnAmount = Math.min(Math.min(unfinishedCount, 27), remainingOperationLimit());
        int secondaryNeeded = withdrawnAmount * secondaryRatio;
        
        if (secondaryCount < secondaryNeeded) {
            withdrawnAmount = secondaryCount / secondaryRatio;
            secondaryNeeded = withdrawnAmount * secondaryRatio;
        }
        
        log.info("Withdrawing {} unfinished and {} secondary", withdrawnAmount, secondaryNeeded);
        Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.secondary, secondaryNeeded);
        
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(currentPotion.unfinished) &&
                               Rs2Inventory.hasItem(currentPotion.secondary), 3000);
        if (withdrawn) initializeBatchTracking(currentPotion.unfinished,
                Rs2Inventory.itemQuantity(currentPotion.unfinished));
        return withdrawn;
    }
    
    private boolean bankForRegularPotion() {
        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished);
        int secondaryCount = Rs2Bank.count(currentPotion.secondary);
        withdrawnAmount = Math.min(Math.min(Math.min(unfinishedCount, secondaryCount), 14), remainingOperationLimit());
        
        log.info("Withdrawing {} unfinished and secondary", withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.secondary, withdrawnAmount);
        
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(currentPotion.unfinished) &&
                               Rs2Inventory.hasItem(currentPotion.secondary), 3000);
        if (withdrawn) initializeBatchTracking(currentPotion.unfinished,
                Rs2Inventory.itemQuantity(currentPotion.unfinished));
        return withdrawn;
    }
    
    private boolean processCleanHerbs() {
        if (turboActive && turboHerbLimit > 0 && turboHerbsCleanedCount >= turboHerbLimit) {
            log.info("Turbo auto-disabled after {} herbs (limit {})", turboHerbsCleanedCount, turboHerbLimit);
            turboActive = false;
        }

        if (!Rs2Inventory.hasItem("grimy")) {
            log.info("No grimy herbs in inventory - returning to banking");
            return true;
        }

        if (turboActive) {
            cleanHerbsTurbo();
            return true;
        }

        switch (herbCleaningMode) {
            case RECORDED_SERPENTINE:
                cleanHerbsRecordedSerpentine();
                break;
            case RANDOM:
                cleanHerbsRandom();
                break;
            case TURBO:
                // A configured limit can disable turbo for the remainder of the run.
                cleanHerbsNormal();
                break;
            case DEFAULT:
            default:
                cleanHerbsNormal();
                break;
        }
        return true;
    }

    private void cleanHerbsNormal() {
        log.info("Cleaning herbs (normal, zigzag)");
        Rs2Inventory.cleanHerbs(InteractOrder.ZIGZAG);
        sleepUntil(() -> !Rs2Inventory.hasItem("grimy"), 5000);
        sleep(gaussianSleep());
    }

    private void cleanHerbsTurbo() {
        List<Rs2ItemModel> grimy = Rs2Inventory.items()
                .filter(item -> item.getName() != null && item.getName().toLowerCase().contains("grimy"))
                .collect(Collectors.toList());
        if (grimy.isEmpty()) return;

        log.info("Cleaning {} herbs (turbo)", grimy.size());
        List<Rs2ItemModel> ordered = Rs2Inventory.calculateInteractOrder(grimy, InteractOrder.ZIGZAG);
        for (Rs2ItemModel herb : ordered) {
            if (herb == null || herb.getName() == null) continue;
            if (!herb.getName().toLowerCase().contains("grimy")) continue;
            Rs2Inventory.interact(herb, "Clean");
            turboHerbsCleanedCount++;
            sleep(Rs2Random.between(5, 15));
        }
        Rs2Inventory.waitForInventoryChanges(3000);
        sleep(Rs2Random.between(50, 100));
    }

    private void cleanHerbsRecordedSerpentine() {
        log.info("Cleaning herbs (recorded serpentine, 180-240ms)");
        List<Integer> slots = new ArrayList<>(RECORDED_SERPENTINE_SLOTS.length);
        for (int slot : RECORDED_SERPENTINE_SLOTS) {
            slots.add(slot);
        }
        cleanHerbsInSlotOrder(slots);
        retryRemainingGrimyHerbs(slots);
    }

    private void cleanHerbsRandom() {
        log.info("Cleaning herbs (random, 180-240ms)");
        List<Integer> slots = currentGrimySlots();
        Collections.shuffle(slots, sleepRandom);
        cleanHerbsInSlotOrder(slots);

        List<Integer> retrySlots = currentGrimySlots();
        Collections.shuffle(retrySlots, sleepRandom);
        retryRemainingGrimyHerbs(retrySlots);
    }

    private void cleanHerbsInSlotOrder(List<Integer> slots) {
        for (int slot : slots) {
            Rs2ItemModel herb = Rs2Inventory.getItemInSlot(slot);
            if (!isGrimyHerb(herb)) {
                continue;
            }
            Rs2Inventory.interact(herb, "Clean");
            sleep(Rs2Random.between(180, 240));
        }
        Rs2Inventory.waitForInventoryChanges(1000);
    }

    private void retryRemainingGrimyHerbs(List<Integer> slots) {
        if (!Rs2Inventory.hasItem("grimy")) {
            return;
        }

        log.info("Retrying remaining grimy herb slots after paced cleaning pass");
        cleanHerbsInSlotOrder(slots);
        sleepUntil(() -> !Rs2Inventory.hasItem("grimy"), 3000);
    }

    private List<Integer> currentGrimySlots() {
        return Rs2Inventory.items()
                .filter(this::isGrimyHerb)
                .map(Rs2ItemModel::getSlot)
                .collect(Collectors.toList());
    }

    private boolean isGrimyHerb(Rs2ItemModel item) {
        return item != null && item.getName() != null
                && item.getName().toLowerCase().contains("grimy");
    }

    private int gaussianSleep() {
        double mean = (sleepMin + sleepMax + sleepTarget) / 3.0;
        double std = Math.abs(sleepTarget - mean) / 3.0;
        if (std <= 0) return sleepTarget;
        int duration;
        do {
            duration = (int) Math.round(mean + sleepRandom.nextGaussian() * std);
        } while (duration < sleepMin || duration > sleepMax);
        return duration;
    }
    
    private boolean processUnfinishedPotions() {
        if (!prepareBatchDispatch()) return batchTransaction != null && batchTransaction.isInFlight();
        if (Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && Rs2Inventory.hasItem(ItemID.VIAL_WATER)) {
            log.info("Combining {} with vial of water", currentHerbForUnfinished.name());
            
            if (combineWithVariation(currentHerbForUnfinished.clean, ItemID.VIAL_WATER)) {
                startBatchTransaction(currentHerbForUnfinished.clean, ItemID.VIAL_WATER, 1);
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue()
                            || Rs2Inventory.itemQuantity(currentHerbForUnfinished.clean) < batchSize, 1800);
                    batchTransaction.observe(currentBatchObservation());
                    if (Rs2Dialogue.hasCombinationDialogue()) {
                    Rs2Keyboard.keyPress('1');
                    }
                }
                log.info("Started making unfinished potions");
                return true;
            }
        }
        return false;
    }
    
    private boolean processFinishedPotions() {
        if (useAmuletOfChemistry && (amuletBroken || !isSupportedAmuletWorn())) {
            log.info("Chemistry amulet missing or depleted - need banking");
            return false;
        }
        
        if (isSuperCombat(currentPotion)) {
            return processSuperCombat();
        } else {
            return processRegularPotion();
        }
    }
    
    private boolean processSuperCombat() {
        if (!prepareBatchDispatch()) return batchTransaction != null && batchTransaction.isInFlight();
        if (Rs2Inventory.hasItem(ItemID.TORSTOL) && Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK)) {
            log.info("Combining torstol with super attack for super combat");
            
            if (combineWithVariation(ItemID.TORSTOL, ItemID._4DOSE2ATTACK)) {
                startBatchTransaction(ItemID.TORSTOL, ItemID._4DOSE2ATTACK, 1);
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue()
                            || Rs2Inventory.itemQuantity(ItemID.TORSTOL) < batchSize, 1800);
                    batchTransaction.observe(currentBatchObservation());
                    if (Rs2Dialogue.hasCombinationDialogue()) {
                    Rs2Keyboard.keyPress('1');
                    }
                }
                log.info("Started making super combat potions");
                return true;
            }
        }
        return false;
    }
    
    private boolean processRegularPotion() {
        if (!prepareBatchDispatch()) return batchTransaction != null && batchTransaction.isInFlight();
        if (Rs2Inventory.hasItem(currentPotion.unfinished) && Rs2Inventory.hasItem(currentPotion.secondary)) {
            log.info("Combining {} unfinished with secondary ingredient", currentPotion.name());
            
            if (combineWithVariation(currentPotion.unfinished, currentPotion.secondary)) {
                startBatchTransaction(currentPotion.unfinished, currentPotion.secondary,
                        getStackableSecondaryRatio(currentPotion));
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue()
                            || Rs2Inventory.itemQuantity(currentPotion.unfinished) < batchSize, 1800);
                    batchTransaction.observe(currentBatchObservation());
                    if (Rs2Dialogue.hasCombinationDialogue()) {
                    Rs2Keyboard.keyPress('1');
                    }
                }
                log.info("Started making {} potions", currentPotion.name());
                return true;
            }
        }
        return false;
    }
    
    // Helper methods from original script
    private boolean usesStackableSecondary(HerblorePotion potion) {
        return getStackableSecondaryRatio(potion) > 1;
    }
    
    private int getStackableSecondaryRatio(HerblorePotion potion) {
        if (potion.secondary == ItemID.PRIF_CRYSTAL_SHARD_CRUSHED) return 4;
        if (potion.secondary == ItemID.SNAKEBOSS_SCALE) return 20;
        if (potion.secondary == ItemID.LAVA_SHARD) return 4;
        if (potion.secondary == ItemID.AMYLASE) return 4;
        if (potion.secondary == ItemID.ARAXYTE_VENOM_SACK) return 1;
        return 1;
    }
    
    private boolean isSuperCombat(HerblorePotion potion) {
        return potion == HerblorePotion.SUPER_COMBAT;
    }
    
    private Herb findHerb() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (cleanHerbMode == CleanHerbMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.grimy)) {
                    log.info("Found herb: {} (level {})", h.name(), h.level);
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromMode(cleanHerbMode);
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.grimy)) {
                log.info("Found specific herb: {}", specificHerb.name());
                return specificHerb;
            }
        }
        return null;
    }
    
    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (unfinishedPotionMode == UnfinishedPotionMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                    log.info("Found herb for unfinished: {} (level {})", h.name(), h.level);
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromUnfinishedMode(unfinishedPotionMode);
            if (specificHerb != null && level >= specificHerb.level && 
                Rs2Bank.hasItem(specificHerb.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                log.info("Found specific herb for unfinished: {}", specificHerb.name());
                return specificHerb;
            }
        }
        return null;
    }
    
    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (finishedPotion != null && level >= finishedPotion.level) {
            if (isSuperCombat(finishedPotion)) {
                boolean hasAll = Rs2Bank.hasItem(ItemID.TORSTOL) && 
                               Rs2Bank.hasItem(ItemID._4DOSE2ATTACK) &&
                               Rs2Bank.hasItem(ItemID._4DOSE2STRENGTH) && 
                               Rs2Bank.hasItem(ItemID._4DOSE2DEFENSE);
                if (hasAll) {
                    log.info("All super combat ingredients available");
                    return finishedPotion;
                }
            } else {
                boolean hasIngredients = Rs2Bank.hasItem(finishedPotion.unfinished) && 
                                       Rs2Bank.hasItem(finishedPotion.secondary);
                if (hasIngredients) {
                    log.info("All regular potion ingredients available");
                    return finishedPotion;
                }
            }
        }
        return null;
    }
    
    private boolean checkAndEquipAmulet() {
        if (!useAmuletOfChemistry) return true;
        if (isSupportedAmuletWorn()) return true;

        log.info("No charged chemistry amulet equipped; checking bank");
        int targetItemId;
        String targetName;
        if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) {
            targetItemId = ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED;
            targetName = "Alchemist's amulet";
        } else if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY)) {
            targetItemId = ItemID.AMULET_OF_CHEMISTRY;
            targetName = "amulet of chemistry";
        } else {
            log.info("No charged chemistry amulet found in bank");
            return false;
        }

        int displacedAmuletId = getWornAmuletId();
        log.info("Withdrawing and equipping {}", targetName);
        Rs2Bank.withdrawAndEquip(targetItemId);
        boolean equipped = sleepUntil(() -> Rs2Equipment.isWearing(targetItemId), 3000);
        log.info("Chemistry amulet equip result: {}", equipped ? "equipped" : "timed out");
        if (equipped) {
            if (targetItemId != lastAmuletItemId) {
                amuletCharges = -1;
            }
            amuletBroken = false;
            lastAmuletItemId = targetItemId;
            if (displacedAmuletId != -1 && displacedAmuletId != targetItemId
                    && Rs2Inventory.hasItem(displacedAmuletId)) {
                log.info("Depositing displaced amulet item {}", displacedAmuletId);
                Rs2Bank.depositOne(displacedAmuletId);
            }
            refreshAmuletStatus();
        }
        return equipped;
    }

    private boolean isSupportedAmuletWorn() {
        return Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY)
                || Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED);
    }

    private void refreshAmuletStatus() {
        if (!useAmuletOfChemistry) {
            amuletStatus = "Disabled";
            return;
        }

        int wornId = getWornAmuletId();
        int previousWornId = lastAmuletItemId;
        if (lastAmuletItemId != -1 && wornId == -1 && !amuletBroken) {
            amuletBroken = true;
            log.info("Previously equipped chemistry amulet is no longer present; replacement required");
        }
        lastAmuletItemId = wornId;
        if (previousWornId != wornId) {
            log.info("Chemistry equipment changed: {} -> {}", previousWornId, wornId);
        }

        if (wornId == ItemID.AMULET_OF_CHEMISTRY) {
            loadTrackedRegularAmuletCharges();
            amuletStatus = amuletCharges >= 0
                    ? "Chemistry " + amuletCharges + "/5"
                    : "Chemistry equipped (charges unknown)";
        } else if (wornId == ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED) {
            amuletStatus = amuletCharges >= 0
                    ? "Alchemist " + amuletCharges + "/5000"
                    : "Alchemist charged (count unknown)";
        } else if (wornId == ItemID.AMULET_OF_CHEMISTRY_IMBUED_UNCHARGED) {
            amuletStatus = "Alchemist uncharged";
        } else if (amuletBroken) {
            amuletStatus = "Depleted - replacement pending";
        } else {
            amuletStatus = "Not equipped";
        }
    }

    private int getWornAmuletId() {
        Rs2ItemModel amulet = Rs2Equipment.get(EquipmentInventorySlot.AMULET);
        return amulet == null ? -1 : amulet.getId();
    }

    private int parseChargeCount(String value) {
        return "one".equalsIgnoreCase(value) ? 1 : Integer.parseInt(value);
    }

    private void loadTrackedRegularAmuletCharges() {
        if (amuletCharges >= 0) return;
        Integer tracked = Microbot.getConfigManager().getRSProfileConfiguration(
                "itemCharge", "amuletOfChemistry", Integer.class);
        if (tracked != null && tracked >= 0 && tracked <= 5) {
            amuletCharges = tracked;
            log.info("Loaded RuneLite-tracked amulet of chemistry charges: {}", tracked);
        }
    }

    private void initializeBatchTracking(int primaryItemId, int initialCount) {
        batchPrimaryItemId = primaryItemId;
        batchSize = Math.max(0, initialCount);
        batchProcessed = 0;
        batchAccounted = 0;
        batchTransaction = null;
        batchRetryCount = 0;
        batchSecondaryItemId = -1;
        batchSecondaryRatio = 1;
        refreshProcessingProgress();
    }

    private boolean prepareBatchDispatch() {
        if (batchTransaction == null) return true;
        batchTransaction.observe(currentBatchObservation());
        if (batchTransaction.isInFlight()) return false;
        if (batchTransaction.getState() == BatchTransaction.State.COMPLETED) return false;
        if (batchRetryCount >= MAX_BATCH_RETRIES) {
            log.info("Batch retry limit reached after generation {}", batchTransaction.getGeneration());
            return false;
        }
        batchRetryCount++;
        batchTransaction = null;
        return true;
    }

    private void startBatchTransaction(int primaryItemId, int secondaryItemId, int secondaryRatio) {
        batchPrimaryItemId = primaryItemId;
        batchSecondaryItemId = secondaryItemId;
        batchSecondaryRatio = Math.max(1, secondaryRatio);
        batchTransaction = new BatchTransaction(++batchGeneration, currentBatchObservation(),
                batchSecondaryRatio, BATCH_ACK_TIMEOUT_TICKS, BATCH_PROGRESS_TIMEOUT_TICKS);
        log.info("Dispatched batch generation {} with primary={}, secondary={}, size={}, retry={}",
                batchGeneration, primaryItemId, secondaryItemId, batchSize, batchRetryCount);
    }

    private BatchTransaction.Observation currentBatchObservation() {
        int tick = Microbot.getClient() == null ? 0 : Microbot.getClient().getTickCount();
        int primary = batchPrimaryItemId < 0 ? 0 : Rs2Inventory.itemQuantity(batchPrimaryItemId);
        int secondary = batchSecondaryItemId < 0
                ? Integer.MAX_VALUE : Rs2Inventory.itemQuantity(batchSecondaryItemId);
        return new BatchTransaction.Observation(tick, primary, secondary,
                Rs2Player.isAnimating(), Rs2Dialogue.hasCombinationDialogue());
    }

    private boolean combineWithVariation(int primaryItemId, int secondaryItemId) {
        boolean reverse = sleepRandom.nextInt(100) < reverseIngredientChance;
        log.info("Batch ingredient selection order: {} first",
                reverse ? "secondary" : "primary");
        return reverse
                ? Rs2Inventory.combine(secondaryItemId, primaryItemId)
                : Rs2Inventory.combine(primaryItemId, secondaryItemId);
    }

    private void maybeTakeBatchBoundaryBreak() {
        if (batchMicroBreakChance <= 0 || sleepRandom.nextInt(100) >= batchMicroBreakChance) return;
        int duration = Rs2Random.between(batchMicroBreakMinMs, batchMicroBreakMaxMs);
        log.info("Taking safe batch-boundary micro-break for {}ms", duration);
        sleep(duration);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int remainingOperationLimit() {
        return operationLimit <= 0 ? Integer.MAX_VALUE : Math.max(0, operationLimit - processedCount);
    }

    private void refreshProcessingProgress() {
        if (batchPrimaryItemId < 0 || batchSize <= 0) return;
        int remaining = Rs2Inventory.itemQuantity(batchPrimaryItemId);
        int completed = Math.max(0, Math.min(batchSize, batchSize - remaining));
        if (completed > batchAccounted) {
            processedCount += completed - batchAccounted;
            batchAccounted = completed;
        }
        batchProcessed = completed;
    }

    private void refreshBankMaterialDiagnostics() {
        String summary = "Awaiting selection";
        int processable = -1;

        if (mode == Mode.CLEAN_HERBS && currentHerb != null) {
            int grimy = Rs2Bank.count(currentHerb.grimy);
            processable = grimy;
            summary = currentHerb.name() + " grimy=" + grimy;
        } else if (mode == Mode.UNFINISHED_POTIONS && currentHerbForUnfinished != null) {
            int herbs = Rs2Bank.count(currentHerbForUnfinished.clean);
            int vials = Rs2Bank.count(ItemID.VIAL_WATER);
            processable = Math.min(herbs, vials);
            summary = currentHerbForUnfinished.name() + " clean=" + herbs + ", vials=" + vials;
        } else if (mode == Mode.FINISHED_POTIONS && finishedPotion != null) {
            if (isSuperCombat(finishedPotion)) {
                int torstol = Rs2Bank.count(ItemID.TORSTOL);
                int attack = Rs2Bank.count(ItemID._4DOSE2ATTACK);
                int strength = Rs2Bank.count(ItemID._4DOSE2STRENGTH);
                int defence = Rs2Bank.count(ItemID._4DOSE2DEFENSE);
                processable = Math.min(Math.min(torstol, attack), Math.min(strength, defence));
                summary = "torstol=" + torstol + ", atk=" + attack + ", str=" + strength + ", def=" + defence;
            } else {
                int primary = Rs2Bank.count(finishedPotion.unfinished);
                int secondary = Rs2Bank.count(finishedPotion.secondary);
                int ratio = getStackableSecondaryRatio(finishedPotion);
                processable = Math.min(primary, secondary / ratio);
                summary = finishedPotion.name() + ": primary=" + primary + ", secondary=" + secondary
                        + (ratio > 1 ? " (" + ratio + "/op)" : "");
            }
        }

        bankProcessableCount = processable;
        bankMaterialSummary = summary;
        String logged = summary + " | processable=" + processable;
        if (!logged.equals(lastLoggedBankSummary)) {
            log.info("Bank material diagnostics: {}", logged);
            lastLoggedBankSummary = logged;
        }
    }
    
    // mapping methods
    private Herb getHerbFromMode(CleanHerbMode mode) {
        switch (mode) {
            case GUAM: return Herb.GUAM;
            case MARRENTILL: return Herb.MARRENTILL;
            case TARROMIN: return Herb.TARROMIN;
            case HARRALANDER: return Herb.HARRALANDER;
            case RANARR: return Herb.RANARR;
            case TOADFLAX: return Herb.TOADFLAX;
            case IRIT: return Herb.IRIT;
            case AVANTOE: return Herb.AVANTOE;
            case KWUARM: return Herb.KWUARM;
            case SNAPDRAGON: return Herb.SNAPDRAGON;
            case CADANTINE: return Herb.CADANTINE;
            case LANTADYME: return Herb.LANTADYME;
            case DWARF: return Herb.DWARF;
            case TORSTOL: return Herb.TORSTOL;
            default: return null;
        }
    }
    
    private Herb getHerbFromUnfinishedMode(UnfinishedPotionMode mode) {
        switch (mode) {
            case GUAM_POTION_UNF: return Herb.GUAM;
            case MARRENTILL_POTION_UNF: return Herb.MARRENTILL;
            case TARROMIN_POTION_UNF: return Herb.TARROMIN;
            case HARRALANDER_POTION_UNF: return Herb.HARRALANDER;
            case RANARR_POTION_UNF: return Herb.RANARR;
            case TOADFLAX_POTION_UNF: return Herb.TOADFLAX;
            case IRIT_POTION_UNF: return Herb.IRIT;
            case AVANTOE_POTION_UNF: return Herb.AVANTOE;
            case KWUARM_POTION_UNF: return Herb.KWUARM;
            case SNAPDRAGON_POTION_UNF: return Herb.SNAPDRAGON;
            case CADANTINE_POTION_UNF: return Herb.CADANTINE;
            case LANTADYME_POTION_UNF: return Herb.LANTADYME;
            case DWARF_WEED_POTION_UNF: return Herb.DWARF;
            case TORSTOL_POTION_UNF: return Herb.TORSTOL;
            default: return null;
        }
    }
}
