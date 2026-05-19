$ErrorActionPreference = 'Stop'

function Invoke-GateStep {
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

function Invoke-JavaProbe {
    param(
        [Parameter(Mandatory=$true)][string]$ClassName,
        [string[]]$ProbeArgs = @()
    )

    $displayName = if ($ProbeArgs.Count -gt 0) {
        "$ClassName $($ProbeArgs -join ' ')"
    } else {
        $ClassName
    }
    $argsForStep = $ProbeArgs

    Invoke-GateStep $displayName {
        & java -cp target\classes $ClassName @argsForStep
    }
}

Invoke-GateStep "Compile" {
    $sources = Get-ChildItem -Path src\main\java -Recurse -Filter *.java |
        ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -d target\classes $sources
}

Invoke-GateStep "git diff --check" {
    & git diff --check
}

$javaProbes = @(
    @{ Class = 'com.tetris.mab.sim.MabTerminologyProbe' },
    @{ Class = 'com.tetris.mab.sim.MabVisibleTextProbe' },
    @{ Class = 'com.tetris.mab.sim.MabNormalTetrisModeProbe' },
    @{ Class = 'com.tetris.mab.sim.MabPveDifficultyStartupProbe' },
    @{ Class = 'com.tetris.mab.sim.MabLocalPvpStartupProbe' },
    @{ Class = 'com.tetris.mab.sim.MabSetupWarheadSummaryProbe' },
    @{ Class = 'com.tetris.mab.sim.MabBattleShellLayoutProbe' },
    @{ Class = 'com.tetris.mab.sim.MabLiveWarheadStatusLayoutProbe' },
    @{ Class = 'com.tetris.mab.sim.MabDesignRouteRequirementProbe' },
    @{ Class = 'com.tetris.mab.sim.MabDefconTempoProbe' },
    @{ Class = 'com.tetris.mab.sim.MabDefconRedesignFlowProbe' },
    @{ Class = 'com.tetris.mab.sim.MabInputStateProbe' },
    @{ Class = 'com.tetris.mab.sim.MabNukeBuilderLiveIntegrationProbe' },
    @{ Class = 'com.tetris.mab.sim.MabNukeBuilderMatchSetupProbe' },
    @{ Class = 'com.tetris.mab.sim.MabEmpPayloadProbe' },
    @{ Class = 'com.tetris.mab.sim.MabThreatLifecycleProbe' },
    @{ Class = 'com.tetris.mab.sim.MabLocalPvpProbe' },
    @{ Class = 'com.tetris.mab.sim.MabAiVsAiModeProbe'; Args = @('4') },
    @{ Class = 'com.tetris.mab.sim.MabAiRemovalProbe' },
    @{ Class = 'com.tetris.mab.sim.MabAiSearchLatencyProbe' },
    @{ Class = 'com.tetris.mab.sim.MabAiStrategicCoverageProbe' },
    @{ Class = 'com.tetris.mab.sim.MabAiFlawsProbe' },
    @{ Class = 'com.tetris.mab.sim.MabAiHeadToHeadProbe' },
    @{ Class = 'com.tetris.mab.sim.MabSimulationRunner'; Args = @('smoke') },
    @{ Class = 'com.tetris.mab.sim.MabSimulationRunner'; Args = @('ai-vs-dummy') },
    @{ Class = 'com.tetris.mab.sim.MabSimulationRunner'; Args = @('ai-vs-ai') },
    @{ Class = 'com.tetris.mab.sim.MabSimulationRunner'; Args = @('balance') }
)

foreach ($probe in $javaProbes) {
    $argsForProbe = if ($probe.ContainsKey('Args')) { $probe.Args } else { @() }
    Invoke-JavaProbe -ClassName $probe.Class -ProbeArgs $argsForProbe
}

Invoke-GateStep "Scoped retired/offline visible-term literal scan" {
    $paths = @(
        'src\main\java\com\tetris\mab\ui',
        'src\main\java\com\tetris\controller',
        'src\main\java\com\tetris\mab\ai',
        'src\main\java\com\tetris\view'
    )
    $matches = Get-ChildItem -Path $paths -Recurse -Filter *.java |
        Select-String -Pattern '"[^"]*(MIRV|radar|warning|intel|decoy|online|networking)[^"]*"' -CaseSensitive:$false
    if ($matches) {
        $matches | ForEach-Object { Write-Host $_ }
        throw "Retired/offline visible-term literals found"
    }
}

Invoke-GateStep "Networking API scan" {
    $pattern = 'import\s+java\.net|new\s+(Socket|ServerSocket|DatagramSocket)|HttpClient|URLConnection|openConnection\(|WebSocket|java\.net\.'
    $matches = Get-ChildItem -Path src\main\java -Recurse -Filter *.java |
        Select-String -Pattern $pattern
    if ($matches) {
        $matches | ForEach-Object { Write-Host $_ }
        throw "Networking API usage found"
    }
}

Write-Host ""
Write-Host "AUTOMATED MAB FINAL GATE PASSED"
Write-Host "Manual GUI playthrough/signoff is still required before marking the goal complete."
