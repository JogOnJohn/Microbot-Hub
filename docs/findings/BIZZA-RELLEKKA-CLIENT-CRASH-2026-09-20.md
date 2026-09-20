# Bizza Rellekka client crash (2026-09-20)

## Scope

This report covers read-only diagnostics of the Bizza 12345 runtime and host-only
source/build work. No guest source, configuration, plugin JAR, VM, or client
lifecycle state was changed.

## Result

The two confirmed failures are the same injected-game-client defect while the
account was running the Rellekka rooftop course. They are not exceptions thrown
by the Agility plugin.

- 2026-09-16 16:01:28 AEST: `Index 108 out of bounds for length 105`
- 2026-09-20 14:55:36 AEST: `Index 109 out of bounds for length 105`
- Both stacks start at `pt.ae -> ec.az -> dz.au` and continue through
  `kc.ce -> ho.cv -> dl.cr` on the client thread.
- In the exact RuneLite 1.12.39 injected-client artifact, `dz` implements
  `WorldView`, `dn` implements `NPC`, and `pt.ae` indexes the world-view
  `int[][][]` tile-height grid. The failing 108/109 index exceeds its 105-cell
  dimension while the engine is calculating an NPC's height.
- `ho.cv` iterates NPCs and calls the terrain-height path with NPC coordinates.
  This is the throwing code. The first Agility timeout occurs ten seconds later
  in `ClientThread.runOnClientThreadOptional -> Rs2Player.getAnimation` because
  the client thread has already stopped servicing work.
- The same timeout flood affects QoL, Wintertodt, AutoPrayer, PotionManager,
  SpecialAttack, BreakHandler, Cannon, Bankpin, and AutoRun. Those are downstream
  symptoms, not independent root causes.

The Agility logs place both incidents on the Rellekka rooftop course. Before the
September 16 failure, marks were collected around `WorldPoint(2623..2642,
3651..3675, 3)`. Before the September 20 failure, a mark was collected at
`WorldPoint(2641,3650,3)`. `Waiting for settled mark scan` is normal plugin flow
seen repeatedly across completed laps; it does not appear in the throwing stack.

## Runtime and artifact provenance

- Runtime startup: RuneLite `1.12.39`, injected-client build
  `35108282871.259`, Microbot `2.6.22-nogit`, Java `17.0.19+10`.
- Installed guest JAR:
  `C:\Users\VMAdmin2\.runelite\microbot-plugins\MicroAgilityPlugin.jar`
- Installed JAR SHA-256:
  `64211649B1FEC0264B40CC9E8CA9EBBE7DB144B58B23E5E4A1E7E385EBD7A341`
- The plugin manager reports that hash differs from the current authoritative
  Hub artifact. Earlier class-by-class comparison found the main plugin class
  matching a host build but `AgilityScript`, `AgilityCourseHandler`, and
  `SeersCourse` differing. Exact installed-JAR source authorship remains
  unproven.
- Host RuneLite 1.12.39 injected-client SHA-256:
  `25F42961C400BD9DFFF1554402441C0BA6D1CFFD011163CB9B0B4C42AE194F85`.
  It has the exact failing bytecode described above.

## Upstream audit and validation

- Agility base: `d25a5f3f37e2fdd35c208effa973171cac923711`
- Fresh upstream/main: `2eb8c8916b7e4097af778fde434234fbee81145d`
- Before integration: 62 commits ahead, 7 behind; the seven upstream commits
  did not touch the Agility package.
- Host-only integration merge: `96dc24d` on
  `codex/agility-client-crash-fix`.
- Focused `compileAgilityJava --rerun-tasks`: passed against Microbot `2.6.23`.
- `MicroAgilityPluginJar` reached compilation but the repository-wide
  `compileJava` task failed in unrelated BankTabSorter, FarmingContract, and
  Tempoross sources. No Agility compile error was reported.

RuneLite's published release remains `1.12.39`. The available
`1.12.40-20260916.145216-2` snapshot contains the same unguarded height-grid
bytecode and therefore is not a fix.

## Fix decision

No Agility source change is justified by the evidence. Mark filtering, stale
obstacle coordinates, and `shouldClickObstacle` do not execute in the throwing
path. Catching plugin exceptions or suppressing timeouts would only hide the
post-failure symptoms. A Rellekka-specific plugin stop would also leave the
client standing in the same scene and would not prevent the NPC update.

The durable fix belongs in the injected game client (bounds-safe NPC terrain
height sampling) or a later stable injected-client revision containing that
fix. Patching obfuscated bytecode locally is possible but is version-fragile and
is not a supported Hub-plugin correction. Until a corrected stable client is
available, avoiding the Rellekka rooftop course is the only evidence-backed
operational mitigation.

## Confidence and limitations

- High confidence: initiating exception, failing data structure, NPC update
  path, and downstream nature of plugin timeouts.
- High confidence: the installed Agility JAR is not provenance-identical to the
  current authoritative artifact.
- Medium confidence: Rellekka scene/NPC boundary conditions are the location
  trigger. Both known events occurred there, but the logs do not identify the
  specific NPC or explain why its coordinate remained outside the height grid.
- No live reproduction or deployment was performed.
