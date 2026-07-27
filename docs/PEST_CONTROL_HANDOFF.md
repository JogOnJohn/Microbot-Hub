# Pest Control strategy handover

## Checkout

- Repository: `https://github.com/JogOnJohn/Microbot-Hub.git`
- Branch: `fix/pest-control-strategy`
- Plugin version: `2.4.32` (work in progress)
- Microbot client used for validation: `2.6.15`
- Strategy reference: `https://oldschool.runescape.wiki/w/Pest_Control/Strategies`

For an existing checkout:

```powershell
git fetch origin
git switch fix/pest-control-strategy
git pull --ff-only origin fix/pest-control-strategy
```

For a new worktree when the branch is not already checked out locally:

```powershell
git fetch origin
git worktree add ..\Microbot-Hub-pest-control -b fix/pest-control-strategy origin/fix/pest-control-strategy
```

Read `CLAUDE.md`, `docs/PLUGIN_DEBUGGING_NOTES.md`, and
`docs/AGENT_SERVER.md` before changing or live-testing the plugin.

## Known-good rollback baseline

Before the 2.4.30 gate-entry and diagnostics work, the known-good local
rollback point was:

- Strategy commit: `ed5074f2523ce99f0171fd816271601ccefd2749`
- Handover-only follow-up commit: `83794a546275b371e36ae2d648903b296b6bd112`
- Plugin version: `2.4.29`
- Packaged and installed JAR size: `55,733` bytes
- SHA-256: `B9B3571495420E5AFB664CA7F490D0E13DE834F381D4713B02E177684E9F0A95`

This is a functional rollback baseline, not a claim that it is bug-free. Its
known limitations include activity losses during long portal engagements,
adjacent gate leaves being treated independently, stale destroyed-portal
staging, and movement/interaction attempts through closed perimeter gates.

## Current Bizza runtime state

- Strategy commit: `ed5074f2523ce99f0171fd816271601ccefd2749`
- Strategy commit subject: `Stabilize Pest Control obstacle recovery`
- Target VMX: `C:\Users\bizz4\Documents\Virtual Machines\Bizza 12345\Bizza 12345.vmx`
- Guest identity: `clanker\vmadmin2`
- Guest Microbot repo: `C:\Users\VMAdmin2\IdeaProjects\Microbot`
- Guest Hub base repo: `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub`
- Build worktree: `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-pest-control-strategy`
- Installed JAR: `C:\Users\VMAdmin2\.runelite\microbot-plugins\PestControlPlugin.jar`
- Installed JAR SHA-256: `B9B3571495420E5AFB664CA7F490D0E13DE834F381D4713B02E177684E9F0A95`
- Bizza SSH helper: `F:\vmware boxs\Bizza 12345 MBOT\host-ssh-vm.ps1`
- Client log: `C:\Users\VMAdmin2\.runelite\logs\client.log`

Do not use `F:\vmware boxs\MBOT\host-ssh-vm.ps1` for Bizza. That helper
targets the older `Windows 11 x64.vmx` VM. Do not launch a visible RuneLite
client through SSH; use IntelliJ, an interactive VM path, or a verified
`vmrun -interactive` helper.

The Bizza build worktree was detached at `78be5add` and dirty at handover:

```text
## HEAD (no branch)
 M PestControlOverlay.java
 M PestControlPlugin.java
 M PestControlScript.java
```

Inspect and preserve those files before changing the guest checkout. The pushed
origin branch is the authoritative commit history; do not reset the guest
worktree merely to synchronize it.

## Strategy contract

The plugin is intentionally opinionated for fast mass-world wins. During a
round its priority is:

1. Keep Auto Retaliate off.
2. Pursue an attackable portal immediately.
3. Kill a Spinner healing that portal.
4. Attack the portal.
5. Before a portal is vulnerable, move to the randomized opening side.
6. If only one shielded portal remains, pre-position there.
7. Maintain activity with a nearby Spinner, then a Torcher, then the
   lowest-level ordinary non-Brawler pest.
8. Wait or stage when activity is safe instead of fighting randomly.

Shield-drop chat and the portal NPC's `Attack` action both mark readiness.
When several portals are ready, the script joins the largest nearby player
group, retains the current target across one-player differences, and favours
Purple on a tie. A newly ready portal preempts ordinary-pest combat.

The explicit runtime states are published through `Microbot.status`, rendered
from the script's own state snapshot, and logged on transition. They include
initialization, travel, boat/requeue, opening and pre-position travel, portal
chase/attack, Spinner combat, activity recovery, holding combat, error, and
stopped states.

## Billy's validated configuration

