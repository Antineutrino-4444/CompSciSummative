param(
    [Parameter(Mandatory=$true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Host "checklistGate=BLOCKED file not found"
    exit 2
}

$text = Get-Content -LiteralPath $Path -Raw
$isGeneratedChecklist = $text -match '(?m)^# MAB Manual Gate Checklist\s*$'
Write-Host "isGeneratedChecklist=$isGeneratedChecklist"

if (-not $isGeneratedChecklist) {
    Write-Host "checklistGate=BLOCKED not a generated MAB manual checklist"
    exit 3
}

$resultPassMarked = $text -match '(?m)^Result:\s*\[x\]\s*PASS\s+\[\s*\]\s*FAIL\s*$'
Write-Host "resultPassMarked=$resultPassMarked"
if (-not $resultPassMarked) {
    Write-Host "checklistGate=BLOCKED result is not marked PASS"
    exit 4
}

$checked = 0
$missing = @()
for ($i = 1; $i -le 11; $i++) {
    if ($text -match "(?m)^\s*$i\.\s*\[[xX]\]") {
        $checked++
    } else {
        $missing += $i
    }
}

Write-Host "checkedItems=$checked"
if ($missing.Count -gt 0) {
    Write-Host "missingItems=$($missing -join ',')"
    Write-Host "checklistGate=BLOCKED required checklist items are unchecked"
    exit 5
}

Write-Host "checklistGate=PASS"
