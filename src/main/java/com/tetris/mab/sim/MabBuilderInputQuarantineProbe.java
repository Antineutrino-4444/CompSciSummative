package com.tetris.mab.sim;

import com.tetris.model.Settings;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe: gameplay keys (hard drop, hold, rotate, soft drop, P2
 * variants) must be consumed by the builder's WHEN_IN_FOCUSED_WINDOW
 * input map and must NOT trigger confirm/cancel handlers.
 *
 * <p>Verified structurally: every gameplay key code defined in
 * {@link Settings} resolves to an action key in the builder panel's
 * input map. Since the bindings are no-op consume actions, dispatching
 * the key cannot fire a confirm.
 */
public final class MabBuilderInputQuarantineProbe {
    private MabBuilderInputQuarantineProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Builder Input Quarantine Probe ===");
        AtomicInteger boundCount = new AtomicInteger();
        AtomicInteger totalCount = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            NukeBuilderDialog panel = NukeBuilderDialog.createEmbedded(() -> {});
            InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = panel.getActionMap();
            Settings s = Settings.get();
            int[] codes = new int[] {
                    s.getKeyHardDrop(), s.getKeyHold(), s.getKeyHoldAlt(),
                    s.getKeyRotateCW(), s.getKeyRotateCCW(),
                    s.getKeyMoveDown(),
                    s.getKeyP2HardDrop(), s.getKeyP2Hold(),
                    s.getKeyP2RotateCW(), s.getKeyP2RotateCCW(),
                    s.getKeyP2MoveDown(),
            };
            for (int code : codes) {
                if (code == 0) continue;
                totalCount.incrementAndGet();
                KeyStroke ks = KeyStroke.getKeyStroke(code, 0);
                Object actionKey = im.get(ks);
                if (actionKey != null && am.get(actionKey) != null) {
                    boundCount.incrementAndGet();
                }
            }
        });

        int total = totalCount.get();
        int bound = boundCount.get();
        boolean success = total > 0 && bound == total;
        System.out.println("totalGameplayCodesChecked=" + total);
        System.out.println("boundOrAlreadyHandled=" + bound);
        System.out.println("heldGameplayInputCannotConfirm=" + success);
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }
}