- World: `344`
- Primary style: `RANGED`
- Primary/ranged weapon: `Adamant crossbow`
- Purple: Adamant crossbow, Rapid
- Blue: `None`, falling back to primary ranged
- Yellow: Dragon scimitar, Slash with Stab fallback
- Red: `None`, falling back to primary ranged
- Opening Purple/ranged weight: `55%`
- Live activity recovery profile: start at `40%`, resume at `70%`
- Quick Prayer: enabled
- All four portal special-attack toggles: disabled

Source defaults for activity recovery are `40%` start and `70%` target; both
thresholds are exposed in config. Old persisted settings named NPC
Priority 1/2/3, `switchWeapon`, and `switchCombatStyle` can remain in the
RuneLite properties file but are no longer declared or read by this strategy.

Portal weapon fields accept `None` to use the primary style. Portal special
attack toggles are independent, so a future dagger can be enabled only for the
desired portal without enabling specials elsewhere.

## Important implementation details

- `PestControlScript.java` owns the strategy and state machine.
- `PestControlConfig.java` owns the current config surface. Do not reintroduce
  the removed generic NPC-priority chain unless the strategy itself changes.
- `PestControlPlugin.java` reads shield-drop game chat and owns the plugin
  version. Bump the version for every installable behavior change.
- `PestControlScript.java` publishes one immutable diagnostic snapshot after
  each scheduled tick. The overlay reads only that snapshot; it must not read
  client, player, portal-widget, or combat-widget state while rendering.
- The expanded overlay reports state/detail and age, round location, activity
  percentage and recovery target, active portal/crowd/readiness source, opening
  side, remaining/ready portal counts, confirmed weapon/style, Auto Retaliate,
  progress and command ages, and conditional boat-entry status.
- Client/player/widget reads in the scheduled loop must stay behind client
  thread helpers. Attack-style widget discovery and `Rs2Combat.setAttackStyle`
  must run on the client thread.
- Attack-style indexes are cached per normalized weapon and desired style.
  RuneScape remembers the mode per weapon, so a known matching `COM_MODE`
  returns without opening the Combat tab. The log message `combat tab
  unchanged` is the live confirmation path.
- Movement uses short `walkFastCanvas` steps, which prefer direct scene clicks
  and fall back to the minimap. East/west portal transfers use the south outer
  perimeter once outside the central enclosure: region waypoints `(15,20)` and
  `(48,20)`. This avoids closed Void Knight gates and keeps travel in pest lanes.
- Ranged portal staging is in front of the portal at normal Rapid range. Direct
  portal/NPC interaction takes precedence when the target is visible.
- Activity recovery uses stable state details so changing meter percentages do
  not reset attack throttling or cause state-log churn.
- Once activity recovery starts, it owns combat until the target threshold is
  reached. Portal hits are not treated as reliable activity recovery because
  misses and low damage can leave the bar falling. The ready portal remains
  the next objective and is re-selected immediately after recovery reaches
  70 percent.
- Movement commands retry after 750 ms when stationary and 1.5 seconds while
  already moving. Every command attempt is throttled even if the walker cannot
  confirm dispatch, while only observed movement counts as watchdog progress.
- A sub-five-second login-state gap immediately after leaving a round is
  reported as a requeue transition. A longer gap still becomes a genuine
  `INITIALISING: waiting for login` state.
- Gangplank boarding has a three-second confirmation guard. A retry is issued
  only when boat entry was not observed.
- The watchdog requires observed movement or interaction; click dispatch alone
  is not progress.

Version 2.4.29 adds these local obstacle-recovery rules:

- Brawler flank candidates must be collision-reachable from the player.
- A Brawler flank is held for at most 4.5 seconds before the blocker is attacked.
- Brawler clearing is committed for 8 seconds to avoid immediately restarting
  the same flank.
- A crossed or timed-out gate receives an 8-second reuse cooldown.
- Round-result grace is six seconds, and a fast next launch finalizes the prior
  result before new-round state is initialized.

## Version 2.4.29 Bizza live validation on 2026-07-27

The focused compile and full build passed against Microbot client `2.6.15`. The
JAR above was installed and loaded by a visible interactive Bizza client. Agent
Server confirmed `Bizza 12345` logged in, scripts unpaused, and Pest Control
active and enabled.

The initial clean-launch acceptance window completed:

```text
4 played, 4 paid wins, 0 unpaid rounds, 16 points gained
```

The final longer-run snapshot at 18:35 AEST was:

```text
Plugin counter: 10 played, 9 won, 1 lost
Points gained: 32
Reward-backed result: 8 paid rounds, 2 unpaid rounds
```

