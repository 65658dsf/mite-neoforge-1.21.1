param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\..\build\mite-server-manager')
)

$ErrorActionPreference = 'Stop'
if (!(Get-Command wails -ErrorAction SilentlyContinue)) {
    throw 'Install the Wails v2 CLI: go install github.com/wailsapp/wails/v2/cmd/wails@v2.15.0'
}

$build = (New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'build') -Force).FullName
$destination = (New-Item -ItemType Directory -Path $OutputDirectory -Force).FullName
$icon = Join-Path $build 'appicon.png'
$name = 'MITE-Server-Manager.exe'
Add-Type -AssemblyName System.Drawing

$bitmap = [Drawing.Bitmap]::new(256, 256)
$surface = [Drawing.Graphics]::FromImage($bitmap)
$ink = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(26, 47, 34))
try {
    $surface.Clear([Drawing.Color]::FromArgb(169, 239, 120))
    $surface.FillRectangle($ink, 55, 70, 22, 116)
    $surface.FillRectangle($ink, 179, 70, 22, 116)
    $surface.FillPolygon($ink, [Drawing.Point[]]@(
        [Drawing.Point]::new(65, 70), [Drawing.Point]::new(85, 70),
        [Drawing.Point]::new(128, 116), [Drawing.Point]::new(171, 70),
        [Drawing.Point]::new(191, 70), [Drawing.Point]::new(191, 95),
        [Drawing.Point]::new(128, 157), [Drawing.Point]::new(65, 95)
    ))
    $bitmap.Save($icon, [Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $ink.Dispose()
    $surface.Dispose()
    $bitmap.Dispose()
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & wails build -s -skipbindings -trimpath -webview2 error -o $name
    if ($LASTEXITCODE -ne 0) { throw "Desktop manager build failed ($LASTEXITCODE)." }
    Copy-Item -LiteralPath (Join-Path $build "bin/$name") -Destination $destination
    Get-Item -LiteralPath (Join-Path $destination $name)
}
finally {
    Pop-Location
}
