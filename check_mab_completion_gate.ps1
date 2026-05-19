param(
    [string]$AuditPath = 'MAB_FINAL_GATE_AUDIT.md'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $AuditPath)) {
    throw "Audit file not found: $AuditPath"
}

function Test-GeneratedManualChecklistPass {
    param([Parameter(Mandatory=$true)][string]$Path)

    $text = Get-Content -LiteralPath $Path -Raw
    if ($text -notmatch '(?m)^# MAB Manual Gate Checklist\s*$') {
        return $true
    }

    if ($text -notmatch '(?m)^Result:\s*\[x\]\s*PASS\s+\[\s*\]\s*FAIL\s*$') {
        return $false
    }

    for ($i = 1; $i -le 11; $i++) {
        if ($text -notmatch "(?m)^\s*$i\.\s*\[[xX]\]") {
            return $false
        }
    }
    return $true
}

$audit = Get-Content -Path $AuditPath -Raw

$automatedPassed = $audit -match 'AUTOMATED MAB FINAL GATE PASSED'
$manualEvidenceMatches = [regex]::Matches(
    $audit,
    '(?ms)^## Manual Gate Evidence\s*(.*?)(?=^## |\z)')

$validManualPass = $false
$manualFail = $false
$manualEvidenceCount = $manualEvidenceMatches.Count
$invalidChecklistArtifacts = @()

foreach ($match in $manualEvidenceMatches) {
    $block = $match.Groups[1].Value
    if ($block -match '\[\s*\]\s*PASS\s+\[x\]\s*FAIL') {
        $manualFail = $true
    }
    if ($block -match '\[x\]\s*PASS\s+\[\s*\]\s*FAIL') {
        $testerOk = $block -match '(?m)^-\s*Tester:\s*(?!\(not provided\)\s*$)\S'
        $artifactMatch = [regex]::Match($block,
                '(?m)^-\s*Artifact path:\s*(?!\(not provided\)\s*$)(\S.*)$')
        $artifactOk = $false
        $artifactProblem = $false
        if ($artifactMatch.Success) {
            $artifactPath = $artifactMatch.Groups[1].Value.Trim()
            if (Test-Path -LiteralPath $artifactPath) {
                $artifactOk = Test-GeneratedManualChecklistPass -Path $artifactPath
                if (-not $artifactOk) {
                    $invalidChecklistArtifacts += $artifactPath
                    $artifactProblem = $true
                }
            } else {
                $artifactProblem = $true
            }
        }
        $notesOk = $block -match '(?m)^-\s*Notes:\s*(?!\(none\)\s*$)\S'
        if ($testerOk -and -not $artifactProblem -and ($artifactOk -or $notesOk)) {
            $validManualPass = $true
        }
    }
}

Write-Host "automatedGateRecorded=$automatedPassed"
Write-Host "manualEvidenceBlocks=$manualEvidenceCount"
Write-Host "validManualPassRecorded=$validManualPass"
Write-Host "manualFailRecorded=$manualFail"
Write-Host "invalidChecklistArtifacts=$($invalidChecklistArtifacts.Count)"

if (-not $automatedPassed) {
    Write-Host "completionGate=BLOCKED automated gate evidence is missing"
    exit 2
}

if ($manualFail) {
    Write-Host "completionGate=BLOCKED manual gate recorded FAIL"
    exit 3
}

if (-not $validManualPass) {
    Write-Host "completionGate=BLOCKED valid manual GUI PASS evidence is missing"
    exit 4
}

Write-Host "completionGate=PASS"
