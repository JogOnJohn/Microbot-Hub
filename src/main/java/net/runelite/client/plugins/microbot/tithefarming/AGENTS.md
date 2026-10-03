# Tithe Farm Plugin Scope

This directory owns `[MB] Tithe Farm` only. Keep changes inside this package,
its focused tests, and `docs/tithe-farm` unless a shared API change is necessary
and justified.

- This is a timed minigame. Model one confirmed action at a time; do not attempt
  to improve it by reducing blind sleeps or polling faster.
- Resolve the selected crop and lane once per run. Do not derive a usable crop
  from level when the account does not satisfy its requirement.
- A patch must be identified from validated, instance-aware live data. Do not
  increase coordinate tolerance to cover a mismatch.
- Do not use generic dialogue continuation, fixed option-number keys, or broad
  chat parsing. Each must be phase-scoped and message/widget-specific.
- Never drop seeds, fertiliser, tools, or user inventory as implicit recovery.
- Do not clear global pause state or leave global anti-ban settings changed when
  this plugin starts or stops.
- Before an artifact replacement, prove the source branch, source version, jar
  hash, installed jar hash, and client process state separately.
