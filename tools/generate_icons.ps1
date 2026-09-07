# Divine & Beast —— 图标生成器（System.Drawing, Windows PowerShell 5.1+）
# 输出：assets/divinebeast/textures/item/*.png (16x16) 与 textures/slot/*.png (32x32)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$script:itemOut = Join-Path $PSScriptRoot '..\src\main\resources\assets\divinebeast\textures\item'
$script:slotOut = Join-Path $PSScriptRoot '..\src\main\resources\assets\divinebeast\textures\slot'
New-Item -ItemType Directory -Force -Path $script:itemOut, $script:slotOut | Out-Null

function Col([int]$r, [int]$g, [int]$b, [int]$a = 255) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }

function Save-Scaled([System.Drawing.Bitmap]$big, [string]$path, [int]$final) {
    $small = New-Object System.Drawing.Bitmap($final, $final)
    $sg = [System.Drawing.Graphics]::FromImage($small)
    $sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $sg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $sg.DrawImage($big, 0, 0, $final, $final)
    $small.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $sg.Dispose(); $small.Dispose(); $big.Dispose()
}

# 通用绘制工具（全部以 0..1 归一化坐标，内部换算到画布）
function G-Bg($g, $s, $c1, $c2) {
    $rect = New-Object System.Drawing.RectangleF(0, 0, $s, $s)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45.0)
    $g.FillRectangle($brush, $rect); $brush.Dispose()
}
function G-Disc($g, $s, $cx, $cy, $r, $c) {
    $b = New-Object System.Drawing.SolidBrush($c)
    $g.FillEllipse($b, ($cx - $r) * $s, ($cy - $r) * $s, (2 * $r) * $s, (2 * $r) * $s)
    $b.Dispose()
}
function G-Ring($g, $s, $cx, $cy, $r, $w, $c) {
    $pen = New-Object System.Drawing.Pen($c, [float]($w * $s))
    $g.DrawEllipse($pen, ($cx - $r) * $s, ($cy - $r) * $s, (2 * $r) * $s, (2 * $r) * $s)
    $pen.Dispose()
}
function G-Line($g, $s, $x1, $y1, $x2, $y2, $w, $c) {
    $pen = New-Object System.Drawing.Pen($c, [float]($w * $s))
    $g.DrawLine($pen, $x1 * $s, $y1 * $s, $x2 * $s, $y2 * $s)
    $pen.Dispose()
}
function G-Poly($g, $s, $pts, $c) {
    $b = New-Object System.Drawing.SolidBrush($c)
    $scaled = New-Object System.Drawing.PointF[]($pts.Length)
    for ($i = 0; $i -lt $pts.Length; $i++) {
        $p = $pts[$i]
        $scaled[$i] = New-Object System.Drawing.PointF([single]($p[0] * $s), [single]($p[1] * $s))
    }
    $g.FillPolygon($b, $scaled)
    $b.Dispose()
}
function G-Tri($g, $s, $x1, $y1, $x2, $y2, $x3, $y3, $c) {
    $b = New-Object System.Drawing.SolidBrush($c)
    $pts = @(
        (New-Object System.Drawing.PointF([single]($x1 * $s), [single]($y1 * $s))),
        (New-Object System.Drawing.PointF([single]($x2 * $s), [single]($y2 * $s))),
        (New-Object System.Drawing.PointF([single]($x3 * $s), [single]($y3 * $s)))
    )
    $g.FillPolygon($b, $pts)
    $b.Dispose()
}
function G-Rect($g, $s, $x, $y, $w, $h, $c) {
    $b = New-Object System.Drawing.SolidBrush($c)
    $g.FillRectangle($b, $x * $s, $y * $s, $w * $s, $h * $s)
    $b.Dispose()
}
function G-RectFrame($g, $s, $x, $y, $w, $h, $lw, $c) {
    $pen = New-Object System.Drawing.Pen($c, [float]($lw * $s))
    $g.DrawRectangle($pen, $x * $s, $y * $s, $w * $s, $h * $s)
    $pen.Dispose()
}

