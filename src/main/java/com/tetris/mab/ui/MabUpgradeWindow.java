package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.*;
import java.awt.*;

/**
 * Legacy upgrade-pause companion window hosting {@link MabUpgradePanel}.
 *
 * <p>The preset-based nuke redesign tab that previously lived here has
 * been removed — mid-game redesign now uses the integrated editable
 * builder hosted by {@link MabDefconRedesignOverlayPanel} inside the
 * main battle shell. This window is retained only for the
 * upgrade-pause flow, which is unrelated to the nuke builder rework.
 *
 * <p><b>Offline-only.</b>
 */
public class MabUpgradeWindow extends JFrame {

    private final MabUpgradePanel upgradePanel;
    private final JButton closePauseButton = new JButton("Close Upgrade Pause");
    private Runnable closePauseAction = () -> {};

    public MabUpgradeWindow(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        super("Mutually Assured Blocks — Upgrade Pause");
        this.upgradePanel = new MabUpgradePanel(match, playerId);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        closePauseButton.addActionListener(e -> closePauseAction.run());
        south.add(closePauseButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(upgradePanel, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(new Dimension(720, 560));
        setLocationByPlatform(true);
    }

    public void setClosePauseAction(Runnable r) {
        this.closePauseAction = (r == null) ? () -> {} : r;
    }

    /** Refreshes the upgrade panel. */
    public void refresh() {
        upgradePanel.refresh();
    }
}
