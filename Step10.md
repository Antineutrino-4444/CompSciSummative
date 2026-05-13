# Step 10 — Mutually Assured Blocks: Radar, Intel Scans, Stale Information, and Warning-System Upgrades

> **Permanent rule:** *Mutually Assured Blocks is an offline-only game.* It will never ship with networking, online multiplayer, or any kind of remote sync. All "intel" in this step is local model-vs-model state.

## 1. Files added

| Path | Purpose |
| --- | --- |
| `src/main/java/com/tetris/mab/intel/IntelLevel.java` | Enum ranking from `NONE` → `FULL_READOUT` (rank 0..6). |
| `src/main/java/com/tetris/mab/intel/RadarScanType.java` | `BASIC / UPGRADED / CRISIS / FULL_SPECTRUM / DEBUG`. |
| `src/main/java/com/tetris/mab/intel/RadarScanResult.java` | Immutable record returned by every scan, with `success(...)` / `failed(...)` factories. |
| `src/main/java/com/tetris/mab/intel/StaleIntelSnapshot.java` | Mutable wrapper around the latest scan result; tracks staleness score, supports manual `markStale(reason)` and per-piece ticks. |
| `src/main/java/com/tetris/mab/intel/RadarScanCalculator.java` | Pure function: `(scanner, target, defcon, sequence, type) → RadarScanResult` applying upgrade and DEFCON modifiers. |
| `Step10.md` | This document. |

## 2. Files modified

| Path | Change |
| --- | --- |
| `src/main/java/com/tetris/mab/RadarIntelState.java` | Replaced placeholder with full per-participant intel state (snapshot, counters, best-rank tracking, debug string). |
| `src/main/java/com/tetris/mab/ParticipantState.java` | Added `getRadarIntelState()` alias and appended radar to `toDebugString()`. |
| `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java` | Added `RadarScanCalculator` + `radarScanSequence`; piece-locked staleness ticks; `RADAR_SCAN` action dispatch; public `performRadarScan(...)`, `getLastRadarScan(...)`, `getEnemyIntelSnapshot(...)`; `markOpponentIntelStale(...)`; staleness emissions on launch authorization, nuke redesign, impact resolution. |
| `src/main/java/com/tetris/mab/MatchDebugSnapshot.java` | `ParticipantSummary` extended with 9 radar fields and a `summarizeRadar(...)` helper. |
| `Step2.md` … `Step9.md` | Replaced "online multiplayer / networking" non-implementation lines with the offline-only positioning paragraph. |

## 3. Offline-only doctrine cleanup

Every prior Step doc that mentioned "online multiplayer / networking" was rewritten to make the offline-only stance explicit. There is no longer any wording suggesting remote play, sync, or future server architecture is planned. README still references *visual* particle network animation (UI only) — unrelated to networking.

## 4. The radar concept

A **radar scan** is a one-shot read by one participant against the other. It produces a truthful — but possibly partial — snapshot of the target's nuke build and silo posture, plus counts of in-flight launches/threats. Scans are stored in the scanner's `RadarIntelState` and become "stale" over time (per-piece tick) or instantly (because the target launched, redesigned, or took an impact).

There are **no decoys, no false doctrine signals, no camouflage deception, no masked launches, no AI decisions, and no full radar UI panels in this step.** All of those remain explicitly out of scope.

## 5. `IntelLevel`

```
NONE              rank 0
SIZE_ESTIMATE     rank 1   size category only
DOCTRINE_ESTIMATE rank 2   + doctrine + design name/id
PROGRESS_ESTIMATE rank 3   + charge/required (rounded), armed estimate, silo damage state
LAUNCH_WARNING    rank 4   + launch & threat counts, first-ones with rounded timers
THREAT_DETAIL     rank 5   reserved (currently identical observable set to LAUNCH_WARNING)
FULL_READOUT      rank 6   exact integers, silo integrity, no rounding
```

`IntelLevel.atLeast(...)` is used everywhere instead of `>=` to keep call-sites readable.

