package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;

/** Owns the outer script decision after a processor reports failure. */
final class ProcessingRecoveryController {
    enum Decision {
        RETRY_BANKING,
        STOP
    }

    enum LoginDecision {
        CONTINUE,
        WAIT_FOR_SCRIPT_GUARD,
        FORCE_BANKING
    }

    private boolean loginRecoveryPending;

    Decision recover(BankStandingProcessor processor) {
        return processor != null && processor.recoverFromProcessingFailure()
                ? Decision.RETRY_BANKING
                : Decision.STOP;
    }

    void observeLoggedOut() {
        loginRecoveryPending = true;
    }

    void reset() {
        loginRecoveryPending = false;
    }

    LoginDecision resumeAfterLogin(boolean scriptGuardReady, BankStandingProcessor processor) {
        if (!loginRecoveryPending) return LoginDecision.CONTINUE;
        if (!scriptGuardReady) return LoginDecision.WAIT_FOR_SCRIPT_GUARD;

        loginRecoveryPending = false;
        return processor != null && processor.recoverAfterLogin()
                ? LoginDecision.FORCE_BANKING
                : LoginDecision.CONTINUE;
    }
}
