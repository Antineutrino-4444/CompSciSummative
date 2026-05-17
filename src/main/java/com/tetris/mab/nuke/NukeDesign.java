package com.tetris.mab.nuke;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable strategic description of a single nuke. Lives in the
 * match layer and is deliberately separate from the UI builder's
 * {@code com.tetris.model.nuke.NukeDesign}, which is a mutable
 * slot/parts editor.
 *
 * <p>DEFCON readiness modifies <em>deployment difficulty</em>
 * (build charge required, launch countdown, impact delay) but never
 * modifies damage values ({@code blastRating}, {@code radiationRating},
 * {@code disarmRating}, {@code siloDamageRating}, {@code empRating}).
 *
 * <p>Launch route requirements (Tetris pips / spin pips) are
 * design-aware and surface via
 * {@link #effectiveLaunchTetrisGoal(int)} /
 * {@link #effectiveLaunchSpinGoal(int)}.
 *
 * <p><b>Terminology note</b> — the historical field {@code warningTime}
 * is now {@code impactDelay}. The old accessor names
 * ({@code getBaseWarningTimePieces}, {@code effectiveWarningTimePieces})
 * are kept as deprecated aliases that delegate to the new accessors so
 * older probes still compile.
 */
public final class NukeDesign {

    private final String id;
    private final String displayName;
    private final NukeDoctrineType doctrineType;
    private final NukeSizeCategory sizeCategory;

    private final int baseBuildChargeRequired;
    private final int blastRating;
    private final int radiationRating;
    private final int disarmRating;
    private final int siloDamageRating;
    private final int empRating;
    private final int baseLaunchTimePieces;
    private final int baseImpactDelayPieces;
    private final int stabilityRating;   // 0..10
    private final int complexityRating;  // 0..10

    private final List<Integer> baseLaunchCode;
    private final Map<Integer, Integer> buildChargeByDefcon;
    private final Map<Integer, List<Integer>> launchCodeByDefcon;
    private final Map<Integer, Integer> launchTimeByDefcon;
    private final Map<Integer, Integer> impactDelayByDefcon;

    private final GarbageProfile garbageProfile;
    private final DisarmProfile disarmProfile;
    private final SiloDamageProfile siloDamageProfile;
    private final EmpProfile empProfile;                              // nullable
    private final FullClearThresholdProfile fullClearThresholdProfile; // nullable

    /** Current constructor — used by the factory and adapter. */
    public NukeDesign(
            String id,
            String displayName,
            NukeDoctrineType doctrineType,
            NukeSizeCategory sizeCategory,
            int baseBuildChargeRequired,
            int blastRating,
            int radiationRating,
            int disarmRating,
            int siloDamageRating,
            int empRating,
            int baseLaunchTimePieces,
            int baseImpactDelayPieces,
            int stabilityRating,
            int complexityRating,
            List<Integer> baseLaunchCode,
            Map<Integer, Integer> buildChargeByDefcon,
            Map<Integer, List<Integer>> launchCodeByDefcon,
            Map<Integer, Integer> launchTimeByDefcon,
            Map<Integer, Integer> impactDelayByDefcon,
            GarbageProfile garbageProfile,
            DisarmProfile disarmProfile,
            SiloDamageProfile siloDamageProfile,
            EmpProfile empProfile,
            FullClearThresholdProfile fullClearThresholdProfile) {

        this.id = id;
        this.displayName = displayName;
        this.doctrineType = doctrineType;
        this.sizeCategory = sizeCategory;
        this.baseBuildChargeRequired = baseBuildChargeRequired;
        this.blastRating = blastRating;
        this.radiationRating = radiationRating;
        this.disarmRating = disarmRating;
        this.siloDamageRating = siloDamageRating;
        this.empRating = empRating;
        this.baseLaunchTimePieces = baseLaunchTimePieces;
        this.baseImpactDelayPieces = baseImpactDelayPieces;
        this.stabilityRating = stabilityRating;
        this.complexityRating = complexityRating;

        this.baseLaunchCode = baseLaunchCode == null
                ? List.of()
                : List.copyOf(baseLaunchCode);
        this.buildChargeByDefcon = buildChargeByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(buildChargeByDefcon));
        this.launchTimeByDefcon = launchTimeByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(launchTimeByDefcon));
        this.impactDelayByDefcon = impactDelayByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(impactDelayByDefcon));

        Map<Integer, List<Integer>> codeCopy = new LinkedHashMap<>();
        if (launchCodeByDefcon != null) {
            for (Map.Entry<Integer, List<Integer>> e : launchCodeByDefcon.entrySet()) {
                List<Integer> v = e.getValue();
                codeCopy.put(e.getKey(), v == null ? List.of() : List.copyOf(v));
            }
        }
        this.launchCodeByDefcon = Collections.unmodifiableMap(codeCopy);

        this.garbageProfile = garbageProfile;
        this.disarmProfile = disarmProfile;
        this.siloDamageProfile = siloDamageProfile;
        this.empProfile = empProfile;
        this.fullClearThresholdProfile = fullClearThresholdProfile;
    }

    /**
     * Legacy constructor for older call sites that still use the
     * pre-cleanup signature (with {@code baseWarningTimePieces} and a
     * {@code detectionProfile}, plus nullable {@code MirvProfile} and
     * {@code DecoyProfile}). The MIRV / decoy / detection arguments are
     * silently dropped — the current MAB design does not use them.
     */
    @Deprecated
    public NukeDesign(
            String id,
            String displayName,
            NukeDoctrineType doctrineType,
            NukeSizeCategory sizeCategory,
            int baseBuildChargeRequired,
            int blastRating,
            int radiationRating,
            int disarmRating,
            int siloDamageRating,
            int baseLaunchTimePieces,
            int baseWarningTimePieces,
            int detectionProfile /*ignored*/,
            List<Integer> baseLaunchCode,
            Map<Integer, Integer> buildChargeByDefcon,
            Map<Integer, List<Integer>> launchCodeByDefcon,
            Map<Integer, Integer> launchTimeByDefcon,
            Map<Integer, Integer> warningTimeByDefcon,
            GarbageProfile garbageProfile,
            DisarmProfile disarmProfile,
            SiloDamageProfile siloDamageProfile,
            MirvProfile mirvProfile /*ignored*/,
            EmpProfile empProfile,
            DecoyProfile decoyProfile /*ignored*/,
            FullClearThresholdProfile fullClearThresholdProfile) {
        this(id, displayName, doctrineType, sizeCategory,
                baseBuildChargeRequired,
                blastRating, radiationRating, disarmRating, siloDamageRating,
                empProfile == null ? 0 : Math.max(1, empProfile.chargeDrain() / 2),
                baseLaunchTimePieces, baseWarningTimePieces,
                /*stability*/ 3, /*complexity*/ Math.min(10, Math.max(1,
                        baseBuildChargeRequired / 12 + 1)),
                baseLaunchCode,
                buildChargeByDefcon, launchCodeByDefcon,
                launchTimeByDefcon, warningTimeByDefcon,
                garbageProfile, disarmProfile, siloDamageProfile,
                empProfile, fullClearThresholdProfile);
    }

    // ─────────────────────── Accessors ───────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public NukeDoctrineType getDoctrineType() { return doctrineType; }
    public NukeSizeCategory getSizeCategory() { return sizeCategory; }
    public int getBaseBuildChargeRequired() { return baseBuildChargeRequired; }
    public int getBlastRating() { return blastRating; }
    public int getRadiationRating() { return radiationRating; }
    public int getDisarmRating() { return disarmRating; }
    public int getSiloDamageRating() { return siloDamageRating; }
    public int getEmpRating() { return empRating; }
    public int getBaseLaunchTimePieces() { return baseLaunchTimePieces; }
    public int getBaseImpactDelayPieces() { return baseImpactDelayPieces; }
    public int getStabilityRating() { return stabilityRating; }
    public int getComplexityRating() { return complexityRating; }
    public List<Integer> getBaseLaunchCode() { return baseLaunchCode; }
    public Map<Integer, Integer> getBuildChargeByDefcon() { return buildChargeByDefcon; }
    public Map<Integer, List<Integer>> getLaunchCodeByDefcon() { return launchCodeByDefcon; }
    public Map<Integer, Integer> getLaunchTimeByDefcon() { return launchTimeByDefcon; }
    public Map<Integer, Integer> getImpactDelayByDefcon() { return impactDelayByDefcon; }
    public GarbageProfile getGarbageProfile() { return garbageProfile; }
    public DisarmProfile getDisarmProfile() { return disarmProfile; }
    public SiloDamageProfile getSiloDamageProfile() { return siloDamageProfile; }
    public EmpProfile getEmpProfile() { return empProfile; }
    public FullClearThresholdProfile getFullClearThresholdProfile() { return fullClearThresholdProfile; }

    // ── Legacy / deprecated accessors ─────────────────────────

    /** @deprecated Use {@link #getBaseImpactDelayPieces()}. */
    @Deprecated
    public int getBaseWarningTimePieces() { return baseImpactDelayPieces; }

    /** @deprecated Detection / radar removed from MAB. Always 0. */
    @Deprecated
    public int getDetectionProfile() { return 0; }

    /** @deprecated MIRV removed from MAB. Always {@code null}. */
    @Deprecated
    public MirvProfile getMirvProfile() { return null; }

    /** @deprecated Decoy removed from MAB. Always {@code null}. */
    @Deprecated
    public DecoyProfile getDecoyProfile() { return null; }

    /** @deprecated Use {@link #getImpactDelayByDefcon()}. */
    @Deprecated
    public Map<Integer, Integer> getWarningTimeByDefcon() { return impactDelayByDefcon; }

    // ─────────────────────── DEFCON-effective values ─────────

    /** Clamps to 1..5; values outside the range fall back to base. */
    private static int clampDefcon(int defconLevel) {
        if (defconLevel < 1) return 1;
        if (defconLevel > 5) return 5;
        return defconLevel;
    }

    public int effectiveBuildChargeRequired(int defconLevel) {
        Integer v = buildChargeByDefcon.get(clampDefcon(defconLevel));
        return v != null ? v : baseBuildChargeRequired;
    }

    public List<Integer> effectiveLaunchCode(int defconLevel) {
        List<Integer> v = launchCodeByDefcon.get(clampDefcon(defconLevel));
        return v != null ? v : baseLaunchCode;
    }

    public int effectiveLaunchTimePieces(int defconLevel) {
        Integer v = launchTimeByDefcon.get(clampDefcon(defconLevel));
        return v != null ? v : baseLaunchTimePieces;
    }

    /**
     * Pieces between launch and impact for an in-flight nuke — the
     * window during which the defender can spin-intercept. This is the
     * MAB design name for the legacy {@code warningTime} field; the UI
     * surfaces it as "IMPACT IN N PIECES", never as "warning time".
     */
    public int effectiveImpactDelayPieces(int defconLevel) {
        Integer v = impactDelayByDefcon.get(clampDefcon(defconLevel));
        return v != null ? v : baseImpactDelayPieces;
    }

    /** @deprecated Use {@link #effectiveImpactDelayPieces(int)}. */
    @Deprecated
    public int effectiveWarningTimePieces(int defconLevel) {
        return effectiveImpactDelayPieces(defconLevel);
    }

    // ─────────────────────── Launch route requirements ───────

    /**
     * How many Tetris clears (after armed) the attacker needs to fire
     * along the Tetris route. Light tactical designs are 3; heavy /
     * strategic are 4; doomsday and concrete/bunker are 5. DEFCON 1
     * (war footing) reduces the requirement by 1.
     */
    public int effectiveLaunchTetrisGoal(int defconLevel) {
        int base = switch (doctrineType) {
            case TACTICAL_BLAST            -> 3;
            case EMP_PAYLOAD,
                 DIRTY_PAYLOAD, DIRTY_BOMB,
                 PLACEHOLDER                -> 4;
            case HEAVY_BLAST, CLEAN_FUSION,
                 BUNKER_BUSTER, CONCRETE_BLASTER,
                 SALTED_PAYLOAD, SALTED_WARHEAD -> 5;
            case DOOMSDAY                  -> 6;
            case MIRV, DECOY_PACKAGE       -> 5; // legacy, never produced
        };
        int defcon = clampDefcon(defconLevel);
        if (defcon == 1) base = Math.max(1, base - 1);
        return base;
    }

    /**
     * How many spin clears (after armed) the attacker needs to fire
     * along the spin route. Default is 2; heavy/strategic add 1;
     * doomsday adds 2.
     */
    public int effectiveLaunchSpinGoal(int defconLevel) {
        int base = switch (doctrineType) {
            case TACTICAL_BLAST,
                 EMP_PAYLOAD,
                 DIRTY_PAYLOAD, DIRTY_BOMB,
                 PLACEHOLDER                -> 2;
            case HEAVY_BLAST, CLEAN_FUSION,
                 BUNKER_BUSTER, CONCRETE_BLASTER,
                 SALTED_PAYLOAD, SALTED_WARHEAD -> 3;
            case DOOMSDAY                  -> 4;
            case MIRV, DECOY_PACKAGE       -> 3; // legacy, never produced
        };
        int defcon = clampDefcon(defconLevel);
        if (defcon == 1) base = Math.max(1, base - 1);
        return base;
    }

    /**
     * Intercept difficulty rating — how hard it is for the defender to
     * spin-intercept this launch. Lower = easier; higher = harder.
     * Light tactical is 1; heavy/clean/dirty is 2; bunker/concrete is
     * 3; doomsday is 4.
     */
    public int interceptDifficultyRating() {
        return switch (doctrineType) {
            case TACTICAL_BLAST            -> 1;
            case EMP_PAYLOAD,
                 DIRTY_PAYLOAD, DIRTY_BOMB,
                 PLACEHOLDER                -> 2;
            case HEAVY_BLAST, CLEAN_FUSION,
                 SALTED_PAYLOAD, SALTED_WARHEAD -> 2;
            case BUNKER_BUSTER, CONCRETE_BLASTER -> 3;
            case DOOMSDAY                  -> 4;
            case MIRV, DECOY_PACKAGE       -> 3;
        };
    }

    public String toDebugString() {
        return "NukeDesign{id=" + id
                + " name=" + displayName
                + " doctrine=" + doctrineType
                + " size=" + sizeCategory
                + " build=" + baseBuildChargeRequired
                + " blast=" + blastRating
                + " rad=" + radiationRating
                + " emp=" + empRating
                + " disarm=" + disarmRating
                + " silo=" + siloDamageRating
                + " stability=" + stabilityRating
                + " complexity=" + complexityRating
                + " baseCode=" + baseLaunchCode
                + "}";
    }
}
