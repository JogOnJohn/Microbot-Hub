# Blackjack Plugin Rules

## Safety Invariants

- Healing has priority over blackjacking. Never drop empty jugs; a full inventory prevents combat after failed pickpockets.
- Operate only in the supported marked Pollnivneach house and against the selected level-appropriate bandit.
- Configure target-specific Menu Entry Swapper actions: normal left-click is `Pickpocket` and Shift+left-click is `Knock-Out`. Verify the live top option and cursor hull before clicking.
- Do not issue another Knock-Out while the target is unconscious or while the current two-pickpocket burst remains unresolved.
- Release pickpocket bursts from success/failure signals, with only a bounded safety fallback for missing events.
- Cursor anchors and wander points must remain inside the live NPC convex hull. Avoid curtain/door menu entries.
- Combat reset first unequips the blackjack, attempts Shift+left-click `Knock-Out`, and re-equips when an inventory slot is available. A full inventory must retain the staged safespot fallback at `3359,2995,0` then `3360,2993,0`.

## Runtime Signals

Prioritize these `client.log` messages during smoke tests:

- `Starting BlackjackPlugin version=`
- `Configured Menu Entry Swapper`
- `Shift-click Knock-Out dispatched`
- `Disarmed combat-reset Knock-Out`
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
