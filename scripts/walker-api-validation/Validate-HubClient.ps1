# Plans by default. -Execute is an explicit, local-only compilation request.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ClientJar,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ClientSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ClientCommit,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedHubCommit,
    [string]$HubPath = (Join-Path $PSScriptRoot '../..'),
    [string[]]$Plugins = @('HuntersRumoursPlugin', 'BarrowsPlugin', 'HSBlackJackPlugin',
        'MicroAgilityPlugin', 'TitheFarmingPlugin', 'AutoHunterPlugin', 'BlackjackPlugin'),
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label = 'candidate',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$OnlineDependencies,
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
$hub = (Resolve-Path -LiteralPath $HubPath).Path
$jar = (Resolve-Path -LiteralPath $ClientJar).Path
$init = Join-Path $PSScriptRoot 'exact-client.init.gradle'
if ($hub -match '(?i)Microbot-Hub-shared-utilities(?:[\\/]|$)') {
    throw 'The protected Shared Automation checkout is off limits. Use an isolated committed snapshot.'
}
if (!(Test-Path -LiteralPath (Join-Path $hub 'gradlew.bat'))) { throw 'Not a Hub checkout.' }
function Git-Read([string[]]$Arguments) {
    $result = & git -c "safe.directory=$($hub.Replace('\', '/'))" -C $hub @Arguments
    if ($LASTEXITCODE -ne 0) { throw "git failed: $Arguments" }
    return $result
}
$head = Git-Read @('rev-parse', 'HEAD')
if ($head -ne $ExpectedHubCommit) { throw "Hub HEAD $head != expected $ExpectedHubCommit" }
$dirty = @(Git-Read @('status', '--porcelain=v1', '--untracked-files=all'))
$unowned = @($dirty | Where-Object {
    $_.Substring(3).Replace('\', '/') -notmatch '^(scripts/walker-api-validation/|docs/walker-api-synthesis/EXACT-ARTIFACT-VALIDATION\.md$)'
})
if ($unowned.Count) { throw "Unowned dirty paths in target checkout; refusing compilation:`n$($unowned -join "`n")" }
$actualHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $ClientSha256.ToLowerInvariant()) { throw 'Candidate client SHA-256 mismatch.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in @('net/runelite/client/plugins/microbot/util/walker/Rs2Walker.class',
        'net/runelite/api/coords/WorldPoint.class')) {
        if (!$zip.GetEntry($entry)) { throw "Candidate is not a complete client JAR: missing $entry" }
    }
} finally { $zip.Dispose() }
if (!$JavaHome) { throw 'Supply -JavaHome with the established Adoptium JDK 11 directory.' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $java)) { throw "Missing $java" }
$tasks = @()
if (!$Plugins.Count) { throw 'Select at least one plugin.' }
foreach ($plugin in $Plugins) {
    if ($plugin -notmatch '^[A-Za-z][A-Za-z0-9]*Plugin$') { throw "Invalid plugin class: $plugin" }
    $matches = @(Get-ChildItem -LiteralPath (Join-Path $hub 'src/main/java/net/runelite/client/plugins/microbot') -Recurse -File -Filter "$plugin.java")
    # The Hub discovers only direct children of a plugin package, not nested entry swappers.
    $matches = @($matches | Where-Object { $_.Directory.Parent.Name -eq 'microbot' })
    if ($matches.Count -ne 1) { throw "Expected exactly one top-level $plugin.java; found $($matches.Count)" }
    $package = $matches[0].Directory.Name
    $tasks += 'compile' + $package.Substring(0, 1).ToUpperInvariant() + $package.Substring(1) + 'Java'
}
if (@($Plugins | Select-Object -Unique).Count -ne $Plugins.Count) { throw 'Duplicate plugin names.' }
$out = Join-Path $hub "build/walker-api-validation/$Label"
if (Test-Path -LiteralPath $out) { throw "Use a fresh -Label; evidence already exists: $out" }
$gradleArgs = @('--init-script', $init, '--no-daemon', '--max-workers=1', '--console=plain',
    '--no-build-cache', '--rerun-tasks', '--no-configuration-cache',
    "-PmicrobotClientPath=$jar", '-PmicrobotClientVersion=exact-local',
    "-PpluginList=$($Plugins -join ',')", "-PwalkerAuditSha256=$actualHash",
    "-PwalkerAuditOutput=$out")
if (!$OnlineDependencies) { $gradleArgs += '--offline' }
$gradleArgs += $tasks
$plan = [ordered]@{
    schema = 1; kind = 'source-compile-only'; hub = $hub; hubCommit = $head
    client = $jar; clientSha256 = $actualHash; clientCommitAssertion = $ClientCommit
    plugins = $Plugins; tasks = $tasks; ownedDirtyPaths = $dirty; javaHome = $JavaHome
    command = 'gradlew.bat'; arguments = $gradleArgs; output = $out
    helperSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
    initSha256 = (Get-FileHash -LiteralPath $init -Algorithm SHA256).Hash
}
if (!$Execute) { $plan | ConvertTo-Json -Depth 6; return }
$oldJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    $version = @(& $java -version 2>&1 | ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'version "11\.') {
        throw "The Hub Gradle JVM must be JDK 11: $version"
    }
    New-Item -ItemType Directory -Path $out | Out-Null
    $plan.javaVersion = $version
    $plan | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $out 'plan.json') -Encoding UTF8
    $code = -1
    Push-Location $hub
    try {
        & (Join-Path $hub 'gradlew.bat') @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $out 'compile.log')
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    $afterHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    $afterHead = Git-Read @('rev-parse', 'HEAD')
    $afterDirty = @(Git-Read @('status', '--porcelain=v1', '--untracked-files=all'))
    $stable = $afterHash -eq $actualHash -and $afterHead -eq $head -and
        ($afterDirty -join "`n") -ceq ($dirty -join "`n") -and
        (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash -eq $plan.helperSha256 -and
        (Get-FileHash -LiteralPath $init -Algorithm SHA256).Hash -eq $plan.initSha256
    $classpathEvidence = Join-Path $out 'classpaths.json'
    $guardsComplete = $false
    if (Test-Path -LiteralPath $classpathEvidence) {
        $observed = (Get-Content -LiteralPath $classpathEvidence -Raw | ConvertFrom-Json).PSObject.Properties.Name
        $expected = @($tasks | ForEach-Object { ':' + $_ })
        $guardsComplete = @($observed).Count -eq $expected.Count -and
            @(Compare-Object -ReferenceObject $expected -DifferenceObject @($observed)).Count -eq 0
    }
    $passed = $code -eq 0 -and $stable -and $guardsComplete
    [ordered]@{ passed = $passed; exitCode = $code; inputsStable = $stable; guardsComplete = $guardsComplete
        clientSha256After = $afterHash; hubCommitAfter = $afterHead
        completedUtc = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out 'result.json') -Encoding UTF8
    if (!$passed) { throw "Exact-artifact compile failed or inputs changed; inspect $out" }
    Write-Output "SOURCE COMPILE PASSED ONLY: $out"
} finally { $env:JAVA_HOME = $oldJavaHome }
