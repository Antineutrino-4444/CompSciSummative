package com.tetris.mab.nuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bridge from neutral builder data ({@link BuilderNukeSpec}) to a
 * fully populated strategic {@link NukeDesign}. UI / dialog classes are
 * NOT depended on; the integration point is the plain DTO.
 *
 * <p>The current MAB design has no MIRV / decoy / radar / warning
 * concept. Older fields on {@link BuilderNukeSpec} that referenced
 * those systems are ignored by this adapter.
 */
public class NukeBuilderAdapter {

    private static final int MIN_TIME_PIECES = 3;

    /** Convert a neutral DTO into a strategic {@link NukeDesign}. */
    public NukeDesign fromBuilderSpec(BuilderNukeSpec spec) {
        if (spec == null) return NukeDesignFactory.createDefaultPlaceholder();

        // Doctrine resolution: prefer explicit doctrineType, fall back to
        // builder hint flags, and finally to PLACEHOLDER. MIRV / DECOY
        // are silently remapped to single-payload doctrines because the
        // current MAB design does not produce them.
        NukeDoctrineType doctrine = NukeDoctrineType.fromString(spec.doctrineType());
        boolean doctrineWeak = doctrine == NukeDoctrineType.PLACEHOLDER;
        if (doctrineWeak) {
            if (spec.doomsday())      doctrine = NukeDoctrineType.DOOMSDAY;
            else if (spec.salted())   doctrine = NukeDoctrineType.SALTED_PAYLOAD;
            else if (spec.bunker())   doctrine = NukeDoctrineType.BUNKER_BUSTER;
            else if (spec.dirty())    doctrine = NukeDoctrineType.DIRTY_PAYLOAD;
            else if (spec.clean())    doctrine = NukeDoctrineType.CLEAN_FUSION;
            else if (spec.emp() > 3)  doctrine = NukeDoctrineType.EMP_PAYLOAD;
            else if (spec.size() >= 60) doctrine = NukeDoctrineType.HEAVY_BLAST;
            else                      doctrine = NukeDoctrineType.TACTICAL_BLAST;
        }
        if (!doctrine.isCurrent()) {
            // Defensive: never let a legacy doctrine through.
            doctrine = NukeDoctrineType.HEAVY_BLAST;
        }

        int sizeReq = Math.max(1, spec.size() > 0 ? spec.size() : 20);
        // Complexity tax — complex designs are slower to bring online.
        int complexity = clamp(spec.complexity(), 0, 10);
        int stability  = clamp(spec.stability(), 0, 10);
        int complexityCharge = (complexity * sizeReq) / 20;        // up to +50%
        int stabilityDiscount = (stability  * sizeReq) / 40;       // up to -25%
        int build = Math.max(1, sizeReq + complexityCharge - stabilityDiscount);
        NukeSizeCategory size = NukeSizeCategory.fromBuildCharge(build);

        // speed: 0..10ish → launch/impact-delay timing; higher = faster.
        int speed = clamp(spec.speed(), 0, 10);
        int baseLaunchTime  = Math.max(MIN_TIME_PIECES, 10 - speed);
        // Complexity drags out the impact delay (longer in-flight time).
        int baseImpactDelay = Math.max(MIN_TIME_PIECES,
                10 - speed + complexity / 3 - stability / 4);

        // Synthesize a base launch code from the rating numbers, clamped to 1..4.
        List<Integer> baseCode = synthesizeBaseCode(spec, doctrine);

        Map<Integer, Integer> buildMap = NukeReadinessScaler.buildCharge(build, size,
                complexity, stability);
        Map<Integer, List<Integer>> codeMap = NukeReadinessScaler.launchCode(baseCode, size);
        Map<Integer, Integer> launchMap = NukeReadinessScaler.launchTime(baseLaunchTime, size,
                complexity, stability);
        Map<Integer, Integer> impactDelayMap = NukeReadinessScaler.impactDelay(baseImpactDelay,
                size, complexity, stability);

        // Profiles
        int blast = nonNeg(spec.blast());
        int rad = nonNeg(spec.radiation());
        int emp = nonNeg(spec.emp());
        int disarm = nonNeg(spec.disarm());
        int silo = nonNeg(spec.siloDamage());

        boolean dirtyDoctrine = doctrine == NukeDoctrineType.DIRTY_PAYLOAD
                || doctrine == NukeDoctrineType.SALTED_PAYLOAD;
        int waveCount = dirtyDoctrine ? Math.max(2, Math.min(4, 1 + rad / 2))
                : (rad >= 3 ? 1 : 0);
        int piecesBetweenWaves = dirtyDoctrine ? 4
                : (doctrine == NukeDoctrineType.DOOMSDAY ? 6 : 5);
        GarbageProfile garbage = new GarbageProfile(
                Math.max(1, blast + Math.max(0, rad / 2)),
                Math.max(1, Math.min(blast, 5)),
                waveCount > 0,
                waveCount,
                piecesBetweenWaves,
                doctrine == NukeDoctrineType.CONCRETE_BLASTER
                        || doctrine == NukeDoctrineType.BUNKER_BUSTER);

        DisarmProfile disarmP = new DisarmProfile(
                disarm,
                doctrine == NukeDoctrineType.CONCRETE_BLASTER ? 0.9 : 1.25,
                disarm >= 3,
                size == NukeSizeCategory.MICRO || size == NukeSizeCategory.TACTICAL);

        SiloDamageProfile siloP = new SiloDamageProfile(
                silo,
                silo > 0,
                doctrine == NukeDoctrineType.BUNKER_BUSTER || doctrine == NukeDoctrineType.CONCRETE_BLASTER,
                doctrine == NukeDoctrineType.EMP_PAYLOAD || doctrine == NukeDoctrineType.CONCRETE_BLASTER);

        EmpProfile empP = (doctrine == NukeDoctrineType.EMP_PAYLOAD || emp >= 3)
                ? EmpProfile.forDisruption(Math.max(3, emp + 1),
                        Math.max(1, emp / 2 + 1),
                        Math.max(1, emp / 3 + 1))
                : null;

        String id = (spec.id() == null || spec.id().isBlank())
                ? "spec_" + System.identityHashCode(spec)
                : spec.id();
        String name = (spec.name() == null || spec.name().isBlank()) ? id : spec.name();

        return new NukeDesign(
                id, name, doctrine, size,
                build, blast, rad, disarm, silo, emp,
                baseLaunchTime, baseImpactDelay,
                stability, complexity,
                baseCode,
                buildMap, codeMap, launchMap, impactDelayMap,
                garbage, disarmP, siloP, empP,
                FullClearThresholdProfile.defaults());
    }

