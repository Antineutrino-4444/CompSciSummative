# Step 19 — Mutually Assured Blocks: Balance Pass v1, PvE Pacing Profiles, Simulation-Based Tuning

## 1. Files added / modified

NEW
- [src/main/java/com/tetris/mab/balance/MabBalanceProfile.java](src/main/java/com/tetris/mab/balance/MabBalanceProfile.java)
- [src/main/java/com/tetris/mab/balance/MabBalanceProfiles.java](src/main/java/com/tetris/mab/balance/MabBalanceProfiles.java)
- [src/main/java/com/tetris/mab/balance/MabBalanceReport.java](src/main/java/com/tetris/mab/balance/MabBalanceReport.java)
- [src/main/java/com/tetris/mab/balance/MabBalanceReporter.java](src/main/java/com/tetris/mab/balance/MabBalanceReporter.java)

MODIFIED
- [src/main/java/com/tetris/mab/ai/MabAiState.java](src/main/java/com/tetris/mab/ai/MabAiState.java) — adds a `MabBalanceProfile balanceProfile` field (defaults to `standardPve()`), getter, and setter.
- [src/main/java/com/tetris/mab/ai/MabAiDriver.java](src/main/java/com/tetris/mab/ai/MabAiDriver.java) — new 5-arg constructor `MabAiDriver(match, aiId, archetype, difficulty, profile)`; the legacy 4-arg constructor delegates with `standardPve()`. Cooldown helpers (`launchCooldownFor`, `radarCooldownFor`, `decoyCooldownFor`, `defenseCooldownFor`, `upgradeCooldownFor`) now read the profile. Per-tick charge-budget gain calls the profile-aware overload of `MabAiPolicy.chargeBudgetGainPerSimulatedPiece(state)`.
- [src/main/java/com/tetris/mab/ai/MabAiPolicy.java](src/main/java/com/tetris/mab/ai/MabAiPolicy.java) — `chargeStep(MabAiState)` and `chargeBudgetGainPerSimulatedPiece(MabAiState)` read from the profile. Decision logic gates Civil Defense, Decoy, Upgrade, and Launch by the profile's pacing fields. New helper `launchAllowedByPvePacing(...)` enforces early-launch delay and `<80`/`<160` launch caps. The legacy `chargeBudgetGainPerSimulatedPiece(MabAiDifficulty)` is retained for compatibility and routes through `standardPve()`.
- [src/main/java/com/tetris/mab/ui/MabPveConfig.java](src/main/java/com/tetris/mab/ui/MabPveConfig.java) — adds `String balanceProfileId` (defaults to `standard-pve`), `getBalanceProfileId()`, `getBalanceProfile()`, and an extra `fromSelections(...)` overload that takes the profile id. The 6-arg constructor is preserved as a delegating overload.
- [src/main/java/com/tetris/mab/ui/MabPveSetupDialog.java](src/main/java/com/tetris/mab/ui/MabPveSetupDialog.java) — adds a "Balance profile:" `JComboBox<MabBalanceProfile>` rendered by display name, seeded with the prior selection, defaulting to Standard PvE. Selection flows through `MabPveConfig.fromSelections(..., profileId)`.
- [src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java) — new 5-arg constructor accepts a `MabBalanceProfile`; the 2- and 4-arg constructors delegate with `standardPve()`. Forwards the profile to `MabAiDriver` and calls `hudPanel.setBalanceProfileName(...)` so the player sees the active profile.
- [src/main/java/com/tetris/mab/ui/MabHudPanel.java](src/main/java/com/tetris/mab/ui/MabHudPanel.java) — adds `setBalanceProfileName(String)` and a private `refreshOpponentLabel()` helper. The header opponent line now reads `Opponent: ARCH / DIFF — Balance: <Profile>`.
- [src/main/java/com/tetris/controller/GameController.java](src/main/java/com/tetris/controller/GameController.java) — `openMabIntegrations()` passes `mabPveConfig.getBalanceProfile()` into the `MabPlayerFacingController`.
- [src/main/java/com/tetris/mab/sim/MabSimulationConfig.java](src/main/java/com/tetris/mab/sim/MabSimulationConfig.java) — record now carries `String balanceProfileId` (compact constructor defaults blank/null to `standard-pve`). Adds `balanceProfile()` accessor and `withBalanceProfileId(...)`. The previous 11-arg shape is preserved as a delegating constructor so all existing factory methods (`smoke`, `aiVsDummy`, `aiVsAi`, etc.) still compile unchanged.
- [src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java](src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java) — both AI scenarios now construct `MabAiDriver` with `cfg.balanceProfile()`.
- [src/main/java/com/tetris/mab/sim/MabSimulationRunner.java](src/main/java/com/tetris/mab/sim/MabSimulationRunner.java) — argument parser accepts `--profile <id>` anywhere in the command line; new `balance` subcommand runs ai-vs-ai for every profile in `MabBalanceProfiles.all()` at a fixed tick count (default 160) and prints a compact `MabBalanceReporter` report per profile. Existing argument forms still work.

