# Step 3 — NukeDesign Schema and Nuke Builder Adapter

This step replaces the Step-2 placeholder string-fields on
`NukeBuildState` with a real, immutable `NukeDesign` schema, plus the
neutral `BuilderNukeSpec` DTO that bridges the existing UI nuke
builder. DEFCON readiness scaling is now integrated end-to-end:
crossing a threshold refreshes both participants' effective build
charge.

> **Design rule held throughout this step:** DEFCON modifies
> *deployment difficulty* (build cost, launch code, launch time,
> warning time) but never modifies damage values
> (`blastRating`, `radiationRating`, `disarmRating`,
> `siloDamageRating`).

---

## 1. Files added / modified

| Status | File |
|---|---|
| Added | [src/main/java/com/tetris/mab/nuke/NukeDoctrineType.java](src/main/java/com/tetris/mab/nuke/NukeDoctrineType.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeSizeCategory.java](src/main/java/com/tetris/mab/nuke/NukeSizeCategory.java) |
| Added | [src/main/java/com/tetris/mab/nuke/GarbageProfile.java](src/main/java/com/tetris/mab/nuke/GarbageProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/DisarmProfile.java](src/main/java/com/tetris/mab/nuke/DisarmProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/SiloDamageProfile.java](src/main/java/com/tetris/mab/nuke/SiloDamageProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/MirvProfile.java](src/main/java/com/tetris/mab/nuke/MirvProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/EmpProfile.java](src/main/java/com/tetris/mab/nuke/EmpProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/DecoyProfile.java](src/main/java/com/tetris/mab/nuke/DecoyProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/FullClearThresholdProfile.java](src/main/java/com/tetris/mab/nuke/FullClearThresholdProfile.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeDesign.java](src/main/java/com/tetris/mab/nuke/NukeDesign.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeDesignValidator.java](src/main/java/com/tetris/mab/nuke/NukeDesignValidator.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeReadinessScaler.java](src/main/java/com/tetris/mab/nuke/NukeReadinessScaler.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeDesignFactory.java](src/main/java/com/tetris/mab/nuke/NukeDesignFactory.java) |
| Added | [src/main/java/com/tetris/mab/nuke/BuilderNukeSpec.java](src/main/java/com/tetris/mab/nuke/BuilderNukeSpec.java) |
| Added | [src/main/java/com/tetris/mab/nuke/NukeBuilderAdapter.java](src/main/java/com/tetris/mab/nuke/NukeBuilderAdapter.java) |
| Added | [src/main/java/com/tetris/mab/DefconChangeResult.java](src/main/java/com/tetris/mab/DefconChangeResult.java) |
| Modified | [src/main/java/com/tetris/mab/NukeBuildState.java](src/main/java/com/tetris/mab/NukeBuildState.java) — placeholder fields replaced with a real `NukeDesign` reference; new `setDesign / redesign / refreshForDefcon` API |
| Modified | [src/main/java/com/tetris/mab/DefconState.java](src/main/java/com/tetris/mab/DefconState.java) — `addEscalation` now returns `DefconChangeResult` |
| Modified | [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — `ParticipantSummary` now includes design id, display name, doctrine, size |
| Modified | [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — captures `DefconChangeResult` from line clears + garbage; on level transition refreshes both `NukeBuildState`s and emits `DEFCON_CHANGED` |
| Added | [Step3.md](Step3.md) |

`ParticipantState` did **not** need to be modified: it already
constructed `NukeBuildState` with the no-arg constructor, which now
defaults to `NukeDesignFactory.createDefaultPlaceholder()` at DEFCON 5.

The Tetris engine itself was not touched in this step.

---

## 2. Existing Nuke Builder inspection

The repository already contains a UI-side "Nuke Builder":

- [src/main/java/com/tetris/view/NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java)
- [src/main/java/com/tetris/model/nuke/NukeDesign.java](src/main/java/com/tetris/model/nuke/NukeDesign.java) (mutable slot/parts editor — not the Step-3 `NukeDesign`)
- [src/main/java/com/tetris/model/nuke/NukePart.java](src/main/java/com/tetris/model/nuke/NukePart.java)
- [src/main/java/com/tetris/model/nuke/NukeSlot.java](src/main/java/com/tetris/model/nuke/NukeSlot.java)

This builder is a Swing dialog that lets the user pick parts per
`NukeSlot` (configuration, primary, secondary, fusion-stage tuning,
specialization). Its model class is mutable, ties to UI state, and
shares its name (`NukeDesign`) with the new schema only in unqualified
form — they live in different packages.

**Decision:** do not couple the strategic match layer to that mutable
UI model. Instead, the neutral `BuilderNukeSpec` record is the
integration seam. A small future glue layer can read the dialog's
selections and build a `BuilderNukeSpec`, which `NukeBuilderAdapter`
turns into a strategic `NukeDesign`. The `fromExistingBuilderObject`
overload also accepts an already-built strategic `NukeDesign` directly.

The Step-2 `nuke-builder.md` repo memory note (the one warning against
re-adding the BUILD SUMMARY panel) still applies and was respected: no
UI files were edited.

---

## 3. NukeDesign schema

`com.tetris.mab.nuke.NukeDesign` is a final immutable class. All
collections are defensively copied at construction (including each
`List<Integer>` value inside `launchCodeByDefcon`) and exposed as
unmodifiable views.

Identity:
- `id`, `displayName`, `doctrineType`, `sizeCategory`

Damage / capability ratings (NEVER touched by DEFCON):
- `blastRating`, `radiationRating`, `disarmRating`, `siloDamageRating`

Deployment difficulty (varies by DEFCON):
- `baseBuildChargeRequired`, `baseLaunchTimePieces`,
  `baseWarningTimePieces`, `detectionProfile`
- `baseLaunchCode` (defensively copied)
- `buildChargeByDefcon`, `launchCodeByDefcon`,
  `launchTimeByDefcon`, `warningTimeByDefcon`

Profiles (nullable ones marked optional):
- required: `garbageProfile`, `disarmProfile`, `siloDamageProfile`
- optional: `mirvProfile`, `empProfile`, `decoyProfile`,
  `fullClearThresholdProfile`

Effective accessors:

```java
int  effectiveBuildChargeRequired(int defconLevel);
List<Integer> effectiveLaunchCode(int defconLevel);   // unmodifiable
int  effectiveLaunchTimePieces(int defconLevel);
int  effectiveWarningTimePieces(int defconLevel);
```

Each effective accessor clamps the DEFCON level to 1..5 and falls back
to the base value if the per-DEFCON map has no entry for that level.

---

## 4. Doctrine types

Enum `NukeDoctrineType` values:

`CLEAN_FUSION`, `DIRTY_BOMB`, `SALTED_WARHEAD`, `CONCRETE_BLASTER`,
`EMP_PAYLOAD`, `MIRV`, `BUNKER_BUSTER`, `DECOY_PACKAGE`, `DOOMSDAY`,
`PLACEHOLDER`.

Helper: `NukeDoctrineType.fromString(String)` is case-insensitive,
treats spaces / hyphens / underscores interchangeably, and falls back
to `PLACEHOLDER` for null or unknown input.

---

## 5. Size categories

Enum `NukeSizeCategory` values: `MICRO`, `TACTICAL`, `THEATER`,
`STRATEGIC`, `SUPERHEAVY`, `DOOMSDAY_SCALE`.

Helper: `NukeSizeCategory.fromBuildCharge(int)` maps build-charge
bands → size: ≤10 MICRO, ≤25 TACTICAL, ≤40 THEATER, ≤70 STRATEGIC,
≤110 SUPERHEAVY, otherwise DOOMSDAY_SCALE.

---

## 6. Profile classes

All seven profiles are immutable Java records:

| Profile | Required | Notes |
|---|---|---|
| `GarbageProfile` | yes | base/max immediate lines, optional waves, targeted flag |
| `DisarmProfile` | yes | disarm power, full-clear ratio, small-bomb gating |
| `SiloDamageProfile` | yes | damage power, hardening / launch-security targeting |
| `MirvProfile` | optional | wave count / lines per wave / gap |
| `EmpProfile` | optional | radar + warning disruption pieces |
| `DecoyProfile` | optional | fake launch / false doctrine / dummy heat / duration |
| `FullClearThresholdProfile` | optional | per-doctrine PC ratios; `defaults()` factory provided |

---

## 7. DEFCON readiness scaling

`NukeReadinessScaler` is the generic helper used by
`NukeBuilderAdapter` to derive per-DEFCON maps from a base value plus a
`NukeSizeCategory`. The hand-tuned defaults in `NukeDesignFactory` use
explicit literal maps (matching the spec exactly) and bypass this
helper.

Scaler intensity by size:

| Size | Build-charge swing | Launch-time swing | Code-length swing |
|---|---|---|---|
| MICRO | ±10% | ±10% | none |
| TACTICAL | ±10/20% | ±20% | shrink at low DEFCON |
| THEATER | +30 / −22% | ±25% | grow at peace, shrink at war |
| STRATEGIC | +40 / −26% | +40 / −25% | strong swing |
| SUPERHEAVY | +55 / −32% | +55 / −30% | very strong swing |
| DOOMSDAY_SCALE | +70 / −40% | +70 / −35% | large swing |

Floors:
- launch / warning time: 3 pieces
- build charge: 1
- launch-code values stay within 1..4

---

## 8. DEFCON modifies deployment difficulty, not damage

This is enforced structurally:

- `NukeDesign.effectiveBuildChargeRequired(...)`,
  `effectiveLaunchCode(...)`, `effectiveLaunchTimePieces(...)`, and
  `effectiveWarningTimePieces(...)` are the **only** DEFCON-aware
  accessors on the schema.
- `blastRating`, `radiationRating`, `disarmRating`, and
  `siloDamageRating` are plain getters with no DEFCON parameter and no
  per-DEFCON map.
- `NukeReadinessScaler` only emits maps for build / launch code /
  launch time / warning time. It has no API surface that touches
  damage.
- The match coordinator's `handleDefconChange` only calls
  `NukeBuildState.refreshForDefcon(...)`, which only re-reads
  `effectiveBuildChargeRequired(...)`.

There is no code path where a DEFCON value reaches a damage rating.

---

## 9. Default nuke designs

`NukeDesignFactory.createAllDefaults()` returns these eight designs:

| id | Doctrine | Size | base build | blast / rad / disarm / silo | base launch code | base L/W |
|---|---|---|---|---|---|---|
| `placeholder_tactical` | PLACEHOLDER | TACTICAL | 20 | 2 / 1 / 1 / 0 | [1,2,1] | 4 / 4 |
| `clean_fusion_strategic` | CLEAN_FUSION | STRATEGIC | 44 | 5 / 1 / 3 / 1 | [4,4] | 7 / 7 |
| `dirty_tactical` | DIRTY_BOMB | TACTICAL | 22 | 2 / 4 / 1 / 0 | [1,3,1] | 5 / 5 |
| `concrete_blaster` | CONCRETE_BLASTER | STRATEGIC | 40 | 2 / 1 / 6 / 5 | [2,4,2] | 7 / 7 |
| `mirv_strategic` | MIRV | STRATEGIC | 42 | 5 / 2 / 2 / 1 | [3,2,3] | 7 / 7 |
| `emp_payload` | EMP_PAYLOAD | THEATER | 30 | 1 / 2 / 3 / 2 | [1,2,2,1] | 6 / 6 |
| `bunker_buster` | BUNKER_BUSTER | THEATER | 34 | 3 / 0 / 5 / 5 | [3,1,3] | 6 / 6 |
| `doomsday_device` | DOOMSDAY | DOOMSDAY_SCALE | 100 | 8 / 3 / 8 / 8 | [4,4,4,4] | 12 / 12 |

Each carries the per-DEFCON build / code / launch-time / warning-time
maps exactly as listed in the Step-3 spec.

---

## 10. NukeBuilderAdapter

`NukeBuilderAdapter` provides:

```java
NukeDesign  fromBuilderSpec(BuilderNukeSpec spec);
NukeDesign  fromExistingBuilderObject(Object builderOutput);
BuilderNukeSpec toBuilderSpec(NukeDesign design);
```

Behavior:
- `doctrineType` is parsed via `NukeDoctrineType.fromString`. The
  `mirv` / `emp` / `decoy` flags only override doctrine when the
  parsed value is `PLACEHOLDER` (i.e. the doctrine string is null,
  blank, or unknown) — explicit doctrines win.
- `size` (build charge) is fed through `NukeSizeCategory.fromBuildCharge`.
- `speed` → `baseLaunchTime` and `baseWarningTime` (`max(3, 10 - speed)`),
  with a hard floor of 3 pieces.
- `stealth` → `detectionProfile` (`max(0, 10 - stealth)`).
- A reasonable `baseLaunchCode` is synthesized from the rating fields,
  clamped to 1..4 (longer codes for DOOMSDAY / very large designs).
- Per-DEFCON maps are produced by `NukeReadinessScaler`.
- Reasonable profile defaults are inferred from doctrine + flags.
- `fromExistingBuilderObject` recognises `BuilderNukeSpec` and the
  Step-3 `NukeDesign`; everything else falls back to
  `createDefaultPlaceholder()`. This documents the neutral
  `BuilderNukeSpec` path as the stable seam.

No UI dependencies: `NukeBuilderAdapter` imports only
`com.tetris.mab.nuke` types and `java.util`.

---

## 11. BuilderNukeSpec

```java
public record BuilderNukeSpec(
        String id, String name, String doctrineType,
        int size,
        int blast, int radiation, int disarm, int siloDamage,
        int speed, int stealth,
        boolean mirv, boolean emp, boolean decoy) { }
```

This is the only thing a future glue layer needs to produce in order
to plug the existing UI builder into the strategic match layer.

---

## 12. NukeBuildState integration

The Step-2 placeholder fields (`currentDesignId`,
`currentDoctrineType`, hardcoded `DEFAULT_REQUIRED_CHARGE`) are gone.
The new state holds:

- `int currentBuildCharge`
- `int effectiveBuildChargeRequired`
- `boolean armed`
- `NukeDesign currentDesign`
- `int overbuiltCharge`

API:

```java
new NukeBuildState();                              // placeholder design @ DEFCON 5
new NukeBuildState(NukeDesign, int defconLevel);

void setDesign(NukeDesign, int defcon);
void redesign(NukeDesign, int defcon, double retainedChargeRatio); // ratio clamped 0..1
void refreshForDefcon(int defcon);
void addCharge(int amount);                         // ignores ≤ 0
void resetCharge();                                 // 0 / 0 / disarmed
```

`armed` and `overbuiltCharge` are recomputed from
`effectiveBuildChargeRequired` on every mutation, so a DEFCON refresh
that changes the requirement immediately re-evaluates whether the
participant is still armed.

---

## 13. Match debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` now carries:

```
participant id, piecesLocked, linesClearedTotal, garbageReceivedTotal,
currentNukeDesignId, currentNukeDisplayName, doctrineType, sizeCategory,
currentNukeCharge, requiredNukeCharge, armed,
siloDamageState, incomingThreatCount, activeLaunchCount, toppedOut
```

Still purely numeric/textual — no `Color[][]`, no Swing types.

`NukeBuildState.toDebugString()` and (transitively)
`MutuallyAssuredBlocksMatch.toDebugString()` now print:

```
Participant{PLAYER_A pieces=0 lines=0 garbage=0 NukeBuild{0/22 armed=false design=placeholder_tactical name=Placeholder Tactical doctrine=PLACEHOLDER size=TACTICAL overbuilt=0} Silo{HP=100 STABLE} incoming=0 launches=0}
```

---

## 14. Intentionally NOT implemented

- Action-code matching against a live key sequence.
- Launch-code execution flow (queueing a launch when the code is
  entered).
- Actual missile launches, in-flight motion, or impacts.
- Incoming threat resolution and warning-time countdowns.
- Garbage / radiation generation from impacts.
- Disarm and silo-damage resolution.
- Upgrade UI.
- AI behaviour.
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)

---

## 15. Acceptance-criteria verification

| Criterion | Status |
|---|---|
| Project compiles with the real javac build | ✓ — `EXITCODE=0` |
| Existing single-player Tetris behavior unchanged | ✓ — engine source untouched in Step 3 |
| Existing Step 2 match creation still works | ✓ — `MutuallyAssuredBlocksMatch.createLocalPvp/createPve` unchanged at the API level |
| `NukeDesign` can be created | ✓ — constructor + 8 factory defaults |
| `NukeDesign` can be validated | ✓ — `NukeDesignValidator.validate(design)` returns `ERROR:` / `WARNING:` strings |
| DEFCON-specific effective build cost | ✓ — `effectiveBuildChargeRequired(defcon)` |
| DEFCON-specific effective launch code | ✓ — `effectiveLaunchCode(defcon)` (unmodifiable) |
| DEFCON-specific launch / warning time | ✓ — `effectiveLaunchTimePieces / effectiveWarningTimePieces` |
| Default nuke designs exist | ✓ — 8 defaults in `NukeDesignFactory.createAllDefaults()` |
| `NukeBuildState` stores a real `NukeDesign` | ✓ — `currentDesign` field is the real schema type |
| `NukeBuildState` uses DEFCON-specific effective build charge | ✓ — `setDesign / redesign / refreshForDefcon` all go through `design.effectiveBuildChargeRequired(defcon)` |
| DEFCON changes refresh both participants' nuke build requirement | ✓ — `MutuallyAssuredBlocksMatch.handleDefconChange(...)` calls `refreshForDefcon` on both, logs `DEFCON_CHANGED` |
| Debug snapshot shows nuke design id, name, doctrine, size | ✓ — `ParticipantSummary` and `NukeBuildState.toDebugString()` |
| `NukeBuilderAdapter` converts `BuilderNukeSpec` → `NukeDesign` | ✓ — `fromBuilderSpec(spec)` + reverse `toBuilderSpec(design)` |
| No launch-code matching, missile launches, impacts, AI, UI (offline-only project) | ✓ — schema/integration only |
| Step3.md documents the implementation | ✓ — this document |

---

## 16. Build verification

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
Remove-Item -Recurse -Force target\classes
New-Item -ItemType Directory -Force target\classes
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | % FullName
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
# EXITCODE=0
```

Final result: clean compile of the entire workspace, zero errors and
zero warnings, with Eclipse Adoptium JDK 25. Maven is not on PATH on
this machine, so `mvn -q -DskipTests compile` was not run; the javac
command above is the same form `run.bat` invokes.
