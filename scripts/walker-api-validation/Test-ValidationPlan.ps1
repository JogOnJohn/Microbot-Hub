# Structural tooling test only: no Gradle, javac, client or plugin execution.
[CmdletBinding()]
param([string]$JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot')
$ErrorActionPreference = 'Stop'
$hub = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$head = & git -c "safe.directory=$($hub.Replace('\', '/'))" -C $hub rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'git failed' }
$fixture = Join-Path $PSScriptRoot ('fixture-' + [guid]::NewGuid().ToString('n') + '.jar')
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    $zip = [IO.Compression.ZipFile]::Open($fixture, [IO.Compression.ZipArchiveMode]::Create)
    try {
        # Empty entries deliberately do not represent a usable candidate client.
        $zip.CreateEntry('net/runelite/client/plugins/microbot/util/walker/Rs2Walker.class') | Out-Null
        $zip.CreateEntry('net/runelite/api/coords/WorldPoint.class') | Out-Null
    } finally { $zip.Dispose() }
    $argsForPlan = @{
        ClientJar = $fixture; ClientSha256 = (Get-FileHash $fixture -Algorithm SHA256).Hash
        ClientCommit = ('1' * 40); ExpectedHubCommit = $head; JavaHome = $JavaHome
        Label = ('plan-test-' + [guid]::NewGuid().ToString('n'))
    }
    $helper = Join-Path $PSScriptRoot 'Validate-HubClient.ps1'
    $plan = & $helper @argsForPlan | ConvertFrom-Json
    if ($plan.tasks.Count -ne 7 -or $plan.tasks -contains 'compileJava' -or
        $plan.arguments -notcontains '--offline' -or $plan.arguments -notcontains '-PmicrobotClientVersion=exact-local' -or
        $plan.arguments -notcontains 'compileHuntersrumoursJava' -or (Test-Path -LiteralPath $plan.output)) {
        throw 'Plan contract failed'
    }
    function Expect-Failure([hashtable]$Overrides, [string]$Message) {
        $testArgs = $argsForPlan.Clone()
        foreach ($key in $Overrides.Keys) { $testArgs[$key] = $Overrides[$key] }
        $caught = $false
        try { & $helper @testArgs | Out-Null }
        catch {
            if ($_.Exception.Message -notlike "*$Message*") { throw }
            $caught = $true
        }
        if (!$caught) { throw "Expected rejection: $Message" }
    }
    Expect-Failure @{ ClientSha256 = ('0' * 64) } 'SHA-256 mismatch'
    Expect-Failure @{ ExpectedHubCommit = ('0' * 40) } '!= expected'
    Expect-Failure @{ Plugins = @('DoesNotExistPlugin') } 'found 0'
    Expect-Failure @{ Plugins = @('BarrowsPlugin', 'BarrowsPlugin') } 'Duplicate plugin'
    $wrapperJar = Join-Path $hub 'gradle/wrapper/gradle-wrapper.jar'
    Expect-Failure @{ ClientJar = $wrapperJar; ClientSha256 = (Get-FileHash $wrapperJar -Algorithm SHA256).Hash } 'missing net/runelite'
    Write-Output 'PASS: plan-only structural fixture and five rejection cases; no builds executed.'
} finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture }
}
