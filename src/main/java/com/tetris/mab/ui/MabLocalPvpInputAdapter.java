package com.tetris.mab.ui;

import com.tetris.controller.LocalInputRouter;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/**
 * Global key dispatcher for local same-keyboard MAB PvP. It routes
 * key events into {@link LocalInputRouter}, clears both players on
 * focus loss, and never models remote or networked input.
 */
public final class MabLocalPvpInputAdapter {

    private static final boolean DEBUG = Boolean.getBoolean("mab.input.debug");

    private final LocalInputRouter router;
    private final JComponent shellRoot;
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final DoctrineComboTracker p1Combo = new DoctrineComboTracker();
    private final DoctrineComboTracker p2Combo = new DoctrineComboTracker();
    private final WindowFocusListener windowFocusListener;
    private Window attachedWindow;
    private boolean installed;

    public MabLocalPvpInputAdapter(LocalInputRouter router, JComponent shellRoot) {
        if (router == null) throw new IllegalArgumentException("router");
        if (shellRoot == null) throw new IllegalArgumentException("shellRoot");
        this.router = router;
        this.shellRoot = shellRoot;
        this.windowFocusListener = new WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) { log("window gained focus"); }
            @Override public void windowLostFocus(WindowEvent e) { clearHeldKeys(); }
        };
    }

    public void install() {
        if (installed) return;
        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);
        SwingUtilities.invokeLater(this::attachWindowListenerLater);
        log("installed (2 local players)");
    }

    private void attachWindowListenerLater() {
        Window w = SwingUtilities.getWindowAncestor(shellRoot);
        if (w != null && attachedWindow == null) {
            attachedWindow = w;
            w.addWindowFocusListener(windowFocusListener);
        }
    }

    public void shutdown() {
        if (!installed) return;
        installed = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(dispatcher);
        if (attachedWindow != null) {
            try { attachedWindow.removeWindowFocusListener(windowFocusListener); }
            catch (RuntimeException ignored) {}
            attachedWindow = null;
        }
        clearHeldKeys();
        log("shutdown");
    }

    public void clearHeldKeys() {
        router.releaseAll();
    }

    public boolean isInstalled() { return installed; }

    private boolean dispatch(KeyEvent e) {
        if (!installed || e == null) return false;
        Window source = e.getComponent() == null
                ? null
                : SwingUtilities.getWindowAncestor(e.getComponent());
        if (attachedWindow != null && source != null && source != attachedWindow) return false;
        if (shellRoot instanceof MabBattleShellPanel shell
                && shell.isKeyboardModalOverlayVisible()) {
            router.releaseAll();
            return false;
        }
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                trackCombo(e.getKeyCode());
                router.keyPressed(e);
                if (DEBUG) log("press " + KeyEvent.getKeyText(e.getKeyCode()));
                return true;
            }
            case KeyEvent.KEY_RELEASED -> {
                router.keyReleased(e);
                if (DEBUG) log("release " + KeyEvent.getKeyText(e.getKeyCode()));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void trackCombo(int code) {
        com.tetris.model.Settings s = com.tetris.model.Settings.get();
        // P1
        if (code == s.getKeyRotateCW()) p1Combo.onRotateCW();
        else if (code == s.getKeyRotateCCW()) p1Combo.onRotateCCW();
        else if (code == s.getKeyHardDrop()) {
            com.tetris.mab.upgrade.draft.MabActiveDoctrineType d = p1Combo.onHardDrop();
            if (d != null && shellRoot instanceof MabBattleShellPanel shell)
                shell.fireActiveDoctrine(com.tetris.mab.ParticipantId.PLAYER_A, d);
        }
        // P2
        if (code == s.getKeyP2RotateCW()) p2Combo.onRotateCW();
        else if (code == s.getKeyP2RotateCCW()) p2Combo.onRotateCCW();
        else if (code == s.getKeyP2HardDrop()) {
            com.tetris.mab.upgrade.draft.MabActiveDoctrineType d = p2Combo.onHardDrop();
            if (d != null && shellRoot instanceof MabBattleShellPanel shell)
                shell.fireActiveDoctrine(com.tetris.mab.ParticipantId.PLAYER_B, d);
        }
    }

    private static void log(String message) {
        if (DEBUG) System.out.println("[MAB-PVP-INPUT] " + message);
    }
}
