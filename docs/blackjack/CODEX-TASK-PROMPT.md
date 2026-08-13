# Blackjack Codex Task Prompt

We are continuing the JOJ Blackjack plugin on branch
`feat/blackjacking-plugin` in `JogOnJohn/Microbot-Hub`.

Use the local operator workspace `F:\vmware boxs\MBOT` and read, in order:

```text
F:\vmware boxs\MBOT\AGENTS.md
F:\vmware boxs\MBOT\operator-work\plugins\blackjack.psd1
F:\vmware boxs\MBOT\repos\Microbot-Hub-blackjacking\src\main\java\net\runelite\client\plugins\microbot\blackjack\AGENTS.md
F:\vmware boxs\MBOT\docs\handoffs\blackjack\BLACKJACK-HANDOFF.md
```

The source worktree is
`F:\vmware boxs\MBOT\repos\Microbot-Hub-blackjacking`. The runtime target is
the newer `Bizza 12345` VM (`clanker\vmadmin2`), never the older MBOT VM by
implication. Agent Server is guest-local on port 8081 and requires the
`X-Agent-Token` header; never print or persist its token.

Current checkpoint and artifacts:

- Source checkpoint: `234f7f60378858da2578cc8d32e2ee5cd60013a3`
- Known-good installed 1.1.4 hash:
  `3E740D752A58A0578CA1F2750573FE6E105BDD09CB129ADE863BB0904BB3372C`
- Clean host-only 1.1.5 build hash, not installed at handoff:
  `A0EE54583253581C61618C5810E93A6F818F17263C3107B82C9BC2CD3882C175`
- Latest unresolved observation: an occasional duplicate Knock-Out after the
  first attempt succeeds.

Preserve fast event-driven timing. Never drop empty jugs. Healing is priority
one. Cursor wandering must remain inside the target hull.

First perform read-only checks: source branch/status, Bizza connectivity, Agent
Server reachability, client state, plugin active/enabled state and installed JAR
hash. Summarize readiness and discrepancies. Do not edit, build, install,
enable/disable, launch, close or restart anything until the user gives the next
instruction.
