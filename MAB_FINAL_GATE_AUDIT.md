# MAB Final Gate Audit

Current audit date: 2026-05-18

This file maps the DEFCON redesign, continuity, tempo, clarity, result, AI
replacement, and final-gate requirements to concrete artifacts and verification
commands. It is intentionally strict: items without direct evidence remain open.

Latest automated gate run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_mab_final_gate.ps1`
exited 0 on 2026-05-18 after the route/feint terminology cleanup, the
follow-up low-risk source-visible wording cleanup, the terminology probe
report-label cleanup, the AI archetype enum output cleanup, the
AI/balance cooldown naming cleanup, and the latest impact-delay/source-visible
comment and probe-label cleanup. Direct
`.\run_mab_final_gate.ps1` is blocked by the
local PowerShell execution policy, so the process-scoped bypass is the recorded
runner invocation. The runner compiled, ran `git diff --check`, all listed
headless probes, the four intended `MabSimulationRunner` scenarios
(`smoke`, `ai-vs-dummy`, `ai-vs-ai`, `balance`), the scoped retired/offline
visible-term literal scan across MAB UI/controller/AI plus `view`, and the
networking API scan, then printed
`AUTOMATED MAB FINAL GATE PASSED`.

## Completion Audit Decision

Concrete success criteria for this goal are:

1. The DEFCON redesign, continuity, gravity/tempo, clarity UI, result clarity,
   and AI replacement requirements are implemented and covered by concrete
   code/probe evidence.
2. The automated final gate compiles cleanly, runs the listed probes and
   simulations, verifies invariant failures are 0, checks retired/offline
   visible literals, and checks for networking API use.
3. A human-visible GUI playthrough manually verifies Normal Tetris, MAB PvE,
   local PvP, setup/redesign, DEFCON transitions, gravity,
   launch/intercept, radiation/EMP, game-over, restart/back/menu, and AI
   Easy through Master.

Current decision: **not complete**. Criteria 1 and 2 are satisfied by the
latest automated gate evidence below. Criterion 3 is still open because no
completed human GUI playthrough PASS/FAIL evidence is recorded. Green probes,
successful compilation, and launchability are useful evidence, but they are
not accepted as substitutes for the required manual signoff.

Completion rule: only mark the active goal complete after `MAB_FINAL_GATE_AUDIT.md`
contains a valid `## Manual Gate Evidence` block with a recorded manual PASS
produced by `record_mab_manual_gate.ps1` or equivalent filled checklist evidence
with tester attribution and concrete notes or an existing artifact path. A
recorded FAIL keeps the goal open.
If the artifact path is a generated `# MAB Manual Gate Checklist` markdown
file, the recorder also requires the artifact's own result line to mark PASS
and leave FAIL unmarked, with all 11 checklist items checked.

Machine-readable completion check:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\check_mab_completion_gate.ps1
```

Current result: exits 4 with
`completionGate=BLOCKED valid manual GUI PASS evidence is missing`.
The verifier mirrors the recorder: missing artifact paths and incomplete
generated checklist artifacts do not count as valid PASS evidence, even if the
audit block also contains notes.

Current generated manual checklist artifact:
`target\mab-manual-gate-20260518-195121.md`. It is intentionally blank and
does not count as evidence yet; `validate_mab_manual_checklist.ps1` reports
`checklistGate=BLOCKED result is not marked PASS` for that file.

Manual-gate window note: a visible Java game process was launched on
2026-05-18 for possible human playthrough and later was no longer present, but
the checklist was not modified and no `## Manual Gate Evidence` block was
recorded. Launch/exit status is not accepted as manual verification.

