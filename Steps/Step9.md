# Step 9 — First Real Upgrade System

This step lands the **upgrade economy + selection flow + first
gameplay-affecting upgrade effects** on top of the Step 1–8 strategy
layer. No UI, no AI upgrade decisions, no online MP, no
second-strike/dead-hand execution, no radar/decoy/treaty effects yet.

---

## 1. New package: `com.tetris.mab.upgrade`

| File | Role |
| ---- | ---- |
| `UpgradeCategory.java`           | 8-value enum grouping upgrades by domain. |
| `UpgradeType.java`               | 27-value enum naming every concrete upgrade. |
| `UpgradeDefinition.java`         | Immutable record: id, name, category, max level, base cost, DEFCON gating, prerequisites. |
| `UpgradeApplicationResult.java`  | Outcome record (success/failed factories). |
| `UpgradeChoiceSet.java`          | Per-participant snapshot of available choices. |
| `UpgradeRegistry.java`           | `createDefault()` seeds all 27 default definitions and provides `getAvailableFor`. |
| `NukeRedesignRetentionRules.java`| Pure helper for suggested charge retention when redesigning. |

## 2. `UpgradeCategory` (8 values)

```
NUKE_DESIGN, LAUNCH_SYSTEMS, MISSILE_SYSTEMS, WARNING_RADAR,
SILO_SYSTEMS, DEFENSE, MAD_SYSTEMS, TEMPO_SYSTEMS
```

## 3. `UpgradeType` (27 values, by category)

Silo systems: `HARDENED_SILO, DEEP_BUNKER, DISTRIBUTED_STOCKPILE,
RAPID_ASSEMBLY_LINE, SECURE_LAUNCH_CHAIN, BLAST_DOORS, SILO_CAMOUFLAGE`

Defense: `SHELTERS, GARBAGE_CONTROL, EMERGENCY_PROTOCOLS, INTERCEPT_CREWS`

Launch: `RAPID_LAUNCH_DRILLS, SECURE_AUTHORIZATION, COUNTDOWN_AUTOMATION`

Radar/warning: `EARLY_WARNING_RADAR, SIGNAL_ANALYSIS, THREAT_TRACKING`

Tempo: `BUILD_EFFICIENCY, LINE_CLEAR_LOGISTICS`

Nuke design: `WARHEAD_REFINEMENT, CLEANER_FUSION,
DIRTY_PAYLOAD_ENGINEERING, PENETRATION_PACKAGE`

MAD: `SECOND_STRIKE_DOCTRINE_I, SECOND_STRIKE_DOCTRINE_II,
DEAD_HAND_PROTOCOL, ASSURED_RETALIATION`

## 4. `UpgradeDefinition` record

Compact-constructor validates: non-null type/displayName/category, `maxLevel ≥ 1`,
`baseCost ≥ 0`, `requiredDefconMaximum ∈ [1,5]`. Prerequisites copied
defensively. The runtime cost for the next level is
`baseCost + currentLevel`.

## 5. `UpgradeApplicationResult`

Factory `success(type, newLevel, costPaid, message)` and
`failed(type, message)`.

## 6. `UpgradeChoiceSet`

Snapshot of `(participantId, defconLevel, availableUpgradePoints,
choices)`. `choices` is defensively copied as an unmodifiable list.

## 7. `UpgradeRegistry.createDefault()`

Seeds every default definition listed in section 3. `getAvailableFor`
filters by:
- `defconLevel ≤ definition.requiredDefconMaximum`
- `!state.isMaxed(definition)`
- `state.hasPrerequisites(definition)`

(Affordability is NOT filtered — UI decides whether to grey out.)

## 8. `NukeRedesignRetentionRules`

Pure helper. Returns a suggested retention ratio for keeping accumulated
build charge across a redesign:

| Scenario | Ratio |
| -------- | ----- |
| Same id                                    | 1.00 |
| Same doctrine, same size                   | 1.00 |
| Same doctrine, larger size                 | 0.90 |
| Same doctrine, smaller / equal             | 1.00 |
| Different doctrine                         | 0.70 |
| Switch to/from `DECOY_PACKAGE`             | 0.75 |
| Switch to `CONCRETE_BLASTER` / `EMP_PAYLOAD` / `DOOMSDAY` | 0.60 |

## 9. `UpgradeState` — full replacement

Replaced the placeholder. Now holds:

- `int upgradePoints`
- `int totalUpgradePointsEarned`
- `EnumMap<UpgradeType, Integer> levels`
- `LinkedList<UpgradeType> recentlyApplied` (trimmed to 10)

API: `getLevel`, `getLevels` (unmodifiable view), `getRecentlyApplied`,
`getTotalLevels`, `addUpgradePoints`, `canAfford`, `hasPrerequisites`,
`isMaxed`, `costForNextLevel`, `applyUpgrade(definition)`,
`toDebugString`. `applyUpgrade` enforces max-level, prerequisites,
affordability; on success it deducts `cost = baseCost + currentLevel`,
bumps the level, and adds to the recently-applied ring.

## 10. `SiloState` — extended

Added `deepBunkerLevel` and `blastDoorLevel` (with clamp-0..5
setters); existing levels also clamped via `clampLevel`. Debug string is
expanded to:

```
Silo{HP=92 DAMAGED hard=1 bunker=0 distributed=1 security=0 assembly=0 blastDoors=0 camo=0}
```

## 11. `CivilDefenseState` — upgrade-aware

Added `shelterLevel`, `garbageControlLevel`, `emergencyProtocolLevel`
(0..5 with setters). `consumeForImpact()` now boosts the chosen base
profile via `applyUpgradeBoosts`:

- `+0.05 × shelterLevel` to blast and radiation reduction (clamped at
  the existing `0.9` mitigation cap).
- `+1 immediateGarbageReduction` once `garbageControlLevel ≥ 2`.
- `+garbageControlLevel extraGraceRows`.

Debug string now reports `shelter=… gc=… emerg=…`. `EmergencyProtocols`
level is currently a placeholder for future stronger emergency
mitigation.

## 12. `ImpactResult` — two new fields

Added `siloUpgradeDisarmReduced` and `siloUpgradeDamageReduced`.
`ImpactResult.skipped(...)` updated to pass `0, 0`.

## 13. `ImpactResolver` — silo-upgrade mitigation

After intercept and civil-defense reductions, a new step applies silo
upgrade mitigation locally before disarm/silo damage is committed:

| Upgrade level field | Affects | Per-level reduction | Cap |
| ------------------- | ------- | ------------------- | --- |
| `hardeningLevel`           | Disarm | 10% | 30% |
| `distributedStockpileLevel`| Disarm | 15% | 30% |
| `deepBunkerLevel`          | Silo damage | 15% | 30% |
| `blastDoorLevel`           | Silo damage | 10% | 20% |

Reductions are stacked multiplicatively. Deltas are emitted in
`ImpactResult.siloUpgradeDisarmReduced` / `siloUpgradeDamageReduced`.

## 14. `MutuallyAssuredBlocksMatch` — wiring

- New imports: upgrade package, `BuilderNukeSpec`, `NukeBuilderAdapter`,
  `HashSet/Set`.
- Fields: `UpgradeRegistry upgradeRegistry`, `NukeBuilderAdapter
  nukeBuilderAdapter`, `Set<Integer> awardedDefconLevels`,
  per-player `playerAUpgradeLineMilestone` /
  `playerBUpgradeLineMilestone`, constant `LINE_CLEAR_UPGRADE_INTERVAL = 20`.

### 14.1 BUILD_EFFICIENCY hook

In the line-clear handler, `+min(buildEfficiencyLevel, lines.count)`
extra build charge is added when the participant has any level of
`BUILD_EFFICIENCY`. Bonus reported in `LINES_CLEARED.meta.buildEffBonus`.

### 14.2 Upgrade-point earning

- **Line-clear milestones** (`UPGRADE_POINT_EARNED`): every 20 lines
  cleared awards +1 upgrade point to that participant. Tracked
  separately per player via `maybeAwardLineClearUpgradePoints`.
- **DEFCON transitions** (`UPGRADE_POINTS_EARNED_GLOBAL_DEFCON`): the
  first time the match crosses to DEFCON 4/3/2 awards `+1` to BOTH
  players; first crossing to DEFCON 1 awards `+2`. Crossings that skip
  intermediate levels still award each crossed level once. Guarded by
  `awardedDefconLevels`.

### 14.3 Public upgrade API

