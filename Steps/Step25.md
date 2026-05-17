# Step 25 - Active Doctrine Integration and Upgrade Completion Pass

Step 25 finishes the live integration pass for the Step 24 upgrade draft system.
The goal was not to replace or rebalance the card list. The goal was to make
owned doctrines visible between drafts, give active cards a real in-match use
path, and make every registered card either do something concrete at runtime or
be documented as a temporary limitation.

## 1. Offline-Only Scope

Mutually Assured Blocks remains an offline-only game mode.

Supported scope:

- PvE.
- Local 1v1 later.
- Practice and debug probes.

Out of scope and not added:

- Online multiplayer.
- Networking.
- Client/server architecture.
- Matchmaking.
- Sockets.
- Rollback netcode.
- Online synchronization.
- Network transport layers.

No Step 25 code reserves online APIs or changes the shared deterministic piece
sequence.

## 2. Files Added

- `src/main/java/com/tetris/mab/upgrade/draft/MabActiveDoctrineType.java`
- `src/main/java/com/tetris/mab/upgrade/draft/MabActiveDoctrineState.java`
- `src/main/java/com/tetris/mab/upgrade/draft/MabActiveDoctrineAvailability.java`
- `src/main/java/com/tetris/mab/upgrade/draft/MabActiveDoctrineUseResult.java`
- `src/main/java/com/tetris/mab/ui/MabActiveDoctrineOverlayPanel.java`
- `src/main/java/com/tetris/mab/ui/MabDoctrineStatusOverlayPanel.java`
- `src/main/java/com/tetris/mab/sim/MabUpgradeCoverageProbe.java`
- `src/main/java/com/tetris/mab/sim/MabActiveDoctrineProbe.java`
- `src/main/java/com/tetris/mab/sim/MabDoctrineStatusOverlayProbe.java`
- `Step25.md`

## 3. Files Modified

- `src/main/java/com/tetris/mab/ParticipantState.java`
- `src/main/java/com/tetris/mab/ActiveLaunchState.java`
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java`
- `src/main/java/com/tetris/mab/impact/ImpactResolver.java`
- `src/main/java/com/tetris/mab/intercept/InterceptResolver.java`
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeEffectResolver.java`
- `src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java`
- `src/main/java/com/tetris/mab/ui/MabBattleShellInputAdapter.java`
- `src/main/java/com/tetris/mab/ui/MabTacticalStripPanel.java`
- `src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java`
- `src/main/java/com/tetris/mab/sim/MabUpgradeDraftProbe.java`
- `src/main/java/com/tetris/mab/sim/MabUpgradeEffectProbe.java`
- `src/main/java/com/tetris/mab/sim/MabUpgradeOverlayLayoutProbe.java`
- `src/main/java/com/tetris/mab/sim/MabInputStateProbe.java`
- `src/main/java/com/tetris/mab/sim/MabBattleShellLayoutProbe.java`

## 4. Why This Step Exists

Step 24 made draft cards selectable and added the overlay. After that, the main
remaining issues were:

- Active cards were definitions, not usable commands.
- Owned upgrades were hard to see between drafts.
- Several defensive and intel-flavored effects were not visible enough.
- Probe coverage checked card generation and some effect math, but not enough
  live match state.

Step 25 closes those gaps without rewriting the Tetris engine, changing the
piece source, or redesigning the battle shell.

## 5. Upgrade Audit

### Current Active Cards

- `manual_override`
  - Display: Manual Override.
  - Tag: `tempo_manual_override`.
  - Classification: `ACTIVE_EFFECT`.
  - Runtime: owned players can open the active doctrine overlay and fire a weak
    launch when nuke-ready.

- `emp`
  - Display: EMP.
  - Tag: `tempo_emp`.
  - Classification: `ACTIVE_EFFECT`.
  - Runtime: owned players can spend 40 charge to cancel a valid opponent launch
    or live incoming warning threat.

### Passive Effects With Runtime Hooks

