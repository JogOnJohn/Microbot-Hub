# Giants' Foundry Upgrade Handoff

Read this before continuing work on the Giants' Foundry plugin.

## Current checkpoint

- Branch: `feat/upgrade-foundry-plugin`
- Base: `origin/main` at `551da81`
- Latest code commit: `10787c4` (`fix(giants-foundry): reset materials after hand-in`)
- Plugin version: `1.2.0`
- Validated VM: `Bizza 12345` (`clanker\vmadmin2`)
- Authoritative guest worktree: `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade`
- Host context worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-giants-foundry`
- Installed JAR: `C:\Users\VMAdmin2\.runelite\microbot-plugins\GiantsFoundryPlugin.jar`
- Installed JAR SHA256: `BE74F9F8E53514E39D9110A8A69CF5DD4455E4DD5EF84B2D2277073B56289FFC`

The installed JAR is the current functional checkpoint. The client was intentionally closed immediately after the final smoke hand-in on 2026-07-30, and the Bizza VM was shut down after this handoff was pushed. The last authenticated check before the smoke showed Smithing 53 (146,372 XP).

## Branch commits

The Foundry-specific branch work is four commits on top of `origin/main`:

```text
10787c4 fix(giants-foundry): reset materials after hand-in
093d2c5 feat(giants-foundry): automate progression and rewards
c9a9e1f fix(giants-foundry): harden live state handling
bd4f720 feat(giants-foundry): upgrade activity automation
```

## Implemented behavior

- Supports level-aware automatic alloys and validated manual bars or recycled items.
- Locks the material plan for each sword so a level-up cannot change materials mid-cycle.
- `AUTO_BEST` uses 14 iron/14 steel from level 30, 14 steel/14 mithril from level 50, 18 mithril/10 adamant from level 70, and 19 adamant/9 rune from level 85.
- `AUTO_ECONOMY` follows the same progression except it remains on mithril/adamant at level 85+.
- Opens the Foundry bank, snapshots relevant supplies, withdraws the exact plan, and stops cleanly when the current strategy cannot be supplied. It does not silently substitute another alloy.
- Defaults preform collection to ice gloves; bucket and Smiths gloves (i) remain supported.
- Uses moderate mouse activity.
- Automates level-eligible mould purchases, then optionally the Smiths outfit. The default is `MOULDS_THEN_OUTFIT`; `MOULDS_ONLY` and `DISABLED` are available.
- Only allowlisted moulds and outfit items are bought. Consumables, the colossal blade, and double ammo mould are not bought.
- Expands the overlay with state, next action, current craft, material plan, supplies, successful crafts, Smithing level and levels gained, XP, reputation earned/spent, reward value, material cost, net GP, runtime, stage, heat, quality, progress, and crucible contents.
- Logs state observations, transitions, actions, confirmations, material accounting, hand-ins, purchases, and nonfatal scheduler failures.

## Live validation

The final installed JAR was built and tested in the Bizza VM, not only in the host checkout.

Confirmed live:

- Recovery from an empty inventory at the Foundry bank.
- Exact withdrawal and loading of 14 steel plus 14 mithril at Smithing 53.
- Full crucible, pour, preform pickup, heat management, all workstations, and hand-in.
- A 113/113 sword completed at 19:19:44 AEST on 2026-07-30.
- Post-hand-in acknowledgement observed both `preformRemoved=true` and `progressReset=true`.
- Automatic purchase of Flamberge Blade, Stiletto Forte, and Serpent Blade from the live Kovac shop.
- After the Serpent Blade purchase, the next commission correctly transitioned through `SELECTING_MOULD -> PREPARING_MATERIALS`, withdrew both materials, filled the crucible, and started another sword.
- Level transition from the pre-50 iron/steel strategy to steel/mithril at level 50.
- Expanded overlay rendered correctly in the live client.

Automated validation command used:

```powershell
$env:JAVA_HOME='C:\Users\VMAdmin2\.jdks\temurin-11.0.31'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.home=C:\Users\VMAdmin2\.jdks\temurin-11.0.31' test GiantsFoundryPluginJar '-PpluginList=GiantsFoundryPlugin' --console=plain
```

The relevant tests are under `src/test/java/net/runelite/client/plugins/microbot/giantsfoundry/` and cover material planning, shop planning, state resolution, state snapshots, and heat action solving.

### Final live smoke: 2026-07-30

The final observed sword started its commission at 19:30:12 AEST and handed in at 19:35:34. It completed every preparation and crafting state without a plugin exception, scheduler failure, material withdrawal regression, or idle state. The hand-in was acknowledged with `preformRemoved=true` and `progressReset=true`; the final quality was 103/113. The client was closed immediately after this completion. The Gradle `FAILURE: Build failed` at the end of the launch log is therefore expected process-termination output, not a Foundry failure.

The smoke exposed two follow-up opportunities:

- A real hand-in immediately before the observed sword was missed by session accounting. The sword entered `HANDING_IN` at 19:30:06 and the next commission at 19:30:10, but no `hand-in acknowledged` or `craft complete` line was emitted. The next cycle recovered through `PREPARING_MATERIALS`, but the successful-craft count and material cost both remained one cycle low. The likely remaining race is that the hand-in reset can arrive after `completeHandInIfAcknowledged()` returns false and before the next snapshot resolves to `GETTING_COMMISSION`. Preserve a pending completed-sword snapshot before interacting with Kovac, then reconcile it idempotently when a later tick sees the preform removed or progress reset.
- The completed sword lost five quality at three workstation-stage boundaries. Each loss began while the previous station animation was still active and the script was travelling to a temperature tool: grind to polish at 19:32:29, polish to grind at 19:33:19, and grind to hammer at 19:34:14. Interrupt the previous workstation immediately when the stage changes, before pathing for heat control. The heat solver also issued near-boundary no-op/correction actions (`292->292`, `736->736`, and heating to 922 followed by cooling from 965); a small deadband plus momentum-aware stopping should reduce redundant clicks and overshoot.

## Last bug and fix

Reported symptom: after handing in a sword, the plugin stood at Kovac/bank and reported `Missing 14 Steel bar from inventory` even though the bank contained hundreds of bars.

Root cause: the game reset Foundry progress before the equipped-preform cache cleared. The old hand-in completion check missed that transient state, so cycle fields were not reset and `inventoryPrepared=true` leaked into the next commission. State resolution then skipped `PREPARING_MATERIALS` and entered `FILLING_CRUCIBLE` with an empty inventory.

Commit `10787c4` fixes both sides:

- A hand-in is acknowledged when either the preform is removed or Foundry progress resets.
- `fillCrucible()` defensively treats an empty crucible plus no planned inventory as stale preparation and returns to bank preparation.

The full hand-in, shop purchase, next commission, bank withdrawal, and crucible-fill sequence was observed live after this fix.

## Known limits and remaining tests

- Hand-in completion accounting still has a narrow asynchronous race. It does not block the next sword because stale material preparation now recovers, but overlay craft, reputation, and material-cost metrics can miss a completed cycle.
- Stage changes can leave the old workstation animation running while the player moves to heat or cool, producing repeatable five-point quality losses. Heat control also chatters near range boundaries.
- Graceful supply exhaustion is covered by planner/state tests but has not been deliberately tested against a depleted live bank.
- The shop interaction uses an allowlisted contract captured from live widget group `753`, child `22`, with entries spaced by 13 and purchase action identifier 2. Unknown names fail closed, but a game widget update may require recapturing this mapping.
- Outfit purchasing has not been reached live because it requires all eligible moulds first and much more reputation.
- Higher transitions at 70 and 85 Smithing have unit coverage only.
- Recycled-item mode has validation coverage but has not had a full live cycle in this session.
- Overlay GP values are value estimates for materials and rewards, not a literal coin-pouch ledger.
- Enabling the plugin before login can briefly show the Sleeping Giants requirement. Start it after login if this latch appears.
- A client relaunch produced two transient `SSLHandshakeException`/HTTP 400 authentication attempts before BreakHandler recovered automatically. No evidence connected this to Foundry logic.

## Continue on the laptop

Fetch the branch before editing:

```powershell
git fetch origin
git switch feat/upgrade-foundry-plugin
git pull --ff-only origin feat/upgrade-foundry-plugin
git status --short --branch
```

In the Bizza VM, discover the current IP through VMware Tools and use the Bizza SSH helper. Do not assume the older `vmadmin` paths. Before changing the guest worktree:

```powershell
cd 'F:\vmware boxs\Bizza 12345 MBOT'
.\host-ssh-vm.ps1 'cmd /c "cd /d C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade && git status --short --branch"'
```

Use the host checkout for fast reading, but build and validate in the Bizza VM. Source changes do not affect the running client until the plugin JAR is rebuilt and installed. Never launch a visible RuneLite client through non-interactive SSH.

The current build output is:

```text
C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-foundry-upgrade\build\libs\GiantsFoundryPlugin-1.2.0.jar
```

The last interactive launch helper is:

```text
Host:  F:\vmware boxs\MBOT\operator-work\temp\launch-bizza-giants-foundry-1.2.0.ps1
Guest: C:\Users\VMAdmin2\operator-work\temp\launch-bizza-giants-foundry-1.2.0.ps1
Log:   C:\Users\VMAdmin2\operator-work\output\logs\microbot-client-launch-giants-foundry-1.2.0.log
```

The Agent Server is guest-local on port 8081 and requires the configured `X-Agent-Token`. Prefer the guest `microbot-cli` wrapper so the token is never printed or copied into documentation. Useful passive checks are:

```powershell
Set-Location C:\Users\VMAdmin2\IdeaProjects\Microbot
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli state'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli skills'
& 'C:\Program Files\Git\bin\bash.exe' -lc './microbot-cli scripts'
```

## Recommended next sequence

1. Fetch this branch and confirm `10787c4` or later before making changes.
2. Passively inspect the current client and recent Foundry log before restarting anything.
3. Let several more hand-in/rebank cycles run to stress the fixed cycle reset.
4. Test a controlled supply shortage when interrupting the existing progression is acceptable.
5. Test the level 70 alloy transition, later mould purchases, and eventually the Smiths outfit path.
6. Rebuild with JDK 11, replace the JAR only while the client is closed, launch interactively, and verify both the visible window and Agent Server state.
