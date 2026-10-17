<#
Apply the built-in ImageGen vibranium palette to exact native source pixel masks.
Preserve source silhouettes, shield UVs, wood, handles and every alpha value.
Author: NingMeng123
#>
[CmdletBinding()]
param(
    [string]$SourceShears = 'src/main/resources/assets/mite/textures/item/shears.png',
    [string]$GoldShears = 'src/main/resources/assets/mite/textures/item/golden_shears.png',
    [string]$ShieldAtlas = 'build/verification/shield-texture-inputs/shield_base_nopattern.png',
    [string]$PaletteImage = 'docs/art/vibranium-palette.png'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $root
$palette = @(@(39,35,58), @(61,48,85), @(75,74,97), @(97,96,119), @(102,122,163), @(116,207,253), @(209,251,247))
if (!(Test-Path -LiteralPath $PaletteImage)) { throw 'Missing built-in ImageGen material palette reference.' }
if (!(Test-Path -LiteralPath $ShieldAtlas)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = Get-ChildItem -LiteralPath 'build/moddev/artifacts' -Filter '*minecraft-resources.jar' | Select-Object -First 1
    if ($null -eq $archive) { throw 'No cached modern Minecraft resource archive for the neutral shield atlas.' }
    $zip = [IO.Compression.ZipFile]::OpenRead($archive.FullName)
    try {
        $entry = $zip.GetEntry('assets/minecraft/textures/entity/shield_base_nopattern.png')
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($ShieldAtlas))) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, [IO.Path]::GetFullPath($ShieldAtlas), $true)
    } finally { $zip.Dispose() }
}

function Test-GreyMetal([Drawing.Color]$Pixel) {
    $range = [Math]::Max($Pixel.R, [Math]::Max($Pixel.G, $Pixel.B)) - [Math]::Min($Pixel.R, [Math]::Min($Pixel.G, $Pixel.B))
    $Pixel.A -gt 0 -and $range -le 12 -and ($Pixel.R + $Pixel.G + $Pixel.B) -gt 0
}

