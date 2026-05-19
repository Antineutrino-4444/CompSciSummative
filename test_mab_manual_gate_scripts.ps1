$ErrorActionPreference = 'Stop'

function Invoke-ScriptExpectExit {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][int]$ExpectedExit,
        [Parameter(Mandatory=$true)][string[]]$ArgsForPowerShell
    )

    & powershell -NoProfile -ExecutionPolicy Bypass @ArgsForPowerShell
    $actual = $LASTEXITCODE
    $ok = $actual -eq $ExpectedExit
    Write-Host "$Name expected=$ExpectedExit actual=$actual ok=$ok"
    if (-not $ok) {
        throw "$Name expected exit $ExpectedExit but got $actual"
    }
}

function Write-CompleteChecklist {
    param([Parameter(Mandatory=$true)][string]$Path)

    $items = for ($i = 1; $i -le 11; $i++) {
        "$i. [x] synthetic checked item $i"
    }
    $content = @(
        '# MAB Manual Gate Checklist',
        '',
        'Tester: Script Self-Test',
        'Date/time: synthetic',
        'Result: [x] PASS   [ ] FAIL',
        'Notes/artifact path: synthetic self-test',
        'App process id: none',
        '',
        '## Checklist',
        ''
    ) + $items
    Set-Content -LiteralPath $Path -Value $content -Encoding UTF8
}

if (-not (Test-Path -LiteralPath 'target')) {
    New-Item -ItemType Directory -Path 'target' | Out-Null
}

$targetRoot = (Resolve-Path -LiteralPath 'target').Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$work = Join-Path $targetRoot "mab-manual-gate-script-selftest-$stamp"
New-Item -ItemType Directory -Path $work | Out-Null

try {
    $blankChecklist = Join-Path $work 'blank.md'
    $completeChecklist = Join-Path $work 'complete.md'
    $audit = Join-Path $work 'audit.md'
    $failAudit = Join-Path $work 'fail-audit.md'

    Invoke-ScriptExpectExit -Name 'generate blank checklist' -ExpectedExit 0 `
        -ArgsForPowerShell @('-File', '.\run_mab_manual_gate.ps1',
            '-SkipCompile', '-NotesPath', $blankChecklist)

    Invoke-ScriptExpectExit -Name 'blank checklist blocked' -ExpectedExit 4 `
        -ArgsForPowerShell @('-File', '.\validate_mab_manual_checklist.ps1',
            '-Path', $blankChecklist)

    Set-Content -LiteralPath $audit -Value 'AUTOMATED MAB FINAL GATE PASSED' -Encoding UTF8
    Invoke-ScriptExpectExit -Name 'recorder rejects blank checklist pass' -ExpectedExit 1 `
        -ArgsForPowerShell @('-File', '.\record_mab_manual_gate.ps1',
            '-Result', 'PASS',
            '-Tester', 'Script Self-Test',
            '-ArtifactPath', $blankChecklist,
            '-Notes', 'synthetic self-test',
            '-AuditPath', $audit)

    Write-CompleteChecklist -Path $completeChecklist
    Invoke-ScriptExpectExit -Name 'complete checklist validates' -ExpectedExit 0 `
        -ArgsForPowerShell @('-File', '.\validate_mab_manual_checklist.ps1',
            '-Path', $completeChecklist)

    Invoke-ScriptExpectExit -Name 'recorder accepts complete checklist pass' -ExpectedExit 0 `
        -ArgsForPowerShell @('-File', '.\record_mab_manual_gate.ps1',
            '-Result', 'PASS',
            '-Tester', 'Script Self-Test',
            '-ArtifactPath', $completeChecklist,
            '-Notes', 'synthetic self-test',
            '-AuditPath', $audit)

    Invoke-ScriptExpectExit -Name 'completion gate passes synthetic audit' -ExpectedExit 0 `
        -ArgsForPowerShell @('-File', '.\check_mab_completion_gate.ps1',
            '-AuditPath', $audit)

    Set-Content -LiteralPath $failAudit -Value 'AUTOMATED MAB FINAL GATE PASSED' -Encoding UTF8
    Invoke-ScriptExpectExit -Name 'recorder accepts manual fail evidence' -ExpectedExit 0 `
        -ArgsForPowerShell @('-File', '.\record_mab_manual_gate.ps1',
            '-Result', 'FAIL',
            '-Tester', 'Script Self-Test',
            '-Notes', 'synthetic failure blocks completion',
            '-AuditPath', $failAudit)

    Invoke-ScriptExpectExit -Name 'completion gate blocks manual fail audit' -ExpectedExit 3 `
        -ArgsForPowerShell @('-File', '.\check_mab_completion_gate.ps1',
            '-AuditPath', $failAudit)

    Write-Host 'manualGateScriptSelfTest=PASS'
} finally {
    if (Test-Path -LiteralPath $work) {
        $resolvedWork = (Resolve-Path -LiteralPath $work).Path
        if ($resolvedWork.StartsWith($targetRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $work -Recurse -Force
        }
    }
}