## 6. `RadarScanResult` fields

The record carries all 28 spec fields including: success flag, scanner/target ids, scan type & intel level, sequence number and "pieces locked at scan" stamps for both sides, DEFCON at scan, target nuke design id/name/doctrine/size, build charge & required (estimates), armed estimate, silo damage state & integrity estimate, active-launch & incoming-threat count estimates, first-active-launch id/phase/countdown estimate, first-incoming-threat id/status/warning estimate, confidence percent (clamped 0..100), `staleImmediately`, and a free-text `message`. Static factories `success(...)` (clamps confidence, sets `staleImmediately=false`) and `failed(...)` (`IntelLevel.NONE`, all strings null, ints −1, `staleImmediately=true`).

## 7. `StaleIntelSnapshot` behaviour

- `STALE_SCORE_THRESHOLD = 4`.
- `tickScannerPiece()` adds 1 every 3 scanner pieces.
- `tickTargetPiece()` adds 1 each.
- Crossing the threshold flips `stale = true`.
- `markStale(reason)` forces `stale = true` and adds:
  - +3 if reason contains `redesign` or `launch`,
  - +2 if reason contains `hit`, `disarm`, or `silo`,
  - otherwise enough to cross the threshold.
- `toDebugString()` emits `Intel{level=… conf=… stale=… score=… reason=…}`.

## 8. `RadarScanCalculator` rules

Base intel from DEFCON (truthful only):
- DEFCON 5/4 → `SIZE_ESTIMATE`.
- DEFCON 3/2/1 → `DOCTRINE_ESTIMATE`.

Upgrade modifiers (on the scanner):
- `EARLY_WARNING_RADAR`: +1 rank per level, capped at `FULL_READOUT`.
- `SIGNAL_ANALYSIS` ≥ 1: doctrine visibility +1 rank. ≥ 2: +10 confidence.
- `THREAT_TRACKING` ≥ 1: ensures at least `LAUNCH_WARNING`. ≥ 2: more accurate timer estimates (currently same rounding window; reserved for refinement).

DEFCON 2 or 1 grants an extra +1 rank (crisis activity is harder to hide).

`DEBUG` short-circuits to `FULL_READOUT` confidence 100.

Confidence formula (clamped 10..100):

```
60 + 10·radar + 10·signal + (10·tracking IF launch/threat present)
   + (10 IF signal ≥ 2) − 5·targetCamouflageLevel
```

Estimate rounding:
- `chargeEstimate` and `requiredEstimate`: nearest 5 at PROGRESS_ESTIMATE; exact at FULL_READOUT.
- `armedEstimate`: at PROGRESS_ESTIMATE, true if confidence ≥ 60; otherwise only if fully armed.
- Timer estimates (launch countdown, threat warning): rounded **up** to nearest 2 at LAUNCH_WARNING; exact at FULL_READOUT.
- Silo integrity is hidden until FULL_READOUT.

## 9. Upgrade effects summary

| Upgrade | Effect on scans about an opponent | Effect on opponent scanning us |
| --- | --- | --- |
| `EARLY_WARNING_RADAR` | +1 rank per level (cap FULL_READOUT) | — |
| `SIGNAL_ANALYSIS` | l1 = +1 rank (doctrine); l2 = +10 confidence | — |
| `THREAT_TRACKING` | l1 = at least LAUNCH_WARNING; l2 confidence boost when launches/threats present | — |
| `SILO_CAMOUFLAGE` (target) | — | −5 confidence per level on enemy scans of us |

## 10. `RADAR_SCAN` action-code integration

The existing `ActionType.RADAR_SCAN` is now functional: in `dispatchCompletedAction` a `RADAR_SCAN` definition triggers `performRadarScan(participant, RadarScanType.BASIC, "action:" + def.getId())` **before** the intercept-registry lookup, so it never falls through to a legacy intercept path.

## 11. Public APIs on `MutuallyAssuredBlocksMatch`

