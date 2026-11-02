package net.runelite.client.plugins.microbot.cgprep.antiban;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CgBreakGateTest {
    @Test public void releaseRestoresPreexistingBreakLock() {
        boolean previous = BreakHandlerScript.lockState.get();
        try {
            BreakHandlerScript.lockState.set(true);
            BreakGate gate = new BreakGate();
            gate.arm();
            gate.sync(true, message -> {});
            gate.release();
            assertTrue(BreakHandlerScript.lockState.get());
        } finally { BreakHandlerScript.lockState.set(previous); }
    }

    @Test public void stoppedGateDoesNotRelockOnLateLoopPass() {
        boolean previous = BreakHandlerScript.lockState.get();
        try {
            BreakHandlerScript.lockState.set(false);
            BreakGate gate = new BreakGate();
            gate.arm();
            gate.sync(true, message -> {});
            assertTrue(BreakHandlerScript.lockState.get());
            gate.release();
            gate.sync(true, message -> {});
            assertFalse(BreakHandlerScript.lockState.get());
        } finally { BreakHandlerScript.lockState.set(previous); }
    }
}
