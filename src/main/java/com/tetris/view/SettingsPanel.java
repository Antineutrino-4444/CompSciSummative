package com.tetris.view;

import com.tetris.model.Settings;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SettingsPanel.java
 * ==================
 * A modal dialog for customizing all game settings. Organized into tabbed
 * sections: Handling, Controls, Visual, and Gameplay.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * OPENING THE DIALOG
 * ═══════════════════════════════════════════════════════════════════════
 * Press F1 (configurable) during gameplay. The game pauses automatically
 * while the dialog is open.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TAB LAYOUT
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ┌─ Handling ─┬─ Controls ─┬─ Visual ─┬─ Game ─┐
 *   │                                              │
 *   │  DAS:    [═══|════════]  167 ms              │
 *   │  ARR:    [═|══════════]   33 ms              │
 *   │  SDF:    [══|═════════]   6×                 │
 *   │                                              │
 *   ├──────────────────────────────────────────────┤
 *   │   [ Reset Defaults ]  [ Cancel ]  [ Save ]  │
 *   └──────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════
 * KEY BINDING CAPTURE
 * ═══════════════════════════════════════════════════════════════════════
 * In the Controls tab, each action shows a button with the current key
 * name. Clicking the button enters "capture mode":
 *   1. Button text changes to "Press a key..."
 *   2. The next physical key press is captured.
 *   3. Button updates to show the new key name.
 *   4. Press Escape during capture to cancel without changing.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * SAVE BEHAVIOR
 * ═══════════════════════════════════════════════════════════════════════
 * - "Save" writes settings to disk and closes the dialog.
 * - "Cancel" discards all changes and closes.
 * - "Reset Defaults" restores factory defaults (still need to Save).
 * - Closing the dialog (X) is the same as Cancel.
 */
public class SettingsPanel extends JPanel {

    /** CERN/LHC color scheme — deep navy + electric cyan accents.
     *  Tuned for high contrast on dark backgrounds. */
    private static final Color DARK_BG     = new Color(10, 14, 22);
    private static final Color PANEL_BG    = new Color(22, 30, 44);
    private static final Color PANEL_BG_HI = new Color(32, 42, 60);   // selected tab / hover
    private static final Color TEXT_FG     = new Color(228, 238, 244); // near-white body
    private static final Color TEXT_DIM    = new Color(170, 190, 205); // secondary
    private static final Color ACCENT      = new Color(64, 220, 240);  // brighter cyan
    private static final Color ACCENT_DIM  = new Color(0, 150, 170);
    private static final Color BTN_BG      = new Color(28, 40, 60);
    private static final Color BTN_BORDER  = new Color(0, 170, 195);

    /** Whether the user clicked Save (vs Cancel / close). */
    private boolean saved = false;

    // ─────── Handling controls ───────
    private JSlider dasSlider;
    private JLabel dasLabel;
    private JSlider arrSlider;
    private JLabel arrLabel;
    private JSlider dcdSlider;
    private JLabel dcdLabel;
    private JSlider sdfSlider;
    private JLabel sdfLabel;

    // ─────── Visual controls ───────
    private JSlider gridSlider;
    private JLabel gridLabel;
    private JSlider boardSlider;
    private JLabel boardLabel;
    private JSlider ghostSlider;
    private JLabel ghostLabel;

    // ─────── Gameplay controls ───────
    private JSlider lockDelaySlider;
    private JLabel lockDelayLabel;
    private JSlider lockResetsSlider;
    private JLabel lockResetsLabel;
    private JSlider previewSlider;
    private JLabel previewLabel;
    private JComboBox<String> irsCombo;
    private JComboBox<String> ihsCombo;

    // ─────── Key binding buttons ───────
    private final Map<String, KeyBindButton> keyBindButtons = new LinkedHashMap<>();

    // ─────────────────────── Constructor ─────────────────────────

    /** Callback invoked when the user clicks Cancel/Back/Save. */
    private final Runnable onClose;

