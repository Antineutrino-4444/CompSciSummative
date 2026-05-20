package com.tetris.view;

import com.tetris.audio.AudioHealth;
import com.tetris.audio.MusicDirector;

import javax.swing.JLayeredPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Process-wide owner of the developer console and the F3 performance overlay.
 *
 * <p>Previously these lived inside {@code GameController}, so they only worked
 * while a game was running. This singleton installs a single global key
 * dispatcher ({@code `}/{@code ~}, the {@code debug} hotword, and F3) and mounts
 * the panels into whichever top-level window currently has focus — so the
 * console and overlay open on the main menu, settings, key-binding screen, and
 * in-game alike.
 *
 * <p>Game-specific commands and the in-game performance read-out are supplied by
 * the active {@code GameController} via {@link #setContextHandler} /
 * {@link #setPerfSupplier}; when no game is active, a small set of global
 * commands and a generic perf block are used instead.
 */
public final class DebugOverlay {

    private static final DebugOverlay SHARED = new DebugOverlay();

    public static DebugOverlay shared() { return SHARED; }

    private static final String HOTWORD = "debug";

    private DevConsolePanel console;
    private PerfOverlayPanel perfOverlay;
    private java.awt.KeyEventDispatcher dispatcher;
    private final StringBuilder hotwordBuf = new StringBuilder();

    private volatile Function<String, String> contextHandler;
    private volatile Supplier<String> perfSupplier;
    private volatile Runnable consoleOpenHook;

    private DebugOverlay() {}

    /** Installs the global key dispatcher once. Safe to call repeatedly. */
    public synchronized void install() {
        SwingPaintDiagnostics.install();
        if (dispatcher != null) return;
        dispatcher = this::dispatchKey;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);
    }

    /** Registers the active game's command handler (cleared with {@link #clearContext}). */
    public void setContextHandler(Function<String, String> handler) {
        this.contextHandler = handler;
    }

    /** Registers the active game's performance read-out supplier. */
    public void setPerfSupplier(Supplier<String> supplier) {
        this.perfSupplier = supplier;
    }

    /** Registers a hook run when the console opens (e.g. release gameplay keys). */
    public void setConsoleOpenHook(Runnable hook) {
        this.consoleOpenHook = hook;
    }

    /** Detaches the active game context (call on game dispose). */
    public void clearContext() {
        contextHandler = null;
        perfSupplier = null;
        consoleOpenHook = null;
    }

    public boolean isConsoleOpen() {
        return console != null && console.isOpen();
    }

    /** Refreshes the perf overlay text if it is currently visible. */
    public void refreshPerf() {
        if (perfOverlay != null && perfOverlay.isVisible()) {
            perfOverlay.update(perfString());
        }
    }

    // ─────────────────────── key handling ───────────────────────

    private boolean dispatchKey(KeyEvent e) {
        if (e == null || e.getID() != KeyEvent.KEY_PRESSED) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_F3) {
            togglePerf();
            return true;
        }
        if (code == KeyEvent.VK_BACK_QUOTE) {
            hotwordBuf.setLength(0);
            toggleConsole();
            return true;
        }
        if (isConsoleOpen()) {
            if (code == KeyEvent.VK_ENTER) {
                console.submitCurrentInput();
                return true;
            }
            SwingUtilities.invokeLater(console::focusInput);
        }
        // Track the 'debug' hotword (only when the console is closed). Letters
        // are not consumed so normal typing/gameplay still receives them.
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z && !isConsoleOpen()) {
            char c = (char) ('a' + (code - KeyEvent.VK_A));
            hotwordBuf.append(c);
            if (hotwordBuf.length() > HOTWORD.length()) {
                hotwordBuf.deleteCharAt(0);
            }
            if (hotwordBuf.toString().equals(HOTWORD)) {
                hotwordBuf.setLength(0);
                toggleConsole();
            }
        } else if (code < KeyEvent.VK_A || code > KeyEvent.VK_Z) {
            hotwordBuf.setLength(0);
        }
        return false;
    }

    // ─────────────────────── toggles ───────────────────────

    /** Toggle the dev console open/closed on the active window. */
    public void toggleConsole() {
        if (console == null) {
            console = new DevConsolePanel(this::handleCommand);
        }
        JLayeredPane lp = activeLayeredPane();
        if (lp == null) return;
        mountInto(console, lp, JLayeredPane.POPUP_LAYER);
        int w = lp.getWidth();
        int h = lp.getHeight();
        int consH = Math.max(240, h * 2 / 5);
        console.setBounds(0, h - consH, w, consH);
        console.revalidate();
        lp.repaint();
        boolean opening = !console.isOpen();
        if (opening) {
            Runnable hook = consoleOpenHook;
            if (hook != null) {
                try { hook.run(); } catch (RuntimeException ignored) {}
            }
        }
        console.toggle();
    }

    /** Toggle the F3 performance overlay on the active window. */
    public void togglePerf() {
        if (perfOverlay == null) {
            perfOverlay = new PerfOverlayPanel();
        }
        JLayeredPane lp = activeLayeredPane();
        if (lp == null) return;
        mountInto(perfOverlay, lp, JLayeredPane.DRAG_LAYER);
        perfOverlay.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        perfOverlay.revalidate();
        boolean nowVisible = !perfOverlay.isVisible();
        perfOverlay.setVisible(nowVisible);
        if (nowVisible) perfOverlay.update(perfString());
        lp.repaint();
    }

    private static void mountInto(java.awt.Component comp, JLayeredPane lp, Integer layer) {
        if (comp.getParent() == lp) return;
        Container prev = comp.getParent();
        if (prev != null) {
            prev.remove(comp);
            prev.repaint();
        }
        lp.add(comp, layer);
    }

    private JLayeredPane activeLayeredPane() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window win = kfm.getActiveWindow();
        if (!(win instanceof RootPaneContainer)) {
            win = kfm.getFocusedWindow();
        }
        if (!(win instanceof RootPaneContainer rpc)) return null;
        JLayeredPane lp = rpc.getRootPane().getLayeredPane();
        if (lp == null || lp.getWidth() <= 0 || lp.getHeight() <= 0) return null;
        return lp;
    }

    // ─────────────────────── commands ───────────────────────

    private String handleCommand(String raw) {
        String[] parts = raw.trim().split("\\s+", 2);
        String verb = parts[0].toLowerCase();
        switch (verb) {
            case "reloadaudio": return reloadAudio();
            case "audio":       return audioStatus();
            case "gc":          { Runtime.getRuntime().gc(); return "GC requested."; }
            default: break;
        }
        // Delegate everything else to the active game, if one is registered.
        Function<String, String> ctx = contextHandler;
        if (ctx != null) {
            try {
                String r = ctx.apply(raw);
                if (r != null) return r;
            } catch (RuntimeException ex) {
                return "command error: " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage();
            }
        }
        return "unknown command: " + raw + "  (type 'help' for commands)";
    }

    private String reloadAudio() {
        AudioHealth.shared().reset();
        try { MusicDirector.shared().reload(); } catch (RuntimeException ignored) {}
        return "Audio re-enabled and failure counter reset.";
    }

    private String audioStatus() {
        AudioHealth h = AudioHealth.shared();
        StringBuilder sb = new StringBuilder("audio ")
                .append(h.isDisabled() ? "DISABLED" : "ok")
                .append("  failures=").append(h.getFailureCount())
                .append('/').append(h.getMaxAttempts());
        if (!h.getLastReason().isEmpty()) {
            sb.append("  last=").append(h.getLastReason());
        }
        return sb.toString();
    }

    private String perfString() {
        Supplier<String> s = perfSupplier;
        if (s != null) {
            try {
                String v = s.get();
                if (v != null) return v;
            } catch (RuntimeException ignored) {}
        }
        return defaultPerfString();
    }

    private static String defaultPerfString() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long total = rt.totalMemory() / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        AudioHealth h = AudioHealth.shared();
        return "§h F3  PERFORMANCE\n"
             + " (no active game)\n"
             + "§h\n"
             + String.format(" Heap     %d / %d MB  (max %d MB)", used, total, max) + "\n"
             + String.format(" Threads  %d", Thread.activeCount()) + "\n"
             + (h.isDisabled()
                    ? "§w Audio    DISABLED (reloadaudio)"
                    : String.format(" Audio    ok  (%d/%d fails)",
                            h.getFailureCount(), h.getMaxAttempts()));
    }
}
