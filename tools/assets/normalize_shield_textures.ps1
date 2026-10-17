<#
Produces exact native-UV shields from imagegen palette references.
Only opaque grey metal pixels may change; wood and alpha retain their source values.
Author: NingMeng123
#>
[CmdletBinding()]
param(
    [string]$Manifest = 'build/verification/shield-generated-textures.json',
    [string]$SourceAtlas = 'build/verification/shield-texture-inputs/shield_base_nopattern.png',
    [string]$OutputDirectory = 'src/main/resources/assets/mite/textures/entity/shield',
    [string]$ReportPath = 'build/verification/shield-texture-normalization.json',
    [string]$PreviewPath = 'build/verification/shield-textures-preview.png'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Get-AbsolutePath([string]$Path) {
    [IO.Path]::GetFullPath($Path.Replace('\\', '\'))
}

function Test-Metal([Drawing.Color]$Pixel) {
    $minimum = [Math]::Min($Pixel.R, [Math]::Min($Pixel.G, $Pixel.B))
    $maximum = [Math]::Max($Pixel.R, [Math]::Max($Pixel.G, $Pixel.B))
    $Pixel.A -gt 0 -and ($maximum - $minimum) -le 12
}

function Get-Median($Values) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { throw 'An imagegen palette level has no opaque sample.' }
    if (($sorted.Count % 2) -eq 1) { return [int]$sorted[[int][Math]::Floor($sorted.Count / 2)] }
    $middle = [int]($sorted.Count / 2)
    [int][Math]::Round(($sorted[$middle - 1] + $sorted[$middle]) / 2)
}

$manifestPath = Get-AbsolutePath $Manifest
$sourcePath = Get-AbsolutePath $SourceAtlas
$outputPath = Get-AbsolutePath $OutputDirectory
$reportFile = Get-AbsolutePath $ReportPath
$previewFile = Get-AbsolutePath $PreviewPath
[IO.Directory]::CreateDirectory($outputPath) | Out-Null
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($reportFile)) | Out-Null
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($previewFile)) | Out-Null
$entries = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
$expected = @('rusted_iron', 'copper', 'silver', 'gold', 'ancient_metal', 'mithril', 'adamantium')
if ((($entries.material | Sort-Object) -join ',') -ne (($expected | Sort-Object) -join ',')) {
    throw 'The manifest must contain exactly the seven new metal shields.'
}

