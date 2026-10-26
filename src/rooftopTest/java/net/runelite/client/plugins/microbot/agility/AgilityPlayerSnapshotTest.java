package net.runelite.client.plugins.microbot.agility;

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgilityPlayerSnapshotTest {
    @Test void unloadedPlayerReturnsNullAndLoadedPlayerCanRecover() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        Player player = mock(Player.class);
        WorldPoint tile = new WorldPoint(2656, 3318, 3);
        boolean[] onClientThread = {false};
        when(thread.invoke(any(Supplier.class))).thenAnswer(inv -> {
            onClientThread[0] = true;
            try { return ((Supplier<?>) inv.getArgument(0)).get(); }
            finally { onClientThread[0] = false; }
        });
        when(client.getLocalPlayer()).thenAnswer(inv -> {
            assertTrue(onClientThread[0]);
            return null;
        });
        when(player.getWorldLocation()).thenAnswer(inv -> {
            assertTrue(onClientThread[0]);
            return tile;
        });
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertNull(AgilityScript.snapshotPlayerLocation());
            doAnswer(inv -> { assertTrue(onClientThread[0]); return player; }).when(client).getLocalPlayer();
            assertEquals(tile, AgilityScript.snapshotPlayerLocation());
            verify(client, times(2)).getLocalPlayer();
        }
    }

    @Test void missingClientThreadResultDoesNotPretendToHaveALocation() {
        ClientThread thread = mock(ClientThread.class);
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertNull(AgilityScript.snapshotPlayerLocation());
            microbot.verify(Microbot::getClient, never());
        }
    }
}
