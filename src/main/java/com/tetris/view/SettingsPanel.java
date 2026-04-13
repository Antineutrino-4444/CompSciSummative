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
public class SettingsPanel extends JDialog {

    /** CERN/LHC color scheme — deep navy + electric cyan accents. */
    private static final Color DARK_BG = new Color(8, 12, 20);
    private static final Color PANEL_BG = new Color(14, 20, 32);
    private static final Color TEXT_FG = new Color(180, 210, 220);
    private static final Color ACCENT = new Color(0, 200, 220);
    private static final Color ACCENT_DIM = new Color(0, 140, 160);
    private static final Color BTN_BG = new Color(20, 30, 48);
    private static final Color BTN_BORDER = new Color(0, 160, 180);

    /** Whether the user clicked Save (vs Cancel / close). */
    private boolean saved = false;

    // ─────── Handling controls ───────
    private JSlider dasSlider;
    private JLabel dasLabel;
    private JSlider arrSlider;
    private JLabel arrLabel;
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

    /**
     * Creates and shows the settings dialog.
     *
     * @param owner the parent frame
     */
    public SettingsPanel(JFrame owner) {
        super(owner, "SETTINGS // CONFIGURATION", true); // modal
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Force dark tab styling via UIManager
        UIManager.put("TabbedPane.selected", new Color(0, 200, 220, 40));
        UIManager.put("TabbedPane.contentAreaColor", DARK_BG);
        UIManager.put("TabbedPane.background", DARK_BG);
        UIManager.put("TabbedPane.shadow", DARK_BG);
        UIManager.put("TabbedPane.darkShadow", ACCENT_DIM);
        UIManager.put("TabbedPane.light", DARK_BG);
        UIManager.put("TabbedPane.highlight", ACCENT_DIM);
        UIManager.put("TabbedPane.focus", ACCENT);
        UIManager.put("TabbedPane.unselectedBackground", new Color(10, 16, 28));
        UIManager.put("TabbedPane.selectHighlight", ACCENT_DIM);

        // Build UI
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(DARK_BG);
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Tabbed pane
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(DARK_BG);
        tabs.setForeground(ACCENT);
        tabs.setFont(new Font("Monospaced", Font.BOLD, 12));
        tabs.setOpaque(true);
        tabs.addTab("Handling", createHandlingTab());
        tabs.addTab("Controls", createControlsTab());
        tabs.addTab("Visual", createVisualTab());
        tabs.addTab("Game", createGameplayTab());
        content.add(tabs, BorderLayout.CENTER);

        // Button row
        content.add(createButtonRow(), BorderLayout.SOUTH);

        setContentPane(content);
        loadFromSettings();
        pack();
        setLocationRelativeTo(owner);
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
        JPanel irsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        irsRow.setBackground(PANEL_BG);
        irsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel irsLabel = new JLabel("IRS (Initial Rotation):");
        irsLabel.setForeground(TEXT_FG);
        irsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        irsCombo = new JComboBox<>(new String[]{"off", "tap", "hold"});
        irsCombo.setBackground(BTN_BG);
        irsCombo.setForeground(ACCENT);
        irsRow.add(irsLabel);
        irsRow.add(irsCombo);
        panel.add(irsRow);

        panel.add(Box.createVerticalStrut(8));

        // IHS mode
        JPanel ihsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ihsRow.setBackground(PANEL_BG);
        ihsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel ihsLabel = new JLabel("IHS (Initial Hold):");
        ihsLabel.setForeground(TEXT_FG);
        ihsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ihsCombo = new JComboBox<>(new String[]{"off", "tap", "hold"});
        ihsCombo.setBackground(BTN_BG);
        ihsCombo.setForeground(ACCENT);
        ihsRow.add(ihsLabel);
        ihsRow.add(ihsCombo);
        panel.add(ihsRow);

        return panel;
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
        cancelBtn.addActionListener(e -> dispose());
        row.add(cancelBtn);

        JButton saveBtn = styleButton(new JButton("SAVE"));
        saveBtn.setForeground(ACCENT);
        saveBtn.addActionListener(e -> {
            saveToSettings();
            saved = true;
            dispose();
        });
        row.add(saveBtn);

        return row;
    }

    /** Styles a button to match the CERN/LHC theme. */
    private JButton styleButton(JButton btn) {
        btn.setBackground(BTN_BG);
        btn.setForeground(TEXT_FG);
        btn.setFont(new Font("Monospaced", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BTN_BORDER, 1),
                new EmptyBorder(5, 12, 5, 12)));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD / SAVE / RESET — transfer between UI and Settings
    // ═══════════════════════════════════════════════════════════════

    private void loadFromSettings() {
        Settings s = Settings.get();

        // Handling
        dasSlider.setValue(s.getDasDelay());
        arrSlider.setValue(s.getArrInterval());
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
     * Adds a labeled slider row to a panel.
     */
    private void addSliderRow(JPanel parent, String title, JSlider slider,
                              JLabel valueLabel, String unit, String tooltip) {
        // Title row
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT_FG);
        titleLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setToolTipText(tooltip);
        parent.add(titleLabel);

        // Slider + value row
        JPanel sliderRow = new JPanel(new BorderLayout(8, 0));
        sliderRow.setBackground(PANEL_BG);
        sliderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sliderRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        slider.setBackground(PANEL_BG);
        slider.setForeground(ACCENT);
        slider.setToolTipText(tooltip);
        sliderRow.add(slider, BorderLayout.CENTER);

        valueLabel.setForeground(ACCENT);
        valueLabel.setPreferredSize(new Dimension(70, 20));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sliderRow.add(valueLabel, BorderLayout.EAST);

        parent.add(sliderRow);
    }

    /**
     * Adds a key-binding row (label + capture button) to a panel.
     */
    private void addKeyBindRow(JPanel parent, String label, String actionId, int currentKey) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(PANEL_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(TEXT_FG);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        lbl.setPreferredSize(new Dimension(130, 26));
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
            setPreferredSize(new Dimension(150, 26));
            setBackground(BTN_BG);
            setForeground(ACCENT);
            setFont(new Font("Monospaced", Font.BOLD, 11));
            setFocusPainted(false);
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
        SettingsPanel dialog = new SettingsPanel(owner);
        dialog.setVisible(true);
    }
}
