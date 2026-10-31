package net.runelite.client.plugins.microbot.tutorialisland;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

class TutorialIslandCookingTest {
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private Rs2TileObjectCache objects;
    private TutorialIslandScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        microbot.when(() -> Microbot.getVarbitPlayerValue(281)).thenReturn(150);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(mock(Rs2NpcCache.class, RETURNS_DEEP_STUBS));
        objects = mock(Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(objects);
        inventory.when(() -> Rs2Inventory.contains("Bread dough")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.contains("Bread")).thenReturn(true);
        script = new TutorialIslandScript(mock(TutorialIslandPlugin.class));
    }

    @AfterEach
    void tearDown() {
        inventory.close();
        microbot.close();
    }

    @Test
    void cooksOnlyAfterSelectionIsObserved() {
        inventory.when(() -> Rs2Inventory.interact("Bread dough")).thenReturn(true);
        inventory.when(Rs2Inventory::isItemSelected).thenReturn(true);
        script.CookingGuide();
        verify(objects.query()).interact(9736, "Cook");
    }

    @Test
    void rejectedDoughInteractionDoesNotCook() {
        inventory.when(() -> Rs2Inventory.interact("Bread dough")).thenReturn(false);
        script.CookingGuide();
        verifyNoInteractions(objects);
    }

    @Test
    void unobservedSelectionDoesNotCook() {
        inventory.when(() -> Rs2Inventory.interact("Bread dough")).thenReturn(true);
        inventory.when(Rs2Inventory::isItemSelected).thenReturn(false);
        script.CookingGuide();
        verifyNoInteractions(objects);
    }
}
