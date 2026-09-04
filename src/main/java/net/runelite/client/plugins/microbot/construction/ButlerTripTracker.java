package net.runelite.client.plugins.microbot.construction;

final class ButlerTripTracker {
    static final long BLOCKED_RETURN_GRACE_MS = 10_000L;

    enum Action {
        NONE,
        WAIT,
        HANDLE_RETURN_DIALOGUE,
        TALK_TO_RETURNED_BUTLER,
        CLICK_CURRENT_TILE
    }

    private enum Phase {
        IDLE,
        SERVANT_REQUESTED,
        AWAITING_DEPARTURE,
        AWAY
    }

    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private boolean repositionRequested;

    void servantRequested(long now) {
        phase = Phase.SERVANT_REQUESTED;
        phaseStartedAt = now;
        repositionRequested = false;
    }

    void dispatched(long now) {
        phase = Phase.AWAITING_DEPARTURE;
        phaseStartedAt = now;
        repositionRequested = false;
    }

    Action observe(boolean dialogueOpen, boolean butlerPresent, long now) {
        if (phase == Phase.IDLE) {
            return Action.NONE;
        }

        long elapsed = now - phaseStartedAt;
        if (phase == Phase.SERVANT_REQUESTED) {
            if (dialogueOpen) return Action.HANDLE_RETURN_DIALOGUE;
            if (butlerPresent) return Action.TALK_TO_RETURNED_BUTLER;
            if (elapsed >= BLOCKED_RETURN_GRACE_MS && !repositionRequested) {
                repositionRequested = true;
                return Action.CLICK_CURRENT_TILE;
            }
            return Action.WAIT;
        }

        if (phase == Phase.AWAITING_DEPARTURE) {
            if (!dialogueOpen && !butlerPresent) {
                phase = Phase.AWAY;
                return Action.WAIT;
            }
            if (elapsed >= BLOCKED_RETURN_GRACE_MS && !repositionRequested) {
                phase = Phase.AWAY;
                repositionRequested = true;
                return Action.CLICK_CURRENT_TILE;
            }
            return Action.WAIT;
        }

        if (dialogueOpen) {
            return Action.HANDLE_RETURN_DIALOGUE;
        }
        if (butlerPresent) {
            return Action.TALK_TO_RETURNED_BUTLER;
        }
        if (elapsed >= BLOCKED_RETURN_GRACE_MS && !repositionRequested) {
            repositionRequested = true;
            return Action.CLICK_CURRENT_TILE;
        }
        return Action.WAIT;
    }

    boolean isTripInProgress() {
        return phase != Phase.IDLE;
    }

    boolean isAwaitingReturn() {
        return phase == Phase.AWAITING_DEPARTURE || phase == Phase.AWAY;
    }

    String getStatus(long now) {
        if (phase == Phase.IDLE) return "IDLE";
        return phase.name() + " (" + Math.max(0L, (now - phaseStartedAt) / 1000L) + "s)";
    }

    void reset() {
        phase = Phase.IDLE;
        phaseStartedAt = 0L;
        repositionRequested = false;
    }
}
