param(
    [int]$MeasureSeconds = 4
)

$ErrorActionPreference = 'Stop'

Write-Host "=== Compile ==="
$sources = Get-ChildItem -Path src\main\java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -d target\classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "Compile failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "=== FPS Stability Probe ==="
& java -cp target\classes com.tetris.mab.sim.GameFpsStabilityProbe $MeasureSeconds
if ($LASTEXITCODE -ne 0) {
    throw "FPS stability probe failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "AUTOMATED FPS GATE PASSED"
