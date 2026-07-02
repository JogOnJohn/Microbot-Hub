# HouseTab Development Notes

HouseTab started as a small lectern helper and grew into a multi-step tablet crafting script. That growth was reasonable: the early version only needed to open a lectern and select a tablet. The later version needed to handle advertised houses, Phials, progressive tablet selection, equipment and rune coverage, GE banking, bad-host recovery, startup safety, and live object/widget data. Once those requirements landed, the script became a state-machine problem.

These notes document what was learned while building and live-testing the plugin, and what should be improved if this is prepared for a wider PR.

## What the script does today

The current plugin supports two broad modes:

- Single tablet mode: make the configured tablet repeatedly.
- Progressive mode: choose the highest supported teleport tablet by Magic XP for the player's level, prepare the matching staff/runes/materials, and continue crafting as the player levels.

The main loop handles:

- Rimmington advertisement-board entry.
- Last-house entry where available.
- Fallback to advertised-house list selection when the last host is unavailable.
- Phials unnoting with noted soft clay.
- Lectern discovery and tablet selection.
- Scrolling the lectern interface when the target tablet is below the visible list.
- POH jewelry-box travel to the Grand Exchange for progressive setup.
- Banking crafted tablets when switching progressive tiers.
- Restocking law runes and soft clay from bank supplies.
- Stopping gracefully when required bank supplies are unavailable.
- Recovering from houses that do not have a nearby compatible lectern.

## What we learned

### Live IDs beat assumptions

The most important lesson was that generated constants are not always enough. The live marble lectern object was `37349`, while the script initially mixed generated `gameval.ObjectID` constants that did not cover that ID in the expected namespace. The result was a valid visible lectern that the script treated as missing.

For object/widget-heavy scripts, gather and record live IDs early:

- Object IDs for every target object and fallback object.
- Widget IDs for the target interface, quantity controls, scrollable containers, and confirm buttons.
- Menu actions that are actually exposed by the client.
- Any alternative IDs used by upgraded or visually similar objects.

### State must be explicit

Several bugs came from implicit state. The script inferred "inside house" from a mix of portal visibility, lectern visibility, coordinates, and boolean flags. That worked while the script was small, but it became fragile once progressive setup and bad-house recovery were added.

The recurring failures were symptoms of unclear state boundaries:

- Being inside a house but still showing status `View House Advertisement`.
- Treating "lectern not detected yet" as "bad house."
- Running bad-house recovery while the lectern interface or crafting flow was active.
- Progressive setup interrupting or bypassing the shared crafting flow.
- Startup/login timing creating partial state before the local player was safe to read.

### Shared flow matters

Single mode and progressive mode should share the same house-entry, lectern, crafting, leaving, and unnoting helpers. Duplicated mode-specific branches made fixes land in one mode but not the other. The refactor toward a shared crafting loop improved this, but the remaining control flow is still more conditional than ideal.

### Recovery needs evidence

Bad-house recovery is useful, but it should only fire after strong evidence:

- Player is actually inside a POH scene.
- No compatible lectern is visible after a reasonable scene-load window.
- The lectern interface is not open.
- The player is not currently crafting or gaining XP.
- The script is not in a transition such as entering, leaving, or teleporting.

Without those guards, recovery can turn a slow scene/cache update into a false failure.

### Logs should describe transitions

Steady-state logs should be debug-level, but transition logs are valuable. The useful logs were the ones that answered:

- What state did the script enter?
- What evidence caused that state?
- What action did it attempt?
- What condition is it waiting for?
- Why did it stop or recover?

The least useful logs were repeated loop messages that did not explain a transition.

## Would a state machine have been better?

Yes. Once progressive mode was added, the script would have been easier to build and debug if it had started from the Microbot state-machine example.

A state machine would not have solved missing live IDs by itself, but it would have made state transitions and stall points much clearer. The script would have had a single current state instead of status text and boolean flags loosely describing the same thing.

A better future shape would look like this:

```text
STARTING
VALIDATE_LOGIN
VALIDATE_WORLD
SNAPSHOT_STATE
SELECT_TABLET
CHECK_LOADOUT
GO_GE
BANK_SETUP
RETURN_RIMMINGTON
UNNOTE_CLAY
OPEN_ADVERTISEMENT_BOARD
SELECT_ADVERTISED_HOUSE
ENTER_HOUSE
WAIT_FOR_HOUSE_SCENE
FIND_LECTERN
OPEN_LECTERN
SELECT_TABLET_WIDGET
CRAFT_TABLETS
LEAVE_HOUSE
RECOVER_BAD_HOUSE
STOPPED
```

Each state should have:

- Entry logging.
- A clear success transition.
- A timeout transition.
- A failure/stop reason.
- A small set of allowed actions.

## Future improvements

### Refactor into an explicit state machine

Move the current loop into a `HouseTabState` enum and a dispatcher. This is the highest-value structural improvement.

Suggested supporting classes:

- `HouseTabState`: the current workflow state.
- `HouseTabSnapshot`: current location, visible objects, inventory, equipment, widgets, and config-derived target.
- `HouseTabPlanner`: resolves selected tablet and required loadout.
- `HouseTabActions`: small wrappers for clicking board, entering house, studying lectern, banking, unnoting, and teleporting.
- `HouseTabRecovery`: handles bad hosts, timeouts, missing supplies, and fallback paths.

### Centralize scene detection

Create one method that answers:

- Is the player logged in and safe to read?
- Is the player at the GE?
- Is the player near Rimmington/Phials/advertisement board?
- Is the player inside a POH instance?
- Is the house portal visible?
- Is a compatible lectern visible?
- Is the lectern interface open?
- Is crafting active?

All states should consume that snapshot instead of each branch making its own partial checks.

### Improve startup safety

The plugin already waits for a stable logged-in state, but startup should be stricter because other Microbot plugins can also read local player state early.

Future startup checks should include:

- `GameState.LOGGED_IN`.
- `Microbot.isLoggedIn()`.
- Non-null local player.
- Non-null world location.
- Stable scene for several ticks.
- No welcome screen.
- Optional delay after profile/world change.

### Make live data collection easier

Add an explicit diagnostic mode that dumps:

- Nearby objects by ID/name/action.
- Current widgets for the lectern and advertisement board.
- Current inventory and equipment summary.
- Current state snapshot.
- Last state transition and timeout reason.

This should be debug-only and should not spam normal info logs.

### Make host selection more robust

Advertised houses change constantly. Better host selection could:

- Prefer top visible listings.
- Blacklist failed hosts for the current run.
- Track hosts with confirmed compatible lecterns.
- Retry the board after a host goes offline.
- Avoid houses where the lectern is not nearby.
- Optionally maintain a short in-session "known good host" cache.

### Improve banking and material planning

Progressive mode should keep doing no GE buying/selling unless explicitly enabled, but bank setup can still improve:

- Cache whether required staves are present in the bank.
- Cache whether law runes and soft clay are present.
- Bank crafted output whenever changing tablet tier.
- Preserve house tablets as the unstuck mechanism.
- Stop cleanly with a precise missing item reason.

### Improve overlay data

The overlay should continue to show task, status, Magic level, XP gained, and tablets made. Future fields could include:

- Current state.
- Selected host.
- Current tablet tier.
- Clay remaining.
- Runtime.
- Last recovery reason.
- Supplies missing when stopped.

Profit tracking can remain out of scope unless the plugin starts handling buying/selling.

### Add tests for planner logic

Most UI and object interaction needs live smoke testing, but pure logic can be tested:

- Progressive tablet ordering by XP.
- Watchtower exclusion.
- Staff coverage for each tablet.
- Rune requirements.
- Bank-prep decisions.
- Stop reasons for missing supplies.
- Host blacklist behavior.

### PR direction

If this is prepared for a PR, the strongest pitch is not just "add a HouseTab script." It is:

- Adds a useful tablet-making plugin.
- Documents live-tested IDs and edge cases.
- Demonstrates a pattern for stateful POH workflows.
- Improves the original small plugin into a maintainable progressive workflow.

Before PR, the best cleanup would be the explicit state-machine refactor. The current implementation works, but a state machine would make it easier for reviewers to reason about recovery, timeouts, and mode-specific behavior.