```java
RadarScanResult performRadarScan(ParticipantId scannerId);
RadarScanResult performRadarScan(ParticipantId scannerId, RadarScanType type);
RadarScanResult getLastRadarScan(ParticipantId scannerId);
StaleIntelSnapshot getEnemyIntelSnapshot(ParticipantId scannerId);
```

Both `performRadarScan` overloads:
- Validate scanner / opponent / phase via `isGameplayMutationAllowed()`.
- Increment `radarScanSequence` (private long) and stamp it on the result.
- Emit `RADAR_SCAN_STARTED`, `RADAR_SCAN_COMPLETED`, `RADAR_INTEL_UPDATED`.
- On rejection emit `RADAR_SCAN_REJECTED` and return `RadarScanResult.failed(...)`.

## 12. Staleness rules

`RadarIntelState.tickScannerPiece()` runs for the participant who just locked a piece; `tickTargetPiece()` runs for the **opponent** of the piece-locker. The match coordinator wires both ticks inside `onPieceLocked` immediately after `incrementPiecesLocked()`.

Hard-stale events (with `markOpponentIntelStale(...)` emitting `RADAR_INTEL_MARKED_STALE`):
- `LAUNCH_AUTHORIZED` → reason `launch:<launchId>`.
- `NUKE_REDESIGNED` (during upgrade pause) → reason `redesign:<newDesignId>`.
- `IMPACT_RESOLVED` with damage/disarm/garbage > 0 → reason `impact-hit:<launchId>`.

## 13. Debug snapshot changes

`ParticipantSummary` now ends with the 9 fields:
`totalRadarScans, successfulRadarScans, failedRadarScans, bestIntelRankAchieved, lastIntelLevel, lastIntelConfidence, intelStale, intelStalenessScore, lastScanSummary`.

`ParticipantState.toDebugString()` now appends `radarIntel.toDebugString()` → `Radar{scans=… ok=… fail=… best=… Intel{level=… conf=… stale=… score=… reason=…}}`.

## 14. Explicit non-goals (this step)

- **No decoys.** Scans always reflect the true model state at scan time.
- **No false-doctrine signals.** `targetDoctrineType` is never lied to.
- **No camouflage deception.** `SILO_CAMOUFLAGE` only reduces enemy *confidence*; it never produces false data.
- **No masked launches.** `LAUNCH_AUTHORIZED` always marks the opponent's stored intel stale.
- **No AI decisions.** No automatic scanning, no enemy bot.
- **No full radar UI panel.** Output is via match events, debug snapshot, and debug strings only.

## 15. Truthful but stale

A scan is correct when produced. It can become wrong over time (because the world moved on) without itself being deceptive. `StaleIntelSnapshot` tracks that exact distinction — last good answer + how trustworthy it still is.

## 16. Offline-only — forever

There is no networking layer planned. There is no scan packet format. There is no remote authority. This is the only kind of intel the game will ever model.

## 17. Acceptance criteria

- [x] All 4 new intel package types compile.
- [x] `RadarScanCalculator.scan(...)` produces an `IntelLevel` and confidence reflecting upgrades and DEFCON.
- [x] `MutuallyAssuredBlocksMatch.performRadarScan(...)` produces `RADAR_SCAN_STARTED` → `RADAR_SCAN_COMPLETED` → `RADAR_INTEL_UPDATED` events and stores the result on the scanner.
- [x] `RADAR_SCAN` action-type entries dispatch through the new path.
- [x] Per-piece staleness ticks fire on every piece lock.
- [x] Hard-stale events fire on launch authorization, nuke redesign, and damaging impacts.
- [x] `MatchDebugSnapshot.ParticipantSummary` carries the 9 new radar fields.
- [x] `ParticipantState.toDebugString()` includes the radar segment.
- [x] All Step*.md docs reflect the offline-only stance.
- [x] Project builds clean.

## 18. Build verification

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

Result: **`EXITCODE=0`**.
