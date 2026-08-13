# Guardians of the Rift Development Handoff

Last refreshed: 2026-08-13 (Australia/Sydney)

## Checkpoint

- Repository: `JogOnJohn/Microbot-Hub`
- Branch: `feat/gotr-mass-strategy-improvements`
- Commit before this documentation package: `d76c2a5`
- Plugin version: `1.5.19`
- Host worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-gotr`
- Runtime VM: newer `Bizza 12345`, guest `clanker\vmadmin2`
- Operator manifest: `F:\vmware boxs\MBOT\operator-work\plugins\gotr.psd1`
- Full local handoff: `F:\vmware boxs\MBOT\docs\handoffs\gotr\GOTR-HANDOFF.md`

Known-good installed artifact:

```text
C:\Users\VMAdmin2\.runelite\microbot-plugins\GotrPlugin.jar
SHA-256: C387F404011E90A2818B4B756FEDA3227D671A465165685F3EB700EEF5D2DAFE
```

The user reported this `1.5.19` build worked well after live startup testing.
Its JAR predates branch/commit manifest metadata, so runtime attribution must use
the SHA-256.

## Implemented Scope

- Mass-world strategy and contribution prioritization.
- Round-end and mine/altar transition hardening.
- Reset of persistent state across disable/re-enable.
- Outside-arena startup preparation for the best usable pickaxe, chisel and eligible pouches.
- Inside-arena startup bypass when a usable pickaxe is already carried or equipped.
- Graceful warnings for optional chisel/pouch absence; missing usable pickaxe remains blocking.

## Remaining Diagnostic Target

There was an earlier observation of dead time when climbing out of the initial
mining area. It was not reproduced after the successful `1.5.19` startup test.
Before patching, capture state, player tile, movement, animation, interaction,
destination, inventory, guardian power and scene load timing across the exit.

Do not alter the running client or replace its JAR without current authorization.