- Charge bonuses:
  - Efficient Reactor.
  - Combo Capacitor.
  - Streak Stoker.
  - B2B Amplifier.
  - Resonance Chamber.
  - Spin Doctrine.
  - Wrist Drill.
  - TST Program.
  - Spin Network.
  - Tetris Doctrine.
  - Clean Well Logistics.
  - Perfect Clear Battery.
  - Wellsmith.
  - Quiet Storm.
  - Stockpile.
  - Adrenaline Sequence.
  - Hair Trigger.

- Route and tempo hooks:
  - Cascade Reactor.
  - Spin Launch Crew.
  - Fast Fuse.
  - Rapid Assembly.
  - Retaliation Doctrine.

- Power hooks:
  - Light the Fuse.
  - Heavy Warhead.
  - Dirty Payload.
  - Glass Cannon.
  - Penetrator Package.

- Defense hooks:
  - Shelters.
  - Bunker.
  - Cold Steel.
  - Intercept Crews.
  - Emergency Protocols.
  - Counterspin Training.
  - Hardened Silos.
  - Dead Hand Protocol.

### Flags, Metadata, or Status-Only Effects

- Manual Override ready-cycle indicator remains a player-facing flag and now has
  a real activation path.
- Bunker and Emergency Protocols have once-per-match used flags.
- Hardened Silos and Dead Hand Protocol have once-per-match fatal-impact
  prevention flags.
- Reactive Plating is classified as `STATUS_EFFECT`; it is represented and
  visible, but the full warning-window doubling behavior is deferred.
- Active doctrine state records last used doctrine and result text.

### Explicitly Deferred Effects

The coverage probe fails if a card silently no-ops. The following tags are
known and deliberately classified as `DOCUMENTED_DEFERRED`:

- `tempo_jam`
  - Card: Jam.
  - Deferred behavior: increasing an opponent route target for one cycle.

- `defense_ablative_spin`
  - Card: Ablative Spin.
  - Deferred behavior: one spin intercept covering two clustered threats.

Dead Hand Protocol's automatic counter-launch is also deferred for v1. Its
fatal-impact prevention hook is implemented now.

### Effect Hooks In `MabUpgradeEffectResolver`

The resolver now exposes:

- Known effect tag registry and effect classifications.
- Charge modification hooks.
- Charge cap and ready threshold hooks.
- Route target hooks.
- Spin pip retention.
- Cascade Reactor route advancement.
- Launch-fired charge refund.
- Impact-taken retaliation charge.
- Full-intercept charge refund.
- Extra outgoing garbage.
- Extra incoming garbage.
- Incoming garbage mitigation.
- Intercept strength delta.
- Penetrator strength delta.
- Fatal-prevention state checks for Dead Hand and Hardened Silos.

### Effects Applied In Match Code

`MutuallyAssuredBlocksMatch` now applies:

- Active doctrine availability and execution.
- Manual Override launch creation.
- EMP launch/threat cancellation.
- Active doctrine event logging.
- Manual Override ready-cycle reset.
- Route reset after Manual Override.
- Rapid Assembly refund after active/manual launches.
- Light the Fuse and outgoing garbage bonuses when launches are authorized.
- Cascade Reactor route pip advancement during simplified clear processing.
- Fatal-prevention event logging after impact resolution.

### Effects Applied In Impact and Intercept Code

`ImpactResolver` now applies:

- Manual Override half-power damage.
- EMP-weakened damage hook for future weakening mode.
- Extra outgoing garbage stored on launches.
- Extra incoming garbage from risky owned cards.
- Incoming mitigation from Shelters, Bunker, and Emergency Protocols.
- Once-per-match fatal-impact prevention from Dead Hand Protocol or Hardened
  Silos.

`InterceptResolver` now applies:

- Intercept Crews strength bonus.
- Penetrator Package strength reduction against defender intercepts.

### Visible To The Player

