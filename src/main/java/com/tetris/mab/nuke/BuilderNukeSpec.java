package com.tetris.mab.nuke;

/**
 * Neutral, immutable DTO bridging the UI Nuke Builder
 * ({@code com.tetris.model.nuke}, etc.) and the strategic
 * {@link NukeDesign} schema. The builder produces a
 * {@code BuilderNukeSpec}; {@link NukeBuilderAdapter} converts it into
 * a fully populated {@link NukeDesign}.
 *
 * <p>Plain-data only — no UI types, no game-engine references.
 *
 * <p><b>Field-mapping guidelines</b>
 * <ul>
 *   <li>{@code size} affects charge requirement and yield class.</li>
 *   <li>{@code blast} affects immediate garbage.</li>
 *   <li>{@code radiation} affects delayed waves and messy garbage patterns.</li>
 *   <li>{@code emp} affects strategic disruption (charge drain / route /
 *       launch delay).</li>
 *   <li>{@code disarm} affects opponent charge reduction.</li>
 *   <li>{@code siloDamage} affects silo / infrastructure damage.</li>
 *   <li>{@code speed} affects launch countdown and charge efficiency.</li>
 *   <li>{@code stability} reduces complexity penalties / improves reliability.</li>
 *   <li>{@code complexity} increases charge requirement and DEFCON sensitivity.</li>
 *   <li>{@code dirty} / {@code salted} / {@code clean} / {@code bunker} /
 *       {@code doomsday} are doctrine hints used when {@code doctrineType}
 *       is null/blank/{@code PLACEHOLDER}.</li>
 * </ul>
 *
 * <p><b>Removed legacy fields (no longer accepted):</b> {@code mirv},
 * {@code decoy}, {@code stealth}. Old callers that still pass them
 * should use the legacy constructor below; the values are ignored.
 */
public record BuilderNukeSpec(
        String id,
        String name,
        String doctrineType,
        int size,
        int blast,
        int radiation,
        int emp,
        int disarm,
        int siloDamage,
        int speed,
        int stability,
        int complexity,
        boolean dirty,
        boolean salted,
        boolean clean,
        boolean bunker,
        boolean doomsday) {

    /**
     * Legacy compatibility constructor for older call sites that still
     * use the pre-cleanup field shape (with {@code stealth},
     * {@code mirv}, {@code decoy}). The deprecated fields are silently
     * ignored — they never affect the resulting strategic design.
     */
    @Deprecated
    public BuilderNukeSpec(
            String id,
            String name,
            String doctrineType,
            int size,
            int blast,
            int radiation,
            int disarm,
            int siloDamage,
            int speed,
            int stealth /*ignored*/,
            boolean mirv /*ignored*/,
            boolean emp,
            boolean decoy /*ignored*/) {
        this(id, name, doctrineType, size, blast, radiation,
                emp ? Math.max(3, 1 + radiation) : 0,
                disarm, siloDamage, speed,
                /*stability*/ 3, /*complexity*/ Math.max(1, Math.min(10, size / 10 + 1)),
                false, false, false, false, false);
    }

    /**
     * Always {@code false} — MIRV / split-payload designs are not produced
     * by current code and are not part of the MAB doctrine model.
     */
    @Deprecated
    public boolean mirv() { return false; }

    /**
     * Always {@code false} — decoy / fake-launch designs are not produced
     * by current code and are not part of the MAB doctrine model.
     */
    @Deprecated
    public boolean decoy() { return false; }

    /**
     * Always {@code 0} — stealth was a radar-era concept and has been
     * removed; the MAB design intentionally does not expose stealth /
     * detection as a player-facing concept.
     */
    @Deprecated
    public int stealth() { return 0; }
}
