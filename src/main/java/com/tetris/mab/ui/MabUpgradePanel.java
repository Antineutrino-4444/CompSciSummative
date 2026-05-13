package com.tetris.mab.ui;

import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.UpgradeState;
import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeChoiceSet;
import com.tetris.mab.upgrade.UpgradeDefinition;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Step 16 — player-facing upgrade selection panel. Reads
 * {@link MutuallyAssuredBlocksMatch#getUpgradeChoices(ParticipantId)}
 * and applies upgrades through
 * {@link MutuallyAssuredBlocksMatch#applyUpgrade(ParticipantId,
 * com.tetris.mab.upgrade.UpgradeType)}.
 *
 * <p>No debug mutators are called from this panel.
 *
 * <p><b>Offline-only.</b>
 */
public class MabUpgradePanel extends JPanel {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId playerId;

    private final JLabel headerLabel = new JLabel(" ");
    private final JLabel ownedLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultListModel<UpgradeDefinition> listModel = new DefaultListModel<>();
    private final JList<UpgradeDefinition> list = new JList<>(listModel);
    private final JTextArea detailArea = new JTextArea(12, 32);
    private final JButton applyButton = new JButton("Apply Upgrade");
    private final JLabel pauseHint = new JLabel(" ");

    public MabUpgradePanel(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        if (match == null) throw new IllegalArgumentException("match");
        this.match = match;
        this.playerId = (playerId == null) ? ParticipantId.PLAYER_A : playerId;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        headerLabel.setFont(MONO_BOLD);
        ownedLabel.setFont(MONO);
        statusLabel.setFont(MONO_BOLD);
        statusLabel.setForeground(new Color(0x20, 0x60, 0x20));
        pauseHint.setFont(MONO_BOLD);
        pauseHint.setForeground(new Color(0xC0, 0x60, 0x00));

        JPanel north = new JPanel(new GridLayout(0, 1, 0, 2));
        JLabel title = new JLabel("Upgrade Pause");
        title.setFont(MONO_BOLD.deriveFont(14f));
        north.add(title);
        north.add(pauseHint);
        north.add(headerLabel);
        north.add(ownedLabel);
        add(north, BorderLayout.NORTH);

        list.setFont(MONO);
        list.setVisibleRowCount(12);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> l, Object value, int index, boolean isSel, boolean focus) {
                Component c = super.getListCellRendererComponent(l, value, index, isSel, focus);
                if (value instanceof UpgradeDefinition def) {
                    UpgradeState st = currentUpgradeState();
                    setText(MabUpgradeFormatter.formatListEntry(def, st));
                }
                setFont(MONO);
                return c;
            }
        });
        list.addListSelectionListener(e -> updateDetailAndButton());

        detailArea.setEditable(false);
        detailArea.setFont(MONO);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list),
                new JScrollPane(detailArea));
        split.setResizeWeight(0.45);
        split.setDividerLocation(260);
        add(split, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        applyButton.addActionListener(e -> onApply());
        buttonRow.add(applyButton);
        south.add(buttonRow, BorderLayout.WEST);
        south.add(statusLabel, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        refresh();
    }

    /** Refreshes the choice list, owned summary, and button state. */
    public void refresh() {
        UpgradeChoiceSet choices;
        try {
            choices = match.getUpgradeChoices(playerId);
        } catch (RuntimeException ex) {
            headerLabel.setText("(unable to read choices: " + ex.getMessage() + ")");
            return;
        }
        headerLabel.setText(MabUpgradeFormatter.formatChoiceSetHeader(choices));
        ownedLabel.setText("Owned: " + MabUpgradeFormatter.formatOwnedUpgradeSummary(
                currentUpgradeState()));

        boolean inPause = match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE;
        pauseHint.setText(inPause
                ? "Gameplay is paused — upgrade selection active."
                : "Open the upgrade pause to apply upgrades.");

        UpgradeDefinition prev = list.getSelectedValue();
        listModel.clear();
        int reselect = -1;
        int idx = 0;
        for (UpgradeDefinition d : choices.choices()) {
            listModel.addElement(d);
            if (prev != null && prev.type() == d.type()) reselect = idx;
            idx++;
        }
        if (reselect >= 0) list.setSelectedIndex(reselect);
        else if (!listModel.isEmpty()) list.setSelectedIndex(0);

        updateDetailAndButton();
    }

    private void updateDetailAndButton() {
        UpgradeDefinition sel = list.getSelectedValue();
        UpgradeState st = currentUpgradeState();
        detailArea.setText(MabUpgradeFormatter.formatUpgradeDetail(sel, st));
        detailArea.setCaretPosition(0);

        boolean inPause = match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE;
        boolean enable = false;
        String tip = null;
        if (sel == null) {
            tip = "Select an upgrade.";
        } else if (!inPause) {
            tip = "Open upgrade pause first.";
        } else if (st == null) {
            tip = "No state.";
        } else if (st.isMaxed(sel)) {
            tip = "Already at max level.";
        } else if (!st.hasPrerequisites(sel)) {
            tip = "Prerequisites missing.";
        } else if (st.costForNextLevel(sel) > st.getUpgradePoints()) {
            int need = st.costForNextLevel(sel) - st.getUpgradePoints();
            tip = "Need " + need + " more point(s).";
        } else {
            enable = true;
            tip = "Affordable — click Apply Upgrade.";
        }
        applyButton.setEnabled(enable);
        applyButton.setToolTipText(tip);
    }

    private void onApply() {
        UpgradeDefinition sel = list.getSelectedValue();
        if (sel == null) return;
        UpgradeApplicationResult result;
        try {
            result = match.applyUpgrade(playerId, sel.type());
        } catch (RuntimeException ex) {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
            statusLabel.setText("Error: " + ex.getMessage());
            return;
        }
        if (result.success()) {
            statusLabel.setForeground(new Color(0x20, 0x60, 0x20));
        } else {
            statusLabel.setForeground(new Color(0xA0, 0x20, 0x20));
        }
        statusLabel.setText(MabUpgradeFormatter.formatApplyResult(result));
        refresh();
    }

    private UpgradeState currentUpgradeState() {
        try {
            ParticipantState p = match.getParticipant(playerId);
            return p == null ? null : p.getUpgradeState();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
