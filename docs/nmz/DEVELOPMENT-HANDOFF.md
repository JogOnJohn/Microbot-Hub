# NMZ Development Handoff

## Source

- Repository: `JogOnJohn/Microbot-Hub`
- Branch: `feat/nmz-prayer-seeding`
- Baseline: `origin/main` at `1ed5e63`
- Plugin: `[MB] Nmz` / `NmzPlugin`
- Plugin version: `2.4.1`
- Host worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-nmz`
- Operator manifest: `F:\vmware boxs\MBOT\operator-work\plugins\nmz.psd1`
- Full local handoff: `F:\vmware boxs\MBOT\docs\handoffs\nmz\NMZ-HANDOFF.md`

## Starting Evidence

The current runtime is inside NMZ with an active/enabled plugin. The installed
JAR hash is `1D9C839A16FFE4E939E04A2203BFCD792D0448A1DCC4D318F72B3D9A670A124E`.
The inventory contains Ancient mace item `11061` and prayer potions.

`NmzScript` schedules a new `PrayerPotionScript` every in-instance NMZ tick;
each call schedules a fixed-delay worker. This is the first defect to isolate
and remove. The generic log does not currently include NMZ state transitions,
so add focused state/action diagnostics before diagnosing overlay labels.

## Planned Sequence

1. Replace competing prayer workers with one owned controller.
2. Add transaction acknowledgement and dose-agnostic prayer potion selection.
3. Add a separate prayer-seeding profile for an existing dream.
4. Add manually configured special weapon handling, initially Ancient mace.
5. Add concise diagnostics and overlay state.
6. Validate in-instance prayer seeding before entry/lobby automation.

No client lifecycle or JAR changes were made while this package was created.
