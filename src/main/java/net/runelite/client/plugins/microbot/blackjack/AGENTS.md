# Blackjack Plugin Rules

## Safety Invariants

- Healing has priority over blackjacking. Never drop empty jugs; a full inventory prevents combat after failed pickpockets.
- Operate only in the supported marked Pollnivneach house and against the selected level-appropriate bandit.
- Use target-specific `Knock-Out` menu entries. Verify the live menu row and cursor hitbox before clicking.
- Do not issue another Knock-Out while the target is unconscious or while the current two-pickpocket burst remains unresolved.
- Release pickpocket bursts from success/failure signals, with only a bounded safety fallback for missing events.
- Cursor anchors and wander points must remain inside the live NPC convex hull. Avoid curtain/door menu entries.
- Combat reset stages at `3359,2995,0`, then moves behind the bed to `3360,2993,0`. Confirm the target is north of the safespot.

## Runtime Signals

Prioritize these `client.log` messages during smoke tests:

- `Starting BlackjackPlugin version=`
- `Clicking verified menu option`
- `Knock-Out dispatched`
- `Knock-Out confirmed`
- `Knock-Out failure signal`
- `Knock-Out command was not confirmed`
- `Pickpocket burst timed out`
- `Unexpected movement after pickpocket click`
- `Idle full-inventory signal`
- `Combat reset staged`
- `Blackjack stopped`

Do not infer a menu miss from a disappearing menu alone. Correlate the verified cursor point, target animation, dispatch time, overhead/chat result, and first pickpocket timing.
