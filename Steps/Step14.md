# Step 14 — Mutually Assured Blocks: Headless Simulation Harness, Deterministic Smoke Tests, and Balance Telemetry

> **Permanent scope rule:** This step (and every following one) remains
> strictly **offline-only**. No networking, no sockets, no
> matchmaking, no rollback, no replay-server, no future-online API
> reservations. The simulation harness introduced here runs entirely
> in-process from a `public static void main` and operates on the same
> `MutuallyAssuredBlocksMatch` model the Swing UI drives.

## 1. Purpose

Step 13 added a strategic AI driver and a HUD. The AI now lets us
play out long PvE matches by hand, but we still cannot quickly answer
questions like:

- *Does a fresh launch always trigger an impact resolution?*
- *Does Civil Defence actually reduce silo damage?*
- *Does the upgrade flow consume points and increase the level?*
- *Do AI archetypes execute decisions and skip when not ready?*

Step 14 adds a **headless simulation harness** that scripts the
existing `debug*` levers from Steps 11–13, runs deterministic
scenarios, samples cumulative telemetry, checks core invariants, and
prints a compact text report. The harness is intentionally NOT a
JUnit test framework, NOT a final balance pass, and NOT a replay
recorder. It is a fast offline sanity tool that future balance steps
can extend.

## 2. Offline scope (what this step is and isn't)

The harness only touches existing offline classes:

- `MutuallyAssuredBlocksMatch` and its participant/silo/upgrade state
- `MabAiDriver` from Step 13
- `GameState` for two boards
- `MatchEventLogEntry` for read-only telemetry

It does NOT introduce:

- a network protocol, snapshot serializer, or schema versioning
- a real test runner, assertion framework, or Maven Surefire wiring
- a final balance pass — counts and tick budgets are *plausible
  defaults*, not tuned numbers
- a deterministic random source — scenarios use direct `debug*` calls
  so reproducibility comes from the deterministic match model itself,
  not from a seeded PRNG inside the harness

## 3. Files added

All new files live under `com.tetris.mab.sim`:

- [src/main/java/com/tetris/mab/sim/MabSimulationMode.java](src/main/java/com/tetris/mab/sim/MabSimulationMode.java)
  — enum of named scenarios (`SMOKE`, `AI_VS_DUMMY`, `AI_VS_AI`,
  `LAUNCH_IMPACT`, `RADAR_DECOY`, `CIVIL_DEFENSE`, `UPGRADE_FLOW`,
  `DEBUG`).
- [src/main/java/com/tetris/mab/sim/MabSimulationConfig.java](src/main/java/com/tetris/mab/sim/MabSimulationConfig.java)
  — record with mode, tick budget, archetypes, difficulty, log
  window, autoresolve flag, and label. Provides `smoke()`,
  `aiVsDummy(ticks)`, `aiVsAi(ticks, a, b, diff)`, `radarDecoy()`,
  `launchImpact()`, `civilDefense()`, `upgradeFlow()` factories.
- [src/main/java/com/tetris/mab/sim/MabSimulationResult.java](src/main/java/com/tetris/mab/sim/MabSimulationResult.java)
  — record describing the outcome (success, summary string, counts,
  invariant failures, recent event rows, both participant snapshots).
- [src/main/java/com/tetris/mab/sim/MabSimulationTelemetry.java](src/main/java/com/tetris/mab/sim/MabSimulationTelemetry.java)
  — read-only counters keyed on event-type strings; supports both
  one-shot scans of `match.getEventLog()` and externally-maintained
  cumulative count maps via `fromCounts(...)`.
- [src/main/java/com/tetris/mab/sim/MabSimulationInvariants.java](src/main/java/com/tetris/mab/sim/MabSimulationInvariants.java)
  — `check(match)` returns a list of invariant-failure strings.
- [src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java](src/main/java/com/tetris/mab/sim/MabHeadlessSimulation.java)
  — orchestrates a single scenario from end to end.
- [src/main/java/com/tetris/mab/sim/MabSimulationRunner.java](src/main/java/com/tetris/mab/sim/MabSimulationRunner.java)
  — `public static void main` CLI entry point.

## 4. Files modified

- [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java)
  — added `debugAddUpgradePoints(ParticipantId, int)` so the
  upgrade-flow scenario can purchase upgrades without first having to
  clear lines.
- [src/main/java/com/tetris/mab/MatchEventLogEntry.java](src/main/java/com/tetris/mab/MatchEventLogEntry.java)
  — the compact constructor now tolerates `null` *values* in the
  metadata map. The previous `Map.copyOf(metadata)` call rejected
  them with a NullPointerException, which surfaced for the first time
  in Step 14 because the simulation harness exercises
  `debugStartLaunch(...)` (which goes through `authorizeLaunchInternal`
  whose `LAUNCH_AUTHORIZED` metadata legitimately contains
  `actionId=null` for debug-initiated launches). Replacing
  `Map.copyOf` with
  `Collections.unmodifiableMap(new LinkedHashMap<>(metadata))` keeps
  the entry immutable while permitting null entries.
