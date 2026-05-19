package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Step 15 — player-facing Mutually Assured Blocks HUD panel.
 *
 * <p>Display-only. Reads a {@link MutuallyAssuredBlocksMatch} via
 * {@link MutuallyAssuredBlocksMatch#toDebugSnapshot()} and
 * {@link MutuallyAssuredBlocksMatch#getRecentEvents(int)}; never
 * mutates match state. No {@code debug*} methods are called from here
 * — those remain reserved for the developer debug HUD and the
 * simulation harness.
 *
 * <p><b>Offline-only.</b> No networking code, no online API hooks.
 */
public class MabHudPanel extends JPanel {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);

    private final JLabel titleLabel = new JLabel("Mutually Assured Blocks — PvE");
    private final JLabel headerLabel = new JLabel("(no match yet)");
    private final JLabel opponentLabel = new JLabel(" ");
    private final JLabel endStateBanner = new JLabel(" ");

    private final JTextArea playerArea = monoArea(8);
    private final JTextArea threatArea = monoArea(6);
    private final JTextArea opponentArea = monoArea(5);
    private final JTextArea actionArea = monoArea(6);
    private final JTextArea eventArea = monoArea(12);
    private final JTextArea alertArea = monoArea(5);
    private final JLabel phaseBanner = new JLabel(" ");
    private final JLabel threatBanner = new JLabel(" ");
    private final JLabel launchReadinessLabel = new JLabel(" ");
    private final JLabel helpLabel;
    private final JButton openUpgradeButton = new JButton("Open Upgrades");
    private final JButton closeUpgradeButton = new JButton("Close Upgrade Pause");
    private final JButton commandGuideButton = new JButton("Command Guide");
    private Runnable openUpgradeAction = () -> {};
    private Runnable closeUpgradeAction = () -> {};
    private Runnable openCommandGuideAction = () -> {};
    private final MabAlertModel alertModel = new MabAlertModel();
    private com.tetris.mab.ai.MabAiArchetype opponentArchetype;
    private com.tetris.mab.ai.MabAiDifficulty opponentDifficulty;
    private String balanceProfileName = "";

    public MabHudPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        titleLabel.setFont(MONO_BOLD.deriveFont(14f));
        headerLabel.setFont(MONO_BOLD);
        phaseBanner.setFont(MONO_BOLD);
        phaseBanner.setForeground(new Color(0xC0, 0x60, 0x00));
        phaseBanner.setHorizontalAlignment(SwingConstants.CENTER);
        threatBanner.setFont(MONO_BOLD);
        threatBanner.setForeground(new Color(0xA0, 0x10, 0x10));
        threatBanner.setHorizontalAlignment(SwingConstants.CENTER);
        launchReadinessLabel.setFont(MONO_BOLD);
        launchReadinessLabel.setHorizontalAlignment(SwingConstants.CENTER);
        opponentLabel.setFont(MONO);
        opponentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        endStateBanner.setFont(MONO_BOLD.deriveFont(14f));
        endStateBanner.setForeground(new Color(0x10, 0x40, 0x80));
        endStateBanner.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel north = new JPanel(new GridLayout(0, 1, 0, 2));
        north.add(titleLabel);
        north.add(headerLabel);
        north.add(opponentLabel);
        north.add(endStateBanner);
        north.add(phaseBanner);
        north.add(threatBanner);
        north.add(launchReadinessLabel);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        openUpgradeButton.setFont(MONO);
        closeUpgradeButton.setFont(MONO);
        commandGuideButton.setFont(MONO);
        openUpgradeButton.addActionListener(e -> openUpgradeAction.run());
        closeUpgradeButton.addActionListener(e -> closeUpgradeAction.run());
        commandGuideButton.addActionListener(e -> openCommandGuideAction.run());
        buttonRow.add(commandGuideButton);
        buttonRow.add(openUpgradeButton);
        buttonRow.add(closeUpgradeButton);
        north.add(buttonRow);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(0, 1, 0, 6));
        center.add(section("Alerts", alertArea));
        center.add(section("Your arsenal", playerArea));
        center.add(section("Incoming threats", threatArea));
        center.add(section("Opponent status", opponentArea));
        center.add(section("Action code", actionArea));
        center.add(section("Recent events", eventArea));
        add(center, BorderLayout.CENTER);

        helpLabel = new JLabel("<html><pre style='margin:0;font-size:10px'>"
                + "Controls: standard Tetris keys.\n"
                + "Strategic actions = line-clear sequences:\n"
                + "  Status Check       2,1,2\n"
                + "  Civil Defense      1,1,2\n"
                + "  Emergency Intercept 1,2,1\n"
                + "  Launch nuke         use the current nuke's launch code\n"
                + "</pre></html>");
        helpLabel.setFont(MONO);
        add(helpLabel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(360, 720));
    }

    /** Wires the "Open Upgrades" button. */
    public void setOpenUpgradeAction(Runnable r) {
        this.openUpgradeAction = (r == null) ? () -> {} : r;
    }

    /** Wires the "Close Upgrade Pause" button. */
    public void setCloseUpgradeAction(Runnable r) {
        this.closeUpgradeAction = (r == null) ? () -> {} : r;
    }

    /** Wires the "Command Guide" button. */
    public void setOpenCommandGuideAction(Runnable r) {
        this.openCommandGuideAction = (r == null) ? () -> {} : r;
    }

    /** Step 18 — sets the opponent description shown in the HUD header. */
    public void setOpponentInfo(com.tetris.mab.ai.MabAiArchetype archetype,
                                com.tetris.mab.ai.MabAiDifficulty difficulty) {
        this.opponentArchetype = archetype;
        this.opponentDifficulty = difficulty;
        refreshOpponentLabel();
    }

    /** Step 19 — sets the active balance profile name shown in the HUD header. */
    public void setBalanceProfileName(String name) {
        this.balanceProfileName = (name == null) ? "" : name;
        refreshOpponentLabel();
    }

    private void refreshOpponentLabel() {
        String base = MabHudFormatter.formatOpponentLine(opponentArchetype, opponentDifficulty);
        if (balanceProfileName != null && !balanceProfileName.isBlank()) {
            opponentLabel.setText(base + "  \u2014  Balance: " + balanceProfileName);
        } else {
            opponentLabel.setText(base);
        }
    }

    private static JTextArea monoArea(int rows) {
        JTextArea a = new JTextArea(rows, 30);
        a.setEditable(false);
        a.setFont(MONO);
        a.setOpaque(true);
        a.setBackground(new Color(0xF7, 0xF7, 0xF7));
        return a;
    }

    private static JComponent section(String title, JTextArea body) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        JLabel l = new JLabel(title);
        l.setFont(MONO_BOLD);
        p.add(l, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xCC, 0xCC, 0xCC)),
                new EmptyBorder(4, 4, 4, 4)));
        return p;
    }

    /**
     * Refreshes every section. Safe to call on the EDT. Never
     * mutates the match.
     *
     * @param match    the live match (may be {@code null} if not started)
     * @param playerId which participant is the human player
     */
    public void refresh(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        if (match == null) {
            headerLabel.setText("(no match yet)");
            phaseBanner.setText(" ");
            threatBanner.setText(" ");
            launchReadinessLabel.setText(" ");
            endStateBanner.setText(" ");
            playerArea.setText("");
            threatArea.setText("");
            opponentArea.setText("");
            actionArea.setText("");
            eventArea.setText("");
            alertArea.setText("");
            openUpgradeButton.setEnabled(false);
            closeUpgradeButton.setEnabled(false);
            return;
        }
        ParticipantId pid = (playerId == null) ? ParticipantId.PLAYER_A : playerId;
        MatchDebugSnapshot snap = match.toDebugSnapshot();
        List<MatchEventLogEntry> recent = match.getRecentEvents(40);

        headerLabel.setText(MabHudFormatter.formatGlobalHeader(snap));
        boolean inUpgradePause = snap.currentPhase() == MatchPhase.UPGRADE_PAUSE;
        openUpgradeButton.setEnabled(!inUpgradePause);
        closeUpgradeButton.setEnabled(inUpgradePause);
        if (inUpgradePause) {
            phaseBanner.setText("UPGRADE PAUSE — open the upgrade window");
        } else if (snap.paused()) {
            phaseBanner.setText("PAUSED");
        } else {
            phaseBanner.setText(" ");
        }
        playerArea.setText(MabHudFormatter.formatPlayerStatus(snap, pid));
        threatArea.setText(MabHudFormatter.formatThreatStatus(snap, pid));
        opponentArea.setText(MabHudFormatter.formatOpponentStatus(snap, pid));
        actionArea.setText(MabHudFormatter.formatActionCodeStatus(snap, pid));

        StringBuilder sb = new StringBuilder();
        for (String row : MabHudFormatter.formatEventFeed(recent, 12)) {
            sb.append(row).append('\n');
        }
        eventArea.setText(sb.toString());

        // Banners + alerts (Step 17).
        String banner = MabHudFormatter.formatThreatLaunchBanner(snap, pid);
        threatBanner.setText(banner.isEmpty() ? " " : banner);
        launchReadinessLabel.setText(MabHudFormatter.formatLaunchReadiness(snap, pid));
        try {
            var alerts = alertModel.buildAlerts(snap, recent, pid);
            alertArea.setText(MabAlertFormatter.formatTopRows(alerts, 5));
        } catch (RuntimeException ex) {
            alertArea.setText("(alert error: " + ex.getMessage() + ")");
        }

        // Step 18 — end-state banner + disable mutating upgrade controls.
        String endBanner = MabHudFormatter.formatEndStateBanner(snap, pid);
        endStateBanner.setText(endBanner.isEmpty() ? " " : endBanner);
        boolean over = snap.currentPhase() == com.tetris.mab.MatchPhase.GAME_OVER;
        if (over) {
            openUpgradeButton.setEnabled(false);
        }
    }
}