## 2. Offline-only scope confirmation

Step 19 is a model + UI tuning pass. No networking, no client/server abstractions, no remote-player handles, no rollback netcode. The only new code is the `com.tetris.mab.balance` package (model-only), small Swing additions for HUD/setup, and a runner argument parser. Everything stays single-process, single-thread on the EDT (or main thread for sims).

## 3. Why balance profiles were added

Balance values were previously inlined into the AI driver/policy, the UI defaults, and the simulation harness. Tuning required scattered edits and made A/B comparison painful. A single `MabBalanceProfile` now centralizes the AI pacing knobs (charge gain, charge step, cooldowns) and PvE pacing gates (early-launch delay, decoy/upgrade/civil-defense minimums, soft launch caps) used by Balance Pass v1.

## 4. Balance profile fields

See [MabBalanceProfile.java](src/main/java/com/tetris/mab/balance/MabBalanceProfile.java).

| Group | Fields |
| --- | --- |
| Identity | `id`, `displayName`, `description` |
| AI charge gain per tick | per `MabAiDifficulty` (EASY/NORMAL/HARD/DEBUG) |
| AI add-charge step | per difficulty |
| Launch cooldown | per difficulty |
| Radar cooldown | per difficulty |
| Decoy cooldown | per difficulty |
| Defense cooldown | per difficulty |
| Upgrade cooldown | per difficulty |
| PvE pacing | `aiEarlyLaunchDelayTicks`, `aiMinimumTicksBeforeDecoy`, `aiMinimumTicksBeforeUpgrade`, `aiMinimumTicksBeforeCivilDefense`, `maxAiLaunchesBeforeTick80`, `maxAiLaunchesBeforeTick160` |
| Strategic safety | `preferredImpactGracePieces`, `preferredMaxImmediateGarbageRows`, `preferredCivilDefenseStartingCharges`, `preferredCivilDefenseShieldPieces` |
| Metadata | `defaultForPve` |

Validation: `id`/`displayName` non-blank; numeric fields ≥ 0; non-DEBUG cooldowns must be > 0; `maxAiLaunchesBeforeTick160 ≥ maxAiLaunchesBeforeTick80`.

## 5. Default profile values

See [MabBalanceProfiles.java](src/main/java/com/tetris/mab/balance/MabBalanceProfiles.java). Provisional Balance Pass v1 values:

`standardPve` (default for MAB PvE):
- gain/tick E1 N3 H5 D12 · step E8 N15 H24 D50
- launch cd E18 N14 H10 D2 · radar cd E18 N14 H10 D2
- decoy cd E26 N22 H16 D3 · defense cd E12 N10 H7 D2 · upgrade cd E36 N30 H22 D4
- earlyLaunchDelay 35 · minDecoy 20 · minUpgrade 40 · minCivDef 10
- max launches <80=2 · <160=5
- safety: grace 5 · maxImmediateRows 6 · civDef startCharges 1 · shieldPieces 8

