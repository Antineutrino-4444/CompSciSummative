package com.tetris.view;

import com.tetris.controller.LocalPlayerAction;
import com.tetris.controller.LocalPlayerInputBindings;
import com.tetris.model.Settings;
import com.tetris.view.theme.Components;
import com.tetris.view.theme.Components.ButtonStyle;
import com.tetris.view.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/**
 * Keyboard calibration wizard — sequential startup mode (default) and
 * manual settings mode (from Controls menu).
 *
 * <p>Sequential startup mode: opens immediately at the first action prompt;
 * the user presses a key for each action in order, no mouse or prior key
 * knowledge required. Automatically advances after each valid press.</p>
 *
 * <p>Manual settings mode: shows the current bindings table and offers a
 * "RUN FULL CALIBRATION" button that enters the same sequential flow.</p>
 *
 * <p>Both modes save through {@link Settings}. Offline-only: no network or
 * remote-player vocabulary anywhere in this class.</p>
 */
public final class KeyMappingWizardPanel extends JPanel {

    // ── Public mode enum ──────────────────────────────────────────────────────

    /** Selects startup-sequential vs settings-manual presentation. */
    public enum WizardMode {
        /** First-launch / forced: cycles through every action automatically. */
        SEQUENTIAL,
        /** Controls menu: shows current bindings + RUN FULL CALIBRATION button. */
        MANUAL
    }

    // ── Sequential capture phases ─────────────────────────────────────────────

    private enum Phase {
        VIEW_CURRENT,  // MANUAL only: display current bindings, not yet capturing
        P1_CAPTURE,    // sequentially binding Player 1 actions
        P2_CHOICE,     // waiting for Y / N to configure Player 2
        P2_CAPTURE,    // sequentially binding Player 2 actions
        SUMMARY        // all done, waiting for Enter to save
    }

    // ── Canonical action sequences ────────────────────────────────────────────

    private static final LocalPlayerAction[] P1_SEQ = LocalPlayerAction.player1Actions();
    private static final LocalPlayerAction[] P2_SEQ = LocalPlayerAction.player2Actions();

    // ── Center-card names ─────────────────────────────────────────────────────

    private static final String CTR_VIEW    = "view";
    private static final String CTR_CAPTURE = "cap";

    // ── Shared state ──────────────────────────────────────────────────────────

    private final Runnable onComplete;
    private final Runnable onBack;
    private final WizardMode mode;
    private final LocalPlayerInputBindings player1;
    private final LocalPlayerInputBindings player2;
    private final KeyEventDispatcher dispatcher = this::dispatchKey;
    private boolean dispatcherInstalled;

    // ── Sequential capture state ──────────────────────────────────────────────

    private Phase phase;
    private int seqIdx;
    private final int[] p1Keys = new int[P1_SEQ.length]; // 0 = not yet captured
    private final int[] p2Keys = new int[P2_SEQ.length]; // 0 = not yet captured
    private boolean p2ManuallyConfigured;

    // ── Center card layout ────────────────────────────────────────────────────

    private final CardLayout ctrCards = new CardLayout();
    private final JPanel ctrHost = new JPanel(ctrCards);

    // ── Sequential capture UI refs (set during build, non-null after) ─────────

    private JLabel seqProgressLabel;
    private JLabel seqActionLabel;
    private JLabel seqInstructionLabel;
    private JLabel seqWarningLabel;
    private JPanel seqCompletedPanel;
    private JButton saveAndContinueBtn;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /** Defaults to SEQUENTIAL mode (used for first-launch / forced wizard). */
    public KeyMappingWizardPanel(Runnable onComplete, Runnable onBack) {
        this(onComplete, onBack, WizardMode.SEQUENTIAL);
    }

