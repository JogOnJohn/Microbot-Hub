package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;

/** Owns the outer script decision after a processor reports failure. */
final class ProcessingRecoveryController {
    enum Decision {
        RETRY_BANKING,
        STOP
    }

    Decision recover(BankStandingProcessor processor) {
        return processor != null && processor.recoverFromProcessingFailure()
                ? Decision.RETRY_BANKING
                : Decision.STOP;
    }
}
