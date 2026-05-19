package com.tetris.mab.sim;

import com.tetris.model.Settings;
import com.tetris.model.nuke.NukeDesign;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe: Nuke Builder keyboard navigation accepts arrow keys plus both
 * players' configured movement keys, and an implicitly highlighted
 * option is committed through the same refresh/listener path as an
 * explicit part change.
 */
public final class MabBuilderKeyboardNavigationProbe {
    private MabBuilderKeyboardNavigationProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Builder Keyboard Navigation Probe ===");

        AtomicBoolean arrowPath = new AtomicBoolean(false);
        AtomicBoolean p1Path = new AtomicBoolean(false);
        AtomicBoolean p2Path = new AtomicBoolean(false);
        AtomicBoolean allNavBindingsPresent = new AtomicBoolean(false);

        SwingUtilities.invokeAndWait(() -> {
            Settings s = Settings.get();

            NukeBuilderDialog bindingPanel = seededPanel();
            allNavBindingsPresent.set(
                    isNavBinding(bindingPanel, KeyEvent.VK_LEFT)
                    && isNavBinding(bindingPanel, KeyEvent.VK_RIGHT)
                    && isNavBinding(bindingPanel, KeyEvent.VK_DOWN)
                    && isNavBinding(bindingPanel, KeyEvent.VK_UP)
                    && isNavBinding(bindingPanel, s.getKeyMoveLeft())
                    && isNavBinding(bindingPanel, s.getKeyMoveRight())
                    && isNavBinding(bindingPanel, s.getKeyMoveDown())
                    && isNavBinding(bindingPanel, s.getKeyMoveUp())
                    && isNavBinding(bindingPanel, s.getKeyP2MoveLeft())
                    && isNavBinding(bindingPanel, s.getKeyP2MoveRight())
                    && isNavBinding(bindingPanel, s.getKeyP2MoveDown())
                    && isNavBinding(bindingPanel, s.getKeyP2MoveUp()));

            arrowPath.set(exerciseNavigationPath(
                    KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT,
                    KeyEvent.VK_UP, KeyEvent.VK_LEFT));

            p1Path.set(exerciseNavigationPath(
                    s.getKeyMoveDown(), s.getKeyMoveRight(),
                    s.getKeyMoveUp(), s.getKeyMoveLeft()));

            p2Path.set(exerciseNavigationPath(
                    s.getKeyP2MoveDown(), s.getKeyP2MoveRight(),
                    s.getKeyP2MoveUp(), s.getKeyP2MoveLeft()));
        });

        boolean success = allNavBindingsPresent.get()
                && arrowPath.get()
                && p1Path.get()
                && p2Path.get();

        System.out.println("allNavigationBindingsPresent=" + allNavBindingsPresent.get());
        System.out.println("arrowKeysNavigateAndCommit=" + arrowPath.get());
        System.out.println("player1MoveKeysNavigateAndCommit=" + p1Path.get());
        System.out.println("player2MoveKeysNavigateAndCommit=" + p2Path.get());
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static boolean exerciseNavigationPath(int down, int right, int up, int left) {
        NukeBuilderDialog panel = seededPanel();
        AtomicInteger listenerCount = new AtomicInteger(0);
        panel.addDesignChangeListener(listenerCount::incrementAndGet);

        NukePart fissile0 = nthPart(NukeSlot.FISSILE, 0);
        NukePart fissile1 = nthPart(NukeSlot.FISSILE, 1);
        NukePart tamper0 = nthPart(NukeSlot.TAMPER, 0);
        if (fissile0 == null || fissile1 == null || tamper0 == null) return false;

        int beforeImplicit = listenerCount.get();
        if (!fireKey(panel, down)) return false;
        NukeDesign afterImplicit = panel.exportBuilderDesign();
        boolean implicitHighlightedSelectionCounted =
                afterImplicit.get(NukeSlot.FISSILE) == fissile0
                && listenerCount.get() > beforeImplicit;

        if (!fireKey(panel, right)) return false;
        if (!fireKey(panel, down)) return false;
        boolean downInsideParts = panel.exportBuilderDesign().get(NukeSlot.FISSILE) == fissile1;

        if (!fireKey(panel, up)) return false;
        boolean upInsideParts = panel.exportBuilderDesign().get(NukeSlot.FISSILE) == fissile0;

        if (!fireKey(panel, left)) return false;
        int beforeNextSlot = listenerCount.get();
        if (!fireKey(panel, down)) return false;
        NukeDesign afterNextSlot = panel.exportBuilderDesign();
        boolean leftReturnedToSlots =
                afterNextSlot.get(NukeSlot.TAMPER) == tamper0
                && listenerCount.get() > beforeNextSlot;

        return implicitHighlightedSelectionCounted
                && downInsideParts
                && upInsideParts
                && leftReturnedToSlots;
    }

    private static NukeBuilderDialog seededPanel() {
        NukeBuilderDialog panel = NukeBuilderDialog.createEmbedded(() -> {});
        NukeDesign seed = new NukeDesign();
        NukePart cfg = tellerUlamConfig();
        if (cfg != null) seed.set(NukeSlot.CONFIGURATION, cfg);
        panel.loadDesign(seed);
        return panel;
    }

    private static boolean fireKey(NukeBuilderDialog panel, int keyCode) {
        if (panel == null || keyCode == 0) return false;
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, 0);
        InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();
        Object actionKey = im.get(ks);
        if (actionKey == null) return false;
        Action action = am.get(actionKey);
        if (action == null) return false;
        action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED,
                String.valueOf(actionKey)));
        return true;
    }

    private static boolean isNavBinding(NukeBuilderDialog panel, int keyCode) {
        if (panel == null || keyCode == 0) return false;
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, 0);
        Object actionKey = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(ks);
        return actionKey != null && String.valueOf(actionKey).startsWith("builder.nav.");
    }

    private static NukePart tellerUlamConfig() {
        for (NukePart p : NukeSlot.CONFIGURATION.getOptions()) {
            if (p != NukePart.NONE && p.getName().startsWith("Teller-Ulam")) return p;
        }
        return null;
    }

    private static NukePart nthPart(NukeSlot slot, int index) {
        int seen = 0;
        for (NukePart p : slot.getOptions()) {
            if (p == NukePart.NONE) continue;
            if (seen == index) return p;
            seen++;
        }
        return null;
    }
}
