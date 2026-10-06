# Rooftop prehover (1.4.0)

Enabled by the `Rooftop prehover` course setting, default on. Requires Microbot 2.6.29.

The script hands off the next obstacle from the actual clicked object's ID, not the
nearest section estimate. All nine rooftop courses are supported. Non-rooftop
courses retain their existing behavior.

During the obstacle completion wait, a client-tick adapter uses the shared
`MouseIntentController` to follow a point inside the next obstacle's current
clickbox. The adapter resolves targets across rooftop planes, checks live scene
membership and viewport clipping, and pauses when the target is offscreen.
It never clicks, changes the camera, or walks to obtain a prehover target.

Cursor ownership is released before the next script iteration, so marks, supplies,
banking and alching keep priority. Human input, menus, selected widgets, breaks,
logout, course changes, falls and a 15-second expiry suspend or cancel tracking.
The efficient-alch sequence arms prehover only after its final obstacle click.

The overlay reports `Idle`, `Waiting <id>` or `Tracking <id>`. Logs distinguish
tracking from confirmed cursor arrival inside a live clickbox.

Focused build and tests:

```text
gradlew MicroAgilityPluginJar rooftopPrehoverTest -PpluginList=MicroAgilityPlugin -PmicrobotClientVersion=2.6.29 -PmicrobotClientPath=<exact runtime jar>
```

The shared library is bundled through the opt-in resource marker; it is not a
separately loaded plugin. The original Agility branch remains unchanged, while
the integration branch includes the current Hub main update and shared utilities.
