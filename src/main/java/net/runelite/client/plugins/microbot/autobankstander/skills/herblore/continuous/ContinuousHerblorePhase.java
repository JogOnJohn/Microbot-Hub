package net.runelite.client.plugins.microbot.autobankstander.skills.herblore.continuous;

public enum ContinuousHerblorePhase {
    PRECHECK,
    ACQUIRE_INPUTS,
    CLEAN_HERBS,
    MAKE_UNFINISHED,
    MAKE_FINISHED,
    INTERIM_DECANT,
    INTERIM_SELL,
    OPTIONAL_DECANT,
    OPTIONAL_SELL,
    RECONCILE,
    STOPPED
}