# ============ 图形元素 ============
function M-Eye($g, $s, $cx, $cy, $rx, $cWhite, $cIris, $cPupil) {
    # 竖瞳眼：外白杏仁 + 瞳孔
    $b = New-Object System.Drawing.SolidBrush($cWhite)
    $g.FillEllipse($b, ($cx - $rx) * $s, ($cy - $rx * 0.62) * $s, (2 * $rx) * $s, (2 * $rx * 0.62) * $s)
    $b.Dispose()
    $b = New-Object System.Drawing.SolidBrush($cIris)
    $g.FillEllipse($b, ($cx - $rx * 0.55) * $s, ($cy - $rx * 0.4) * $s, (2 * $rx * 0.55) * $s, (2 * $rx * 0.4) * $s)
    $b.Dispose()
    $b = New-Object System.Drawing.SolidBrush($cPupil)
    $g.FillEllipse($b, ($cx - $rx * 0.16) * $s, ($cy - $rx * 0.5) * $s, (2 * $rx * 0.16) * $s, (2 * $rx) * $s)
    $b.Dispose()
}
function M-Crown($g, $s, $cGold, $cGem) {
    G-Poly $g $s @( @(0.14, 0.72), @(0.20, 0.34), @(0.33, 0.55), @(0.50, 0.18), @(0.67, 0.55), @(0.80, 0.34), @(0.86, 0.72) ) $cGold
    G-Rect $g $s 0.14 0.70 0.72 0.10 $cGold
    G-Disc $g $s 0.50 0.40 0.05 $cGem
}
function M-Heart($g, $s, $cMain, $cGlow) {
    G-Disc $g $s 0.34 0.40 0.19 $cMain
    G-Disc $g $s 0.66 0.40 0.19 $cMain
    G-Poly $g $s @( @(0.16, 0.44), @(0.84, 0.44), @(0.50, 0.90) ) $cMain
    G-Disc $g $s 0.34 0.36 0.07 $cGlow
    G-Disc $g $s 0.66 0.36 0.07 $cGlow
}
function M-Book($g, $s, $cPages, $cCover, $cSpine) {
    G-Rect $g $s 0.14 0.22 0.34 0.58 $cPages
    G-Rect $g $s 0.52 0.22 0.34 0.58 $cPages
    G-RectFrame $g $s 0.10 0.18 0.80 0.66 0.05 $cCover
    G-Line $g $s 0.50 0.22 0.50 0.80 0.05 $cSpine
    G-Rect $g $s 0.20 0.30 0.20 0.05 $cCover
    G-Rect $g $s 0.60 0.30 0.20 0.05 $cCover
    G-Rect $g $s 0.20 0.42 0.20 0.05 $cCover
    G-Rect $g $s 0.60 0.42 0.20 0.05 $cCover
}
function M-Burst($g, $s, $cx, $cy, $len, $c) {
    $angles = @(0, 40, 80, 120, 160, 200, 240, 280, 320)
    foreach ($a in $angles) {
        $rad = $a * [Math]::PI / 180.0
        $l = $len * (0.55 + 0.45 * [Math]::Abs([Math]::Sin(($a + 20) * [Math]::PI / 90.0)))
        G-Line $g $s ($cx + [Math]::Cos($rad) * 0.10) ($cy + [Math]::Sin($rad) * 0.10) ($cx + [Math]::Cos($rad) * $l) ($cy + [Math]::Sin($rad) * $l) 0.075 $c
    }
    G-Disc $g $s $cx $cy 0.10 $c
}
function M-Mouth($g, $s, $cInner, $cTeeth) {
    G-Disc $g $s 0.50 0.46 0.36 $cInner
    # 上排牙
    for ($i = 0; $i -lt 4; $i++) {
        $x = 0.24 + $i * 0.17
        G-Tri $g $s $x 0.30 ($x + 0.13) 0.30 ($x + 0.065) 0.46 $cTeeth
    }
    # 下排牙
    for ($i = 0; $i -lt 4; $i++) {
        $x = 0.24 + $i * 0.17
        G-Tri $g $s $x 0.62 ($x + 0.13) 0.62 ($x + 0.065) 0.48 $cTeeth
    }
}
function M-Wheel($g, $s, $c) {
    G-Ring $g $s 0.5 0.5 0.38 0.07 $c
    for ($i = 0; $i -lt 8; $i++) {
        $rad = ($i * 45) * [Math]::PI / 180.0
        G-Line $g $s (0.5 + [Math]::Cos($rad) * 0.12) (0.5 + [Math]::Sin($rad) * 0.12) (0.5 + [Math]::Cos($rad) * 0.34) (0.5 + [Math]::Sin($rad) * 0.34) 0.06 $c
    }
    G-Disc $g $s 0.5 0.5 0.09 $c
}
function M-Split($g, $s, $cA, $cB) {
    # 左右两半
    $half = [single](0.5 * $s)
    $rectL = New-Object System.Drawing.RectangleF([single]0, [single]0, $half, [single]$s)
    $rectR = New-Object System.Drawing.RectangleF($half, [single]0, $half, [single]$s)
    $save = $g.Save()
    $g.SetClip($rectL)
    G-Disc $g $s 0.5 0.5 0.40 $cA
    $g.Restore($save)
    $save = $g.Save()
    $g.SetClip($rectR)
    G-Disc $g $s 0.5 0.5 0.40 $cB
    $g.Restore($save)
    G-Line $g $s 0.5 0.10 0.5 0.90 0.02 ([System.Drawing.Color]::FromArgb(120, 255, 255, 255))
}
function M-Swirl($g, $s, $c) {
    $penW = 0.075
    for ($i = 0; $i -lt 4; $i++) {
        $pen = New-Object System.Drawing.Pen($c, [float]($penW * $s))
        $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        $r = 0.28 + $i * 0.07
        $g.DrawArc($pen, (0.5 - $r) * $s, (0.5 - $r) * $s, (2 * $r) * $s, (2 * $r) * $s, 200 + $i * 55, 240)
        $pen.Dispose()
    }
}
function M-Claw($g, $s, $cMain, $cSlash) {
    G-Poly $g $s @( @(0.18, 0.86), @(0.30, 0.26), @(0.42, 0.86) ) $cMain
    G-Poly $g $s @( @(0.42, 0.86), @(0.55, 0.16), @(0.68, 0.86) ) $cMain
    G-Poly $g $s @( @(0.66, 0.86), @(0.78, 0.34), @(0.88, 0.86) ) $cMain
    G-Line $g $s 0.34 0.60 0.40 0.60 0.04 $cSlash
    G-Line $g $s 0.54 0.50 0.60 0.50 0.04 $cSlash
    G-Line $g $s 0.76 0.66 0.82 0.66 0.04 $cSlash
}
function M-DotRing($g, $s, $cx, $cy, $r, $c, $hasDot) {
    G-Ring $g $s $cx $cy $r 0.09 $c
    if ($hasDot) { G-Disc $g $s $cx $cy ($r * 0.35) $c }
}
function M-Rays($g, $s, $cx, $cy, $c) {
    for ($i = 0; $i -lt 12; $i++) {
        $rad = ($i * 30) * [Math]::PI / 180.0
        G-Line $g $s ($cx + [Math]::Cos($rad) * 0.24) ($cy + [Math]::Sin($rad) * 0.24) ($cx + [Math]::Cos($rad) * 0.46) ($cy + [Math]::Sin($rad) * 0.46) 0.05 $c
    }
}

