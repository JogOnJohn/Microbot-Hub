package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

/** Explicit operator-selected entry point for a continuous Herblore cycle. */
public enum ContinuousStartPhase {
    ACQUIRE_INPUTS("Buy inputs (full cycle)", ContinuousHerblorePhase.ACQUIRE_INPUTS),
    CLEAN_HERBS("Clean existing grimy herbs", ContinuousHerblorePhase.CLEAN_HERBS),
    MAKE_UNFINISHED("Use existing clean herbs", ContinuousHerblorePhase.MAKE_UNFINISHED),
    MAKE_FINISHED("Use existing unfinished potions", ContinuousHerblorePhase.MAKE_FINISHED),
    DECANT_OUTPUT("Decant existing finished stock", ContinuousHerblorePhase.OPTIONAL_DECANT),
    SELL_OUTPUT("Sell existing finished stock", ContinuousHerblorePhase.OPTIONAL_SELL);

    private final String displayName;
    private final ContinuousHerblorePhase phase;

    ContinuousStartPhase(String displayName, ContinuousHerblorePhase phase) {
        this.displayName = displayName;
        this.phase = phase;
    }

    public ContinuousHerblorePhase getPhase() {
        return phase;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
