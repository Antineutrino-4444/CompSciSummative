# Step 21 — Simplification Pivot for Mutually Assured Blocks

> **Scope:** Mutually Assured Blocks (MAB) PvE / local 1v1 only.
> **NORMAL\_TETRIS gameplay is unchanged.** **Offline-only:** no
> networking, no sockets, no online prep.

The previous action-code system (multi-clear command sequences such as
"Radar 2,1,2") made MAB feel like a memorisation puzzle layered on top
of a falling-block game. Step 21 replaces that with a small, skill-based
trigger model the player learns by playing rather than by reading a
table.

---

## 1. Files added

| File | Purpose |
|---|---|
| `src/main/java/com/tetris/mab/pieces/MabSharedPieceSequence.java` | Seeded shared 7-bag generator. |
| `src/main/java/com/tetris/mab/pieces/MabPieceStream.java` | Per-player `BagRandomizer` view onto the shared sequence. |
| `src/main/java/com/tetris/mab/clear/MabClearResult.java` | Snapshot of a single line-clear (lines, spin, PC, B2B, combo, charge gained, display text). |
| `src/main/java/com/tetris/mab/clear/MabChargeCalculator.java` | Charge formula and human-readable label builder. |
| `src/main/java/com/tetris/mab/clear/MabSimplifiedStrategicState.java` | Per-participant simplified strategic state (charge, ready flag, Tetris/spin progress, last clear, last trigger). |
| `src/main/java/com/tetris/mab/sim/MabSharedPieceSequenceProbe.java` | Verifies the shared sequence is deterministic and that hold cannot desync the two players. |
| `src/main/java/com/tetris/mab/sim/MabChargeCalculatorProbe.java` | Verifies all charge-formula outputs (single, double, tetris, B2B tetris, spin single/double/triple, PC, B2B spin double). |
| `src/main/java/com/tetris/mab/sim/MabSimplifiedCoreProbe.java` | End-to-end probe: charge gain, ready transition, Tetris-route launch, spin-route launch, intercept priority, reset on launch. |
| `Step21.md` | This file. |

## 2. Files modified

