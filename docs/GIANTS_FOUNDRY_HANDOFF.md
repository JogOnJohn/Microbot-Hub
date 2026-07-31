# Giants' Foundry Upgrade Handoff

Read this before continuing work on the Giants' Foundry plugin.

## Current checkpoint

- Branch: `feat/upgrade-foundry-plugin`
- Base: `origin/main` at `551da81`
- Previous code commit: `d9b111d` (`fix(giants-foundry): same-tick stage interrupts and heat momentum control`)
- Plugin version: `1.4.1` (**live-validated on 2026-07-31**; see "1.4.0 regression and 1.4.1 recovery" below)
- Validated VM: `Bizza 12345` (`clanker\vmadmin2`)
- Authoritative guest worktree: `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade`
- Host context worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-giants-foundry`
- Installed JAR: `C:\Users\VMAdmin2\.runelite\microbot-plugins\GiantsFoundryPlugin.jar` (`1.4.1`)
- Installed 1.4.1 JAR SHA256: `720578ABC83915F1320AB681A76F2B6D30DB8CAEDA5335EA8D2B0D5DA301DAB4`

The installed VM JAR is the current **live-validated** checkpoint. The 2026-07-31 smoke completed
and handed in one sword after the unsafe 1.4.0 same-tick stage handler was removed. The client was
left open and logged in on world 330, but the Giants' Foundry plugin was stopped after the hand-in
to avoid consuming another cycle while the residual quality losses are reviewed.

## Branch commits

The Foundry-specific branch work is five code commits on top of `origin/main`:

```text
f2ab87a fix(giants-foundry): harden recovery and align rewards with wiki strategy
10787c4 fix(giants-foundry): reset materials after hand-in
093d2c5 feat(giants-foundry): automate progression and rewards
c9a9e1f fix(giants-foundry): harden live state handling
bd4f720 feat(giants-foundry): upgrade activity automation
```

## Implemented behavior

- Supports level-aware automatic alloys and validated manual bars or recycled items.
- Locks the material plan for each sword so a level-up cannot change materials mid-cycle.
- `AUTO_BEST` uses 14 iron/14 steel from level 30, 14 steel/14 mithril from level 50, 18 mithril/10 adamant from level 70, and 19 adamant/9 rune from level 85.
- `AUTO_ECONOMY` follows the same progression except it remains on mithril/adamant at level 85+.
- Opens the Foundry bank, snapshots relevant supplies, withdraws the exact plan, and stops cleanly when the current strategy cannot be supplied. It does not silently substitute another alloy.
- Defaults preform collection to ice gloves; bucket and Smiths gloves (i) remain supported.
- Uses moderate mouse activity.
- Automates level-eligible mould purchases, then optionally the Smiths outfit. The default is `MOULDS_THEN_OUTFIT`; `MOULDS_ONLY` and `DISABLED` are available.
- Only allowlisted moulds and outfit items are bought. Consumables, the colossal blade, and double ammo mould are not bought.
- Expands the overlay with state, next action, current craft, material plan, supplies, successful crafts, Smithing level and levels gained, XP, reputation earned/spent, reward value, material cost, net GP, runtime, stage, heat, quality, progress, and crucible contents.
- Logs state observations, transitions, actions, confirmations, material accounting, hand-ins, purchases, and nonfatal scheduler failures.

### Added in 1.3.0 (commit `f2ab87a`, unit-tested only)

- Bucket mode fetches the bucket of water in a short bank trip before preform pickup instead of withdrawing it with the bars. The old order needed 29 slots for a 28-bar plan, so bucket mode always stopped with a bogus supply shortage.
- Restarting with a partially filled crucible banks for the exact remainder instead of latching a fatal error. Remainder-based withdrawal also means a partial crucible can no longer be overfilled past the plan.
- Errors are split into fatal (quest, region, gear, plan validation, crucible/plan mismatch) and transient (interaction and confirmation failures). Transient failures retry with the action cooldown and only latch after three consecutive misses of the same action; the counter resets when the calculated state changes.
- A completed-sword snapshot is retained before any Kovac interaction and reconciled idempotently from the next tick if the acknowledgement is missed, closing the session-accounting race from the 1.2.0 smoke.
- A stage change clears the action cooldown and animation guard so the interrupting click fires immediately, addressing the repeatable five-point quality losses at workstation boundaries.
- Temperature actions are floored at two ticks in `HeatActionStateMachine` so near-boundary corrections cannot resolve as instant no-op clicks (the `292->292` chatter).
- `MOULDS_THEN_OUTFIT` now prioritises the Smiths outfit once six shop moulds are unlocked (wiki: outfit ~15% rate gain vs ~3-5% for the mould tail), then finishes the remaining moulds. Consecutive affordable purchases happen without closing and reopening the shop.
- Session material cost is charged by actually withdrawn quantities; a pre-filled crucible no longer bills a full cycle to the session ledger.
- Validation waits for the player position instead of latching the Sleeping Giants error when enabled around login, and the quest check runs only after the region check passes.
- The config panel text documents the full auto progression (bronze/iron, iron/steel at 30, steel/mithril at 50, 18 mithril + 10 adamant at 70, 19 adamant + 9 rune at 85; economy stays on mithril/adamant).

## Live validation

The final installed JAR was built and tested in the Bizza VM, not only in the host checkout.

Confirmed live:

- Recovery from an empty inventory at the Foundry bank.
- Exact withdrawal and loading of 14 steel plus 14 mithril at Smithing 53.
- Full crucible, pour, preform pickup, heat management, all workstations, and hand-in.
- A 113/113 sword completed at 19:19:44 AEST on 2026-07-30.
- Post-hand-in acknowledgement observed both `preformRemoved=true` and `progressReset=true`.
- Automatic purchase of Flamberge Blade, Stiletto Forte, and Serpent Blade from the live Kovac shop.
- After the Serpent Blade purchase, the next commission correctly transitioned through `SELECTING_MOULD -> PREPARING_MATERIALS`, withdrew both materials, filled the crucible, and started another sword.
- Level transition from the pre-50 iron/steel strategy to steel/mithril at level 50.
- Expanded overlay rendered correctly in the live client.

Automated validation command used:

```powershell
$env:JAVA_HOME='C:\Users\VMAdmin2\.jdks\temurin-11.0.31'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.home=C:\Users\VMAdmin2\.jdks\temurin-11.0.31' test GiantsFoundryPluginJar '-PpluginList=GiantsFoundryPlugin' --console=plain
```

The relevant tests are under `src/test/java/net/runelite/client/plugins/microbot/giantsfoundry/` and cover material planning, shop planning, state resolution, state snapshots, and heat action solving.

### Final live smoke: 2026-07-30

The final observed sword started its commission at 19:30:12 AEST and handed in at 19:35:34. It completed every preparation and crafting state without a plugin exception, scheduler failure, material withdrawal regression, or idle state. The hand-in was acknowledged with `preformRemoved=true` and `progressReset=true`; the final quality was 103/113. The client was closed immediately after this completion. The Gradle `FAILURE: Build failed` at the end of the launch log is therefore expected process-termination output, not a Foundry failure.

The smoke exposed two follow-up opportunities, **both addressed in code by `f2ab87a` (1.3.0) but not yet re-smoked**:

- A real hand-in immediately before the observed sword was missed by session accounting. The sword entered `HANDING_IN` at 19:30:06 and the next commission at 19:30:10, but no `hand-in acknowledged` or `craft complete` line was emitted. The next cycle recovered through `PREPARING_MATERIALS`, but the successful-craft count and material cost both remained one cycle low. The race was that the hand-in reset can arrive after `completeHandInIfAcknowledged()` returns false and before the next snapshot resolves to `GETTING_COMMISSION`. 1.3.0 retains a pending completed-sword snapshot before interacting with Kovac and reconciles it idempotently on the next tick that leaves `HANDING_IN`.
- The completed sword lost five quality at three workstation-stage boundaries. Each loss began while the previous station animation was still active and the script was travelling to a temperature tool: grind to polish at 19:32:29, polish to grind at 19:33:19, and grind to hammer at 19:34:14. 1.3.0 interrupts the previous workstation immediately on stage change (cooldown and animation guard cleared). The heat solver also issued near-boundary no-op/correction actions (`292->292`, `736->736`); 1.3.0 floors temperature actions at two ticks so they cannot resolve as instant no-ops. The heating-to-922-then-cooling-from-965 overshoot (momentum-aware stopping) remains open.

## Last bug and fix

Reported symptom: after handing in a sword, the plugin stood at Kovac/bank and reported `Missing 14 Steel bar from inventory` even though the bank contained hundreds of bars.

Root cause: the game reset Foundry progress before the equipped-preform cache cleared. The old hand-in completion check missed that transient state, so cycle fields were not reset and `inventoryPrepared=true` leaked into the next commission. State resolution then skipped `PREPARING_MATERIALS` and entered `FILLING_CRUCIBLE` with an empty inventory.

Commit `10787c4` fixes both sides:

- A hand-in is acknowledged when either the preform is removed or Foundry progress resets.
- `fillCrucible()` defensively treats an empty crucible plus no planned inventory as stale preparation and returns to bank preparation.

The full hand-in, shop purchase, next commission, bank withdrawal, and crucible-fill sequence was observed live after this fix.

## Laptop live smoke: 2026-07-30 evening (1.3.0)

Client on the laptop (`Bizza 12345`, Smithing 55), JAR hash `25d9ef15...`, script started 21:20:31,
four complete swords plus most of a fifth by 21:48. Zero errors, zero transient failures, zero
temperature-action stalls, no no-op heat clicks.

Confirmed live on 1.3.0:

- All four hand-ins counted (`sessionCraft=1..4`). Sword 3 hit the exact acknowledgement race from
  the VM smoke and the reconcile path caught it (`hand-in acknowledgement arrived late; reconciling`).
- Startup purchase of Defender Base (359->59 rep) and mid-session purchase of Defenders Tip
  (344->44 rep, 3 seconds after the sword 3 hand-in). Rep gating reads live varp 3436.
- Qualities: 86/91, 107/107 (a -5 was repaired by a sweet spot), 92/97, 115/115.
- The polled stage-change interrupt fired on every boundary; hammer and polish exits never lost
  quality, but grindstone exits lost 5 quality on 3 of 4 occasions (2-tick swing beats the 300ms
  poll). Every hammer entry's long fine heat overshot to 997-998 of the 1000 damage cap and forced
  a double cool. Roughly two redundant "top-up" temperature sips per sword.

Those three findings are what `d9b111d` (1.4.0) addresses:

1. Same-tick stage-flip interrupt via the progress varbit (13949) event in
   `GiantsFoundryPlugin.onVarbitChanged` -> `GiantsFoundryScript.onStageFlip`. The polled
   interrupt remains as fallback; `adjustTemperature` now monitors an already-running
   action instead of re-clicking.
2. `HeatActionSolver.relativeSolve` reserves headroom for the accelerating in-flight ticks that
   land after the stop decision (`momentum` guard against `max`).
3. Temperature actions end with a settle cooldown (`markAction` after monitoring) and
   `suppressHeatTopUp` skips proactive in-band corrections while current heat still supports
   `min(actionsLeftInStage, 3)` station actions.

## 1.4.0 regression and 1.4.1 recovery: 2026-07-31

The first live 1.4.0 sword failed at progress 400. At the
`POLISHING_WHEEL -> TRIP_HAMMER` boundary, the progress-varbit event set the new stage and clicked
`Dunk-preform`, but the 300 ms worker had already captured the previous polishing snapshot. The
worker resumed, treated that stale snapshot as a reverse `TRIP_HAMMER -> POLISHING_WHEEL`
transition, and clicked the polishing wheel. That cancelled the lava action while
`heatingCoolingState` remained active. The next worker tick monitored a temperature action that
was no longer happening for about 45 seconds, until heat and quality both reached zero. The
damaged sword was returned to Kovac.

This was not fail-safe exception handling; it was a cross-thread state/action race. Version 1.4.1
removes only the progress-varbit same-tick action path and the scheduler branch that monitored an
event-started temperature action. The authoritative polled stage transition is restored. The 1.4
heat-momentum guard, settle cooldown, top-up suppression, and all earlier Foundry improvements
remain.

Validation:

- Guest build: JDK 11, `test GiantsFoundryPluginJar -PpluginList=GiantsFoundryPlugin`.
- Tests: 33 passed, zero failures/errors.
- Built and installed JAR SHA256:
  `720578ABC83915F1320AB681A76F2B6D30DB8CAEDA5335EA8D2B0D5DA301DAB4`.
- Interactive client launch; visible-session Java process and authenticated Agent Server on
  `127.0.0.1:8081` confirmed separately.
- One full sword ran from progress 0 to hand-in without a reverse-stage transition, temperature
  stall, damaged-sword path, scheduler exception, or zero-quality collapse.
- Hand-in at 13:34:04 AEST: `sessionCraft=1`, quality `88/113`, Smithing XP gained `7422`.
- The next cycle had two transient `bank-open` failures and then recovered on the third attempt.

Residual issues:

- Polled transitions still lose five quality on some boundaries. The recovery sword fell from
  113 to 88, with losses around grindstone/hammer/polish transitions. This is a performance and
  reward issue, not a completion failure.
- Several fine heat/cool corrections fired within a stage. The heat solver avoided the previous
  997-998 hammer-entry peak, but temperature chatter is not fully eliminated.
- Any future same-tick optimization must pass a stage generation/snapshot token into the worker
  and prevent stale worker actions after an event. Do not restore the 1.4.0 shared-state mutation
  or event-started action monitor.
- Longer soak: Claymore Blade should be bought at Smithing 59, and once six shop moulds are
  unlocked the next purchase target should switch to Smiths boots (outfit priority).

## Known limits and remaining tests

- **1.4.1 is the current live-validated completion checkpoint**, but only one recovery sword was
  observed. The following remain unexercised live: bucket-of-water mode end to end, a
  mid-fill restart with an empty inventory, a genuine transient-failure retry, and a
  multi-purchase shop visit (two items affordable at once).
- Graceful supply exhaustion is covered by planner/state tests but has not been deliberately tested against a depleted live bank.
- The shop interaction uses an allowlisted contract captured from live widget group `753`, child `22`, with entries spaced by 13 and purchase action identifier 2. Unknown names fail closed, but a game widget update may require recapturing this mapping.
- Outfit purchasing has not been reached live. Note it now takes priority once six shop moulds are unlocked, so it will be reached earlier than under 1.2.0 ordering.
- Higher transitions at 70 and 85 Smithing have unit coverage only.
- Recycled-item mode has validation coverage but has not had a full live cycle.
- Overlay GP values are value estimates for materials and rewards, not a literal coin-pouch ledger. Material cost is charged only for what the session actually withdraws.
- Transient interaction failures latch into a fatal error after three consecutive misses of the same action; a genuinely stuck client therefore still stops instead of retrying forever.
- A client relaunch produced two transient `SSLHandshakeException`/HTTP 400 authentication attempts before BreakHandler recovered automatically. No evidence connected this to Foundry logic.

## Continue on the laptop

Fetch the branch before editing:

```powershell
git fetch origin
git switch feat/upgrade-foundry-plugin
git pull --ff-only origin feat/upgrade-foundry-plugin
git status --short --branch
```

In the Bizza VM, discover the current IP through VMware Tools and use the Bizza SSH helper. Do not assume the older `vmadmin` paths. Before changing the guest worktree:

```powershell
cd 'F:\vmware boxs\Bizza 12345 MBOT'
.\host-ssh-vm.ps1 'cmd /c "cd /d C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade && git status --short --branch"'
```

Use the host checkout for fast reading, but build and validate in the Bizza VM. Source changes do not affect the running client until the plugin JAR is rebuilt and installed. Never launch a visible RuneLite client through non-interactive SSH.

The guest worktree currently builds:

```text
C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade\build\libs\GiantsFoundryPlugin-1.4.1.jar
```

The last interactive launch helper is:

```text
Host:  F:\vmware boxs\MBOT\operator-work\temp\launch-bizza-giants-foundry-1.4.1.ps1
Guest: C:\Users\VMAdmin2\operator-work\temp\launch-bizza-giants-foundry-1.4.1.ps1
Log:   C:\Users\VMAdmin2\operator-work\output\logs\microbot-client-launch-giants-foundry-1.4.1.log
```

The Agent Server is guest-local on port 8081 and requires the configured `X-Agent-Token`. Prefer the guest `microbot-cli` wrapper so the token is never printed or copied into documentation. Useful passive checks are:

```powershell
Set-Location C:\Users\VMAdmin2\IdeaProjects\Microbot
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli state'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli skills'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli scripts'
```

## Recommended next sequence

1. Fetch this branch and confirm version 1.4.1 or later before making changes.
2. Treat 1.4.1 as the completion-safe baseline. Do not restore the 1.4.0 same-tick event action
   without explicit stale-snapshot/action arbitration.
3. Smoke several full hand-in/rebank cycles on 1.4.1 and quantify quality losses and redundant
   temperature corrections before designing the next stage-interrupt optimization.
4. Deliberately stop the plugin mid-crucible-fill with an empty inventory and restart; it should bank for the remainder instead of latching an error.
5. Run at least one full sword in bucket-of-water mode; the bucket should be fetched in a separate bank trip just before pickup.
6. Test a controlled supply shortage when interrupting the existing progression is acceptable.
7. Test the level 70 alloy transition, the six-mould outfit priority (buy order and multi-purchase in one shop visit), and eventually the full Smiths outfit path.
