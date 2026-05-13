package com.tetris.mab.nuke;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable strategic description of a single nuke. This is the
 * Step-3 schema used by the match layer; it is deliberately separate
 * from the UI builder's {@code com.tetris.model.nuke.NukeDesign},
 * which is a mutable slot/parts editor.
 *
 * <p>DEFCON readiness modifies <em>deployment difficulty</em>
 * (build charge required, launch code, launch time, warning time)
 * but never modifies damage values
 * ({@code blastRating}, {@code radiationRating}, {@code disarmRating},
 * {@code siloDamageRating}).
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
    private final int baseLaunchTimePieces;
    private final int baseWarningTimePieces;
    private final int detectionProfile;

    private final List<Integer> baseLaunchCode;
    private final Map<Integer, Integer> buildChargeByDefcon;
    private final Map<Integer, List<Integer>> launchCodeByDefcon;
    private final Map<Integer, Integer> launchTimeByDefcon;
    private final Map<Integer, Integer> warningTimeByDefcon;

    private final GarbageProfile garbageProfile;
    private final DisarmProfile disarmProfile;
    private final SiloDamageProfile siloDamageProfile;
    private final MirvProfile mirvProfile;                       // nullable
    private final EmpProfile empProfile;                         // nullable
    private final DecoyProfile decoyProfile;                     // nullable
    private final FullClearThresholdProfile fullClearThresholdProfile; // nullable

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
            int detectionProfile,
            List<Integer> baseLaunchCode,
            Map<Integer, Integer> buildChargeByDefcon,
            Map<Integer, List<Integer>> launchCodeByDefcon,
            Map<Integer, Integer> launchTimeByDefcon,
            Map<Integer, Integer> warningTimeByDefcon,
            GarbageProfile garbageProfile,
            DisarmProfile disarmProfile,
            SiloDamageProfile siloDamageProfile,
            MirvProfile mirvProfile,
            EmpProfile empProfile,
            DecoyProfile decoyProfile,
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
        this.baseLaunchTimePieces = baseLaunchTimePieces;
        this.baseWarningTimePieces = baseWarningTimePieces;
        this.detectionProfile = detectionProfile;

        this.baseLaunchCode = baseLaunchCode == null
                ? List.of()
                : List.copyOf(baseLaunchCode);
        this.buildChargeByDefcon = buildChargeByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(buildChargeByDefcon));
        this.launchTimeByDefcon = launchTimeByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(launchTimeByDefcon));
        this.warningTimeByDefcon = warningTimeByDefcon == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(warningTimeByDefcon));

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
        this.mirvProfile = mirvProfile;
        this.empProfile = empProfile;
        this.decoyProfile = decoyProfile;
        this.fullClearThresholdProfile = fullClearThresholdProfile;
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
    public int getBaseLaunchTimePieces() { return baseLaunchTimePieces; }
    public int getBaseWarningTimePieces() { return baseWarningTimePieces; }
    public int getDetectionProfile() { return detectionProfile; }
    public List<Integer> getBaseLaunchCode() { return baseLaunchCode; }
    public Map<Integer, Integer> getBuildChargeByDefcon() { return buildChargeByDefcon; }
    public Map<Integer, List<Integer>> getLaunchCodeByDefcon() { return launchCodeByDefcon; }
    public Map<Integer, Integer> getLaunchTimeByDefcon() { return launchTimeByDefcon; }
    public Map<Integer, Integer> getWarningTimeByDefcon() { return warningTimeByDefcon; }
    public GarbageProfile getGarbageProfile() { return garbageProfile; }
    public DisarmProfile getDisarmProfile() { return disarmProfile; }
    public SiloDamageProfile getSiloDamageProfile() { return siloDamageProfile; }
    public MirvProfile getMirvProfile() { return mirvProfile; }
    public EmpProfile getEmpProfile() { return empProfile; }
    public DecoyProfile getDecoyProfile() { return decoyProfile; }
    public FullClearThresholdProfile getFullClearThresholdProfile() { return fullClearThresholdProfile; }

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

    public int effectiveWarningTimePieces(int defconLevel) {
        Integer v = warningTimeByDefcon.get(clampDefcon(defconLevel));
        return v != null ? v : baseWarningTimePieces;
    }

    public String toDebugString() {
        return "NukeDesign{id=" + id
                + " name=" + displayName
                + " doctrine=" + doctrineType
                + " size=" + sizeCategory
                + " build=" + baseBuildChargeRequired
                + " blast=" + blastRating
                + " rad=" + radiationRating
                + " disarm=" + disarmRating
                + " silo=" + siloDamageRating
                + " baseCode=" + baseLaunchCode
                + "}";
    }
}
