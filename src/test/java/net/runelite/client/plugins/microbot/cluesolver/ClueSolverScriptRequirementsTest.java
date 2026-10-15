package net.runelite.client.plugins.microbot.cluesolver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClueSolverScriptRequirementsTest {
    @Test
    void failedRequirementsDoNotStartClue() {
        RecordingScript script = new RecordingScript();
        script.overlay = mock(ClueSolverOverlay.class);
        script.onRequirementsComplete(false);
        assertEquals(0, script.started);
        verify(script.overlay).updateTaskStatus("Requirement fulfillment failed");
    }

    @Test
    void successfulRequirementsStartClue() {
        RecordingScript script = new RecordingScript();
        script.onRequirementsComplete(true);
        assertEquals(1, script.started);
    }

    @Test
    void absentResultIsNotSuccess() {
        RecordingScript script = new RecordingScript();
        script.overlay = mock(ClueSolverOverlay.class);
        script.onRequirementsComplete(null);
        assertEquals(0, script.started);
        verify(script.overlay).updateTaskStatus("Requirement fulfillment failed");
    }

    private static class RecordingScript extends ClueSolverScript {
        private int started;

        @Override
        void onRequirementsMet() {
            started++;
        }
    }
}