`gentlePve`:
- ~30–40% slower than standard; earlyLaunchDelay 60; max launches <80=1, <160=3.

`highPressurePve`:
- faster than standard but not DEBUG; earlyLaunchDelay 20; max launches <80=3, <160=7.

`debugFast`:
- effectively no pacing gates (earlyLaunchDelay 0, mins 0, caps 20/40); near-DEBUG cooldowns.

These numbers are explicitly provisional and intended to be tuned via the simulation harness.

## 6. How profile is selected in MAB PvE setup

`MabPveSetupDialog` adds a fourth dropdown labeled **Balance profile:** populated from `MabBalanceProfiles.all()` and rendered by `displayName`. The selection is captured in `MabPveConfig.balanceProfileId` and flows through `GameController.openMabIntegrations()` → `MabPlayerFacingController(..., profile)` → `MabAiDriver`. Default is **Standard PvE**. There is no persistence (no save files).

## 7. How the AI uses balance-profile values

`MabAiState` carries a `MabBalanceProfile` reference. `MabAiDriver`:
- Per-tick simulated-charge gain comes from `MabAiPolicy.chargeBudgetGainPerSimulatedPiece(state)`, which reads `profile.chargeGainPerTickFor(difficulty)`.
- Each post-action cooldown (`launchCooldownFor()`, `radarCooldownFor()`, `decoyCooldownFor()`, `defenseCooldownFor()`, `upgradeCooldownFor()`) reads the profile.
- `MabAiPolicy.chargeStep(state)` returns `profile.addChargeStepFor(difficulty)`.

## 8. AI early-launch / decoy / upgrade / defense gates

In `MabAiPolicy.chooseDecision(...)`:
- **Civil Defense** is gated by `aiTicks >= profile.aiMinimumTicksBeforeCivilDefense` *unless* an `IMPACT_READY` is already on the board (emergency override).
- **Decoy** is gated by `aiTicks >= profile.aiMinimumTicksBeforeDecoy`.
- **Upgrade pause** is gated by `aiTicks >= profile.aiMinimumTicksBeforeUpgrade`.
- **Launch** is gated by `launchAllowedByPvePacing(state)`:
  - `aiTicks >= profile.aiEarlyLaunchDelayTicks`,
  - `aiTicks < 80` → at most `profile.maxAiLaunchesBeforeTick80` launches by this AI,
  - `aiTicks < 160` → at most `profile.maxAiLaunchesBeforeTick160` launches by this AI.

When a gate blocks a decision, the policy falls through to the next priority (RADAR / ADD_CHARGE / etc.), so the AI never sits idle. These are PvE pacing gates, not competitive rules; `debugFast` sets every gate to 0 so DEBUG behavior is preserved.

## 9. Civil defense / impact centralization status

Civil Defense, impact grace pieces, and immediate-row-cap values remain in their original classes (`CivilDefenseState`, `ImpactResolver`, etc.). Refactoring them through balance-profile values would touch multiple verified flows (Step 6 / Step 8 / Step 13) and risk regressions in interception, grace buffering, and patterned garbage. Step 19 documents the *preferred* values on the profile (`preferredImpactGracePieces`, `preferredMaxImmediateGarbageRows`, `preferredCivilDefenseStartingCharges`, `preferredCivilDefenseShieldPieces`) so that a future, scoped refactor can wire them in without reshaping the profile schema. They are therefore **observed-only** in v1.

## 10. Simulation profile support

