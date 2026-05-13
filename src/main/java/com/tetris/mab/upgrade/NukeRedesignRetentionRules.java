package com.tetris.mab.upgrade;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.nuke.NukeSizeCategory;

/**
 * Pure helper that suggests how much existing nuke build charge should
 * be retained when a player redesigns their nuke during an upgrade
 * pause. The match coordinator forwards the chosen ratio to
 * {@code NukeBuildState.redesign(...)}.
 */
public final class NukeRedesignRetentionRules {

    private NukeRedesignRetentionRules() {}

    public static double suggestedRetentionRatio(NukeDesign oldDesign, NukeDesign newDesign) {
        if (oldDesign == null || newDesign == null) return 0.0;
        if (oldDesign.getId() != null && oldDesign.getId().equals(newDesign.getId())) return 1.0;
        NukeDoctrineType od = oldDesign.getDoctrineType();
        NukeDoctrineType nd = newDesign.getDoctrineType();
        // Special-case heavy retooling regardless of doctrine match.
        if (nd == NukeDoctrineType.CONCRETE_BLASTER
                || nd == NukeDoctrineType.EMP_PAYLOAD
                || nd == NukeDoctrineType.DOOMSDAY) return 0.60;
        if (od == NukeDoctrineType.DECOY_PACKAGE || nd == NukeDoctrineType.DECOY_PACKAGE) return 0.75;
        if (od != nd) return 0.70;
        // Same doctrine — retention depends on size delta.
        NukeSizeCategory os = oldDesign.getSizeCategory();
        NukeSizeCategory ns = newDesign.getSizeCategory();
        if (os == null || ns == null || os == ns) return 1.0;
        if (ns.ordinal() > os.ordinal()) return 0.90;     // larger
        return 1.0;                                        // smaller / equal
    }

    public static String explain(NukeDesign oldDesign, NukeDesign newDesign) {
        double r = suggestedRetentionRatio(oldDesign, newDesign);
        if (oldDesign == null || newDesign == null) return "no design (retain " + r + ")";
        return "from " + oldDesign.getId() + " (" + oldDesign.getDoctrineType()
                + "/" + oldDesign.getSizeCategory() + ") -> "
                + newDesign.getId() + " (" + newDesign.getDoctrineType()
                + "/" + newDesign.getSizeCategory() + ") retain " + r;
    }
}