- `src/main/java/com/tetris/model/GameState.java` — `bag` is no longer
  `final`; new method `replacePieceSourceForMabSharedSequence(...)`
  swaps in the shared bag and resets hold/piece counters. Documented
  as offline-only MAB-only.
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java`
  - New factories `createLocalPvpShared(...)` and `createPveShared(...)`
    that build a `MabSharedPieceSequence` from a seed and inject a
    `MabPieceStream` into both players' `GameState`s.
  - `onLinesCleared` listener body now routes through
    `processSimplifiedClearForLineClear(...)` and **skips** the
    action-code charge / launch path. DEFCON escalation and upgrade
    awards are preserved.
  - New helpers: `processSimplifiedClearForLineClear`,
    `hasActiveIncomingThreat`, `triggerSimplifiedSpinIntercept`,
    `fireSimplifiedLaunch`, `debugSimplifiedFeedClear`,
    `debugInjectIncomingThreatForTesting`.
  - The legacy action-code dispatch path (`processActionForLineClear`,
    `dispatchCompletedAction`, intercept-from-action) is **bypassed**,
    not deleted, so legacy probes / debug entry points still run.
- `src/main/java/com/tetris/mab/ParticipantState.java` — added
  `simplifiedState` field and `getSimplifiedState()` accessor.
- `src/main/java/com/tetris/controller/GameController.java` —
  `openMabIntegrations()` now uses `createLocalPvpShared(...)` so the
  live PvE match plays the shared piece sequence.
- `src/main/java/com/tetris/mab/ui/MabCompactHudPanel.java` — the old
  "Action Code" card is now the **Strategic (skill)** card and reads
  from `ParticipantState.getSimplifiedState()`. Shows charge `cur/req`
  with a `READY` flag, Tetris route progress `n/4`, spin route progress
  `n/2`, status (`Build charge` / `Fire: 4 Tetrises OR 2 Spins` /
  `INCOMING — SPIN TO INTERCEPT`), and the last clear's label and
  charge gain. The confirm row doubles as the in-card incoming-threat
  banner.
- `src/main/java/com/tetris/mab/ui/MabActionFeedback.java` — added
  mappings for `MAB_CLEAR_CHARGE_GAINED`, `MAB_NUKE_READY`,
  `MAB_LAUNCH_PROGRESS_TETRIS`, `MAB_LAUNCH_PROGRESS_SPIN`,
  `MAB_LAUNCH_FIRED_SIMPLIFIED`, `MAB_SPIN_INTERCEPT_TRIGGERED`,
  `MAB_INTERCEPT_RESOLVED_SIMPLIFIED`.

## 3. Offline scope

No network code, sockets, or online services were added or referenced.
The shared piece sequence is constructed from a local seed
(`System.nanoTime() ^ 0xC0FFEE`) inside `GameController.openMabIntegrations()`.

## 4. UI compliance (1366×768)

- The new Strategic card uses six existing `kvRow` slots; no new card
  was added, so the existing seven-card grid still fits without
  horizontal scroll on a 1280-wide panel.
- Charge, Tetris/Spin route progress, the `INCOMING — SPIN TO INTERCEPT`
  banner, and the last-clear read-out are all rendered inside the
  always-visible compact HUD with no scroll.
- The Recent Events strip remains the only scrollable region.

## 5. Shared piece sequence

`MabSharedPieceSequence` lazily appends shuffled 7-bags to a single
`List<TetrominoType>` using a seeded `Random`. Each player receives a
`MabPieceStream extends BagRandomizer` that holds a per-player cursor
and overrides `next()` and `peek(int)` to read from the shared list.
This guarantees:

- **Player A's piece #n == Player B's piece #n by index**, regardless of
  wall-clock timing.
- **Hold cannot desync**, because `GameState.holdPiece` re-uses the
  held slot and never calls `bag.next()` to fulfil a hold action.
- **Same seed → identical sequence**, used for reproducible probes.

`MabSharedPieceSequenceProbe` (5/5 checks): `first50Equal`,
`independentCursors`, `sameSeedReproducible`, `differentSeedDifferent`,
`holdDoesNotMutateSequence` — all pass.

## 6. Simplified clear model

Each line clear produces a `MabClearResult` capturing lines cleared,
spin kind, perfect clear, back-to-back, combo count, tetris flag,
charge gained, and a human-readable label.

## 7. Charge formula

Base by line count (`0/1/3/5/8`, plus `+2` per line beyond 4).
Spin bonus by line count (`4/8/14/20/24`). Combo bonus by tier
(`0/1/2/4`). Perfect clear `+12`. Back-to-back (tetris or any spin)
multiplies the base by `1.25`. Display labels include `B2B`, `Spin`,
`Perfect`, `Single/Double/Triple/Tetris`, `xN` combo, and `+N` charge.

`MabChargeCalculatorProbe` (9/9): `single=1`, `double=3`, `tetris=8`,
`b2bTetris=10`, `spinSingle=9`, `spinDouble=17`, `spinTriple=25`,
`pcTetris=20`, `b2bSpinDouble=21`.

## 8. Launch rule

Once the nuke charge reaches the required total (default 100) the nuke
is auto-armed and `nukeReady` flips to `true`. After that:

- **4 Tetrises** in any order → fire a launch on the Tetris route.
- **2 spins** in any order → fire a launch on the spin route.

Either route resets charge and both progress counters to zero.

## 9. Intercept rule

If the player has any incoming threat in `WARNING_ACTIVE` and clears
**any spin**, an intercept is fired immediately. The intercept type is
chosen from spin strength: PC or 3-line → `FULL`, 2-line → `STANDARD`,
otherwise `EMERGENCY`. The spin **does not** count toward the spin
launch route, and **no charge is added** for that clear. Intercept has
priority over launch progress.

## 10. Action-code bypass

The action-code subsystem (manager, registry, dispatcher) compiles and
runs but is no longer reachable from the listener path in normal MAB.
It remains accessible to:

- `MabActionCodeInputProbe` (which calls `debugFeedLineClear` directly).
- AI strategic launch / intercept calls that still go through
  `startActionAttempt(...)`. These ultimately call
  `authorizeLaunchInternal(...)` so the launch pipeline is preserved.

## 11. AI behaviour

`MabAiDriver` was not modified in Step 21. Strategic AI launches still
fire via the action-code path internally, which is acceptable because
the underlying launch pipeline (`authorizeLaunchInternal`) is shared
with the simplified router. **Limitation:** the AI does not currently
honour the simplified Tetris/spin route gating; this is intentional
for parity with prior behaviour and is documented as a follow-up.

## 12. HUD changes

`MabCompactHudPanel` re-uses the existing six-slot card to surface
simplified state instead of action-code state. The card title is now
**Strategic (skill)**. Field mapping:

| Slot | Old (action code) | New (Step 21 simplified) |
|---|---|---|
| Last clear | last 4-line | last clear seen (unchanged) |
| Command | active command name | `cur/req READY?` charge gauge |
| Entered | tokens entered | `Build charge` / `Fire: …` / `INCOMING — SPIN TO INTERCEPT` |
| Progress | `m/n` | Tetris route `n/4` |
| Next | next required clear | Spin route `n/2` |
| Spin OK? | spin-allowed flag | last clear label |

The threat banner row (`actionConfirm`) reads
**INCOMING — SPIN TO INTERCEPT** whenever the player has an incoming
threat.

## 13. Event / banner changes

`MabActionFeedback.mapEvent(...)` now routes the seven new
`MAB_*` event types to the same banner colour palette:

| Event | Severity | Banner |
|---|---|---|
| `MAB_CLEAR_CHARGE_GAINED` | INFO | label + `(+N charge)` |
| `MAB_NUKE_READY` | SUCCESS | "NUKE READY — fire 4 Tetrises OR 2 spins" |
| `MAB_LAUNCH_PROGRESS_TETRIS` | INFO | "Launch route: Tetrises n/4" |
| `MAB_LAUNCH_PROGRESS_SPIN` | INFO | "Launch route: Spins n/2" |
| `MAB_LAUNCH_FIRED_SIMPLIFIED` | SUCCESS | "LAUNCH FIRED — 4 Tetrises" / "2 Spins" |
| `MAB_SPIN_INTERCEPT_TRIGGERED` | WARNING | "Spin intercept (FULL/STANDARD/EMERGENCY)" |
| `MAB_INTERCEPT_RESOLVED_SIMPLIFIED` | SUCCESS | "Intercept FULLY/PARTIALLY_INTERCEPTED" |

## 14. Probe / sim results

| Probe / Sim | Result |
|---|---|
| `MabSharedPieceSequenceProbe` | success=true (5/5) |
| `MabChargeCalculatorProbe` | success=true (9/9) |
| `MabSimplifiedCoreProbe` | success=true (8/8) |
| `MabSimulationRunner smoke` | success=true, invariantFailures=0 |
| `MabSimulationRunner ai-vs-dummy 160` | success=true, invariantFailures=0 |
| `MabSimulationRunner ai-vs-ai 160` | success=true, invariantFailures=0 |
| `MabSimulationRunner balance` | invariantFailures=0 |

## 15. Manual UI checklist (1366×768)

- [x] Strategic card visible without scrolling.
- [x] Charge gauge visible with both `cur/req` and `READY` flag.
- [x] Tetris/Spin route counters visible.
- [x] `INCOMING — SPIN TO INTERCEPT` banner appears in Strategic card
      and Threats card (suggested response) whenever a threat is active.
- [x] Last clear label visible and updates per clear.
- [x] Open Upgrades / Close Pause / Command Guide / Full HUD buttons
      remain visible without scrolling.

## 16. Known limitations / follow-ups

- The AI still uses the action-code launch path internally; it does
  not yet honour the 4-Tetris / 2-spin gate for player-perceived
  parity. Sims run unmodified; AI launches still resolve through the
  shared launch pipeline.
- The legacy action-code UI affordances (Command Guide popup) remain
  reachable via the **Command Guide** button and may be hidden in a
  follow-up step.
- `lineClearChargeFor(...)` in `MutuallyAssuredBlocksMatch` is now
  unused in normal flow but preserved for legacy probes.
