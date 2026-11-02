package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CgPlanExecutorTest {
    @Test public void protectionHasPriorityOverMovementAndConsumables() {
        FightPlan plan = new FightPlan();
        plan.protect = Style.MAGIC;
        plan.moveX = 3; plan.moveY = 4; plan.eat = true; plan.drink = true;
        FightObs observation = new FightObs();
        observation.prayer = 20;
        List<Click> clicks = new PlanExecutor().clicks(plan, observation, 1, 1);
        assertEquals(1, clicks.size());
        assertEquals(Click.Type.PROTECT, clicks.get(0).type);
    }

    @Test public void movementIsNotReissuedAtTheDestination() {
        FightPlan plan = new FightPlan();
        plan.moveX = 3; plan.moveY = 4;
        FightObs observation = new FightObs();
        observation.px = 3; observation.py = 4;
        assertTrue(new PlanExecutor().clicks(plan, observation, 1, 5).isEmpty());
    }
}
