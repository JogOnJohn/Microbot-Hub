# NMZ Codex Task Prompt

Continue `[MB] Nmz` on `feat/nmz-prayer-seeding` in
`F:\vmware boxs\MBOT\repos\Microbot-Hub-nmz`.

Read:

```text
F:\vmware boxs\MBOT\AGENTS.md
F:\vmware boxs\MBOT\operator-work\plugins\nmz.psd1
F:\vmware boxs\MBOT\repos\Microbot-Hub-nmz\src\main\java\net\runelite\client\plugins\microbot\nmz\AGENTS.md
F:\vmware boxs\MBOT\docs\handoffs\nmz\NMZ-HANDOFF.md
```

Use the newer Bizza VM for runtime validation. Its Agent Server is guest-local
on port 8081 with `X-Agent-Token`; do not expose the token.

First fix and validate the duplicate prayer-worker scheduling defect. Then build
the prayer-seeding profile and manual Ancient mace special configuration without
depending on Inventory Setups. Start every task with read-only attribution and
do not change the running client until authorized.
