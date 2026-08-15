package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

public final class ContinuousHerbloreControllerTest {
    public static void main(String[] args) {
        boundsCapitalAndPrice();
        advancesAndStopsAtCycleLimit();
        stopsAfterBoundedRetries();
        exposesTimeoutBeforeMutation();
        stopsAtActualSpendLossBound();
        routesCycleOutputThroughSale();
    }

    private static ContinuousHerblorePlan plan() {
        return new ContinuousHerblorePlan(100_000, 10_000, 7_000, 2,
                5_000, 500_000, 1, false, false, false);
    }

    private static void boundsCapitalAndPrice() {
        ContinuousHerbloreController c = new ContinuousHerbloreController(plan(), 0);
        c.succeedPhase(1);
        expect(c.mayBuy(9_000, 10, 200_000), "bounded purchase should be permitted");
        expect(!c.mayBuy(11_000, 1, 200_000), "max buy price must be enforced");
        expect(!c.mayBuy(9_000, 12, 200_000), "capital reserve must be enforced");
    }

    private static void advancesAndStopsAtCycleLimit() {
        ContinuousHerbloreController c = new ContinuousHerbloreController(plan(), 0);
        for (int i = 1; i <= 6; i++) c.succeedPhase(i);
        expect(c.getPhase() == ContinuousHerblorePhase.STOPPED, "bounded cycle should stop");
        expect(c.getCompletedCycles() == 1, "one cycle should reconcile");
    }

    private static void stopsAfterBoundedRetries() {
        ContinuousHerbloreController c = new ContinuousHerbloreController(plan(), 0);
        c.failPhase("x", 1);
        c.failPhase("x", 2);
        c.failPhase("x", 3);
        expect(c.getPhase() == ContinuousHerblorePhase.STOPPED, "retry exhaustion should stop");
    }

    private static void exposesTimeoutBeforeMutation() {
        ContinuousHerbloreController c = new ContinuousHerbloreController(plan(), 0);
        expect(!c.isPhaseTimedOut(4_999), "phase should remain within timeout");
        expect(c.isPhaseTimedOut(5_000), "adapter must be able to abort before timeout mutation");
        expect(c.getPhaseRetries() == 0, "timeout inspection must not mutate retry accounting");
    }

    private static void stopsAtActualSpendLossBound() {
        ContinuousHerbloreController c = new ContinuousHerbloreController(plan(), 0);
        c.succeedPhase(1);
        c.recordPurchase(500_000);
        expect(c.getPhase() == ContinuousHerblorePhase.STOPPED, "actual spend must enforce stop loss");
    }

    private static void routesCycleOutputThroughSale() {
        ContinuousHerblorePlan selling = new ContinuousHerblorePlan(100_000, 10_000, 7_000, 2,
                5_000, 500_000, 1, false, true, true);
        ContinuousHerbloreController c = new ContinuousHerbloreController(selling, 0);
        c.succeedPhase(1); // acquire
        c.succeedPhase(2); // clean
        c.succeedPhase(3); // unfinished
        c.succeedPhase(4); // finished
        c.succeedPhase(5); // enter decant
        c.succeedPhase(6); // decant complete
        expect(c.getPhase() == ContinuousHerblorePhase.OPTIONAL_SELL,
                "cycle output must enter sale before reconciliation");
        c.recordSale(123_456);
        c.succeedPhase(7);
        expect(c.getPhase() == ContinuousHerblorePhase.RECONCILE,
                "sale must reconcile before another cycle");
        expect(c.getRevenue() == 123_456, "actual sale proceeds must fund cycle accounting");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
