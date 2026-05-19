package com.tetris.mab.sim;

import com.tetris.model.nuke.NukeDesign;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task #5 — focused probe for the {@link NukeBuilderDialog} augmentations:
 * loadDesign best-effort seeding, exportBuilderDesign deep-copy,
 * addDesignChangeListener firing on edits, and absence of any
 * JFrame/JDialog/popup window when the panel is instantiated.
 *
 * <p>Intentionally narrow — does not exercise StartMenu, MAB redesign
 * overlay, or preset removal (those are later tasks).
 */
public final class MabNukeBuilderAugmentationProbe {

    private MabNukeBuilderAugmentationProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Nuke Builder Augmentation Probe ===");

        final java.awt.Window[] before = java.awt.Window.getWindows();
        final int windowsBefore = before == null ? 0 : before.length;

        final AtomicBoolean loadRoundTrips    = new AtomicBoolean(false);
        final AtomicBoolean exportIsDeepCopy  = new AtomicBoolean(false);
        final AtomicBoolean listenerFiredOnLoad = new AtomicBoolean(false);
        final AtomicInteger listenerCount     = new AtomicInteger(0);

        SwingUtilities.invokeAndWait(() -> {
            NukeBuilderDialog panel = NukeBuilderDialog.createEmbedded(() -> {});

            // ── change-listener wiring ───────────────────────────────
            Runnable listener = listenerCount::incrementAndGet;
            panel.addDesignChangeListener(listener);
            int beforeLoad = listenerCount.get();

            // ── loadDesign round-trip ────────────────────────────────
            NukeDesign seed = new NukeDesign();
            // Pick a slot/part pair that exists in NukeSlot.ALL and is
            // not the default NONE — use the CONFIGURATION slot's first
            // applicable part if any.
            NukePart desired = pickAnyNonNonePart(NukeSlot.CONFIGURATION);
            if (desired != null) seed.set(NukeSlot.CONFIGURATION, desired);
            seed.setFusionStageCount(2);
            seed.setFusionPusher(0, "Lead");

            panel.loadDesign(seed);

            if (listenerCount.get() > beforeLoad) {
                listenerFiredOnLoad.set(true);
            }

            NukeDesign liveExport = panel.exportBuilderDesign();
            boolean slotMatches = desired == null
                    || liveExport.get(NukeSlot.CONFIGURATION) == desired;
            boolean fusionMatches = liveExport.getFusionStageCount() == 2
                    && "Lead".equals(liveExport.getFusionPusher(0));
            loadRoundTrips.set(slotMatches && fusionMatches);

            // ── exportBuilderDesign deep-copy independence ───────────
            NukeDesign snapshot = panel.exportBuilderDesign();
            // Mutate the snapshot; the panel's internal design must
            // not see the change on a subsequent export.
            snapshot.setFusionStageCount(3);
            snapshot.setFusionPusher(0, "Tungsten");
            NukeDesign reExport = panel.exportBuilderDesign();
            exportIsDeepCopy.set(reExport.getFusionStageCount() == 2
                    && "Lead".equals(reExport.getFusionPusher(0)));

            panel.removeDesignChangeListener(listener);
        });

        java.awt.Window[] after = java.awt.Window.getWindows();
        int windowsAfter = after == null ? 0 : after.length;
        // Account for the shared graphics environment window if Swing
        // initialised one — only a true *new* JFrame/JDialog would
        // increase this count beyond the baseline.
        boolean noWindowsOpened = windowsAfter <= windowsBefore;

        boolean success = loadRoundTrips.get()
                && exportIsDeepCopy.get()
                && listenerFiredOnLoad.get()
                && noWindowsOpened;

        System.out.println("loadRoundTrips=" + loadRoundTrips.get());
        System.out.println("exportIsDeepCopy=" + exportIsDeepCopy.get());
        System.out.println("listenerFiredOnLoad=" + listenerFiredOnLoad.get());
        System.out.println("listenerCallCount=" + listenerCount.get());
        System.out.println("noNewWindowsOpened=" + noWindowsOpened
                + " (before=" + windowsBefore
                + ", after=" + windowsAfter + ")");
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    /** Returns the first non-NONE option for the given slot, or
     *  {@code null} if the slot has no real options. Used only to
     *  construct a non-trivial seed design for round-trip verification. */
    private static NukePart pickAnyNonNonePart(NukeSlot slot) {
        for (NukePart p : slot.getOptions()) {
            if (p != NukePart.NONE) return p;
        }
        return null;
    }
}
