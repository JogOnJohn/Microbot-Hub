# AutoHunter Development Handoff

The operator package is at
`F:\vmware boxs\MBOT\docs\handoffs\autohunter\AUTOHUNTER-HANDOFF.md`.

Current source review: AutoHunter 1.1.1 is a box-trap-only loop whose fixed
multi-second sleeps and unowned nearby-trap scans are the main performance and
safety issues. Do not tune the old values in isolation. Replace them with a
small confirmed-action controller after capturing live red-chin object states.
