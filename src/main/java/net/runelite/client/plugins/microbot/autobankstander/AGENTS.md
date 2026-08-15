# Auto Bank Stander Plugin Rules

## Scope

- This package owns `[MB] Bank Stander` (`AutoBankStanderPlugin`), not
  `BanksBankStanderPlugin`.
- Keep changes inside `autobankstander` unless a shared Microbot API change is
  necessary and separately justified.
- Preserve existing Magic and Fletching behavior while changing Herblore.

## Processing Invariants

- One ingredient-combine dispatch creates one in-flight batch transaction.
- Do not dispatch another combine/use command until the transaction has either
  started and completed, or failed through an explicit bounded recovery path.
- Use inventory/container deltas, animation, make-interface state, and game
  ticks as acknowledgements. Do not rely on one fixed sleep.
- Revalidate item IDs and quantities immediately before banking or processing.
- Micro-breaks occur only between complete batches and outside unresolved bank,
  make, decant, or GE interfaces.

## Recorded Actions

- Treat Action Recorder data as observed intent and outcomes, not replay input.
- Never hard-code or replay canvas coordinates.
- Resolve current inventory items and slots for every batch.
- The 2026-08-15 fast-herb recording demonstrates serpentine cleaning with
  mostly 180-240 ms inter-click timing. Keep this experimental mode separate
  from the existing 5-15 ms turbo mode until live comparisons justify replacing
  either behavior.

## Continuous Mode

- Keep acquisition, cleaning, unfinished potion, finished potion, decant,
  selling, and reconciliation as explicit restartable phases.
- Every phase needs entry conditions, success evidence, timeout, retry limit,
  and a graceful stop state.
- Enforce configured capital reserve, buy/sell limits, cycle limits, and stop
  conditions. Never continue buying or selling after accounting becomes
  ambiguous.
- Build recipes from item IDs and level/ratio metadata; prayer potions are the
  first live profile, not a hard-coded special case for the state machine.

## Runtime

- Build/install changes do not affect the running client until the plugin JAR is
  replaced and the client/plugin is reloaded.
- Never build, install, toggle, launch, close, or restart without current user
  authorization.
- Before interpreting logs, prove branch, commit, source version, installed JAR
  hash, and loaded plugin identity.
