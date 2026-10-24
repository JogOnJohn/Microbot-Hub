# Watering priority live test

Source: host branch `fix/tithe-farm-state-machine`, base `42ba409cd6d42ab854ede21173d788f5a3058796`, uncommitted Tithe changes. Plugin version 1.1.15.

Fixed dry-crop starvation: choose the crop with the oldest last-watered timestamp before choosing an empty patch, including while in HARVEST. Existing confirmed-action waits and natural mouse input remain in use.

Focused Gradle JAR build succeeded against Microbot 2.6.38 with JDK 11.
SHA256: `C2B64DA2592D9587041BD88746EC465162D444A0D65216409CB4DC8BADB721EA`.

Bizza client PID 3068 uses Microbot 2.6.38 and watches `E:\IdeaProjects\Microbot-Hub-tithe-farm\build\libs\TitheFarmingPlugin-1.1.14.jar`. While Tithe was stopped, backed up the prior watched artifact to `TitheFarmingPlugin-1.1.14-before-watering-priority.bak`, then copied the built 1.1.15 artifact onto that fixed watched path. Guest hash matched host. No sideload installation or client restart performed. Guest source checkout was not synchronized by this artifact-only test.

Clean-room reset removed old fruit/seeds. Started patched plugin at 2026-10-24T09:10:17Z. Observed crop accounting through the first batch: 2 fruit + 12 standing; 8 fruit + 6 standing; 12 fruit + 2 standing; finally 14 fruit and four new seedlings from the next cycle. No lost crops in this batch. Plugin left running.

Seed inventory reached 9986 for the completed 14 plots, indicating a 10000-seed initial supply; earlier calculations assuming 9999 were off by one. Use completed harvest counts and field state for survival evidence.

Limitations: one complete clean 14-plant batch only. Natural input remains slow and causes staggered planting/watering; this is survival evidence, not proof of an efficient 20-plant route or long-duration reliability. Prior loss observations do not alone establish a single exclusive cause.
