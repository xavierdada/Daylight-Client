<#
    Fix the ForgeGradle plugin resolution failure:

        Plugin [id: 'net.minecraftforge.gradle.forge',
                artifact: 'com.github.ccbluex:ForgeGradle:a3d86a59c0']
        was not found in any of the following sources

    Why it happens
    --------------
    settings.gradle maps the plugin id to a JitPack build of the CCBlueX ForgeGradle
    fork, because the stock ForgeGradle 2.x cannot run on Gradle 6.9.1 / MC 1.8.9.
    JitPack currently answers HTTP 404 for that artifact, so Gradle cannot resolve it.

    What this script does, in order
    -------------------------------
      1. Tests the coordinate currently configured in gradle.properties.
         If it downloads, there is nothing to fix.
      2. Tests other JitPack spellings of the same commit (same code, different
         build record) and switches to the first one that answers.
      3. Otherwise it clones CCBlueX/ForgeGradle at that commit, builds it with
         "gradlew publishToMavenLocal" and repoints gradle.properties at the local
         artifact (net.minecraftforge.gradle:ForgeGradle:2.1).

    Usage
    -----
        powershell -ExecutionPolicy Bypass -File tools\fix-forgegradle.ps1
        powershell -ExecutionPolicy Bypass -File tools\fix-forgegradle.ps1 -SkipJitPack

    Build ForgeGradle from source needs JDK 8-16 (its own wrapper uses Gradle 7.0.2).
    Building this mod afterwards needs JDK 8.
#>
[CmdletBinding()]
param(
    # Go straight to the local source build, do not probe JitPack at all.
    [switch] $SkipJitPack
)

$ErrorActionPreference = 'Stop'

# PowerShell 7.4+ can turn a native command's stderr into a terminating error.
# git and gradlew both write progress to stderr, so make sure that cannot abort us.
$nativePref = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
if ($nativePref) { Set-Variable -Name PSNativeCommandUseErrorActionPreference -Value $false }

$projectRoot = Split-Path -Parent $PSScriptRoot
$propsFile = Join-Path $projectRoot 'gradle.properties'
$commit = 'a3d86a59c08c471c8a9b822642d0b7ce71ff4455'
$localCoordinate = 'net.minecraftforge.gradle:ForgeGradle:2.1'
# The clone directory name becomes the Maven artifactId (the fork has no
# settings.gradle), so it must stay "ForgeGradle".
$workDir = Join-Path $env:USERPROFILE '.daylight-tools\ForgeGradle'
$localJar = Join-Path $env:USERPROFILE '.m2\repository\net\minecraftforge\gradle\ForgeGradle\2.1\ForgeGradle-2.1.jar'

# Same commit, several JitPack build records. All produce identical code.
$jitpackCandidates = @(
    'a3d86a59c0',
    $commit,
    'a3d86a59c08',
    'a3d86a5'
)

function Write-Step {
    param([string] $Text)
    Write-Host ''
    Write-Host "== $Text" -ForegroundColor Cyan
}

function Get-HttpStatus {
    param([Parameter(Mandatory = $true)][string] $Url)
    try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }
    try {
        $request = [System.Net.WebRequest]::Create($Url)
        $request.Method = 'GET'
        $request.Timeout = 30000
        $request.AllowAutoRedirect = $true
        $response = $request.GetResponse()
        $status = [int]$response.StatusCode
        $response.Close()
        return $status
    }
    catch [System.Net.WebException] {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
    catch {
        return 0
    }
}

function Get-ConfiguredCoordinate {
    $line = Select-String -LiteralPath $propsFile -Pattern '^\s*forgegradle_coordinate\s*=' |
        Select-Object -First 1
    if (-not $line) { return $null }
    return ($line.Line -split '=', 2)[1].Trim()
}

function Set-ConfiguredCoordinate {
    param([Parameter(Mandatory = $true)][string] $Coordinate)
    $lines = @(Get-Content -LiteralPath $propsFile)
    $replaced = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*forgegradle_coordinate\s*=') {
            $lines[$i] = "forgegradle_coordinate = $Coordinate"
            $replaced = $true
        }
    }
    if (-not $replaced) { $lines += "forgegradle_coordinate = $Coordinate" }
    # UTF-8 without BOM: Gradle reads gradle.properties byte-wise and a BOM breaks the first key.
    [System.IO.File]::WriteAllLines($propsFile, $lines)
    Write-Host "  gradle.properties -> forgegradle_coordinate = $Coordinate" -ForegroundColor Green
}

function Test-JitPackVersion {
    param([Parameter(Mandatory = $true)][string] $Version)
    $url = "https://jitpack.io/com/github/ccbluex/ForgeGradle/$Version/ForgeGradle-$Version.pom"
    $status = Get-HttpStatus $url
    Write-Host ("  {0,-42} HTTP {1}" -f $Version, $status)
    return $status -eq 200
}

if (-not (Test-Path -LiteralPath $propsFile)) {
    throw "Cannot find $propsFile - run this script from inside the project."
}

Write-Host "Project : $projectRoot"
Write-Host "Commit  : $commit"

