# AutoHunter Plugin Rules

## Scope

- This plugin currently supports box traps only. The target first profile is
  red chinchompas; do not broaden it into a generic Hunter rewrite.
- Do not add automatic banking, dropping, wilderness handling, or NPC-killing
  methods without a separate design and live validation.

## State-Machine Rules

- Treat action completion as an observed state transition, not a fixed sleep.
- Perform one trap interaction per scheduler pass and wait only for a bounded,
  observable result before retrying or recovering.
- Manage only traps adopted at startup or created/recovered by this script.
  Never interact with arbitrary nearby traps after startup.
- Derive the normal box-trap limit from Hunter level. Keep the black-chin
  wilderness extra-trap rule out of the red-chin first pass.
- A full red-chinchompa inventory is a safe stop condition. Never drop catches
  to make room unless an explicit policy says otherwise.

## Spawn-Ring Rules

- Learn candidate respawn tiles from target `NpcSpawned` events, not a static
  coordinate list. De-duplicate by world tile, score recent reappearances, and
  expire stale entries.
- Ignore scene-load spawn noise until a stable baseline is established. Surface
  candidate tiles in diagnostics before using them to reposition traps.
- Generate ring tiles only after checking collision, reachability, occupancy,
  player position, and existing managed traps. Preserve the Hunter trap limit.
- Stray-target killing is disabled by default and separate from trap handling.
  It needs an explicit weapon, line-of-sight, combat, and pending-trap-action
  guard. Never assume Hunter's spear or a specific red-chin area.

## Diagnostics And Validation

- Log state changes, interaction dispatches, confirmations, bounded timeouts,
  and stop reasons. Do not log each scheduler pulse.
- The overlay should report state, next action, managed/active traps, catches,
  free inventory slots, and a stop reason when relevant.
- Before relying on an object ID or action, collect it live for empty, caught,
  escaped, and fallen traps. Object IDs/actions in the existing script are not
  proof that the red-chin states are complete.
- Compile before any install. A source build does not authorise a JAR replacement
  or client lifecycle action.
