# Step 11 — Mutually Assured Blocks: Decoys, False Intel, Masked Launches, and Camouflage Effects

This step turns the five decoy `ActionType` codes (`DECOY_LAUNCH`, `GHOST_MIRV`,
`FALSE_DOCTRINE_SIGNAL`, `DUMMY_SILO_HEAT`, `MASKED_LAUNCH`) — previously inert
opcodes — into real, time-limited strategic effects that distort enemy radar
without ever causing damage. Everything is offline-only and fully deterministic;
no RNG, no UI panels, no AI, no networking, no second-strike doctrine, and no
treaty bonuses were added.

---

## 1. Files added

```
src/main/java/com/tetris/mab/decoy/DecoyType.java
src/main/java/com/tetris/mab/decoy/DecoyVisibility.java
src/main/java/com/tetris/mab/decoy/DecoyDefinition.java
src/main/java/com/tetris/mab/decoy/DecoyRegistry.java
src/main/java/com/tetris/mab/decoy/ActiveDecoyState.java
src/main/java/com/tetris/mab/decoy/DecoyResolutionResult.java
src/main/java/com/tetris/mab/decoy/DecoyResolver.java
Step11.md
```

## 2. Files modified

```
src/main/java/com/tetris/mab/ParticipantState.java
src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java
src/main/java/com/tetris/mab/MatchDebugSnapshot.java
src/main/java/com/tetris/mab/intel/RadarScanCalculator.java   (rewritten)
```

## 3. Offline-only confirmation

Decoy state lives entirely on the local `ParticipantState` and is mutated only
by the same single-process `MutuallyAssuredBlocksMatch` loop that already drives
nuke building, civil defence, and radar in offline mode. No networking,
serialisation, packet protocol, or external service was added.

## 4. Concept

A **decoy** is a temporary strategic asset attached to a participant. While
active it lies to *enemy* radar scans about that participant in one of four
ways:

1. inflates the *count* of incoming/outgoing launch tracks (`createsFalseLaunchSignature`);
2. inflates the *count* of incoming threats against the scanner
   (`createsFalseThreatSignature`);
3. spoofs the reported nuke **doctrine** (`falsifiesDoctrine`);
4. fakes silo activity so build progress reads higher than reality
   (`falsifiesBuildProgress`); or
5. **masks a real launch** — the launch still flies and still does damage on
   impact, but the masking decoy hides it from radar count and "first launch"
   readouts (`masksRealLaunch` + `linkedLaunchId`).

Decoys decay deterministically by piece count and never inflict damage.

## 5. `DecoyType` and `DecoyVisibility`

```
DecoyType { DECOY_LAUNCH, GHOST_MIRV, FALSE_DOCTRINE_SIGNAL,
            DUMMY_SILO_HEAT, MASKED_LAUNCH }

DecoyVisibility { HIDDEN, SUSPECTED, VISIBLE_AS_REAL,
                  IDENTIFIED_AS_DECOY, EXPIRED }
```

Initial visibility on activation:

| Decoy                   | Initial visibility |
|-------------------------|--------------------|
| `MASKED_LAUNCH`         | `HIDDEN`           |
| `DECOY_LAUNCH`/`GHOST_MIRV` (false launch flag) | `VISIBLE_AS_REAL` |
| `FALSE_DOCTRINE_SIGNAL` (no false sigs)         | `SUSPECTED`       |
| `DUMMY_SILO_HEAT` (no false sigs)               | `SUSPECTED`       |

## 6. `DecoyDefinition` defaults

| Type                      | id                      | duration (pieces) | falseLaunch | falseThreat | doctrine | siloHeat | mask | confPenalty |
|---------------------------|-------------------------|-------------------|-------------|-------------|----------|----------|------|-------------|
| `DECOY_LAUNCH`            | `decoy_launch`          | 12                | 1           | 1           | false    | false    | no   | 10          |
| `GHOST_MIRV`              | `ghost_mirv`            | 16                | 3           | 3           | false    | false    | no   | 15          |
| `FALSE_DOCTRINE_SIGNAL`   | `false_doctrine_signal` | 14                | 0           | 0           | true     | false    | no   | 10          |
| `DUMMY_SILO_HEAT`         | `dummy_silo_heat`       | 14                | 0           | 0           | false    | true     | no   | 10          |
| `MASKED_LAUNCH`           | `masked_launch`         | 10                | 0           | 0           | false    | false    | yes  | 20          |

Defaults are validated in `DecoyDefinition`'s compact constructor.

## 7. `ActiveDecoyState` lifecycle

* Constructed via `DecoyResolver` with `(definition, ownerId, targetId,
  decoyId, linkedLaunchId, initialVisibility)`.
* `tickPiece()` decrements `remainingPieces`; at zero the decoy auto-expires
  and visibility flips to `EXPIRED`.