- `STATUS` shell button opens the loaded doctrine inventory.
- `ACTIVE Q` shell button opens the active doctrine selector.
- `Q` opens the active doctrine selector when the battle shell is active.
- Tactical strips show doctrine count and active doctrine availability.
- Ops deck shows threat tracking, ETA pieces, and `ACTIVE: Q` / `ACTIVE: Q READY`.
- The active overlay shows disabled reasons such as `NOT READY`, `NO TARGET`,
  `INSUFFICIENT CHARGE`, and `USED THIS CYCLE`.
- Manual Override displays `MANUAL OVERRIDE FIRED` / `HALF-POWER LAUNCH`.
- EMP displays `EMP DISCHARGE` / `LAUNCH CANCELLED`.
- Fatal prevention logs and shell refresh paths expose `DEAD HAND TRIGGERED` or
  `HARDENED SILOS HELD` style state.

### Invisible But Working

- Charge math and stack counts.
- Manual Override launch metadata.
- EMP launch cancellation state.
- Outgoing garbage modifiers.
- Incoming garbage mitigation.
- Intercept strength deltas.
- Once-per-match defensive used flags.
- Shared sequence preservation.

### Effects Needing Dedicated Probes

Added in this step:

- `MabUpgradeCoverageProbe`.
- `MabActiveDoctrineProbe`.
- `MabDoctrineStatusOverlayProbe`.

Extended in this step:

- `MabUpgradeEffectProbe`.
- `MabUpgradeOverlayLayoutProbe`.
- `MabInputStateProbe`.
- `MabBattleShellLayoutProbe`.

## 6. Active Doctrine Model

`MabActiveDoctrineType` defines the currently supported active doctrines:

- Manual Override.
- EMP.

`MabActiveDoctrineState` is stored per participant in `ParticipantState`. It
tracks:

- Manual Override used this ready cycle.
- EMP used this match.
- Last active doctrine used.
- Last active doctrine result.

`MabActiveDoctrineAvailability` is the UI-facing availability row. It includes:

- Doctrine type.
- Owned flag.
- Available flag.
- Status text.
- Reason text.

`MabActiveDoctrineUseResult` is returned after an activation attempt and carries
the display headline, subline, launch id or threat id, and charge spent.

This is a local/offline match model only. It is not an action network protocol.

## 7. Manual Override V1

Eligibility:

- Participant owns Manual Override.
- Match phase is `ACTIVE`.
- Participant is nuke-ready.
- No active outgoing launch is already in countdown or in flight.
- A route has not already completed.
- Manual Override has not already been used this ready cycle.

Activation:

- Open the active doctrine overlay with `ACTIVE Q`, `Q`, or the shell button.
- Select `MANUAL OVERRIDE`.
- The hotkey never fires the launch directly.

Effect:

- Fires a launch through the normal launch pipeline.
- Marks the launch with `manualOverride=true`.
- Impact resolution halves blast, radiation, disarm, and silo damage values with
  minimum useful rounding.
- Resets nuke-ready and route progress for the current cycle.
- Marks Manual Override used for that cycle.
- Logs `MAB_ACTIVE_DOCTRINE_USED` with `manual_override`.
- Displays `MANUAL OVERRIDE FIRED` and `HALF-POWER LAUNCH`.

Manual Override does not alter the piece sequence and does not override incoming
spin-intercept priority.

## 8. EMP V1

Eligibility:

- Participant owns EMP.
- Match phase is `ACTIVE`.
- Participant has at least 40 charge.
- Participant has not used EMP this match.
- There is a valid opponent launch in countdown or in flight, or a live warning
  threat against the participant.

Activation:

- Open the active doctrine overlay.
- Select `EMP`.

Effect:

- Spends 40 charge.
- Cancels the selected launch and associated live warning threat.
- Marks EMP used for the match.
- Logs `MAB_ACTIVE_DOCTRINE_USED` with `emp`.
- Displays `EMP DISCHARGE` and `LAUNCH CANCELLED`.