    /**
     * Best-effort entry point for unknown builder outputs.
     */
    public NukeDesign fromExistingBuilderObject(Object builderOutput) {
        if (builderOutput instanceof BuilderNukeSpec spec) {
            return fromBuilderSpec(spec);
        }
        if (builderOutput instanceof NukeDesign nd) {
            return nd;
        }
        return NukeDesignFactory.createDefaultPlaceholder();
    }

    /** Reverse mapping for round-tripping a strategic design back to a DTO. */
    public BuilderNukeSpec toBuilderSpec(NukeDesign design) {
        if (design == null) return null;
        int speed = Math.max(0, 10 - design.getBaseLaunchTimePieces());
        return new BuilderNukeSpec(
                design.getId(),
                design.getDisplayName(),
                design.getDoctrineType().name(),
                design.getBaseBuildChargeRequired(),
                design.getBlastRating(),
                design.getRadiationRating(),
                design.getEmpRating(),
                design.getDisarmRating(),
                design.getSiloDamageRating(),
                speed,
                design.getStabilityRating(),
                design.getComplexityRating(),
                design.getDoctrineType() == NukeDoctrineType.DIRTY_PAYLOAD,
                design.getDoctrineType() == NukeDoctrineType.SALTED_PAYLOAD,
                design.getDoctrineType() == NukeDoctrineType.CLEAN_FUSION,
                design.getDoctrineType() == NukeDoctrineType.BUNKER_BUSTER,
                design.getDoctrineType() == NukeDoctrineType.DOOMSDAY);
    }

    // ──────────────────────── helpers ────────────────────────

    private static List<Integer> synthesizeBaseCode(BuilderNukeSpec spec, NukeDoctrineType doctrine) {
        List<Integer> out = new ArrayList<>();
        int blast = clamp(spec.blast(), 1, 4);
        int disarm = clamp(Math.max(1, spec.disarm()), 1, 4);
        int silo = clamp(Math.max(1, spec.siloDamage()), 1, 4);
        out.add(blast);
        out.add(disarm);
        if (doctrine == NukeDoctrineType.DOOMSDAY || spec.size() > 70) {
            out.add(silo);
            out.add(blast);
        } else if (silo > 1) {
            out.add(silo);
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int nonNeg(int v) { return v < 0 ? 0 : v; }
}