* `markIdentified()` is a no-op once expired.
* `isActive() == !expired && remainingPieces > 0`.
* `toDebugString()` →
  `Decoy{<id> type=<T> owner=<id> remaining=R/T vis=<V> linked=<L> [EXPIRED]}`.

## 8. Decoy action-code integration

In `MutuallyAssuredBlocksMatch.dispatchCompletedAction`:

* `MASKED_LAUNCH` is checked **first**: it runs the existing launch
  authorisation pipeline (now refactored to return the `launchId`) and, on
  success, activates a `MASKED_LAUNCH` decoy linked to that launch id.
* The remaining four decoy codes (`DECOY_LAUNCH`, `GHOST_MIRV`,
  `FALSE_DOCTRINE_SIGNAL`, `DUMMY_SILO_HEAT`) bypass the intercept registry by
  catching `type.isDecoy()` after the radar/civil-defence branches and
  activating directly.

Non-launch decoys do **not** require an armed warhead; only `MASKED_LAUNCH`
inherits the armed/payload checks that live inside
`authorizeLaunchFromAction`.

## 9. Masked launch behaviour

A `MASKED_LAUNCH` produces two side effects: a normal real launch (full
damage), plus a linked decoy that:

* hides the linked launch from radar launch counts (count is decremented by
  the number of currently-flying linked launches);
* prevents the linked launch from being chosen as the "first visible launch"
  in radar readouts;
* expires after 10 pieces, restoring full visibility.

A `FULL_READOUT` scan with confidence ≥ `PIERCE_CONFIDENCE` (85) ignores the
mask and reports the launch normally with the message
`masking pierced`.

## 10. Radar integration & false-intel rules

`RadarScanCalculator` now reads the target's active decoys before producing
its `RadarScanResult`:

* Confidence is reduced by the sum of every active decoy's
  `confidencePenalty` (clamped to the existing 10–100 range).
* If the requested level reaches `DOCTRINE_ESTIMATE` and a `falsifiesDoctrine`
  decoy is active and the scan is **not** piercing, the reported doctrine is
  swapped via the spoofing table (§ 11) and the message
  `doctrine spoofed by decoy` is appended.
* If the level reaches `PROGRESS_ESTIMATE` and a `falsifiesBuildProgress`
  decoy is active and the scan is not piercing, displayed `currentBuildCharge`
  is inflated by `max(10, round(realRequired * 0.25))`, clamped to
  `realRequired`, and rounded to the nearest 5; message
  `build progress inflated by dummy silo heat`.
* If the level reaches `LAUNCH_WARNING`, displayed launch count becomes
  `real + falseLaunches − maskedHidden` and threat count becomes
  `real + falseThreats`. Counts are clamped at zero.
* Pierced (`FULL_READOUT` and confidence ≥ 85) scans drop all false signatures
  and restore masked launches with messages `decoy signatures identified`
  and/or `masking pierced`. Non-pierced scans append
  `false signatures present` / `masked launch suppressed from count`
  as appropriate.
* `pickVisibleFirstLaunch` skips masked launches unless the scan pierces.

## 11. Doctrine spoof table

| Real doctrine        | Reported when spoofed |
|----------------------|-----------------------|
| `CLEAN_FUSION`       | `DIRTY_BOMB`          |
| `DIRTY_BOMB`         | `CLEAN_FUSION`        |
| `SALTED_WARHEAD`     | `CLEAN_FUSION`        |
| `CONCRETE_BLASTER`   | `MIRV`                |
| `MIRV`               | `CONCRETE_BLASTER`    |
| `BUNKER_BUSTER`      | `CONCRETE_BLASTER`    |
| `EMP_PAYLOAD`        | `DECOY_PACKAGE`       |
| `DECOY_PACKAGE`      | `EMP_PAYLOAD`         |
| `DOOMSDAY`           | `MIRV`                |
| `PLACEHOLDER` / null | `DIRTY_BOMB`          |

## 12. `DUMMY_SILO_HEAT` behaviour

While active and not pierced: `displayedCharge = min(realRequired,
realCharge + max(10, round(realRequired * 0.25)))`, then rounded to the
nearest 5 for display. The real `NukeBuildState` is **never** mutated.

## 13. `GHOST_MIRV` and `DECOY_LAUNCH` behaviour

Both raise the count of "things the enemy radar sees" without any flying
projectile: `DECOY_LAUNCH` adds 1 launch-like and 1 threat-like false
signature, while `GHOST_MIRV` adds 3 launch-like and 3 threat-like false
signatures. Both effects are dropped from displayed counts on a piercing
scan.

## 14. Silo camouflage interaction

The pre-existing `SiloState.camouflageLevel` continues to subtract 5
confidence per level inside `RadarScanCalculator`. Decoy penalties apply
**after** camouflage and stack additively, and the same combined confidence
gate decides whether the scan pierces.

## 15. Staleness updates

