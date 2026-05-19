param(
    [switch]$SkipCompile,
    [string]$NotesPath
)

$ErrorActionPreference = 'Stop'

function Invoke-Step {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][scriptblock]$Body
    )

    Write-Host ""
    Write-Host "=== $Name ==="
    & $Body
    if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

if (-not $SkipCompile) {
    Invoke-Step "Compile" {
        $sources = Get-ChildItem -Path src\main\java -Recurse -Filter *.java |
            ForEach-Object { $_.FullName }
        & javac -encoding UTF-8 -d target\classes $sources
    }
}

if (-not $NotesPath) {
    if (-not (Test-Path target)) {
        New-Item -ItemType Directory -Path target | Out-Null
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $NotesPath = Join-Path (Get-Location).Path "target\mab-manual-gate-$stamp.md"
}

$notesDir = Split-Path -Path $NotesPath -Parent
if ($notesDir -and -not (Test-Path -LiteralPath $notesDir)) {
    New-Item -ItemType Directory -Path $notesDir | Out-Null
}

$checklist = @'
# MAB Manual Gate Checklist

Tester:
Date/time:
Result: [ ] PASS   [ ] FAIL
Notes/artifact path:
App process id:

Valid PASS evidence requires Tester plus either concrete Notes or an existing
artifact path. Use record_mab_manual_gate.ps1 to copy the final PASS/FAIL into
MAB_FINAL_GATE_AUDIT.md after the human-visible playthrough.

Before recording PASS with this checklist as the artifact, validate it with:
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate_mab_manual_checklist.ps1 -Path "<this file>"

## Checklist

1. [ ] Normal Tetris starts from the main menu, accepts movement/rotation/drop,
   restarts in place, and returns to the main menu without MAB chrome.
2. [ ] MAB PvE setup opens the safe warhead picker, the picker explains payload,
   charge, route, countdown, impact, BLAST, RAD, EMP, DISARM, SILO, and
   intercept difficulty, and no option clips at 1366x768.
3. [ ] PvE difficulty selection reaches every shipping player-facing tier:
   Easy, Medium, Hard, Expert, and Master.
4. [ ] A PvE match starts, boards remain dominant, and the live high-contrast
   Warhead Tempo/Payload panel shows design, payload, DEFCON, next progress,
   gravity, charge/requirement, route targets, countdown, impact delay, BLAST,
   RAD, EMP, DISARM, SILO, and intercept difficulty without tooltip-only
   critical information.
5. [ ] During PvE DEFCON escalation, both boards pause, a 3-second paused
   countdown is visible, held gameplay input is cleared, hard drop/rotate/hold
   do not confirm the overlay, the human review opens from the current warhead,
   the AI redesign follows automatically, and the match resumes only afterward.
6. [ ] During local PvP setup, P1 and P2 choose separate warheads; both boards
   accept same-keyboard input; DEFCON redesign order is P1 then P2; cancel
   preserves the current design; confirm applies once.
7. [ ] Launch, spin intercept, radiation waves, EMP disruption, disarm, and
   silo damage are visibly understandable during live play.
8. [ ] Result overlay states winner, loser, explicit cause, triggering system or
   payload, DEFCON, final warheads, launches, impacts, radiation waves, EMP
   disruptions, disarm, silo damage, survival time, and max height.
9. [ ] Result overlay distinguishes observed loss causes, including normal
   top-out, garbage overflow, blast overflow, radiation wave overflow, and
   strategic collapse when triggered.
10. [ ] Restart, Back to Setup, and Main Menu are visible and functional from
    the result overlay.
11. [ ] No player-visible text says MIRV, radar, warning, intel, decoy, online,
    or networking.
'@

Set-Content -Path $NotesPath -Value $checklist -Encoding UTF8

Write-Host ""
Write-Host "Manual gate checklist written to:"
Write-Host $NotesPath
Write-Host ""
Write-Host "No game instance was launched."
Write-Host "The active goal is not complete until this checklist is filled with PASS/FAIL evidence."