- [src/main/java/com/tetris/mab/debugui/MabDebugController.java](src/main/java/com/tetris/mab/debugui/MabDebugController.java)
  — added `runHeadlessSmokeSimulation()` that invokes the new harness
  and returns a formatted summary string for the HUD.
- [src/main/java/com/tetris/mab/debugui/MabDebugFrame.java](src/main/java/com/tetris/mab/debugui/MabDebugFrame.java)
  — added a *Run Smoke Sim* button that displays the result in a
  modal `JOptionPane`.

## 5. `MabSimulationConfig` field reference

| Field | Purpose | Default |
| --- | --- | --- |
| `mode` | which scenario to run | `SMOKE` if null |
| `ticks` | max strategic-clock advances per side | 80 (smoke) |
| `playerAArchetype` / `playerBArchetype` | AI archetypes for AI scenarios | `BALANCED` |
| `difficulty` | AI difficulty | `NORMAL` |
| `maxEventRows` | size of the recent-events tail in the result | 40 |
| `autoResolveImpacts` | sweep `debugResolveAllImpacts()` each tick | `true` |
| `label` | human label for the report | falls back to `mode.name()` |

The compact constructor rejects negative `ticks` and `maxEventRows`
and substitutes safe defaults for null enum fields.

## 6. `MabSimulationResult` field reference

| Field | Meaning |
| --- | --- |
| `mode`, `label` | echo of the config |
| `success` | scenario-specific acceptance flag |
| `summary` | one-line text summary used by the runner and HUD |
| `failureReason` | empty when `success` is true |
| `ticksExecuted` | how many strategic-clock ticks ran |
| `totalEvents` | cumulative count of distinct events observed |
| `eventCounts` | unmodifiable `Map<String,Integer>` keyed by event type |
| `invariantFailures` | unmodifiable list of failure strings |
| `recentEvents` | tail of the match event log, for visual inspection |
| `participantA` / `participantB` | post-run state snapshots |

## 7. Telemetry rules

`MabSimulationTelemetry` is intentionally *just a counter*. It does
not interpret semantics. Two construction paths:

1. `from(match)` — a one-shot scan of `match.getEventLog()`.
2. `fromCounts(map, total)` — wraps an externally accumulated count
   map. The harness uses this path because `MutuallyAssuredBlocksMatch`
   keeps a **bounded** event-log ring buffer; a long scenario can
   easily generate more events than the buffer holds, so the harness
   maintains its own running tally inside `Setup` and folds new
   entries in via the private `tally(Setup)` helper after every
   significant action.

This is the most important architectural detail of the harness:
**counts are accumulated incrementally**, not derived from a single
end-of-run scan.

## 8. `MabSimulationInvariants.check(match)`

Returns a list of human-readable failure strings (empty list ⇒
healthy). The current checks are:

- Strictly increasing event sequence numbers.
- DEFCON state is between 1 and 5.
- Per participant: nuke charge, civil-defence stock and upgrade
  points are non-negative; silo integrity is in `[0, 100]`.
- Per participant: any active outgoing decoy reports
  `getDurationPiecesRemaining() >= 0`.

The check list is deliberately **shallow and conservative**. Step 14
is not the right place to encode subtle balance assertions like *"a
medium nuke must always do at least N silo damage to an
undefended target"* — those belong in a future balance pass.

## 9. Scenarios

Each scenario is a method in `MabHeadlessSimulation`. All scenarios
follow the same shape: `setup` → seed any preconditions →
loop {trigger inputs, advance strategic clocks, optionally
resolve impacts, `tally`} → final sweep → `collect` → assemble result.

| Scenario | Acceptance criteria |
| --- | --- |
| `SMOKE` | invariants pass; ≥1 launch authorized; ≥1 radar scan; ≥1 decoy activation |
| `LAUNCH_IMPACT` | invariants pass; ≥1 launch authorized; ≥1 impact resolved |
| `RADAR_DECOY` | invariants pass; ≥1 decoy activation; ≥1 scan; ≥1 radar-decoy effect application |
| `CIVIL_DEFENSE` | invariants pass; ≥1 civil-defence activation; ≥1 civil-defence consumption or mitigation; ≥1 impact resolved |
| `UPGRADE_FLOW` | invariants pass; ≥1 `UPGRADE_APPLIED`; the `applyUpgrade` result reports success; HARDENED_SILO level ≥1 |
| `AI_VS_DUMMY` | invariants pass; AI driver decisions executed or skipped >0; ≥1 strategic-clock advance |
| `AI_VS_AI` | invariants pass; ≥1 AI decision executed; ≥1 strategic-clock advance |
| `DEBUG` | invariants pass; reserved for ad-hoc poking |

