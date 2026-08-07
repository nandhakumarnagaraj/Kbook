# Generates a transparent-background splash logo from the opaque source logo.
#
# Both khanabook_logo.png and ic_launcher_foreground.png ship with an opaque
# white background, which renders as a white block behind the splash icon.
# Simply keying out every white pixel would also erase the white receipt inside
# the mark, so this does a flood fill inward from the border and only clears the
# white region that is connected to the edge.

Add-Type -AssemblyName System.Drawing

$src = "C:\Users\nandh\Desktop\Khanabook\KhanaBook\Android\app\src\main\res\drawable\khanabook_logo.png"
$dst = "C:\Users\nandh\Desktop\Khanabook\KhanaBook\Android\app\src\main\res\drawable\splash_logo.png"

$bmp = New-Object System.Drawing.Bitmap($src)
$w = $bmp.Width
$h = $bmp.Height

# Pull pixels into a byte array (BGRA, 4 bytes per pixel) for fast access.
$rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
$data = $bmp.LockBits($rect,
    [System.Drawing.Imaging.ImageLockMode]::ReadWrite,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$bytes = New-Object byte[] ($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)

# Treat anything brighter than this on all channels as background white.
$threshold = 235
$visited = New-Object 'bool[]' ($w * $h)
$queue = New-Object System.Collections.Generic.Queue[int]

function Test-IsWhite([int]$idx) {
    $b = $bytes[$idx]; $g = $bytes[$idx + 1]; $r = $bytes[$idx + 2]
    return ($r -ge $threshold -and $g -ge $threshold -and $b -ge $threshold)
}

# Seed the queue with every border pixel that is white.
for ($x = 0; $x -lt $w; $x++) {
    foreach ($y in @(0, ($h - 1))) {
        $flat = $y * $w + $x
        if (-not $visited[$flat] -and (Test-IsWhite ($y * $stride + $x * 4))) {
            $visited[$flat] = $true
            $queue.Enqueue($flat)
        }
    }
}
for ($y = 0; $y -lt $h; $y++) {
    foreach ($x in @(0, ($w - 1))) {
        $flat = $y * $w + $x
        if (-not $visited[$flat] -and (Test-IsWhite ($y * $stride + $x * 4))) {
            $visited[$flat] = $true
            $queue.Enqueue($flat)
        }
    }
}

# Flood fill inward, clearing alpha on each connected white pixel.
$cleared = 0
while ($queue.Count -gt 0) {
    $flat = $queue.Dequeue()
    $y = [int][math]::Floor($flat / $w)
    $x = $flat - ($y * $w)
    $idx = $y * $stride + $x * 4

    $bytes[$idx + 3] = 0   # alpha -> fully transparent
    $cleared++

    foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
        $nx = $x + $d[0]
        $ny = $y + $d[1]
        if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $w -or $ny -ge $h) { continue }
        $nflat = $ny * $w + $nx
        if ($visited[$nflat]) { continue }
        if (Test-IsWhite ($ny * $stride + $nx * 4)) {
            $visited[$nflat] = $true
            $queue.Enqueue($nflat)
        }
    }
}

[System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $bytes.Length)
$bmp.UnlockBits($data)
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "Source : ${w}x${h}"
Write-Host "Cleared: $cleared pixels -> transparent"
Write-Host "Wrote  : $dst"
