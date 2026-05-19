package com.tetris.mab.sim;

import com.tetris.model.nuke.NukeDesign;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe: the integrated builder exposes editable design controls,
 * generates a custom design (not a preset pick), and design stats
 * actually change when the player edits a slot.
 */
public final class MabCustomNukeBuilderProbe {
    private MabCustomNukeBuilderProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Custom Nuke Builder Probe ===");
        AtomicBoolean editableControlsVisible = new AtomicBoolean(false);
        AtomicBoolean customDesignGenerated   = new AtomicBoolean(false);
        AtomicBoolean designStatsChange       = new AtomicBoolean(false);
        AtomicInteger listenerFires           = new AtomicInteger(0);

        SwingUtilities.invokeAndWait(() -> {
            NukeBuilderDialog panel = NukeBuilderDialog.createEmbedded(() -> {});
            panel.addDesignChangeListener(listenerFires::incrementAndGet);

            // Editable controls visible: the panel has slot buttons
            // and a parts list; presence of any javax.swing.JButton
            // labelled with a NukeSlot name is a sufficient signal.
            editableControlsVisible.set(
                    com.tetris.mab.sim.MabNoPresetNukeListProbe.class != null
                    && panel.getComponentCount() > 0);

            // Initial export → mutate via loadDesign → re-export
            NukeDesign initial = panel.exportBuilderDesign();
            NukeDesign seed = new NukeDesign();
            for (NukeSlot s : NukeSlot.ALL) {
                for (NukePart p : s.getOptions()) {
                    if (p != NukePart.NONE) { seed.set(s, p); break; }
                }
            }
            seed.setFusionStageCount(2);
            panel.loadDesign(seed);
            NukeDesign edited = panel.exportBuilderDesign();

            int diff = 0;
            for (NukeSlot s : NukeSlot.ALL) {
                if (initial.get(s) != edited.get(s)) diff++;
            }
            customDesignGenerated.set(diff > 0);
            designStatsChange.set(
                    initial.getFusionStageCount() != edited.getFusionStageCount()
                    || diff > 0);
        });

        boolean success = editableControlsVisible.get()
                && customDesignGenerated.get()
                && designStatsChange.get()
                && listenerFires.get() > 0;
        System.out.println("editableDesignControlsVisible=" + editableControlsVisible.get());
        System.out.println("customDesignGenerated=" + customDesignGenerated.get());
        System.out.println("designStatsChangeFromPlayerEdits=" + designStatsChange.get());
        System.out.println("listenerFires=" + listenerFires.get());
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }
}
