# Step 26 - Pre-Game Controls, Local PvP, Nuke Builder Integration

## Files Added
- `src/main/java/com/tetris/controller/LocalPlayerAction.java`
- `src/main/java/com/tetris/controller/LocalPlayerInputBindings.java`
- `src/main/java/com/tetris/controller/LocalInputRouter.java`
- `src/main/java/com/tetris/view/KeyMappingWizardPanel.java`
- `src/main/java/com/tetris/mab/ui/MabLocalPvpConfig.java`
- `src/main/java/com/tetris/mab/ui/MabLocalPvpInputAdapter.java`
- `src/main/java/com/tetris/mab/ui/MabNukeDesignSelection.java`
- `src/main/java/com/tetris/mab/nuke/MabNukeBuilderBridge.java`
- `src/main/java/com/tetris/mab/sim/MabControlMappingProbe.java`
- `src/main/java/com/tetris/mab/sim/MabLocalPvpProbe.java`
- `src/main/java/com/tetris/mab/sim/MabNukeBuilderIntegrationProbe.java`
- `src/main/java/com/tetris/mab/sim/MabUpgradeObservedEffectProbe.java`

## Files Modified
- `src/main/java/com/tetris/Main.java`
- `src/main/java/com/tetris/controller/GameController.java`
- `src/main/java/com/tetris/controller/GameLaunchMode.java`
- `src/main/java/com/tetris/model/Settings.java`
- `src/main/java/com/tetris/view/NukeBuilderDialog.java`
- `src/main/java/com/tetris/view/StartMenu.java`
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java`
- `src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java`
- `src/main/java/com/tetris/mab/sim/MabSimulationInvariants.java`
- `src/main/java/com/tetris/mab/sim/MabSimulationRunner.java`
- `src/main/java/com/tetris/mab/ui/MabBattleShellInputAdapter.java`
- `src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java`
- `src/main/java/com/tetris/mab/ui/MabOpponentBoardPanel.java`
- `src/main/java/com/tetris/mab/ui/MabPveConfig.java`
- `src/main/java/com/tetris/mab/ui/MabUpgradeDraftOverlayPanel.java`
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeDraftManager.java`

## Offline-Only Confirmation
Mutually Assured Blocks remains offline-only. No networking, sockets, matchmaking, client/server code, rollback, transport abstractions, or online-prep APIs were added.

## Architecture Inspection
1. `Main` creates `StartMenu`, wires normal, MAB PvE, and now MAB local PvP factories.
2. `StartMenu` hosts menu, settings, Nuke Builder, MAB selection, setup cards, controls wizard, and game in one `CardLayout`.
3. `Settings` persists key codes in the existing properties file and now also stores Player 2 bindings, the active doctrine key, and `controlsWizardCompleted`.
4. Normal Tetris still uses `InputHandler -> GameController -> GameState`.
5. MAB PvE still uses the existing global `MabBattleShellInputAdapter` around `InputHandler`.
6. The battle-shell adapter clears held keys on overlays/focus loss and now reads the configured active doctrine key.
7. Nuke Builder was previously reachable only as a top-level menu card.
8. MAB PvE setup existed through the inline setup card and `MabPveConfig`.
9. `MatchMode.PVP_LOCAL` and local/shared match factories existed, but the launcher did not expose playable local PvP and the battle shell did not route two humans.
10. Normal Tetris preservation requirement: do not alter its board loop, scoring, piece source, or single-player input path.

## Control Calibration Wizard
`KeyMappingWizardPanel` appears on first launch or with `-Dtetris.controls.wizard=true`. It can also be opened from the new `Controls` menu button.

Player 1 defaults:
- Left Arrow, Right Arrow, Down Arrow, Space, Up Arrow, Z, A, C, P, R, Q.

Player 2 defaults:
- J, L, K, Enter, I, U, O, Semicolon.

Conflict behavior:
- Duplicate keys are detected before save.
- The wizard shows a clear warning and offers `SWAP` or `REJECT`.
- Cross-player duplicate keys are rejected so one key cannot drive both PvP boards.
- Escape while assigning cancels only that assignment.
- Active doctrine conflicts with movement are marked as explicit conflicts.

Persistence:
- Saves through `Settings.save()`.
- Sets `controlsWizardCompleted=true`.
- `-Dtetris.controls.reset=true` resets mappings and clears the wizard flag before launch.

## Local PvP Flow
Menu flow:
- `START MAB`
- `PvE vs AI` or `LOCAL PvP :: SAME KEYBOARD`
- Local PvP setup: Player 1 name, Player 2 name, start level, balance profile, Nuke Design, Start, Back.

Game launch:
- `GameLaunchMode.MAB_LOCAL_PVP`
- `MutuallyAssuredBlocksMatch.createLocalPvpShared(...)`
- two human `GameState`s
- no `MabBoardAiDriver`
- same battle shell layout, right board labelled P2
- shared pause/reset through the local router

Input model:
- Normal and PvE keep `InputHandler`.
- Local PvP uses `LocalInputRouter` plus `MabLocalPvpInputAdapter`.
- Player 1 routes only to Player A.
- Player 2 routes only to Player B.
- Release-all clears both players during setup, overlays, focus loss, result overlay, and shutdown.

