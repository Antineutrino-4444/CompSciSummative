package com.tetris.mab.ui;

import com.tetris.mab.nuke.MabNukeBuilderBridge;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Player-facing MAB setup selection for the starting nuke design.
 * Stores only abstract gameplay design data and safe summary text.
 */
public final class MabNukeDesignSelection {

    public enum Source {
        DEFAULT_DOCTRINE,
        BUILDER_DERIVED
    }

    private static final MabNukeBuilderBridge BRIDGE = new MabNukeBuilderBridge();

    private final Source source;
    private final NukeDesign design;
    private final String summary;

    private MabNukeDesignSelection(Source source, NukeDesign design, String summary) {
        this.source = source == null ? Source.DEFAULT_DOCTRINE : source;
        this.design = design == null ? NukeDesignFactory.createDefaultPlaceholder() : design;
        this.summary = (summary == null || summary.isBlank())
                ? BRIDGE.safeGameplaySummary(this.design)
                : summary;
    }

    public static MabNukeDesignSelection defaultSelection() {
        NukeDesign d = NukeDesignFactory.createDefaultPlaceholder();
        return new MabNukeDesignSelection(Source.DEFAULT_DOCTRINE, d,
                BRIDGE.safeGameplaySummary(d));
    }

    public static MabNukeDesignSelection fromMabDesign(NukeDesign design) {
        NukeDesign d = design == null ? NukeDesignFactory.createDefaultPlaceholder() : design;
        return new MabNukeDesignSelection(Source.BUILDER_DERIVED, d,
                BRIDGE.safeGameplaySummary(d));
    }

    public static MabNukeDesignSelection fromBuilderDesign(
            com.tetris.model.nuke.NukeDesign builderDesign) {
        if (builderDesign == null) return defaultSelection();
        NukeDesign d = BRIDGE.toMabDesign(builderDesign);
        return fromMabDesign(d);
    }

    public Source getSource() { return source; }
    public NukeDesign getDesign() { return design; }
    public String getSummary() { return summary; }
    public boolean isBuilderDerived() { return source == Source.BUILDER_DERIVED; }
    public boolean isSummarySafe() { return BRIDGE.isSafeGameplaySummary(summary); }

    @Override
    public String toString() {
        return summary;
    }
}
