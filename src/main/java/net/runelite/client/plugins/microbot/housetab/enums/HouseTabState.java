package net.runelite.client.plugins.microbot.housetab.enums;

public enum HouseTabState {
    STARTING("Starting"),
    VALIDATE_LOGIN("Validate login"),
    VALIDATE_WORLD("Validate world"),
    SNAPSHOT_STATE("Snapshot state"),
    SELECT_TABLET("Select tablet"),
    CHECK_LOADOUT("Check loadout"),
    GO_GE("Go GE"),
    BANK_SETUP("Bank setup"),
    RETURN_RIMMINGTON("Return Rimmington"),
    UNNOTE_CLAY("Unnote clay"),
    OPEN_ADVERTISEMENT_BOARD("Open advertisement board"),
    SELECT_ADVERTISED_HOUSE("Select advertised house"),
    ENTER_HOUSE("Enter house"),
    WAIT_FOR_HOUSE_SCENE("Wait for house scene"),
    FIND_LECTERN("Find lectern"),
    OPEN_LECTERN("Open lectern"),
    SELECT_TABLET_WIDGET("Select tablet widget"),
    CRAFT_TABLETS("Craft tablets"),
    LEAVE_HOUSE("Leave house"),
    RECOVER_BAD_HOUSE("Recover bad house"),
    STOPPED("Stopped");

    private final String label;

    HouseTabState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
