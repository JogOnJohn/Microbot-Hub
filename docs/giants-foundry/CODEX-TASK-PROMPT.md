# Giants' Foundry Codex Task Prompt

Continue Giants' Foundry development on `feat/upgrade-foundry-plugin` in
`JogOnJohn/Microbot-Hub`.

Read the operator root `AGENTS.md`, the plugin manifest at
`F:\vmware boxs\MBOT\operator-work\plugins\giants-foundry.psd1`,
`docs/GIANTS_FOUNDRY_HANDOFF.md`, this directory's `DEVELOPMENT-HANDOFF.md`, and
the plugin package's `AGENTS.md` before acting.

Use `F:\vmware boxs\MBOT\repos\Microbot-Hub-giants-foundry` for source and
only the newer `Bizza 12345` VM (`clanker\vmadmin2`) for runtime validation.
Agent Server is guest-local on port 8081 and requires `X-Agent-Token`; never
expose the token.

Start with read-only status checks. Confirm branch/dirty state, SSH, Agent
Server, client/plugin state and the installed JAR hash
`CD545C4878CB0D6AEE81E81EB43A294CABE38F4CCEF954F04576CEF67710D313`.
Do not edit, build, install or change client lifecycle state until instructed.
