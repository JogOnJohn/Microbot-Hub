package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesInteractionApiTest {
    @Test void transientEmptyLayoutIsNotAConfirmedRepairEffect() {
        assertFalse(MahoganyHomesScript.interactionWorkChanged(3, 0));
        assertFalse(MahoganyHomesScript.interactionWorkChanged(4, 4));
        assertTrue(MahoganyHomesScript.interactionWorkChanged(3, 4));
        assertTrue(MahoganyHomesScript.interactionWorkChanged(4, 7));
        assertTrue(MahoganyHomesScript.interactionWorkChanged(1, 2));
    }
    @Test void bothPilotSwitchesDefaultOffForPairedBuildRollout() {
        MahoganyHomesConfig config = new MahoganyHomesConfig() {};
        assertFalse(config.interactionAwareTurnIn());
        assertFalse(config.interactionAwareFurniture());
    }

    @Test void cleanupCancelsBothOwnedRequestsAndDropsThem() throws Exception {
        MahoganyHomesScript script = new MahoganyHomesScript();
        WorldPoint p = new WorldPoint(3242, 3452, 0);
        InteractionRequest npc = InteractionRequest.npc(10415, "Jeff", p, "Talk-to", () -> false);
        InteractionRequest furniture = InteractionRequest.object(39989, p, p, "Remove", () -> false);
        Field turnIn = MahoganyHomesScript.class.getDeclaredField("turnInInteraction");
        Field work = MahoganyHomesScript.class.getDeclaredField("furnitureInteraction");
        turnIn.setAccessible(true); work.setAccessible(true);
        turnIn.set(script, npc); work.set(script, furniture);
        Method cancel = MahoganyHomesScript.class.getDeclaredMethod("cancelInteractionRequests");
        cancel.setAccessible(true); cancel.invoke(script);
        assertFalse(npc.active()); assertFalse(furniture.active());
        assertNull(turnIn.get(script)); assertNull(work.get(script));
    }
}