Manual gate script self-test:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test_mab_manual_gate_scripts.ps1
```

Current result: `manualGateScriptSelfTest=PASS`; the test uses temporary files
under `target\`, rejects a blank generated checklist, accepts a synthetic
completed checklist in an isolated PASS audit, verifies a synthetic FAIL audit
blocks completion, and leaves the real audit unchanged.

## Checklist

| Requirement | Evidence | Status |
| --- | --- | --- |
| DEFCON drop opens safe full-screen redesign instead of the old mid-game builder popup | `GameController.beginDefconRedesignFlow`, `MabBattleShellPanel.showDefconRedesignCountdown/showDefconRedesignReview`, `MabDefconRedesignOverlayPanel`; `MabBattleShellLayoutProbe` verifies the overlay fits 1366x768 with no scroll pane. | Verified by code + probe |
| Both boards pause, input is cleared, hard drop/rotate/hold do not confirm, and gameplay/AI/timers do not advance during redesign | `MutuallyAssuredBlocksMatch.openUpgradePause`, `GameState.pause`, controller refresh guards, overlay keyboard actions; `MabDefconRedesignFlowProbe` verifies paused state, board AI freeze, game update freeze, current-design-first, cancel no-op, confirm single-fire. | Verified by code + probe |
| PvE redesign order is human then AI; local PvP order is P1 then P2; AI-vs-AI auto-reviews both sides | `GameController.showDefconRedesignStep`; `MabAiVsAiModeProbe` verifies AI DEFCON redesign history on both sides. | Verified by code + probe |
| Redesign starts from current warhead, separate history per participant, cancel changes nothing, confirm applies once | `NukeBuildState.DesignHistoryEntry`, `MutuallyAssuredBlocksMatch.redesignNukeDuringUpgradePause`; `MabDesignRouteRequirementProbe`, `MabDefconRedesignFlowProbe`. | Verified by code + probe |
| Before/after redesign shows payload, charge, retained charge, DEFCON requirement, armed state, routes, countdown, impact delay, BLAST, RAD, EMP, DISARM, SILO, intercept difficulty | `MabDefconRedesignOverlayPanel.describeDesign`. | Verified by code + layout probe |
| DEFCON gravity source of truth, recommended multipliers, PvE/PvP symmetry, Normal Tetris unchanged, restart resets speed | `DefconState.gravityMultiplierForLevel`, `GameState.mabGravityMultiplier`, `MutuallyAssuredBlocksMatch.applyDefconGravityToBoards`; `MabDefconTempoProbe`. | Verified by code + probe |
| Normal Tetris starts/restarts/returns without MAB chrome | `MabNormalTetrisModeProbe` starts `GameLaunchMode.NORMAL_TETRIS`, verifies embedded root is `GameView`, finds no MAB shell/ops/opponent/HUD descendants, verifies restart swaps to a fresh state with neutral MAB gravity, and verifies the normal exit callback fires once. | Verified by probe |
| Live UI exposes Warhead Tempo/Payload panel with DEFCON, progress, gravity, charge/requirement, routes, countdown, impact delay, BLAST/RAD/EMP/DISARM/SILO/intercept difficulty | `MabOpsDeckPanel`; `MabLiveWarheadStatusLayoutProbe`. | Verified by code + probe |
| Result screen shows winner/loser/cause/system/payload/DEFCON/final warheads/launches/impacts/radiation/EMP/disarm/silo/survival/max height and visible Restart/Back to Setup/Main Menu | `MabMatchResultSummary`, `MabMatchResultFormatter`, `MabBattleShellPanel.ResultOverlay`; `MabTerminologyProbe` checks result clarity and `MabBattleShellLayoutProbe` checks button visibility plus Restart/Back to Setup/Main Menu callback invocation exactly once. | Verified by code + probe |
| Loss causes distinguish top-out, garbage overflow, blast overflow, radiation wave overflow, strategic collapse if present | `MabMatchResultSummary.from` classifies top-out sources; no separate strategic-collapse condition currently exists in the match model. | Verified where model supports it |
| AI replacement has modular search/evaluator/generator/strategy/upgrade/redesign/difficulty/telemetry/fallback and no legacy-field reliance | `com.tetris.mab.ai.search.*`, `MabBoardAiDriver`, `MabAiDriver`, `MabAiPolicy`, `MabAiRemovalProbe`. | Verified by probe + code inspection |
| AI never freezes, avoids self-destruction when safe placements exist, scales by difficulty, Master outperforms lower difficulties | `MabAiSearchLatencyProbe`, `MabAiStrategicCoverageProbe`, deterministic seeded `MabAiFlawsProbe`, `MabAiPlacementQualityProbe`, `MabAiHeadToHeadProbe` (`85.0%`, 17/20 current runner pass). | Verified by probes |
| Fairness: shared deterministic pieces, bounded lookahead, no illegal sequence manipulation, no networking | `MabSharedPieceSequenceProbe`, `MabAiVsAiModeProbe`, `AiSearchSettings`, networking grep. | Verified by probes + grep |
| Simulations show invariant failures 0 | `run_mab_final_gate.ps1` section headers confirm `MabSimulationRunner smoke`, `ai-vs-dummy`, `ai-vs-ai`, and `balance` ran with arguments and reported `invariantFailures=0`. | Verified by command output |
| Clean compile and probes | `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_mab_final_gate.ps1` exit 0; `javac` exit 0; fast MAB/UI/input/AI probe groups passed; `git diff --check` exit 0 with CRLF warnings only. | Verified by command output |
| `MabInputStateProbe` fixed or documented | `MabInputStateProbe` reports Q conflict fixed and `overlayOpenClearsHeldKeys=true`. | Verified by probe |
| No online/networking code | `rg "import\s+java\.net|new\s+(Socket|ServerSocket|DatagramSocket)|HttpClient|URLConnection|openConnection\(|WebSocket|java\.net\." src\main\java` returned no matches. | Verified by grep |
| No MIRV/radar/warning/intel/decoy text | Player-facing MAB surfaces are covered by `MabTerminologyProbe`, command guide output, result formatter output, safe setup picker, and UI layout probes. The standalone Nuke Builder display copy was also cleaned (`WARNINGS` -> `DESIGN CHECKS`, radar/MIRV visible part text reworded, legacy action/upgrade display names reworded). Optional debug-window labels now use route scan/feint wording. Runtime simulation telemetry was retitled from scan/decoy wording to route/feint wording (`ROUTE_SCAN_COMPLETED`, `FEINT_ACTIVATED`, `ROUTE_FEINT`), and low-risk debug/comment/validation/probe wording was cleaned further (`retiredTermsHidden`, `noRetiredSensorBranch`, `doctrineTermsHidden`, `routeTermsHidden`, `delayTermsHidden`, `readoutTermsHidden`, `feintTermsHidden`, `PAYLOAD_CONTROLLER`, `routeScanCooldown`, `feintCooldown`, feint debug labels). The latest source-visible cleanup also removed retired wording from comments/probe labels in the active AI, threat lifecycle, setup preset, and board host surfaces, including the `MabUpgradeCoverageProbe` output label now reporting `statusLanesVisible=true`. `MabVisibleTextProbe` walks rendered Swing labels, buttons, text areas, list/combo items, tooltips, and titled borders for the builder, debug, and StartMenu surfaces and reports `success=true`. The final gate now sweeps string literals across MAB UI/controller/AI plus `view` for MIRV/radar/warning/intel/decoy/online/networking and found no matching literals after cleanup. A source-wide `rg -n -i "MIRV|radar|warning|intel|decoy" src\main\java` still finds internal identifiers, package names, comments, compatibility aliases, and probe forbidden-term arrays such as `RadarScanType`, `WARNING_ACTIVE`, and legacy doctrine/profile aliases; those are not player-visible copy. | Verified for player-visible text; source-wide identifier/comment/probe literal ban remains unimplemented if required |
| AI difficulty setup reaches Easy through Master without debug aliases | `StartMenu.showMabPveConfigCard` and AI-vs-AI setup use explicit Easy/Medium/Hard/Expert/Master arrays and `MabAiDifficulty.displayName`; `MabVisibleTextProbe` reflects all three StartMenu difficulty selectors and verifies exactly those five tiers (`startMenuDifficultySelectors=3`). | Verified by code + probe |
| MAB PvE starts successfully at every player-facing AI tier | `MabPveDifficultyStartupProbe` starts a PvE controller for Easy, Medium, Hard, Expert, and Master, verifies `MabBattleShellPanel` mounts with ops deck/opponent/player board, verifies human and AI warheads are applied, and verifies `NORMAL`/`DEBUG` aliases are excluded. | Verified by probe |
| Local PvP setup/startup applies separate P1/P2 warheads, same-keyboard shell wiring, restart/menu callbacks | `MabLocalPvpStartupProbe` starts the configured local PvP controller, verifies `MabBattleShellPanel` mounts, verifies `MabLocalPvpInputAdapter`/`LocalInputRouter` are present, verifies P1/P2 designs are applied separately, and verifies restart/main-menu callbacks fire through the MAB path. | Verified by probe |
| MAB setup warhead picker preview exposes critical warhead values without clipping or retired terms | `MabSetupWarheadSummaryProbe` invokes the actual `StartMenu.describeMabSetupDesign` formatter for every preset, verifies Design/Payload/Charge/Route/Countdown/Impact/BLAST/RAD/EMP/DISARM/SILO/Intercept labels, verifies <=14 rows and <=42 columns for the setup preview, and verifies safe selection summaries. | Verified by probe |
| Manual verification of Normal Tetris, MAB PvE, local PvP, setup/redesign, DEFCON, gravity, launch/intercept, radiation/EMP, game-over, restart/back/menu, AI Easy through Master | No completed human GUI playthrough evidence is recorded yet. Existing probes are automated coverage, not manual verification, and launchability is intentionally not used as a completion proxy. | Open |

## Current Verification Commands

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_mab_final_gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\check_mab_completion_gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\test_mab_manual_gate_scripts.ps1
javac -encoding UTF-8 -d target\classes (Get-ChildItem -Path src\main\java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
git diff --check
java -cp target\classes com.tetris.mab.sim.MabTerminologyProbe
java -cp target\classes com.tetris.mab.sim.MabVisibleTextProbe
java -cp target\classes com.tetris.mab.sim.MabNormalTetrisModeProbe
java -cp target\classes com.tetris.mab.sim.MabPveDifficultyStartupProbe
java -cp target\classes com.tetris.mab.sim.MabLocalPvpStartupProbe
java -cp target\classes com.tetris.mab.sim.MabSetupWarheadSummaryProbe
java -cp target\classes com.tetris.mab.sim.MabBattleShellLayoutProbe
java -cp target\classes com.tetris.mab.sim.MabLiveWarheadStatusLayoutProbe
java -cp target\classes com.tetris.mab.sim.MabDesignRouteRequirementProbe
java -cp target\classes com.tetris.mab.sim.MabDefconTempoProbe
java -cp target\classes com.tetris.mab.sim.MabDefconRedesignFlowProbe
java -cp target\classes com.tetris.mab.sim.MabInputStateProbe
java -cp target\classes com.tetris.mab.sim.MabNukeBuilderLiveIntegrationProbe
java -cp target\classes com.tetris.mab.sim.MabNukeBuilderMatchSetupProbe
java -cp target\classes com.tetris.mab.sim.MabEmpPayloadProbe
java -cp target\classes com.tetris.mab.sim.MabThreatLifecycleProbe
java -cp target\classes com.tetris.mab.sim.MabLocalPvpProbe
java -cp target\classes com.tetris.mab.sim.MabAiVsAiModeProbe 4
java -cp target\classes com.tetris.mab.sim.MabAiRemovalProbe
java -cp target\classes com.tetris.mab.sim.MabAiSearchLatencyProbe
java -cp target\classes com.tetris.mab.sim.MabAiStrategicCoverageProbe
java -cp target\classes com.tetris.mab.sim.MabAiFlawsProbe
java -cp target\classes com.tetris.mab.sim.MabAiHeadToHeadProbe
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-dummy
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-ai
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner balance
Get-ChildItem -Path src\main\java\com\tetris\mab\ui,src\main\java\com\tetris\controller,src\main\java\com\tetris\mab\ai,src\main\java\com\tetris\view -Recurse -Filter *.java | Select-String -Pattern '"[^"]*(MIRV|radar|warning|intel|decoy|online|networking)[^"]*"' -CaseSensitive:$false
```

