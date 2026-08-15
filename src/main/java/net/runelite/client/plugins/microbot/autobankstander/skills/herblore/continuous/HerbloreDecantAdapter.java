package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Bob Barter adapter validated against the live group-582 decant interface. */
@Slf4j
public final class HerbloreDecantAdapter {
    private static final Pattern DOSE_SUFFIX = Pattern.compile("^(.*)\\((1|2|3|4)\\)$");

    public Result decantToFourDoses(String potionName) {
        Snapshot before = snapshot(potionName);
        if (before.containers <= 0 || before.doses <= 0) {
            return Result.failed("no matching potion doses in inventory", before, before);
        }
        if (before.otherPotionContainers > 0 || before.unnotedContainers > 0) {
            return Result.failed("decant inventory must contain only noted doses of the selected potion",
                    before, before);
        }

        if (!Rs2Widget.isWidgetVisible(InterfaceID.DECANT, 2)) {
            if (!Rs2Npc.interact(NpcID.GE_EXPERT_HERBS, "Decant")
                    || !sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.DECANT, 2), 3000)) {
                return Result.failed("decant interface did not open", before, snapshot(potionName));
            }
        }

        if (!Rs2Widget.clickWidget(InterfaceID.Decant.DECANT_4)
                || !sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.DECANT, 2), 3000)) {
            return Result.failed("four-dose selection did not resolve", before, snapshot(potionName));
        }

        Snapshot after = snapshot(potionName);
        int expectedContainers = (before.doses + 3) / 4;
        int expectedEmptyVials = before.containers - expectedContainers;
        boolean conserved = before.doses == after.doses
                && after.containers == expectedContainers
                && after.emptyVials - before.emptyVials == expectedEmptyVials;
        if (!conserved) {
            log.warn("Ambiguous decant accounting for {}: before={}, after={}", potionName, before, after);
            return Result.failed("dose or container conservation failed", before, after);
        }
        log.info("Decanted {}: {} doses in {} containers -> {} containers and {} empty vials",
                potionName, before.doses, before.containers, after.containers, expectedEmptyVials);
        return Result.success(before, after);
    }

    private Snapshot snapshot(String potionName) {
        String expectedBase = normalize(potionName);
        List<Rs2ItemModel> items = Rs2Inventory.items().collect(Collectors.toList());
        int doses = 0;
        int containers = 0;
        int emptyVials = 0;
        int otherPotionContainers = 0;
        int unnotedContainers = 0;
        for (Rs2ItemModel item : items) {
            if (item == null || item.getName() == null) continue;
            String name = item.getName();
            int quantity = Math.max(0, item.getQuantity());
            if ("vial".equalsIgnoreCase(name)) {
                emptyVials += quantity;
                continue;
            }
            Matcher matcher = DOSE_SUFFIX.matcher(name);
            if (!matcher.matches()) continue;
            if (!normalize(matcher.group(1)).equals(expectedBase)) {
                otherPotionContainers += quantity;
                continue;
            }
            int dose = Integer.parseInt(matcher.group(2));
            doses += dose * quantity;
            containers += quantity;
            if (!item.isNoted()) unnotedContainers += quantity;
        }
        return new Snapshot(doses, containers, emptyVials, otherPotionContainers, unnotedContainers);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(" potion", "");
    }

    public static final class Snapshot {
        public final int doses;
        public final int containers;
        public final int emptyVials;
        public final int otherPotionContainers;
        public final int unnotedContainers;

        Snapshot(int doses, int containers, int emptyVials,
                 int otherPotionContainers, int unnotedContainers) {
            this.doses = doses;
            this.containers = containers;
            this.emptyVials = emptyVials;
            this.otherPotionContainers = otherPotionContainers;
            this.unnotedContainers = unnotedContainers;
        }

        @Override public String toString() {
            return "doses=" + doses + ", containers=" + containers + ", emptyVials=" + emptyVials
                    + ", otherPotions=" + otherPotionContainers + ", unnoted=" + unnotedContainers;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String reason;
        public final Snapshot before;
        public final Snapshot after;

        private Result(boolean success, String reason, Snapshot before, Snapshot after) {
            this.success = success;
            this.reason = reason;
            this.before = before;
            this.after = after;
        }

        static Result success(Snapshot before, Snapshot after) {
            return new Result(true, "", before, after);
        }

        static Result failed(String reason, Snapshot before, Snapshot after) {
            return new Result(false, reason, before, after);
        }
    }
}
