# Render the supplied original into Android density resources, with no detail enhancement.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = [System.Drawing.Image]::FromFile((Join-Path $root 'assets\branding\kirino_source.png'))
function Save-Icon([int]$size, [string]$path, [bool]$adaptive) {
    $bitmap = [System.Drawing.Bitmap]::new($size,$size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $margin = if ($adaptive) { [int]($size * 0.17) } else { [int]($size * 0.05) }
    $diameter = $size - 2*$margin
    $shape = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $shape.AddEllipse($margin,$margin,$diameter,$diameter)
    $graphics.SetClip($shape)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,244,220,179))
    $graphics.FillRectangle($brush,0,0,$size,$size)
    $graphics.DrawImage($source,[System.Drawing.Rectangle]::new($margin,$margin,$diameter,$diameter),[System.Drawing.Rectangle]::new(35,5,355,355),[System.Drawing.GraphicsUnit]::Pixel)
    $graphics.ResetClip()
    $bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $brush.Dispose(); $shape.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}
try {
    $res = Join-Path $root 'app\src\main\res'
    New-Item -ItemType Directory -Path (Join-Path $res 'drawable-nodpi') -Force | Out-Null
    Save-Icon 432 (Join-Path $res 'drawable-nodpi\kirino_foreground.png') $true
    $densities = @{ 'mdpi'=48; 'hdpi'=72; 'xhdpi'=96; 'xxhdpi'=144; 'xxxhdpi'=192 }
    foreach ($density in $densities.Keys) {
        $dir = Join-Path $res "mipmap-$density"
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Save-Icon $densities[$density] (Join-Path $dir 'ic_launcher.png') $false
    }
} finally { $source.Dispose() }
