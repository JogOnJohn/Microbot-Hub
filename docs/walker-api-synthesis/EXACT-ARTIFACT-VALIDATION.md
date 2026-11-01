# Exact Artifact Hub Validation

Prepared 2026-11-01. Tooling only; no application builds, VM actions, installation,
commit or push. Existing inventories remain unchanged. The parent runs builds
sequentially. Do not use the dirty Shared Automation checkout, even read/write
validation there. Use full committed snapshots in separately provisioned checkouts.

## Runnable Entry Point

`scripts/walker-api-validation/Validate-HubClient.ps1` requires an exact existing
client JAR, expected SHA-256, asserted client source commit and expected Hub HEAD.
No artifact is selected by version/directory/mtime. Plans print JSON without
calling Java or Gradle or creating output; only `-Execute` starts compilation.
The source commit assertion is provenance supplied by the parent, **not** proof
that those sources produced the JAR. Preserve the client's build manifest and
dirty source patch/hash separately. Do not release from the unresolved stage-3
night checkpoint; parent owns fixing/revalidating those blockers and producing
an authorized candidate artifact.

Use PowerShell 7, the installed Adoptium JDK 11 and an already built client JAR.
Example from this checkout (replace candidate values with the parent's manifest):

```powershell
$helper = 'F:/vmware boxs/MBOT/repos/Microbot-Hub-walker-api/scripts/walker-api-validation/Validate-HubClient.ps1'
$case = @{
    ClientJar = 'F:/vmware boxs/MBOT/repos/Microbot-walker-api/runelite-client/build/libs/EXACT-CANDIDATE.jar'
    ClientSha256 = '<64-hex SHA-256 from parent build manifest>'
    ClientCommit = '<40-hex client source commit from parent build manifest>'
    ExpectedHubCommit = '5da5504c7a1fe6a18c76b4a36d17ac9310aefc50'
    JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot'
    Label = 'main-candidate-01'
}
& $helper @case
# Parent only, after inspecting the plan:
& $helper @case -Execute
```

The current preparation HEAD above includes docs; main consumer source is pinned
at `0b017fdea8ab94added7bf65a626263e4fa18337`. For a provisioned exact-main
checkout set `HubPath` and `ExpectedHubCommit` to that pin instead. Never pass a
branch name as the commit, automatically switch branches, or fetch/reset a live
checkout to satisfy the helper. Reconfirm HEAD if preparation advances.

For each private row below, parent provisions a clean, isolated full checkout at
the pin (not just changed files), then changes the same argument dictionary:

```powershell
$case.HubPath = 'F:/vmware boxs/MBOT/repos/VALIDATION-blackjack-pinned'
$case.ExpectedHubCommit = 'bbfc80ff0c0080f0c26c2865eede2efde7cda8b4'
$case.Plugins = @('BlackjackPlugin')
$case.Label = 'blackjack-candidate-01'
& $helper @case
# Parent only; sequential, never parallel builds in one checkout:
& $helper @case -Execute
```

Run the same case against its **known-compatible baseline** JAR/hash/commit with
a fresh baseline label, then candidate with another fresh label. Do not compute
the expected hash from an arbitrarily discovered JAR and call that provenance.
Record baseline failure separately; it is not automatically a candidate regression.
If dependencies are absent from cache, default `--offline` fails closed. Explicit
`-OnlineDependencies` permits Gradle/plugin/library downloads, never a published
client. Wrapper distribution cache availability is separate from Gradle offline
mode: pre-provision Gradle 8.2 if network-free execution is required.

## What Is Enforced

- Existing client ZIP must contain walker and WorldPoint classes and match the
  supplied SHA-256 before planning and immediately before every selected compile.
- Explicit `microbotClientPath` plus `microbotClientVersion=exact-local` prevents
  Hub's latest-version lookup. Missing local artifacts cannot select published ones.
- The external init script rejects published client coordinates/components,
  requires microbotRuntime to contain only the candidate, and rejects duplicate
  walker, WorldPoint or RuneLite classes on each selected compiler classpath.
- Plugin names must exist exactly; selection must match Gradle discovery. Generated
  `compile<Package>Java` tasks compile the complete plugin source set, including
  declared shared sources, not only inventoried changed files. Global `compileJava`
  is rejected. No `build`, test, JAR packaging, run/debug or deployment task is used.
- Fresh `build/walker-api-validation/<Label>/classes-build` outputs, no build cache,
  rerun tasks, one worker and no daemon prevent accidental baseline output reuse.
- Target HEAD and dirty state are checked; unowned dirty paths are rejected. Only
  these new helper/docs paths are permitted dirty. The protected Shared Automation
  path is rejected outright. No automatic stash/reset/clean/copy/install exists.

Each executed case records `plan.json`, `compile.log`, `classpaths.json` (including
file hashes), `result.json` and compiler outputs under its fresh label. Client
hash, Hub HEAD and dirty paths must remain stable afterward. Keep parent builds
sequential, including any shared subproject dependencies. Classpath directory
entries are listed but not recursively hashed; the clean pinned source checkout
and fresh output directory are required evidence, not interchangeable with a JAR
hash. This helper does not run focused unit tests or semantic route validation.

## Representative Matrix

Pins come from the client workspace manifest and Hub `inventory/consumers.csv`.
Zero private delta rows mean inherited calls still exist, not no dependencies.
Baseline main uses the parent's compatible playable artifact; historical request
consumers require a compatible runtime/data-sync baseline, not canonical playable.

| Slice / pluginList | Full Hub source pin | Why selected |
| --- | --- | --- |
| Main `HuntersRumoursPlugin,BarrowsPlugin,HSBlackJackPlugin,MicroAgilityPlugin,TitheFarmingPlugin,AutoHunterPlugin,BlackjackPlugin` | `0b017fdea8ab94added7bf65a626263e4fa18337` | Legacy overloads, owned banked targets, predicate yield, minimap/canvas, shutdown cleanup |
| `BlackjackPlugin` / `feat/blackjacking-plugin` | `bbfc80ff0c0080f0c26c2865eede2efde7cda8b4` | Run-permitted fallback/return path; zero matching delta records still needs full compilation |
| `MicroAgilityPlugin` / `codex/agility-rooftop-prehover` | `fa9ecded5f0b843760ead89deb1534ef6304767f` | Rooftop prehover, route cleanup and scene/minimap boundaries |
| `TitheFarmingPlugin` / `fix/tithe-farm-state-machine` | `e8d6b83703ff75f8cb2a7ee4656afd89d8f55e66` | State-machine walker contract, original coordinates/plane |
| `TitheFarmingPlugin` / `codex/tithe-prehover-20261025` | `22db5ebbf0af223cea86c47cb7142b694eb01ec2` | Full prehover branch, object API and interaction tolerance |
| `AutoHunterPlugin` / `fix/autohunter-trap-layout-throughput` | `bd6404df3c302017a53d6caaf5ea853aa7282417` | Active private hunter compile guard despite zero direct walker delta |
| Historical `MahoganyHomesPlugin` / `codex/mahogany-interaction-api-20261020` | `97be5d6d627e6e525535bb2f3341a0bfb2ea92d0` | Source/linkage only, actual request/result overload, public fields, factories/cancellation; never launch |

Check `HuntersRumours/WalkerAccess.java:38,44,78,80` (actual package
`huntersrumours`), `barrows/BarrowsScript.java:3155`,
`hsblackjack/HSBlackJackScript.java:507,509`, `agility/AgilityScript.java:111,115`
and historical `MahoganyHomesScript.java:1987,2001` in the pinned inventory.
Method name/arity alone cannot distinguish request versus boolean overloads or
the return descriptor. Compilation proves source linkage only, not ownership,
arrival, cancellation, safe geometry, run policy or observed interaction effects.
Later runtime checks require separate permission and the then-active workload.

Shared Automation's `c737ee32d0b2d0fa9a7fbb2f080a74183af2a294` snapshot remains
an optional future **isolated committed snapshot** expansion, not permission to
inspect/edit/build the existing dirty worktree. No shared consumer migration here.

## Executable Precompiled Binary Gate

Keep baseline-built representative plugin JARs **unchanged**. Candidate-rebuilt
plugins cannot test old binary linkage. Record plugin SHA-256, plugin source pin,
baseline client hash/source provenance, compile JDK and external runtime classpath.
Use a separate future packaging step to produce absent baseline JARs; this helper
intentionally compiles only and does not claim packaging/binary success.

`Test-PrecompiledLinkage.ps1` compiles only `PrecompiledLinkage.java` with JDK17
and scans existing old plugin archives/class directories. Default is plan-only;
parent adds `-Execute`. No Hub/client build, application class initialization,
RuneLite launch or gameplay occurs. Use one plugin source-set class root, e.g.
`classes/java/agility`, not aggregate parent `classes/java`.

```powershell
$binaryHelper = 'F:/vmware boxs/MBOT/repos/Microbot-Hub-walker-api/scripts/walker-api-validation/Test-PrecompiledLinkage.ps1'
$binary = @{
    ClientJar = $case.ClientJar
    ClientSha256 = $case.ClientSha256
    ClientCommit = $case.ClientCommit
    PluginPath = 'F:/vmware boxs/MBOT/repos/Microbot-Hub-walker-api/build/walker-api-validation/main-baseline-20261101/classes-build/classes/java/agility'
    PluginSha256 = '<64-hex old class-directory fingerprint>'
    PluginCommit = '0b017fdea8ab94added7bf65a626263e4fa18337'
    DependencyManifest = 'F:/vmware boxs/MBOT/operator-work/output/audits/walker-api-synthesis-20261031/linkage-dependencies-20261101.json'
    Label = 'agility-candidate-linkage-02'
}
& $binaryHelper @binary
# Parent only, once authoring is frozen:
& $binaryHelper @binary -Execute
# Independent synthetic self-tests, fresh label; not plugin compatibility evidence:
$binary.Label = 'linkage-tool-selftest-02'
& $binaryHelper @binary -SelfTest -Execute
```

An archive fingerprint is SHA-256 of the archive. A class-directory fingerprint is
SHA-256 of UTF-8 records sorted by relative path with ordinal comparison:
`relative/path.class + NUL + lowercase-SHA256-of-class-bytes + LF`. Only `.class`
files enter that fingerprint. Parent supplies expected fingerprints/source pins;
the launcher verifies before and after, without accepting mismatches automatically.
Client/plugin commit assertions require the parent's build provenance; they are
not cryptographic proof of source-to-binary correspondence.

The dependency manifest is a JSON array of absolute JAR `path`/`sha256` values.
No downloads or directory discovery. The prepared manifest pins cached ASM9.4
(`39d0e2b3dc45af65a09b097945750a94a126e052e124f93468443a1d0e15f381`).
The first explicit manifest JAR providing ASM core is the **tool-only** provider,
preferred over bundled ASM; absent an external provider, bundled ASM must support
ASM9/V17. `javac --release 17 -proc:none -cp <tool-ASM.jar>` compiles the verifier.
Its runtime classpath contains only verifier classes and this ASM provider, never
candidate/plugin classes. Actual ASM CodeSource must equal the selected pinned
file; ASM9/V17 fields and a Java17 class-read probe are checked, and CodeSource,
implementation version (possibly absent in JAR metadata) and capabilities reported.
Older bundled ASM cannot shadow the supplied ASM9.4.

The selected external ASM provider is excluded from inspected runtime dependencies
unless its entry explicitly sets `"role":"both"`. Other manifest entries are
inspected runtime dependencies. Inspected resolution order is candidate, original
old plugin, exact runtime JARs, JDK17 JRT. Duplicate scoped API or java/* definitions
fail. Non-scope duplicates use this explicit order and emit class-selection records.
Original old plugin bytes are always scanned, including shaded/shared helpers.
Unavailable required classes/dependencies are explicit failures, not silent fallback
to the tool JVM/application classpath, baseline client or published artifacts.

The resolver selects `util/walker/**` (path API/interaction included),
`api/tileobject/**`, `util/tileobject/**`, `api/npc/**`, `util/npc/**`, `api/actor/**`,
and shared query types `AbstractEntityQueryable`, `IEntityQueryable`, `IEntity` plus
their nested types. Scoped descriptor types and inherited API calls through old
plugin subclasses are selected too. It checks exact full JVM descriptors including
return types, fields, constructors (not inherited), superclass/maximally-specific
interface methods, invokeinterface/owner kind, staticness, access/nestmates,
final-field writes and basic invokespecial owner restrictions. Bootstrap,
invokedynamic and ConstantDynamic method/field/constructor handles are extracted.
Zero scoped refs fails as nonrepresentative, rather than producing a green result.

Fresh-label evidence: `plan.json`, `tool-compile.log`, `linkage.jsonl`, `result.json`,
`tool-classes`. Java exits: 0 scoped symbolic pass, 1 selected-ref incompatibility,
2 setup/scan/tool failure. Self-tests use synthetic ASM fixtures for inherited,
static, interface, field, constructor and bootstrap/constant handle positives;
missing member, return-type-only change, staticness/access, missing dependencies
and conflicting-default negatives. Parent executes these; authoring agent does not
compile the verifier or perform application builds.

### Binary Limits

This is scoped symbolic linkage, **not a complete JVM verifier**. It does not prove
operand-stack/protected-receiver constraints, actual dispatch targets or freedom
from AbstractMethodError, all ACC_SUPER selection details, module exports, loader
constraints/runtime package identity, annotation/generic metadata, reflection or
resource strings, dynamically constructed handles, initializers, invokedynamic
bootstrap execution or behaviour. Signature-polymorphic MethodHandle/VarHandle
call-site descriptors follow polymorphic resolution; dynamically chosen targets
remain unverified. Access checks for descriptor types are conservative.
Outside-scope references are counted, not fully validated. A pass closes only this
scoped precompiled-symbolic slice, not all HUB-01/API-01 or runtime acceptance.

Optional class-level diagnostics on existing binaries (parent only):

```powershell
$jdk17 = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin'
$oldPlugin = 'F:/vmware boxs/MBOT/repos/VALIDATION-blackjack-pinned/baseline-binaries/BlackjackPlugin-EXACT.jar'
$candidate = $case.ClientJar
# Add only the pinned external libraries actually required by that binary.
& "$jdk17/jdeps.exe" --multi-release 17 --missing-deps --class-path $candidate $oldPlugin
& "$jdk17/javap.exe" -classpath $candidate -public -s net.runelite.client.plugins.microbot.util.walker.Rs2Walker
& "$jdk17/javap.exe" -classpath $candidate -public -s net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest
```

`jdeps` finds missing classes, not missing members. Javap output is descriptor
evidence, not a substitute for the executable member resolver above.

## Tooling Checks

```powershell
& 'F:/vmware boxs/MBOT/repos/Microbot-Hub-walker-api/scripts/walker-api-validation/Test-ValidationPlan.ps1'
```

The fixture test creates/removes one synthetic ZIP under the new helper directory;
it only tests plan structure and rejection paths. It never invokes Gradle or
compiles code. Parent's first seven-consumer source baseline built but lacked
classpaths.json: GString/String allowlist hashing skipped doFirst guards. Inputs
were stable. The fix normalizes task names and requires the exact evidence task
set in Gradle and PowerShell, without weakening input checks. Rerun under a fresh
label. Parent's first binary-helper attempt failed javac at V17 because bundled
ASM was older; tool/inspected classpaths are now separated as described above.
Corrected Gradle and binary-tool runs remain for parent after file freeze.
