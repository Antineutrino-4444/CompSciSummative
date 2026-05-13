package com.tetris.mab.ui;

import com.tetris.controller.InputHandler;
import com.tetris.model.Settings;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/**
 * Step 23 Control Refinement \u2014 MAB battle shell input adapter.
 *
 * <p>The original Step 23 wiring relied on the player {@code GamePanel}
 * holding keyboard focus to receive directional input. After the
 * battle shell was added, several focusable components (BACK button,
 * result overlay RESTART/BACK buttons, the panel hosting various
 * children) could steal focus. Worse, when focus shifted mid-press,
 * the matching {@code keyReleased} event was delivered to the new
 * focus owner instead of the {@link InputHandler}, leaving directions
 * "stuck" in the held-key set so the piece kept shifting one way
 * forever.
 *
 * <p>This adapter installs a global {@link KeyEventDispatcher} with
 * the active {@link KeyboardFocusManager}. While installed, the
 * adapter sees every {@link KeyEvent} dispatched anywhere in the JVM
 * BEFORE the normal focus-owner dispatch. We forward presses and
 * releases directly to the existing {@link InputHandler}, so input
 * is robust to focus changes, button clicks, overlay popups, etc.
 *
 * <p>Plus: a {@link FocusListener} / {@link WindowFocusListener}
 * pair calls {@link InputHandler#releaseAll()} whenever the window
 * deactivates or the shell loses focus, so any keys the OS may have
 * dropped are forcibly forgotten. The adapter therefore guarantees
 * the "no stuck movement after focus loss" invariant the spec
 * requires.
 *
 * <p><b>Offline-only.</b> This class has no networking code and is
 * intentionally a single-player local input adapter. Local 1v1 (when
 * implemented) can add a second adapter for the second player; the
 * design does not require that today.
 */
public final class MabBattleShellInputAdapter {

    private static final boolean DEBUG =
            Boolean.getBoolean("mab.input.debug");

    private final InputHandler inputHandler;
    private final JComponent shellRoot;
    private final KeyEventDispatcher dispatcher;
    private final DoctrineComboTracker p1Combo = new DoctrineComboTracker();
    private final WindowFocusListener windowFocusListener;

    private Window attachedWindow;
    private boolean installed = false;

    public MabBattleShellInputAdapter(InputHandler inputHandler, JComponent shellRoot) {
        if (inputHandler == null) throw new IllegalArgumentException("inputHandler");
        if (shellRoot == null) throw new IllegalArgumentException("shellRoot");
        this.inputHandler = inputHandler;
        this.shellRoot = shellRoot;
        this.dispatcher = this::dispatch;
        this.windowFocusListener = new WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) {
                log("window gained focus");
            }
            @Override public void windowLostFocus(WindowEvent e) {
                log("window lost focus -> clearing held keys");
                inputHandler.releaseAll();
            }
        };
    }

    /** Installs the global key dispatcher and focus listeners. Idempotent. */
    public void install() {
        if (installed) return;
        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);
        // Window focus listener attaches once the shell is in a window.
        SwingUtilities.invokeLater(this::attachWindowListenerLater);
        log("installed (1 active player input adapter)");
    }

    private void attachWindowListenerLater() {
        Window w = SwingUtilities.getWindowAncestor(shellRoot);
        if (w != null && attachedWindow == null) {
            attachedWindow = w;
            w.addWindowFocusListener(windowFocusListener);
        }
    }

    /** Removes the dispatcher and listeners; clears held keys. Idempotent. */
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
        inputHandler.releaseAll();
        log("shutdown -> input state cleared");
    }

    /** Forcibly clear all held key state. Safe to call any time. */
    public void clearHeldKeys() {
        inputHandler.releaseAll();
        log("clearHeldKeys()");
    }

    public boolean isInstalled() { return installed; }

    /** Core dispatcher. Returns true to mark the event consumed so it
     *  is NOT routed to whatever component currently has focus. We
     *  forward every keyboard event to the {@link InputHandler}
     *  ourselves, completely bypassing focus-owner routing. */
    private boolean dispatch(KeyEvent e) {
        if (!installed) return false;
        // Only act on key events that target our hosting window. This
        // keeps the dispatcher safe if the JVM has multiple frames open
        // (e.g. settings dialog, MAB debug HUD).
        Window source = (e.getComponent() == null)
                ? null
                : SwingUtilities.getWindowAncestor(e.getComponent());
        if (attachedWindow != null && source != null && source != attachedWindow) {
            return false;
        }
        if (shellRoot instanceof MabBattleShellPanel shell
                && shell.isKeyboardModalOverlayVisible()) {
            inputHandler.releaseAll();
            return false;
        }
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED:
                trackCombo(e.getKeyCode());
                inputHandler.keyPressed(e);
                if (DEBUG) log("press " + KeyEvent.getKeyText(e.getKeyCode()));
                return true;
            case KeyEvent.KEY_RELEASED:
                inputHandler.keyReleased(e);
                if (DEBUG) log("release " + KeyEvent.getKeyText(e.getKeyCode()));
                return true;
            default:
                return false;
        }
    }

    private void trackCombo(int code) {
        Settings s = Settings.get();
        if (code == s.getKeyRotateCW()) {
            p1Combo.onRotateCW();
        } else if (code == s.getKeyRotateCCW()) {
            p1Combo.onRotateCCW();
        } else if (code == s.getKeyHardDrop()) {
            com.tetris.mab.upgrade.draft.MabActiveDoctrineType doctrine = p1Combo.onHardDrop();
            if (doctrine != null && shellRoot instanceof MabBattleShellPanel shell) {
                shell.fireActiveDoctrine(com.tetris.mab.ParticipantId.PLAYER_A, doctrine);
            }
        }
    }

    private static String simpleName(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    private static void log(String msg) {
        if (DEBUG) System.out.println("[MAB-INPUT] " + msg);
    }
}
