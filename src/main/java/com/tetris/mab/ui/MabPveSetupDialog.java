package com.tetris.mab.ui;

import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Step 18 — small modal Swing dialog that lets the player pick the
 * AI archetype, difficulty, starting level, and debug-HUD opt-in
 * before launching MAB PvE.
 *
 * <p>The dialog never mutates a match — it only collects a
 * {@link MabPveConfig} and hands it to a {@link Consumer}. The
 * caller (typically {@link com.tetris.view.StartMenu}) is responsible
 * for actually constructing the {@link com.tetris.controller.GameController}.
 *
 * <p><b>Offline-only.</b>
 */
public class MabPveSetupDialog extends JDialog {

    private final JComboBox<MabAiArchetype> archetypeBox =
            new JComboBox<>(MabAiArchetype.values());
    private final JComboBox<MabAiDifficulty> difficultyBox =
            new JComboBox<>(MabAiDifficulty.values());
    private final JSpinner levelSpinner =
            new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JComboBox<MabBalanceProfile> balanceBox =
            new JComboBox<>(MabBalanceProfiles.all().toArray(new MabBalanceProfile[0]));
    private final JCheckBox debugHudBox = new JCheckBox("Show MAB Debug HUD");

    private final Consumer<MabPveConfig> onStart;

    public MabPveSetupDialog(Window owner,
                             MabPveConfig initial,
                             Consumer<MabPveConfig> onStart) {
        super(owner, "Mutually Assured Blocks — PvE Setup",
                ModalityType.APPLICATION_MODAL);
        this.onStart = (onStart == null) ? cfg -> {} : onStart;

        MabPveConfig seed = (initial == null) ? MabPveConfig.defaults() : initial;
        archetypeBox.setSelectedItem(seed.getAiArchetype());
        difficultyBox.setSelectedItem(seed.getAiDifficulty());
        levelSpinner.setValue(seed.getStartLevel());
        debugHudBox.setSelected(seed.isShowDebugHud());
        // Render profiles by display name and pre-select the seed profile id.
        balanceBox.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof MabBalanceProfile p) {
                    setText(p.getDisplayName());
                }
                return c;
            }
        });
        for (int i = 0; i < balanceBox.getItemCount(); i++) {
            if (balanceBox.getItemAt(i).getId().equals(seed.getBalanceProfileId())) {
                balanceBox.setSelectedIndex(i);
                break;
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 6, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, g, row++, "AI archetype:", archetypeBox);
        addRow(form, g, row++, "AI difficulty:", difficultyBox);
        addRow(form, g, row++, "Start level:", levelSpinner);
        addRow(form, g, row++, "Balance profile:", balanceBox);
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        form.add(debugHudBox, g);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton start = new JButton("Start PvE");
        JButton cancel = new JButton("Cancel");
        start.addActionListener(e -> { dispose(); this.onStart.accept(buildConfig()); });
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        buttons.add(start);

        getRootPane().setDefaultButton(start);
        installKeyboardNavigation(archetypeBox, difficultyBox, levelSpinner,
                balanceBox, debugHudBox, cancel, start);
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) {
                archetypeBox.requestFocusInWindow();
            }
        });
    }

    private void installKeyboardNavigation(JComponent... controls) {
        for (JComponent control : controls) {
            if (control == null) continue;
            control.setFocusable(true);
            InputMap im = control.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = control.getActionMap();
            bindNav(im, am, "setupPrev", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                    () -> focusRelative(controls, control, -1));
            bindNav(im, am, "setupNext", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                    () -> focusRelative(controls, control, +1));

            if (control instanceof JComboBox<?>) {
                bindNav(im, am, "setupComboPrev", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                        () -> cycleCombo((JComboBox<?>) control, -1));
                bindNav(im, am, "setupComboNext", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                        () -> cycleCombo((JComboBox<?>) control, +1));
                bindNav(im, am, "setupComboEnter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                        () -> toggleCombo((JComboBox<?>) control));
            } else if (control instanceof JSpinner) {
                bindNav(im, am, "setupSpinPrev", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                        () -> stepSpinner((JSpinner) control, -1));
                bindNav(im, am, "setupSpinNext", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                        () -> stepSpinner((JSpinner) control, +1));
                bindNav(im, am, "setupSpinEnter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                        () -> focusRelative(controls, control, +1));
            } else if (control instanceof AbstractButton) {
                bindNav(im, am, "setupButtonPrev", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                        () -> focusRelative(controls, control, -1));
                bindNav(im, am, "setupButtonNext", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                        () -> focusRelative(controls, control, +1));
                bindNav(im, am, "setupButtonEnter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                        ((AbstractButton) control)::doClick);
            }
        }
    }

    private void bindNav(InputMap im, ActionMap am, String name, KeyStroke key, Runnable action) {
        im.put(key, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void focusRelative(JComponent[] controls, JComponent current, int delta) {
        if (controls == null || controls.length == 0) return;
        int idx = 0;
        for (int i = 0; i < controls.length; i++) {
            if (controls[i] == current) { idx = i; break; }
        }
        int n = controls.length;
        for (int step = 1; step <= n; step++) {
            int next = ((idx + delta * step) % n + n) % n;
            JComponent candidate = controls[next];
            if (candidate != null && candidate.isEnabled() && candidate.isVisible()) {
                candidate.requestFocusInWindow();
                return;
            }
        }
    }

    private void cycleCombo(JComboBox<?> combo, int delta) {
        int n = combo.getItemCount();
        if (n <= 0) return;
        int next = ((combo.getSelectedIndex() + delta) % n + n) % n;
        combo.setSelectedIndex(next);
    }

    private void toggleCombo(JComboBox<?> combo) {
        if (combo.isPopupVisible()) combo.hidePopup();
        else combo.showPopup();
    }

    private void stepSpinner(JSpinner spinner, int delta) {
        Object next = delta < 0
                ? spinner.getModel().getPreviousValue()
                : spinner.getModel().getNextValue();
        if (next != null) spinner.setValue(next);
    }

    private static void addRow(JPanel form, GridBagConstraints g, int row,
                               String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        form.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        form.add(field, g);
    }

    private MabPveConfig buildConfig() {
        int level;
        try { level = ((Number) levelSpinner.getValue()).intValue(); }
        catch (RuntimeException ex) { level = 1; }
        MabBalanceProfile p = (MabBalanceProfile) balanceBox.getSelectedItem();
        String profileId = (p == null) ? MabBalanceProfiles.STANDARD_PVE : p.getId();
        return MabPveConfig.fromSelections(
                level,
                (MabAiArchetype) archetypeBox.getSelectedItem(),
                (MabAiDifficulty) difficultyBox.getSelectedItem(),
                debugHudBox.isSelected(),
                profileId);
    }
}
