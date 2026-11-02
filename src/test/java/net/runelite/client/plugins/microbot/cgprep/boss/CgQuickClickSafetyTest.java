package net.runelite.client.plugins.microbot.cgprep.boss;

import java.awt.Rectangle;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CgQuickClickSafetyTest {
    @Test public void missingGeometryNeverFallsBackToRandomCanvas() {
        QuickClicks.Target target = new QuickClicks.Target();
        target.entry = new NewMenuEntry().type(MenuAction.CC_OP);
        assertFalse(QuickClicks.sendQuick(target));
    }

    @Test public void globalPausePreventsQuickDispatch() {
        boolean before = Microbot.pauseAllScripts.get();
        try {
            Microbot.pauseAllScripts.set(true);
            QuickClicks.Target target = new QuickClicks.Target();
            target.entry = new NewMenuEntry().type(MenuAction.CC_OP);
            target.rect = new Rectangle(5, 5, 10, 10);
            assertFalse(QuickClicks.sendQuick(target));
        } finally { Microbot.pauseAllScripts.set(before); }
    }
}