- `MabSimulationConfig` now carries a `balanceProfileId` (default `standard-pve`). The compact constructor null/blank-defaults the id, and an 11-arg legacy constructor delegates to the new 12-arg shape so every existing factory (`smoke`, `aiVsDummy`, `aiVsAi`, `radarDecoy`, `launchImpact`, `civilDefense`, `upgradeFlow`) compiles unchanged.
- `MabSimulationConfig.withBalanceProfileId(id)` returns a copy with a different profile.
- `MabHeadlessSimulation` passes `cfg.balanceProfile()` to every `MabAiDriver` it constructs.
- `MabSimulationRunner` accepts `--profile <id>` anywhere in argv. The previous positional `[scenario] [ticks]` form still works, and an unrecognised id silently falls back to `standardPve()`.

Example:
```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-ai 160 --profile gentle-pve
```

## 11. Balance report / balance command

`MabBalanceReport` is an immutable record carrying profile id/name, ticks, all telemetry counts, both players' charge/silo, invariant failure count, notes, and the simulation summary string.

`MabBalanceReporter`:
- `fromSimulation(MabSimulationResult, MabBalanceProfile)` builds a report.
- `formatReport(MabBalanceReport)` returns a compact multi-line string.
- `formatProfile(MabBalanceProfile)` prints all knob values in a per-difficulty table.

The `balance` runner subcommand runs `ai-vs-ai 160` once per profile and prints one compact report per profile (no pass/fail; observation only).

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner balance
```

## 12. HUD profile display

`MabHudPanel` header now shows:
```
Opponent: BALANCED / NORMAL  —  Balance: Standard PvE
```
Updated whenever `setOpponentInfo(...)` or `setBalanceProfileName(...)` is called. `MabPlayerFacingController.start()` calls both.

## 13. Observed simulation counts (Balance Pass v1)

All commands run on Adoptium JDK 25.0.3.9. Profile shown is whatever default each command uses; ai-vs-ai under standard profile.

| Command | success | launches | impacts | scans | decoys | civDef | upgrades | aiExec | aiSkip | invariantFailures |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `smoke` | true | 1 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| `ai-vs-dummy 160` (standard) | true | 6 | 0 | 0 | 7 | 0 | 0 | 48 | 112 | 0 |
| `ai-vs-ai 160` (standard) | true | 6 | 6 | 10 | 7 | 6 | 1 | 76 | 244 | 0 |
| `balance` Standard PvE | — | 6 | 5 | 10 | 7 | 6 | 1 | 75 | 245 | 0 |
| `balance` Gentle PvE | — | 6 | 6 | 10 | 6 | 6 | 1 | 81 | 239 | 0 |
| `balance` High Pressure PvE | — | 6 | 5 | 8 | 4 | 6 | 1 | 53 | 267 | 0 |
| `balance` Debug Fast | — | 7 | 5 | 10 | 3 | 6 | 0 | 39 | 281 | 0 |

Observations:
- Standard profile produces visible activity (launches, scans, decoys, civDef, upgrades) without the AI flooding the player. The `<160=5`-launch cap is per-AI; the aggregate `6` includes both AIs.
- ai-vs-dummy shows the new pacing in action: 6 launches over 160 ticks (vs. previously much faster), upgrades skipped because Player A never advances strategic clock.
- All four profiles complete cleanly with `invariantFailures=0`.

## 14. What remains provisional

- Specific cooldown numbers — only one round of tuning has been applied.
- The strategic-safety values (`preferredImpactGracePieces`, etc.) are *not yet wired* into the underlying state machines (see §9).
- Civil-defense charge starting count is not driven from the profile yet.
- AI archetype-specific aggressiveness multipliers are not represented; they are still expressed via the per-archetype upgrade/decoy preferences in `MabAiDriver` / `MabAiPolicy`.

## 15. Intentionally not implemented yet

- Final competitive balance.
- Save files / persistent settings / settings menu.
- Campaign / missions / achievements.
- Online multiplayer / networking / client-server.
- Two-board local 1v1 layout.
- Major UI redesign.
- Per-profile localization.
- A debug HUD selector for switching profile mid-match.

## 16. Acceptance-criteria verification

| Criterion | Result |
| --- | --- |
| Project compiles with EXITCODE=0 | ✅ confirmed below |
| Smoke simulation `success=true` | ✅ |
| AI-vs-dummy 160 `success=true` | ✅ |
| AI-vs-AI 160 `success=true` | ✅ |
| NORMAL_TETRIS Play unchanged | ✅ — no edits to NORMAL_TETRIS path |
| MAB PvE setup includes balance profile selection | ✅ Balance dropdown in `MabPveSetupDialog` |
| MAB PvE uses selected profile for AI pacing | ✅ flows via `MabPveConfig` → `GameController` → `MabPlayerFacingController` → `MabAiDriver` |
| HUD shows active balance profile | ✅ `Opponent: … — Balance: <profile>` |
| Standard profile gates early AI launch pressure | ✅ early delay 35 + cap 2/<80 |
| EASY/NORMAL/HARD/DEBUG distinct | ✅ each profile carries a four-difficulty table |
| Debug HUD AI still works | ✅ `MabDebugController` continues to use the 4-arg `MabAiDriver` constructor (defaults to `standardPve()`) |
| Sim harness still works with old commands | ✅ `smoke`, `ai-vs-dummy 160`, `ai-vs-ai 160`, `radar-decoy`, `launch-impact`, `civil-defense`, `upgrade-flow` all still resolve |
| No networking / online code | ✅ none added |
| Step19.md documents implementation and counts | ✅ this file |

## 17. Build command and result

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
```