The impact resolver also contains an EMP-weakened launch flag hook for a future
balance variant, but the current v1 behavior is full cancellation before impact.

## 9. Dead Hand and Fatal-Impact Prevention V1

If an owned fatal-prevention card would be relevant during nuke impact garbage,
the impact resolver can consume it once per match.

Current behavior:

- Dead Hand Protocol is checked first.
- Hardened Silos is checked as the fallback.
- If impact grace identifies immediate rows that would be unsafe, the hook can
  discard enough deferred fatal pressure for v1 survival.
- The effect is marked used.
- The match logs:
  - `MAB_FATAL_IMPACT_PREVENTED`.
  - `MAB_DEAD_HAND_TRIGGERED` when Dead Hand was the source.

Deferred:

- Dead Hand's automatic counter-launch is not fired in v1. The fatal-prevention
  behavior is real, and the counter-launch remains a documented future pass
  item pending the updated upgrade list.

## 10. Intel Visibility

The current v2 registry has no dedicated INTEL category cards, but Step 25
adds visible intel lanes for owned or future intel/status cards:

- Doctrine status overlay includes an `INTEL` group.
- Ops deck shows threat count and ETA pieces.
- Ops deck distinguishes warning-active and impact-ready state through threat
  tracking text.
- Station and tactical surfaces now have room for active doctrine and threat
  status without adding a large debug panel.

This keeps intel readable without crowding the player or opponent boards.

## 11. Doctrine Status Overlay

`MabDoctrineStatusOverlayPanel` shows:

- Title: `LOADED DOCTRINES`.
- Subtitle: `CURRENT MATCH INVENTORY`.
- Active doctrines in a fixed top section.
- Owned upgrades grouped by:
  - CHARGE.
  - TETRIS.
  - SPIN.
  - DEFENSE.
  - POWER.
  - INTEL.
  - TEMPO.
- Stack counts.
- Short effect text.
- Status values such as `PASSIVE`, `ACTIVE READY`, `ACTIVE LOCKED`, `USED`, and
  `ONCE PER MATCH USED`.

The owned list may scroll, but the active doctrine section stays visible. Empty
inventory state is readable and themed.

## 12. Active Doctrine Overlay

`MabActiveDoctrineOverlayPanel` shows:

- Title: `ACTIVE DOCTRINES`.
- Subtitle: `SELECT AVAILABLE COMMAND`.
- Manual Override card.
- EMP card.
- Disabled reasons when a command is unavailable.
- Cancel action.

It uses battle-shell colors, does not require text entry, and does not expose a
default white Swing panel.

## 13. Input and Focus Handling

Step 25 preserves the Step 23/24 MAB input fix:

- Opening active or status overlays calls the shell input-clear path.
- Modal overlay visibility gates gameplay forwarding in
  `MabBattleShellInputAdapter`.
- `Q` is intercepted only by the MAB battle shell and only opens the selector.
- `Q` does not fire an active doctrine directly.
- Escape can close modal overlays safely.
- Closing overlays restores focus to gameplay.
- Buttons are made non-focusable where appropriate.
- Probes check that overlay open/close does not remove board components and
  does not require text input.

## 14. Build Verification

Compile command used:

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
Write-Host "EXITCODE=$LASTEXITCODE"
```

Result:

```text
EXITCODE=0
```

## 15. Probe Results

Commands and results:

```text
java -cp target\classes com.tetris.mab.sim.MabUpgradeCoverageProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabActiveDoctrineProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabDoctrineStatusOverlayProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabUpgradeOverlayLayoutProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabUpgradeDraftProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabUpgradeEffectProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabInputStateProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabSharedPieceSequenceProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabChargeCalculatorProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabSimplifiedCoreProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabSpinDetectionProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabThreatLifecycleProbe
success=true

