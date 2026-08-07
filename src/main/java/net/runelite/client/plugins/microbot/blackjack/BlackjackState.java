package net.runelite.client.plugins.microbot.blackjack;

public enum BlackjackState
{
    STARTING,
    VALIDATING,
    RETURNING_TO_HOUSE,
    FINDING_TARGET,
    KNOCKING_OUT,
    SELECTING_KNOCKOUT,
    PICKPOCKETING,
    HEALING,
    POSITIONING_COMBAT_RESET,
    ESCAPING_COMBAT,
    WAITING_FOR_COMBAT_CLEAR,
    RESTOCKING_WINE,
    STOPPED,
    ERROR
}
