package compatibility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionResult;

/** Compiled against the pinned old runtime. Inspected as bytecode only; never execute. */
public final class InteractionConsumer {
    public static Object[] descriptors(WorldPoint goal) {
        InteractionRequest npc = InteractionRequest.npc(1, "fixture", goal, "Talk-to", () -> false);
        InteractionRequest object = InteractionRequest.object(1, goal, goal, "Open", () -> false);
        InteractionResult direct = Rs2Walker.walkTo(goal, npc);
        InteractionResult banked = Rs2Walker.walkWithBankedTransportsAndState(goal, 0, object);
        return new Object[]{npc.id, npc.kind, npc.entityId, npc.name, npc.action, npc.anchor,
                npc.access, npc.ownedTarget, npc.runtime, npc.lifecycle.reason(), npc.lifecycle.attempts(),
                direct, banked, InteractionResult.SUCCEEDED};
    }

    public static void cancel(InteractionRequest request) {
        Rs2Walker.cancelInteraction(request);
    }
}
