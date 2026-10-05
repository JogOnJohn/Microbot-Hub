# Shared Automation Utilities

The Hub now has an optional Java library for small mechanics that recur across
plugins. It is a library, not a RuneLite plugin: it adds no plugin-panel entry,
does not start a script, and does not make gameplay decisions for its consumers.

The library has no Microbot or RuneLite dependency. A consuming plugin supplies
the game-specific policy, state checks, target geometry, and interaction calls.
This keeps shared code portable and avoids pulling a broad framework into each
plugin.

Public APIs are grouped under
`net.runelite.client.plugins.microbot.sharedautomation.humanizer`,
`net.runelite.client.plugins.microbot.sharedautomation.mouse`, and
`net.runelite.client.plugins.microbot.sharedautomation.action`.

## Included APIs

### Humanizer timing

`Humanizer` samples inclusive `DelayRange` values and provides a probability
helper. The default random source uses `ThreadLocalRandom`; callers can inject a
`RandomSource` for deterministic behavior. It does not sleep or schedule actions.

```java
Humanizer humanizer = new Humanizer();
DelayRange clickDelay = new DelayRange(180, 420);
long delayMillis = humanizer.delayMillis(clickDelay);
boolean optionalBehavior = humanizer.occurs(0.08);
```

The plugin decides whether a sampled delay is compatible with its action window
and whether an optional behavior is safe. Use the script scheduler for waiting;
do not block the client thread.

### Safe-boundary break schedule

`SafeBoundaryBreakSchedule` repeats a randomized interval/duration pair. A due
break remains pending until the consumer passes `safeBoundary=true`. Create
separate instances for different break classes (for example, short and longer
breaks), and let the plugin arbitrate which one may start first.

```java
long now = elapsedMillis(); // derive from a monotonic clock
SafeBoundaryBreakSchedule breaks = new SafeBoundaryBreakSchedule(
    now,
    new DelayRange(10 * 60_000L, 12 * 60_000L),
    new DelayRange(25_000L, 35_000L),
    RandomSource.threadLocal());

SafeBoundaryBreakSchedule.Event event = breaks.poll(now, isSafeBoundary());
```

Call `poll` regularly. `STARTED` and `ACTIVE` mean the caller should honor the
break; `FINISHED` indicates the break ended; `NONE` means no transition. The
utility never pauses, logs out, or changes plugin state itself. Supply elapsed
monotonic milliseconds, not wall-clock time.

### Mouse intent tracking

`MouseIntentController` smoothly follows a target that may move between frames.
The plugin provides a `MousePort` adapter and a `MouseTarget` that returns the
target's current projected canvas point, or `null` when the target is invalid.
The controller only moves the pointer: it never clicks, chooses menu entries,
projects world coordinates, or decides whether a target is safe to interact
with.

```java
MouseIntentController cursor = new MouseIntentController(mousePort);
cursor.request("trap-prehover", 10, this::projectCurrentTrapPoint);

// From the plugin's chosen tick callback:
cursor.tick();

// On state change, stop, or when another action takes cursor ownership:
cursor.cancel("trap-prehover");
```

Priorities are explicit: a different owner can replace the current intent only
with a strictly higher priority. Re-issuing an intent from its current owner
updates the target without resetting motion. Call `cancel` when the owning
workflow ends or when external input/action takes over; the controller does not
try to infer user input from cursor drift.

The target supplier and mouse adapter should be called on the same appropriate
client/UI thread. Reproject moving targets on each tick rather than capturing a
stale canvas point. Validate the target and its clickable area in plugin code
before starting the intent.

The same adapter can support plugin-owned intents such as AutoHunter pre-hovering
the projected click point of a selected trap or MM Caves tracking a moving wall
tile projection. Each consumer still chooses the target, validates its
tile/clickbox, selects priority, and cancels the intent at state changes. For
Blackjack, where cursor placement is part of a right-click menu sequence, retain
the menu/action timing policy in the plugin and delegate only a well-defined
cursor move.

### Bounded action confirmation

`ActionAttemptGate` tracks a single action key, its confirmation deadline, and a
maximum attempt count. The plugin owns the action and the evidence predicate.
Call `begin` after dispatching the first attempt, `poll` while awaiting evidence,
`confirm` when the expected evidence is observed, and `recordRetry` only after
the plugin has decided a retry is safe and has issued it.

```java
ActionAttemptGate gate = new ActionAttemptGate();
ActionAttemptGate.Ticket ticket = gate.begin("open-gate", elapsedMillis(), 1_200, 2);

if (ticket != null) {
    if (expectedEvidenceObserved()) {
        gate.confirm(ticket);
    } else {
        ActionAttemptGate.State state = gate.poll(ticket, elapsedMillis());
        if (state == ActionAttemptGate.State.RETRY_DUE && retryIsStillSafe()) {
            issueRetry();
            gate.recordRetry(ticket, elapsedMillis());
        }
    }
}
```

`begin` returns `null` if another action is still waiting for confirmation. Do
not replace or overlap that action unless the plugin has explicitly cleared it.

Use a monotonic clock. A late confirmation can still resolve a timed-out or
exhausted action while its ticket is current. Tickets prevent stale evidence
from confirming a later action after the gate is reused. This utility does not
interpret chat, animation, inventory, or interaction state.

## Opt-In Build Wiring

The library is included as the `:shared-automation` Gradle subproject and targets
Java 11. It is not added to every plugin JAR. To opt a plugin in, create an
empty marker file at:

```text
src/main/resources/net/runelite/client/plugins/microbot/<plugin-package>/shared-automation.txt
```

The Hub build then adds the library to that plugin's compile/runtime classpath
and shades it into the plugin's standalone JAR. No separate library JAR needs to
be installed beside the plugin. The marker is excluded from the final artifact.
Plugins without the marker are unaffected.

Build only the library:

```powershell
.\gradlew.bat :shared-automation:jar --no-daemon --console=plain
```

Build an opted-in plugin using its discovered plugin class name:

```powershell
.\gradlew.bat <PluginClassName>Jar -PpluginList=<PluginClassName> --no-daemon --console=plain
```

`pluginList` remains important: it limits processing to the selected plugin.
The build does not install, enable, reload, or live-test the resulting JAR.

## Ownership and Lifecycle Rules

- Keep state machines, target selection, game-state evidence, and stop policy in
  the consuming plugin.
- Keep shared objects instance-scoped to a plugin/script lifecycle; do not add
  static session state.
- Cancel mouse intents and reset schedules during the consumer's normal stop or
  shutdown path.
- Do not run blocking sleeps from client event handlers.
- Do not use a shared random choice to invent game interactions. The consumer
  must explicitly authorize every optional action and check that it is safe.
- Keep target projection and click validation in the plugin that understands
  the relevant scene, NPC, object, or menu.

The first version deliberately avoids a cross-plugin base class, global service,
plugin toggle, or shared gameplay state. New helpers should be added only when a
second real consumer demonstrates a stable common contract.

All helpers are independent and instance-scoped. A consumer may use any subset;
the opt-in marker makes the APIs available and bundles their classes into that
plugin's JAR. No existing plugin has been migrated as part of this package, so
current plugin behavior remains unchanged.
