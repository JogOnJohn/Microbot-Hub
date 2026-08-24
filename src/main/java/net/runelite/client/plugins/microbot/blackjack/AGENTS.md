# Blackjack Plugin Rules

## Safety Invariants

- Healing has priority over blackjacking. Never drop empty jugs inside the house. A disarmed combat reset may temporarily drop exactly one full wine only when healing is not latched, and must re-equip the blackjack and recover that owned wine before continuing.
- Operate only in the supported marked Pollnivneach house and against the selected level-appropriate bandit.
- Configure target-specific Menu Entry Swapper actions: normal left-click is `Pickpocket` and Shift+left-click is `Knock-Out`. Verify the live top option and cursor hull before clicking.
- Do not issue another Knock-Out while the target is unconscious or while the current two-pickpocket burst remains unresolved.
- Release pickpocket bursts from success/failure signals, with only a bounded safety fallback for missing events.
- Cursor anchors and wander points must remain inside the live NPC convex hull. Avoid curtain/door menu entries.
- Combat reset first creates a slot if configured, unequips the blackjack, attempts Shift+left-click `Knock-Out`, re-equips, and recovers any temporarily dropped wine. The staged safespot fallback remains at `3359,2995,0` then `3360,2993,0`.
- Wine exit and re-entry use the curtain's live `Open`/`Close` action on every retry. Do not trust a previous open timestamp or require exact player-tile equality before interacting.

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