    public KeyMappingWizardPanel(Runnable onComplete, Runnable onBack, WizardMode mode) {
        super(new BorderLayout(0, Theme.SPACE_M));
        this.onComplete = onComplete;
        this.onBack = onBack;
        this.mode = mode;
        this.player1 = LocalPlayerInputBindings.fromSettingsPlayer1(Settings.get());
        this.player2 = LocalPlayerInputBindings.fromSettingsPlayer2(Settings.get());
        this.phase = (mode == WizardMode.MANUAL) ? Phase.VIEW_CURRENT : Phase.P1_CAPTURE;
        this.seqIdx = 0;

        setOpaque(true);
        setBackground(Theme.BG_0);
        setFocusable(true);
        setBorder(new EmptyBorder(24, 40, 24, 40));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        refreshWizardUI();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API / probe accessors
    // ─────────────────────────────────────────────────────────────────────────

    public WizardMode getWizardMode()  { return mode; }
    public boolean isSequentialMode()  { return mode == WizardMode.SEQUENTIAL; }

    /** Returns true once all sequential-capture UI labels have been constructed. */
    public boolean hasSequentialCaptureUI() {
        return seqProgressLabel != null
            && seqActionLabel    != null
            && seqInstructionLabel != null
            && seqWarningLabel   != null;
    }

    public boolean hasCompletedPanel() { return seqCompletedPanel != null; }

    /** Returns true when the panel background is not white (Swing default). */
    public boolean hasNonWhiteBackground() {
        Color bg = getBackground();
        return bg != null && !Color.WHITE.equals(bg);
    }

    public void requestInitialFocus() { requestFocusInWindow(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void addNotify() {
        super.addNotify();
        installDispatcher();
    }

    @Override
    public void removeNotify() {
        uninstallDispatcher();
        super.removeNotify();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI construction
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel h = new JPanel();
        h.setOpaque(false);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("CONTROL CALIBRATION", SwingConstants.CENTER);
        title.setFont(Theme.FONT_DISPLAY);
        title.setForeground(Theme.ACCENT);
        title.setAlignmentX(CENTER_ALIGNMENT);

        String subText = (mode == WizardMode.SEQUENTIAL)
            ? "No controls are assumed yet.  Press the key you want for each action."
            : "Review or recalibrate your current key bindings.";
        JLabel sub = new JLabel(subText, SwingConstants.CENTER);
        sub.setFont(Theme.FONT_MONO_BOLD);
        sub.setForeground(Theme.HIGHLIGHT);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        String sub2Text = (mode == WizardMode.SEQUENTIAL)
            ? "The system will advance automatically after each valid key press."
            : "Click RUN FULL CALIBRATION to set keys from scratch, or SAVE CURRENT to keep them.";
        JLabel sub2 = new JLabel(sub2Text, SwingConstants.CENTER);
        sub2.setFont(Theme.FONT_BODY);
        sub2.setForeground(Theme.TEXT_BODY);
        sub2.setAlignmentX(CENTER_ALIGNMENT);

        h.add(title);
        h.add(Box.createVerticalStrut(6));
        h.add(sub);
        h.add(Box.createVerticalStrut(4));
        h.add(sub2);
        return h;
    }

    private JPanel buildCenter() {
        ctrHost.setOpaque(false);
        ctrHost.add(buildViewCurrentPanel(), CTR_VIEW);
        ctrHost.add(buildCapturePanel(),     CTR_CAPTURE);
        return ctrHost;
    }

    // ── VIEW_CURRENT: existing bindings + RUN CALIBRATION / SAVE CURRENT ─────

    private JPanel buildViewCurrentPanel() {
        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACE_M));
        root.setOpaque(false);

        JPanel tables = new JPanel(new GridLayout(1, 2, Theme.SPACE_M, 0));
        tables.setOpaque(false);
        tables.add(buildViewTable("PLAYER 1", player1, LocalPlayerAction.player1Actions()));
        tables.add(buildViewTable("PLAYER 2 (LOCAL PvP)", player2, LocalPlayerAction.player2Actions()));
        root.add(tables, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_L, 0));
        btns.setOpaque(false);
        JButton runCal = Components.button("RUN FULL CALIBRATION", ButtonStyle.PRIMARY_BLUE);
        JButton saveCur = Components.button("SAVE CURRENT", ButtonStyle.SECONDARY);
        runCal.addActionListener(e -> startSequentialCapture());
        saveCur.addActionListener(e -> saveAndContinue());
        btns.add(runCal);
        btns.add(saveCur);
        root.add(btns, BorderLayout.SOUTH);
        return root;
    }

