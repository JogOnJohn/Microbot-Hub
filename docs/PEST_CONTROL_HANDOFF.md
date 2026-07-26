# Pest Control strategy handover

## Checkout

- Repository: `https://github.com/JogOnJohn/Microbot-Hub.git`
- Branch: `fix/pest-control-strategy`
- Plugin version: `2.4.13`
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
  already moving. This preserves prompt stall recovery without repeatedly
  issuing minimap clicks during healthy travel.
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

`build\libs\PestControlPlugin-2.4.13.jar`

Installed artifact:

`C:\Users\Billy\.runelite\microbot-plugins\PestControlPlugin.jar`

The 2.4.13 installed/package SHA-256 will be recorded after packaging and
replacement. The previous validated 2.4.10 SHA-256 was:

`C9EBD537FA25D07D1D590F384CFB05160CA081680866CFC338F4EFDAD0535A13`

There was exactly one matching jar in the active plugin directory. The 2.4.10
backup created for the 2.4.11 pre-smoke install is:

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.10-20260727-082401.jar`

The previous 2.4.9 backup remains at:

`C:\Users\Billy\.runelite\microbot-plugin-backups\PestControl\PestControlPlugin.backup-2.4.9-20260727-075113.jar`

Launch the validated spike client with:

`C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync\launch-microbot-shortestpath-spike.bat`

## Version 2.4.13 validation status

The implementation addresses the 2.4.10 diagnostic findings: portal pursuit
now outranks activity fallback, overlay state is isolated from global utility
status text, healthy movement is clicked less often, and short round-loading
gaps remain in `REQUEUE`. A pre-smoke 2.4.11 screenshot found that directly
injecting `PestControlScript` into the overlay created a second, stopped script
instance. Version 2.4.12 instead reads status through the plugin's actual
running script. Its first complete round then showed that the login grace could
still miss a transition after `wasInPestControl` had already been cleared.
Version 2.4.13 preserves a separate round-exit timestamp so the grace survives
that state reset. Focused build, installation, and two-round live smoke evidence
are pending.

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
