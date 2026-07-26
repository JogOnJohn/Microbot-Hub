# Pest Control strategy handover

## Checkout

- Repository: `https://github.com/JogOnJohn/Microbot-Hub.git`
- Branch: `fix/pest-control-strategy`
- Plugin version: `2.4.14`
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
- Live activity recovery profile: start at `40%`, resume at `60%`
- Quick Prayer: enabled
- All four portal special-attack toggles: disabled

Source defaults for activity recovery remain `60%` start and `75%` target;
both thresholds are exposed in config. Old persisted settings named NPC
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
- Attackable portal selection runs before sustained activity recovery. Ordinary
  fallback pests therefore cannot delay a portal chase; the portal or its
  healing Spinner becomes the activity source once a shield drops.
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

## Build and install

The focused build used the current shortest-path spike client jar:

```powershell
.\gradlew.bat compilePestControlJava PestControlPluginJar --console=plain "-PmicrobotClientPath=C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync\runelite-client\build\libs\microbot-2.6.15.jar"
```

Packaged artifact:

`build\libs\PestControlPlugin-2.4.14.jar`

Installed artifact:

`C:\Users\Billy\.runelite\microbot-plugins\PestControlPlugin.jar`

Installed/package SHA-256:

`F51B0F41F66222C3BE17DD012026CCF1E0E13B48EE4947B0CAB098340BA24E24`

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
- Token source: `C:\Users\Billy\.runelite\.agent-token`
- Required header: `X-Agent-Token`
- Never print, log, commit, or copy the token into another file.
- Client log: `C:\Users\Billy\.runelite\logs\client.log`
- Agent screenshots default under
  `C:\Users\Billy\.runelite\test-results\screenshots`.

Compilation and packaging do not prove runtime correctness. For future strategy
changes, smoke at least two complete rounds, include a winning-team requeue, and
check the exact route/style/activity behavior affected by the change.

## Remaining watch items

- The outer-perimeter route is validated for the current ranged setup. A
  melee-primary profile may need different staging and route tuning.
- Brawlers are deliberately excluded from fallback targeting, but their body
  blocking can still trap movement; rely on watchdog recovery evidence before
  adding special-case Brawler combat.
- If gate-aware routing is added later, distinguish open IDs from closed IDs
  (`14233` through `14248`) and do not replace the validated outer route with
  unconditional gate clicking.
- Reassess the live `40/60` activity thresholds only with several-round data;
  the source defaults are intentionally more defensive.