Awarded points are the authoritative result. The plugin's 9-1 label overstates
the effective record:

- At 18:27 all four portals were destroyed and the outcome was inferred as a
  win, but points stayed at 28. Activity had fallen to 7 percent, so this was an
  unpaid round despite the winning team result.
- At 18:35 a second unpaid round was inferred as lost with only one portal
  destroyed. Points remained at 32.

No new Java or Pest Control exception occurred after the clean 2.4.29 launch.
The Brawler patch was exercised repeatedly: long flanks switched to
`clearing Brawler`, short flanks resumed portal attacks, and paid rounds
completed afterward. The old indefinite same-tile flank loop was not observed.

Current extant bugs and follow-up work:

1. Make result accounting reward-backed. Do not infer a paid win solely because
   all portals were destroyed; zero activity reward is still possible.
2. Preserve the last in-round activity value. Current round-end diagnostics
   report `activity unknown` after transition to the boat.
3. Treat adjacent tiles/objects as one logical gate. The 18:27 unpaid round
   recorded crossing timeouts at adjacent west gate tiles and the central gate.
   The cooldown suppressed immediate same-object reopen spam but did not remove
   all time lost around gate pairs.
4. Protect activity during long portal engagements. In the 18:27 round activity
   recovered to 81 percent, then decayed to 7 percent during portal chasing and
   attacks before the game ended.

Deferred strategy work, deliberately outside the 2.4.30 fixes: track the Void
Knight's health and, after a player death, enter a defense-assist state when
that health is below a configurable threshold. Treat this as an independent
strategy run with its own live validation; do not mix it into gate or activity
recovery fixes.

## Version 2.4.30 work in progress

A live 2.4.29 capture at about 20:23 AEST reproduced repeated `I can't reach
that!` messages while the script owned a portal/Spinner objective across a
closed fence. Earlier round-entry logs also showed Yellow opening movement
reaching its staging state without first recording a gate open. This is now a
first-class regression case: no portal, Spinner, or Brawler click may bypass
gate passage while the player is inside the central enclosure.

The 2.4.30 implementation pass adds:

- Gate-first east/west lane entry using safe inner approach points, observed
  open/closed gate state, and observed passage before portal logic resumes.
- Outer-perimeter ownership before direct NPC interaction when transferring
  between east and west portal lanes.
- One logical cooldown covering both adjacent leaves of a gate.
- Source activity defaults of 40/70 while preserving recovery ownership once
  recovery begins.
- Moderate Microbot mouse intensity at plugin start without applying an
  antiban template, action delay, or microbreak profile.
- A single camera pivot for each newly selected portal, with an eight-second
  same-target cooldown.
- Stable staging on a surviving shielded portal; a destroyed opening portal
  can no longer remain the waiting destination.
- A same-target attack acknowledgment guard and rate-limited duplicate-command
  diagnostics. State-detail transitions no longer reset the attack throttle.

Focused compilation and `PestControlPluginJar` packaging passed against
Microbot 2.6.15. The packaged 2.4.30 JAR is 58,872 bytes with SHA-256:

```text
254413EF0648A463C026FF6DE65664F924F756AC42C034E5DD91485F09579374
```

Installation and live validation remain pending at this point in the
handover.

The first 2.4.30 live launch confirmed the new version overlay, Moderate mouse
speed, 40/70 config, Auto Retaliate OFF, safe west-gate approach, one gate-open
interaction, ordinary-pest activity combat, immediate Purple pursuit, and a
portal Spinner preemption. It also exposed two issues fixed in 2.4.31:

- During an accepted gate crossing, the safe inner-approach check could regain
  control and briefly steer back toward the gate. Active crossing now owns
  movement until passage is observed. The same commitment is created when the
  gate was already open, so another player's gate action follows the identical
  crossing path.
- Camera conversion from a logical instanced `WorldPoint` returned no scene
  point, so no pivot occurred. Camera targeting now prefers the matched portal
  NPC's scene-local position and uses logical conversion only as a fallback.

That 2.4.30 round requeued normally but earned no points and was classified as
a loss. It also exercised the duplicate-attack diagnostics, Purple portal
combat, Spinner preemption, an in-round respawn/gate re-entry, and an
east-to-west outer-perimeter transfer. Focused compilation and packaging then
passed for 2.4.31. Its JAR is 59,109 bytes with SHA-256:

```text
6052BF9D992BE505AF5FFA0F40BA53BE4C0C4CF535CA9835CDA6EE3A29193FAF
```

