# Bandit Target Audit

Review date: 2026-10-18. Baseline: Blackjack 1.1.26 (`b3635ab2`).

## Corrected Findings

- Both Bandit wine routes searched for Curtain after the Thug work. The original Bandit implementation (`234f7f6`) used Door near its east exit. Restore that name, retain live Open/Close actions and the existing location tolerance, and reject a different plane. This shared lookup also serves scheduled-break exits.
- Bandit acquisition could run with the player outside, and did not close an open door after initial arrival. Return inside first and confirm the east door is closed before clicking. No automatic Bandit luring is implemented.
- AUTO re-evaluated level on each call, potentially changing NPC type/house mid-fight or mid-wine trip at level 55/70. Pin the first populated level-based choice for each enable session. Explicit choices remain available at higher player levels.
- The bounded zoom/approach recovery was Thug-only. Apply it to an eligible target and player inside either supported house. Bandit approach stays local; Thug inner-curtain handling is unchanged. Disarmed recovery remains camera-only and bounded.
- Standalone Entry Swap matched every NPC named Bandit. Restrict it to the two supported combat levels and test both standing/unconscious menu transitions, an open menu, missing actions and overlapping NPC entries.

## Behaviour Retained

- Bandit level 41 requires 45 Thieving; level 56 requires 55. AUTO selects 41 below 55, 56 below 70, and Thugs from 70; this is now the initial selection, not an automatic mid-session migration.
- Both Bandit modes require the selected NPC already isolated in the northern house: x3357-3360, y2991-2995, plane 0. Main point 3358,2993; east door inside/outside points 3360,2993 and 3362,2993.
- Shared continuous left clicks, animation-based Entry Swap, healing priority, full-inventory policy, noted-wine exchange and humanizer scheduling are retained.
- Bandit combat fallback still stages at 3359,2995 and then 3360,2993. Bandit scheduled-break preparation closes the east door and waits outside; it does not use the pub stairs.
- Startup inventory validation discussed earlier is not implemented by this audit.

## PR Gates

Offline compilation/tests are not live gameplay proof. For EACH Bandit mode, validate:

1. Equip blackjack, fill inventory with wine/coins/notes, isolate the correct Bandit, and start inside with the east door open. Confirm closure, correct target selection and repeated Knock-Out/Pickpocket cycles.
2. Start outside that house. Confirm entry, closure and acquisition; wrong-level/outside-house NPCs must not be selected.
3. Put the NPC off-screen. Confirm bounded camera recovery and local approach without walking outside or moving the cursor beyond its hull.
4. Trigger a combat reset: test disarming with a full inventory, re-equip/recover owned wine, safespot fallback if needed and successful resumption.
5. Complete wine exit, close door, unnote all, return, close door and resume at preferred zoom. Empty jugs must only be dropped outside.
6. Request a scheduled break and resume. Confirm no Thug stairs/lure phases occur and the retained Bandit remains enclosed.

The current build uses a custom client supplying `BreakPreparation` and the newer Menu API. Inspected locally cached Maven artifacts 2.6.26 and 2.6.31 both contain Menu but neither contains BreakPreparation; this is not a check of the latest upstream release. The descriptor still advertises minClientVersion 2.1.0, which does not establish compatibility. Before opening an upstream PR, verify the intended public client supplies these APIs, compile against that exact artifact, and set an accurate minimum version (or separately adapt the dependency). Do not remove break safety merely to make an older client compile.

This audit does not create/push a PR, install a normal JAR or change the live client. Live Bandit validation and public-client compatibility remain outstanding.