# ============ 具体图标（物品与槽位共用图案；槽位加边框） ============
function Draw-Icon($g, $s, [string]$key, [bool]$slot) {
    switch ($key) {
        'deity' {
            G-Bg $g $s (Col 22 12 62) (Col 74 38 140)
            M-Rays $g $s 0.5 0.5 (Col 255 214 90 200)
            G-Disc $g $s 0.5 0.5 0.20 (Col 120 70 190)
            M-Eye $g $s 0.5 0.5 0.16 (Col 255 235 180) (Col 255 200 60) (Col 40 16 40)
        }
        'beast' {
            G-Bg $g $s (Col 46 8 14) (Col 128 22 34)
            G-Disc $g $s 0.5 0.5 0.40 (Col 150 26 38)
            M-Claw $g $s (Col 255 150 90) (Col 40 6 8)
        }
        'nonexist_nonexist' {
            G-Bg $g $s (Col 8 8 14) (Col 30 30 46)
            G-Disc $g $s 0.5 0.5 0.30 (Col 14 14 22)
            M-DotRing $g $s 0.5 0.5 0.30 (Col 120 120 150) $false
        }
        'nonexist_exist' {
            G-Bg $g $s (Col 18 14 30) (Col 70 80 120)
            M-Split $g $s (Col 20 18 34) (Col 190 210 240)
        }
        'maybe_exist' {
            G-Bg $g $s (Col 8 40 48) (Col 30 120 130)
            M-Swirl $g $s (Col 150 240 235)
        }
        'exist_exist' {
            G-Bg $g $s (Col 90 60 8) (Col 230 180 60)
            M-DotRing $g $s 0.5 0.5 0.34 (Col 255 235 150) $true
        }
        'impossible_nonexist' {
            G-Bg $g $s (Col 40 30 70) (Col 160 150 220)
            G-Ring $g $s 0.5 0.5 0.30 0.05 (Col 255 255 255)
            G-Ring $g $s 0.5 0.5 0.18 0.05 (Col 120 110 200)
            G-Disc $g $s 0.5 0.5 0.06 (Col 255 255 255)
        }
        'supreme' {
            G-Bg $g $s (Col 70 12 12) (Col 180 60 20)
            M-Crown $g $s (Col 255 205 70) (Col 255 70 90)
        }
        'wisdom' {
            G-Bg $g $s (Col 12 30 70) (Col 70 140 220)
            M-Book $g $s (Col 250 250 255) (Col 60 110 190) (Col 220 230 250)
        }
        'life' {
            G-Bg $g $s (Col 10 52 22) (Col 60 150 80)
            M-Heart $g $s (Col 250 120 120) (Col 255 235 210)
        }
        'chaos' {
            G-Bg $g $s (Col 40 12 66) (Col 120 50 160)
            M-Burst $g $s 0.5 0.5 0.42 (Col 210 140 255)
        }
        'self' {
            G-Bg $g $s (Col 10 46 60) (Col 60 170 200)
            M-Eye $g $s 0.5 0.5 0.20 (Col 220 255 255) (Col 90 220 240) (Col 10 40 60)
        }
        'devour' {
            G-Bg $g $s (Col 60 22 4) (Col 200 100 30)
            M-Mouth $g $s (Col 120 30 10) (Col 255 230 200)
        }
        'samsara' {
            G-Bg $g $s (Col 16 18 60) (Col 70 80 200)
            M-Wheel $g $s (Col 220 230 255)
        }
        'divine' { Draw-Icon $g $s 'deity' $false }
        default {
            G-Bg $g $s (Col 60 60 60) (Col 120 120 120)
            G-Disc $g $s 0.5 0.5 0.3 (Col 180 180 180)
        }
    }
    if ($slot) {
        G-RectFrame $g $s 0.03 0.03 0.94 0.94 0.09 (Col 255 255 255 210)
        G-RectFrame $g $s 0.11 0.11 0.78 0.78 0.03 (Col 30 30 40)
    }
}

function Render([string]$key, [int]$final, [string]$outDir, [bool]$slot) {
    $scale = 4
    $big = New-Object System.Drawing.Bitmap(($final * $scale), ($final * $scale))
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    Draw-Icon $g ($final * $scale) $key $slot
    $path = Join-Path $outDir ($key + '.png')
    Save-Scaled $big $path $final
    $g.Dispose()
    Write-Host ("  {0}.png  {1}x{1}" -f $key, $final)
}

$itemKeys = @('deity', 'beast', 'nonexist_nonexist', 'nonexist_exist', 'maybe_exist',
    'exist_exist', 'impossible_nonexist', 'supreme', 'wisdom', 'life', 'chaos', 'self', 'devour', 'samsara')
$slotKeys = @('divine', 'beast') + @('nonexist_nonexist', 'nonexist_exist', 'maybe_exist', 'exist_exist',
    'impossible_nonexist', 'supreme', 'wisdom', 'life', 'chaos', 'self', 'devour', 'samsara')

Write-Host '[items 16x16]'
foreach ($k in $itemKeys) { Render $k 16 $script:itemOut $false }
Write-Host '[slots 32x32]'
foreach ($k in $slotKeys) { Render $k 32 $script:slotOut $true }
Write-Host 'done'