The 2.4.31 live round then confirmed a clean opening sequence when the west
gate was already open: one approach, one gate commitment, one crossing, and no
return to the inner anchor. Portal-local camera targeting also worked. However,
the later death/re-entry path briefly released gate ownership on fence boundary
tiles, producing alternating `CHASE_PORTAL` and `OPEN_GATE` states before the
player crossed. Same-portal camera turns also recurred every eight seconds.

Version 2.4.32 addresses those findings:

- A pending crossing stores its own outer target and remains authoritative on
  boundary tiles and across portal target changes.
- Gate-pair cooldowns are also honored by the dedicated lane-entry path.
- Camera steering is exactly one pivot per selected portal, not a periodic
  same-target steer.
- The legacy first-opposite-drop hold is removed. With activity recovery still
  authoritative once started, the first attackable portal now resumes the
  agreed portal priority instead of being ignored for up to the next drop.

Focused compilation and packaging passed for 2.4.32. Its JAR is 58,878 bytes
with SHA-256:

```text
3B3CCAB4C19ABFBD6A74CD250B284BD5ED4E8D25B158EF381249F893C418D89A
```

Version 2.4.32 still requires installation and live validation.

Keep these fixes local to Pest Control unless a separate reproduction proves a
shared WebWalker defect.

## Current Bizza build and install

The focused validation commands were:

```powershell
gradlew.bat compileJava -PpluginList=PestControlPlugin --console=plain
gradlew.bat clean build -PpluginList=PestControlPlugin --console=plain
```

The full focused build completed successfully, including Pest Control
compilation, JAR generation, test compilation, and tests. The packaged and
installed 2.4.29 JAR was 55,733 bytes with SHA-256:

```text
B9B3571495420E5AFB664CA7F490D0E13DE834F381D4713B02E177684E9F0A95
```

The 2.4.29 runtime activity settings at that validation point were:

```text
pestcontrol.activityRecoveryStart = 60
pestcontrol.activityRecoveryTarget = 75
```

## Historical Billy 2.4.15 build and install

The focused build used the current shortest-path spike client jar:

```powershell
.\gradlew.bat compilePestControlJava PestControlPluginJar --console=plain "-PmicrobotClientPath=C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync\runelite-client\build\libs\microbot-2.6.15.jar"
```

Packaged artifact:

`build\libs\PestControlPlugin-2.4.15.jar`

Packaged 2.4.15 SHA-256:

`454CB6CC64E92D037CBDD7E7C7303181DB9F3F47FE05E914ADB4EBA6D3302E6B`

Installed artifact:

`C:\Users\Billy\.runelite\microbot-plugins\PestControlPlugin.jar`

Installed version: `2.4.14`

Installed SHA-256:

`F51B0F41F66222C3BE17DD012026CCF1E0E13B48EE4947B0CAB098340BA24E24`

Version 2.4.15 was intentionally compiled and packaged only. It was not copied
over the active 2.4.14 jar and was not live-smoked in this pass.

There was exactly one matching jar in the active plugin directory. The
immediately previous 2.4.12 install is recoverable from:

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.12-20260727-084501.jar`

Pre-smoke 2.4.11 and 2.4.10 installs are also recoverable from:

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.11-20260727-082858.jar`

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.10-20260727-082401.jar`

The previous 2.4.9 backup remains at:

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.9-20260727-075113.jar`

Launch the validated spike client with:

`C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync\launch-microbot-shortestpath-spike.bat`

## Version 2.4.15 compile-only validation on 2026-07-27

Version 2.4.15 replaces the minimal one-line overlay and dormant debug portal
widget reads with an immutable script-owned diagnostic snapshot. This preserves
the binding to the plugin's actual running script while ensuring rendering does
not perform client/widget reads on the overlay thread.

The focused `compilePestControlJava` task and `PestControlPluginJar` packaging
both passed against Microbot 2.6.15. The packaged jar hash is recorded above.
Per the requested boundary, the installed plugin remains the live-validated
2.4.14 build; 2.4.15 still needs a visual overlay check and live runtime smoke
after it is installed.

## Version 2.4.14 live validation on 2026-07-27

The implementation addresses the 2.4.10 diagnostic findings: portal pursuit
now outranks activity fallback, overlay state is isolated from global utility
status text, healthy movement is clicked less often, and short round-loading
gaps remain in `REQUEUE`. A pre-smoke 2.4.11 screenshot found that directly
injecting `PestControlScript` into the overlay created a second, stopped script
instance. Version 2.4.12 instead reads status through the plugin's actual
running script. Its first complete round then showed that the login grace could
still miss a transition after `wasInPestControl` had already been cleared.
Version 2.4.13 preserves a separate round-exit timestamp so the grace survives
that state reset. A subsequent death/loading transition showed that an in-round
login gap should preserve the current combat state rather than report
`REQUEUE`; version 2.4.14 distinguishes those cases and also throttles ambiguous
walker attempts.

