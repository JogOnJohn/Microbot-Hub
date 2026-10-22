package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import java.util.function.Supplier;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmzScriptTest {

    @Test
    void missingLocalPlayerIsSafeDuringSceneChanges() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertFalse(mock(NmzScript.class, CALLS_REAL_METHODS).isOutside());
            verify(client).getLocalPlayer();
        }
    }

    @Test
    void lobbyReadUsesOneCapturedPlayer() {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(client.getLocalPlayer()).thenReturn(player, null);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2602, 3116, 0));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertTrue(mock(NmzScript.class, CALLS_REAL_METHODS).isOutside());
            verify(client).getLocalPlayer();
        }
    }

    @Test
    void unknownPlayerLocationIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(null));
    }

    @Test
    void locationNearLobbyIsOutside() {
        assertTrue(NmzScript.isNearLobby(new WorldPoint(2609, 3114, 0)));
    }

    @Test
    void locationInsideDreamIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(new WorldPoint(2273, 4694, 0)));
    }

    @Test
    void unknownPlayerLocationIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), null));
    }

    @Test
    void playerFarFromCenterIsAwayFromCenter() {
        assertTrue(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2290, 4694, 0)));
    }

    @Test
    void playerNearCenterIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2275, 4694, 0)));
    }
}
