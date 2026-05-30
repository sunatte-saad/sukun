param(
    [string]$Source = "C:\Users\jtc\.cursor\projects\d-sukun\assets\sukun_logo_calm_orbit_refined.png",
    [string]$ProjectRoot = "D:\sukun"
)

Add-Type -AssemblyName System.Drawing

# Logo scale within the icon canvas (66dp safe zone ≈ 0.61; slightly larger for visibility).
$SafeZoneRatio = 0.70
$LegacyIconFillRatio = 0.80

function New-Graphics {
    param([System.Drawing.Bitmap]$Bitmap)
    $g = [System.Drawing.Graphics]::FromImage($Bitmap)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return $g
}

function Get-AspectFitRect {
    param(
        [int]$CanvasSize,
        [int]$SrcWidth,
        [int]$SrcHeight,
        [double]$MaxFillRatio
    )
    $maxW = $CanvasSize * $MaxFillRatio
    $maxH = $CanvasSize * $MaxFillRatio
    $scale = [Math]::Min($maxW / $SrcWidth, $maxH / $SrcHeight)
    $drawW = [int][Math]::Round($SrcWidth * $scale)
    $drawH = [int][Math]::Round($SrcHeight * $scale)
    $x = [int][Math]::Round(($CanvasSize - $drawW) / 2.0)
    $y = [int][Math]::Round(($CanvasSize - $drawH) / 2.0)
    return @{ X = $x; Y = $y; Width = $drawW; Height = $drawH }
}

function Make-BlackTransparent {
    param([System.Drawing.Bitmap]$Bitmap, [int]$Threshold = 28)
    for ($y = 0; $y -lt $Bitmap.Height; $y++) {
        for ($x = 0; $x -lt $Bitmap.Width; $x++) {
            $pixel = $Bitmap.GetPixel($x, $y)
            if ($pixel.R -le $Threshold -and $pixel.G -le $Threshold -and $pixel.B -le $Threshold) {
                $Bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
        }
    }
}

function Write-AdaptiveForeground {
    param(
        [System.Drawing.Image]$Src,
        [string]$OutputPath,
        [int]$Size
    )
    $bmp = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = New-Graphics $bmp
    $g.Clear([System.Drawing.Color]::Transparent)
    $rect = Get-AspectFitRect -CanvasSize $Size -SrcWidth $Src.Width -SrcHeight $Src.Height -MaxFillRatio $SafeZoneRatio
    $g.DrawImage($Src, $rect.X, $rect.Y, $rect.Width, $rect.Height)
    $g.Dispose()
    Make-BlackTransparent -Bitmap $bmp

    $dir = Split-Path $OutputPath -Parent
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Wrote foreground $OutputPath ($Size px, aspect-fit)"
}

function Write-LegacyLauncherIcon {
    param(
        [System.Drawing.Image]$Src,
        [string]$OutputPath,
        [int]$Size
    )
    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = New-Graphics $bmp
    $g.Clear([System.Drawing.Color]::Black)
    # Slightly smaller than full canvas so OEM masks do not clip the mark.
    $rect = Get-AspectFitRect -CanvasSize $Size -SrcWidth $Src.Width -SrcHeight $Src.Height -MaxFillRatio $LegacyIconFillRatio
    $g.DrawImage($Src, $rect.X, $rect.Y, $rect.Width, $rect.Height)
    $g.Dispose()

    $dir = Split-Path $OutputPath -Parent
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Wrote legacy icon $OutputPath ($Size px, aspect-fit)"
}

if (-not (Test-Path $Source)) {
    Write-Error "Source image not found: $Source"
    exit 1
}

$src = [System.Drawing.Image]::FromFile($Source)
Write-Host "Source: $($src.Width)x$($src.Height)"

$foregroundTargets = @(
    @{ Path = "$ProjectRoot\app\src\main\res\drawable\ic_launcher_image.png"; Size = 432 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-mdpi\ic_launcher_foreground.png"; Size = 108 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-hdpi\ic_launcher_foreground.png"; Size = 162 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xhdpi\ic_launcher_foreground.png"; Size = 216 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxhdpi\ic_launcher_foreground.png"; Size = 324 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png"; Size = 432 }
)

$legacyTargets = @(
    @{ Path = "$ProjectRoot\app\src\main\ic_launcher-playstore.png"; Size = 512 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-mdpi\ic_launcher.png"; Size = 48 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-hdpi\ic_launcher.png"; Size = 72 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xhdpi\ic_launcher.png"; Size = 96 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxhdpi\ic_launcher.png"; Size = 144 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"; Size = 192 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-mdpi\ic_launcher_round.png"; Size = 48 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-hdpi\ic_launcher_round.png"; Size = 72 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xhdpi\ic_launcher_round.png"; Size = 96 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxhdpi\ic_launcher_round.png"; Size = 144 },
    @{ Path = "$ProjectRoot\app\src\main\res\mipmap-xxxhdpi\ic_launcher_round.png"; Size = 192 }
)

foreach ($t in $foregroundTargets) {
    Write-AdaptiveForeground -Src $src -OutputPath $t.Path -Size $t.Size
}

foreach ($t in $legacyTargets) {
    Write-LegacyLauncherIcon -Src $src -OutputPath $t.Path -Size $t.Size
}

$src.Dispose()

Get-ChildItem -Path "$ProjectRoot\app\src\main\res\mipmap-*" -Filter "ic_launcher*.webp" -Recurse -ErrorAction SilentlyContinue |
    ForEach-Object {
        Remove-Item $_.FullName -Force
        Write-Host "Removed $($_.FullName)"
    }

Write-Host "Done."