# ---------------------------------------------------------------- step 1 -----
if (-not $SkipJitPack) {
    Write-Step '1/3  Checking the coordinate currently configured'

    $configured = Get-ConfiguredCoordinate
    Write-Host "  current: $configured"

    if ($configured -eq $localCoordinate) {
        if (Test-Path -LiteralPath $localJar) {
            Write-Host '  A local ForgeGradle build is already configured and present.' -ForegroundColor Green
            Write-Host '  Nothing to fix - just run:  .\gradlew.bat build' -ForegroundColor Green
            exit 0
        }
        Write-Host '  Configured for the local build but the artifact is missing; rebuilding it.' -ForegroundColor Yellow
    }
    elseif ($configured -and (Test-JitPackVersion ($configured -replace '^.*:', ''))) {
        Write-Host '  That coordinate resolves. Nothing to fix.' -ForegroundColor Green
        Write-Host '  If Gradle still fails, it is a network problem, not a missing artifact:' -ForegroundColor Yellow
        Write-Host '  check that https://jitpack.io is reachable from this machine.' -ForegroundColor Yellow
        exit 0
    }
    else {
        Write-Host '  Not downloadable - the JitPack artifact is missing.' -ForegroundColor Yellow
    }

    # ------------------------------------------------------------ step 2 -----
    Write-Step '2/3  Trying other JitPack build records for the same commit'

    foreach ($version in $jitpackCandidates) {
        if ($version -eq ($configured -replace '^.*:', '')) { continue }
        if (Test-JitPackVersion $version) {
            Write-Host "  Found a working build record: $version" -ForegroundColor Green
            Set-ConfiguredCoordinate "com.github.ccbluex:ForgeGradle:$version"
            Write-Host ''
            Write-Host 'Done. Now run:  .\gradlew.bat build' -ForegroundColor Green
            exit 0
        }
    }

    Write-Host '  No JitPack build record for this commit is downloadable.' -ForegroundColor Yellow
}

# ---------------------------------------------------------------- step 3 -----
Write-Step '3/3  Building ForgeGradle from source into Maven Local'

foreach ($tool in @('git')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "'$tool' is not on PATH. Install Git, or fetch the plugin another way."
    }
}

# Route through cmd so java's stderr output cannot be mistaken for a terminating error.
$javaVersion = (cmd /c 'java -version 2>&1' | Select-Object -First 1)
if (-not $javaVersion) {
    throw "No Java found on PATH. ForgeGradle's own build needs JDK 8-16."
}
Write-Host "  java on PATH : $javaVersion"

# The Gradle wrapper prefers JAVA_HOME over PATH, so judge the version it will use.
$effectiveVersion = $javaVersion
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $fromHome = (cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1" | Select-Object -First 1)
    if ($fromHome) {
        $effectiveVersion = $fromHome
        Write-Host "  JAVA_HOME    : $env:JAVA_HOME"
        Write-Host "  java used    : $fromHome"
    }
}

$javaMajor = 0
if ($effectiveVersion -match '"(\d+)') { $javaMajor = [int]$Matches[1] }
if ($javaMajor -ge 17) {
    throw ("Gradle 7.0.2 (used to build ForgeGradle) supports JDK 8-16 only, but it would run on Java $javaMajor. " +
           "Point JAVA_HOME at a JDK 8-16 and run this script again, for example: " +
           "`$env:JAVA_HOME = 'C:\Users\HP\.jdks\corretto-15.0.2'")
}

if (Test-Path -LiteralPath (Join-Path $workDir '.git')) {
    Write-Host "  reusing $workDir"
    & git -C $workDir fetch --all --tags --quiet
    if ($LASTEXITCODE -ne 0) { throw 'git fetch failed.' }
}
else {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $workDir) | Out-Null
    Write-Host "  cloning CCBlueX/ForgeGradle -> $workDir"
    & git clone --quiet https://github.com/CCBlueX/ForgeGradle $workDir
    if ($LASTEXITCODE -ne 0) { throw 'git clone failed.' }
}

& git -C $workDir checkout --quiet $commit
if ($LASTEXITCODE -ne 0) { throw "git checkout $commit failed." }

$buildExit = 1
Push-Location $workDir
try {
    Write-Host '  running: gradlew publishToMavenLocal --no-daemon'
    Write-Host '  (the first run downloads Gradle 7.0.2 and ForgeGradle''s dependencies)'
    & .\gradlew.bat publishToMavenLocal --no-daemon
    $buildExit = $LASTEXITCODE
}
finally {
    Pop-Location
}
if ($buildExit -ne 0) {
    throw "ForgeGradle build failed with exit code $buildExit. Scroll up for the reason."
}

if (-not (Test-Path -LiteralPath $localJar)) {
    throw "Build reported success but $localJar is missing."
}
Write-Host "  installed: $localJar" -ForegroundColor Green

Set-ConfiguredCoordinate $localCoordinate

Write-Host ''
Write-Host 'Done. Now run:  .\gradlew.bat build' -ForegroundColor Green
Write-Host 'settings.gradle already lists mavenLocal() first, so the local artifact is picked up.'
Write-Host ''
Write-Host 'To go back to JitPack later, set in gradle.properties:'
Write-Host '  forgegradle_coordinate = com.github.ccbluex:ForgeGradle:a3d86a59c0'
