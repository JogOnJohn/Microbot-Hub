---
name: microbot-plugin-dev
description: Create or modify a Microbot Hub plugin or script (C:\Users\Billy\IdeaProjects\Microbot-Hub). Use whenever writing plugin/script/config code, fixing plugin bugs, adding features to an existing Hub plugin, or wiring blocking events. Covers the plugin skeleton, threading rules, reachability footguns, verification patterns, build/deploy/restart cycle, and known-good reference plugins.
---

# Microbot Hub plugin development

Hub repo: `C:\Users\Billy\IdeaProjects\Microbot-Hub`. Plugins compile against the engine client jar
(version fetched from microbot.cloud at configure time; override with `-PmicrobotClientVersion=`).

## Plugin anatomy

Each plugin = one package under `src/main/java/net/runelite/client/plugins/microbot/<name>/`:
- `XPlugin.java` — `@PluginDescriptor` + RuneLite lifecycle. Bump the `version` field on ANY behavior change.
- `XScript.java` — extends `Script`; loop runs on a background executor via
  `scheduledExecutorService.scheduleWithFixedDelay`. Guard each tick with `Microbot.isLoggedIn()` and `super.run()`.
- `XConfig.java` — `@ConfigItem`s; UI is the custom MicrobotConfigPanel.
- Optional `BlockingEvent` implementations registered with `Microbot.getBlockingEventManager()`.
- Resources: `src/main/resources/.../<name>/docs/README.md` + assets.

## Footguns (each of these cost a live debugging session — read before coding)

### Reachability — three APIs, only one tells the truth
- `Rs2Walker.canReach(target)` — collision-blind 2D area intersection of the pathfinder endpoint vs
  target. Falsely passes for targets on another rooftop section / fenced area. Do not use for "can I
  walk there".
- `IEntity.isReachable()` (so `Rs2NpcModel.isReachable()`, `Rs2TileObjectModel.isReachable()`) —
  BROKEN: BFSes from the TARGET tile then asks if the target is in the result. Trivially true for
  anything in-scene on the current plane.
- `Rs2Tile.isTileReachable(target)` (`util.tile.Rs2Tile`) — real BFS from the PLAYER over live
  collision. This is the one to use.
- Items/NPCs on collision-blocked furniture (e.g. a mark of grace on a market-stall table): the tile
  itself is never walkable, but the game interacts from beside it. Accept target if its tile OR any
  cardinal neighbour (`loc.dx(±1)`, `loc.dy(±1)`) is reachable.

### Threading
- Raw RuneLite reads (`NPC.getWorldLocation()`, `Actor` getters, widget reads) assert the client
  thread and THROW `IllegalStateException: must be called on client thread` from script/blocking-event
  threads. Wrap with `Microbot.getClientThread().invoke(() -> ...)` (returns the value) or use the
  `Rs2*Model` wrappers, which hop threads internally.
- Script loops and blocking events run on background threads (`AgilityScript-N`,
  `Microbot-BlockingEvent`). Never block the client thread.

### Blocking events
- The manager catches exceptions from `execute()` and RE-VALIDATES IMMEDIATELY. An exception that
  escapes execute() before your failure handling runs = tight error loop, several fires per second.
  ALWAYS wrap the execute body in try/catch and convert unexpected exceptions into your backoff.
- Failure paths need a cooldown/backoff (e.g. 30s) checked in `validate()`, or the event hammers
  dead clicks while the underlying condition persists (e.g. pet stranded on another rooftop section).

### Verify by ground truth, never by click success
A menu click / `interact()` returning true only means the menu was invoked. The player may walk
nowhere and nothing happens. Real success signals:
- loot/feed → inventory count actually rose (`Rs2Inventory.itemQuantity` before/after)
- dialogue interaction → the expected dialogue/widget actually appeared
- timers → the varbit/timer actually reset
- "call follower" → the follower is actually within N tiles and reachable afterwards

### Commit-then-retry (sticky target) pattern
When a script decides to act on a transient target (ground item, NPC), COMMIT to it (record it in
state) BEFORE any early-out (isMoving/off-screen/menu-build-failure). Otherwise the next tick's
main-loop action (e.g. the next agility obstacle) preempts it and the target is lost. Bound the
commitment with a timeout + a per-location failure blocklist so a dead target can't stall the loop.

### Scan timing on plane transitions
Right after a plane change, tile-item/collision/plane state lags 1-2 ticks. A scan that runs while
the player is still moving/animating can miss entities that are really there. Only trust a scan taken
with the player settled (not moving, not animating); gate follow-up actions on one settled scan,
bounded (~1.5s) so a stuck animation can't stall.

## Build / deploy / reload

```bash
./gradlew compile<SourceSet>Java     # compile only, e.g. compileAgilityJava, compileKittentrackerJava
./gradlew <PluginName>Jar            # e.g. MicroAgilityPluginJar, QoLPluginJar, KittenPluginJar
cp build/libs/<PluginName>-<ver>.jar ~/.runelite/microbot-plugins/<PluginName>.jar   # no version in target name
```
- Stale versioned jars accumulate in `build/libs/` — copy the EXACT current version (matches the
  plugin descriptor), never a wildcard.
- Sideloaded jars can be copied while the client runs, BUT classes are cached: a **full client
  restart** is required to load them. Toggling the plugin off/on does NOT reload the jar.
- Never rebuild the ENGINE client jar while the client is running (corrupts the live JVM's lazy
  class loading).

## Known-good reference plugins (crib from these)

- `agility/AgilityScript.java` — mark-of-grace handling: reachability filter, sticky pickup,
  failure blocklist, settled-scan gate. The canonical transient-target implementation.
- `kittentracker/` — blocking events with backoff, verified follower calls, thread-safe actor reads.
- `qualityoflife/QoLScript.java` — dialogue matching (`Rs2Dialogue.hasQuestion` on title text so it
  never fires on unrelated Yes/No prompts), config-gated handlers in a fixed-delay loop.
- `housetab/` — POH/house interaction flows.

## Verification loop after changes

1. Compile → build jar → deploy → FULL client restart.
2. Confirm the new jar actually loaded: `~/.runelite/logs/client.log` — the
   `Hash mismatch for plugin X: local=<hash>` line changes when the jar changed;
   `Plugin loaded X` timestamps mark restarts.
3. Add targeted `Microbot.log("[Tag] ...")` diagnostics on decision points before guessing at causes.
