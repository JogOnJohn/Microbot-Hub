# Guardians of the Rift Plugin Rules

## State-Machine Invariants

- Reset static round state when the plugin starts; do not inherit a previous enable cycle.
- Ignore main-arena objects and actions while an altar scene is still loading.
- Inside the game area, a usable equipped or inventory pickaxe bypasses bank preparation.
- Outside the game area, obtain the best usable pickaxe and opportunistically obtain a chisel and level-eligible pouches.
- Only absence of a usable pickaxe is fatal. Missing optional supplies must not block the script.
- Prefer a colossal pouch when usable and present; otherwise use eligible smaller pouches.
- Prioritize contribution, cell repair and rune deposit before portal/mining work.
- Keep short arena moves local where the web walker can mistake active portals for route doors.

## Runtime Diagnosis

Correlate state with location, scene, movement, animation, interaction,
destination, inventory, guardian power and portal timing. Do not call one delayed
transition a stall without showing that all of those signals remained unchanged.

Useful log signals include:

- `GOTR Plugin started`
- `GOTR mass config`
- `Preparing GOTR supplies`
- `GOTR startup supplies ready`
- `No usable pickaxe`
- `Entering game`
- `Traveling to large mine`
- `Rift closed`
- `Something went wrong in the GOTR Script`

Do not log every 100 ms scheduler iteration. Add logs on state changes, bounded
retries and confirmed action outcomes.
