# Blackjack Development Handoff

Last refreshed: 2026-08-13 (Australia/Sydney)

## Source

- Repository: `JogOnJohn/Microbot-Hub`
- Branch: `feat/blackjacking-plugin`
- Checkpoint: `234f7f60378858da2578cc8d32e2ee5cd60013a3`
- Plugin version: `1.1.5`
- Host worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-blackjacking`
- Runtime VM: newer `Bizza 12345`, guest `clanker\vmadmin2`
- Operator manifest: `F:\vmware boxs\MBOT\operator-work\plugins\blackjack.psd1`
- Full local handoff: `F:\vmware boxs\MBOT\docs\handoffs\blackjack\BLACKJACK-HANDOFF.md`

Read the repository and plugin `AGENTS.md` files before acting. Inspect Git status
and preserve any dirty state before fetching, editing, building, or switching.

## Artifact Identity

Known-good installed Bizza artifact:

```text
Path: C:\Users\VMAdmin2\.runelite\microbot-plugins\BlackjackPlugin.jar
Behavioral version: 1.1.4
SHA-256: 3E740D752A58A0578CA1F2750573FE6E105BDD09CB129ADE863BB0904BB3372C
```

That older JAR incorrectly reports `Implementation-Version: 1.0.0` due to the
descriptor parser defect fixed on this branch.

Clean host-only development build, not installed at handoff time:

```text
Version: 1.1.5
Build commit: 234f7f603788
Build dirty: false
SHA-256: A0EE54583253581C61618C5810E93A6F818F17263C3107B82C9BC2CD3882C175
```

Version 1.1.5 logs its version, branch, commit, dirty state, loaded source and
exact loaded JAR SHA-256 at startup. The focused build passed; Gradle's test task
completed but discovered no tests.

## Current Behavior

- Level-appropriate target selection in the supported Pollnivneach house.
- Verified target-specific right-click `Knock-Out` selection.
- Event-first success/failure handling with a short bounded fallback.
- Two-pickpocket bursts and pre-armed follow-up Knock-Out timing.
- Convex-hull-constrained anchors and burst wandering.
- Absolute-HP healing priority.
- Staged combat reset through `3359,2995,0`, then `3360,2993,0`.
- Projected wine depletion and door-secured wine restocking states.
- Bounded timing, mouse, menu-mistake and break variation.
- Expanded diagnostics and JOJ plugin identity.

## Safety Invariants

- Never drop empty jugs; a full inventory protects failed pickpockets from combat.
- Healing outranks blackjacking and humanizer behavior.
- Never infer combat from damage alone.
- Never infer a menu miss from menu disappearance alone.
- Keep cursor wandering inside the live target hull.
- Do not issue another Knock-Out while the target is unconscious or the current
  burst remains unresolved.
- Do not use a large fixed delay to solve timing races.
- Do not alter the client lifecycle without current user authorization.

## Outstanding Validation

The latest unresolved observation is an occasional second Knock-Out even when
the first succeeds. Reproduce against a proven installed hash and separate:

- two actual menu dispatches;
- duplicate logging/accounting;
- a pre-armed click surviving a success signal;
- fallback timeout racing success detection; and
- unconscious animation arriving after another command was queued.

Wine restocking and the broader humanizer state machine also require deliberate
end-to-end live validation before being called production-stable.