- `openUpgradePause(reason)` / `closeUpgradePause(reason)`: guarded
  wrappers around the existing `enterUpgradePause` / `exitUpgradePause`
  that emit `UPGRADE_PAUSE_OPENED` / `_CLOSED` (or `_OPEN_REJECTED` /
  `_CLOSE_REJECTED`).
- `getUpgradeChoices(participantId)`: returns an `UpgradeChoiceSet`
  computed from the current DEFCON level.
- `applyUpgrade(participantId, type)`: rejected when not in
  `UPGRADE_PAUSE` (`UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE`); rejected on
  unknown type / DEFCON gate / state-side validation
  (`UPGRADE_APPLY_REJECTED`); on success emits `UPGRADE_APPLIED` and
  calls `applyUpgradeSideEffects` which emits
  `UPGRADE_SIDE_EFFECT_APPLIED`. Side-effect switch hits the silo and
  civil-defense level setters; tempo / nuke / MAD / radar levels are
  read at their use sites instead.
- `redesignNukeDuringUpgradePause(id, NukeDesign, ratio)`: requires
  `UPGRADE_PAUSE` and a non-null design; calls
  `NukeBuildState.redesign(...)`; emits `NUKE_REDESIGNED` or
  `NUKE_REDESIGN_REJECTED`.
- `redesignNukeFromBuilderSpecDuringUpgradePause(id, BuilderNukeSpec)`:
  converts via `NukeBuilderAdapter.fromBuilderSpec`, computes the
  retention ratio with `NukeRedesignRetentionRules`, then forwards to
  the `NukeDesign` overload.

### 14.4 Silo-upgrade mitigation log

After the existing `CIVIL_DEFENSE_MITIGATION_APPLIED` log, a new
`SILO_UPGRADE_MITIGATION_APPLIED` event is emitted whenever
`siloUpgradeDisarmReduced > 0 || siloUpgradeDamageReduced > 0`.

## 15. `MatchDebugSnapshot.ParticipantSummary`

Added `upgradePoints, totalUpgradePointsEarned, upgradeCount,
recentUpgradeSummary, siloUpgradeLevels,
civilDefenseUpgradeLevels`. Helpers `summarizeRecentUpgrades`,
`summarizeSiloUpgrades`, `summarizeCivilDefenseUpgrades` keep the
record self-contained.

## 16. `ParticipantState.toDebugString`

Now appends `" " + upgradeState.toDebugString()`, e.g.
`Upgrade{points=2 total=5 levels=HARDENED_SILO:1,SHELTERS:2}`.

## 17. New log events

| Event | When |
| ----- | ---- |
| `UPGRADE_POINT_EARNED`               | Line-clear milestone hit. |
| `UPGRADE_POINTS_EARNED_GLOBAL_DEFCON`| First time DEFCON 4/3/2/1 reached. |
| `UPGRADE_PAUSE_OPENED`               | `openUpgradePause` succeeds. |
| `UPGRADE_PAUSE_OPEN_REJECTED`        | Wrong phase. |
| `UPGRADE_PAUSE_CLOSED`               | `closeUpgradePause` succeeds. |
| `UPGRADE_PAUSE_CLOSE_REJECTED`       | Not currently in upgrade pause. |
| `UPGRADE_APPLIED`                    | Upgrade applied + level bumped. |
| `UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE`| Apply attempted outside `UPGRADE_PAUSE`. |
| `UPGRADE_APPLY_REJECTED`             | Definition or state-level rejection. |
| `UPGRADE_SIDE_EFFECT_APPLIED`        | Silo/CD setter applied. |
| `NUKE_REDESIGNED`                    | Nuke redesign during pause succeeded. |
| `NUKE_REDESIGN_REJECTED`             | Wrong phase or null input. |
| `SILO_UPGRADE_MITIGATION_APPLIED`    | Silo-level damage reductions applied. |

## 18. Things explicitly NOT implemented

- Upgrade UI (selection screen, animations, panels).
- AI-driven upgrade decisions.
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)
- Second-strike or dead-hand automatic execution.
- Radar / scan / threat-tracking gameplay effects.
- Decoy / camouflage gameplay effects.
- Treaty / restraint / MAD bonuses (still placeholder definitions).

## 19. Build verification

```
javac -d target\classes-step9 -encoding UTF-8 (all src\main\java sources)
EXITCODE=0
```

The build is clean. The Tetris engine (`com.tetris.model.*` and
`com.tetris.view.*`) is untouched.