Result: `EXITCODE=0`.

## 18. Smoke simulation command and result

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
```

```
Profile: Standard PvE [standard-pve]
=== MAB Simulation: SMOKE ===
success=true
summary: mode=SMOKE ok=true ticks=80 events=271 launches=1 impacts=1 scans=1 decoys=1 civDef=1 upgrades=0 aiExec=0 aiSkip=0 garbage=2 invariantFailures=0
```

## 19. Additional simulation commands and results

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-dummy 160
=== MAB Simulation: AI_VS_DUMMY ===
success=true
summary: mode=AI_VS_DUMMY ok=true ticks=160 events=611 launches=6 impacts=0 scans=0 decoys=7 civDef=0 upgrades=0 aiExec=48 aiSkip=112 garbage=0 invariantFailures=0
```

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner ai-vs-ai 160
=== MAB Simulation: AI_VS_AI ===
success=true
summary: mode=AI_VS_AI ok=true ticks=160 events=1089 launches=6 impacts=6 scans=10 decoys=7 civDef=6 upgrades=1 aiExec=76 aiSkip=244 garbage=12 invariantFailures=0
```

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner balance
=== MAB Balance Comparison (ai-vs-ai, ticks=160) ===

--- Balance Report: Standard PvE [standard-pve] ---
ticks=160  launches=6  impacts=5  scans=10  decoys=7  civDef=6  upgrades=1
aiExec=75  aiSkip=245  invariantFailures=0
state: A charge=21 silo=100 | B charge=20 silo=100

--- Balance Report: Gentle PvE [gentle-pve] ---
ticks=160  launches=6  impacts=6  scans=10  decoys=6  civDef=6  upgrades=1
aiExec=81  aiSkip=239  invariantFailures=0
state: A charge=9 silo=100 | B charge=9 silo=100

--- Balance Report: High Pressure PvE [high-pressure-pve] ---
ticks=160  launches=6  impacts=5  scans=8  decoys=4  civDef=6  upgrades=1
aiExec=53  aiSkip=267  invariantFailures=0
state: A charge=20 silo=100 | B charge=21 silo=100

--- Balance Report: Debug Fast [debug-fast] ---
ticks=160  launches=7  impacts=5  scans=10  decoys=3  civDef=6  upgrades=0
aiExec=39  aiSkip=281  invariantFailures=0
state: A charge=18 silo=100 | B charge=0 silo=100
```

All simulations: `success=true`, `invariantFailures=0`.
