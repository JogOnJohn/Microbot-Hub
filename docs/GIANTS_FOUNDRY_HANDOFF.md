# Giants' Foundry Upgrade Handoff

Read this before continuing work on the Giants' Foundry plugin.

## Current checkpoint

- Branch: `feat/upgrade-foundry-plugin`
- Base: `origin/main` at `551da81`
- Rollback commit: `0cc07d6` (`revert(giants-foundry): restore 7866f97 checkpoint`)
- Source plugin version: `1.2.3` (**built, tested, and installed**)
- Validated VM: `Bizza 12345` (`clanker\vmadmin2`)
- Authoritative guest worktree: `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade`
- Host context worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-giants-foundry`
- Installed JAR: `C:\Users\VMAdmin2\.runelite\microbot-plugins\GiantsFoundryPlugin.jar`
- Installed JAR SHA256: `CD545C4878CB0D6AEE81E81EB43A294CABE38F4CCEF954F04576CEF67710D313`

The installed `1.2.3` JAR is the streamlined event-assisted temperature-control checkpoint.
It keeps run enabled and continuous workstation use. A controlled-final-action experiment that
stopped and restarted workstations near stage boundaries was rejected because it caused repeated
station use, disabled run through its movement call, and materially slowed crafts. That experiment
is not present in the installed source or JAR.

The final requested one-sword smoke of this exact streamlined JAR was blocked before gameplay:
the relaunched client remained at `LOGIN_SCREEN` because RuneScape's authentication REST request
repeatedly returned HTTP 400 with `Remote host terminated the handshake`. The client was left open
with Giants' Foundry disabled for manual takeover. See the 1.2.3 section for the live evidence
collected before the authentication failure.

The rejected controlled-final experimental JAR is retained at:
`C:\Users\VMAdmin2\operator-work\output\archive\giants-foundry-plugin-backups\GiantsFoundryPlugin-controlled-final-experiment-20260731-195539.jar`.
The previous `1.2.1` JAR is retained at:
`C:\Users\VMAdmin2\operator-work\output\archive\giants-foundry-plugin-backups\GiantsFoundryPlugin-before-1.2.2-20260731-160412.jar`.
The previous `1.2.0` known-good JAR is retained at:
`C:\Users\VMAdmin2\operator-work\output\archive\giants-foundry-plugin-backups\GiantsFoundryPlugin-before-1.2.1-20260731-151157.jar`.
The last committed `1.2.2` source and JAR remain the rollback checkpoint at commit `4be239b`
(`fix(giants-foundry): checkpoint quality controls`).

## Version 1.2.3: event-assisted streamlined controller

Version 1.2.3 adds:

- A progress-varbit event coordinator that publishes immutable stage transitions with a monotonic
  generation. All game interactions remain owned by one scheduler.
- Generation checks before interactions so stale snapshots cannot click the previous workstation.
- Immediate cancellation of the old continuous action after a real stage transition.
- Closed-loop temperature control driven by observed heat changes, with fast bulk adjustment,
  bounded downshift to the slow action, dynamic arrival prediction, and immediate workstation
  handoff.
- A one-tick handoff barrier so an interrupted heat/cool action can settle before the destination
  workstation is clicked.
- Run-preserving interruption through `Rs2Walker.walkFastCanvas(step, true)`.
- A bank-open guard that treats the visible bank widget as authoritative before attempting to
  reopen the Foundry chest. Both material preparation and supply snapshots use this guard.

The intentionally removed controlled-final experiment:

- stopped a workstation before its final progress action;
- retried after three seconds even though a normal workstation progress tick can take about four
  seconds;
- generated repeated station interactions and slowed the script;
- used a movement call that disabled run.

Guest validation used JDK 11 and completed successfully:

```powershell
.\gradlew.bat --no-daemon '-Dorg.gradle.java.home=C:\Users\VMAdmin2\.jdks\temurin-11.0.31' test GiantsFoundryPluginJar '-PpluginList=GiantsFoundryPlugin' --console=plain
```

The built and installed JAR hashes matched:
`CD545C4878CB0D6AEE81E81EB43A294CABE38F4CCEF954F04576CEF67710D313`.

Live evidence collected while tuning 1.2.3:

- One controlled-final experimental sword completed and handed in at `116/121`.
- A following experimental sword showed one deterministic five-quality boundary loss before a
  sweet spot recovered it.
- The experiment was then observed interrupting and reclicking the trip hammer before its normal
  progress tick, confirming that its pursuit of perfect quality was too costly.
- After hand-in, the prior build reproduced a false `Could not open the Foundry bank chest`
  report while the bank was visibly open. The widget-aware bank guard was added afterward.
- The exact final streamlined build passed tests, built, installed, and launched. Its requested
  one-sword smoke could not begin because login authentication failed as described above.

## Version 1.2.2: quality-control experiment

Version 1.2.2 keeps all 1.2.1 behavior and adds four bounded changes:

- Reserves one additional accelerating heat/cooling tick in `HeatActionSolver` so an interrupt has
  more room to settle before the edge of a working band.
- Raises the workstation heat safety margin from 5 to 10.
- Suppresses a redundant in-band top-up when the current heat already supports at least the next
  three workstation actions (or all remaining actions when fewer than three remain).
- Detects stage changes on the scheduler thread and bypasses movement, animation, and action
  cooldown guards once, so the old workstation is interrupted as soon as the next scheduler
  snapshot observes the new stage. Interactions remain scheduler-owned; this does not restore the
  unsafe cross-thread 1.4.0 event action.

Guest validation used JDK 11 and:

```powershell
.\gradlew.bat --no-daemon '-Dorg.gradle.java.home=C:\Users\VMAdmin2\.jdks\temurin-11.0.31' test GiantsFoundryPluginJar '-PpluginList=GiantsFoundryPlugin' --console=plain
```

All 35 tests passed. The built and installed JAR hashes matched:
`D489D84C7E5661A1E4EE5CBFF020A2950A89D7C22127E9570C132221C65C5272`.

Live smoke on 2026-07-31:

- A pre-crash partial sword resumed only for hand-in at `116/121`; it was excluded from the two
  clean-start samples.
- Clean sword 1 completed at `114/119` (95.8%), compared with the previous 1.2.1 two-sword result
  of `95/105` (90.5%) each. Two five-quality losses occurred and Juggernaut Forte recovered one.
- Clean sword 2 completed at `81/111`. A shared RuneLite client-thread pause occurred from about
  16:16:25 to 16:16:36 AEST: both Giants Foundry and an unrelated QoL Wintertodt worker logged
  `ClientThread TimeoutException`, and quality had already fallen from 106 to 91 when the next
  Foundry snapshot ran. No DWM/display event or new VMware DX12 presentation error coincided with
  this pause. Additional five-quality losses occurred during large stage-boundary temperature
  corrections.
- The passive-cooling branch exercised successfully at 16:15:42 AEST, waiting about 700 ms and
  entering the trip-hammer range without a waterfall round trip.
- Both swords completed and handed in. There were no reverse-stage transitions, temperature-state
  stalls, repeated-click loops, or damaged-sword failures.

Conclusion: the momentum, settle, top-up, and passive-wait controls are useful, and a normal first
sword improved materially. The remaining deterministic loss is the interval between a progress
stage flip and completing travel to the new temperature tool. A future event-assisted interrupt
must queue work onto the existing single scheduler and carry a stage generation/token so a stale
snapshot cannot issue a reverse action. Do not restore direct client-thread interactions.

## Bizza graphics finding

The repeated VM failures are not supported by a VRAM-exhaustion theory. The VM has 2 GiB configured
graphics memory, while the VMware log reported about 98 MiB local usage and several GiB available
during the failure. The previous crash instead showed:

- repeated guest `dwm.exe` crashes and a graphics watchdog report;
- broad RuneLite `ClientThread TimeoutException` failures after DWM destabilized;
- repeated host `DX12Presentation: failed to close/reset command list` warnings with
  `E_INVALIDARG`;
- the presentation surface changing to approximately `3538 x 1935`, consistent with the reported
  4K-monitor/viewport-resize trigger.

The current VM keeps the DX12 renderer enabled. Broadcom's documented Workstation workaround for
this DX12 presentation failure is the host-global preference
`mks.enableDX12Presentation = "FALSE"` in
`C:\Users\bizz4\AppData\Roaming\VMware\preferences.ini`. It was applied with the VM and Workstation
closed. Guest SVGA3D acceleration and the DX12 renderer remain enabled; only the problematic host
DX12 presentation path is disabled.

## Version 1.2.1: passive cooling route decision

The 2026-07-31 rollback smoke observed two complete clean-start swords:

- `93/93`, with one temporary five-quality transition loss later recovered by a sweet spot.
- `105/115`, with two five-quality transition losses.
- Both hand-ins counted; there were no transient failures, temperature stalls, or script errors.
- Across the two cycles there were 37 temperature actions, including 10 zero-tick actions.

Version 1.2.1 adds a bounded wait-versus-waterfall decision for small cooling corrections after a
temperature-tool action:

- Costs are compared in game ticks using the live player/waterfall positions, the stage's existing
  waterfall distance, run state, passive decay of one heat per two ticks, and a two-tick waterfall
  interaction allowance.
- Waiting is capped at 16 game ticks and must save at least two ticks over travelling to the
  waterfall and then to the workstation.
- The decision is re-evaluated by the normal scheduler and times out to the waterfall path if
  passive decay does not reach the expected range.
- Waiting is allowed only after a temperature-tool action. It never waits while a workstation
  animation may still be changing heat.
- Sweet-spot bonuses, fast cooling, movement, missing scene data, and invalid inputs all retain the
  existing waterfall behavior.

The Bizza VM build ran `test GiantsFoundryPluginJar` successfully with 33 tests and zero
failures. The built and installed JAR hashes both matched:
`A9434E4759E70383FCF792D43CAFADA7663A27E5F36E890B2ECAE78F3F4C715F`.

## Branch commits

The current runtime checkpoint is the rollback baseline plus the passive-cooling change:

```text
0d08e1b feat(giants-foundry): prefer efficient passive cooling
0cc07d6 revert(giants-foundry): restore 7866f97 checkpoint
7866f97 docs(giants-foundry): add laptop handoff
```

The branch retains the earlier upgrade, live-fix, and documentation history below that checkpoint.

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

## Live validation

The final installed JAR was built and tested in the Bizza VM, not only in the host checkout.

Confirmed live:

- Version `1.2.1` completed and handed in two clean-start 14 steel/14 mithril swords on
  2026-07-31. Both logged `95/105`; session totals after the second hand-in were two crafts,
  8,511 Smithing XP gained, 42,392 GP estimated material cost, and -25,370 estimated net GP.
- The two cycles issued 39 temperature actions, including 12 zero-tick actions. There were five
  logged quality-loss transitions, no transient failures, no temperature stalls, no script
  errors, and no passive-cooling timeouts.
- No passive-cooling wait was selected in these two cycles. The observed position and route-cost
  combinations stayed on the existing waterfall path, so this smoke validates no regression but
  does not exercise the new wait branch.
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

## Last bug and fix

Reported symptom: after handing in a sword, the plugin stood at Kovac/bank and reported `Missing 14 Steel bar from inventory` even though the bank contained hundreds of bars.

Root cause: the game reset Foundry progress before the equipped-preform cache cleared. The old hand-in completion check missed that transient state, so cycle fields were not reset and `inventoryPrepared=true` leaked into the next commission. State resolution then skipped `PREPARING_MATERIALS` and entered `FILLING_CRUCIBLE` with an empty inventory.

Commit `10787c4` fixes both sides:

- A hand-in is acknowledged when either the preform is removed or Foundry progress resets.
- `fillCrucible()` defensively treats an empty crucible plus no planned inventory as stale preparation and returns to bank preparation.

The full hand-in, shop purchase, next commission, bank withdrawal, and crucible-fill sequence was observed live after this fix.

## Known limits and remaining tests

- Exercise an eligible passive-cooling decision live. The first two `1.2.1` swords did not produce
  a route comparison that met the current guard and minimum two-tick saving.
- Temperature control still loses quality at some boundary transitions. Both `1.2.1` smoke swords
  finished at `95/105`; one long fast-heat action and several fine heat/cool transitions accounted
  for the observed losses. This predates the passive-cooling decision and did not stall the state
  machine.
- The Agent Server script-health route reported `NO_HEARTBEAT`/loop count zero while the plugin was
  active and fresh Foundry transitions were continuing. Treat live transitions and craft
  completion as authoritative for this plugin until its scheduler reports health heartbeats.
- Graceful supply exhaustion is covered by planner/state tests but has not been deliberately tested against a depleted live bank.
- The shop interaction uses an allowlisted contract captured from live widget group `753`, child `22`, with entries spaced by 13 and purchase action identifier 2. Unknown names fail closed, but a game widget update may require recapturing this mapping.
- Outfit purchasing has not been reached live because it requires all eligible moulds first and much more reputation.
- Higher transitions at 70 and 85 Smithing have unit coverage only.
- Recycled-item mode has validation coverage but has not had a full live cycle in this session.
- Overlay GP values are value estimates for materials and rewards, not a literal coin-pouch ledger.
- Enabling the plugin before login can briefly show the Sleeping Giants requirement. Start it after login if this latch appears.
- A client relaunch produced two transient `SSLHandshakeException`/HTTP 400 authentication attempts before BreakHandler recovered automatically. No evidence connected this to Foundry logic.
- The 1.2.3 relaunch on 2026-07-31 repeatedly failed the same authentication REST request with
  HTTP 400 and `Remote host terminated the handshake`; unlike the earlier occurrence, it did not
  recover during the bounded smoke window. The final one-sword smoke remains pending.

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

The current build output is:

```text
C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade\build\libs\GiantsFoundryPlugin-1.2.3.jar
```

The last interactive launch helper is:

```text
Guest: C:\Users\VMAdmin2\operator-work\temp\launch-bizza-giants-foundry-1.2.3.ps1
Log:   C:\Users\VMAdmin2\operator-work\output\logs\microbot-client-launch-giants-foundry-1.2.3.log
```

The Agent Server is guest-local on port 8081 and requires the configured `X-Agent-Token`. Prefer the guest `microbot-cli` wrapper so the token is never printed or copied into documentation. Useful passive checks are:

```powershell
Set-Location C:\Users\VMAdmin2\IdeaProjects\Microbot
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli state'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli skills'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli scripts'
```

## Recommended next sequence

1. Fetch this branch and confirm the 1.2.3 checkpoint or later before making changes.
2. Log in manually if the transient authentication handshake failure persists.
3. Start Giants' Foundry only after `LOGGED_IN`, then smoke one complete sword through hand-in.
4. Confirm the open-bank guard prevents the false bank-open error on the next material withdrawal.
4. Test a controlled supply shortage when interrupting the existing progression is acceptable.
5. Test the level 70 alloy transition, later mould purchases, and eventually the Smiths outfit path.
6. Rebuild with JDK 11, replace the JAR only while the client is closed, launch interactively, and verify both the visible window and Agent Server state.
