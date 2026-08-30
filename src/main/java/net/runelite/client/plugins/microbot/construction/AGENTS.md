# Construction 2 Plugin Rules

## Scope

- Keep current-development construction behavior intact. The immediate task is
  butler call, bank-trip, return-dialogue, and outside-house recovery only.
- FibbyBibby's `fix-construction-butler` is a read-only donor reference, not a
  merge/cherry-pick target. Port only behavior that is explained and testable.

## State Rules

- Re-query House Options and Call Servant widgets at each interaction boundary.
  Do not click a widget reference captured before a tab/interface transition.
- A confirmed bank trip suppresses further Call Servant attempts until return,
  timeout recovery, or a proven failed dispatch.
- Continue construction only while the current dialogue/action permits it.
  Treat missed return dialogue, absent butler, payment, and insufficient planks
  as explicit recoverable states with bounded retries.
- Outside the POH must retain an explicit `returnToTheHouse()` recovery path.
  Do not reduce it to idle without live evidence.

## Validation And Logging

- Preserve all existing supported construction modes unless a targeted test
  proves a required change.
- Version-bump the plugin for any behavior change.
- Log state transitions, Call Servant dispatch/result, bank-trip state, return
  recovery, and terminal failures. Do not print every scheduler iteration.
- Compile before installation. A built source JAR is not proof of a sideloaded
  or running plugin.
