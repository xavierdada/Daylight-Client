<#
    Install Eclipse Temurin JDK 8 (Windows x64) for this project.

    Why JDK 8:
        ForgeGradle 2.1 unpacks Forge's binary patches with java.util.jar.Pack200,
        an API that was removed from the JDK in 14. Building on a newer JDK fails with
        "NoClassDefFoundError: java/util/jar/Pack200", and java.util.jar is a
        restricted package, so it cannot be shimmed back in.

    What it does:
        1. asks the Adoptium API for the current JDK 8 GA build (name, size, sha256)
        2. downloads the zip, preferring the Tsinghua TUNA mirror and falling back
           to the official GitHub release
        3. verifies the published SHA-256
        4. unpacks it into %USERPROFILE%\.jdks and smoke-tests bin\java.exe

    No installer, no admin rights, no Oracle account.

    Usage:
        powershell -ExecutionPolicy Bypass -File tools\install-jdk8.ps1
        powershell -ExecutionPolicy Bypass -File tools\install-jdk8.ps1 -NoMirror
#>
[CmdletBinding()]
param(
    [string] $Destination = (Join-Path $env:USERPROFILE '.jdks'),

    # Always download from the official GitHub release, skip the Chinese mirror.
    [switch] $NoMirror
)

$ErrorActionPreference = 'Stop'

# git/curl write progress to stderr; make sure that cannot abort the script.
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    Set-Variable -Name PSNativeCommandUseErrorActionPreference -Value $false
}
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$api        = 'https://api.adoptium.net/v3/assets/latest/8/hotspot?os=windows&architecture=x64&image_type=jdk&vendor=eclipse'
$mirrorBase = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/8/jdk/x64/windows'

Write-Host 'Querying the Adoptium API ...'
$info = @(Invoke-RestMethod -Uri $api)[0]
$pkg  = $info.binary.package

Write-Host "  version : $($info.version.openjdk_version)"
Write-Host "  file    : $($pkg.name)"
Write-Host "  size    : $([math]::Round($pkg.size / 1MB, 1)) MB"

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$zip = Join-Path $env:TEMP $pkg.name

# ------------------------------------------------------------------ source ----
$url = $pkg.link
if (-not $NoMirror) {
    $mirrorUrl = "$mirrorBase/$($pkg.name)"
    try {
        $head = Invoke-WebRequest -Uri $mirrorUrl -Method Head -UseBasicParsing -TimeoutSec 20
        if ($head.StatusCode -eq 200) {
            $url = $mirrorUrl
            Write-Host '  source  : TUNA mirror (mirrors.tuna.tsinghua.edu.cn)'
        }
    }
    catch {
        Write-Host '  source  : TUNA mirror unavailable, using GitHub' -ForegroundColor DarkGray
    }
}
if ($url -eq $pkg.link -and -not $NoMirror) { Write-Host '  source  : github.com/adoptium' }

# ---------------------------------------------------------------- download ----
if (Test-Path -LiteralPath $zip) {
    Write-Host "Already downloaded, reusing $zip"
}
else {
    Write-Host "Downloading to $zip ..."
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        & curl.exe -L --fail --retry 3 --progress-bar -o $zip $url
        if ($LASTEXITCODE -ne 0) { throw "Download failed (curl exit code $LASTEXITCODE)." }
    }
    else {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    }
}

# ------------------------------------------------------------------ verify ----
Write-Host 'Verifying SHA-256 ...'
$actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $pkg.checksum.ToLowerInvariant()) {
    throw "SHA-256 mismatch, the download is corrupt.`n  expected $($pkg.checksum)`n  actual   $actual"
}
Write-Host '  ok' -ForegroundColor Green

# ----------------------------------------------------------------- extract ----
Write-Host "Extracting into $Destination ..."
Expand-Archive -LiteralPath $zip -DestinationPath $Destination -Force

$jdkHome = Get-ChildItem -LiteralPath $Destination -Directory |
    Where-Object { $_.Name -like 'jdk8u*' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jdkHome) { throw "No jdk8u* directory found under $Destination." }

$javaExe = Join-Path $jdkHome.FullName 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "$javaExe is missing - the archive was not a JDK."
}

Write-Host ''
Write-Host 'JDK 8 ready.' -ForegroundColor Green
Write-Host "  JAVA_HOME = $($jdkHome.FullName)"
Write-Host ''
& $javaExe -version
Write-Host ''
Write-Host 'Next, in the same terminal:'
Write-Host "  `$env:JAVA_HOME = '$($jdkHome.FullName)'"
Write-Host '  $env:Path      = "$env:JAVA_HOME\bin;$env:Path"'
Write-Host '  java -version            # must print 1.8.0_xxx'
Write-Host '  cd D:\NightX-Reborn-master\NightX-Reborn-master'
Write-Host '  .\gradlew.bat --stop'
Write-Host '  .\gradlew.bat setupDevWorkspace --stacktrace'
Write-Host '  .\gradlew.bat build --stacktrace'
