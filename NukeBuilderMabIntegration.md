# Nuke Builder ↔ MAB Integration

This document describes the work that turned the Nuke Builder from a
mostly-cosmetic side feature into the live strategic tempo core of
Mutually Assured Blocks (MAB). It covers the full audit, the design
rewrite, the live wiring, the safety/terminology cleanup, the new
probes, and verification results.

> **Project scope (permanent).** MAB is offline-only. Supported modes:
> Normal Modern Tetris, MAB PvE, MAB local PvP, the Nuke Builder, and
> practice/debug tools. **Out of scope (permanent):** online multiplayer,
> networking, client/server, matchmaking, sockets, rollback, online
> synchronization, transport layers, online-prep APIs. **No networking
> abstractions were added.**

---

## 1. Goal statement

> The Nuke Builder must directly determine the live MAB match tempo,
> charge requirement, DEFCON scaling, launch behavior, impact profile,
> radiation pressure, EMP disruption, disarm pressure, silo pressure,
> and strategic identity. MAB should feel like Tetris execution powering
> a custom-built nuclear strategy.

This is achieved by:

1. Treating the educational Nuke Builder as the loadout engine, with a
   safe bridge to a strategic `NukeDesign` record consumed by every
   live MAB subsystem.
2. Driving the live charge requirement, launch countdown, impact delay,
   launch route, and intercept difficulty from the design plus DEFCON.
3. Giving each doctrine (`TACTICAL_BLAST`, `HEAVY_BLAST`,
   `DIRTY_PAYLOAD`, `SALTED_PAYLOAD`, `EMP_PAYLOAD`, `BUNKER_BUSTER`,
   `CONCRETE_BLASTER`, `CLEAN_FUSION`, `DOOMSDAY`, `PLACEHOLDER`) a
   distinct strategic identity in the live impact resolver.

---

## 2. Current-state audit (pre-change)

### 2.1 Educational builder fields

`src/main/java/com/tetris/model/nuke/NukePart.java`,
`NukeSlot.java`, `NukeDesign.java` — the educational subsystem
remains largely unchanged. It carries:

- slots (`CONFIGURATION`, `FISSILE`, `TAMPER`, `INITIATOR`,
  `IMPLOSION`, `BOOST`, `SECONDARY`, `CASING`, `SAFETY`,
  `DELIVERY`),
- pedagogical numbers per part (`baseYieldKt`, `efficiencyBonus`,
  `complexity`),
- a multi-stage Teller-Ulam fusion sub-designer (per-stage pusher,
  channel filler, spark plug),
- educational `getWarnings()` and `getHistoricalAnalog()` text.

The builder remains the **educational** front-end. It is allowed to
expose isotope/component names there because that is its purpose.
**Live MAB UI never reads those strings directly** — it only reads the
safe summary produced by `MabNukeBuilderBridge.safeGameplaySummary`.

### 2.2 Original strategic `NukeDesign` fields (before)

```java
public final class NukeDesign {
    String id, displayName;
    NukeDoctrineType doctrineType;     // included MIRV, DECOY_PACKAGE
    NukeSizeCategory sizeCategory;
    int baseBuildChargeRequired;
    int blastRating, radiationRating, disarmRating, siloDamageRating;
    int baseLaunchTimePieces, baseWarningTimePieces;
    int detectionProfile;              // radar-era field
    List<Integer> baseLaunchCode;
    Map<Integer,Integer> buildChargeByDefcon, launchTimeByDefcon,
                          warningTimeByDefcon;
    Map<Integer,List<Integer>> launchCodeByDefcon;
    GarbageProfile, DisarmProfile, SiloDamageProfile;
    MirvProfile mirvProfile;           // split-payload, removed
    EmpProfile  empProfile;            // radar disruption, reframed
    DecoyProfile decoyProfile;         // fake launch, removed
    FullClearThresholdProfile fullClearThresholdProfile;
}
```

No `emp`, `stability`, or `complexity` ratings existed on the design.
The launch route was a global static (`LAUNCH_TETRIS_GOAL=4`,
`LAUNCH_SPIN_GOAL=2`).

### 2.3 Bridge conversion behavior (before)

`MabNukeBuilderBridge.toBuilderSpec` produced a `BuilderNukeSpec` with
`mirv`, `decoy`, `stealth` booleans/ints. ICBM deliveries were forced
to MIRV doctrine. Tamper choices that named U-238 routed to
`DIRTY_BOMB`. The summary mentioned "split launch", "EMP profile",
"decoy profile" as inline strings.

### 2.4 Setup UI behavior (before)

PvE and local PvP setup carried a `MabNukeDesignSelection` but the
selected design's strategic implications were never surfaced in the
battle shell — only `MabNukeRedesignPanel` (a mid-match dialog)
referenced it. The simplified strategic state hardcoded
`chargeRequired = 100` and route targets, ignoring the design.

### 2.5 Live match behavior (before)

`MabSimplifiedStrategicState.chargeRequired = 100` (universal). Charge
came from `NukeBuildState.effectiveBuildChargeRequired` but the
simplified HUD readout was independently maintained and did not always
mirror it. Launch route used hardcoded `LAUNCH_TETRIS_GOAL=4`,
`LAUNCH_SPIN_GOAL=2`. The active warhead design had no effect on the
live intercept window, the live launch countdown displayed to the
defender, or the route requirement.

### 2.6 DEFCON behavior (before)

`DefconState` only changed the readiness level. `NukeBuildState.refreshForDefcon`
re-derived the effective build charge from the design's per-DEFCON map,
but the simplified strategic state was not refreshed alongside, so the
HUD charge requirement was stale after a DEFCON drop.

