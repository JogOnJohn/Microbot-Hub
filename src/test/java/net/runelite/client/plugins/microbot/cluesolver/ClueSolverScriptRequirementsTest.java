package net.runelite.client.plugins.microbot.cluesolver;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClueSolverScriptRequirementsTest {
    @Test
    void pendingRequirementsDoNotLoseTheirClueToANewClue() throws Exception {
        RecordingScript script = new RecordingScript();
        script.overlay = mock(ClueSolverOverlay.class);
        script.clueScrollPlugin = mock(ClueScrollPlugin.class);
        when(script.clueScrollPlugin.getClue()).thenReturn(mock(CoordinateClue.class));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        field("executorService").set(script, executor);
        field("itemRequirementsTask").set(script, new CompletableFuture<Boolean>());
        Method process = ClueSolverScript.class.getDeclaredMethod("processClues");
        process.setAccessible(true);

        process.invoke(script);

        assertNull(field("currentClue").get(script));
        verifyNoInteractions(script.overlay);
        verify(executor, never()).submit(any(Runnable.class));
    }

    private static Field field(String name) throws Exception {
        Field field = ClueSolverScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
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
