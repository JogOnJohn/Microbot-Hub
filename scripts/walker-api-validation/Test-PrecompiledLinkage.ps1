# Plans by default. -Execute compiles/runs ONLY the offline verifier, never Hub/client.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ClientJar,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ClientSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ClientCommit,
    [Parameter(Mandatory)][string]$PluginPath,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$PluginSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$PluginCommit,
    [string]$DependencyManifest,
    [string]$JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot',
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label = 'linkage-candidate',
    [switch]$SelfTest,
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientJar).Path
$plugin = (Resolve-Path -LiteralPath $PluginPath).Path
$source = Join-Path $PSScriptRoot 'PrecompiledLinkage.java'
$hub = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$out = Join-Path $hub "build/walker-api-validation/$Label"
if (Test-Path -LiteralPath $out) { throw "Use a fresh label: $out" }
function Get-BinaryFingerprint([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $root = (Resolve-Path -LiteralPath $Path).Path.TrimEnd('\', '/')
    [string[]]$relative = @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.class' | ForEach-Object {
        $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    })
    if (!$relative.Count) { throw "No class files: $Path" }
    [Array]::Sort($relative, [StringComparer]::Ordinal)
    $text = [Text.StringBuilder]::new()
    foreach ($name in $relative) {
        $hash = (Get-FileHash -LiteralPath (Join-Path $root $name) -Algorithm SHA256).Hash.ToLowerInvariant()
        [void]$text.Append($name).Append([char]0).Append($hash).Append("`n")
    }
    $digest = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($text.ToString()))).ToLowerInvariant() }
    finally { $digest.Dispose() }
}
function Assert-Hash([string]$Path, [string]$Expected) {
    $actual = Get-BinaryFingerprint $Path
    if ($actual -ne $Expected) { throw "Exact binary SHA-256 mismatch: $Path (actual $actual)" }
}
Assert-Hash $client $ClientSha256
Assert-Hash $plugin $PluginSha256
$dependencies = @()
if ($DependencyManifest) {
    $manifestPath = (Resolve-Path -LiteralPath $DependencyManifest).Path
    $dependencies = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
    foreach ($dep in $dependencies) {
        if (!$dep.path -or $dep.sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Each dependency needs absolute path and sha256.' }
        if (![IO.Path]::IsPathRooted($dep.path) -or !(Test-Path -LiteralPath $dep.path -PathType Leaf)) {
            throw "Unavailable exact dependency JAR: $($dep.path)"
        }
        $dep.path = (Resolve-Path -LiteralPath $dep.path).Path
        Assert-Hash $dep.path $dep.sha256
    }
}
$cpFiles = @($client) + @($dependencies | ForEach-Object path)
if (@($cpFiles | Select-Object -Unique).Count -ne $cpFiles.Count -or $cpFiles -contains $plugin) {
    throw 'Duplicate input paths or plugin reused as a client/dependency.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$asmProviders = @()
# Explicit dependencies precede the candidate for TOOLING ONLY.
foreach ($file in (@($dependencies | ForEach-Object path) + @($client))) {
    $zip = [IO.Compression.ZipFile]::OpenRead($file)
    try { if ($zip.GetEntry('org/objectweb/asm/ClassReader.class')) { $asmProviders += $file } }
    finally { $zip.Dispose() }
}
if (!$asmProviders.Count) { throw 'ASM core unavailable: provide an exact hash-pinned ASM 9 JAR via DependencyManifest. No download/fallback.' }
$toolAsm = $asmProviders[0]
# A supplied core ASM JAR is a tool dependency, not an alternate inspected client.
# Set role="both" explicitly only if an old binary itself also requires that library.
$runtimeDeps = @($dependencies | Where-Object { $_.path -ne $toolAsm -or $_.role -eq 'both' })
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
foreach ($exe in @($javac, $java)) { if (!(Test-Path -LiteralPath $exe)) { throw "Missing JDK17 executable: $exe" } }
$sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
$launcherHash = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
$plan = [ordered]@{
    kind = 'scoped-precompiled-symbolic-linkage'; client = $client; clientSha256 = $ClientSha256
    clientCommitAssertion = $ClientCommit; plugin = $plugin; pluginSha256 = $PluginSha256
    pluginCommitAssertion = $PluginCommit; dependencies = $dependencies; javaHome = $JavaHome
    toolAsm = $toolAsm; toolClasspath = @($toolAsm); inspectedDependencies = $runtimeDeps
    sourceSha256 = $sourceHash; launcherSha256 = $launcherHash; selfTest = [bool]$SelfTest
    output = $out; classDirectoryFingerprint = 'SHA256(UTF8(ordinal-relative-path + NUL + lowercase-class-SHA256 + LF per class))'
}
if (!$Execute) { $plan | ConvertTo-Json -Depth 6; return }
$version = @(& $java -version 2>&1 | ForEach-Object { "$_" })
if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'version "17\.') { throw 'JDK17 is required.' }
New-Item -ItemType Directory -Path (Join-Path $out 'tool-classes') -Force | Out-Null
$plan.javaVersion = $version
$plan | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $out 'plan.json') -Encoding UTF8
& $javac --release 17 -proc:none -cp $toolAsm -d (Join-Path $out 'tool-classes') $source 2>&1 |
    Tee-Object -FilePath (Join-Path $out 'tool-compile.log')
if ($LASTEXITCODE -ne 0) { throw "Verifier compilation failed; ASM 9 core must be present. See $out" }
$javaArgs = @('-Xverify:all', "-Dwalker.audit.asm=$toolAsm", '-cp',
    ((Join-Path $out 'tool-classes') + [IO.Path]::PathSeparator + $toolAsm), 'PrecompiledLinkage')
if ($SelfTest) { $javaArgs += '--self-test' }
else { $javaArgs += @($client, $plugin) + @($runtimeDeps | ForEach-Object path) }
& $java @javaArgs 2>&1 | Tee-Object -FilePath (Join-Path $out 'linkage.jsonl')
$code = $LASTEXITCODE
Assert-Hash $client $ClientSha256
Assert-Hash $plugin $PluginSha256
foreach ($dep in $dependencies) { Assert-Hash $dep.path $dep.sha256 }
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $sourceHash -or
    (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash -ne $launcherHash) { throw 'Verifier inputs changed during run.' }
[ordered]@{ passed = $code -eq 0; exitCode = $code; selfTest = [bool]$SelfTest
    completedUtc = [DateTime]::UtcNow.ToString('o'); claim = 'scoped symbolic linkage only, not gameplay or whole-JVM verification' } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out 'result.json') -Encoding UTF8
if ($code -ne 0) { throw "Linkage failed/incomplete; inspect $out" }
Write-Output "SCOPED SYMBOLIC LINKAGE ONLY: $out"
