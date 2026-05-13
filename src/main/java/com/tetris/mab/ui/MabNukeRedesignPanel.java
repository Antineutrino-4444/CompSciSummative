package com.tetris.mab.ui;

import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.upgrade.NukeRedesignRetentionRules;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Step 16 — player-facing nuke redesign panel. Lets the player pick a
 * factory-defined preset and apply it during {@link MatchPhase#UPGRADE_PAUSE}
 * via {@link MutuallyAssuredBlocksMatch#redesignNukeDuringUpgradePause(
 * ParticipantId, NukeDesign, double)}.
 *
 * <p>Retention ratio is computed by
 * {@link NukeRedesignRetentionRules#suggestedRetentionRatio(NukeDesign,
 * NukeDesign)} and shown to the player before they commit.
 *
 * <p><b>Offline-only.</b>
 */
public class MabNukeRedesignPanel extends JPanel {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId playerId;
    private final List<MabNukePresetDefinition> presets = MabNukePresetDefinition.defaults();

    private final JLabel currentLabel = new JLabel(" ");
    private final JComboBox<MabNukePresetDefinition> combo = new JComboBox<>();
    private final JTextArea previewArea = new JTextArea(10, 32);
    private final JButton applyButton = new JButton("Apply Redesign");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel pauseHint = new JLabel(" ");

    public MabNukeRedesignPanel(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        if (match == null) throw new IllegalArgumentException("match");
        this.match = match;
        this.playerId = (playerId == null) ? ParticipantId.PLAYER_A : playerId;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        currentLabel.setFont(MONO_BOLD);
        statusLabel.setFont(MONO_BOLD);
        pauseHint.setFont(MONO_BOLD);
        pauseHint.setForeground(new Color(0xC0, 0x60, 0x00));

        JPanel north = new JPanel(new GridLayout(0, 1, 0, 2));
        JLabel title = new JLabel("Nuke Redesign");
        title.setFont(MONO_BOLD.deriveFont(14f));
        north.add(title);
        north.add(pauseHint);
        north.add(currentLabel);
        add(north, BorderLayout.NORTH);

        for (MabNukePresetDefinition d : presets) combo.addItem(d);
        combo.addActionListener(e -> updatePreview());

        previewArea.setEditable(false);
        previewArea.setFont(MONO);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel comboRow = new JPanel(new BorderLayout(8, 0));
        JLabel pickLabel = new JLabel("Preset: ");
        pickLabel.setFont(MONO);
        comboRow.add(pickLabel, BorderLayout.WEST);
        comboRow.add(combo, BorderLayout.CENTER);
        center.add(comboRow, BorderLayout.NORTH);
        center.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        applyButton.addActionListener(e -> onApply());
        JPanel south = new JPanel(new BorderLayout(8, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(applyButton);
        south.add(left, BorderLayout.WEST);
        south.add(statusLabel, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        refresh();
    }

    /** Refreshes the current-design label, preview, and button state. */
    public void refresh() {
        NukeDesign current = currentDesign();
        int charge = currentCharge();
        currentLabel.setText("Current: "
                + (current == null ? "—" : current.getDisplayName())
                + "    charge=" + charge);
        boolean inPause = match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE;
        pauseHint.setText(inPause
                ? "Gameplay paused — redesign allowed."
                : "Redesign requires UPGRADE_PAUSE.");
        updatePreview();
    }

    private void updatePreview() {
        MabNukePresetDefinition sel = (MabNukePresetDefinition) combo.getSelectedItem();
        if (sel == null) {
            previewArea.setText("(no preset selected)");
            applyButton.setEnabled(false);
            return;
        }
        NukeDesign current = currentDesign();
        NukeDesign next;
        try {
            next = sel.toNukeDesign();
        } catch (RuntimeException ex) {
            previewArea.setText("Failed to build preset: " + ex.getMessage());
            applyButton.setEnabled(false);
            return;
        }
        previewArea.setText(MabUpgradeFormatter.formatRetentionPreview(
                current, next, currentCharge()));
        previewArea.setCaretPosition(0);
        applyButton.setEnabled(match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE);
        applyButton.setToolTipText(applyButton.isEnabled()
                ? "Apply this design now (consumes any non-retained charge)."
                : "Open upgrade pause first.");
    }

    private void onApply() {
        MabNukePresetDefinition sel = (MabNukePresetDefinition) combo.getSelectedItem();
        if (sel == null) return;
        if (match.getCurrentPhase() != MatchPhase.UPGRADE_PAUSE) {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
            statusLabel.setText("Not in UPGRADE_PAUSE.");
            return;
        }
        NukeDesign current = currentDesign();
        NukeDesign next;
        try { next = sel.toNukeDesign(); }
        catch (RuntimeException ex) {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
            statusLabel.setText("Build failed: " + ex.getMessage());
            return;
        }
        double ratio = NukeRedesignRetentionRules.suggestedRetentionRatio(current, next);
        boolean ok;
        try {
            ok = match.redesignNukeDuringUpgradePause(playerId, next, ratio);
        } catch (RuntimeException ex) {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
            statusLabel.setText("Error: " + ex.getMessage());
            return;
        }
        if (ok) {
            statusLabel.setForeground(new Color(0x20, 0x60, 0x20));
            statusLabel.setText("Redesigned -> " + next.getDisplayName()
                    + String.format(" (ratio %.2f)", ratio));
        } else {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
            statusLabel.setText("Redesign rejected.");
        }
        refresh();
    }

    private NukeDesign currentDesign() {
        try {
            ParticipantState p = match.getParticipant(playerId);
            if (p == null) return null;
            NukeBuildState nb = p.getNukeBuildState();
            return nb == null ? null : nb.getCurrentDesign();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int currentCharge() {
        try {
            ParticipantState p = match.getParticipant(playerId);
            if (p == null) return 0;
            NukeBuildState nb = p.getNukeBuildState();
            return nb == null ? 0 : nb.getCurrentBuildCharge();
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