When a decoy is activated (auto via action, or manual via API) the owner's
`markOpponentIntelStale` is called with reason `decoy:<id>`. When a decoy
expires during `tickAndExpireDecoys`, staleness is bumped again with reason
`decoy-expired:<id>`. This keeps each opponent's saved scan summaries marked
"stale" as soon as the picture they showed becomes a lie or stops being one.

## 16. Public decoy APIs

```java
List<ActiveDecoyState> getActiveDecoys(ParticipantId id);
DecoyResolutionResult  activateDecoyManually(ParticipantId owner, DecoyType type);
```

`activateDecoyManually` rejects `MASKED_LAUNCH` (must come from a real launch
action) and rejects activation outside the active phase, logging
`DECOY_MANUAL_ACTIVATION_REJECTED` with the reason.

## 17. Debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` gained seven new fields populated in
`of(...)`:

```
int    activeDecoyCount;
int    falseLaunchSignatureCount;
int    falseThreatSignatureCount;
int    activeDecoyConfidencePenalty;
String firstActiveDecoyId;
DecoyType firstActiveDecoyType;
int    maskedLaunchDecoyCount;
```

## 18. Determinism

Every effect described above is a pure function of decoy definitions,
participant state, and the deterministic piece-locked tick. There are no
calls into `Random`, `ThreadLocalRandom`, or system clocks anywhere in the
new package or in the modified radar calculator.

## 19. Information-not-damage

Decoys never call `applyDamage`, never construct `IncomingThreatState`,
never schedule `ImpactWave`, and never modify silo integrity, board state,
build charge, or any score. They affect only what radar **reports**, and
they self-expire.

## 20. Intentionally not implemented

The following were **explicitly excluded** by the spec and remain so:

* AI behaviour for choosing when to deploy decoys.
* Full UI panels for visualising decoys (debug snapshot fields exist; no Swing
  components were touched).
* Second-strike / dead-hand doctrines.
* Treaty / restraint bonuses.
* Online multiplayer or any networking.
* `DECOY_IDENTIFIED` mutation of `ActiveDecoyState` from a piercing scan:
  identification is communicated through scan messages only
  (`decoy signatures identified`, `masking pierced`,
  `false doctrine signal pierced`, `dummy silo heat filtered`); the spec
  permits either approach and this avoids cross-participant mutation from a
  read query.

`RADAR_DECOY_EFFECT_APPLIED` **is** emitted (see § 23) whenever a successful
radar scan is run against a target that has at least one active decoy.

## 21. Acceptance criteria

* Build compiles with `EXITCODE=0` (172 classes, see § 22).
* Five decoy `ActionType` codes now have observable effects on radar output.
* Effects are time-limited and self-expire.
* No code path damages any participant from a decoy.
* Opponent intel goes stale when decoys appear or disappear.
* Public APIs `getActiveDecoys` and `activateDecoyManually` available on
  `MutuallyAssuredBlocksMatch`.
* Debug snapshot exposes seven new decoy fields per participant.
* Spoof table, penalties, and pierce threshold (`PIERCE_CONFIDENCE = 85`)
  match the spec exactly.

## 22. Build verification

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

```
EXITCODE=0
```

172 `.class` files were produced under `target\classes`.

---

## 23. Step 11 refinement (post-review)

A review of the original Step 11 implementation found two deviations from the
spec; both have been corrected:

1. **`DecoyDefinition` defaults aligned to the spec.** `DECOY_LAUNCH` now
   creates **1 false launch signature and 1 false threat signature**, and
   `GHOST_MIRV` now creates **3 false launch signatures and 3 false threat
   signatures** (duration 16 pieces). IDs use the underscore form
   (`decoy_launch`, `ghost_mirv`, etc.) as specified. The defaults table in
   § 6 reflects the corrected values.

2. **`RADAR_DECOY_EFFECT_APPLIED` log added.** In
   `MutuallyAssuredBlocksMatch.performRadarScan`, immediately after
   `RADAR_INTEL_UPDATED`, the match emits `RADAR_DECOY_EFFECT_APPLIED` when
   the scan succeeded and the target has at least one active decoy. Metadata
   includes `scanner`, `target`, `scanSequenceNumber`, `scanType`,
   `intelLevel`, `confidence`, `activeDecoyCount`,
   `falseLaunchSignatureCount`, `falseThreatSignatureCount`,
   `activeDecoyConfidencePenalty`, and `message`. The log is informational
   only — it never mutates `ActiveDecoyState`, even when the scan pierced.

`RadarScanCalculator` was already summing both `falseLaunchCount` and
`falseThreatCount` from active decoys (helpers `sumFalseLaunches` and
`sumFalseThreats`) and dropping them on a piercing `FULL_READOUT` scan with
confidence ≥ 85, so the corrected `GHOST_MIRV` defaults now visibly affect
both `activeLaunchCountEstimate` and `incomingThreatCountEstimate` before
piercing without further calculator changes.

The project still compiles cleanly (`EXITCODE=0`) after the refinement; the
offline-only scope, action-code surface, and absence of UI/AI/networking are
unchanged.
