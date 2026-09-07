package net.runelite.client.plugins.microbot.autobankstander;

import java.util.Collections;
import java.util.List;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;

/** Executable lifecycle assertions for the outer processing recovery decision. */
public final class ProcessingRecoveryControllerTest {
    public static void main(String[] args) {
        retriesBankingOnlyAfterProcessorStateIsReset();
        stopsWhenProcessorRecoveryIsExhausted();
        waitsForOuterScriptGuardBeforeLoginRecovery();
        System.out.println("ProcessingRecoveryControllerTest PASSED");
    }

    private static void retriesBankingOnlyAfterProcessorStateIsReset() {
        StubProcessor processor = new StubProcessor(true);
        ProcessingRecoveryController.Decision decision =
                new ProcessingRecoveryController().recover(processor);
        expect(decision == ProcessingRecoveryController.Decision.RETRY_BANKING,
                "successful processor reset should retry through banking");
        expect(processor.recoveryCalls == 1, "outer lifecycle must invoke processor recovery exactly once");
    }

    private static void stopsWhenProcessorRecoveryIsExhausted() {
        StubProcessor processor = new StubProcessor(false);
        ProcessingRecoveryController.Decision decision =
                new ProcessingRecoveryController().recover(processor);
        expect(decision == ProcessingRecoveryController.Decision.STOP,
                "exhausted processor recovery should stop the script");
        expect(processor.recoveryCalls == 1, "outer lifecycle must consult processor recovery exactly once");
    }

    private static void waitsForOuterScriptGuardBeforeLoginRecovery() {
        StubProcessor processor = new StubProcessor(true);
        ProcessingRecoveryController controller = new ProcessingRecoveryController();
        controller.observeLoggedOut();

        expect(controller.resumeAfterLogin(false, processor)
                        == ProcessingRecoveryController.LoginDecision.WAIT_FOR_SCRIPT_GUARD,
                "login recovery must wait while super.run blocks the script");
        expect(processor.loginRecoveryCalls == 0,
                "processor state must not change while Break Handler still owns the pause");
        expect(controller.resumeAfterLogin(true, processor)
                        == ProcessingRecoveryController.LoginDecision.FORCE_BANKING,
                "released script guard should force one banking reconciliation");
        expect(processor.loginRecoveryCalls == 1,
                "processor login recovery must run exactly once");
        expect(controller.resumeAfterLogin(true, processor)
                        == ProcessingRecoveryController.LoginDecision.CONTINUE,
                "completed login recovery must not repeat");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class StubProcessor implements BankStandingProcessor {
        private final boolean recoverable;
        private int recoveryCalls;
        private int loginRecoveryCalls;

        private StubProcessor(boolean recoverable) {
            this.recoverable = recoverable;
        }

        @Override public boolean recoverFromProcessingFailure() { recoveryCalls++; return recoverable; }
        @Override public boolean recoverAfterLogin() { loginRecoveryCalls++; return true; }
        @Override public boolean validate() { return true; }
        @Override public List<String> getBankingRequirements() { return Collections.emptyList(); }
        @Override public boolean hasRequiredItems() { return true; }
        @Override public boolean performBanking() { return true; }
        @Override public boolean process() { return true; }
        @Override public boolean canContinueProcessing() { return true; }
        @Override public String getStatusMessage() { return "test"; }
    }
}