$source = [Drawing.Bitmap]::FromFile($sourcePath)
$results = [Collections.Generic.List[object]]::new()
try {
    if ($source.Width -ne 64 -or $source.Height -ne 64) { throw 'The native shield atlas must be 64 by 64.' }
    $maskCount = 0
    $blackCount = 0
    for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
        $pixel = $source.GetPixel($x, $y)
        if (Test-Metal $pixel) {
            $maskCount++
            if (($pixel.R + $pixel.G + $pixel.B) -eq 0) { $blackCount++ }
        }
    }}

    foreach ($entry in $entries) {
        $generatedPath = Get-AbsolutePath $entry.path
        $generated = [Drawing.Bitmap]::FromFile($generatedPath)
        $normalized = [Drawing.Bitmap]::new(64, 64, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            if ($generated.Width -ne $generated.Height) { throw "The generated UV reference is not square: $generatedPath" }
            $samples = @{}
            # Sample the centres of the 64 native pixels: exact nearest-neighbour downsampling.
            for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
                $original = $source.GetPixel($x, $y)
                if (!(Test-Metal $original) -or ($original.R + $original.G + $original.B) -eq 0) { continue }
                $sx = [Math]::Min($generated.Width - 1, [int][Math]::Floor(($x + .5) * $generated.Width / 64))
                $sy = [Math]::Min($generated.Height - 1, [int][Math]::Floor(($y + .5) * $generated.Height / 64))
                $sample = $generated.GetPixel($sx, $sy)
                if ($sample.A -lt 128 -or ($sample.R + $sample.G + $sample.B) -eq 0) { continue }
                if (!$samples.ContainsKey($original.Name)) { $samples[$original.Name] = [Collections.Generic.List[Drawing.Color]]::new() }
                $samples[$original.Name].Add($sample)
            }}
            $palette = @{}
            $paletteReport = [Collections.Generic.List[object]]::new()
            $generatedLevels = [Collections.Generic.List[object]]::new()
            foreach ($key in @($samples.Keys | Sort-Object)) {
                $colors = $samples[$key]
                $color = [Drawing.Color]::FromArgb(255, (Get-Median $colors.R), (Get-Median $colors.G), (Get-Median $colors.B))
                $generatedLevels.Add([pscustomobject]@{
                    sampled_source_grey = $key
                    color = $color
                    sample_count = $colors.Count
                    luminance = .299 * $color.R + .587 * $color.G + .114 * $color.B
                })
            }
            # Imagegen can move a highlight to a neighbouring UV pixel. Keep its palette,
            # while restoring the original native ordering of shadow and highlight levels.
            $nativeLevels = @($samples.Keys | Sort-Object)
            $orderedGenerated = @($generatedLevels | Sort-Object luminance, sampled_source_grey)
            for ($i = 0; $i -lt $nativeLevels.Count; $i++) {
                $key = $nativeLevels[$i]
                $level = $orderedGenerated[$i]
                $palette[$key] = $level.color
                $paletteReport.Add([ordered]@{
                    original_argb = $key
                    generated_argb = $level.color.Name
                    generated_palette_sampled_at_native_grey = $level.sampled_source_grey
                    opaque_samples = $level.sample_count
                    luminance = $level.luminance
                })
            }
            for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
                $original = $source.GetPixel($x, $y)
                $color = $original
                if ((Test-Metal $original) -and ($original.R + $original.G + $original.B) -gt 0) {
                    if (!$palette.ContainsKey($original.Name)) { throw "Missing metal palette entry $($original.Name): $($entry.material)" }
                    $mapped = $palette[$original.Name]
                    $color = [Drawing.Color]::FromArgb($original.A, $mapped.R, $mapped.G, $mapped.B)
                }
                $normalized.SetPixel($x, $y, $color)
            }}
            $destination = Join-Path $outputPath ($entry.material + '.png')
            $normalized.Save($destination, [Drawing.Imaging.ImageFormat]::Png)
            $saved = [Drawing.Bitmap]::FromFile($destination)
            try {
                $nonMetalChanges = 0; $alphaChanges = 0; $changedMetal = 0; $blackChanges = 0
                for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
                    $original = $source.GetPixel($x, $y); $actual = $saved.GetPixel($x, $y)
                    if ($original.A -ne $actual.A) { $alphaChanges++ }
                    if ($original.ToArgb() -ne $actual.ToArgb()) {
                        if (Test-Metal $original) { $changedMetal++ } else { $nonMetalChanges++ }
                        if ($original.A -gt 0 -and ($original.R + $original.G + $original.B) -eq 0) { $blackChanges++ }
                    }
                }}
                if ($saved.Width -ne 64 -or $saved.Height -ne 64 -or $nonMetalChanges -ne 0 -or $alphaChanges -ne 0 -or $blackChanges -ne 0) {
                    throw "Exact UV/wood/alpha validation failed: $($entry.material)"
                }
                $results.Add([ordered]@{
                    material = $entry.material
                    source_generated_path = $generatedPath
                    source_generated_sha256 = (Get-FileHash -LiteralPath $generatedPath -Algorithm SHA256).Hash.ToLowerInvariant()
                    output_path = $destination
                    output_sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
                    width = $saved.Width; height = $saved.Height
                    source_generated_width = $generated.Width; source_generated_height = $generated.Height
                    changed_metal_pixels = $changedMetal
                    changed_nonmetal_rgba_pixels = $nonMetalChanges
                    changed_alpha_pixels = $alphaChanges
                    changed_native_black_uv_pixels = $blackChanges
                    palette = $paletteReport.ToArray()
                    status = 'passed_exact_native_uv_wood_and_alpha'
                })
            } finally { $saved.Dispose() }
        } finally { $normalized.Dispose(); $generated.Dispose() }
    }

    # A flat crop of the native front UV, enlarged without smoothing; this is not a game-render screenshot.
    $preview = [Drawing.Bitmap]::new(1280, 320, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($preview)
    $font = [Drawing.Font]::new('Segoe UI', 13, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
    try {
        $graphics.Clear([Drawing.Color]::FromArgb(255, 31, 37, 48))
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
        $materials = @('rusted_iron', 'copper', 'silver', 'gold', 'iron', 'ancient_metal', 'mithril', 'adamantium')
        $labels = @('Rusted Iron', 'Copper', 'Silver', 'Gold', 'Iron (vanilla)', 'Ancient Metal', 'Mithril', 'Adamantium')
        for ($i = 0; $i -lt $materials.Count; $i++) {
            $imagePath = if ($materials[$i] -eq 'iron') { $sourcePath } else { Join-Path $outputPath ($materials[$i] + '.png') }
            $image = [Drawing.Bitmap]::FromFile($imagePath)
            try {
                $graphics.DrawString($labels[$i], $font, [Drawing.Brushes]::White, [single]($i * 160 + 18), 16)
                $target = [Drawing.Rectangle]::new($i * 160 + 20, 48, 120, 220)
                $graphics.DrawImage($image, $target, 14, 1, 12, 22, [Drawing.GraphicsUnit]::Pixel)
            } finally { $image.Dispose() }
        }
        $graphics.DrawString('Native front UV crops. Only metal pixels differ; identical wood and alpha. Author: NingMeng123.',
                $font, [Drawing.Brushes]::LightGray, 18, 292)
        $preview.Save($previewFile, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $graphics.Dispose(); $preview.Dispose() }

    $report = [ordered]@{
        author = 'NingMeng123'
        original_atlas = $sourcePath
        original_atlas_sha256 = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
        method = 'Nearest-neighbour pixel-centre sampling of imagegen output; per-native-grey median RGB palette, sorted by luminance to preserve native shadow/highlight order; exact source RGBA outside mask.'
        metal_mask = 'alpha > 0 and max(R,G,B)-min(R,G,B) <= 12; native pure-black UV pixels remain black'
        native_metal_mask_pixels = $maskCount
        native_black_uv_pixels_preserved = $blackCount
        nonmetal_rgba_pixels_preserved_per_image = 4096 - $maskCount
        alpha_pixels_preserved_per_image = 4096
        preview_path = $previewFile
        game_render_screenshot = $false
        results = $results.ToArray()
        status = 'passed_all_seven_exact_native_uv_wood_and_alpha'
    }
    [IO.File]::WriteAllText($reportFile, ($report | ConvertTo-Json -Depth 12) + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    Write-Output "Saved 7 native-UV shield PNGs. Report: $reportFile"
    Write-Output "Preview: $previewFile"
} finally { $source.Dispose() }
