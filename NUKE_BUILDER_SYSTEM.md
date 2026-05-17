# Nuke Builder System — Complete Technical Reference

## Table of Contents

1. [Overview & Architecture Philosophy](#1-overview--architecture-philosophy)
2. [Layer 1 — Educational Builder Model](#2-layer-1--educational-builder-model)
   - 2.1 [NukePart.java](#21-nukepartjava)
   - 2.2 [NukeSlot.java](#22-nukeslotjava)
   - 2.3 [NukeDesign.java (educational)](#23-nukedesignjava-educational)
3. [Layer 2 — Strategic MAB Schema](#3-layer-2--strategic-mab-schema)
   - 3.1 [NukeDoctrineType.java](#31-nukedoctrinetypejava)
   - 3.2 [NukeSizeCategory.java](#32-nukesizecategoryjava)
   - 3.3 [NukeDesign.java (strategic)](#33-nukedesignjava-strategic)
   - 3.4 [Profile Records](#34-profile-records)
   - 3.5 [NukeDesignFactory.java](#35-nukedesignfactoryjava)
   - 3.6 [NukeReadinessScaler.java](#36-nukereadinessscalerjava)
   - 3.7 [BuilderNukeSpec.java](#37-buildernukespecdto)
4. [Layer 3 — Bridge & Conversion](#4-layer-3--bridge--conversion)
   - 4.1 [MabNukeBuilderBridge.java](#41-mabnukebuilderbridge)
   - 4.2 [NukeBuilderAdapter.java](#42-nukebuilderadapter)
5. [Game State Integration](#5-game-state-integration)
   - 5.1 [NukeBuildState.java](#51-nukebuildstatejava)
   - 5.2 [ParticipantState.java](#52-participantstatejava)
   - 5.3 [DefconState.java](#53-defconstatejava)
   - 5.4 [NukeRedesignRetentionRules.java](#54-nukeredesignretentionrulesjava)
6. [Launch & Impact Mechanics](#6-launch--impact-mechanics)
   - 6.1 [ActiveLaunchState.java](#61-activelaunchstatejava)
   - 6.2 [IncomingThreatState.java](#62-incomingthreatstatejava)
   - 6.3 [ImpactResolver.java](#63-impactresolverjava)
   - 6.4 [ImpactResult.java](#64-impactresultjava)
7. [Launch Authorization — Action Codes](#7-launch-authorization--action-codes)
8. [UI Components](#8-ui-components)
   - 8.1 [NukeBuilderDialog.java (Educational Builder UI)](#81-nukebuilderdialog)
   - 8.2 [MabNukeDesignSelection.java](#82-mabnukedesignselection)
   - 8.3 [MabNukeRedesignPanel.java](#83-mabnukeredesignpanel)
   - 8.4 [MabNukePresetDefinition.java](#84-mabnukepresetdefinition)
9. [MAB Match Coordinator Integration](#9-mab-match-coordinator-integration)
10. [Complete Data Flow Diagrams](#10-complete-data-flow-diagrams)
11. [DEFCON Scaling Rules](#11-defcon-scaling-rules)
12. [Design Preset Ladder](#12-design-preset-ladder)
13. [Safety & Censorship System](#13-safety--censorship-system)
14. [Testing & Validation](#14-testing--validation)

---

## 1. Overview & Architecture Philosophy

The nuke builder system is a two-layer architecture that deliberately separates **educational physics modelling** from **strategic gameplay mechanics**. These two layers communicate only through a narrow bridge/adapter pair, ensuring that engineering-level details never leak into the game UI or affect gameplay values directly.

```
┌──────────────────────────────────────────────────────────────────────┐
│  LAYER 1 — Educational Builder                                       │
│  com.tetris.model.nuke                                               │
│  NukePart, NukeSlot, NukeDesign (educational)                        │
│  Purpose: KSP-style modular weapon constructor; physics yield model  │
└──────────────────────┬───────────────────────────────────────────────┘
                       │
          MabNukeBuilderBridge  (com.tetris.mab.nuke)
          NukeBuilderAdapter    (com.tetris.mab.nuke)
                       │
┌──────────────────────▼───────────────────────────────────────────────┐
│  LAYER 2 — Strategic MAB Schema                                      │
│  com.tetris.mab.nuke                                                 │
│  NukeDesign (strategic), NukeDoctrineType, NukeSizeCategory,        │
│  NukeDesignFactory, NukeReadinessScaler, profile records             │
│  Purpose: Gameplay ratings, DEFCON tables, launch/impact mechanics   │
└──────────────────────┬───────────────────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────────────────┐
│  GAME STATE LAYER                                                    │
│  NukeBuildState, ParticipantState, DefconState                       │
│  MutuallyAssuredBlocksMatch (coordinator)                            │
│  ActiveLaunchState, IncomingThreatState, ImpactResolver              │
└──────────────────────────────────────────────────────────────────────┘
```

### Core Design Principles

- **Damage ratings are invariant.** No matter what DEFCON level the match is at, a nuke's blast/radiation/disarm/silo ratings never change. Only the *difficulty of deploying* the weapon changes with DEFCON.
- **DEFCON scales deployment difficulty.** Build charge required, launch code length, launch countdown duration, and warning duration all scale with DEFCON level. At high DEFCON (peacetime), everything is harder and slower. At low DEFCON (crisis), everything is faster.
- **Immutability in the strategic layer.** Once a `NukeDesign` (strategic) is constructed by the factory or adapter, it is immutable. All DEFCON-dependent values are pre-computed into maps at construction time.
- **Safe gameplay summaries.** The bridge layer generates a plain-English description of a nuke's effects that is scrubbed of all engineering language before any text reaches the UI.

---

## 2. Layer 1 — Educational Builder Model

**Package**: `com.tetris.model.nuke`

This layer models a nuclear weapon as a set of independently chosen subsystem components. It computes an estimated yield in kilotons using multiplicative efficiency factors. All values are pedagogically scaled from publicly available historical sources (Rhodes' *The Making of the Atomic Bomb*, the Nuclear Weapon Archive, declassified documents). No classified information is used or implied.

### 2.1 NukePart.java

Represents a single selectable component within one weapon subsystem slot.

**Fields:**
| Field | Type | Description |
|---|---|---|
| `name` | `String` | Display name of the component (e.g., "Levitated Pit") |
| `shortDescription` | `String` | One-line summary for the parts list |
| `educationalNotes` | `String` | Historical context paragraph shown in info panel |
| `baseYieldKt` | `double` | Base yield contribution in kilotons (TNT equivalent) |
| `efficiencyBonus` | `double` | Multiplicative factor applied to total yield when this part is selected |
| `complexity` | `int` | Engineering difficulty score (affects compatibility warnings) |

**Special static field:** `NukePart.NONE` — a singleton representing an unselected slot. Selecting NONE in a slot contributes zero yield and zero efficiency bonus.

**Usage pattern:** NukePart objects are never constructed by the player at runtime. They exist as immutable constants inside the NukeSlot catalog. The player's action is simply to *select* which NukePart to use in each slot.

---

### 2.2 NukeSlot.java

Represents a single subsystem category of the weapon (e.g., "Fissile Material", "Tamper/Reflector"). Each slot owns an immutable ordered list of `NukePart` options that the player can choose from.

**Static catalog — 11 slots defined:**

| Slot ID | Display Name | What it models |
|---|---|---|
| `CONFIGURATION` | Weapon Configuration | Gun-type vs. implosion geometry; pure fission vs. boosted vs. staged |
| `FISSILE` | Fissile Core | Primary fissionable material (HEU vs. Pu) |
| `TAMPER` | Tamper / Reflector | Neutron reflector and inertial confinement shell |
| `INITIATOR` | Initiator | Neutron source that triggers the chain reaction |
| `IMPLOSION` | Implosion Lens System | Explosive lens geometry for symmetric compression |
| `BOOST` | Boosting System | Fusion gas injection for boosted yield |
| `SECONDARY` | Thermonuclear Secondary | Teller-Ulam secondary stage (only relevant in staged configurations) |
| `CASING` | Weapon Casing | Outer shell; affects delivery profile and radiation hardening |
| `FUZE` | Fuzing System | Airburst altitude vs. ground burst vs. delayed fuze |
| `SAFETY` | Safety & Arming | PAL / environmental sensing; affects launch code complexity |
| `DELIVERY` | Delivery Vehicle | Ballistic missile, cruise, gravity bomb, MIRV bus |

**Accessors:**
- `getId()` → `String`
- `getName()` → `String`
- `getDescription()` → `String` (paragraph describing what this subsystem does)
- `getOptions()` → `List<NukePart>` (immutable)

---

### 2.3 NukeDesign.java (Educational)

The player's mutable work-in-progress selection across all NukeSlots. This is the "document" that the educational builder UI edits.

**Core state:**

| Field | Type | Description |
|---|---|---|
| `selections` | `Map<NukeSlot, NukePart>` | Current part choice per slot |
| `fusionPusher` | `String[3]` | Per-stage pusher material: `"U-238"`, `"Lead"`, or `"Tungsten"` |
| `fusionChannelFiller` | `String[3]` | Per-stage radiation channel filler: `"Polystyrene foam"` or `"Vacuum"` |
| `fusionSparkPlug` | `boolean[3]` | Whether each stage has a fissile spark plug; default: only stage 0 true |
| `fusionStageCount` | `int` | 1 = 2-stage design, 2 = 3-stage, 3 = 4-stage |

**Key computed methods:**

- `getEstimatedYieldKt()` → `double`
  Multiplies all selected parts' `efficiencyBonus` values together, applies `baseYieldKt` from the fissile selection, and applies fusion stage multipliers. Returns total estimated yield in kilotons.

- `getWarnings()` → `List<String>`
  Scans all selected parts for incompatible combinations. Examples:
  - *Hard fizzle*: implosion lens selected with gun-type configuration → "Implosion lenses are meaningless in a gun-type design."
  - *Soft fizzle*: SECONDARY slot selected but CONFIGURATION is pure fission → "Secondary has no primary to be driven by."
  - *Synergies*: boosting + implosion → no warning (compatible).
  - *Anti-synergies*: high-complexity implosion + low-complexity initiator → "Initiator complexity insufficient for chosen lens geometry."

- `getHistoricalAnalog()` → `String`
  Pattern-matches the current selection to known historical weapons (Little Boy, Fat Man, Ivy Mike, Castle Bravo, W87, B83, etc.) and returns a display name plus brief historical note.

- `getEffectsSummary()` → `String`
  Produces a text block estimating approximate damage radii (fireball, overpressure, thermal, radiation) based on computed yield. Uses the standard Glasstone & Dolan scaling laws from *The Effects of Nuclear Weapons*.

---

## 3. Layer 2 — Strategic MAB Schema

**Package**: `com.tetris.mab.nuke`

This layer defines how nukes behave inside the MAB game: what kind of damage they deal, how hard they are to build and launch, and how DEFCON readiness modifies those values. Nothing in this layer uses kilotons, isotopes, or engineering language.

### 3.1 NukeDoctrineType.java

An enum that categorizes the weapon's broad strategic role. The doctrine type is the most important classifier — it determines which damage profiles are active and which action code the launch requires.

| Enum Value | Strategic Role |
|---|---|
| `CLEAN_FUSION` | Maximum blast, minimal radiation. Counterforce or city-busting. |
| `DIRTY_BOMB` | Moderate blast, heavy radiation. Area denial and long-term contamination pressure. |
| `SALTED_WARHEAD` | High area contamination; maximizes radiological garbage profile. |
| `CONCRETE_BLASTER` | Penetrating warhead; high disarm and silo damage, lower blast radius. |
| `EMP_PAYLOAD` | Electromagnetic pulse delivery; disrupts opponent's radar and warning timers rather than sending garbage. |
| `MIRV` | Multiple Independent Reentry Vehicle; sends garbage in multiple waves at intervals. |
| `BUNKER_BUSTER` | Earth-penetrating warhead; exceptional silo damage, moderate disarm. |
| `DECOY_PACKAGE` | Creates false launch signatures, fake doctrine readings, or dummy silo heat on the opponent's radar. |
| `DOOMSDAY` | Maximum across all damage categories; extremely high build charge requirement. |
| `PLACEHOLDER` | Default fallback used during match setup before the player has selected a design. |

**Parsing:** `NukeDoctrineType.fromString(String)` is a lenient parser that normalizes case, spaces, hyphens, and underscores before matching. Unrecognized input falls back to `PLACEHOLDER`.

---

### 3.2 NukeSizeCategory.java

Classifies the weapon's overall scale, used by `NukeReadinessScaler` to determine DEFCON multiplier tables.

| Enum Value | Approximate Scale |
|---|---|
| `MICRO` | Sub-kiloton; small tactical device |
| `TACTICAL` | 1–10 kt range; battlefield weapon |
| `THEATER` | 10–100 kt range; regional strike |
| `STRATEGIC` | 100 kt–1 Mt range; intercontinental |
| `SUPERHEAVY` | Multi-megaton; area denial |
| `DOOMSDAY_SCALE` | Tens of megatons; game-ending weapon |

**Factory method:** `NukeSizeCategory.fromBuildCharge(int charge)` maps a build charge value to the most appropriate size band. This is used when the adapter needs to infer size from a builder spec rather than from an explicit factory preset.

---

### 3.3 NukeDesign.java (Strategic)

The central immutable record of a weapon's strategic characteristics. This is what `NukeBuildState` holds, what `ActiveLaunchState` copies at launch time, and what `ImpactResolver` reads when applying damage.

**Immutable identity fields:**

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique design identifier (e.g., `"clean_fusion_default"`) |
| `displayName` | `String` | Player-facing name shown in UI |
| `doctrineType` | `NukeDoctrineType` | Broad strategic role |
| `sizeCategory` | `NukeSizeCategory` | Scale band |

**Invariant damage ratings (never change with DEFCON):**

| Field | Type | Description |
|---|---|---|
| `blastRating` | `int` | Governs immediate garbage lines and pressure on opponent's board |
| `radiationRating` | `int` | Governs delayed radiation-patterned garbage waves |
| `disarmRating` | `int` | Governs how much of opponent's nuke build charge is destroyed on impact |
| `siloDamageRating` | `int` | Governs how much integrity opponent's SiloState loses on impact |

**Base deployment values (used as fallback if DEFCON maps are absent):**

| Field | Type | Description |
|---|---|---|
| `baseBuildChargeRequired` | `int` | Charge units needed to arm the weapon at the reference DEFCON level |
| `baseLaunchTimePieces` | `int` | Countdown duration in Tetris pieces |
| `baseWarningTimePieces` | `int` | Defender warning duration in Tetris pieces |
| `detectionProfile` | `String` | How visible this launch is to opponent radar (`"LOW"`, `"MEDIUM"`, `"HIGH"`) |
| `baseLaunchCode` | `List<Integer>` | Reference launch code sequence |

**DEFCON-keyed deployment maps (pre-computed at construction):**

| Map | Key | Value |
|---|---|---|
| `buildChargeByDefcon` | `int` (1–5) | Build charge required at that DEFCON level |
| `launchCodeByDefcon` | `int` (1–5) | Launch code sequence at that DEFCON level |
| `launchTimeByDefcon` | `int` (1–5) | Countdown duration in pieces at that DEFCON level |
| `warningTimeByDefcon` | `int` (1–5) | Warning duration in pieces at that DEFCON level |

**Effective value accessors (read-path used by game logic):**

```java
int effectiveBuildChargeRequired(int defconLevel)
List<Integer> effectiveLaunchCode(int defconLevel)
int effectiveLaunchTimePieces(int defconLevel)
int effectiveWarningTimePieces(int defconLevel)
```

Each of these first checks the corresponding DEFCON map; if the map is absent or the level is not in the map, falls back to the base field.

**Profile objects (optional, null if doctrine doesn't use them):**

| Field | Type | When present |
|---|---|---|
| `garbageProfile` | `GarbageProfile` | Always present |
| `disarmProfile` | `DisarmProfile` | Always present |
| `siloDamageProfile` | `SiloDamageProfile` | Always present |
| `mirvProfile` | `MirvProfile` | Only when `doctrineType == MIRV` |
| `empProfile` | `EmpProfile` | Only when `doctrineType == EMP_PAYLOAD` |
| `decoyProfile` | `DecoyProfile` | Only when `doctrineType == DECOY_PACKAGE` |
| `fullClearThresholdProfile` | `FullClearThresholdProfile` | Always present |

---

### 3.4 Profile Records

All profile objects are Java records (immutable value types).

#### GarbageProfile

Controls how garbage is delivered to the defender's Tetris board.

| Field | Type | Description |
|---|---|---|
| `baseGarbageLines` | `int` | Total garbage lines to deliver |
| `maxImmediateLines` | `int` | Cap on lines sent in the first wave (rest are delayed) |
| `usesWaves` | `boolean` | Whether remaining lines are delivered in periodic waves |
| `waveCount` | `int` | Number of subsequent waves |
| `piecesBetweenWaves` | `int` | Tetris piece interval between waves |
| `targetedGarbage` | `boolean` | Whether garbage is radiation-patterned (asymmetric holes) |

#### DisarmProfile

Controls how the impact damages the defender's nuke build charge.

| Field | Type | Description |
|---|---|---|
| `disarmPower` | `int` | Base disarm damage in charge units |
| `fullClearRequiredRatio` | `double` | Fraction of board that must be cleared to negate disarm |
| `canFullClear` | `boolean` | Whether a full clear can completely negate the disarm |
| `smallBombFullClearBlocked` | `boolean` | Whether small bombs ignore even a full clear |

#### SiloDamageProfile

Controls how the impact damages the defender's silo structure.

| Field | Type | Description |
|---|---|---|
| `siloDamagePower` | `int` | Integrity damage dealt to SiloState |
| `canDamageSilo` | `boolean` | Whether this weapon type can damage silos at all |
| `targetsHardening` | `boolean` | Whether damage bypasses some silo passive hardening |
| `targetsLaunchSecurity` | `boolean` | Whether damage degrades the defender's launch authorization |

#### MirvProfile

Present only on MIRV-doctrine weapons. Defines the multi-wave delivery pattern.

| Field | Type | Description |
|---|---|---|
| `waveCount` | `int` | Number of independent reentry vehicles (garbage wave groups) |
| `linesPerWave` | `int` | Garbage lines per wave |
| `piecesBetweenWaves` | `int` | Piece interval between successive reentry vehicles |

#### EmpProfile

Present only on EMP_PAYLOAD weapons. Disrupts opponent systems rather than sending garbage.

| Field | Type | Description |
|---|---|---|
| `radarDisruptionPieces` | `int` | How many pieces the opponent's radar display is blinded |
| `warningDisruptionPieces` | `int` | How many pieces the opponent's incoming warning display is suppressed |
| `canFullClearWithUpgradeOnly` | `boolean` | Whether a full clear negates EMP effects (only with specific upgrade) |

#### DecoyProfile

Present only on DECOY_PACKAGE weapons. Creates false information on opponent radar.

| Field | Type | Description |
|---|---|---|
| `createsFakeLaunch` | `boolean` | Whether a phantom incoming threat appears on defender's panel |
| `createsFalseDoctrine` | `boolean` | Whether the fake launch shows a randomized false doctrine |
| `createsDummySiloHeat` | `boolean` | Whether the opponent's silo heat display shows false positive |
| `decoyDurationPieces` | `int` | How long the false information persists |

#### FullClearThresholdProfile

Defines how many rows must be cleared (as a fraction of total board height) to qualify for a "full clear" defensive bonus. Different doctrines have different thresholds.

| Field | Type | Description |
|---|---|---|
| `normalRequiredRatio` | `double` | Threshold for most doctrine types |
| `strategicRequiredRatio` | `double` | Threshold when facing a STRATEGIC-size weapon |
| `concreteBlasterRequiredRatio` | `double` | Threshold when facing CONCRETE_BLASTER |
| `bunkerBusterRequiredRatio` | `double` | Threshold when facing BUNKER_BUSTER |
| `doomsdayRequiredRatio` | `double` | Threshold when facing DOOMSDAY |

---

### 3.5 NukeDesignFactory.java

Provides static factory methods that hand-tune the complete strategic NukeDesign for each of the 8 preset weapon archetypes. Each factory method explicitly sets every DEFCON map rather than relying on `NukeReadinessScaler`, giving designers full control over game balance.

**Factory methods:**

| Method | Doctrine | Size | Base Build Charge | Blast | Rad | Disarm | Silo |
|---|---|---|---|---|---|---|---|
| `createDefaultPlaceholder()` | PLACEHOLDER | TACTICAL | 22 | 2 | 1 | 1 | 0 |
| `createDefaultCleanFusion()` | CLEAN_FUSION | STRATEGIC | 60 | 5 | 1 | 3 | 1 |
| `createDefaultDirtyBomb()` | DIRTY_BOMB | TACTICAL | 25 | 2 | 4 | 1 | 0 |
| `createDefaultSaltedWarhead()` | SALTED_WARHEAD | THEATER | ~35 | 2 | 5 | 1 | 0 |
| `createDefaultConcreteBlaster()` | CONCRETE_BLASTER | STRATEGIC | 56 | 2 | 1 | 6 | 5 |
| `createDefaultMirv()` | MIRV | STRATEGIC | 58 | 5 | 2 | 2 | 1 |
| `createDefaultEmp()` | EMP_PAYLOAD | THEATER | 36 | 1 | 2 | 3 | 2 |
| `createDefaultBunkerBuster()` | BUNKER_BUSTER | THEATER | 44 | 3 | 0 | 5 | 5 |
| `createDefaultDoomsday()` | DOOMSDAY | DOOMSDAY_SCALE | 150 | 8 | 3 | 8 | 8 |

Each factory method constructs:
1. All four damage ratings
2. All four DEFCON maps (levels 1–5) for build charge, launch code, launch time, and warning time
3. All relevant profile objects (`GarbageProfile`, `DisarmProfile`, `SiloDamageProfile`, and doctrine-specific profiles)
4. The `FullClearThresholdProfile`

---

### 3.6 NukeReadinessScaler.java

A generic helper used when a weapon design is generated by the adapter (not by the hand-tuned factory) and needs DEFCON scaling tables computed algorithmically.

**Per-DEFCON build charge multipliers (example for STRATEGIC size):**

| DEFCON | Multiplier | Rationale |
|---|---|---|
| 5 (peacetime) | 1.40× | Full authorization chain required |
| 4 | 1.20× | Elevated readiness, some shortcuts |
| 3 | 1.00× | Reference level |
| 2 | 0.88× | Pre-war shortcuts activated |
| 1 (war imminent) | 0.74× | Emergency procedures |

Each size band has its own multiplier table — MICRO weapons are affected less by DEFCON changes than STRATEGIC or DOOMSDAY weapons.

**Launch code scaling:**

The launch code is a `List<Integer>` (a sequence of values the player must input). At high DEFCON, the list is longer (more authorization steps). `launchCode(baseCode, size)` returns DEFCON-keyed maps where the code length is adjusted by `±delta` per level.

**Warning and launch time scaling:**

Both countdown and warning time scale similarly — longer at high DEFCON, shorter at low DEFCON.

---

### 3.7 BuilderNukeSpec.java (DTO)

A lightweight, neutral data transfer object that carries a weapon specification across the bridge between the educational builder and the strategic schema. It has no dependencies on either layer — no UI types, no game engine references, no Swing imports.

**Fields (all via record components):**

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique design identifier |
| `name` | `String` | Display name |
| `doctrineType` | `String` | Doctrine type as a plain string (parsed by `NukeDoctrineType.fromString()`) |
| `size` | `int` | Numeric size score (0–10 scale, mapped to `NukeSizeCategory`) |
| `blast` | `int` | Blast damage rating (0–10 scale) |
| `radiation` | `int` | Radiation damage rating |
| `disarm` | `int` | Disarm power rating |
| `siloDamage` | `int` | Silo damage rating |
| `speed` | `int` | Launch speed (inverse of launch time) |
| `stealth` | `int` | Detection avoidance (maps to detection profile) |
| `mirv` | `boolean` | Whether this is a MIRV-delivery weapon |
| `emp` | `boolean` | Whether this is an EMP payload |
| `decoy` | `boolean` | Whether this is a decoy package |

Because `BuilderNukeSpec` is a Java record, all fields are final and accessible via getter methods matching the field name.

---

## 4. Layer 3 — Bridge & Conversion

This layer translates between the two design spaces. It is the only code allowed to know about both `com.tetris.model.nuke` (educational) and `com.tetris.mab.nuke` (strategic) simultaneously.

### 4.1 MabNukeBuilderBridge

**Full class name**: `com.tetris.mab.nuke.MabNukeBuilderBridge`

Accepts a `com.tetris.model.nuke.NukeDesign` (educational, mutable) and produces gameplay-safe outputs.

**Methods:**

```java
BuilderNukeSpec toBuilderSpec(com.tetris.model.nuke.NukeDesign educationalDesign)
```
Reads the educational design's slot selections and computes:
- **doctrine**: Derived from CONFIGURATION slot (gun-type → clean fusion or dirty bomb, implosion → concrete blaster or MIRV, etc.) and DELIVERY slot (MIRV bus → MIRV doctrine; cruise → EMP or decoy).
- **blast rating**: Derived from FISSILE yield and SECONDARY presence.
- **radiation rating**: Derived from TAMPER material (U-238 → high radiation, Lead → moderate, Tungsten → low).
- **disarm rating**: Derived from FUZE type (airburst → low disarm, ground burst → moderate, penetrating → high).
- **silo damage**: Derived from CASING and DELIVERY (hardened re-entry vehicle → higher silo damage).
- **size**: Derived from `getEstimatedYieldKt()` mapped to a 0–10 scale.
- **speed**: Derived from DELIVERY slot (ICBM → 8, cruise missile → 4, gravity bomb → 2).
- **stealth**: Derived from CASING and DELIVERY (stealth casing → higher stealth).
- **mirv/emp/decoy booleans**: Directly from DELIVERY and SECONDARY slot selections.

```java
com.tetris.mab.nuke.NukeDesign toMabDesign(com.tetris.model.nuke.NukeDesign educationalDesign)
```
Chains through `toBuilderSpec()` then through `NukeBuilderAdapter.fromBuilderSpec()` to produce the final strategic NukeDesign.

```java
String safeGameplaySummary(com.tetris.model.nuke.NukeDesign educationalDesign)
```
Generates a plain-English description of gameplay effects (not physics). Example output: *"This weapon delivers heavy blast pressure in a single concentrated wave, with moderate disarm potential. Minimal radiological residue."* All forbidden terms are replaced with safe paraphrases.

```java
boolean isSafeGameplaySummary(String text)
```
Validates that a candidate summary string contains none of the forbidden engineering terms. Returns false if any blocked term is found.

**Blocked terms list** (partial):
`kg`, `kilogram`, `u-235`, `u-238`, `pu-239`, `plutonium`, `uranium`, `tritium`, `deuterium`, `lithium deuteride`, `lens`, `initiator`, `tamper`, `fissile`, `implosion`, `spherical`, `cylindrical`, `casing`, `fuze`, `enrichment`, `critical mass`, `pit`, `reflector`, `beryllium`, `polonium`, `explosive`.

---

### 4.2 NukeBuilderAdapter

**Full class name**: `com.tetris.mab.nuke.NukeBuilderAdapter`

Converts the neutral `BuilderNukeSpec` DTO into a fully populated strategic `NukeDesign`. This is the piece that actually invokes `NukeReadinessScaler` and constructs all profile objects.

**Primary method:**

```java
NukeDesign fromBuilderSpec(BuilderNukeSpec spec)
```

Step-by-step processing:

1. **Doctrine resolution**: Parse `spec.doctrineType()` via `NukeDoctrineType.fromString()`. If the result is `PLACEHOLDER` or `CLEAN_FUSION` but `spec.mirv()` is true, upgrade doctrine to `MIRV`. If `spec.emp()` is true, upgrade to `EMP_PAYLOAD`. If `spec.decoy()` is true, upgrade to `DECOY_PACKAGE`.

2. **Size and base charge**: Call `NukeSizeCategory.fromBuildCharge(derivedCharge)` where `derivedCharge = spec.size() * 15` (linear scale). This gives the size category.

3. **Damage ratings**: Map spec's 0–10 blast/radiation/disarm/silo to the strategic schema's rating scale.

4. **Timing derivation**:
   - Launch time: `baseLaunchTimePieces = max(8, 20 - spec.speed() * 2)`
   - Warning time: `baseWarningTimePieces = max(4, 12 - spec.stealth() * 1)`

5. **Detection profile**: `spec.stealth() >= 7` → `"LOW"`, `spec.stealth() >= 4` → `"MEDIUM"`, else `"HIGH"`.

6. **Launch code synthesis**: Length is `3 + spec.disarm() / 3` values; each value is derived from blast and silo ratings. The base code is then passed to `NukeReadinessScaler.launchCode()`.

7. **DEFCON maps**: All four maps (build charge, launch code, launch time, warning time) built by `NukeReadinessScaler` using the size category.

8. **Profile construction**: All mandatory profiles always built. Doctrine-specific profiles (`MirvProfile`, `EmpProfile`, `DecoyProfile`) only built when relevant.

**Reverse method:**

```java
BuilderNukeSpec toBuilderSpec(NukeDesign design)
```

Round-trips a strategic design back into a DTO. This is used by `MabNukeRedesignPanel` to re-display a previously saved design.

**Resilience method:**

```java
BuilderNukeSpec fromExistingBuilderObject(Object o)
```

Type-checks the argument; if it is already a `BuilderNukeSpec`, returns it directly. If it is a `NukeDesign` (strategic), calls `toBuilderSpec()`. Otherwise returns a default placeholder spec.

---

## 5. Game State Integration

### 5.1 NukeBuildState.java

**Package**: `com.tetris.mab`

Tracks a single player's progress toward arming their nuke. One instance per `ParticipantState`.

**Fields:**

| Field | Type | Description |
|---|---|---|
| `currentBuildCharge` | `int` | Accumulated charge units toward arming |
| `effectiveBuildChargeRequired` | `int` | DEFCON-adjusted target (refreshed on DEFCON change) |
| `armed` | `boolean` | True when `currentBuildCharge >= effectiveBuildChargeRequired` |
| `currentDesign` | `NukeDesign` | Current weapon design reference (strategic) |
| `overbuiltCharge` | `int` | Charge in excess of `effectiveBuildChargeRequired`; retained across some design changes |

**Methods:**

```java
void setDesign(NukeDesign design, int defconLevel)
```
Sets a new design (replacing the old one completely) and immediately calls `refreshForDefcon()` to recompute the effective requirement. Does not retain any previously accumulated charge — this is used during initial setup only.

```java
void redesign(NukeDesign newDesign, int defconLevel, double retainedChargeRatio)
```
Changes design mid-match (during upgrade pause). Preserves `floor(currentBuildCharge * retainedChargeRatio)` of the old charge, then recomputes armed/overbuilt. The retention ratio is provided by `NukeRedesignRetentionRules`.

```java
void refreshForDefcon(int defconLevel)
```
Called whenever `DefconState` changes. Reads `currentDesign.effectiveBuildChargeRequired(defconLevel)` and updates `effectiveBuildChargeRequired`. Then calls `recomputeArmedAndOverbuilt()`.

```java
void addCharge(int amount)
```
Adds charge units (from line clears or upgrade bonuses). Calls `recomputeArmedAndOverbuilt()` after.

```java
void reduceCharge(int amount)
```
Removes charge units (from disarm damage). Floors at zero. Calls `recomputeArmedAndOverbuilt()`.

```java
void resetCharge()
```
Zeros out both `currentBuildCharge` and `overbuiltCharge` after a launch. The design is retained; the player must rebuild from scratch.

```java
private void recomputeArmedAndOverbuilt()
```
Sets `armed = (currentBuildCharge >= effectiveBuildChargeRequired)`. Sets `overbuiltCharge = max(0, currentBuildCharge - effectiveBuildChargeRequired)`.

**Charge accumulation source:** Line clears in the Tetris engine are forwarded to this state. The number of charge units awarded per line clear depends on the game's difficulty setting and the current upgrade inventory (some upgrades grant bonus charge per tetris/t-spin).

---

### 5.2 ParticipantState.java

Owns all per-player strategic state for one participant in a MAB match. There are exactly two `ParticipantState` instances per match (playerA and playerB).

**Nuke-related fields:**

| Field | Type | Description |
|---|---|---|
| `nukeBuildState` | `NukeBuildState` | Live build progress and current design |
| `nukeDesignSelection` | `MabNukeDesignSelection` | Player's confirmed design selection (wrapper with source tracking) |
| `activeLaunches` | `List<ActiveLaunchState>` | Launches this player has initiated, in any phase |
| `incomingThreats` | `List<IncomingThreatState>` | Threats currently inbound against this player |

**Other strategic state fields** (relevant context):

| Field | Type | Description |
|---|---|---|
| `siloState` | `SiloState` | Silo integrity and hardening; damaged by silo-targeting weapons |
| `upgradeInventory` | `MabUpgradeInventory` | Owned upgrades affecting charge rate, intercept, etc. |
| `actionCodeRegistry` | `ActionCodeRegistry` | Currently active launch codes |
| `radarIntel` | `RadarIntelState` | What this player knows about opponent's silo and nuke |
| `civilDefense` | `CivilDefenseState` | Passive grace period and intercept policies |
| `escalationContribution` | `int` | How much this player has contributed to DEFCON escalation meter |

---

### 5.3 DefconState.java

**Package**: `com.tetris.mab`

A single shared instance per match. Tracks the global DEFCON readiness level and the escalation meter that drives it.

**Fields:**

| Field | Type | Description |
|---|---|---|
| `currentDefcon` | `int` | Current DEFCON level (5 = peacetime, 1 = war imminent) |
| `escalationMeter` | `int` | Accumulated escalation points |

**Escalation thresholds (escalation → DEFCON):**

| Escalation Points | DEFCON Level |
|---|---|
| 0 | 5 |
| ≥ 100 | 4 |
| ≥ 250 | 3 |
| ≥ 500 | 2 |
| ≥ 850 | 1 |

**Method:**

```java
DefconChangeResult addEscalation(int amount, String reason)
```
Adds escalation points. If the new total crosses a threshold, decrements DEFCON and returns a `DefconChangeResult` record containing:
- `oldLevel` — previous DEFCON
- `newLevel` — new DEFCON (if changed)
- `changed` — boolean
- `reason` — the string passed in (logged to the match event log)

**Sources of escalation** (examples):
- A nuke launch: +50 to +120 escalation depending on doctrine
- A successful disarm impact: +20
- A silo strike: +30
- Upgrade selections involving heavy doctrine: +10
- Player reaching DEFCON-1 auto-escalation events

**Effect of DEFCON drop**: When DEFCON decreases, `MutuallyAssuredBlocksMatch` calls `refreshForDefcon()` on both players' `NukeBuildState` instances immediately, so effective build charges and launch codes are updated for both players simultaneously.

---

### 5.4 NukeRedesignRetentionRules.java

**Package**: `com.tetris.mab`

A pure utility class (no mutable state) used during the upgrade pause phase to determine how much of an existing build charge should be retained when a player switches to a different nuke design.

**Method:**

```java
double suggestedRetentionRatio(NukeDesign oldDesign, NukeDesign newDesign)
```

| Condition | Retention Ratio |
|---|---|
| Same design (same `id`) | 1.00 (100%) |
| New design is DOOMSDAY | 0.60 (60%) |
| New design is CONCRETE_BLASTER | 0.60 |
| New design is EMP_PAYLOAD | 0.60 |
| Either design is DECOY_PACKAGE | 0.75 |
| Different doctrine (general case) | 0.70 |
| Same doctrine, new design is larger size | 0.90 |
| Same doctrine, new design is smaller size | 1.00 |

The rationale: switching doctrine represents a fundamental redesign requiring partial disassembly of existing build progress. Switching to a bigger weapon of the same type loses some efficiency due to the scale change. Switching to a smaller weapon of the same type retains all progress.

---

## 6. Launch & Impact Mechanics

### 6.1 ActiveLaunchState.java

Created by the match coordinator when a player successfully inputs a valid launch action code. Represents a single launched weapon from the attacker's perspective. One `ActiveLaunchState` can exist per active launch (multiple concurrent launches are possible).

**Immutable fields (set at creation, never change):**

| Field | Type | Description |
|---|---|---|
| `launchId` | `UUID` | Unique identifier for this specific launch event |
| `attacker` | `ParticipantId` | Which player launched |
| `defender` | `ParticipantId` | Target player |
| `nukeDesignId` | `String` | ID of the design at launch time |
| `nukeDisplayName` | `String` | Name shown in event log |
| `doctrineType` | `NukeDoctrineType` | Snapshot of doctrine at launch |
| `sizeCategory` | `NukeSizeCategory` | Snapshot of size at launch |
| `nukeDesign` | `NukeDesign` | Deep copy of the full design at launch time (immutable snapshot) |
| `defconAtLaunch` | `int` | DEFCON level at the moment of launch (used to resolve impact values) |

**Mutable fields (evolve through launch lifecycle):**

| Field | Type | Description |
|---|---|---|
| `launchCountdownPieces` | `int` | Pieces remaining in countdown |
| `warningPieces` | `int` | Pieces of warning the defender will receive |
| `launchTimerId` | `TimerId` | Handle to the countdown timer |
| `flightTimerId` | `TimerId` | Handle to the in-flight timer (warning countdown) |
| `phase` | `LaunchPhase` | Current lifecycle phase |
| `inFlight` | `boolean` | True after countdown completes |
| `impactReady` | `boolean` | True when warning has expired and impact can be resolved |
| `manualOverride` | `boolean` | True if coordinator forced phase transition |
| `empWeakened` | `boolean` | True if this launch was partially disrupted by opponent EMP |
| `deadHandCounterLaunch` | `boolean` | True if this launch was triggered by dead hand auto-response |
| `extraGarbageLines` | `int` | Additional lines added by upgrades at launch time |
| `interceptMitigation` | `InterceptMitigationState` | Tracks partial intercept reductions |

**LaunchPhase lifecycle:**

```
AUTHORIZED
    ↓ (coordinator calls startCountdown())
COUNTDOWN
    ↓ (countdown timer fires)
IN_FLIGHT → [creates IncomingThreatState on defender side]
    ↓ (warning timer fires)
IMPACT_READY
    ↓ (coordinator calls ImpactResolver.resolveImpact())
RESOLVED
    — or —
CANCELLED (intercept, abort, decoy redirect)
```

---

### 6.2 IncomingThreatState.java

The defender's view of an inbound launch. Created when the attacker's countdown completes and the weapon transitions to `IN_FLIGHT`. Stored in `ParticipantState.incomingThreats`.

**Immutable fields:**

| Field | Type | Description |
|---|---|---|
| `threatId` | `UUID` | Unique threat identifier |
| `launchId` | `UUID` | Matches the corresponding `ActiveLaunchState.launchId` |
| `attacker` | `ParticipantId` | Who launched |
| `defender` | `ParticipantId` | This player |
| `knownOrEstimatedDoctrine` | `NukeDoctrineType` | What the defender's radar shows (may be falsified by decoy) |
| `nukeDesignId` | `String` | Design ID (may be falsified) |
| `nukeDisplayName` | `String` | Display name |
| `doctrineType` | `NukeDoctrineType` | True doctrine (used by resolver; not shown to player unless radar upgraded) |
| `sizeCategory` | `NukeSizeCategory` | True size |
| `warningPiecesTotal` | `int` | Total warning duration at creation |
| `flightTimerId` | `TimerId` | Handle to warning countdown timer |

**Mutable fields:**

| Field | Type | Description |
|---|---|---|
| `warningPiecesRemaining` | `int` | Countdown toward impact |
| `status` | `ThreatStatus` | Current status |
| `intercepted` | `boolean` | True if an intercept action resolved this threat |
| `interceptMitigation` | `InterceptMitigationState` | Details of partial intercepts |

**ThreatStatus lifecycle:**

```
WARNING_ACTIVE
    ↓ (warning timer fires)
IMPACT_READY
    ↓ (resolver called)
RESOLVED
    — or —
INTERCEPTED (intercept action code succeeded)
    — or —
CANCELLED (attacker aborted, decoy resolved it)
```

---

### 6.3 ImpactResolver.java

Translates an `IMPACT_READY` launch+threat pair into concrete game state changes. Called by the match coordinator.

**Method signature:**

```java
ImpactResult resolveImpact(
    ActiveLaunchState launch,
    IncomingThreatState threat,
    ParticipantState attacker,
    ParticipantState defender,
    PieceTimerManager timerManager,
    int sequence,
    GarbageWaveSink waveSink,
    CivilDefenseState civilDefense,
    GracePeriodPolicy gracePolicy
)
```

**Processing steps:**

1. **Read damage ratings** from `launch.nukeDesign` (the frozen snapshot).

2. **Apply GarbageProfile**:
   - Immediate lines: `min(garbageProfile.maxImmediateLines, blastRating * someMultiplier)`
   - Grace period check: `gracePolicy.allowedLines(requestedImmediateLines)` — may reduce allowed immediate lines based on whether the defender just placed a piece.
   - Deferred lines: remaining garbage queued via `waveSink`.
   - If `usesWaves`: schedule additional garbage waves on `timerManager` at `piecesBetweenWaves` intervals.
   - If `targetedGarbage`: call `defender.gameState.insertGarbagePattern(radiationPattern)` instead of plain garbage rows.

3. **Apply DisarmProfile**:
   - Check if defender cleared enough rows to qualify for full-clear mitigation.
   - If mitigation applies and `canFullClear`: disarm damage = 0.
   - Otherwise: call `defender.nukeBuildState.reduceCharge(disarmProfile.disarmPower * disarmRating / scaleFactor)`.

4. **Apply SiloDamageProfile**:
   - If `canDamageSilo`: call `defender.siloState.takeDamage(siloDamageProfile.siloDamagePower * siloDamageRating)`.
   - If `targetsHardening`: bypass some passive hardening reduction.

5. **Apply doctrine-specific effects**:
   - MIRV: schedule each reentry vehicle wave as a separate timer event.
   - EMP: call `defender.radarIntel.applyEmpDisruption(empProfile)`.
   - DECOY: if this was identified as a decoy by the defender's intercept, cancel; otherwise apply `DecoyProfile` effects.

6. **Mark states resolved**: `launch.phase = RESOLVED`, `threat.status = RESOLVED`.

7. **Return `ImpactResult`** with all metrics.

---

### 6.4 ImpactResult.java

An immutable record returned by `ImpactResolver.resolveImpact()`. Used by the match coordinator to log events and update the UI.

**Key fields:**

| Field | Type | Description |
|---|---|---|
| `status` | `ImpactStatus` | `RESOLVED`, `CANCELLED`, or `MITIGATED` |
| `launchId` / `threatId` | `UUID` | Cross-references |
| `attacker` / `defender` | `ParticipantId` | Who was involved |
| `nukeDesignId` / `nukeDisplayName` | `String` | Design identification |
| `doctrineType` / `sizeCategory` | Enums | Weapon classification |
| `blastRating` / `radiationRating` / `disarmRating` / `siloDamageRating` | `int` | Ratings used |
| `garbageLinesAppliedImmediately` | `int` | Actual immediate lines sent |
| `garbageLinesDelayed` | `int` | Lines queued for delayed delivery |
| `radiationLevel` | `int` | Radiation pattern intensity |
| `defenderChargeBefore` / `defenderChargeAfter` | `int` | Defender's build charge before and after |
| `disarmAmountApplied` | `int` | Actual disarm damage dealt |
| `defenderNukeFullyDisarmed` | `boolean` | True if defender's charge dropped to zero |
| `siloIntegrityBefore` / `siloIntegrityAfter` | `int` | Silo state before and after |
| `siloDamageApplied` | `int` | Actual silo damage dealt |
| `requestedImmediateRowsBeforeGrace` | `int` | Lines requested before grace filtering |
| `allowedImmediateRowsAfterGrace` | `int` | Lines actually allowed through grace |
| `graceDeferredRows` | `int` | Lines deferred by grace policy |

---

## 7. Launch Authorization — Action Codes

**Class**: `ActionCodeRegistry` (`com.tetris.mab`)

The launch authorization system prevents players from launching a nuke by simply clicking a button. Instead, they must input a specific sequence (the "action code") on their keyboard/controller during active gameplay. This creates tension because inputting a launch code competes with the need to play Tetris.

**ActionCodeDefinition structure:**

Each definition has:
- `id` — unique identifier
- `displayName` — shown in tactical strip UI
- `actionType` — `ActionType` enum (determines rendering style and input handling)
- `codeSequence` — `List<Integer>` values to input in order
- `requiresHardFourConfirm` — whether a confirmation code must be entered after

**Built-in action codes (partial list):**

| ID | Display Name | Action Type |
|---|---|---|
| `micro_launch` | Micro Launch | LAUNCH |
| `tactical_launch` | Tactical Launch | LAUNCH |
| `theater_launch` | Theater Launch | LAUNCH |
| `strategic_launch` | Strategic Launch | LAUNCH_STRATEGIC |
| `dirty_launch` | Dirty Bomb Launch | LAUNCH |
| `mirv_launch` | MIRV Launch | LAUNCH_STRATEGIC |
| `concrete_blaster_launch` | Concrete Blaster Launch | LAUNCH_STRATEGIC |
| `superheavy_launch` | Superheavy Launch | LAUNCH_STRATEGIC |
| `doomsday_launch` | Doomsday Launch | LAUNCH_DOOMSDAY |
| `intercept_alpha` | Intercept Alpha | DEFENSE |
| `silo_harden` | Silo Hardening | UTILITY |
| `radar_sweep` | Radar Sweep | INTEL |

**Per-nuke launch code generation:**

```java
ActionCodeDefinition launchDefinitionForNuke(NukeDesign design, int defconLevel)
```
Reads `design.effectiveLaunchCode(defconLevel)` and constructs an `ActionCodeDefinition` whose `codeSequence` matches that list. This means every time DEFCON changes, the launch codes change too — players must re-memorize their code.

```java
ActionCodeDefinition launchDefinitionForNukeWithHardFourConfirm(NukeDesign design, int defconLevel)
```
Same as above but appends a mandatory 4-line confirmation sequence after the main code. Used for STRATEGIC and DOOMSDAY size weapons.

```java
ActionType mapDoctrineToActionType(NukeDesign design)
```
Returns `LAUNCH_DOOMSDAY` for DOOMSDAY doctrine, `LAUNCH_STRATEGIC` for STRATEGIC/SUPERHEAVY size, and `LAUNCH` for everything else.

```java
boolean isStrategicForNuke(NukeDesign design, List<Integer> code)
```
Returns true if the code represents a strategic-level launch requiring hard confirm.

---

## 8. UI Components

### 8.1 NukeBuilderDialog

**Full class**: `com.tetris.view.NukeBuilderDialog` (or similar package)

A custom Swing dialog implementing a KSP-style (Kerbal Space Program) modular weapon constructor. Players assemble their weapon design visually before a match begins.

**Layout (3-column):**

```
┌────────────────┬──────────────────────┬────────────────────────┐
│  PARTS PALETTE │  CROSS-SECTION       │  INFO PANEL            │
│                │  SCHEMATIC           │                        │
│  [filtered to  │  (clickable regions  │  Estimated Yield: X kt │
│  active slot]  │  for each slot)      │  Complexity: X         │
│                │                      │  Warnings: [list]      │
│  [Part 1]      │     ┌───────┐        │  Historical analog: X  │
│  [Part 2]      │     │ FUSE  │        │  Effects: X km radius  │
│  [Part 3]      │  ───┼───────┼───     │                        │
│  [Part 4]      │     │ CORE  │        │  [Educational notes]   │
│                │     │       │        │                        │
│                │  ───┼───────┼───     │                        │
│                │     │ CASE  │        │                        │
└────────────────┴──────────────────────┴────────────────────────┘
```

**Inner classes:**

- `SchematicPanel` — custom `JPanel` that draws the weapon cross-section. Each slot region is a clickable polygon. When clicked, it sets the "active slot" and filters the parts palette.
- `FuzeTerminal` — sub-panel for the FUZE slot shown separately at the base of the schematic, since fuzing interacts with all other components.

**Fusion sub-designer:**

A separate UI pane appears when the SECONDARY slot has content. It allows per-stage tuning of the Teller-Ulam configuration:

| FusionGroup enum value | UI Control |
|---|---|
| `STAGE_SELECT` | Number-of-stages selector (1–3 additional stages) |
| `FUEL` | Fuel type selection per stage |
| `PUSHER` | Pusher material (U-238 / Lead / Tungsten) per stage |
| `CHANNEL` | Radiation channel filler (Polystyrene / Vacuum) per stage |
| `SPARK_PLUG` | Fissile spark plug toggle per stage |
| `STAGES` | Summary overview of all configured stages |

**Per-slot fill colors:** Each NukeSlot has a distinct background color in the schematic so the player can immediately identify which region maps to which subsystem (e.g., FISSILE = yellow, TAMPER = grey, IMPLOSION = orange).

**Info panel updates:** Every time the player changes any selection, the info panel recomputes and displays:
1. `NukeDesign.getEstimatedYieldKt()` — displayed with appropriate unit prefix (e.g., "~3.2 kt", "~450 kt", "~12 Mt")
2. Total complexity score — sum of all selected parts' `complexity` fields
3. Warnings from `NukeDesign.getWarnings()` — color-coded red (hard fizzle), yellow (soft fizzle), green (synergy)
4. Historical analog from `NukeDesign.getHistoricalAnalog()`
5. Effects summary from `NukeDesign.getEffectsSummary()`
6. Educational notes from the hovered/selected `NukePart.educationalNotes`

---

### 8.2 MabNukeDesignSelection

**Full class**: `com.tetris.mab.ui.MabNukeDesignSelection` (or `com.tetris.mab.nuke`)

A player-facing wrapper around a strategic `NukeDesign` that also tracks where the design came from.

**Source enum:**

```java
enum Source {
    DEFAULT_DOCTRINE,   // Chosen from the preset ladder
    BUILDER_DERIVED     // Generated by the educational builder via the bridge
}
```

**Fields:**

| Field | Type | Description |
|---|---|---|
| `source` | `Source` | Origin of this design |
| `design` | `NukeDesign` | The strategic design |
| `summary` | `String` | Pre-generated safe gameplay summary |

**Factory methods:**

```java
MabNukeDesignSelection defaultSelection()
```
Returns a selection wrapping `NukeDesignFactory.createDefaultPlaceholder()` with source `DEFAULT_DOCTRINE`.

```java
MabNukeDesignSelection fromMabDesign(NukeDesign design)
```
Wraps an existing strategic design. Generates summary via the bridge's `safeGameplaySummary()`.

```java
MabNukeDesignSelection fromBuilderDesign(com.tetris.model.nuke.NukeDesign educationalDesign)
```
Full conversion path: educational → bridge → strategic. Source is `BUILDER_DERIVED`.

**Accessor:**

```java
boolean isSummarySafe()
```
Delegates to `MabNukeBuilderBridge.isSafeGameplaySummary(summary)`. If this returns false, the UI replaces the summary with a generic fallback text rather than displaying potentially unsafe content.

---

### 8.3 MabNukeRedesignPanel

**Full class**: `com.tetris.mab.ui.MabNukeRedesignPanel`

The UI panel displayed to the player during the upgrade pause phase, allowing them to switch to a different weapon design. It presents the 8 preset options from `MabNukePresetDefinition.defaults()`.

**Layout:** A vertical list of preset cards, each showing:
- Design name
- Doctrine type badge
- Size category badge
- Four rating bars (blast, radiation, disarm, silo)
- Build charge cost (shown at current DEFCON level)
- Current vs. new charge retention estimate

When a different preset is highlighted, the panel shows the `suggestedRetentionRatio` as a percentage and the projected new armed state.

**Confirmation flow:** The player must explicitly confirm the redesign. On confirmation:
1. `NukeRedesignRetentionRules.suggestedRetentionRatio(oldDesign, newDesign)` is computed.
2. `nukeBuildState.redesign(newDesign, defconLevel, ratio)` is called.
3. The match coordinator updates the player's `MabNukeDesignSelection`.

---

### 8.4 MabNukePresetDefinition

**Full class**: `com.tetris.mab.ui.MabNukePresetDefinition` (or `com.tetris.mab.nuke`)

A lightweight record that wraps a factory method reference, providing a display name, description, and the ability to instantiate a fresh `NukeDesign` on demand.

**Fields:**

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Display name (e.g., "Clean Fusion") |
| `description` | `String` | One-sentence gameplay description |
| `factory` | `Supplier<NukeDesign>` | Reference to `NukeDesignFactory::createDefault...` method |

**Method:**

```java
NukeDesign build()
```
Invokes the factory method and returns a fresh immutable strategic NukeDesign.

**Static factory:**

```java
List<MabNukePresetDefinition> defaults()
```
Returns the ordered list of 8 presets in presentation order (Placeholder first, Doomsday last).

---

## 9. MAB Match Coordinator Integration

**Full class**: `com.tetris.mab.MutuallyAssuredBlocksMatch`

The central coordinator for the entire strategic layer. All nuke-related events pass through this class.

### Construction & Ownership

The coordinator owns:
- `ParticipantState playerA` and `ParticipantState playerB`
- `DefconState defconState` (shared)
- `PieceTimerManager pieceTimerManager`
- `ActionCodeRegistry actionCodeRegistry`
- `ImpactResolver impactResolver`
- `NukeBuilderAdapter nukeBuilderAdapter`
- `InterceptRegistry interceptRegistry`
- `DecoyRegistry decoyRegistry`
- `MabUpgradeRegistry upgradeRegistry`
- `MatchEventLog eventLog` (ring buffer, capacity 200 events)
- `MatchPhase matchPhase` (`SETUP`, `ACTIVE`, `UPGRADE_PAUSE`, `GAME_OVER`)

**Constructor overloads:**

```java
// Two human players, independent piece bags
static MutuallyAssuredBlocksMatch createLocalPvp(GameState gameA, GameState gameB, Difficulty difficulty)

// Two human players, shared deterministic 7-bag (fair)
static MutuallyAssuredBlocksMatch createLocalPvpShared(GameState gameA, GameState gameB, Difficulty difficulty, long seed)

// Human vs AI, shared deterministic bag
static MutuallyAssuredBlocksMatch createPveShared(GameState humanGame, GameState aiGame, Difficulty difficulty, long seed)

// Human vs AI, independent bags (legacy)
static MutuallyAssuredBlocksMatch createPve(GameState humanGame, GameState aiGame, Difficulty difficulty)
```

### Nuke Setup Integration

During the `SETUP` phase (before `startMatch()` is called):

1. If player used the educational builder: `MabNukeDesignSelection.fromBuilderDesign(educationalDesign)` is called, running the full conversion pipeline.
2. If player chose a preset: `MabNukeDesignSelection.fromMabDesign(preset.build())`.
3. The coordinator calls `playerState.nukeBuildState.setDesign(selection.design(), currentDefcon)` for both players.

### Active Phase — Charge Accumulation

Each time a player's Tetris engine reports a line clear, the coordinator:
1. Computes charge units: `lines * chargePerLine * difficultyMultiplier * upgradeMultiplier`
2. Calls `playerState.nukeBuildState.addCharge(computedCharge)`
3. Checks `nukeBuildState.armed`
4. If newly armed: logs a `NUKE_ARMED` event and updates the tactical strip UI

### Active Phase — Launch Sequence

When a player successfully inputs a launch action code:

1. `nukeBuildState.armed` must be true; otherwise action code is rejected.
2. Coordinator reads `design.effectiveLaunchCode(defconState.currentDefcon)` and confirms the input matches.
3. Creates `ActiveLaunchState` with snapshot of current design.
4. Schedules countdown timer: `pieceTimerManager.schedule(launch.launchTimerId, effectiveLaunchTimePieces, () -> onCountdownComplete(launch))`.
5. `launch.phase = COUNTDOWN`.
6. Calls `nukeBuildState.resetCharge()` — the player must start building the next weapon immediately.
7. Adds escalation: `defconState.addEscalation(+50 to +120, "Launch initiated")`.

**On countdown complete** (`onCountdownComplete`):
1. `launch.phase = IN_FLIGHT`, `launch.inFlight = true`.
2. Creates `IncomingThreatState` on the defender's `ParticipantState`.
3. Schedules warning timer: `pieceTimerManager.schedule(flight.flightTimerId, effectiveWarningTimePieces, () -> onWarningExpired(launch, threat))`.
4. `threat.status = WARNING_ACTIVE`.
5. Updates defender's UI warning panel.

**On warning expired** (`onWarningExpired`):
1. `threat.status = IMPACT_READY`, `launch.impactReady = true`.
2. Calls `impactResolver.resolveImpact(launch, threat, attackerState, defenderState, timerManager, sequence, waveSink, civilDefense, gracePolicy)`.
3. Processes `ImpactResult`: applies garbage, disarm, silo damage.
4. Checks if defender is fully disarmed → logs event.
5. Checks if silo destroyed → logs event.
6. Adds escalation based on impact severity.

### Upgrade Pause Integration

When the coordinator determines an upgrade pause is due:
1. `matchPhase = UPGRADE_PAUSE`.
2. Both Tetris engines are paused.
3. Players enter `MabUpgradeDraftOverlayPanel` — can acquire upgrades AND/OR access `MabNukeRedesignPanel`.
4. If redesign is confirmed: calls `nukeBuildState.redesign(newDesign, defconState.currentDefcon, retentionRatio)`.
5. Updates `playerState.nukeDesignSelection`.
6. On resume: `matchPhase = ACTIVE`, both engines unpause.

### DEFCON Change Propagation

Whenever `defconState.addEscalation()` returns a `DefconChangeResult` with `changed == true`:
1. Call `playerA.nukeBuildState.refreshForDefcon(newDefcon)`.
2. Call `playerB.nukeBuildState.refreshForDefcon(newDefcon)`.
3. Call `actionCodeRegistry.refreshForDefcon(newDefcon)` — updates launch code definitions.
4. Log `DEFCON_CHANGE` event to `eventLog`.
5. Update both players' tactical strip UI displays.

---

## 10. Complete Data Flow Diagrams

### Setup Flow

```
Player opens NukeBuilderDialog
    │
    ▼
Player selects NukeParts for each NukeSlot
    │
    ├─ Info panel: getEstimatedYieldKt(), getWarnings(), getHistoricalAnalog()
    │
    ▼
Player confirms design
    │
    ▼
MabNukeDesignSelection.fromBuilderDesign(educationalNukeDesign)
    │
    ▼
MabNukeBuilderBridge.toBuilderSpec(educationalNukeDesign)
    │  ← reads slot selections, derives doctrine, blast, rad, disarm, silo, speed, stealth
    ▼
BuilderNukeSpec (DTO) ──── MabNukeBuilderBridge.safeGameplaySummary()
    │                               │
    ▼                               ▼
NukeBuilderAdapter.fromBuilderSpec() ←─── summary stored in MabNukeDesignSelection
    │  ← resolves doctrine, builds DEFCON maps via NukeReadinessScaler
    ▼
NukeDesign (strategic, immutable)
    │
    ▼
MutuallyAssuredBlocksMatch constructor receives MabNukeDesignSelection
    │
    ▼
ParticipantState.nukeBuildState.setDesign(design, initialDefcon)
    │  ← effective build charge computed
    ▼
Match begins (SETUP → ACTIVE)
```

---

### Build Charge Flow

```
Player clears N lines in Tetris engine
    │
    ▼
MutuallyAssuredBlocksMatch.onLinesClear(player, N)
    │
    ▼
charge = N * chargePerLine * difficultyMultiplier * upgradeMultiplier
    │
    ▼
nukeBuildState.addCharge(charge)
    │  ← currentBuildCharge += charge
    │  ← recomputeArmedAndOverbuilt()
    ▼
armed? ─── NO ──→ continue playing
    │
    YES
    ▼
Log NUKE_ARMED event
Update MabTacticalStripPanel (ARMED indicator)
Player can now input launch action code
```

---

### Launch Flow

```
Player inputs launch action code correctly
    │
    ▼
MutuallyAssuredBlocksMatch.onActionCodeComplete(player, codeId)
    │  ← verify armed, verify code matches
    ▼
Create ActiveLaunchState (snapshot of design + defcon)
    │
    ▼
nukeBuildState.resetCharge()        defconState.addEscalation(+escalation)
    │                                        │
    ▼                                        ▼
Schedule countdown timer (effectiveLaunchTimePieces pieces)
    │
    ▼
launch.phase = COUNTDOWN
    │
    ▼ [pieces pass]
Countdown timer fires: onCountdownComplete()
    │
    ▼
launch.phase = IN_FLIGHT
Create IncomingThreatState on defender
    │
    ▼
Schedule warning timer (effectiveWarningTimePieces pieces)
threat.status = WARNING_ACTIVE
    │
    ▼ [pieces pass]
Warning timer fires: onWarningExpired()
    │
    ▼
threat.status = IMPACT_READY
launch.impactReady = true
    │
    ▼
ImpactResolver.resolveImpact(...)
    │
    ├─ Apply garbage (immediate + waves)
    ├─ Apply disarm damage to defender's nukeBuildState
    ├─ Apply silo damage to defender's siloState
    └─ Apply doctrine-specific effects (MIRV waves, EMP disruption, decoy logic)
    │
    ▼
ImpactResult returned
    │
    ▼
Log IMPACT_RESOLVED event
Update UI panels
defconState.addEscalation(+impact escalation)
    │
    ▼
launch.phase = RESOLVED
threat.status = RESOLVED
```

---

### Redesign Flow (Upgrade Pause)

```
Upgrade pause triggered
    │
    ▼
MabNukeRedesignPanel displayed
    │
    ▼
Player selects new preset from MabNukePresetDefinition.defaults()
    │
    ▼
Preview: NukeRedesignRetentionRules.suggestedRetentionRatio(old, new)
         Display: retained charge estimate, new armed projection
    │
    ▼
Player confirms
    │
    ▼
ratio = suggestedRetentionRatio(oldDesign, newDesign)
nukeBuildState.redesign(newDesign, currentDefcon, ratio)
    │  ← currentBuildCharge = floor(old * ratio)
    │  ← effectiveBuildChargeRequired = newDesign.effectiveBuildChargeRequired(currentDefcon)
    │  ← recomputeArmedAndOverbuilt()
    ▼
playerState.nukeDesignSelection = MabNukeDesignSelection.fromMabDesign(newDesign)
Update tactical strip UI
```

---

## 11. DEFCON Scaling Rules

The key invariant of the DEFCON system: **damage never changes, only deployment difficulty changes.**

| DEFCON Level | Meaning | Effect on Deployment |
|---|---|---|
| 5 | Peacetime | Highest build charge required; longest launch codes; longest countdown; longest warning |
| 4 | Elevated watch | Slightly reduced requirements |
| 3 | Crisis | Reference level; factory base values apply |
| 2 | War footing | Notably reduced requirements |
| 1 | War imminent | Minimum requirements; fastest everything |

**Why this design:** At DEFCON 5, the strategic layer imposes full peacetime authorization procedures — the player must work harder and wait longer to deploy. At DEFCON 1, emergency procedures are active — players can arm and launch much faster. This creates escalating tension as the match progresses and DEFCON drops.

**What DEFCON does NOT change:**
- `blastRating`, `radiationRating`, `disarmRating`, `siloDamageRating` — all invariant
- Garbage line counts — determined by ratings, not DEFCON
- Disarm and silo damage amounts — determined by ratings, not DEFCON

---

## 12. Design Preset Ladder

The 8 built-in presets form a ladder from cheapest/simplest to most powerful/expensive. Players choose their starting design during setup or switch during upgrade pauses.

| # | Name | Doctrine | Size | Build (DEFCON 3) | Blast | Rad | Disarm | Silo | Strategic Role |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Placeholder | PLACEHOLDER | TACTICAL | 22 | 2 | 1 | 1 | 0 | Default fallback; minimal threat |
| 2 | Dirty Bomb | DIRTY_BOMB | TACTICAL | 25 | 2 | 4 | 1 | 0 | Radiation pressure; cheap and fast |
| 3 | EMP Strike | EMP_PAYLOAD | THEATER | 36 | 1 | 2 | 3 | 2 | Disrupts radar and warning systems |
| 4 | Bunker Buster | BUNKER_BUSTER | THEATER | 44 | 3 | 0 | 5 | 5 | Hard silo killer; no radiation |
| 5 | Concrete Blaster | CONCRETE_BLASTER | STRATEGIC | 56 | 2 | 1 | 6 | 5 | Anti-nuke weapon; disarms and silo-busts |
| 6 | MIRV | MIRV | STRATEGIC | 58 | 5 | 2 | 2 | 1 | Multi-wave blast pressure; hard to clear |
| 7 | Clean Fusion | CLEAN_FUSION | STRATEGIC | 60 | 5 | 1 | 3 | 1 | Maximum blast with minimal radiation |
| 8 | Doomsday | DOOMSDAY | DOOMSDAY_SCALE | 150 | 8 | 3 | 8 | 8 | All-or-nothing; rarely achievable |

**Player strategy notes:**
- The Dirty Bomb is the fastest to arm but the hardest to defend against via board clearing (radiation lines are harder to clear efficiently).
- The Concrete Blaster is the counter-weapon: it targets the opponent's ability to launch by reducing their build charge and silo integrity, potentially locking them into a disarmed state.
- The MIRV is the hardest to defend because its waves arrive at intervals — the defender cannot clear all garbage at once.
- The Doomsday requires 150 charge at DEFCON 3 (vs. 22 for the Placeholder). It is only viable if a player has significant upgrade bonuses to charge accumulation, or if the match reaches a very late stage.

---

## 13. Safety & Censorship System

The codebase includes a layered safety system to ensure that no engineering-level weapon design information reaches the game UI.

### Layer 1 — Educational Layer Containment

The `com.tetris.model.nuke` package is only accessed from:
- `NukeBuilderDialog` (the educational UI itself)
- `MabNukeBuilderBridge` (the sole conversion point)

No other game code imports from `com.tetris.model.nuke`. This is an architectural guarantee enforced by package structure.

### Layer 2 — Bridge Sanitization

`MabNukeBuilderBridge.safeGameplaySummary()` generates gameplay-effect text using only strategic terminology:
- "blast pressure", "radiation waves", "disarm power", "silo damage" — always allowed
- "kilograms", "enrichment", "isotopes", "plutonium", etc. — never appear

### Layer 3 — Blocklist Validation

`MabNukeBuilderBridge.isSafeGameplaySummary(String text)` validates any candidate text. The blocklist includes:

```
kg, kilogram, u-235, u-238, pu-239, plutonium, uranium, tritium,
deuterium, lithium deuteride, lens, initiator, tamper, fissile,
implosion, spherical, cylindrical, casing, fuze, enrichment,
critical mass, pit, reflector, beryllium, polonium, explosive,
compression, gun-type, chain reaction, neutron flux
```

If validation fails, the UI displays a generic fallback: *"This weapon has been configured for strategic deployment."*

### Layer 4 — MabNukeDesignSelection Guard

`MabNukeDesignSelection.isSummarySafe()` is called every time the selection is displayed in any UI panel. This is the last line of defense before text reaches the player.

---

## 14. Testing & Validation

**Class**: `MabNukeBuilderIntegrationProbe` (`com.tetris.mab.sim`)

A probe/validation class (Step 26) that exercises the entire nuke builder pipeline in isolation.

**Validations performed:**

| Test | What it checks |
|---|---|
| Educational builder model available | `NukeSlot` catalog loaded; all 11 slots present |
| Parts selectable | `NukeDesign.selections` can be mutated; yield computed |
| Bridge conversion | `MabNukeBuilderBridge.toBuilderSpec()` produces valid `BuilderNukeSpec` |
| Adapter conversion | `NukeBuilderAdapter.fromBuilderSpec()` produces valid strategic `NukeDesign` |
| Safe summary generated | `safeGameplaySummary()` returns non-null, non-empty string |
| Summary passes blocklist | `isSafeGameplaySummary()` returns true |
| Default designs accessible | All 8 `NukeDesignFactory` methods return non-null designs |
| DEFCON scaling works | Effective build charges differ across DEFCON 1–5 |
| Damage ratings invariant | Blast/rad/disarm/silo identical across all DEFCON levels |
| Invalid input handled | Bridge fallback behavior on null/incomplete educational design |
| Setup stores selections | `MabNukeDesignSelection` correctly wraps strategic design |
| PvP match applies selections | `createLocalPvp()` coordinator uses player selections |
| PvE match applies selections | `createPveShared()` coordinator uses selections |
| Redesign retention applied | `redesign()` with 0.70 ratio retains correct charge |
| Reset clears charge | `resetCharge()` zeros current charge, retains design |

The probe returns a structured result indicating pass/fail for each validation, with descriptive failure messages. It is invoked from the developer console panel (`DevConsolePanel`) via a dedicated probe command.