$sources = @(
    @{ kind = 'shears'; source = $SourceShears; mask_reference = $GoldShears; destination = 'src/main/resources/assets/mite/textures/item/vibranium_shears.png' },
    @{ kind = 'shield'; source = $ShieldAtlas; mask_reference = $null; destination = 'src/main/resources/assets/mite/textures/entity/shield/vibranium.png' }
)
$results = [Collections.Generic.List[object]]::new()
foreach ($asset in $sources) {
    $source = [Drawing.Bitmap]::FromFile([IO.Path]::GetFullPath($asset.source))
    $comparison = if ($asset.mask_reference) { [Drawing.Bitmap]::FromFile([IO.Path]::GetFullPath($asset.mask_reference)) } else { $null }
    $output = [Drawing.Bitmap]::new($source.Width, $source.Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        if ($asset.kind -eq 'shears' -and ($source.Width -ne 16 -or $source.Height -ne 16)) { throw 'Expected native 16 x 16 shears.' }
        if ($asset.kind -eq 'shield' -and ($source.Width -ne 64 -or $source.Height -ne 64)) { throw 'Expected native 64 x 64 shield UV.' }
        $mask = @{}
        $levels = @{}
        for ($y=0; $y -lt $source.Height; $y++) { for ($x=0; $x -lt $source.Width; $x++) {
            $color = $source.GetPixel($x,$y)
            $eligible = Test-GreyMetal $color
            if ($null -ne $comparison) { $eligible = $eligible -and $color.ToArgb() -ne $comparison.GetPixel($x,$y).ToArgb() }
            if ($eligible) {
                $mask["$x,$y"] = $true
                $levels[$color.Name] = [pscustomobject]@{ key=$color.Name; luminance=.299*$color.R+.587*$color.G+.114*$color.B }
            }
        }}
        $ordered = @($levels.Values | Sort-Object luminance,key)
        if ($ordered.Count -lt 2) { throw "No usable grey metal mask for $($asset.kind)." }
        $mapping = @{}
        for ($i=0; $i -lt $ordered.Count; $i++) {
            $index = [int][Math]::Round($i * 6.0 / ($ordered.Count-1))
            $rgb = $palette[$index]
            $mapping[$ordered[$i].key] = [Drawing.Color]::FromArgb(255,$rgb[0],$rgb[1],$rgb[2])
        }
        $changed=0; $preserved=0; $alphaChanges=0; $outsideChanges=0
        for ($y=0; $y -lt $source.Height; $y++) { for ($x=0; $x -lt $source.Width; $x++) {
            $color=$source.GetPixel($x,$y)
            $actual=$color
            if ($mask.ContainsKey("$x,$y")) {
                $mapped=$mapping[$color.Name]
                $actual=[Drawing.Color]::FromArgb($color.A,$mapped.R,$mapped.G,$mapped.B)
            }
            $output.SetPixel($x,$y,$actual)
            if ($color.A -ne $actual.A) { $alphaChanges++ }
            if ($color.ToArgb() -ne $actual.ToArgb()) {
                $changed++
                if (!$mask.ContainsKey("$x,$y")) { $outsideChanges++ }
            } else { $preserved++ }
        }}
        if ($alphaChanges -ne 0 -or $outsideChanges -ne 0 -or $changed -eq 0) { throw 'Native pixel preservation audit failed.' }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($asset.destination))) | Out-Null
        $output.Save([IO.Path]::GetFullPath($asset.destination),[Drawing.Imaging.ImageFormat]::Png)
        $results.Add([ordered]@{
            kind=$asset.kind; source=$asset.source; mask_reference=$asset.mask_reference; destination=$asset.destination
            source_sha256=(Get-FileHash -LiteralPath $asset.source -Algorithm SHA256).Hash.ToLowerInvariant()
            sha256=(Get-FileHash -LiteralPath $asset.destination -Algorithm SHA256).Hash.ToLowerInvariant()
            width=$source.Width; height=$source.Height; metal_mask_pixels=$mask.Count; changed_metal_pixels=$changed
            unchanged_rgba_pixels=$preserved; changed_alpha_pixels=$alphaChanges; changed_nonmetal_rgba_pixels=$outsideChanges
        })
    } finally { $output.Dispose(); if ($null -ne $comparison) {$comparison.Dispose()}; $source.Dispose() }
}
$previewPath='docs/art/vibranium-extensions-preview.png'
$preview=[Drawing.Bitmap]::new(640,360,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics=[Drawing.Graphics]::FromImage($preview)
$font=[Drawing.Font]::new('Segoe UI',14,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
try {
    $graphics.Clear([Drawing.Color]::FromArgb(255,29,31,42))
    $graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    $shears=[Drawing.Bitmap]::FromFile([IO.Path]::GetFullPath($sources[0].destination))
    $shield=[Drawing.Bitmap]::FromFile([IO.Path]::GetFullPath($sources[1].destination))
    try {
        $graphics.DrawString('Vibranium shears', $font,[Drawing.Brushes]::White,32,16)
        $graphics.DrawString('Vibranium shield front UV', $font,[Drawing.Brushes]::White,350,16)
        $graphics.DrawImage($shears,[Drawing.Rectangle]::new(24,56,256,256),0,0,16,16,[Drawing.GraphicsUnit]::Pixel)
        $graphics.DrawImage($shield,[Drawing.Rectangle]::new(368,48,144,264),14,1,12,22,[Drawing.GraphicsUnit]::Pixel)
        $graphics.DrawString('Native pixel masks and UVs preserved. Author: NingMeng123.', $font,[Drawing.Brushes]::LightGray,24,330)
        $preview.Save([IO.Path]::GetFullPath($previewPath),[Drawing.Imaging.ImageFormat]::Png)
    } finally {$shears.Dispose();$shield.Dispose()}
} finally {$font.Dispose();$graphics.Dispose();$preview.Dispose()}
$report=[ordered]@{
    developer='NingMeng123'; method='Built-in ImageGen palette; deterministic per-native-grey rank mapping with System.Drawing. Exact source RGBA outside the grey-metal mask; no resizing of game textures.'
    palette_reference=$PaletteImage; palette_reference_sha256=(Get-FileHash -LiteralPath $PaletteImage -Algorithm SHA256).Hash.ToLowerInvariant()
    palette_rgb=$palette; mask='Opaque near-grey metal with channel spread <= 12, excluding pure black; shears also require a difference from the gold sprite.'
    preview=$previewPath; game_render_screenshot=$false; assets=$results.ToArray()
}
[IO.File]::WriteAllText((Join-Path $root 'src/main/resources/mite/vibranium-extension-assets.json'),($report | ConvertTo-Json -Depth 10)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Write-Output "Generated 2 vibranium extension textures. Preview: $previewPath"
