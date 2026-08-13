# Giants' Foundry Plugin Rules

## Controller Invariants

- Keep run enabled.
- Keep all game interactions on the single scheduler. Varbit handlers publish immutable transition data only.
- Validate the stage generation immediately before every interaction so stale snapshots cannot click an old workstation.
- Preserve closed-loop, two-speed heat control and observed-delta recalculation.
- Interrupt the prior temperature action and hand off promptly when the target band is reached.
- Treat the visible bank widget as authoritative; do not report chest-open failure while the bank is already open.
- Do not restore the rejected controlled-final-action station stop/reclick experiment.
- A requested live smoke ends only after sword hand-in and reward acknowledgement.

## Runtime Signals

Prioritize these log families:

- `Giants' Foundry transition`
- `Giants' Foundry observation`
- `Giants' Foundry: stage event`
- `Giants' Foundry temperature controller`
- `Giants' Foundry action result`
- `Giants' Foundry hand-in acknowledged`
- `Giants' Foundry craft complete`
- `Giants' Foundry tick failed`

Script-health may report `NO_HEARTBEAT` while scheduler transitions continue.
Use fresh state/action transitions and craft completion as authoritative evidence.
Correlate any quality loss with stage generation, heat, progress, station action
and timestamp before changing timing.
