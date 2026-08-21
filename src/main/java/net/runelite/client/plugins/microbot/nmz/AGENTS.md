# NMZ Plugin Rules

## Scope

- This package owns `[MB] Nmz` (`NmzPlugin`) and the adjacent NMZ prayer helper.
- Keep prayer, absorption/overload, entry, and special-attack flows explicit and
  mutually exclusive by profile. Do not let a prayer-seed run fall through into
  barrel, overload, absorption, or self-harm actions.
- Do not depend on Inventory Setups for the prayer-seed profile or manual special
  weapon selection.

## Scheduler And Interaction Invariants

- The plugin owns at most one scheduled prayer controller for a running plugin.
- Every drink, equip, special, NPC attack, object interaction, and interface
  command needs one in-flight generation and observed success/failure evidence.
- Never repeat a potion drink, weapon switch, or special because a client tick,
  animation, or widget update is delayed.
- Every worker must stop during plugin shutdown and must honor its profile and
  config enablement before doing any action.

## Prayer Seed And Special Weapon

- Accept valid prayer-restoring potions by item semantics, not one exact dose.
- Use threshold hysteresis and bounded wait/recovery around drinking.
- Validate the configured special weapon against live inventory/equipment before
  switching. The first live target is Ancient mace item `11061`.
- Restore the main weapon only after the special result is acknowledged or a
  bounded failure state is reached.
- Do not use Inventory Setups as a hidden fallback when the user disables it.

## Diagnostics And Runtime

- Log state transitions, selected potion/weapon, action generation, and results.
  Do not emit per-tick logs.
- Verify the installed JAR hash and loaded source before interpreting live logs.
- Never build, install, toggle, launch, close, or restart without current user
  authorization.
