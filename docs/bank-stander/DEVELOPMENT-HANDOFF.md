# Bank Stander Development Handoff

## Source

- Repository: `JogOnJohn/Microbot-Hub`
- Branch: `feat/bank-stander-continuous-mode`
- Baseline: `origin/development` at `a662687` when this package was created
- Plugin: `[MB] Bank Stander` / `AutoBankStanderPlugin`
- Plugin version at baseline: `1.0.3`
- Host worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-bank-stander`
- Operator manifest: `F:\vmware boxs\MBOT\operator-work\plugins\bank-stander.psd1`
- Full local handoff: `F:\vmware boxs\MBOT\docs\handoffs\bank-stander\BANK-STANDER-HANDOFF.md`

## Current Baseline

The branch begins after the upstream-development commits that added hidden turbo
herb cleaning (`9d366ae`) and removed the stale finished-potion state/make-all
delay (`7b62fc7`). Verify those changes against the running client before
creating overlapping fixes.

At package creation, Bizza had an active/enabled `AutoBankStanderPlugin` with
installed JAR SHA-256:

```text
FE53ACF5DD225A0220DAA30D50622EE57280095F59B27893A2A5B5CC94B87EC5
```

No build, install, toggle, or lifecycle action was performed.

## Recorded Fast Cleaning

The host action bundle is:

```text
F:\vmware boxs\MBOT\operator-work\output\action-recordings\20260815-125509-fast-herb-clean-ff211694
```

It contains two 27-herb Ranarr batches. The 54 clean interactions use a
serpentine slot order and are mostly 180-240 ms apart. Resolve live inventory
slots and use container deltas; never replay the recorded screen coordinates.

## Development Plan

1. Introduce a batch transaction model that owns dispatch, start acknowledgement,
   progress, completion, timeout, and recovery.
2. Remove fixed-delay races from potion combining and suppress duplicate use
   commands once a transaction is acknowledged.
3. Make bank turnaround event-driven and quantity-aware.
4. Add a separately selectable recorded-fast cleaning experiment.
5. Add recipe metadata and a phase-based continuous Herblore orchestrator.
6. Implement and validate prayer potions first, then exercise other recipe shapes.
7. Capture live GE and decant action metadata before implementing those adapters.
8. Add ingredient-order variation and safe batch-boundary micro-breaks.

Continuous mode must be restartable and bounded by capital reserve, price limits,
retry limits, stop-loss/supply conditions, and an explicit cycle policy.

## Validation

- Unit-test batch transaction generations and duplicate suppression.
- Compare normal, turbo, and recorded-fast cleaning over multiple inventories.
- Smoke finished prayer potions with and without amulets of chemistry.
- Validate each continuous phase independently before one complete bounded cycle.
- Prove runtime attribution from the installed JAR and startup logs before
  accepting any behavioral result.
