param(
    [string]$JarPath = "target\ModernTetris.jar",
    [switch]$NoMusic,
    [switch]$SmokeTest
)

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$srcDir = "src\main\java"
$classesDir = "target\classes"
$packageDir = "target\package-resources"
$sourceList = "target\javac-sources.txt"
$manifestFile = "target\manifest.mf"
$mainClass = "com.tetris.Main"

function Require-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "$name not found on PATH. A JDK 17+ is required."
    }
}

function Full-Path($path) {
    $parent = Split-Path -Parent $path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    return (Join-Path (Get-Location).Path $path)
}

Require-Command javac
Require-Command jar
Require-Command java

$repoRoot = (Get-Location).Path
$repoRootWithSlash = $repoRoot.TrimEnd('\') + '\'

function Resolve-InRepo($path) {
    $full = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $path))
    if (-not $full.StartsWith($repoRootWithSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify path outside repo: $full"
    }
    return $full
}

function Remove-TreeInRepo($path) {
    $full = Resolve-InRepo $path
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
}

Write-Host "[clean] Preparing target directories..."
Remove-TreeInRepo $classesDir
Remove-TreeInRepo $packageDir
New-Item -ItemType Directory -Path $classesDir | Out-Null
New-Item -ItemType Directory -Path $packageDir | Out-Null

Write-Host "[build] Compiling Java sources..."
Get-ChildItem -Path $srcDir -Recurse -Filter *.java |
    ForEach-Object { $_.FullName } |
    Set-Content -Path $sourceList -Encoding ASCII

& javac -d $classesDir -encoding UTF-8 "@$sourceList"
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed."
}

Write-Host "[package] Writing JAR manifest..."
@(
    "Manifest-Version: 1.0"
    "Main-Class: $mainClass"
    ""
) | Set-Content -Path $manifestFile -Encoding ASCII

$includeMusic = -not $NoMusic
if ($includeMusic) {
    if (-not (Test-Path -LiteralPath "music")) {
        throw "music folder not found. Use -NoMusic only for code-only diagnostics."
    }

    Write-Host "[package] Writing packaged music manifest..."
    $musicRows = Get-ChildItem -Path "music" -Recurse -File -Filter *.wav |
        Sort-Object FullName |
        ForEach-Object {
            $rel = $_.FullName.Substring($repoRootWithSlash.Length).Replace('\', '/')
            "$rel`t$($_.Length)"
        }
    $musicManifest = Join-Path $packageDir "music-manifest.txt"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines(
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot $musicManifest)),
        [string[]]$musicRows,
        $utf8NoBom)
} else {
    Write-Host "[package] Skipping bundled music by request."
}

$jarFullPath = Full-Path $JarPath
if (Test-Path -LiteralPath $jarFullPath) {
    Remove-Item -LiteralPath $jarFullPath -Force
}

Write-Host "[package] Creating $jarFullPath ..."
$jarArgs = @(
    "--create",
    "--no-compress",
    "--file", $jarFullPath,
    "--manifest", $manifestFile,
    "-C", $classesDir, ".",
    "-C", $packageDir, "."
)
# Bundle the open-licensed fonts (registered at startup so the UI font
# chains resolve on hosts lacking the proprietary originals).
if (Test-Path -LiteralPath "fonts") {
    $jarArgs += @("-C", (Get-Location).Path, "fonts")
} else {
    Write-Warning "[package] fonts\ folder not found; jar will rely on host fonts only."
}
if ($includeMusic) {
    $jarArgs += @("-C", (Get-Location).Path, "music")
}

& jar @jarArgs
if ($LASTEXITCODE -ne 0) {
    throw "jar command failed."
}

$jarInfo = Get-Item -LiteralPath $jarFullPath
Write-Host ("[package] Built {0} ({1:N1} MiB)" -f $jarInfo.FullName, ($jarInfo.Length / 1MB))

if ($SmokeTest) {
    $smokeDir = Join-Path (Get-Location).Path "target\jar-smoke-data"
    if (Test-Path -LiteralPath $smokeDir) {
        Remove-TreeInRepo "target\jar-smoke-data"
    }
    Write-Host "[smoke] Running executable JAR smoke test..."
    & java "-Dtetris.appDataDir=$smokeDir" -jar $jarFullPath --smoke-test
    if ($LASTEXITCODE -ne 0) {
        throw "JAR smoke test failed."
    }
}

Write-Host "[done] Run with: java -jar `"$jarFullPath`""