Shared piece fairness:
- MAB PvE, local PvP, and headless simulations use shared deterministic MAB piece streams.
- Player A piece `n` equals Player B piece `n`.
- Hold does not consume or mutate the underlying sequence.
- No upgrade or Nuke Builder selection alters the shared sequence.

## PvP Drafts And Active Doctrines
PvP draft v1 is implemented through the existing draft queue:
- Player A level-up queues Player A draft.
- Player B level-up queues Player B draft.
- Both boards pause during the draft.
- The overlay title identifies `PLAYER 1 DOCTRINE LOADOUT` or `PLAYER 2 DOCTRINE LOADOUT`.
- After the queued draft resolves, match phase returns to active.

PvP active doctrines are intentionally limited in this build:
- The local PvP shell shows `ACTIVE OFF`.
- Pressing/opening active doctrines in PvP displays `ACTIVE DOCTRINES: PVE ONLY IN THIS BUILD`.
- PvE Manual Override and EMP remain live and unchanged.

## Nuke Builder Integration
MAB setup now includes:
- current nuke design summary
- `OPEN NUKE BUILDER`
- `RESET NUKE DEFAULT`
- `USE DESIGN` from the embedded builder

Safety boundary:
- The bridge emits only gameplay summaries: display name, size class, doctrine flavor, charge/blast/defense ratings.
- It does not emit raw engineering detail or construction instructions.
- Invalid or incomplete builder designs safely fall back to the default MAB design.

Application behavior:
- PvE applies selected/builder-derived design to Player A. The AI keeps the default design for balance/readability.
- Local PvP applies the selected/default design to both local human participants.

## Balance And Invariants
Added/preserved telemetry and checks cover:
- match duration/ticks, winner/result, launches, impacts, scans, decoys, civil defense, upgrades, AI decisions, garbage, charge, silo state, invariant failures.
- unknown upgrade tags, silent no-op upgrade tags, sequence-mutating upgrade tags.
- local PvP shared sequence requirement.
- local PvP input isolation probe.
- invalid Nuke Builder design safety.
- resolved/cancelled threats not remaining live after prune.

No numeric upgrade/balance tuning was applied; the pass found no invariant failures after fixing the headless simulation factory to use the shared sequence path.

## Verification
Clean command requested by prompt included a recursive delete of `target\classes`; the approval reviewer blocked that exact destructive clean step. Safer compile command used:

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
Write-Host "EXITCODE=$LASTEXITCODE"
```

Final compile result:
- `EXITCODE=0`

Probe results:
- `MabControlMappingProbe`: `success=true`
- `MabLocalPvpProbe`: `success=true`
- `MabNukeBuilderIntegrationProbe`: `success=true`
- `MabUpgradeObservedEffectProbe`: `success=true`
- `MabUpgradeCoverageProbe`: `success=true`
- `MabActiveDoctrineProbe`: `success=true`
- `MabDoctrineStatusOverlayProbe`: `success=true`
- `MabUpgradeOverlayLayoutProbe`: `success=true`
- `MabUpgradeDraftProbe`: `success=true`
- `MabUpgradeEffectProbe`: `success=true`
- `MabInputStateProbe`: `success=true`
- `MabSharedPieceSequenceProbe`: `success=true`
- `MabChargeCalculatorProbe`: `success=true`
- `MabSimplifiedCoreProbe`: `success=true`
- `MabSpinDetectionProbe`: `success=true`
- `MabThreatLifecycleProbe`: `success=true`
- `MabBattleShellLayoutProbe`: `success=true`

Simulation results:
- `MabSimulationRunner smoke`: `success=true`, `invariantFailures=0`
- `MabSimulationRunner ai-vs-dummy 160`: `success=true`, `invariantFailures=0`
- `MabSimulationRunner ai-vs-ai 160`: `success=true`, `invariantFailures=0`
- `MabSimulationRunner balance`: all profiles reported `invariantFailures=0`
- `MabSimulationRunner balance-report`: `success=true`, `invariantFailures=0`

Balance report summary:
- Standard PvE: ticks 160, launches 0, scans 21, decoys 14, AI exec 99, invariant failures 0.
- Gentle PvE: ticks 160, launches 0, scans 16, decoys 10, AI exec 90, invariant failures 0.
- High Pressure PvE: ticks 160, launches 0, scans 30, decoys 20, AI exec 122, invariant failures 0.
- Debug Fast: ticks 160, launches 0, scans 107, decoys 72, AI exec 242, invariant failures 0.

Manual verification:
- Not completed interactively in this run. The launch command remains:
  `java -Dmab.input.debug=true -Dmab.debug.upgrades=true -Dtetris.controls.wizard=true -cp target\classes com.tetris.Main`

## Remaining Limitations
- PvP active doctrines are visible but disabled with an explicit PVE-only message.
- PvE applies builder-derived Nuke Design to Player A only; AI remains default.
- Manual UI/control verification still needs a human pass on the full-screen Swing app.

## Recommended Next Step
Step 27 should focus on a manual playtest polish pass: PvP draft ergonomics, visible nuke design summary placement during live MAB, and optionally a safe two-player active doctrine design if it can remain readable and input-stable.