The final world-344 smoke window ran from 08:49:01 through the next launch at
08:53:03:

- Round 1 opened Yellow at 08:49:01 and ended at 08:50:58. It reached `BOAT`
  at 08:51:00 and the next launch opened Blue at 08:51:03.
- Round 2 ended at 08:53:00, reached `BOAT` at 08:53:02, and the following
  launch opened Purple at 08:53:03.
- The overlay visibly reported `Micro PestControl V2.4.14`, the script-owned
  runtime state, Adamant crossbow/Rapid, and Auto Retaliate OFF.
- The window contained eight shield drops and eight adaptive portal selections.
  Low activity never delayed a selected portal: all five 39% recovery starts
  recovered, portal combat remained in control while a target was attackable,
  and a new selected portal immediately preempted ordinary fallback combat.
- Portal Spinners preempted portal attacks twice. Yellow selected Dragon
  scimitar/Slash, and Adamant crossbow/Rapid was restored with cached styles
  leaving the Combat tab unchanged.
- The sole remaining shielded portals were pre-positioned at Yellow in round 1
  and Red in round 2.
- Both round ends issued one gangplank interaction and had zero confirmation
  retries. The sub-five-second next launches contained no `INITIALISING` or
  false `round transition` state.
- The final window had zero watchdog recoveries, script errors, client-thread
  errors, and `must be called on client thread` messages.

BreakHandler V2 auto-login won the first relaunch race and selected world 388.
It was stopped through the Agent Server before the final window, after which
Pest Control switched to world 344. Stop that break handler before future
automated login smoke tests so it cannot override the requested world. The
forced wrong-world recovery produced setup-only errors before 08:49:01; none
recurred during either counted round.

## Historical 2.4.10 live validation on 2026-07-27

Version 2.4.10 was restarted cleanly, logged into world 344, and completed two
full mass-world rounds from launch through immediate requeue:

- Round 1: opened Purple at 07:52:42 and ended at 07:54:47.
- Round 2: opened Purple at 07:55:21 and ended at 07:57:25.
- The second requeue reached `BOAT` at 07:57:27, two seconds after victory,
  without a gangplank retry.
- The overlay visibly reported `Micro PestControl V2.4.10`.
- Auto Retaliate was confirmed off at both round entries.
- Activity recovery acquired targets, held combat, and recovered successfully.
- Portal Spinners preempted portal attacks in both rounds.
- Yellow selected Dragon scimitar/Slash; primary restoration selected Adamant
  crossbow/Rapid.
- In round 2 the Inventory tab was left open. Cached Slash and Rapid switches
  both logged `combat tab unchanged`, and a subsequent frame still showed
  Inventory. This verifies the redundant Combat-tab opening fix.
- Cross-map movement used the south perimeter at region `y=20`, including
  Blue-to-Purple and Yellow-to-Purple transfers, instead of clicking through
  the central enclosure.
- No Pest Control watchdog recovery and no `must be called on client thread`
  error occurred. Unrelated world-hopper ping timeouts can still appear.

The activity bar commonly reports `7%` on the same tick as a victory/death
transition. Treat that as a round-boundary reset unless it persists into the
next round.

## Live-debug workflow

- Agent Server: `http://127.0.0.1:8081`
- Current Bizza token source: `C:\Users\VMAdmin2\.runelite\.agent-token`
- Required header: `X-Agent-Token`
- Never print, log, commit, or copy the token into another file.
- Current Bizza client log: `C:\Users\VMAdmin2\.runelite\logs\client.log`

Compilation and packaging do not prove runtime correctness. For future strategy
changes, smoke at least two complete rounds, include a winning-team requeue, and
check the exact route/style/activity behavior affected by the change.

## Remaining watch items

- The outer-perimeter route is validated for the current ranged setup. A
  melee-primary profile may need different staging and route tuning.
- Brawlers remain excluded from ordinary activity fallback targeting. When a
  Brawler blocks portal line of sight, 2.4.29 attempts one reachable flank and
  then attacks the blocker for a bounded commitment.
- If gate-aware routing is added later, distinguish open IDs from closed IDs
  (`14233` through `14248`) and do not replace the validated outer route with
  unconditional gate clicking.
- Current Bizza activity thresholds are the defensive source defaults `60/75`.
  Reassess them only with several-round reward-backed data.
