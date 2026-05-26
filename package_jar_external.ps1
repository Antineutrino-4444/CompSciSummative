param(
    [string]$JarPath = "target\MAB-external.jar",
    [switch]$Stage,
    [string]$StageDir = "target\dist-external",
    [switch]$SmokeTest
)

# ============================================================
#  package_jar_external.ps1
#  =======================================================
#  Builds a SLIM "external assets" JAR: the compiled game only,
#  with NO music and NO sfx bundled inside. At runtime the game
#  reads its audio from folders next to the JAR:
#
#      <run folder>\music\...
#      <run folder>\sfx\...
#
#  (AppPaths.musicDir() prefers a local music\ folder; the SFX
#   resolver already prefers a local sfx\ folder.)
#
#  Compare with package_jar.ps1, which bundles ~1 GB of music
#  INSIDE the JAR and extracts it to %APPDATA% on first run.
#
#  Usage:
#    .\package_jar_external.ps1
#        -> builds target\MAB-external.jar
#           Run it from any folder that has music\ and sfx\
#           subfolders (e.g. the repo root):
#               java -jar target\MAB-external.jar
#
#    .\package_jar_external.ps1 -Stage
#        -> additionally assembles a ready-to-run folder at
#           target\dist-external\ containing the JAR plus copies
#           of music\ and sfx\. (Copies ~1 GB; slower.)
#
#    .\package_jar_external.ps1 -SmokeTest
#        -> builds, then headless-launches the JAR from the repo
#           root (where music\ and sfx\ live) to confirm it boots.
# ============================================================

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$srcDir = "src\main\java"
$classesDir = "target\classes"
$sourceList = "target\javac-sources.txt"
$manifestFile = "target\manifest-external.mf"
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
New-Item -ItemType Directory -Path $classesDir | Out-Null

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

$jarFullPath = Full-Path $JarPath
if (Test-Path -LiteralPath $jarFullPath) {
    Remove-Item -LiteralPath $jarFullPath -Force
}

# Slim JAR: compiled classes + manifest, plus the small open-licensed fonts
# (registered at startup). NO music, no sfx, and no music-manifest.txt — so
# RuntimeBootstrap finds nothing to extract and the game reads music\ and
# sfx\ from the working directory.
Write-Host "[package] Creating slim JAR (no bundled audio) $jarFullPath ..."
$jarArgs = @(
    "--create",
    "--no-compress",
    "--file", $jarFullPath,
    "--manifest", $manifestFile,
    "-C", $classesDir, "."
)
if (Test-Path -LiteralPath "fonts") {
    $jarArgs += @("-C", (Get-Location).Path, "fonts")
} else {
    Write-Warning "[package] fonts\ folder not found; jar will rely on host fonts only."
}
& jar @jarArgs
if ($LASTEXITCODE -ne 0) {
    throw "jar command failed."
}

$jarInfo = Get-Item -LiteralPath $jarFullPath
Write-Host ("[package] Built {0} ({1:N1} MiB)" -f $jarInfo.FullName, ($jarInfo.Length / 1MB))

if ($Stage) {
    Write-Host "[stage] Assembling ready-to-run folder at $StageDir ..."
    Remove-TreeInRepo $StageDir
    $stageFull = Resolve-InRepo $StageDir
    New-Item -ItemType Directory -Path $stageFull | Out-Null

    Copy-Item -LiteralPath $jarFullPath -Destination (Join-Path $stageFull (Split-Path -Leaf $jarFullPath))

    foreach ($asset in @("music", "sfx")) {
        $srcAsset = Join-Path $repoRoot $asset
        if (Test-Path -LiteralPath $srcAsset) {
            Write-Host "[stage]   copying $asset\ ..."
            Copy-Item -LiteralPath $srcAsset -Destination $stageFull -Recurse
        } else {
            Write-Warning "[stage]   $asset\ not found in repo; skipping."
        }
    }
    Write-Host "[stage] Done. Run with:"
    Write-Host ("[stage]   cd `"{0}`"; java -jar `"{1}`"" -f $stageFull, (Split-Path -Leaf $jarFullPath))
}

if ($SmokeTest) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot "music"))) {
        throw "Smoke test needs a music\ folder in the repo root."
    }
    $smokeDir = Join-Path $repoRoot "target\jar-external-smoke-data"
    if (Test-Path -LiteralPath $smokeDir) {
        Remove-TreeInRepo "target\jar-external-smoke-data"
    }
    Write-Host "[smoke] Running external JAR smoke test from repo root..."
    # extractMusic=false: nothing is bundled, so skip the extraction probe
    # and exercise the local music\ / sfx\ resolution path instead.
    & java "-Dtetris.appDataDir=$smokeDir" "-Dtetris.bootstrap.extractMusic=false" -jar $jarFullPath --smoke-test
    if ($LASTEXITCODE -ne 0) {
        throw "External JAR smoke test failed."
    }
}

Write-Host "[done] Slim JAR built. Place music\ and sfx\ next to it (or run from a"
Write-Host "[done] folder that has them), then: java -jar `"$jarFullPath`""