    private static JPanel buildViewTable(String caption, LocalPlayerInputBindings bindings,
                                         LocalPlayerAction[] actions) {
        JPanel wrap = new JPanel(new BorderLayout(0, Theme.SPACE_S));
        wrap.setOpaque(true);
        wrap.setBackground(Theme.BG_1);
        wrap.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Theme.ACCENT_DIM, 1, true),
            new EmptyBorder(Theme.SPACE_M, Theme.SPACE_L, Theme.SPACE_M, Theme.SPACE_L)));

        JLabel cap = new JLabel(caption);
        cap.setFont(Theme.FONT_MONO_BOLD);
        cap.setForeground(Theme.ACCENT);
        wrap.add(cap, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < actions.length; i++) {
            LocalPlayerAction a = actions[i];
            gc.gridy = i; gc.gridx = 0; gc.weightx = 0.7;
            JLabel name = new JLabel(a.getDisplayName().toUpperCase());
            name.setFont(Theme.FONT_CAPTION);
            name.setForeground(Theme.TEXT_BODY);
            grid.add(name, gc);
            gc.gridx = 1; gc.weightx = 0.3;
            JLabel key = new JLabel(bindings.keyText(a));
            key.setFont(Theme.FONT_CAPTION);
            key.setForeground(Theme.ACCENT_BRIGHT);
            grid.add(key, gc);
        }
        wrap.add(grid, BorderLayout.CENTER);
        return wrap;
    }

    // ── CAPTURE panel: progress bar + action prompt + completed list ───────────

    private JPanel buildCapturePanel() {
        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACE_M));
        root.setOpaque(false);

        // ── Prompt box ─────────────────────────────────────────────────────────
        JPanel promptBox = new JPanel();
        promptBox.setOpaque(true);
        promptBox.setBackground(Theme.BG_1);
        promptBox.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Theme.ACCENT_DIM, 1, true),
            new EmptyBorder(Theme.SPACE_L, Theme.SPACE_XL, Theme.SPACE_L, Theme.SPACE_XL)));
        promptBox.setLayout(new BoxLayout(promptBox, BoxLayout.Y_AXIS));

        seqProgressLabel = new JLabel("PLAYER 1 — 1 / 11", SwingConstants.CENTER);
        seqProgressLabel.setFont(Theme.FONT_MONO_BOLD);
        seqProgressLabel.setForeground(Theme.TEXT_BODY);
        seqProgressLabel.setAlignmentX(CENTER_ALIGNMENT);

        seqActionLabel = new JLabel("MOVE LEFT", SwingConstants.CENTER);
        seqActionLabel.setFont(Theme.FONT_MONO_LARGE);
        seqActionLabel.setForeground(Theme.ACCENT_BRIGHT);
        seqActionLabel.setAlignmentX(CENTER_ALIGNMENT);

        seqInstructionLabel = new JLabel("Press the key you want for PLAYER 1 — MOVE LEFT",
            SwingConstants.CENTER);
        seqInstructionLabel.setFont(Theme.FONT_MONO_BOLD);
        seqInstructionLabel.setForeground(Theme.TEXT_PRIMARY);
        seqInstructionLabel.setAlignmentX(CENTER_ALIGNMENT);

        seqWarningLabel = new JLabel(" ", SwingConstants.CENTER);
        seqWarningLabel.setFont(Theme.FONT_BODY_BOLD);
        seqWarningLabel.setForeground(Theme.WARN);
        seqWarningLabel.setOpaque(true);
        seqWarningLabel.setBackground(Theme.alpha(Theme.WARN, 30));
        seqWarningLabel.setBorder(new EmptyBorder(4, 12, 4, 12));
        seqWarningLabel.setAlignmentX(CENTER_ALIGNMENT);
        seqWarningLabel.setVisible(false);

        promptBox.add(seqProgressLabel);
        promptBox.add(Box.createVerticalStrut(10));
        promptBox.add(seqActionLabel);
        promptBox.add(Box.createVerticalStrut(8));
        promptBox.add(seqInstructionLabel);
        promptBox.add(Box.createVerticalStrut(8));
        promptBox.add(seqWarningLabel);

        root.add(promptBox, BorderLayout.NORTH);

        // ── Completed bindings ─────────────────────────────────────────────────
        JPanel completedWrap = new JPanel(new BorderLayout());
        completedWrap.setOpaque(true);
        completedWrap.setBackground(Theme.BG_1);
        completedWrap.setBorder(new LineBorder(Theme.ACCENT_DIM, 1, true));

        JLabel complHdr = new JLabel("  COMPLETED BINDINGS", SwingConstants.LEFT);
        complHdr.setFont(Theme.FONT_CAPTION);
        complHdr.setForeground(Theme.ACCENT_DIM);
        complHdr.setBorder(new EmptyBorder(6, 8, 4, 8));
        completedWrap.add(complHdr, BorderLayout.NORTH);

        seqCompletedPanel = new JPanel(new GridBagLayout());
        seqCompletedPanel.setOpaque(false);
        seqCompletedPanel.setBorder(new EmptyBorder(0, 8, 8, 8));
        completedWrap.add(seqCompletedPanel, BorderLayout.CENTER);

        root.add(completedWrap, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildFooter() {
        JPanel f = new JPanel(new BorderLayout());
        f.setOpaque(false);

        JLabel hint = new JLabel(
            "BACKSPACE — go back one step    USE DEFAULTS — skip calibration with preset keys",
            SwingConstants.CENTER);
        hint.setFont(Theme.FONT_CAPTION);
        hint.setForeground(Theme.TEXT_FAINT);
        f.add(hint, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_M, 0));
        btns.setOpaque(false);

        if (onBack != null) {
            JButton back = Components.button("BACK", ButtonStyle.TEXT);
            back.addActionListener(e -> { if (onBack != null) onBack.run(); });
            btns.add(back);
        }

        JButton useDefaults = Components.button("USE DEFAULTS", ButtonStyle.SECONDARY);
        useDefaults.addActionListener(e -> useDefaultsAndContinue());
        btns.add(useDefaults);

        saveAndContinueBtn = Components.button("SAVE AND CONTINUE", ButtonStyle.PRIMARY_BLUE);
        saveAndContinueBtn.addActionListener(e -> saveAndContinue());
        saveAndContinueBtn.setVisible(false); // shown only at SUMMARY
        btns.add(saveAndContinueBtn);

        f.add(btns, BorderLayout.EAST);
        return f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase transitions
    // ─────────────────────────────────────────────────────────────────────────

    private void startSequentialCapture() {
        Arrays.fill(p1Keys, 0);
        Arrays.fill(p2Keys, 0);
        p2ManuallyConfigured = false;
        seqIdx = 0;
        phase = Phase.P1_CAPTURE;
        refreshWizardUI();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI refresh
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshWizardUI() {
        if (phase == Phase.VIEW_CURRENT) {
            ctrCards.show(ctrHost, CTR_VIEW);
            if (saveAndContinueBtn != null) saveAndContinueBtn.setVisible(false);
        } else {
            ctrCards.show(ctrHost, CTR_CAPTURE);
            updateCapturePrompt();
        }
        revalidate();
        repaint();
    }

    private void updateCapturePrompt() {
        if (seqProgressLabel == null) return;
        switch (phase) {
            case P1_CAPTURE -> {
                LocalPlayerAction a = P1_SEQ[Math.min(seqIdx, P1_SEQ.length - 1)];
                seqProgressLabel.setText("PLAYER 1  —  " + (seqIdx + 1) + " / " + P1_SEQ.length);
                seqActionLabel.setText(a.getDisplayName().toUpperCase());
                seqInstructionLabel.setText("Press the key you want for PLAYER 1 — "
                    + a.getDisplayName().toUpperCase());
                if (saveAndContinueBtn != null) saveAndContinueBtn.setVisible(false);
            }
            case P2_CAPTURE -> {
                LocalPlayerAction a = P2_SEQ[Math.min(seqIdx, P2_SEQ.length - 1)];
                seqProgressLabel.setText("PLAYER 2  —  " + (seqIdx + 1) + " / " + P2_SEQ.length);
                seqActionLabel.setText(a.getDisplayName().toUpperCase());
                seqInstructionLabel.setText("Press the key you want for PLAYER 2 — "
                    + a.getDisplayName().toUpperCase());
                if (saveAndContinueBtn != null) saveAndContinueBtn.setVisible(false);
            }
            case SUMMARY -> {
                seqProgressLabel.setText("CALIBRATION COMPLETE");
                seqActionLabel.setText("ALL DONE");
                int p1Drop = capturedHardDropKey(p1Keys, P1_SEQ);
                int p2Drop = capturedHardDropKey(p2Keys, P2_SEQ);
                String dropHint = buildDropHint(p1Drop, p2Drop);
                seqInstructionLabel.setText(dropHint + "   |   BACKSPACE to revise last binding");
                if (saveAndContinueBtn != null) saveAndContinueBtn.setVisible(true);
            }
            default -> {}
        }
        clearSeqWarning();
        refreshCompletedList();
        revalidate();
        repaint();
    }

    private void refreshCompletedList() {
        if (seqCompletedPanel == null) return;
        seqCompletedPanel.removeAll();

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(1, 4, 1, 16);
        int row = 0;

        int p1Shown = (phase == Phase.P1_CAPTURE) ? seqIdx : P1_SEQ.length;
        for (int i = 0; i < p1Shown; i++) {
            gc.gridy = row; gc.gridx = 0; gc.weightx = 0;
            seqCompletedPanel.add(entryLabel("P1  " + P1_SEQ[i].getDisplayName().toUpperCase(),
                Theme.TEXT_BODY), gc);
            gc.gridx = 1; gc.weightx = 1;
            String kt = (p1Keys[i] != 0) ? KeyEvent.getKeyText(p1Keys[i]).toUpperCase() : "?";
            seqCompletedPanel.add(entryLabel(kt, Theme.ACCENT_BRIGHT), gc);
            row++;
        }

        if (phase == Phase.P2_CAPTURE || phase == Phase.SUMMARY) {
            int p2Shown = (phase == Phase.P2_CAPTURE) ? seqIdx : P2_SEQ.length;
            for (int i = 0; i < p2Shown; i++) {
                gc.gridy = row; gc.gridx = 0; gc.weightx = 0;
                seqCompletedPanel.add(entryLabel("P2  " + P2_SEQ[i].getDisplayName().toUpperCase(),
                    Theme.HIGHLIGHT), gc);
                gc.gridx = 1; gc.weightx = 1;
                String kt = (p2Keys[i] != 0)
                    ? KeyEvent.getKeyText(p2Keys[i]).toUpperCase() : "DEFAULT";
                seqCompletedPanel.add(entryLabel(kt, Theme.ACCENT_BRIGHT), gc);
                row++;
            }
        }

        seqCompletedPanel.revalidate();
        seqCompletedPanel.repaint();
    }

    private static JLabel entryLabel(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_CAPTION);
        l.setForeground(fg);
        return l;
    }

    private void clearSeqWarning() {
        if (seqWarningLabel == null) return;
        seqWarningLabel.setText(" ");
        seqWarningLabel.setVisible(false);
    }

    private void showSeqWarning(String text) {
        if (seqWarningLabel == null) return;
        seqWarningLabel.setText(text);
        seqWarningLabel.setVisible(true);
        revalidate();
        repaint();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private boolean dispatchKey(KeyEvent e) {
        if (!isShowing()) return false;
        if (e == null || e.getID() != KeyEvent.KEY_PRESSED) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_F3) {
            DebugOverlay.shared().togglePerf();
            return true;
        }
        if (code == KeyEvent.VK_BACK_QUOTE) {
            DebugOverlay.shared().toggleConsole();
            return true;
        }
        if (DebugOverlay.shared().isConsoleOpen()) return false;
        switch (phase) {
            case P1_CAPTURE: return handleP1Key(code);
            case P2_CAPTURE: return handleP2Key(code);
            case SUMMARY:    return handleSummaryKey(code);
            default:         return false; // VIEW_CURRENT: let keys through
        }
    }

    private boolean handleP1Key(int code) {
        if (code == KeyEvent.VK_BACK_SPACE) {
            if (seqIdx > 0) {
                seqIdx--;
                p1Keys[seqIdx] = 0;
                refreshWizardUI();
            } else {
                showSeqWarning("Already at the first action. No previous step to go back to.");
            }
            return true;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            showSeqWarning("Press the USE DEFAULTS button to skip calibration and use preset keys.");
            return true;
        }
        if (code == KeyEvent.VK_UNDEFINED || code == 0) {
            return true;
        }
        if (code == KeyEvent.VK_WINDOWS) {
            showSeqWarning("The Windows key cannot be bound. Choose a different key.");
            return true;
        }
        p1Keys[seqIdx] = code;
        seqIdx++;
        if (seqIdx >= P1_SEQ.length) {
            phase = Phase.P2_CAPTURE;
            seqIdx = 0;
        }
        refreshWizardUI();
        return true;
    }

    private boolean handleP2Key(int code) {
        if (code == KeyEvent.VK_BACK_SPACE) {
            if (seqIdx > 0) {
                seqIdx--;
                p2Keys[seqIdx] = 0;
            } else {
                // Back to end of P1 capture
                phase = Phase.P1_CAPTURE;
                seqIdx = P1_SEQ.length - 1;
                p1Keys[seqIdx] = 0;
                Arrays.fill(p2Keys, 0);
                p2ManuallyConfigured = false;
            }
            refreshWizardUI();
            return true;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            showSeqWarning("Press BACKSPACE to go back, or USE DEFAULTS button to skip.");
            return true;
        }
        if (code == KeyEvent.VK_UNDEFINED || code == 0) {
            return true;
        }
        if (code == KeyEvent.VK_WINDOWS) {
            showSeqWarning("The Windows key cannot be bound. Choose a different key.");
            return true;
        }
        LocalPlayerAction p2Action = P2_SEQ[seqIdx];
        LocalPlayerAction p1c = findKeyIn(p1Keys, P1_SEQ, code, p1Keys.length);
        if (p1c != null && !canShareAcrossPlayers(p1c, p2Action)) {
            showSeqWarning("That key is used by Player 1 for "
                + p1c.getDisplayName().toUpperCase() + ". Choose a different key for Player 2.");
            return true;
        }
        p2Keys[seqIdx] = code;
        p2ManuallyConfigured = true;
        seqIdx++;
        if (seqIdx >= P2_SEQ.length) {
            phase = Phase.SUMMARY;
        }
        refreshWizardUI();
        return true;
    }

    private boolean handleSummaryKey(int code) {
        int p1Drop = capturedHardDropKey(p1Keys, P1_SEQ);
        int p2Drop = capturedHardDropKey(p2Keys, P2_SEQ);
        if ((p1Drop != 0 && code == p1Drop) || (p2Drop != 0 && code == p2Drop)) {
            saveAndContinue();
            return true;
        }
        if (code == KeyEvent.VK_BACK_SPACE) {
            if (p2ManuallyConfigured) {
                phase = Phase.P2_CAPTURE;
                seqIdx = P2_SEQ.length - 1;
                p2Keys[seqIdx] = 0;
                if (seqIdx == 0) p2ManuallyConfigured = false;
            } else {
                phase = Phase.P1_CAPTURE;
                seqIdx = P1_SEQ.length - 1;
                p1Keys[seqIdx] = 0;
            }
            refreshWizardUI();
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conflict helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static LocalPlayerAction findKeyIn(int[] keys, LocalPlayerAction[] seq,
                                               int code, int upTo) {
        for (int i = 0; i < upTo && i < keys.length; i++) {
            if (keys[i] != 0 && keys[i] == code) return seq[i];
        }
        return null;
    }

    private static String buildDropHint(int p1Drop, int p2Drop) {
        if (p1Drop != 0 && p2Drop != 0 && p1Drop != p2Drop) {
            return "Press " + KeyEvent.getKeyText(p1Drop).toUpperCase()
                + " (P1 hard drop) or " + KeyEvent.getKeyText(p2Drop).toUpperCase()
                + " (P2 hard drop) to save";
        } else if (p1Drop != 0) {
            return "Press " + KeyEvent.getKeyText(p1Drop).toUpperCase() + " (hard drop) to save";
        } else if (p2Drop != 0) {
            return "Press " + KeyEvent.getKeyText(p2Drop).toUpperCase() + " (hard drop) to save";
        }
        return "Click SAVE AND CONTINUE to proceed";
    }

    private static int capturedHardDropKey(int[] keys, LocalPlayerAction[] seq) {
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] == LocalPlayerAction.HARD_DROP) {
                return (i < keys.length) ? keys[i] : 0;
            }
        }
        return 0;
    }

    private static boolean canShareAcrossPlayers(LocalPlayerAction p1Action,
                                                 LocalPlayerAction p2Action) {
        return p1Action == p2Action
            && (p1Action == LocalPlayerAction.EXIT_STAGE
                || p1Action == LocalPlayerAction.RESET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save / continue
    // ─────────────────────────────────────────────────────────────────────────

    private void applySequentialCapture() {
        LocalPlayerInputBindings def1 = LocalPlayerInputBindings.player1Defaults();
        LocalPlayerInputBindings def2 = LocalPlayerInputBindings.player2Defaults();
        for (int i = 0; i < P1_SEQ.length; i++) {
            player1.set(P1_SEQ[i], p1Keys[i] != 0 ? p1Keys[i] : def1.get(P1_SEQ[i]));
        }
        for (int i = 0; i < P2_SEQ.length; i++) {
            player2.set(P2_SEQ[i], p2Keys[i] != 0 ? p2Keys[i] : def2.get(P2_SEQ[i]));
        }
    }

    private void saveAndContinue() {
        if (phase != Phase.VIEW_CURRENT) {
            applySequentialCapture();
        }
        Settings s = Settings.get();
        player1.applyToSettingsPlayer1(s);
        player2.applyToSettingsPlayer2(s);
        s.setControlsWizardCompleted(true);
        s.save();
        uninstallDispatcher();
        if (onComplete != null) onComplete.run();
    }

    private void useDefaultsAndContinue() {
        player1.resetToDefaults();
        player2.resetToDefaults();
        Settings s = Settings.get();
        player1.applyToSettingsPlayer1(s);
        player2.applyToSettingsPlayer2(s);
        s.setControlsWizardCompleted(true);
        s.save();
        uninstallDispatcher();
        if (onComplete != null) onComplete.run();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KeyEventDispatcher lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private void installDispatcher() {
        if (dispatcherInstalled) return;
        dispatcherInstalled = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(dispatcher);
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void uninstallDispatcher() {
        if (!dispatcherInstalled) return;
        dispatcherInstalled = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removeKeyEventDispatcher(dispatcher);
    }
}
