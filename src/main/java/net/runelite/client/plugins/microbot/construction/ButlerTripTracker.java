package net.runelite.client.plugins.microbot.construction;

final class ButlerTripTracker {
    static final long DEPARTURE_TIMEOUT_MS = 8_000L;
    static final long RETURN_TIMEOUT_MS = 45_000L;

    enum Action {
        NONE,
        WAIT,
        HANDLE_RETURN_DIALOGUE,
        TALK_TO_RETURNED_BUTLER,
        RETRY_DISPATCH
    }

    private enum Phase {
        IDLE,
        AWAITING_DEPARTURE,
        AWAY
    }

    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;

    void dispatched(long now) {
        phase = Phase.AWAITING_DEPARTURE;
        phaseStartedAt = now;
    }

    Action observe(boolean dialogueOpen, boolean butlerPresent, long now) {
        if (phase == Phase.IDLE) {
            return Action.NONE;
        }

        long elapsed = now - phaseStartedAt;
        if (phase == Phase.AWAITING_DEPARTURE) {
            if (!dialogueOpen && !butlerPresent) {
                phase = Phase.AWAY;
                phaseStartedAt = now;
                return Action.WAIT;
            }
            if (elapsed >= DEPARTURE_TIMEOUT_MS) {
                reset();
                return Action.RETRY_DISPATCH;
            }
            return Action.WAIT;
        }

        if (dialogueOpen) {
            reset();
            return Action.HANDLE_RETURN_DIALOGUE;
        }
        if (butlerPresent) {
            reset();
            return Action.TALK_TO_RETURNED_BUTLER;
        }
        if (elapsed >= RETURN_TIMEOUT_MS) {
            reset();
            return Action.RETRY_DISPATCH;
        }
        return Action.WAIT;
    }

    boolean isTripInProgress() {
        return phase != Phase.IDLE;
    }

    void reset() {
        phase = Phase.IDLE;
        phaseStartedAt = 0L;
    }
}