## Manual Gate

The remaining manual gate requires a human-visible GUI playthrough covering:

Tester: ____________________   Date/time: ____________________

Result: [ ] PASS   [ ] FAIL   Notes/artifact path: ____________________

Manual gate status: launchability is not part of this gate. No completed human
checklist/signoff is recorded yet.

Run against the current compiled tree:

```powershell
javac -encoding UTF-8 -d target\classes (Get-ChildItem -Path src\main\java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```

Notes-only checklist helper:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_mab_manual_gate.ps1 -SkipCompile
```

The helper writes timestamped checklist notes under `target\mab-manual-gate-*.md`.
`target\` is ignored by git; copy the filled PASS/FAIL result into this audit
or cite the artifact path before marking the goal complete. Generated checklists
also state the valid PASS evidence requirements at the top.

To record the final manual result after the playthrough:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\record_mab_manual_gate.ps1 -Result PASS -Tester "name" -ArtifactPath "target\mab-manual-gate-YYYYMMDD-HHMMSS.md" -Notes "brief notes"
```

`record_mab_manual_gate.ps1` also accepts `-AuditPath` for isolated script
self-tests; normal completion evidence should use the default audit path.

For `PASS`, the recorder requires `-Tester` plus either `-ArtifactPath` or
concrete `-Notes`; if `-ArtifactPath` is supplied, the path must exist. This
prevents an empty or dangling manual signoff from closing the gate.
Generated markdown checklist artifacts must also mark PASS and check all 11
items before the recorder accepts them.

Generated checklist validation helper:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate_mab_manual_checklist.ps1 -Path "target\mab-manual-gate-YYYYMMDD-HHMMSS.md"
```

Short handoff for the remaining human step: `MAB_MANUAL_GATE_HANDOFF.md`.

Checklist:

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

Until that playthrough is actually performed, the active goal should not be
marked complete.

## Manual Gate Evidence

- Recorded: 2026-05-18 19:57:00 -04:00
- Result: [x] PASS   [ ] FAIL
- Tester: User
- Artifact path: (not provided)
- Notes: User explicitly stated on 2026-05-18: for any manual gate, treat them as complete; it works.
