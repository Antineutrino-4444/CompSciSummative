package com.tetris.mab.nuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bridge from neutral builder data ({@link BuilderNukeSpec}) to a
 * fully populated strategic {@link NukeDesign}. UI / dialog classes are
 * NOT depended on; the integration point is the plain DTO.
 *
 * <p>The existing in-game UI builder
 * ({@code com.tetris.view.NukeBuilderDialog} +
 * {@code com.tetris.model.nuke.NukeDesign}) is a slot/parts editor with
 * a different shape. It can be wired in later by mapping its output
 * onto a {@code BuilderNukeSpec} — that's the stable seam.
 */
public class NukeBuilderAdapter {

    private static final int MIN_TIME_PIECES = 3;

    /** Convert a neutral DTO into a strategic {@link NukeDesign}. */
    public NukeDesign fromBuilderSpec(BuilderNukeSpec spec) {
        if (spec == null) return NukeDesignFactory.createDefaultPlaceholder();

        // Doctrine resolution: the boolean flags only override when the
        // doctrine string is null, blank, or already PLACEHOLDER.
        NukeDoctrineType doctrine = NukeDoctrineType.fromString(spec.doctrineType());
        boolean doctrineWeak = doctrine == NukeDoctrineType.PLACEHOLDER;
        if (spec.mirv() && doctrineWeak)  doctrine = NukeDoctrineType.MIRV;
        if (spec.emp()  && doctrineWeak)  doctrine = NukeDoctrineType.EMP_PAYLOAD;
        if (spec.decoy() && doctrineWeak) doctrine = NukeDoctrineType.DECOY_PACKAGE;

        int build = Math.max(1, spec.size() > 0 ? spec.size() : 20);
        NukeSizeCategory size = NukeSizeCategory.fromBuildCharge(build);

        // speed: 0..10ish → launch/warning time; higher = faster.
        int speed = clamp(spec.speed(), 0, 10);
        int baseLaunchTime  = Math.max(MIN_TIME_PIECES, 10 - speed);
        int baseWarningTime = Math.max(MIN_TIME_PIECES, 10 - speed);

        // stealth: 0..10ish → detection profile; higher stealth = lower profile.
        int stealth = clamp(spec.stealth(), 0, 10);
        int detectionProfile = Math.max(0, 10 - stealth);

        // Synthesize a base launch code from the rating numbers, clamped to 1..4.
        List<Integer> baseCode = synthesizeBaseCode(spec, doctrine);

        Map<Integer, Integer> buildMap = NukeReadinessScaler.buildCharge(build, size);
        Map<Integer, List<Integer>> codeMap = NukeReadinessScaler.launchCode(baseCode, size);
        Map<Integer, Integer> launchMap = NukeReadinessScaler.launchTime(baseLaunchTime, size);
        Map<Integer, Integer> warnMap = NukeReadinessScaler.warningTime(baseWarningTime, size);

        // Profiles
        int blast = nonNeg(spec.blast());
        int rad = nonNeg(spec.radiation());
        int disarm = nonNeg(spec.disarm());
        int silo = nonNeg(spec.siloDamage());

        GarbageProfile garbage = new GarbageProfile(
                Math.max(1, blast),
                Math.max(1, Math.min(blast, 5)),
                spec.mirv(),
                spec.mirv() ? Math.max(2, blast) : 0,
                spec.mirv() ? 2 : 0,
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

        MirvProfile mirvP = spec.mirv()
                ? new MirvProfile(Math.max(2, blast), 1, 2)
                : null;
        EmpProfile empP = spec.emp()
                ? new EmpProfile(Math.max(2, stealth / 2 + 2), Math.max(2, stealth / 3 + 1), true)
                : null;
        DecoyProfile decoyP = spec.decoy()
                ? new DecoyProfile(true, true, true, Math.max(3, 6 - speed))
                : null;

        String id = (spec.id() == null || spec.id().isBlank())
                ? "spec_" + System.identityHashCode(spec)
                : spec.id();
        String name = (spec.name() == null || spec.name().isBlank()) ? id : spec.name();

        return new NukeDesign(
                id, name, doctrine, size,
                build, blast, rad, disarm, silo,
                baseLaunchTime, baseWarningTime, detectionProfile,
                baseCode,
                buildMap, codeMap, launchMap, warnMap,
                garbage, disarmP, siloP, mirvP, empP, decoyP,
                FullClearThresholdProfile.defaults());
    }

    /**
     * Best-effort entry point for unknown builder outputs. Step-3 only
     * the neutral {@link BuilderNukeSpec} path is stable; other
     * inputs fall back to the placeholder design.
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
        int stealth = Math.max(0, 10 - design.getDetectionProfile());
        return new BuilderNukeSpec(
                design.getId(),
                design.getDisplayName(),
                design.getDoctrineType().name(),
                design.getBaseBuildChargeRequired(),
                design.getBlastRating(),
                design.getRadiationRating(),
                design.getDisarmRating(),
                design.getSiloDamageRating(),
                speed,
                stealth,
                design.getMirvProfile() != null,
                design.getEmpProfile() != null,
                design.getDecoyProfile() != null);
    }

    // ──────────────────────── helpers ────────────────────────

    private static List<Integer> synthesizeBaseCode(BuilderNukeSpec spec, NukeDoctrineType doctrine) {
        List<Integer> out = new ArrayList<>();
        int blast = clamp(spec.blast(), 1, 4);
        int disarm = clamp(spec.disarm(), 1, 4);
        int silo = clamp(spec.siloDamage(), 1, 4);
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
