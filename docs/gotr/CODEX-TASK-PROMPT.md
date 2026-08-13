# Guardians of the Rift Codex Task Prompt

Continue GOTR development on `feat/gotr-mass-strategy-improvements` in
`JogOnJohn/Microbot-Hub`.

Read the operator root `AGENTS.md`,
`F:\vmware boxs\MBOT\operator-work\plugins\gotr.psd1`, this directory's
`DEVELOPMENT-HANDOFF.md`, and the plugin package's `AGENTS.md` before acting.

Use `F:\vmware boxs\MBOT\repos\Microbot-Hub-gotr` as the active source
worktree. Use only the newer `Bizza 12345` VM (`clanker\vmadmin2`) for runtime
validation. Agent Server is guest-local on port 8081 and requires
`X-Agent-Token`; never expose the token.

Start with read-only status checks. Confirm branch/dirty state, SSH, Agent
Server, client/plugin state and the installed JAR hash
`C387F404011E90A2818B4B756FEDA3227D671A465165685F3EB700EEF5D2DAFE`.
Do not edit, build, install or change client lifecycle state until instructed.
