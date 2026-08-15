package net.runelite.client.plugins.microbot.gildedaltar;

import net.runelite.api.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class GildedAltarScript extends Script {

    private static final int HOUSE_PORTAL_OBJECT = 4525;
    private static final int FAST_LOOP_DELAY_MS = 100;
    private static final int NORMAL_LOOP_DELAY_MS = 900;
    private Widget toggleArrow;
    public Widget targetWidget;
    public String houseOwner;

    public boolean visitedOnce;
    List<String> blacklistNames = new ArrayList<>();

    private final Random random = new Random();
    private int lastOfferTick = -1;
    private int lastUnnotedBoneCount = -1;
    private int lazyFastOffersRemaining;
    private int lazyBurstRemaining;
    private int lazyNextBurstAtBoneCount;
    private long lastNormalLoopAt;
    private boolean leaveHousePending;
    private long leaveHouseAttemptedAt;


    public static GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

    private boolean inHouse() {
        return Microbot.getRs2NpcCache().query().withName("Phials").nearestOnClientThread() == null;
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    private int unnotedBoneCount() {
        return Rs2Inventory.count(item -> !item.isNoted()
                && item.getName().toLowerCase().contains("bones"));
    }

    private void calculateState() {
        boolean inHouse = inHouse();
        boolean hasUnNotedBones = hasUnNotedBones();

        // If we have unNoted bones:
        // If we're in the house, use bones on altar. Else, enter the portal
        // If we don't have unNoted bones:
        // If we're in the house, leave house. Else, talk to Phials
        if (hasUnNotedBones) {
            state = inHouse ? GildedAltarPlayerState.BONES_ON_ALTAR : GildedAltarPlayerState.ENTER_HOUSE;
        } else {
            state = inHouse ? GildedAltarPlayerState.LEAVE_HOUSE : GildedAltarPlayerState.UNNOTE_BONES;
        }
    }


    public boolean run(GildedAltarConfig config) {
        blacklistNames = new ArrayList<>();
        resetOfferingPlan();
        leaveHousePending = false;
        leaveHouseAttemptedAt = 0;
        lastNormalLoopAt = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) {
                    return;
                }
                if (!Rs2Inventory.hasItem(995)) {
                    Microbot.showMessage("No gp found in your inventory");
                    shutdown();
                    return;
                }
                if (!hasNotedBones() && !hasUnNotedBones()) {
                    Microbot.showMessage("No bones found in your inventory");
                    shutdown();
                    return;
                }

                calculateState();

                boolean acceleratedOffering = config.oneTickOffering() || config.randomLazyOffering();
                if (Microbot.isGainingExp
                        && !(acceleratedOffering && state == GildedAltarPlayerState.BONES_ON_ALTAR)) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (state != GildedAltarPlayerState.BONES_ON_ALTAR
                        && now - lastNormalLoopAt < NORMAL_LOOP_DELAY_MS) {
                    return;
                }
                if (state != GildedAltarPlayerState.BONES_ON_ALTAR) {
                    lastNormalLoopAt = now;
                }

                switch (state) {
                    case LEAVE_HOUSE:
                        leaveHouse();
                        break;
                    case UNNOTE_BONES:
                        unnoteBones();
                        break;
                    case ENTER_HOUSE:
                        enterHouse();
                        break;
                    case BONES_ON_ALTAR:
                        bonesOnAltar(config);
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, FAST_LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    public void leaveHouse() {
        System.out.println("Attempting to leave house...");
        long now = System.currentTimeMillis();
        Rs2TileObjectModel portalObject = Microbot.getRs2TileObjectCache().query()
                .withId(HOUSE_PORTAL_OBJECT)
                .nearest();

        if (leaveHousePending) {
            if (portalObject == null) {
                leaveHousePending = false;
                leaveHouseAttemptedAt = 0;
                resetOfferingPlan();
                return;
            }
            if (now - leaveHouseAttemptedAt < 9000) {
                return;
            }
            System.out.println("House portal exit did not resolve; retrying.");
            leaveHousePending = false;
        }

        if (portalObject == null) {
            System.out.println("House portal not found; waiting for the house scene.");
            return;
        }

        boolean clicked = Microbot.getRs2TileObjectCache().query().interact(HOUSE_PORTAL_OBJECT, "Enter");
        if (!clicked) {
            clicked = portalObject.click("Enter");
        }
        if (!clicked) {
            System.out.println("Could not click the leave-house portal; will retry.");
            return;
        }

        leaveHousePending = true;
        leaveHouseAttemptedAt = System.currentTimeMillis();
        if (sleepUntil(() -> Microbot.getRs2TileObjectCache().query()
                .withId(HOUSE_PORTAL_OBJECT).nearest() == null, 8000)) {
            leaveHousePending = false;
            leaveHouseAttemptedAt = 0;
            resetOfferingPlan();
        }
    }

    public void unnoteBones() {
        if (Microbot.getClient().getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
            } else {
                Microbot.getClientThread().invoke(() -> Microbot.getRs2NpcCache().query().withName("Phials").interact("Use"));
                Rs2Player.waitForWalking();
            }
        } else if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
        }
    }

    private void enterHouse() {
        // If we've already visited a house this session, use 'Visit-Last' on advertisement board
        if (visitedOnce) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
            sleep(2400, 3000);
            return;
        }

        boolean isAdvertisementWidgetOpen = Rs2Widget.isWidgetVisible(3407875);

        if (!isAdvertisementWidgetOpen) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
            sleep(1200, 1800);
        }

        Widget containerNames = Rs2Widget.getWidget(52, 9);
        Widget containerEnter = Rs2Widget.getWidget(52, 19);
        if (containerNames == null || containerNames.getChildren() == null) return;

        //Sort house advertisements by Gilded Altar availability
        toggleArrow = Rs2Widget.getWidget(3407877);
        if (toggleArrow.getSpriteId() == 1050) {
            Rs2Widget.clickWidget(3407877);
            sleep(600, 1200);
        }

        // Get all names on house board and find the one with the smallest Y value
        if (containerNames.getChildren() != null) {
            int smallestOriginalY = Integer.MAX_VALUE; // Track the smallest OriginalY

            Widget[] children = containerNames.getChildren();

            for (int i = 0; i < children.length; i++) {
                Widget child = children[i];
                if (child.getText() == null || child.getText().isEmpty()|| child.getText() == ""){
                    continue;
                }
                if (child.getText() != null) {
                    if (child.getOriginalY() < smallestOriginalY && !blacklistNames.contains(child.getText())) {
                        houseOwner = child.getText();
                        smallestOriginalY = child.getOriginalY();
                    }
                }
            }

            // Use playername at top of advertisement board as search criteria and find their Enter button
            Widget[] children2 = containerEnter.getChildren();
            for (int i = 0; i < children2.length; i++) {
                Widget child = children2[i];
                if (child == null || child.getOnOpListener() == null) {
                    continue;
                }
                Object[] listenerArray = child.getOnOpListener();
                boolean containsHouseOwner = Arrays.stream(listenerArray)
                        .filter(Objects::nonNull) // Ensure no null elements
                        .anyMatch(obj -> obj.toString().replace("\u00A0", " ").contains(houseOwner)); // Check if houseOwner is part of any listener object
                if (containsHouseOwner) {
                    targetWidget = child;
                    break;
                }
            }
            sleep(600, 1200);
            Rs2Widget.clickChildWidget(3407891, targetWidget.getIndex());
            visitedOnce = true;
            sleep(2400, 3000);
        }
    }

    public void bonesOnAltar(GildedAltarConfig config) {
        Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query().withName("Altar").nearestOnClientThread();
        if (altar == null) {
            return;
        }

        int boneCount = unnotedBoneCount();
        if (boneCount <= 0) {
            resetOfferingPlan();
            return;
        }

        if (config.oneTickOffering()) {
            offerOncePerGameTick(altar);
            lastUnnotedBoneCount = boneCount;
            return;
        }

        if (config.randomLazyOffering()) {
            randomLazyOffer(config, altar, boneCount);
            return;
        }

        resetOfferingPlan();
        if (!Rs2Player.isAnimating()) {
            Rs2Inventory.useUnNotedItemOnObject("bones", altar.getId());
            Rs2Player.waitForAnimation();
        }
    }

    private void randomLazyOffer(GildedAltarConfig config, Rs2TileObjectModel altar, int boneCount) {
        if (lastUnnotedBoneCount <= 0 || boneCount > lastUnnotedBoneCount) {
            startLazyInventory(boneCount, config.randomLazySpeedBoost());
            // Start the normal automatic offering cycle. Planned bursts begin
            // after a short randomized gap and interrupt it only occasionally.
            if (!Rs2Player.isAnimating()) {
                offerOncePerGameTick(altar);
            }
        }

        if (lazyFastOffersRemaining > 0
                && lazyBurstRemaining == 0
                && boneCount <= lazyNextBurstAtBoneCount) {
            lazyBurstRemaining = Math.min(lazyFastOffersRemaining, 1 + random.nextInt(4));
        }

        if (lazyBurstRemaining > 0 && offerOncePerGameTick(altar)) {
            lazyBurstRemaining--;
            lazyFastOffersRemaining--;
            if (lazyBurstRemaining == 0 && lazyFastOffersRemaining > 0) {
                lazyNextBurstAtBoneCount = Math.max(1, boneCount - (2 + random.nextInt(5)));
            }
        }

        lastUnnotedBoneCount = boneCount;
    }

    private void startLazyInventory(int boneCount, int speedBoostPercent) {
        double boost = Math.max(1, Math.min(100, speedBoostPercent)) / 100.0;
        double manualOfferShare = (3.0 * boost) / (2.0 * (1.0 + boost));
        int targetOffers = (int) Math.round(boneCount * manualOfferShare);

        // Keep at least one normal bone between initialization and the first
        // burst, and leave some of the inventory to the automatic offering loop.
        lazyFastOffersRemaining = Math.max(1, Math.min(Math.max(1, boneCount - 2), targetOffers));
        lazyBurstRemaining = 0;
        lazyNextBurstAtBoneCount = Math.max(1, boneCount - (1 + random.nextInt(3)));
        lastUnnotedBoneCount = boneCount;
        lastOfferTick = -1;
    }

    private boolean offerOncePerGameTick(Rs2TileObjectModel altar) {
        int currentTick = Microbot.getClient().getTickCount();
        if (currentTick == lastOfferTick) {
            return false;
        }
        if (!Rs2Inventory.useUnNotedItemOnObject("bones", altar.getId())) {
            return false;
        }
        lastOfferTick = currentTick;
        return true;
    }

    private void resetOfferingPlan() {
        lastOfferTick = -1;
        lastUnnotedBoneCount = -1;
        lazyFastOffersRemaining = 0;
        lazyBurstRemaining = 0;
        lazyNextBurstAtBoneCount = 0;
    }

    public void addNameToBlackList() {
        blacklistNames.add(houseOwner);
    }
}
