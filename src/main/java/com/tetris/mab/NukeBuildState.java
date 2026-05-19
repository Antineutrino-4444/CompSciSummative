package com.tetris.mab;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-participant nuke build progress. Wires the participant's currently
 * equipped {@link NukeDesign} to the live charge requirement and exposes
 * the design's other tempo properties (launch countdown, impact delay,
 * launch route requirements) for callers that need them.
 *
 * <p><b>Design-specific charge:</b>
 * {@link #getEffectiveBuildChargeRequired()} is always read from the
 * current {@link NukeDesign} and the current DEFCON level. It is never
 * a hardcoded universal value.
 */
public class NukeBuildState {

    /** Initial DEFCON used until the match coordinator refreshes us. */
    private static final int DEFAULT_DEFCON_LEVEL = 5;

    private int currentBuildCharge;
    private int effectiveBuildChargeRequired;
    private int currentDefconLevel = DEFAULT_DEFCON_LEVEL;
    private boolean armed;
    private NukeDesign currentDesign;
    private int overbuiltCharge;
    private long designRevision;
    private final List<DesignHistoryEntry> designHistory = new ArrayList<>();

    public NukeBuildState() {
        this(NukeDesignFactory.createDefaultPlaceholder(), DEFAULT_DEFCON_LEVEL);
    }

    public NukeBuildState(NukeDesign initialDesign, int initialDefconLevel) {
        this.currentDesign = (initialDesign != null)
                ? initialDesign
                : NukeDesignFactory.createDefaultPlaceholder();
        this.currentDefconLevel = clampDefcon(initialDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        this.currentBuildCharge = 0;
        this.overbuiltCharge = 0;
        this.armed = false;
        recordDesignChange(null, this.currentDesign, this.currentDefconLevel,
                1.0, 0, 0, "initial");
    }

    // ─────────────────────── Mutation ────────────────────────

    /** Replaces the current design and refreshes effective charge. */
    public void setDesign(NukeDesign design, int currentDefconLevel) {
        NukeDesign oldDesign = this.currentDesign;
        int chargeBefore = this.currentBuildCharge;
        applyDesign(design, currentDefconLevel);
        recordDesignChange(oldDesign, this.currentDesign, this.currentDefconLevel,
                1.0, chargeBefore, this.currentBuildCharge, "set");
    }

    /**
     * Switch to a new design while keeping a fraction of the charge
     * already spent on the old one.
     */
    public void redesign(NukeDesign newDesign, int currentDefconLevel, double retainedChargeRatio) {
        double r = retainedChargeRatio;
        if (r < 0.0) r = 0.0;
        if (r > 1.0) r = 1.0;
        NukeDesign oldDesign = this.currentDesign;
        int chargeBefore = this.currentBuildCharge;
        this.currentBuildCharge = (int) Math.floor(this.currentBuildCharge * r);
        applyDesign(newDesign, currentDefconLevel);
        recordDesignChange(oldDesign, this.currentDesign, this.currentDefconLevel,
                r, chargeBefore, this.currentBuildCharge, "redesign");
    }

    /** Recomputes effective build charge for the (possibly changed) DEFCON. */
    public void refreshForDefcon(int currentDefconLevel) {
        this.currentDefconLevel = clampDefcon(currentDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        recomputeArmedAndOverbuilt();
    }

    /** Adds nuke build charge. Negative or zero amounts are ignored. */
    public void addCharge(int amount) {
        if (amount <= 0) return;
        currentBuildCharge += amount;
        recomputeArmedAndOverbuilt();
    }

    /** Reduces nuke build charge by {@code amount}, clamped at 0. */
    public void reduceCharge(int amount) {
        if (amount <= 0) return;
        currentBuildCharge = Math.max(0, currentBuildCharge - amount);
        recomputeArmedAndOverbuilt();
    }

    /** Resets accumulated charge after a nuke is fired. */
    public void resetCharge() {
        currentBuildCharge = 0;
        overbuiltCharge = 0;
        armed = false;
    }

    private void recomputeArmedAndOverbuilt() {
        if (effectiveBuildChargeRequired <= 0) {
            armed = currentBuildCharge > 0;
            overbuiltCharge = Math.max(0, currentBuildCharge);
            return;
        }
        armed = currentBuildCharge >= effectiveBuildChargeRequired;
        overbuiltCharge = Math.max(0, currentBuildCharge - effectiveBuildChargeRequired);
    }

    // ─────────────────────── Accessors ───────────────────────

    private void applyDesign(NukeDesign design, int currentDefconLevel) {
        this.currentDesign = (design != null)
                ? design
                : NukeDesignFactory.createDefaultPlaceholder();
        this.currentDefconLevel = clampDefcon(currentDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        recomputeArmedAndOverbuilt();
    }

    private void recordDesignChange(NukeDesign oldDesign,
                                    NukeDesign newDesign,
                                    int defconLevel,
                                    double retainedChargeRatio,
                                    int chargeBefore,
                                    int chargeAfter,
                                    String source) {
        if (newDesign == null) return;
        designHistory.add(new DesignHistoryEntry(
                ++designRevision,
                source == null ? "" : source,
                oldDesign == null ? null : oldDesign.getId(),
                oldDesign == null ? null : oldDesign.getDisplayName(),
                newDesign.getId(),
                newDesign.getDisplayName(),
                newDesign.getDoctrineType() == null ? "" : newDesign.getDoctrineType().name(),
                newDesign.getSizeCategory() == null ? "" : newDesign.getSizeCategory().name(),
                clampDefcon(defconLevel),
                retainedChargeRatio,
                chargeBefore,
                chargeAfter));
    }

    public NukeDesign getCurrentDesign() { return currentDesign; }
    public int getCurrentBuildCharge() { return currentBuildCharge; }
    public int getEffectiveBuildChargeRequired() { return effectiveBuildChargeRequired; }
    public int getCurrentDefconLevel() { return currentDefconLevel; }
    public boolean isArmed() { return armed; }
    public int getOverbuiltCharge() { return overbuiltCharge; }
    public long getDesignRevision() { return designRevision; }
    public List<DesignHistoryEntry> getDesignHistory() {
        return List.copyOf(designHistory);
    }

    /** Launch countdown (in pieces) for the current design + DEFCON. */
    public int getEffectiveLaunchCountdownPieces() {
        return currentDesign.effectiveLaunchTimePieces(currentDefconLevel);
    }

    /**
     * Pieces between launch and impact for the current design + DEFCON
     * (intercept-window length). Player-facing as "IMPACT IN N PIECES",
     * never with retired delay terminology.
     */
    public int getEffectiveImpactDelayPieces() {
        return currentDesign.effectiveImpactDelayPieces(currentDefconLevel);
    }

    /** Tetris-route launch goal for the current design + DEFCON. */
    public int getEffectiveLaunchTetrisGoal() {
        return currentDesign.effectiveLaunchTetrisGoal(currentDefconLevel);
    }

    /** Spin-route launch goal for the current design + DEFCON. */
    public int getEffectiveLaunchSpinGoal() {
        return currentDesign.effectiveLaunchSpinGoal(currentDefconLevel);
    }

    private static int clampDefcon(int level) {
        if (level < 1) return 1;
        if (level > 5) return 5;
        return level;
    }

    public String toDebugString() {
        return "NukeBuild{" + currentBuildCharge + "/" + effectiveBuildChargeRequired
                + " armed=" + armed
                + " design=" + currentDesign.getId()
                + " name=" + currentDesign.getDisplayName()
                + " doctrine=" + currentDesign.getDoctrineType()
                + " size=" + currentDesign.getSizeCategory()
                + " defcon=" + currentDefconLevel
                + " launchT=" + getEffectiveLaunchCountdownPieces()
                + " impactT=" + getEffectiveImpactDelayPieces()
                + " tetrisGoal=" + getEffectiveLaunchTetrisGoal()
                + " spinGoal=" + getEffectiveLaunchSpinGoal()
                + " overbuilt=" + overbuiltCharge
                + " designRevision=" + designRevision
                + "}";
    }

    /** Immutable per-participant design-history row. */
    public static final class DesignHistoryEntry {
        private final long revision;
        private final String source;
        private final String previousDesignId;
        private final String previousDisplayName;
        private final String designId;
        private final String displayName;
        private final String doctrineType;
        private final String sizeCategory;
        private final int defconLevel;
        private final double retainedChargeRatio;
        private final int chargeBefore;
        private final int chargeAfter;

        private DesignHistoryEntry(long revision,
                                   String source,
                                   String previousDesignId,
                                   String previousDisplayName,
                                   String designId,
                                   String displayName,
                                   String doctrineType,
                                   String sizeCategory,
                                   int defconLevel,
                                   double retainedChargeRatio,
                                   int chargeBefore,
                                   int chargeAfter) {
            this.revision = revision;
            this.source = source;
            this.previousDesignId = previousDesignId;
            this.previousDisplayName = previousDisplayName;
            this.designId = designId;
            this.displayName = displayName;
            this.doctrineType = doctrineType;
            this.sizeCategory = sizeCategory;
            this.defconLevel = defconLevel;
            this.retainedChargeRatio = retainedChargeRatio;
            this.chargeBefore = chargeBefore;
            this.chargeAfter = chargeAfter;
        }

        public long revision() { return revision; }
        public String source() { return source; }
        public String previousDesignId() { return previousDesignId; }
        public String previousDisplayName() { return previousDisplayName; }
        public String designId() { return designId; }
        public String displayName() { return displayName; }
        public String doctrineType() { return doctrineType; }
        public String sizeCategory() { return sizeCategory; }
        public int defconLevel() { return defconLevel; }
        public double retainedChargeRatio() { return retainedChargeRatio; }
        public int chargeBefore() { return chargeBefore; }
        public int chargeAfter() { return chargeAfter; }

        @Override
        public String toString() {
            return "DesignHistoryEntry{"
                    + "revision=" + revision
                    + ", source='" + source + '\''
                    + ", previousDesignId='" + previousDesignId + '\''
                    + ", designId='" + designId + '\''
                    + ", defconLevel=" + defconLevel
                    + ", retainedChargeRatio=" + retainedChargeRatio
                    + ", chargeBefore=" + chargeBefore
                    + ", chargeAfter=" + chargeAfter
                    + '}';
        }
    }
}
