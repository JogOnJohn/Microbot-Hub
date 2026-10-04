# Southern Tent Lure Review - 2026-10-04

Source checkpoint before review: `b44d5ca8983f`. Plugin version: 1.1.9.

## Fixed Findings

- P1: The curtain threshold at (3350,2957,0) satisfied `!inside`, so eviction could close the curtain with the thug still in the doorway. Crossing and closing now require both actors on the intended side. Closing rechecks their positions, and release also checks the thug remains outside with the outer curtain closed.
- P1: Walking continued after the follow signal disappeared. Route actions now wait for a nearby follower and retry Lure after a bounded missing-signal interval. Retries approach the current target location again. The target's NPC identity is retained as well as its index so a recycled index cannot silently select another thug.
- P1: Visible curtains returned before their timeout checks, and dialogue disappearance before the first Continue left the script waiting until the entire preparation failed. Phase deadlines now run before every handler. Disappeared dialogue proceeds to bounded follow verification; a missing follow sample resets the confirmation streak instead of instantly reissuing Lure.
- P1: Healing left preparation and subsequently attempted normal target acquisition outside. Preparation now heals in place, retries an interrupted dialogue, and retains its route state. Acquisition explicitly requires the player to be inside the tent.
- P2: Direct canvas walking did not open the inner curtain for an extra thug in the rear room. Inter-room routes now open only the exact inner curtain. Its closure remains optional.
- P2: Both the base script and the web-walker fallback could re-enable running after the single walk toggle. The confirmed lure temporarily suppresses base-script auto-run, restores the prior runtime/persisted preference on completion, recovery, failure or shutdown, and uses bounded canvas/minimap attempts. Pre-lure approach and independent transit retain the web-walker fallback.

NPCs occupied by other actors are excluded from new lure selection, and unconscious extras are allowed to stand before Lure. Villagers and street urchins remain ignored.

## Validation Boundary

Build/test with the custom Microbot 2.6.26 / RuneLite 1.13.1 artifact recorded in `operator-work/output/audits/hub-active-sync-20261003/blackjacking-artifact.json` in the operator workspace. The public 2.6.26 client lacks an InventorySetup API needed by the unrelated Dro KBD source in this branch; do not skip global compilation to conceal that mismatch.

Validation passed: normal global compilation, `BlackjackPluginJar`, and 10 JUnit tests (zero failures/errors/skips) against that custom client. `SouthernTentLureSafetyTest` covers entrance crossing, rear-room geometry, inner-curtain routing, follow proximity/plane checks, and bounded phase deadlines. `SouthernTentPreparationTest` exercises handler timeout recovery before client access, safe release timeout, and restoration of the original auto-run preference. Dialogue actions, following, run toggles, and curtain interaction still require a live session.

Live checks, in order:

1. Start with one thug inside: enter if necessary, secure outer curtain, begin blackjacking without Lure.
2. Start with an empty tent: complete Lure, lead both actors fully inside, close outer curtain.
3. Start with two thugs, including an extra in the rear room: open inner curtain if needed, lead one outside, close outer curtain, confirm release, return to the retained thug.
4. Interrupt dialogue with healing, lose follow mid-route, close the outer curtain mid-crossing, and let the target move back onto the threshold. Confirm bounded recovery rather than false completion.
5. Confirm wine exchange and return still resume blackjacking with one thug inside.

The host hot-reload scheduled task was stopped before editing. This review does not install or reload a guest artifact.
