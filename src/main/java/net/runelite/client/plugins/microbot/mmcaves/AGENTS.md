# MM Caves Plugin Scope

This package owns `MmCavesPlugin` only. Keep changes inside this package,
focused tests, and `docs/mm-caves` unless a shared API change is necessary and
well justified.

- Treat coordinates, object IDs, NPC names, world/region/plane checks, and
  interaction names as unverified until captured from a current live run.
- Do not use unbounded world-hop loops, blind sleep chains, or direct logout as
  generic recovery. Every action requires confirmation, deadline, retry bound,
  and a visible stop reason.
- Track cave occupancy by world and generation; do not apply a stale chat result
  to a new world.
- Keep magic/ranged preflight explicit and fail safely. Do not silently alter
  autocast, prayer, inventory, ground items, or equipment without confirmation.
- A failed preflight must disable/stop safely without moving, dropping items,
  hopping, or logging out unless the user configures that behavior.
- Before replacement, prove source branch/version, jar hash, installed jar hash,
  process state, and live behavior separately.
