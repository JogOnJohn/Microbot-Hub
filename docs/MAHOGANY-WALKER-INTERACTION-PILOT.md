# Mahogany Homes interaction-aware walker pilot

Mahogany Homes 1.0.20, branch `codex/mahogany-interaction-api-20261020`.
Requires the paired client from `spike/shortest-path-data-sync`; an older custom
client may have the same Microbot version without implementing this API.
This is not a playable merge or an upstream release.

## Configuration

Group `mahoganyhomes`:

- `interactionAwareTurnIn`: off by default. Submits homeowner ID, expected home,
  `Talk-to`, and a read-only confirmation to the bank-aware walker overload.
- `interactionAwareFurniture`: off by default. Submits exact mapped objects,
  Remove/Build/Repair actions, access points and hotspot-effect confirmation.
  Unmapped furniture keeps the existing execution path.

The walker owns approach, closed-crossing handling, guarded input, bounded retry
and observation. Homes retains contract selection, Astral Contact, dialogue
choices, hotspot ordering, supplies, plank-sack handling and completion counting.
Neither proximity nor accepted input counts as a completed interaction.

## Lifecycle integration

Retain one request across script ticks. Poll pending hand-ins before continuing
dialogue; poll furniture before selecting another work item. Do not transition
to hand-in/DONE while an owned request remains pending, even if the final
hotspot/chat event arrives between ticks. Cancel requests on shutdown/reset.
Bind the first NPC poll to its anchor; its destination is not set until binding.

For furniture, the live footprint must also have a reachable, unblocked cardinal
interaction edge. A mapped approach tile alone is not proof that an intervening
door is open. Existing route staircase execution is not replaced by this pilot.

## Validation

- 62 focused Homes tests passed, including first/final state-transition guards.
- Paired client full suite: 2,432 tests, zero failures/errors, six skips.
- Preliminary EXACT hand-in cohort completed Norman, Noella, Jess, Barbara and
  Larry consecutively. The final collision-hardened cohort is recorded separately
  in the operator artifact directory `walker-interaction-api-20261020`.
- A hot-reload-interrupted Astral Contact failure was preserved rather than
  counted as success. Full client restart restored progression.
- Final cohort results and scope limitations belong in the operator report;
  do not infer an A/B speedup from unmatched houses or XP/hour.

## Rollback

Both settings can disable the opt-in adapters on the paired client. To restore
the old runtime entirely, close the client and restore the previous client and
Homes JARs together; do not run a watched JAR alongside the normal sideload.
`checkpoint/mahogany-before-interaction-20261020` preserves Homes 1.0.19 source.
`checkpoint/data-sync-before-interaction-20261020` preserves the client base.
`checkpoint/walker-before-interaction-api-20261020` preserves the later pre-API
client state with the validated fluidity fixes and selective upstream data port.
Playable is unchanged until the pilot is accepted.

## Follow-up 1.0.21

The frozen 1.0.20 pair completed six new houses (three EXACT, three LEGACY), with
six confirmed NPC hand-ins and eight mapped-furniture effects, all one attempt.
Barbara later failed on its old unmapped bed path outside the north wall.
Version 1.0.21 supplies the interior south-side access/recovery target for that
bed. All 63 focused tests pass against client d56349e3ea. The later five-house
cohort (two EXACT, three LEGACY) confirmed both Barbara bed effects and five NPC
hand-ins, all first attempt. Barbara completed at 14:23:35 AEDT. The initial
Remove was issued five tiles away and the observed effect occurred inside.
Do not extrapolate this bounded smoke to the full controlled efficiency matrix
or merge playable yet. A separate shared-planner refresh cancellation bug found
on the preceding build is fixed in d56349e3ea; it was not a missing Homes supply.