    /**
     * Creates the settings form panel.
     *
     * @param onClose callback to invoke when the user dismisses the panel
     *                (Cancel/Back/Save). Used by the dialog wrapper to
     *                close the dialog, or by an embedded host (e.g. the
     *                start menu) to navigate back to the previous view.
     */
    public SettingsPanel(Runnable onClose) {
        this.onClose = onClose != null ? onClose : () -> {};
        setLayout(new BorderLayout(0, 10));
        setBackground(DARK_BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Tab styling is configured locally on this dialog's JTabbedPane
        // below — we no longer pollute the global UIManager because doing
        // so leaks the dark theme into every JOptionPane / JFileChooser
        // in the rest of the application.

        // Tabbed pane — force the Basic UI so our colors aren't ignored
        // by the platform L&F (Windows in particular paints its own tab area).
        JTabbedPane tabs = new JTabbedPane();
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                lightHighlight = ACCENT_DIM;
                shadow         = DARK_BG;
                darkShadow     = DARK_BG;
                focus          = ACCENT;
            }
            @Override protected void paintTabBackground(java.awt.Graphics g, int placement,
                    int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? PANEL_BG_HI : PANEL_BG);
                g.fillRect(x, y, w, h);
            }
            @Override protected void paintContentBorder(java.awt.Graphics g, int placement, int selectedIndex) {
                g.setColor(ACCENT_DIM);
                int w = tabPane.getWidth();
                int h = tabPane.getHeight();
                int top = calculateTabAreaHeight(placement, runCount, maxTabHeight);
                g.drawLine(0, top, w - 1, top);
                g.drawLine(0, top, 0, h - 1);
                g.drawLine(w - 1, top, w - 1, h - 1);
                g.drawLine(0, h - 1, w - 1, h - 1);
            }
        });
        tabs.setBackground(DARK_BG);
        tabs.setForeground(TEXT_FG);
        tabs.setFont(new Font("Monospaced", Font.BOLD, 14));
        tabs.setOpaque(true);
        tabs.addTab("Handling", createHandlingTab());
        tabs.addTab("Controls", createControlsTab());
        tabs.addTab("Visual", createVisualTab());
        tabs.addTab("Game", createGameplayTab());
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setBackgroundAt(i, PANEL_BG);
            tabs.setForegroundAt(i, TEXT_FG);
        }
        add(tabs, BorderLayout.CENTER);

        // Button row
        add(createButtonRow(), BorderLayout.SOUTH);

        loadFromSettings();
    }

    /** Backwards-compatible constructor — kept so existing call sites
     *  that say {@code new SettingsPanel(frame)} still compile. The frame
     *  is unused; callers should prefer {@link #showDialog(JFrame)}. */
    public SettingsPanel(JFrame ignored) {
        this(() -> {});
    }

    /** Returns true if the user clicked Save. */
    public boolean wasSaved() { return saved; }

    // ═══════════════════════════════════════════════════════════════
    // TAB BUILDERS
    // ═══════════════════════════════════════════════════════════════

    private JPanel createHandlingTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // DAS
        dasSlider = new JSlider(0, 500, 167);
        dasLabel = new JLabel("167 ms");
        addSliderRow(panel, "DAS (Delayed Auto Shift)", dasSlider, dasLabel, "ms",
                "Delay before auto-repeat starts when holding a movement key.");
        dasSlider.addChangeListener(e -> dasLabel.setText(dasSlider.getValue() + " ms"));

        panel.add(Box.createVerticalStrut(12));

        // ARR
        arrSlider = new JSlider(0, 200, 33);
        arrLabel = new JLabel("33 ms");
        addSliderRow(panel, "ARR (Auto Repeat Rate)", arrSlider, arrLabel, "ms",
                "Interval between repeated moves after DAS fires. 0 = instant.");
        arrSlider.addChangeListener(e -> arrLabel.setText(arrSlider.getValue() + " ms"));

        panel.add(Box.createVerticalStrut(12));

        // DCD
        dcdSlider = new JSlider(0, 500, 17);
        dcdLabel = new JLabel("17 ms");
        addSliderRow(panel, "DCD (DAS Cut Delay)", dcdSlider, dcdLabel, "ms",
                "Pause after a piece spawns before DAS resumes auto-repeating.");
        dcdSlider.addChangeListener(e -> dcdLabel.setText(dcdSlider.getValue() + " ms"));

        panel.add(Box.createVerticalStrut(12));

        // SDF
        sdfSlider = new JSlider(0, 40, 6);
        sdfLabel = new JLabel("6\u00d7");
        addSliderRow(panel, "SDF (Soft Drop Factor)", sdfSlider, sdfLabel, "\u00d7",
                "Soft drop speed multiplier. 0 = instant drop.");
        sdfSlider.addChangeListener(e -> {
            int v = sdfSlider.getValue();
            sdfLabel.setText(v == 0 ? "INF" : v + "\u00d7");
        });

        return panel;
    }

    private JPanel createControlsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));

        Settings s = Settings.get();
        addKeyBindRow(panel, "Move Left",      "moveLeft",    s.getKeyMoveLeft());
        addKeyBindRow(panel, "Move Right",     "moveRight",   s.getKeyMoveRight());
        addKeyBindRow(panel, "Soft Drop",      "softDrop",    s.getKeySoftDrop());
        addKeyBindRow(panel, "Hard Drop",      "hardDrop",    s.getKeyHardDrop());
        addKeyBindRow(panel, "Rotate CW",      "rotateCW",    s.getKeyRotateCW());
        addKeyBindRow(panel, "Rotate CCW",     "rotateCCW",   s.getKeyRotateCCW());
        addKeyBindRow(panel, "Rotate 180\u00b0", "rotate180",   s.getKeyRotate180());
        addKeyBindRow(panel, "Hold",           "hold",        s.getKeyHold());
        addKeyBindRow(panel, "Hold (Alt)",     "holdAlt",     s.getKeyHoldAlt());
        addKeyBindRow(panel, "Pause",          "pause",       s.getKeyPause());
        addKeyBindRow(panel, "Pause (Alt)",    "pauseAlt",    s.getKeyPauseAlt());
        addKeyBindRow(panel, "Reset",          "reset",       s.getKeyReset());
        addKeyBindRow(panel, "Settings",       "settings",    s.getKeySettings());

        return wrapInScrollPane(panel);
    }

    private JPanel createVisualTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Grid opacity
        gridSlider = new JSlider(0, 100, 10);
        gridLabel = new JLabel("10%");
        addSliderRow(panel, "Grid Opacity", gridSlider, gridLabel, "%",
                "Visibility of the grid lines on the playfield.");
        gridSlider.addChangeListener(e -> gridLabel.setText(gridSlider.getValue() + "%"));

        panel.add(Box.createVerticalStrut(12));

        // Board opacity
        boardSlider = new JSlider(0, 100, 85);
        boardLabel = new JLabel("85%");
        addSliderRow(panel, "Board Opacity", boardSlider, boardLabel, "%",
                "Darkness of the playfield background.");
        boardSlider.addChangeListener(e -> boardLabel.setText(boardSlider.getValue() + "%"));

        panel.add(Box.createVerticalStrut(12));

        // Ghost opacity
        ghostSlider = new JSlider(0, 100, 15);
        ghostLabel = new JLabel("15%");
        addSliderRow(panel, "Ghost Opacity", ghostSlider, ghostLabel, "%",
                "Visibility of the ghost piece (drop shadow).");
        ghostSlider.addChangeListener(e -> ghostLabel.setText(ghostSlider.getValue() + "%"));

        return panel;
    }

    private JPanel createGameplayTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Lock delay
        lockDelaySlider = new JSlider(100, 2000, 500);
        lockDelayLabel = new JLabel("500 ms");
        addSliderRow(panel, "Lock Delay", lockDelaySlider, lockDelayLabel, "ms",
                "Time before a grounded piece locks in place.");
        lockDelaySlider.addChangeListener(e -> lockDelayLabel.setText(lockDelaySlider.getValue() + " ms"));

        panel.add(Box.createVerticalStrut(12));

        // Max lock resets
        lockResetsSlider = new JSlider(0, 30, 15);
        lockResetsLabel = new JLabel("15");
        addSliderRow(panel, "Max Lock Resets", lockResetsSlider, lockResetsLabel, "",
                "How many times moving/rotating on the ground resets the lock timer.");
        lockResetsSlider.addChangeListener(e -> lockResetsLabel.setText(String.valueOf(lockResetsSlider.getValue())));

        panel.add(Box.createVerticalStrut(12));

        // Preview count
        previewSlider = new JSlider(1, 6, 5);
        previewLabel = new JLabel("5");
        addSliderRow(panel, "Preview Count", previewSlider, previewLabel, "",
                "Number of upcoming pieces shown in the Next queue.");
        previewSlider.addChangeListener(e -> previewLabel.setText(String.valueOf(previewSlider.getValue())));

        panel.add(Box.createVerticalStrut(12));

        // IRS mode
        JPanel irsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        irsRow.setBackground(PANEL_BG);
        irsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        irsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel irsLabel = new JLabel("IRS (Initial Rotation):");
        irsLabel.setForeground(TEXT_FG);
        irsLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        irsLabel.setPreferredSize(new Dimension(260, 28));
        irsCombo = new JComboBox<>(new String[]{"off", "tap", "hold"});
        styleCombo(irsCombo);
        irsRow.add(irsLabel);
        irsRow.add(irsCombo);
        panel.add(irsRow);

        panel.add(Box.createVerticalStrut(8));

        // IHS mode
        JPanel ihsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        ihsRow.setBackground(PANEL_BG);
        ihsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        ihsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel ihsLabel = new JLabel("IHS (Initial Hold):");
        ihsLabel.setForeground(TEXT_FG);
        ihsLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        ihsLabel.setPreferredSize(new Dimension(260, 28));
        ihsCombo = new JComboBox<>(new String[]{"off", "tap", "hold"});
        styleCombo(ihsCombo);
        ihsRow.add(ihsLabel);
        ihsRow.add(ihsCombo);
        panel.add(ihsRow);

        return panel;
    }

    /** Apply the dark-cyan theme to a JComboBox, including its popup. */
    private void styleCombo(JComboBox<String> combo) {
        // Force the Basic UI so the Windows L&F doesn't paint its own
        // light-grey editor/arrow button on top of our colors.
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton("\u25BE");
                b.setBackground(BTN_BG);
                b.setForeground(ACCENT);
                b.setFont(new Font("Monospaced", Font.BOLD, 11));
                b.setFocusPainted(false);
                b.setContentAreaFilled(false);
                b.setOpaque(true);
                b.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ACCENT_DIM));
                return b;
            }
        });
        combo.setBackground(BTN_BG);
        combo.setForeground(TEXT_FG);
        combo.setFont(new Font("Monospaced", Font.BOLD, 14));
        combo.setOpaque(true);
        combo.setPreferredSize(new Dimension(120, 30));
        combo.setBorder(BorderFactory.createLineBorder(ACCENT_DIM, 1));
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? PANEL_BG_HI : BTN_BG);
                c.setForeground(isSelected ? ACCENT : TEXT_FG);
                if (c instanceof JComponent jc) {
                    jc.setBorder(new EmptyBorder(3, 8, 3, 8));
                    jc.setOpaque(true);
                }
                return c;
            }
        });
        // Theme the popup's scroll pane / list as well.
        Object popup = combo.getUI().getAccessibleChild(combo, 0);
        if (popup instanceof javax.swing.plaf.basic.ComboPopup cp) {
            JList<?> list = cp.getList();
            list.setBackground(BTN_BG);
            list.setForeground(TEXT_FG);
            list.setSelectionBackground(PANEL_BG_HI);
            list.setSelectionForeground(ACCENT);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BUTTON ROW (Reset / Cancel / Save)
    // ═══════════════════════════════════════════════════════════════

    private JPanel createButtonRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setBackground(DARK_BG);

        JButton resetBtn = styleButton(new JButton("RESET DEFAULTS"));
        resetBtn.addActionListener(e -> resetToDefaults());
        row.add(resetBtn);

        row.add(Box.createHorizontalStrut(20));

        JButton cancelBtn = styleButton(new JButton("CANCEL"));
        cancelBtn.addActionListener(e -> onClose.run());
        row.add(cancelBtn);

        JButton saveBtn = styleButton(new JButton("SAVE"));
        saveBtn.setForeground(ACCENT);
        saveBtn.addActionListener(e -> {
            String conflict = findKeyConflict();
            if (conflict != null) {
                int r = JOptionPane.showConfirmDialog(this,
                        "Two or more actions are bound to the same key:\n\n  " + conflict
                                + "\n\nSave anyway?",
                        "Key conflict",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) return;
            }
            saveToSettings();
            saved = true;
            onClose.run();
        });
        row.add(saveBtn);

        return row;
    }

    /** Styles a button to match the CERN/LHC theme. */
    private JButton styleButton(JButton btn) {
        btn.setBackground(BTN_BG);
        btn.setForeground(TEXT_FG);
        btn.setFont(new Font("Monospaced", Font.BOLD, 13));
        btn.setFocusPainted(false);
        // Required so setBackground actually paints on Windows L&F.
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BTN_BORDER, 1),
                new EmptyBorder(7, 16, 7, 16)));
        return btn;
    }

    /**
     * Returns a human-readable description of the first duplicate key
     * binding found, or {@code null} if all bindings are unique. Bindings
     * with key code 0 (unbound) are ignored.
     */
    private String findKeyConflict() {
        java.util.Map<Integer, String> seen = new java.util.HashMap<>();
        for (var entry : keyBindButtons.entrySet()) {
            int code = entry.getValue().getKeyCode();
            if (code == 0) continue;
            String prev = seen.put(code, entry.getKey());
            if (prev != null) {
                return prev + " + " + entry.getKey()
                        + "  \u2192  " + KeyEvent.getKeyText(code);
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD / SAVE / RESET — transfer between UI and Settings
    // ═══════════════════════════════════════════════════════════════

    private void loadFromSettings() {
        Settings s = Settings.get();

        // Handling
        dasSlider.setValue(s.getDasDelay());
        arrSlider.setValue(s.getArrInterval());
        dcdSlider.setValue(s.getDasCutDelay());
        sdfSlider.setValue(s.getSoftDropFactor());

        // Visual
        gridSlider.setValue((int)(s.getGridOpacity() * 100));
        boardSlider.setValue((int)(s.getBoardOpacity() * 100));
        ghostSlider.setValue((int)(s.getGhostOpacity() * 100));

        // Gameplay
        lockDelaySlider.setValue(s.getLockDelay());
        lockResetsSlider.setValue(s.getMaxLockResets());
        previewSlider.setValue(s.getPreviewCount());
        irsCombo.setSelectedItem(s.getIrsMode());
        ihsCombo.setSelectedItem(s.getIhsMode());

        // Key bindings
        keyBindButtons.get("moveLeft").setKeyCode(s.getKeyMoveLeft());
        keyBindButtons.get("moveRight").setKeyCode(s.getKeyMoveRight());
        keyBindButtons.get("softDrop").setKeyCode(s.getKeySoftDrop());
        keyBindButtons.get("hardDrop").setKeyCode(s.getKeyHardDrop());
        keyBindButtons.get("rotateCW").setKeyCode(s.getKeyRotateCW());
        keyBindButtons.get("rotateCCW").setKeyCode(s.getKeyRotateCCW());
        keyBindButtons.get("rotate180").setKeyCode(s.getKeyRotate180());
        keyBindButtons.get("hold").setKeyCode(s.getKeyHold());
        keyBindButtons.get("holdAlt").setKeyCode(s.getKeyHoldAlt());
        keyBindButtons.get("pause").setKeyCode(s.getKeyPause());
        keyBindButtons.get("pauseAlt").setKeyCode(s.getKeyPauseAlt());
        keyBindButtons.get("reset").setKeyCode(s.getKeyReset());
        keyBindButtons.get("settings").setKeyCode(s.getKeySettings());

        // Fire change listeners to update labels
        dasLabel.setText(dasSlider.getValue() + " ms");
        arrLabel.setText(arrSlider.getValue() + " ms");
        dcdLabel.setText(dcdSlider.getValue() + " ms");
        int sdf = sdfSlider.getValue();
        sdfLabel.setText(sdf == 0 ? "INF" : sdf + "\u00d7");
        gridLabel.setText(gridSlider.getValue() + "%");
        boardLabel.setText(boardSlider.getValue() + "%");
        ghostLabel.setText(ghostSlider.getValue() + "%");
        lockDelayLabel.setText(lockDelaySlider.getValue() + " ms");
        lockResetsLabel.setText(String.valueOf(lockResetsSlider.getValue()));
        previewLabel.setText(String.valueOf(previewSlider.getValue()));
    }

    private void saveToSettings() {
        Settings s = Settings.get();

        // Handling
        s.setDasDelay(dasSlider.getValue());
        s.setArrInterval(arrSlider.getValue());
        s.setDasCutDelay(dcdSlider.getValue());
        s.setSoftDropFactor(sdfSlider.getValue());

        // Visual
        s.setGridOpacity(gridSlider.getValue() / 100.0);
        s.setBoardOpacity(boardSlider.getValue() / 100.0);
        s.setGhostOpacity(ghostSlider.getValue() / 100.0);

        // Gameplay
        s.setLockDelay(lockDelaySlider.getValue());
        s.setMaxLockResets(lockResetsSlider.getValue());
        s.setPreviewCount(previewSlider.getValue());
        s.setIrsMode((String) irsCombo.getSelectedItem());
        s.setIhsMode((String) ihsCombo.getSelectedItem());

        // Key bindings
        s.setKeyMoveLeft(keyBindButtons.get("moveLeft").getKeyCode());
        s.setKeyMoveRight(keyBindButtons.get("moveRight").getKeyCode());
        s.setKeySoftDrop(keyBindButtons.get("softDrop").getKeyCode());
        s.setKeyHardDrop(keyBindButtons.get("hardDrop").getKeyCode());
        s.setKeyRotateCW(keyBindButtons.get("rotateCW").getKeyCode());
        s.setKeyRotateCCW(keyBindButtons.get("rotateCCW").getKeyCode());
        s.setKeyRotate180(keyBindButtons.get("rotate180").getKeyCode());
        s.setKeyHold(keyBindButtons.get("hold").getKeyCode());
        s.setKeyHoldAlt(keyBindButtons.get("holdAlt").getKeyCode());
        s.setKeyPause(keyBindButtons.get("pause").getKeyCode());
        s.setKeyPauseAlt(keyBindButtons.get("pauseAlt").getKeyCode());
        s.setKeyReset(keyBindButtons.get("reset").getKeyCode());
        s.setKeySettings(keyBindButtons.get("settings").getKeyCode());

        s.save();
    }

    private void resetToDefaults() {
        Settings.get().resetToDefaults();
        loadFromSettings();
    }

    // ═══════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds a labeled slider row to a panel. Title, slider, and value
     * are placed on a single horizontal line so vertical space isn't
     * wasted on a separate caption.
     */
    private void addSliderRow(JPanel parent, String title, JSlider slider,
                              JLabel valueLabel, String unit, String tooltip) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(PANEL_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setToolTipText(tooltip);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT_FG);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        titleLabel.setPreferredSize(new Dimension(260, 28));
        titleLabel.setToolTipText(tooltip);
        row.add(titleLabel, BorderLayout.WEST);

        slider.setBackground(PANEL_BG);
        slider.setForeground(ACCENT);
        slider.setOpaque(true);
        slider.setToolTipText(tooltip);
        row.add(slider, BorderLayout.CENTER);

        valueLabel.setForeground(ACCENT);
        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        valueLabel.setPreferredSize(new Dimension(90, 28));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(valueLabel, BorderLayout.EAST);

        parent.add(row);
    }

    /**
     * Adds a key-binding row (label + capture button) to a panel.
     */
    private void addKeyBindRow(JPanel parent, String label, String actionId, int currentKey) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(PANEL_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(TEXT_FG);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 14));
        lbl.setPreferredSize(new Dimension(220, 30));
        row.add(lbl, BorderLayout.WEST);

        KeyBindButton btn = new KeyBindButton(currentKey);
        keyBindButtons.put(actionId, btn);
        row.add(btn, BorderLayout.CENTER);

        parent.add(row);
    }

    /**
     * Wraps a panel in a scroll pane (for the Controls tab).
     */
    private JPanel wrapInScrollPane(JPanel inner) {
        JScrollPane sp = new JScrollPane(inner);
        sp.setBackground(PANEL_BG);
        sp.getViewport().setBackground(PANEL_BG);
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(PANEL_BG);
        wrapper.add(sp, BorderLayout.CENTER);
        return wrapper;
    }

    // ═══════════════════════════════════════════════════════════════
    // KEY BIND BUTTON — captures key presses for rebinding
    // ═══════════════════════════════════════════════════════════════

    /**
     * A button that captures key presses for key binding.
     *
     * Normal state: displays the current key name (e.g., "Left Arrow").
     * Capture state: displays "Press a key..." and waits for input.
     *   - Pressing Escape during capture cancels without changing.
     *   - Any other key assigns the new binding.
     */
    private static class KeyBindButton extends JButton {
        private int keyCode;
        private boolean capturing = false;

        KeyBindButton(int initialKey) {
            this.keyCode = initialKey;
            updateText();
            setFocusable(true);
            setPreferredSize(new Dimension(220, 30));
            setBackground(BTN_BG);
            setForeground(ACCENT);
            setFont(new Font("Monospaced", Font.BOLD, 13));
            setFocusPainted(false);
            // Required so setBackground actually paints on Windows L&F.
            setContentAreaFilled(false);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_DIM, 1),
                    new EmptyBorder(2, 8, 2, 8)));

            addActionListener(e -> startCapture());

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (!capturing) return;
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        // Cancel capture
                        capturing = false;
                        updateText();
                    } else {
                        keyCode = e.getKeyCode();
                        capturing = false;
                        updateText();
                    }
                    e.consume();
                }
            });
        }

        int getKeyCode() { return keyCode; }

        void setKeyCode(int code) {
            this.keyCode = code;
            updateText();
        }

        private void startCapture() {
            capturing = true;
            setText("Press a key...");
            requestFocusInWindow();
        }

        private void updateText() {
            if (keyCode == 0) {
                setText("(none)");
            } else {
                setText(KeyEvent.getKeyText(keyCode));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC CONVENIENCE METHOD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates and shows the settings dialog modally.
     * Blocks until the user closes the dialog.
     *
     * @param owner the parent frame
     */
    public static void showDialog(JFrame owner) {
        JDialog dlg = new JDialog(owner, "SETTINGS // CONFIGURATION", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setResizable(false);
        SettingsPanel form = new SettingsPanel(dlg::dispose);
        dlg.setContentPane(form);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    /**
     * Creates an embeddable settings panel for inline use (e.g. inside
     * the start menu's card layout). Save and Back both invoke
     * {@code onBack}.
     */
    public static SettingsPanel createEmbedded(Runnable onBack) {
        return new SettingsPanel(onBack);
    }
}
