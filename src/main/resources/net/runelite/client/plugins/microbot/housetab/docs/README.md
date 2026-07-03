# HouseTab

HouseTab crafts magic tablets at a player-owned house lectern. It supports fixed single-tablet crafting and a progressive mode that moves through teleport tablets by Magic XP as the account levels.

## Current version

Version `2.0.8` is the current version.

The 2.0 line changed the plugin from a small lectern helper into a stateful tablet-making workflow with:

- Explicit workflow states and transition logging.
- A shared scene/material snapshot.
- Single-tablet and progressive modes using the same core crafting flow.
- Advertisement-board host selection.
- Bad-host recovery and per-run host blacklist.
- Phials unnoting.
- Progressive bank setup at the Grand Exchange.
- Overlay status for state, host, clay, materials, runtime data, and recovery reason.
- Planner tests for progressive ordering, material decisions, and lectern compatibility.

## Setup

Recommended setup:

- Start on world `330`.
- Use the Rimmington house advertisement board.
- Keep noted soft clay, law runes, and teleport-to-house tablets available.
- Keep the required combination battlestaves in the bank for progressive mode.
- Keep the house advertisement board mode enabled unless intentionally using a named friend house.

The plugin assumes hosted houses for normal operation. It can use an own house or named friend house fallback, but the default and best-tested path is the advertisement board.

## Modes

### Single tablet mode

Single mode repeatedly makes the tablet selected in the plugin settings.

Use this when:

- You only want one tablet type.
- You are targeting a profitable or easy-to-sell tablet.
- You do not want the plugin changing staff/tablet tiers as Magic level changes.

### Progressive mode

Progressive mode selects the highest supported teleport tablet by Magic XP that the player can currently make. It is XP-sorted, not profit-sorted.

When the player unlocks a higher supported tablet, progressive mode should:

- Finish the current inventory when already crafting.
- Travel to the Grand Exchange through a hosted house jewellery box when bank setup is needed.
- Bank crafted output from the previous tablet tier.
- Keep teleport-to-house tablets as the recovery mechanism.
- Withdraw/restock required supplies from the bank.
- Equip or withdraw the required staff where applicable.
- Return to Rimmington and continue crafting.

The plugin does not buy or sell on the Grand Exchange. If law runes or soft clay run out and the bank does not contain more, the script stops with a missing-material reason.

## Progressive tablet order

Progressive mode currently follows this order as the player unlocks tablets:

```text
Teleport to House
Varrock Teleport
Lumbridge Teleport
Falador Teleport
Camelot Teleport
Kourend Castle Teleport
Ardougne Teleport
Civitas illa Fortis Teleport
Teleport to Boat
```

Watchtower teleport tablets are intentionally skipped because their trade volume is too low for this workflow.

## Staff and rune handling

The plugin prefers combination battlestaves when configured to do so. A staff is considered valid when it covers the elemental rune requirements for the selected tablet. Law runes are still required in inventory or bank supplies.

Examples:

- Camelot tablets can use any staff that covers the air rune requirement.
- Kourend Castle tablets require steam coverage.
- Teleport to Boat tablets require mud coverage.

If combination staff mode is disabled or a suitable staff is not available, the plugin can fall back to rune coverage where configured and available.

## House entry behavior

Advertisement-board entry is the default path.

Relevant settings:

- `Use advertisement board`: use the Rimmington house advertisement board for hosted houses. This defaults to enabled.
- `Use last house`: use the board's visit-last option when available. This does not mean the Rimmington portal's friend-house option. The stored config key is advertisement-board specific so old friend-house fallback settings do not affect it.
- `Advertised houses`: optional comma-separated host names to prefer. If none match, the plugin uses the best available listing from the board.
- `Player Name`: fallback friend-house name for the old portal flow when advertisement-board mode is disabled.

When advertisement-board mode is enabled, the plugin should not fall through to the Rimmington portal `Friend's house` action.

## Recovery behavior

The plugin can recover from common hosted-house problems:

- Host went offline.
- Entered house has no nearby compatible lectern.
- House scene is slow to load.
- Previously selected advertised host fails.

When a hosted house is bad, the plugin blacklists that host for the current run, breaks a teleport-to-house tablet where available, returns outside, and tries the next advertisement-board listing.

## Logging and diagnostics

Normal transition logs are kept at info level because they explain meaningful state changes.

Steady-state loop noise should stay at debug level. Enable `Debug diagnostics` only when actively debugging; it logs state snapshots, object visibility, material summaries, known-good hosts, blacklisted hosts, and recovery reasons.

`Debug widget dump` logs the lectern interface widget tree once when the lectern interface opens. Use it only when widget IDs or scroll behavior need to be checked.

## What we learned

### Live IDs beat assumptions

Object and widget constants are useful, but live data still matters. The lectern, advertisement board, quantity controls, scroll containers, and tablet widgets all needed live confirmation.

For object/widget-heavy scripts, collect:

- Object IDs for every target and fallback object.
- Widget IDs for the target interface and important child widgets.
- Menu actions exposed by the client.
- Alternative IDs used by upgraded or visually similar objects.

### State must be explicit

Most serious bugs came from implicit state:

- Being inside a house while status still said advertisement-board entry.
- Treating a slow lectern cache update as a bad house.
- Recovering while the lectern interface or crafting flow was active.
- Progressive setup interrupting shared crafting behavior.
- Startup/login timing reading player state too early.

The 2.0 refactor added explicit states and snapshots because this script is naturally a state machine.

### Shared flow matters

Single mode and progressive mode should keep sharing the same helpers for:

- House entry.
- Lectern detection.
- Tablet selection.
- Crafting.
- Leaving the house.
- Phials unnoting.
- Bad-host recovery.

Duplicating these paths caused earlier fixes to work in one mode but not the other.

### Recovery needs evidence

Bad-house recovery should only run after strong evidence:

- The player is actually inside a POH scene.
- No compatible lectern is visible after a scene-load window.
- The lectern interface is not open.
- The player is not crafting or gaining XP.
- The script is not already entering, leaving, or teleporting.

Without those guards, a slow scene/cache update can look like a failure.

## Future improvements

The highest-value next improvements are:

- Move the remaining loop branching into a stricter state dispatcher.
- Add more tests around host blacklist and material-stop reasons.
- Cache bank supply checks more deliberately during progressive setup.
- Improve world validation and world-hop handling for world `330`.
- Add an optional live diagnostic dump for nearby objects and open widgets from the overlay/config.
- Add richer overlay counters, especially per-tablet output counts.
- Keep GE buying/selling out of scope unless explicitly added as a separate feature.

The current implementation is working and live-tested, but the long-term maintainable shape is still a stricter state machine with small action methods and explicit timeout transitions.
