# Mahogany Homes and Hunter Rumours local port review

Reviewed 8 October 2026. Source baseline: RLitePlus/rliteplus-plugins-public,
commit `95d3da6e053b1d58f88ba91a222c274f880b7c20`.

## Scope and findings

The port replaces the existing `mahoganyhomez` implementation and adds
`huntersrumours`. Both plugins are disabled by default and marked external.
The minimum client version is 2.6.29; compatibility was checked against the
exact custom client JAR running in Bizza 12345, rather than a downloaded release.

The source review covered imports, executable side effects, configuration
writes, scheduling, walker adapters, descriptors, and resource dependencies.
No plugin-owned network clients, outbound request code, logger calls,
process launchers, native loading, credential access, reflection loaders,
encoded executable payloads, or obfuscation were found in the two packages.
The copyright-comment URL was removed while author names and the license
notice were retained. No additional dependencies or binaries were imported.
Existing Microbot framework and walker code can still produce internal logs.
This is a bounded source review, not proof of all transitive client behavior.

Persistent writes use ConfigManager and are scoped to the plugins' own
profile configuration: Mahogany assignment homeowner/tier, Hunter assignment
history, and trap recovery. Existing `MahoganyHomesBot` settings are not deleted;
the replacement uses the `mahoganyhomes` group and requires its settings to be
reviewed before first use.

The reflection bridge to Efficient Walker was replaced with direct calls to
Microbot Rs2Walker. Core walker/W330 code was not modified. Hunter travel uses
the state-returning API so MOVING is not mistaken for failure, matches the
script's three-tile arrival check, and does not report interrupted travel as a
route rejection. Walker work runs on script workers rather than the client
thread. Cancellation clears a route only when its current target matches the
adapter's requested target; this target check is not a general ownership lock
between multiple automators.

## Compile evidence

Both `compileMahoganyhomezJava` and `compileHuntersrumoursJava` passed with
JDK 11 and Gradle offline mode after the final code changes. The first check
identified missing Hub descriptor metadata; `minClientVersion` and
`isExternal` were added before the passing check.

Client: `microbot-2.6.29-160b7a04cf.jar`

SHA-256: `90CEFF7189C6BAAE65C33B78F2D61E75721E893EFDA02BA5E807C6EFB9DD85ED`

Plugin versions: Mahogany Homes 1.0.4, Hunter Rumours 1.0.1.
Compile success establishes API/type compatibility only. No live gameplay
validation or client restart was performed as part of this review.
