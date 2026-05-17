package com.tetris.mab.nuke;

/**
 * EMP payload disruption profile. Describes how an EMP-class warhead
 * disrupts the defender's <em>strategic tempo</em>:
 *
 * <ul>
 *   <li>{@link #chargeDrain()} — flat amount of opponent build-charge
 *       drained on impact.</li>
 *   <li>{@link #routeProgressLoss()} — number of launch-route pips
 *       (Tetris and/or spin) the opponent loses on impact.</li>
 *   <li>{@link #launchDelayPieces()} — extra pieces added to any
 *       opponent active launch countdown on impact.</li>
 * </ul>
 *
 * <p>EMP intentionally does NOT model radar disruption, warning
 * disruption, intel suppression, or any hidden-information system —
 * those concepts have been removed from the MAB design. EMP is a
 * gameplay-level tempo disruptor only.
 *
 * <p>The legacy {@code radarDisruptionPieces} /
 * {@code warningDisruptionPieces} fields are kept as deprecated
 * accessors that always return 0, so older code that still inspects
 * them compiles and does nothing.
 */
public record EmpProfile(
        int chargeDrain,
        int routeProgressLoss,
        int launchDelayPieces,
        boolean canFullClearWithUpgradeOnly) {

    /** Standard factory used by the adapter / factory. */
    public static EmpProfile forDisruption(int chargeDrain,
                                           int routeProgressLoss,
                                           int launchDelayPieces) {
        return new EmpProfile(chargeDrain, routeProgressLoss,
                launchDelayPieces, true);
    }

    /** No-op EMP profile. */
    public static EmpProfile none() {
        return new EmpProfile(0, 0, 0, false);
    }

    // ── Legacy / deprecated accessors ──────────────────────────
    // The historical schema named these radarDisruptionPieces /
    // warningDisruptionPieces. Both concepts have been removed from
    // the MAB design; the accessors return 0 so older callers do
    // nothing in live gameplay.

    /** @deprecated Radar / warning disruption removed from MAB. Always 0. */
    @Deprecated
    public int radarDisruptionPieces() { return 0; }

    /** @deprecated Radar / warning disruption removed from MAB. Always 0. */
    @Deprecated
    public int warningDisruptionPieces() { return 0; }
}