### 2.7 Impact / radiation behavior (before)

`ImpactResolver` separated blast → immediate garbage, radiation → wave
schedule, disarm → charge reduction, silo damage → integrity
reduction. **EMP did nothing live** — `EmpProfile` only defined
`radarDisruptionPieces` / `warningDisruptionPieces`. MIRV produced
extra wave entries via `GarbageProfile.usesWaves` + `MirvProfile`.

### 2.8 MIRV / radar / warning / intel / decoy locations (before)

- `NukeDoctrineType` — included `MIRV` and `DECOY_PACKAGE`.
- `MirvProfile.java`, `DecoyProfile.java`, `EmpProfile.java`
  (with radar disruption fields).
- `BuilderNukeSpec` — `mirv`, `decoy`, `stealth` fields.
- `NukeDesignFactory.createDefaultMirv`.
- `MabNukePresetDefinition` — included a "MIRV Package" preset.
- `MabNukeBuilderBridge` — preferentially mapped ICBM → MIRV; summary
  emitted "split launch" / "EMP profile" / "decoy profile".
- `NukeBuilderAdapter` — produced `MirvProfile`/`DecoyProfile` from
  spec flags.
- `ImpactResolver` — fed the `GarbageProfile.usesWaves` path with
  MIRV-derived wave counts.
- `MabBattleShellPanel.buildIntelBay` — rendered "RADAR :: PASSIVE"
  in the opponent intel bay.
- `MabAlertModel` — surfaced "Radar scan complete", "Opponent decoy",
  "Intel stale", "Incoming threat / WARNING …" alerts.
- `MabHudFormatter` — surfaced `THREAT_WARNING_STARTED`,
  `RADAR_SCAN_COMPLETED`, `RADAR_DECOY_EFFECT_APPLIED`,
  `DECOY_ACTIVATED`, `RADAR_SCAN_STARTED` in the player feed.
- `MabPveGamePanel` — "WARNING — N incoming threats".
- `MabStage` — "INCOMING THREAT" / "IMPACT INCOMING".
- `MabMatchResultFormatter` — "Radar scans : N" / "Decoys activated : N".
- `RadarScanCalculator.spoofDoctrine` — switch-cased on `MIRV` /
  `DECOY_PACKAGE`.
- `InterceptResolver.computeThreatResistance` — special-cased `MIRV`
  / `DECOY_PACKAGE` and `getDetectionProfile() >= 4`.

### 2.9 Underused Nuke Builder output (before)

The educational design fed into `MabNukeDesignSelection` only via
`MabNukeBuilderBridge`, which produced a `NukeDesign` that was then
**stored** but rarely consulted: the simplified strategic state used
its own hardcoded charge/route values. `MabNukeRedesignPanel` could
swap designs mid-match, but the swap did not propagate to the route
goals shown on the tactical strip.

---

## 3. Exact changes made

### 3.1 Files added

- `src/main/java/com/tetris/mab/sim/MabNukeBuilderLiveIntegrationProbe.java`
- `src/main/java/com/tetris/mab/sim/MabNukeBuilderMatchSetupProbe.java`
- `src/main/java/com/tetris/mab/sim/MabDefconTempoProbe.java`
- `src/main/java/com/tetris/mab/sim/MabRadiationImpactProbe.java`
- `src/main/java/com/tetris/mab/sim/MabEmpPayloadProbe.java`
- `src/main/java/com/tetris/mab/sim/MabImpactProfileSeparationProbe.java`
- `src/main/java/com/tetris/mab/sim/MabLiveWarheadStatusLayoutProbe.java`
- `src/main/java/com/tetris/mab/sim/MabDesignRouteRequirementProbe.java`
- `src/main/java/com/tetris/mab/sim/MabDesignInterceptProbe.java`
- `src/main/java/com/tetris/mab/sim/MabTerminologyProbe.java`
- `NukeBuilderMabIntegration.md` (this file)

### 3.2 Files modified