Tick budgets are conservative defaults; a future balance pass may
shorten or lengthen them. They are plausible enough that **all seven
named scenarios pass on a clean build today**.

## 10. CLI usage

```
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner <scenario> [ticks]
```

Where `<scenario>` is one of:
`smoke`, `launch-impact`, `radar-decoy`, `civil-defense`,
`upgrade-flow`, `ai-vs-dummy`, `ai-vs-ai`. The optional second
argument overrides ticks for the AI scenarios.

The runner prints a banner, the success flag, the summary, the
failure reason (if any), the per-event-type counts, both
participants' charge / silo headlines, and the tail of the most
recent events.

## 11. HUD button

[MabDebugFrame](src/main/java/com/tetris/mab/debugui/MabDebugFrame.java)
gains a *Run Smoke Sim* button right after the Step 13 AI buttons.
Clicking it constructs a fresh `MabHeadlessSimulation`, runs
`MabSimulationConfig.smoke()`, and shows the formatted summary in a
modal dialog. The HUD's own match is **not** affected; the
simulation builds its own throwaway match instance.

## 12. *Not a full test framework*

The harness deliberately does NOT:

- depend on JUnit, AssertJ, Hamcrest, or Surefire
- emit JUnit XML, TAP, or any CI-consumable report format
- support `@BeforeEach`, fixtures, parameterized tests, or tags
- block CI on failure (it always exits the JVM after printing the
  result; `success=false` does not affect the exit code)

If/when a real test pass is added, it should sit in
`src/test/java/com/tetris/mab/sim/` and call into the same harness
classes. The harness was designed to be importable from a real test
without modification.

## 13. *Not final balance tuning*

All numerical thresholds in this step are *plausible defaults*, not
tuned values:

- 80 ticks for `SMOKE`, 60 for `LAUNCH_IMPACT` and `CIVIL_DEFENSE`,
  20 for `RADAR_DECOY` and `UPGRADE_FLOW`
- 50 upgrade points seeded into `UPGRADE_FLOW`
- 1000 nuke charge for `SMOKE`, 2000 for `LAUNCH_IMPACT` and
  `CIVIL_DEFENSE`
- 40 recent-event rows in the result tail

Future steps may revise these once balance work begins.

## 14. Manual test script

```powershell
# Compile.
$env:Path = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;' + $env:Path
Get-ChildItem -Recurse -Filter *.java src\main\java |
    ForEach-Object { $_.FullName } |
    Set-Content -Encoding ASCII "$env:TEMP\tetris_sources.txt"
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"

# Run every scenario.
foreach ($sc in 'smoke','launch-impact','radar-decoy','civil-defense',
                'upgrade-flow','ai-vs-dummy','ai-vs-ai') {
    Write-Host "=== $sc ==="
    java -cp target\classes com.tetris.mab.sim.MabSimulationRunner $sc
}
```

Optional: launch the Swing UI, open the MAB debug HUD (Step 12),
click *Run Smoke Sim*, confirm a dialog appears with `success=true`.

## 15. What is intentionally NOT implemented

- Network play or any network-shaped abstraction.
- Per-tick deterministic PRNG seeded inside the harness.
- A property-based fuzzer or model checker.
- A persistent regression baseline that the harness diff'd against.
- HTML / JSON / Markdown report writers.
- Cross-scenario aggregation (e.g., a single `runAll()`).
- Headless rendering, board snapshot images, or replay export.
- Real Tetris piece play. The harness drives the strategic clock
  directly via `debugAdvanceStrategicClockOnly` rather than placing
  pieces, because Step 14 is about **strategic-layer telemetry**,
  not Tetris solver work.

## 16. Acceptance verification

Build:

```
EXITCODE=0
```

CLI run summary (one line per scenario):

| Scenario | success | events | launches | impacts | scans | decoys | civDef | upgrades | aiExec | aiSkip | invariantFailures |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| smoke         | true | 271 | 1 | 1 | 1 | 1 | 1 | 0 | 0  | 0   | 0 |
| launch-impact | true | 196 | 1 | 1 | 0 | 0 | 0 | 0 | 0  | 0   | 0 |
| radar-decoy   | true | 137 | 0 | 0 | 20| 1 | 0 | 0 | 0  | 0   | 0 |
| civil-defense | true | 201 | 1 | 1 | 0 | 0 | 1 | 0 | 0  | 0   | 0 |
| upgrade-flow  | true | 32  | 0 | 0 | 0 | 0 | 0 | 1 | 0  | 0   | 0 |
| ai-vs-dummy   | true | 281 | 7 | 0 | 0 | 4 | 0 | 0 | 25 | 35  | 0 |
| ai-vs-ai      | true | 601 | 6 | 5 | 6 | 4 | 6 | 1 | 41 | 119 | 0 |

All scenarios report `success=true` with zero invariant failures.
