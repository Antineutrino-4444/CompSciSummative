param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('PASS', 'FAIL')]
    [string]$Result,

    [string]$Tester = '',
    [string]$ArtifactPath = '',
    [string]$Notes = '',
    [string]$AuditPath = 'MAB_FINAL_GATE_AUDIT.md'
)

$ErrorActionPreference = 'Stop'

$auditPath = if ([System.IO.Path]::IsPathRooted($AuditPath)) {
    $AuditPath
} else {
    Join-Path (Get-Location).Path $AuditPath
}
if (-not (Test-Path $auditPath)) {
    throw "Audit file not found: $auditPath"
}

function Assert-GeneratedManualChecklistPass {
    param([Parameter(Mandatory=$true)][string]$Path)

    $text = Get-Content -LiteralPath $Path -Raw
    if ($text -notmatch '(?m)^# MAB Manual Gate Checklist\s*$') {
        return
    }

    if ($text -notmatch '(?m)^Result:\s*\[x\]\s*PASS\s+\[\s*\]\s*FAIL\s*$') {
        throw 'PASS checklist artifact must mark Result: [x] PASS   [ ] FAIL.'
    }

    $missing = @()
    for ($i = 1; $i -le 11; $i++) {
        if ($text -notmatch "(?m)^\s*$i\.\s*\[[xX]\]") {
            $missing += $i
        }
    }
    if ($missing.Count -gt 0) {
        throw "PASS checklist artifact has unchecked required items: $($missing -join ', ')"
    }
}

$timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
$testerText = if ($Tester.Trim()) { $Tester.Trim() } else { '(not provided)' }
$artifactText = if ($ArtifactPath.Trim()) { $ArtifactPath.Trim() } else { '(not provided)' }
$notesText = if ($Notes.Trim()) { $Notes.Trim() } else { '(none)' }

if ($Result -eq 'PASS') {
    if (-not $Tester.Trim()) {
        throw 'PASS requires -Tester so the manual GUI signoff is attributable.'
    }
    if (-not $ArtifactPath.Trim() -and -not $Notes.Trim()) {
        throw 'PASS requires -ArtifactPath or -Notes with concrete manual playthrough evidence.'
    }
    if ($ArtifactPath.Trim() -and -not (Test-Path -LiteralPath $ArtifactPath.Trim())) {
        throw "PASS artifact path does not exist: $($ArtifactPath.Trim())"
    }
    if ($ArtifactPath.Trim()) {
        Assert-GeneratedManualChecklistPass -Path $ArtifactPath.Trim()
    }
}

$resultMarker = if ($Result -eq 'PASS') {
    '[x] PASS   [ ] FAIL'
} else {
    '[ ] PASS   [x] FAIL'
}

$entry = @"

## Manual Gate Evidence

- Recorded: $timestamp
- Result: $resultMarker
- Tester: $testerText
- Artifact path: $artifactText
- Notes: $notesText
"@

Add-Content -Path $auditPath -Value $entry -Encoding UTF8

Write-Host "Recorded manual gate result in $auditPath"
Write-Host "Result=$Result"