java -cp target\classes com.tetris.mab.sim.MabBattleShellLayoutProbe
success=true
```

Key new probe details:

```text
=== MAB Upgrade Coverage Probe ===
registryCards=40
knownEffectTags=true
manualOverrideActive=true
empActive=true
deadHandHooked=true
intelVisible=true
noSilentNoOps=true
sharedSequenceUnaffected=true
success=true
```

```text
=== MAB Active Doctrine Probe ===
manualUnavailableWithoutCard=true
manualUnavailableNotReady=true
manualAvailableWhenReady=true
manualFiresLaunch=true
manualConsumesCycle=true
manualNoDoubleUse=true
empUnavailableWithoutCard=true
empUnavailableNoCharge=true
empUnavailableNoTarget=true
empAvailableWithTarget=true
empSpendsCharge=true
empCancelsOrWeakens=true
sharedSequenceUnaffected=true
success=true
```

```text
=== MAB Doctrine Status Overlay Probe ===
overlayInstantiates=true
overlayFits1366x768=true
ownedUpgradesGrouped=true
activeDoctrinesShown=true
multipleOwnedListScrollsOnlyListArea=true
noDefaultWhitePanels=true
emptyInventoryReadable=true
openingClosingKeepsBoards=true
success=true
```

## 16. Simulation Results

Commands and results:

```text
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
success=true

java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-dummy 160
success=true

java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-ai 160
success=true

java -cp target\classes com.tetris.mab.sim.MabSimulationRunner balance
completed all profiles with invariantFailures=0
```

The balance command is still a comparison report rather than a single
`success=true` probe line. It ran successfully and reported no invariant
failures for Standard PvE, Gentle PvE, High Pressure PvE, or Debug Fast.

## 17. Manual Verification Checklist

Manual launch command:

```powershell
java -Dmab.input.debug=true -Dmab.debug.upgrades=true -cp target\classes com.tetris.Main
```

Run status:

```text
The launcher command was started successfully during Step 25 verification.
The remaining checklist is an interactive GUI pass and requires human visual
confirmation in the open Swing window.
```

Checklist:

- Start Normal Tetris and confirm controls remain unchanged.
- Start MAB PvE and confirm both boards remain visible.
- Open `STATUS` and confirm owned doctrines are readable.
- Open `ACTIVE Q` or press `Q` and confirm active doctrines are readable.
- Draft or debug-add Manual Override and verify it becomes available when ready.
- Fire Manual Override and verify a weak launch fires and the ready cycle resets.
- Confirm Manual Override cannot fire twice in the same ready cycle.
- Draft or debug-add EMP and verify it is disabled with no target.
- Create an incoming or opponent launch target and verify EMP cancels it for 40
  charge.
- Trigger fatal impact pressure and verify Dead Hand or Hardened Silos prevents
  it once.
- Open and close overlays repeatedly and confirm no stuck movement.
- Confirm upgrade draft and result overlays still work.
- Confirm restart and back-to-menu still work.

Automated probes cover the state, layout, and input invariants above. The full
GUI walk-through is interactive and should be completed from the launcher.

## 18. Remaining Limitations

- Jam is documented deferred.
- Ablative Spin is documented deferred.
- Reactive Plating is status-only in this pass.
- Dead Hand auto-counter-launch is deferred; fatal-impact prevention is active.
- EMP uses full cancellation in v1. The impact resolver has a weakening hook for
  a possible future balance variant.
- Intel category lanes are present and readable, but the current v2 registry has
  no dedicated INTEL cards.

## 19. Pending Updated Upgrade List

The user will provide an updated upgrade list later. Until then, Step 25 keeps
the current 40-card registry intact and avoids broad rebalancing. Future work
should map the updated list into the existing classifications:

- `PASSIVE_EFFECT`.
- `ACTIVE_EFFECT`.
- `UI_INTEL_EFFECT`.
- `STATUS_EFFECT`.
- `DOCUMENTED_DEFERRED`.

The coverage probe should remain the guardrail against silent no-op cards.
