package net.runelite.client.plugins.microbot.GemCrabKiller;

import java.lang.reflect.Method;
import java.util.Collections;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GemCrabKillerPotionSafetyTest {
    private boolean invoke(String name, Class<?> argumentType, Object argument) throws Exception {
        Method method = GemCrabKillerScript.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        return (boolean) method.invoke(new GemCrabKillerScript(), argument);
    }

    private boolean canDrink(int hp, boolean food, boolean dharok) throws Exception {
        GemCrabKillerConfig config = mock(GemCrabKillerConfig.class);
        when(config.dharokMode()).thenReturn(dharok);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(hp);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
            inventory.when(Rs2Inventory::getInventoryFood).thenReturn(food
                    ? Collections.singletonList(mock(Rs2ItemModel.class)) : Collections.emptyList());
            return invoke("canAffordDivineHpCost", GemCrabKillerConfig.class, config);
        }
    }

    @Test void divineCostCannotConsumeAllRemainingHealth() throws Exception {
        assertFalse(canDrink(10, true, false));
    }

    @Test void normalModeCanHealAfterDivineWithFood() throws Exception {
        assertTrue(canDrink(11, true, false));
    }

    @Test void withoutFoodKeepThePostDoseBankMargin() throws Exception {
        assertFalse(canDrink(39, false, false));
    }

    @Test void dharokDoesNotAssumeFoodWillHealTheDose() throws Exception {
        assertFalse(canDrink(39, true, true));
    }

    @Test void enoughHealthAllowsDivineWithoutFood() throws Exception {
        assertTrue(canDrink(50, false, false));
    }

    @Test void divineTimerPreventsPrematureRedosing() throws Exception {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getVarbitValue(Varbits.DIVINE_RANGING)).thenReturn(1);
            assertTrue(invoke("isDivineActive", Skill.class, Skill.RANGED));
            assertFalse(invoke("isDivineActive", Skill.class, Skill.ATTACK));
        }
    }

    @Test void ordinaryPotionWaitsUntilBoostRunsLow() throws Exception {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.RANGED)).thenReturn(99);
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.RANGED)).thenReturn(104);
            assertFalse(invoke("isBoostRunOut", Skill.class, Skill.RANGED));
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.RANGED)).thenReturn(102);
            assertTrue(invoke("isBoostRunOut", Skill.class, Skill.RANGED));
        }
    }

    @Test void magicUsesItsFlatBoostThreshold() throws Exception {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(101);
            assertFalse(invoke("isBoostRunOut", Skill.class, Skill.MAGIC));
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(100);
            assertTrue(invoke("isBoostRunOut", Skill.class, Skill.MAGIC));
        }
    }
}