- `src/main/java/com/tetris/mab/nuke/NukeDoctrineType.java`
- `src/main/java/com/tetris/mab/nuke/BuilderNukeSpec.java`
- `src/main/java/com/tetris/mab/nuke/EmpProfile.java`
- `src/main/java/com/tetris/mab/nuke/NukeDesign.java`
- `src/main/java/com/tetris/mab/nuke/NukeDesignFactory.java`
- `src/main/java/com/tetris/mab/nuke/NukeBuilderAdapter.java`
- `src/main/java/com/tetris/mab/nuke/MabNukeBuilderBridge.java`
- `src/main/java/com/tetris/mab/nuke/NukeReadinessScaler.java`
- `src/main/java/com/tetris/mab/NukeBuildState.java`
- `src/main/java/com/tetris/mab/IncomingThreatState.java`
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java`
- `src/main/java/com/tetris/mab/clear/MabSimplifiedStrategicState.java`
- `src/main/java/com/tetris/mab/impact/ImpactResolver.java`
- `src/main/java/com/tetris/mab/intercept/InterceptResolver.java`
- `src/main/java/com/tetris/mab/intel/RadarScanCalculator.java`
- `src/main/java/com/tetris/mab/launch/ThreatStatus.java`
- `src/main/java/com/tetris/mab/ui/MabNukePresetDefinition.java`
- `src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java`
- `src/main/java/com/tetris/mab/ui/MabStage.java`
- `src/main/java/com/tetris/mab/ui/MabAlertModel.java`
- `src/main/java/com/tetris/mab/ui/MabHudFormatter.java`
- `src/main/java/com/tetris/mab/ui/MabMatchResultFormatter.java`
- `src/main/java/com/tetris/mab/ui/MabPveGamePanel.java`
- `src/main/java/com/tetris/mab/sim/MabSimulationConfig.java`
- `src/main/java/com/tetris/mab/sim/MabSimulationRunner.java`
- `src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java`

### 3.3 Files NOT modified (preserved)

The educational builder package
(`src/main/java/com/tetris/model/nuke/*`) was left alone — it remains
the historical/educational sandbox. The Tetris engine, controller, key
wizard, draft overlay, and active-doctrine panels were not touched.

---

## 4. Removed / neutralised systems

### 4.1 MIRV

- `NukeDoctrineType.MIRV` is kept as a deprecated enum value for
  binary compatibility. `isCurrent()` returns `false`; the parser
  remaps stored `"MIRV"` strings to `HEAVY_BLAST`. The label
  `displayLabel()` returns `"Heavy Blast"` so the value can never
  surface to the player.
- `MirvProfile.java` is **kept as a legacy class** so binary
  references resolve, but `NukeDesign.getMirvProfile()` is a
  deprecated accessor that always returns `null`. The factory and
  adapter never construct one.
- `BuilderNukeSpec.mirv()` is a deprecated accessor that always
  returns `false`.
- `NukeDesignFactory.createDefaultMirv()` delegates to
  `createDefaultHeavyBlast()` and is marked deprecated.
- `MabNukePresetDefinition` no longer offers MIRV. The new ladder is
  10 doctrines, none of which are MIRV-derived.
- `MabNukeBuilderBridge.doctrineFor`: ICBM delivery now resolves to
  `HEAVY_BLAST`. Salted choice → `SALTED_PAYLOAD`. Dirty tamper →
  `DIRTY_PAYLOAD`. Etc.
- `RadarScanCalculator.spoofDoctrine` (legacy, no live consumers) was
  patched to compile under the new enum and never returns MIRV /
  DECOY_PACKAGE.
- `ImpactResolver` no longer reads `MirvProfile`. The wave-count for
  delayed garbage comes from the doctrine + radiation rating.
- `NukeBuilderAdapter.fromBuilderSpec` cannot produce MIRV doctrine.
  If a legacy spec arrives with MIRV doctrine string, the adapter
  defensively remaps it to `HEAVY_BLAST` via
  `NukeDoctrineType.isCurrent()`.

### 4.2 Radar, warning, intel, detection

- `NukeDesign.getDetectionProfile()` is deprecated and always
  returns 0.
- `NukeDesign.getBaseWarningTimePieces()` is deprecated; live code
  reads `getBaseImpactDelayPieces()`.
- `NukeDesign.effectiveImpactDelayPieces(int defcon)` is the new
  accessor for the intercept window. The legacy
  `effectiveWarningTimePieces` is a deprecated alias.
- `EmpProfile`'s legacy `radarDisruptionPieces` /
  `warningDisruptionPieces` accessors always return 0.
- `ThreatStatus.WARNING_ACTIVE` remains as an internal lifecycle
  value, but `playerFacingLabel()` returns `"IN FLIGHT"` for it (and
  `"IMPACT PENDING"` for `IMPACT_READY`). The defender never sees the
  word "warning".
- `MabBattleShellPanel.buildIntelBay` no longer renders
  `RADAR :: PASSIVE`; it shows `MODE :: OFFLINE` and the header is
  `STATUS` instead of `INTEL`.
- `MabAlertModel`: `INCOMING_THREAT_CREATED` → "INCOMING IMPACT",
  `IMPACT_READY` → "IMPACT PENDING". `RADAR_SCAN_*` and
  `DECOY_ACTIVATED` events are no longer surfaced to the player.
  "Intel stale" alert removed.
- `MabHudFormatter.PLAYER_FACING_EVENTS` no longer lists
  `THREAT_WARNING_STARTED`, `RADAR_SCAN_*`, `RADAR_DECOY_EFFECT_APPLIED`,
  `DECOY_ACTIVATED`, `DECOY_EXPIRED`.
- `MabPveGamePanel` alert banner now says
  `"INCOMING IMPACT — N inbound · SPIN TO INTERCEPT"` instead of
  `"WARNING — N incoming threat(s)"`.
- `MabStage` headlines: `INCOMING IMPACT` and `IMPACT PENDING`
  replace the old `INCOMING THREAT` / `IMPACT INCOMING`.
- `MabMatchResultFormatter` no longer prints the "Radar scans" /
  "Decoys activated" lines.

### 4.3 Decoy

- `NukeDoctrineType.DECOY_PACKAGE` deprecated; `isCurrent() == false`;
  parser remaps to `PLACEHOLDER`.
- `DecoyProfile.java` kept as a legacy record for binary
  compatibility, but `NukeDesign.getDecoyProfile()` always returns
  `null`. The factory/adapter never construct one.
- `BuilderNukeSpec.decoy()` always returns `false`.

### 4.4 Stealth

- `BuilderNukeSpec.stealth()` always returns `0` and is marked
  deprecated.
- `NukeBuilderAdapter` no longer consults the stealth field.

---

## 5. Updated doctrine model

`NukeDoctrineType.java`:

| Value | Player-facing label | Role |
|---|---|---|
| `TACTICAL_BLAST` | Tactical Blast | Fast/light. Low charge, short countdown, low impact. Good for tempo rush. |
| `HEAVY_BLAST` | Heavy Blast | Slower build. Strong immediate garbage. Decisive single-payload. |
| `DIRTY_PAYLOAD` | Dirty Payload | Moderate immediate, **delayed radiation waves**, messy patterns. |
| `SALTED_PAYLOAD` | Salted Payload | Extreme radiation. Slower; severe lingering pressure. |
| `EMP_PAYLOAD` | EMP Payload | Low blast. Drains opponent charge, disrupts route, delays launch. |
| `BUNKER_BUSTER` | Bunker Buster | Silo / infrastructure focus. Moderate blast, low radiation. |
| `CONCRETE_BLASTER` | Concrete Blaster | Anti-nuke. Heavy disarm + silo damage. Low board garbage. |
| `CLEAN_FUSION` | Clean Fusion | Strong clean blast, low radiation, reliable. |
| `DOOMSDAY` | Doomsday | Extremely high charge, severe multi-axis impact, rare. |
| `PLACEHOLDER` | Training Payload | Cheap fallback. |
| `MIRV` (legacy) | "Heavy Blast" | Never produced; remapped. |
| `DECOY_PACKAGE` (legacy) | "Training Payload" | Never produced; remapped. |
| `DIRTY_BOMB` (legacy) | "Dirty Payload" | Alias retained for save compat. |
| `SALTED_WARHEAD` (legacy) | "Salted Payload" | Alias retained for save compat. |

`fromString` migrates legacy strings (`"DIRTY_BOMB"`, `"MIRV"`,
`"DECOY_PACKAGE"`, etc.) so older saves still parse safely.

---

## 6. Updated `BuilderNukeSpec`

```java
public record BuilderNukeSpec(
        String id, String name, String doctrineType,
        int size, int blast, int radiation, int emp, int disarm, int siloDamage,
        int speed, int stability, int complexity,
        boolean dirty, boolean salted, boolean clean, boolean bunker, boolean doomsday) { }
```

The legacy 13-arg constructor (with `stealth`, `mirv`, `decoy`) is
kept as a deprecated compatibility constructor that drops the obsolete
fields and synthesises reasonable defaults for the new ones.

Field-mapping guidelines are documented inline in
`BuilderNukeSpec.java` and `NukeBuilderAdapter.java`:

- `size` → charge requirement and yield class.
- `blast` → immediate garbage.
- `radiation` → delayed waves and messy garbage pattern.
- `emp` → strategic disruption (charge drain / route / launch delay).
- `disarm` → opponent charge reduction.
- `siloDamage` → silo / infrastructure damage.
- `speed` → launch countdown and charge efficiency.
- `stability` → reduces complexity penalties / improves reliability.
- `complexity` → increases charge requirement and DEFCON sensitivity.

---

## 7. Updated bridge conversion

`MabNukeBuilderBridge` now:

- maps ICBM delivery → `HEAVY_BLAST` (was `MIRV`),
- maps salted tamper → `SALTED_PAYLOAD`,
- maps U-238/depleted-U tamper → `DIRTY_PAYLOAD`,
- assigns `emp` from doctrine + complexity (EMP doctrine: 4 + c/2;
  doomsday: 4; else: 0),
- derives `stability` from complexity and safety-hardware choice
  (Enhanced Nuclear Detonation Safety etc.),
- produces a player-safe display name like `"Heavy Clean Fusion"` or
  `"Tactical Tactical Blast (Concept)"` — never raw kt numbers,
- writes a safe summary:
  `"Dirty Tactical Payload | Size: TACTICAL | Payload: Dirty Payload | Charge 28 | Blast 2 | Rad 4"`.

`isSafeGameplaySummary` now also blocks: `mirv`, `radar`, `warning`,
`intel`, `decoy`, `detection`, `critical mass`, `isotope`.

---

## 8. DEFCON tempo integration

- `DefconState` starts at 5 and drops toward 1 on escalation.
- `MutuallyAssuredBlocksMatch.handleDefconChange` now also calls
  `applyDesignToSimplifiedState(...)` on both participants, refreshing
  the HUD-visible charge requirement and route goals.
- New public `addEscalationAndRefresh(int amount, String reason)`
  hook for callers (probes / debug) to apply escalation that takes
  effect immediately on both sides.
- `NukeReadinessScaler` now takes complexity / stability as
  parameters; the DEFCON sensitivity curve is biased ±20% by
  `(complexity - stability) × 0.02`. Stable designs damp the swing;
  complex designs amplify it.
- DEFCON **never** changes raw damage ratings (blast / radiation /
  EMP / disarm / silo damage). Verified by
  `MabDefconTempoProbe` → `damageRatingsInvariant=true`.

---

## 9. NukeBuildState charge integration

`NukeBuildState.getEffectiveBuildChargeRequired` is sourced from
`currentDesign.effectiveBuildChargeRequired(currentDefconLevel)`. The
state also exposes:

- `getEffectiveLaunchCountdownPieces()`
- `getEffectiveImpactDelayPieces()`
- `getEffectiveLaunchTetrisGoal()`
- `getEffectiveLaunchSpinGoal()`

`MutuallyAssuredBlocksMatch.applyDesignToSimplifiedState(participant)`
synchronises the simplified state with the design (charge requirement
and both route goals).

`MutuallyAssuredBlocksMatch.applyWarheadDesign(pid, design)` is the
new entry point used by `GameController` at match start. It logs a
`MAB_WARHEAD_DESIGN_APPLIED` event with charge/route metadata.

---

## 10. Launch countdown / impact delay integration

- Strategic field `baseWarningTimePieces` renamed to
  `baseImpactDelayPieces` (legacy accessor preserved as deprecated
  alias).
- `NukeReadinessScaler.impactDelay(...)` replaces the old
  `warningTime(...)` (which now delegates).
- `NukeDesign.effectiveImpactDelayPieces(defcon)` is the live
  accessor; `effectiveWarningTimePieces` is deprecated alias.
- `IncomingThreatState.getImpactDelayPiecesTotal` /
  `getImpactDelayPiecesRemaining` added as new accessors; legacy
  `warningPieces*` retained for binary compatibility.
- `ThreatStatus.playerFacingLabel()` returns:
  - `WARNING_ACTIVE` → `"IN FLIGHT"`
  - `IMPACT_READY`   → `"IMPACT PENDING"`
  - `INTERCEPTED`    → `"INTERCEPT SUCCESS"`
  - `RESOLVED`       → `"IMPACT RESOLVED"`
  - `CANCELLED`      → `"LAUNCH CANCELLED"`

Player-facing UI uses `INCOMING IMPACT`, `IMPACT PENDING`,
`SPIN TO INTERCEPT`. The words `WARNING`, `RADAR`, `DETECTION`,
`INTEL` are not used.

---

## 11. Radiation impact system

`RadiationLevel` maps the design's `radiationRating` to a 0..5
messiness score. `RadiationGarbagePatternGenerator` is deterministic:
the hole column for each row is computed from `(rowIndex,
radiationLevel, tagHash)` with no RNG. Higher radiation shifts the
hole more frequently and (at SEVERE+) jumps across the board.

`ImpactResolver.buildPlan(...)`:

- splits `effectiveBlastLines` into `immediate` (capped by
  `GarbageProfile.maxImmediateLines`) and `delayed`,
- when the design declares `usesWaves`, splits the delayed lines
  across `waveCount` waves,
- schedules each wave with a `PieceCountdownTimer` measured in
  defender pieces.

Clean Fusion (rad 1) produces a single hole column; Dirty Payload
(rad 4) shifts every few rows; Salted Payload (rad 6 → SALTED) jumps
across the board. Verified by `MabRadiationImpactProbe`.

---

## 12. EMP disruption system

`EmpProfile` was reframed as a gameplay-level tempo disruptor:

```java
public record EmpProfile(
        int chargeDrain,
        int routeProgressLoss,
        int launchDelayPieces,
        boolean canFullClearWithUpgradeOnly) { }
```

The legacy `radarDisruptionPieces` / `warningDisruptionPieces`
accessors always return 0.

`ImpactResolver` now applies EMP effects after disarm/silo:

1. Drain opponent build charge by `chargeDrain` (after halving for
   manual override).
2. Drain spin pips, then Tetris pips, totalling
   `routeProgressLoss` (via the new
   `MabSimplifiedStrategicState.decrementSpinProgress` /
   `decrementTetrisProgress`).
3. If `launchDelayPieces > 0`, mark every COUNTDOWN/IN_FLIGHT
   opponent launch as `empWeakened` (existing damage-reduction path).

The result message appends
`emp:chargeDrain=N,routeLoss=N,launchDelay=N` for debugging /
telemetry.

Verified by `MabEmpPayloadProbe` and `MabImpactProfileSeparationProbe`.

---

## 13. Blast / disarm / silo / EMP separation

| Axis | Source | Effect |
|---|---|---|
| Blast | `design.getBlastRating()` | Immediate garbage rows (capped by `GarbageProfile.maxImmediateLines`). |
| Radiation | `design.getRadiationRating()` | Delayed wave count + scheduling + hole-shift messiness. |
| Disarm | `design.getDisarmRating()` | Drains defender's current build charge. |
| Silo damage | `design.getSiloDamageRating()` | Reduces `SiloState.integrity`; may trigger silo states. |
| EMP | `design.getEmpProfile()` | Charge drain + route progress loss + launch delay. |

Profiles are distinct in the canonical presets:

- Heavy Blast: blast 7, rad 2 — wins on immediate.
- Dirty Payload: blast 2, rad 4 — wins on delayed messy pressure.
- EMP Disruptor: blast 1, emp 6 — wins on tempo disruption.
- Bunker Buster: silo 5 — wins on infrastructure.
- Concrete Blaster: disarm 6, silo 5 — wins on anti-nuke.

---

## 14. PvE setup behavior

`MabPveConfig.getNukeDesignSelection()` is required (defaults to the
placeholder if none provided). The controller calls
`match.applyWarheadDesign(PLAYER_A, selection.getDesign())` and
applies a default design to PLAYER_B (AI). The selection is visible
in the setup dialog. The selected design appears in the live battle
shell sub-line.

---

## 15. Local PvP setup behavior

`MabLocalPvpConfig.getNukeDesignP1()` and `getNukeDesignP2()` are
independent. Each defaults to the placeholder. Both designs are
visible at setup. The controller calls
`match.applyWarheadDesign(...)` for each side, then the battle shell
shows both warhead lines in their banners.

---

## 16. Live battle-shell warhead display

`MabStationBannerPanel.setNukeDesignLine(...)` is now fed every
refresh from `MabBattleShellPanel.compactWarheadLine(match, pid)`,
which returns:

```
DesignName | DOCTRINE LABEL | charge/required | B# R# E# D# S#
```

This sits in the banner's sub-B slot when no other dramatic state
overrides it. Width is bounded by `shortName(...)` (truncates names
to 22 chars) and fits inside the banner at 1366×768 without
clipping. No tooltip required.

The `MabTacticalStripPanel` is unchanged structurally — it already
shows DEFCON-derived charge / route pips. Now those values are
design-specific because the simplified state mirrors them.

---

## 17. Upgrade synergy changes

The upgrade system already wires into charge/route/intercept via
`MabUpgradeEffectResolver`. No upgrade was structurally changed —
they now operate on top of the design-derived baselines:

- Fast Fuse still calls `state.setLaunchTetrisGoal(...)`; the
  baseline is whatever the design specified, so Fast Fuse pulls
  Heavy's 5 down to 3 instead of pulling Light's 3 down to 1.
- Charge-cap upgrades still apply on top, but `enforceCap(...)` keeps
  the cap at least as high as the design's effective requirement
  (so Doomsday's 180 charge requirement still arms cleanly).
- Intercept strength deltas still apply, but the base resistance is
  the design's `interceptDifficultyRating()` plus stability /
  complexity modifiers.

Verified by `MabUpgradeObservedEffectProbe` (still passing) and
`MabDesignRouteRequirementProbe` (`upgradesStillApply=true`).

---

## 18. UI terminology cleanup

| Old | New |
|---|---|
| `WARNING — N incoming threats` | `INCOMING IMPACT — N inbound · SPIN TO INTERCEPT` |
| `Warning Active` | `In Flight` |
| `INCOMING THREAT` | `INCOMING IMPACT` |
| `IMPACT INCOMING` | `IMPACT PENDING` |
| `RADAR :: PASSIVE` (intel bay) | `MODE :: OFFLINE` (status bay) |
| `Intel stale — run Radar Scan` | (alert removed) |
| `MIRV Package` (preset) | `Heavy Strategic Blast` |
| `Decoys activated : N` (match result) | (line removed) |
| `Radar scans : N` (match result) | (line removed) |
| `Radar scan complete` (alert) | (event no longer surfaced) |
| `Opponent decoy` (alert) | (event no longer surfaced) |

The forbidden tokens are blocked at the bridge level
(`MabNukeBuilderBridge.isSafeGameplaySummary`) and verified by
`MabTerminologyProbe`.

---

## 19. Safety boundary

The educational Nuke Builder dialog continues to contain
educational/historical/contextual information (isotope names, real
historical analogs, "Castle Bravo" surprise, etc.). **None of that
text reaches live MAB gameplay UI.**

`MabNukeBuilderBridge.isSafeGameplaySummary(...)` is the boundary
function. It rejects any of: `kg`, `kilogram`, `u-235`, `u-238`,
`pu-239`, `plutonium`, `uranium`, `tritium`, `deuterium`, `lens`,
`initiator`, `tamper`, `fissile`, `implosion`, `casing`,
`mirv`, `radar`, `warning`, `intel`, `decoy`, `detection`,
`critical mass`, `isotope`.

`MabTerminologyProbe` swings through every doctrine, preset,
summary, stage headline, threat-status label, and synthesised alert
and rejects any that hit a forbidden token.

---

## 20. Probe results

### 20.1 New probes (all passing)

```
MabNukeBuilderLiveIntegrationProbe: success=true
MabNukeBuilderMatchSetupProbe:      success=true
MabDefconTempoProbe:                success=true
MabRadiationImpactProbe:            success=true
MabEmpPayloadProbe:                 success=true
MabImpactProfileSeparationProbe:    success=true
MabLiveWarheadStatusLayoutProbe:    success=true
MabDesignRouteRequirementProbe:     success=true
MabDesignInterceptProbe:            success=true
MabTerminologyProbe:                success=true
```

Selected expected output blocks:

```
=== MAB DEFCON Tempo Probe ===
startsAtDefcon5=true
escalationDropsDefcon=true
damageRatingsInvariant=true
chargeRequirementScales=true
launchCountdownScales=true
bothPlayersRefresh=true
simplifiedStateScales=true
noRadarWarningIntel=true
success=true

=== MAB Radiation Impact Probe ===
cleanLowRadiation=true
dirtyDelayedWaves=true
saltedSevereWaves=true
radiationAffectsPattern=true
radiationDeterministic=true
blastStillImmediate=true
noRealWorldUnits=true
success=true

=== MAB EMP Payload Probe ===
empLowGarbage=true
empHasProfile=true
empDrainsCharge=true
empDisruptsRoute=true
empCanDelayLaunch=true
empDoctrineCorrect=true
empNoRadarWarningIntel=true
activeEmpStillMinor=true
noEngineeringText=true
success=true
```

### 20.2 Existing probes

```
MabControlMappingProbe:             success=true
MabLocalPvpProbe:                   success=true
MabNukeBuilderIntegrationProbe:     success=true
MabUpgradeObservedEffectProbe:      success=true
MabUpgradeCoverageProbe:            success=true
MabActiveDoctrineProbe:             success=true
MabDoctrineStatusOverlayProbe:      success=true
MabUpgradeOverlayLayoutProbe:       success=true
MabUpgradeDraftProbe:               success=true
MabUpgradeEffectProbe:              success=true
MabSharedPieceSequenceProbe:        success=true
MabChargeCalculatorProbe:           success=true
MabSimplifiedCoreProbe:             success=true
MabSpinDetectionProbe:              success=true
MabThreatLifecycleProbe:            success=true
MabBattleShellLayoutProbe:          success=true
MabInputStateProbe:                 success=false  (pre-existing; not introduced by this change)
MabKeyWizardLayoutProbe:            NOT_PRESENT (pre-existing)
MabLocalPvpDraftProbe:              NOT_PRESENT (pre-existing)
MabLocalPvpActiveDoctrineProbe:     NOT_PRESENT (pre-existing)
MabLocalPvpLifecycleProbe:          NOT_PRESENT (pre-existing)
```

`MabInputStateProbe.qDoesNotConflictWithDefaultControls=false` was
verified against the unchanged main branch — pre-existing condition,
not introduced here. The four `NOT_PRESENT` probes are documented in
the original spec but never existed in the repository.

---

## 21. Simulation results

```
java -cp target/classes com.tetris.mab.sim.MabSimulationRunner smoke
mode=SMOKE ok=true ticks=80 events=270 launches=1 impacts=1
  scans=1 decoys=1 civDef=1 upgrades=0 garbage=2 invariantFailures=0

java -cp target/classes com.tetris.mab.sim.MabSimulationRunner ai-vs-dummy 160
mode=AI_VS_DUMMY ok=true ticks=160 events=535 launches=0 impacts=0
  garbage=0 invariantFailures=0

java -cp target/classes com.tetris.mab.sim.MabSimulationRunner ai-vs-ai 160
mode=AI_VS_AI ok=true ticks=160 events=1029 launches=0 impacts=0
  garbage=0 invariantFailures=0

java -cp target/classes com.tetris.mab.sim.MabSimulationRunner balance
4 profiles × 80 ticks; all profiles invariantFailures=0

java -cp target/classes com.tetris.mab.sim.MabSimulationRunner balance-report
4 profiles; localPvpHeadlessSmoke=true; invariantFailures=0; success=true

java -cp target/classes com.tetris.mab.sim.MabSimulationRunner nuke-builder-balance 160
9 design-vs-design scenarios; all invariantFailures=0; success=true
```

The `nuke-builder-balance` scenarios:

1. Tactical Blast vs Tactical Blast
2. Tactical Blast vs Heavy Blast
3. Dirty Payload vs Clean Fusion
4. EMP Payload vs Heavy Blast
5. Bunker Buster vs Dirty Payload
6. Salted Payload vs Tactical Blast
7. Doomsday vs Tactical Blast
8. Placeholder vs Placeholder
9. Concrete Blaster vs Dirty Payload

Each scenario reports `launches / impacts / garbage / chargeA /
chargeB / siloA / siloB / invariantFailures=0`. The different charge
totals across scenarios at the same tick budget confirm the designs
produce different live tempos.

---

## 22. Build verification (exact commands)

```
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative\.claude\worktrees\infallible-wescoff-d9fc26'
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
# EXITCODE = 0
```

The Bash-equivalent that was actually used (Git Bash on Windows):

```
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
find src/main/java -name "*.java" > /tmp/tetris_sources.txt
javac -d target/classes -encoding UTF-8 @/tmp/tetris_sources.txt
# Exit 0; "Note: Some input files use or override a deprecated API."
# (Expected — the legacy MIRV / DECOY / detection accessors are
# intentionally deprecated.)
```

---

## 23. Manual verification

`MabLiveWarheadStatusLayoutProbe` covers the structural side of the
live UI in headless mode. The full visual flow is exercised by the
manual command:

```
java -Dtetris.controls.wizard=true -Dmab.input.debug=true -Dmab.debug.upgrades=true -cp target\classes com.tetris.Main
```

Manual checklist coverage (instrumented checks via probes vs visual
inspection in the live UI):

| # | Check | Coverage |
|---|---|---|
| 1 | Startup key calibration | unchanged; existing `MabControlMappingProbe` |
| 2 | Main menu opens | unchanged |
| 3 | Nuke Builder opens | `MabNukeBuilderIntegrationProbe` (builder/adapter path) |
| 4 | Builder can create/select a design | `MabNukeBuilderIntegrationProbe.builderModelAvailable=true` |
| 5 | PvE setup requires visible warhead design | `MabNukeBuilderMatchSetupProbe.pveDesignRequiredVisible=true` |
| 6 | PvE selected design shown before launch | structural; bridge summary safe |
| 7 | PvE selected design in live battle shell | `MabLiveWarheadStatusLayoutProbe.p1WarheadVisible=true` |
| 8 | AI/opponent design visible in battle shell | `MabLiveWarheadStatusLayoutProbe.p2WarheadVisible=true` |
| 9 | PvP setup shows both designs | `MabNukeBuilderMatchSetupProbe.pvpP1/P2DesignVisible=true` |
| 10 | PvP starts with both designs | `MabNukeBuilderMatchSetupProbe.pvpAppliesEachPlayer=true` |
| 11 | DEFCON visible | `MabLiveWarheadStatusLayoutProbe.defconVisible=true` |
| 12 | DEFCON changes update effective charge requirement | `MabDefconTempoProbe.chargeRequirementScales=true` |
| 13 | Light arms faster than heavy | `MabNukeBuilderLiveIntegrationProbe.designsHaveDifferentCharge=true` |
| 14 | Heavy impacts harder than light | `MabImpactProfileSeparationProbe.blastImmediate=true` |
| 15 | Dirty creates delayed/messier pressure | `MabRadiationImpactProbe.dirtyDelayedWaves=true` |
| 16 | EMP disrupts charge/route/launch tempo | `MabEmpPayloadProbe.empDrainsCharge/Disrupts/Route=true` |
| 17 | Disarm design reduces opponent charge | `MabImpactProfileSeparationProbe.disarmReducesCharge=true` |
| 18 | Silo design damages silo | `MabImpactProfileSeparationProbe.siloDamagesInfrastructure=true` |
| 19 | Spin intercept works | `MabSpinDetectionProbe` + `MabThreatLifecycleProbe` |
| 20 | Intercept difficulty reflects design | `MabDesignInterceptProbe.lightEasier/heavyHarder/doomsdayHardest=true` |
| 21–25 | No MIRV/radar/warning/intel/decoy text | `MabTerminologyProbe.noMirvText/noRadarText/noWarningText/noIntelText/noDecoyText=true` |
| 26 | No unsafe engineering text | `MabTerminologyProbe.summariesSafe + alertsSafe` |
| 27 | Normal Tetris still works | `MabSharedPieceSequenceProbe` and Tetris model untouched |
| 28 | MAB PvE still works | `MabSimulationRunner ai-vs-dummy` |
| 29 | Local PvP still works | `MabLocalPvpProbe` |
| 30 | Upgrade draft still works | `MabUpgradeDraftProbe` |
| 31 | Active commands remain minor | `MabActiveDoctrineProbe` + `MabDoctrineStatusOverlayProbe` |
| 32–34 | Restart / Back to menu / No stuck input | unchanged; existing probes |

---

## 24. Remaining limitations

1. **No new GUI feature for builder/setup-time preview of the
   compact warhead line.** The current setup dialogs only show the
   bridge's `safeGameplaySummary` string. A side-by-side compact
   profile card (matching the battle-shell line format) would be a
   nice polish but is not required for integration.
2. **AI archetype names**: `MabAiArchetype.MIRV_CONTROLLER` is kept
   as an internal enum because removing it would force a much larger
   refactor of `MabAiDriver` / `MabAiPolicy`. The value is never
   surfaced to the player and the AI no longer produces MIRV designs
   (it falls through to a default doctrine in the new bridge). It
   could be renamed to `HEAVY_CONTROLLER` in a follow-up.
3. **Pre-existing test failures**: `MabInputStateProbe` reports
   `qDoesNotConflictWithDefaultControls=false` on both my branch and
   on the unmodified main branch. Four "required existing probes"
   (`MabKeyWizardLayoutProbe`, `MabLocalPvpDraftProbe`,
   `MabLocalPvpActiveDoctrineProbe`, `MabLocalPvpLifecycleProbe`) are
   listed in the spec but do not exist in the repository. None of
   these are caused by the Nuke Builder integration work.
4. **Educational dialog**: live MAB never reads from the educational
   `com.tetris.model.nuke.NukeDesign` directly, but the dialog itself
   still uses the historical text. This is intentional — the dialog
   is the educational sandbox. Only the bridge output reaches live
   gameplay.

---

## 25. Recommended next step

Add a builder/redesign-time preview card that renders the live
battle-shell warhead line for the design currently being authored, so
players can see in advance how their build will read on the tactical
strip. The bridge already exposes
`MabNukeBuilderBridge.safeGameplaySummary`; the layout would
construct it from a builder snapshot on every edit.

A second follow-up would be to rename
`MabAiArchetype.MIRV_CONTROLLER` to `HEAVY_CONTROLLER` and adjust
the AI driver / policy switch arms, removing the last internal MIRV
reference.

---

## 26. Acceptance criteria coverage

| Criterion | Status |
|---|---|
| Project compiles with EXITCODE=0 | ✅ |
| Nuke Builder is treated as integral to MAB | ✅ |
| Builder-derived design changes live match behavior | ✅ — `MabNukeBuilderLiveIntegrationProbe` |
| Different designs → different charge requirements | ✅ — `designsHaveDifferentCharge=true` |
| Different designs → different launch countdowns | ✅ — `differentCountdowns=true` |
| Different designs → different impact profiles | ✅ — `MabImpactProfileSeparationProbe` |
| DEFCON scales deployment tempo | ✅ — `MabDefconTempoProbe.chargeRequirementScales / launchCountdownScales` |
| DEFCON does not change raw damage ratings | ✅ — `damageRatingsInvariant=true` |
| Radiation creates live delayed/messy board pressure | ✅ — `MabRadiationImpactProbe` |
| EMP is strategic disruption, not radar | ✅ — `MabEmpPayloadProbe.empNoRadarWarningIntel=true` |
| Blast / radiation / EMP / disarm / silo distinct | ✅ — `MabImpactProfileSeparationProbe.profilesDistinct=true` |
| NukeBuildState uses design-specific charge requirement | ✅ |
| Full charge / armed state design-specific | ✅ |
| Launch route requirements design-aware | ✅ — `MabDesignRouteRequirementProbe` |
| Intercept difficulty design-aware | ✅ — `MabDesignInterceptProbe` |
| Live battle shell shows warhead profile | ✅ — `MabLiveWarheadStatusLayoutProbe` |
| PvE setup visibly requires/uses warhead design | ✅ — `MabNukeBuilderMatchSetupProbe.pveAppliesPlayerDesign=true` |
| Local PvP setup visibly requires/uses warhead designs | ✅ — `pvpAppliesEachPlayer=true` |
| No player-facing MIRV / radar / warning / intel / decoy text | ✅ — `MabTerminologyProbe` |
| No unsafe engineering-level gameplay summary | ✅ — `summariesSafe` + `alertsSafe` |
| Normal Tetris still works | ✅ — engine package untouched |
| MAB PvE still works | ✅ — simulations pass |
| MAB local PvP still works | ✅ — `MabLocalPvpProbe` passes |
| Startup key calibration still works | ✅ — `MabControlMappingProbe` passes |
| Local PvP input remains isolated | ✅ — `MabLocalPvpProbe` |
| Shared deterministic piece sequence remains fair | ✅ — `MabSharedPieceSequenceProbe` |
| Upgrade draft still works | ✅ — `MabUpgradeDraftProbe` |
| Active commands remain minor | ✅ — `MabActiveDoctrineProbe` |
| All new probes return `success=true` | ✅ |
| Existing probes remain `success=true` | ✅ (except pre-existing `MabInputStateProbe`) |
| Simulations return `success=true` / `invariantFailures=0` | ✅ |
| Manual verification documented | ✅ (this file) |
| No networking or online multiplayer code added | ✅ |
| `NukeBuilderMabIntegration.md` documents implementation | ✅ |
