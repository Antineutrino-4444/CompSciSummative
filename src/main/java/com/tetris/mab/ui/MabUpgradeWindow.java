package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.*;
import java.awt.*;

/**
 * Step 16 — companion window hosting {@link MabUpgradePanel} and
 * {@link MabNukeRedesignPanel}. HIDE_ON_CLOSE so the player-facing
 * controller controls true disposal.
 *
 * <p><b>Offline-only.</b>
 */
public class MabUpgradeWindow extends JFrame {

    private final MabUpgradePanel upgradePanel;
    private final MabNukeRedesignPanel redesignPanel;
    private final JButton closePauseButton = new JButton("Close Upgrade Pause");
    private Runnable closePauseAction = () -> {};

    public MabUpgradeWindow(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        super("Mutually Assured Blocks — Upgrade Pause");
        this.upgradePanel = new MabUpgradePanel(match, playerId);
        this.redesignPanel = new MabNukeRedesignPanel(match, playerId);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Upgrades", upgradePanel);
        tabs.addTab("Nuke Redesign", redesignPanel);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        closePauseButton.addActionListener(e -> closePauseAction.run());
        south.add(closePauseButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(new Dimension(720, 560));
        setLocationByPlatform(true);
    }

    public void setClosePauseAction(Runnable r) {
        this.closePauseAction = (r == null) ? () -> {} : r;
    }

    /** Refreshes both inner panels. */
    public void refresh() {
        upgradePanel.refresh();
        redesignPanel.refresh();
    }
}
