# Verifies the HUD renders correctly on the light theme.
#
# Guards against the bug where a leaked GL blend state made every string drawn after the
# avatar lose its font-atlas transparency: glyphs came out pure white inside solid pure
# black boxes. Correct text is #1C1C22 on a light panel, i.e. neither extreme.
#
# Metric: pure-white pixels that have a pure-black pixel within 3px, counted over the
# top-left HUD area (where the watermark and the session-info panel live).
#   measured on a screenshot showing the bug : ~1260
#   measured with the black boxes erased     :   ~76
# so the threshold has roughly 5x headroom.
#
# Usage:
#   .\tools\verify-hud.ps1                       # newest F2 screenshot
#   .\tools\verify-hud.ps1 -Screenshot <path>
#
# Exit codes: 0 = PASS, 1 = FAIL, 2 = no screenshot, 3 = unreadable image.

param(
    [string]$Screenshot,
    [int]$Threshold = 400
)

Add-Type -AssemblyName System.Drawing

function Find-NewestScreenshot {
    $candidates = @(
        (Join-Path $PSScriptRoot '..\run\screenshots'),
        (Join-Path $env:APPDATA '.minecraft\screenshots')
    )
    foreach ($dir in $candidates) {
        if (Test-Path $dir) {
            $file = Get-ChildItem $dir -Filter *.png |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($file) { return $file.FullName }
        }
    }
    return $null
}

if (-not $Screenshot) { $Screenshot = Find-NewestScreenshot }
if (-not $Screenshot) {
    Write-Output 'FAIL: no screenshot found; pass -Screenshot <path>.'
    exit 2
}
if (-not (Test-Path $Screenshot)) {
    Write-Output "FAIL: screenshot not found: $Screenshot"
    exit 2
}

$bmp = [System.Drawing.Bitmap]::FromFile($Screenshot)
try {
    $rect = New-Object System.Drawing.Rectangle(0, 0, $bmp.Width, $bmp.Height)
    $data = $bmp.LockBits(
        $rect,
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $bmp.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $bmp.UnlockBits($data)
} catch {
    Write-Output "FAIL: cannot read image: $($_.Exception.Message)"
    $bmp.Dispose()
    exit 3
}
$imgW = $bmp.Width
$imgH = $bmp.Height
$bmp.Dispose()

# At any GUI scale the watermark and the session panel sit well inside this area.
$regionW = [int]($imgW * 0.40)
$regionH = [int]($imgH * 0.30)

$black = New-Object 'bool[]' ($regionW * $regionH)
$whiteX = New-Object 'int[]' ($regionW * $regionH)
$whiteY = New-Object 'int[]' ($regionW * $regionH)
$whiteCount = 0

for ($y = 0; $y -lt $regionH; $y++) {
    $rowOff = $y * $stride
    $gridOff = $y * $regionW
    for ($x = 0; $x -lt $regionW; $x++) {
        $i = $rowOff + $x * 3
        $b = $bytes[$i]; $g = $bytes[$i + 1]; $r = $bytes[$i + 2]
        if ($r -lt 20 -and $g -lt 20 -and $b -lt 20) {
            $black[$gridOff + $x] = $true
        } elseif ($r -gt 250 -and $g -gt 250 -and $b -gt 250) {
            $whiteX[$whiteCount] = $x
            $whiteY[$whiteCount] = $y
            $whiteCount++
        }
    }
}

# Count white glyph pixels that sit inside a black box.
$blackAdjacentWhite = 0
for ($k = 0; $k -lt $whiteCount; $k++) {
    $x = $whiteX[$k]; $y = $whiteY[$k]
    $hit = $false
    for ($dy = -3; $dy -le 3 -and -not $hit; $dy++) {
        $ny = $y + $dy
        if ($ny -lt 0 -or $ny -ge $regionH) { continue }
        $nOff = $ny * $regionW
        for ($dx = -3; $dx -le 3; $dx++) {
            $nx = $x + $dx
            if ($nx -lt 0 -or $nx -ge $regionW) { continue }
            if ($black[$nOff + $nx]) { $hit = $true; break }
        }
    }
    if ($hit) { $blackAdjacentWhite++ }
}

Write-Output "screenshot   : $(Split-Path $Screenshot -Leaf) ($imgW x $imgH)"
Write-Output "scan area    : top-left $regionW x $regionH"
Write-Output "pure white   : $whiteCount pixels"
Write-Output "bug signature: $blackAdjacentWhite  (threshold $Threshold)"

if ($blackAdjacentWhite -le $Threshold) {
    Write-Output 'RESULT: PASS - HUD text renders with transparency, no black boxes.'
    exit 0
}

Write-Output 'RESULT: FAIL - white glyphs sitting inside black boxes.'
Write-Output '        Expected dark text (#1C1C22) on the light panel instead.'
Write-Output '        Check the GL state handling in Interface.drawCustomHead,'
Write-Output '        Interface.drawRoundedAvatar and Interface.drawSmoothOutlineRect:'
Write-Output '        they must leave blending enabled and must not mix raw glEnable/'
Write-Output '        glDisable with GlStateManager.'
exit 1
