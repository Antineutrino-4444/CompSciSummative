package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.nuke.MabNukeBuilderBridge;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabDefconRedesignOverlayPanel;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.model.GameState;
import com.tetris.model.nuke.NukeDesign;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;
import com.tetris.view.GamePanel;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Probe: mid-game redesign loads the current design (closest editable
 * approximation), cancel preserves the previous design unchanged, and
 * confirm applies the edited design exactly once.
 */
public final class MabMidgameRedesignUsesCurrentDesignProbe {
    private MabMidgameRedesignUsesCurrentDesignProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Midgame Redesign Uses Current Design Probe ===");

        boolean[] loadsCurrent = {false};
        boolean[] cancelPreserves = {false};
        AtomicInteger confirmFires = new AtomicInteger();
        AtomicReference<MabNukeDesignSelection> confirmedSel = new AtomicReference<>();

        GameState p1s = new GameState(0);
        GameState p2s = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                .createPveShared(p1s, p2s, MatchDifficulty.NORMAL, 42L);
        SwingUtilities.invokeAndWait(() -> {
            GamePanel pg = new GamePanel(p1s);
            MabOpponentBoardPanel opp = new MabOpponentBoardPanel(p2s);
            MabBattleShellPanel shell = new MabBattleShellPanel(
                    pg, p1s, opp,
                    ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                    "P1", "AI",
                    MabBattleShellPanel.defaultModeLine(null),
                    MabBattleShellPanel.defaultModeSub(),
                    () -> {});
            shell.setSize(new Dimension(1366, 768));
            shell.doLayout();

            // Seed the participant with a non-default MAB design and
            // also stash a non-trivial builder source. The builder
            // does some slot-applicability re-sync on refresh (e.g.
            // the SECONDARY slot is driven by the fusion sub-builder
            // when CONFIGURATION is two-stage), so we only assert
            // round-trip on CONFIGURATION + a stable slot like
            // FISSILE where no auto-sync runs.
            com.tetris.mab.nuke.NukeDesign mabSeed = NukeDesignFactory.createDefaultTacticalBlast();
            match.getParticipant(ParticipantId.PLAYER_A).getNukeBuildState()
                    .setDesign(mabSeed, 5);
            NukeDesign builderSeed = new NukeDesign();
            NukePart cfgPart = firstRealPart(NukeSlot.CONFIGURATION);
            NukePart fisPart = firstRealPart(NukeSlot.FISSILE);
            if (cfgPart != null) builderSeed.set(NukeSlot.CONFIGURATION, cfgPart);
            if (fisPart != null) builderSeed.set(NukeSlot.FISSILE, fisPart);

            // ── 1. Cancel preserves ─────────────────────────────────
            com.tetris.mab.nuke.NukeDesign beforeCancel = match
                    .getParticipant(ParticipantId.PLAYER_A).getNukeBuildState().getCurrentDesign();
            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - CANCEL", 4,
                    builderSeed, null, false,
                    s -> confirmFires.incrementAndGet(),
                    () -> {});
            MabDefconRedesignOverlayPanel overlay = shell.getDefconRedesignOverlay();
            NukeBuilderDialog builder = overlay.getEmbeddedBuilderForProbe();
            // loadsCurrent: the builder seeded from builderSeed → its
            // exported design should match the seed slot-for-slot.
            if (builder != null) {
                NukeDesign exported = builder.exportBuilderDesign();
                boolean cfgOk = cfgPart == null
                        || exported.get(NukeSlot.CONFIGURATION) == cfgPart;
                boolean fisOk = fisPart == null
                        || exported.get(NukeSlot.FISSILE) == fisPart;
                loadsCurrent[0] = cfgOk && fisOk;
            }
            JButton cancel = findButton(overlay, "CANCEL");
            if (cancel != null) cancel.doClick();
            com.tetris.mab.nuke.NukeDesign afterCancel = match
                    .getParticipant(ParticipantId.PLAYER_A).getNukeBuildState().getCurrentDesign();
            cancelPreserves[0] = beforeCancel == afterCancel
                    && confirmFires.get() == 0;

            // ── 2. Confirm applies once ─────────────────────────────
            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - CONFIRM", 4,
                    builderSeed, null, false,
                    s -> { confirmFires.incrementAndGet(); confirmedSel.set(s); },
                    () -> {});
            overlay.triggerConfirmForProbe();
            overlay.triggerConfirmForProbe();
            shell.hideDefconRedesignOverlay();
        });

        boolean confirmOnce = confirmFires.get() == 1;
        boolean confirmAppliedEdited = confirmedSel.get() != null
                && confirmedSel.get().getBuilderSource() != null;
        boolean success = loadsCurrent[0]
                && cancelPreserves[0]
                && confirmOnce
                && confirmAppliedEdited;

        System.out.println("loadsCurrentDesign=" + loadsCurrent[0]);
        System.out.println("cancelPreservesOldDesign=" + cancelPreserves[0]);
        System.out.println("confirmAppliesEditedDesignOnce=" + (confirmOnce && confirmAppliedEdited));
        System.out.println("confirmFires=" + confirmFires.get());
        System.out.println("success=" + success);
        System.exit(success ? 0 : 1);
    }

    private static NukePart firstRealPart(NukeSlot s) {
        for (NukePart p : s.getOptions()) {
            if (p != NukePart.NONE) return p;
        }
        return null;
    }

    private static JButton findButton(Container root, String text) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container cc) {
                JButton hit = findButton(cc, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
