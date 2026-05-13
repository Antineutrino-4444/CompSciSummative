package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchDebugSnapshot.ParticipantSummary;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.action.ActionClearToken;
import com.tetris.mab.action.ActionCodeAttempt;
import com.tetris.mab.action.ActionCodeManager;
import com.tetris.mab.action.ActionCodeTokenRequirement;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Step 20 Second Refinement — compact, always-visible MAB strategic
 * dashboard for the embedded PvE layout.
 *
 * <p>Replaces the previous "scrolled full {@link MabHudPanel}" right
 * column with a single horizontal row of small dark cards: <b>Status,
 * Your Arsenal, Incoming, Action, Opponent Intel, Recent Events,
 * Controls</b>. All core warnings are visible without scrolling at
 * normal desktop resolution; the only scrollable region is the
 * Recent-Events list, which is a secondary reference.
 *
 * <p>Display-only. The panel reads
 * {@link MutuallyAssuredBlocksMatch#toDebugSnapshot()} and
 * {@link MutuallyAssuredBlocksMatch#getRecentEvents(int)} via
 * {@link #refresh(MutuallyAssuredBlocksMatch, ParticipantId)} and
 * never mutates match state. The Open Upgrades / Close Pause /
 * Command Guide buttons run callbacks supplied by the controller.
 *
 * <p><b>Offline-only.</b>
 */
public class MabCompactHudPanel extends JPanel {

    // ── Status card ──
    private final JLabel defconValue = MabUiTheme.statValue("?", MabUiTheme.WARNING);
    private final JLabel phaseValue = MabUiTheme.bodyLabel("?");
    private final JLabel profileValue = MabUiTheme.mutedLabel(" ");
    private final JLabel opponentValue = MabUiTheme.mutedLabel(" ");

    // ── Arsenal card ──
    private final JLabel nukeName = MabUiTheme.bodyLabel("?");
    private final JLabel nukeChargeBar = MabUiTheme.statValue("0/0", MabUiTheme.INFO);
    private final JLabel armedValue = MabUiTheme.statValue("no", MabUiTheme.TEXT_MUTED);
    private final JLabel siloValue = MabUiTheme.statValue("100/100", MabUiTheme.SUCCESS);
    private final JLabel launchReady = MabUiTheme.bodyLabel(" ");

    // ── Incoming card ──
    private final JLabel incomingValue = MabUiTheme.statValue("0", MabUiTheme.TEXT);
    private final JLabel impactReadyValue = MabUiTheme.statValue("0", MabUiTheme.TEXT);
    private final JLabel firstWarn = MabUiTheme.statValue("-", MabUiTheme.TEXT_MUTED);
    private final JLabel suggestedResponse = MabUiTheme.bodyLabel(" ");

    // ── Action card ──
    private final JLabel actionName = MabUiTheme.bodyLabel("(none)");
    private final JLabel actionProgress = MabUiTheme.statValue("0/0", MabUiTheme.INFO);
    private final JLabel actionNextClear = MabUiTheme.statValue("-", MabUiTheme.TEXT_MUTED);
    private final JLabel actionEntered = MabUiTheme.statValue("-", MabUiTheme.TEXT_MUTED);
    private final JLabel actionSpinAllowed = MabUiTheme.statValue("-", MabUiTheme.TEXT_MUTED);
    /** Step 20 Fourth Refinement — always visible "last line clear seen"
     *  read-out so the player knows their input reached the system. */
    private final JLabel actionLastClear = MabUiTheme.statValue("-", MabUiTheme.TEXT_MUTED);
    private final JLabel actionConfirm = MabUiTheme.bodyLabel(" ");
    private final JLabel actionFeedback = MabUiTheme.bodyLabel(" ");

    // ── Opponent intel card ──
    private final JLabel intelLevel = MabUiTheme.bodyLabel("?");
    private final JLabel intelConf = MabUiTheme.statValue("?", MabUiTheme.TEXT_MUTED);
    private final JLabel intelStale = MabUiTheme.statValue("?", MabUiTheme.TEXT_MUTED);
    private final JLabel scansValue = MabUiTheme.statValue("0", MabUiTheme.TEXT_MUTED);

    // ── Events strip ──
    private final JTextArea eventsArea = new JTextArea(6, 28);

    // ── Controls ──
    private final JButton openUpgradeBtn = button("Open Upgrades");
    private final JButton closeUpgradeBtn = button("Close Upgrade Pause");
    private final JButton commandGuideBtn = button("Command Guide");
    private final JButton fullHudBtn = button("Full HUD");

    private Runnable openUpgradeAction = () -> {};
    private Runnable closeUpgradeAction = () -> {};
    private Runnable commandGuideAction = () -> {};
    private Runnable fullHudAction = () -> {};

    private MabAiArchetype opponentArchetype;
    private MabAiDifficulty opponentDifficulty;
    private String balanceProfileName = "";
    private MabActionFeedback.Message latestFeedback = MabActionFeedback.Message.EMPTY;

    public MabCompactHudPanel() {
        super(new BorderLayout(0, 0));
        MabUiTheme.styleAsRoot(this);
        setBorder(MabUiTheme.padding(6, 8, 6, 8));

        JPanel cards = new JPanel();
        cards.setOpaque(false);
        cards.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        gbc.insets = new Insets(0, 0, 0, 6);

        gbc.gridx = 0; gbc.weightx = 0.10; cards.add(buildStatusCard(), gbc);
        gbc.gridx = 1; gbc.weightx = 0.16; cards.add(buildArsenalCard(), gbc);
        gbc.gridx = 2; gbc.weightx = 0.16; cards.add(buildIncomingCard(), gbc);
        gbc.gridx = 3; gbc.weightx = 0.16; cards.add(buildActionCard(), gbc);
        gbc.gridx = 4; gbc.weightx = 0.13; cards.add(buildIntelCard(), gbc);
        gbc.gridx = 5; gbc.weightx = 0.20; cards.add(buildEventsCard(), gbc);
        gbc.gridx = 6; gbc.weightx = 0.09; gbc.insets = new Insets(0, 0, 0, 0);
        cards.add(buildControlsCard(), gbc);

        add(cards, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1280, 240));
    }

    private JPanel buildStatusCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 2));
        body.setOpaque(false);
        body.add(MabUiTheme.kvRow("DEFCON", defconValue));
        body.add(MabUiTheme.kvRow("Phase", phaseValue));
        body.add(MabUiTheme.kvRow("Profile", profileValue));
        body.add(MabUiTheme.kvRow("Opponent", opponentValue));
        return MabUiTheme.card("Status", body);
    }

    private JPanel buildArsenalCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 2));
        body.setOpaque(false);
        body.add(MabUiTheme.kvRow("Nuke", nukeName));
        body.add(MabUiTheme.kvRow("Charge", nukeChargeBar));
        body.add(MabUiTheme.kvRow("Armed", armedValue));
        body.add(MabUiTheme.kvRow("Silo", siloValue));
        body.add(launchReady);
        return MabUiTheme.card("Your Arsenal", body);
    }

    private JPanel buildIncomingCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 2));
        body.setOpaque(false);
        body.add(MabUiTheme.kvRow("Incoming", incomingValue));
        body.add(MabUiTheme.kvRow("Impact-ready", impactReadyValue));
        body.add(MabUiTheme.kvRow("First warn pcs", firstWarn));
        body.add(suggestedResponse);
        return MabUiTheme.card("Threats", body);
    }

    private JPanel buildActionCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 2));
        body.setOpaque(false);
        body.add(MabUiTheme.kvRow("Last clear", actionLastClear));
        body.add(MabUiTheme.kvRow("Charge", actionName));
        body.add(MabUiTheme.kvRow("Tetris route", actionProgress));
        body.add(MabUiTheme.kvRow("Spin route", actionNextClear));
        body.add(MabUiTheme.kvRow("Status", actionEntered));
        body.add(MabUiTheme.kvRow("Last gain", actionSpinAllowed));
        body.add(actionConfirm);
        body.add(actionFeedback);
        return MabUiTheme.card("Strategic (skill)", body);
    }

    private JPanel buildIntelCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 2));
        body.setOpaque(false);
        body.add(MabUiTheme.kvRow("Level", intelLevel));
        body.add(MabUiTheme.kvRow("Conf", intelConf));
        body.add(MabUiTheme.kvRow("Stale", intelStale));
        body.add(MabUiTheme.kvRow("Scans", scansValue));
        return MabUiTheme.card("Intel", body);
    }

    private JPanel buildEventsCard() {
        eventsArea.setEditable(false);
        eventsArea.setLineWrap(false);
        eventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        eventsArea.setBackground(new Color(0x12, 0x18, 0x24));
        eventsArea.setForeground(MabUiTheme.TEXT);
        eventsArea.setBorder(MabUiTheme.padding(4, 6, 4, 6));
        // The Events strip is the only scrollable region; spec allows
        // optional detail to scroll, just not the core warnings.
        JScrollPane scroll = new JScrollPane(eventsArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(0x12, 0x18, 0x24));
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return MabUiTheme.card("Recent Events", scroll);
    }

    private JPanel buildControlsCard() {
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 4));
        body.setOpaque(false);
        openUpgradeBtn.addActionListener(e -> openUpgradeAction.run());
        closeUpgradeBtn.addActionListener(e -> closeUpgradeAction.run());
        commandGuideBtn.addActionListener(e -> commandGuideAction.run());
        fullHudBtn.addActionListener(e -> fullHudAction.run());
        body.add(openUpgradeBtn);
        body.add(closeUpgradeBtn);
        body.add(commandGuideBtn);
        body.add(fullHudBtn);
        return MabUiTheme.card("Controls", body);
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        return b;
    }

    // ── Wiring ──

    public void setOpenUpgradeAction(Runnable r)  { this.openUpgradeAction  = r == null ? () -> {} : r; }
    public void setCloseUpgradeAction(Runnable r) { this.closeUpgradeAction = r == null ? () -> {} : r; }
    public void setCommandGuideAction(Runnable r) { this.commandGuideAction = r == null ? () -> {} : r; }
    public void setFullHudAction(Runnable r)      { this.fullHudAction      = r == null ? () -> {} : r; }

    public void setOpponentInfo(MabAiArchetype archetype, MabAiDifficulty difficulty,
                                String profileName) {
        this.opponentArchetype = archetype;
        this.opponentDifficulty = difficulty;
        this.balanceProfileName = profileName == null ? "" : profileName;
        opponentValue.setText(
                (archetype == null ? "?" : archetype.name())
                        + " / " + (difficulty == null ? "?" : difficulty.name()));
        profileValue.setText(balanceProfileName.isBlank() ? "-" : balanceProfileName);
    }

    /** Refresh all cards from the live match. Safe on the EDT. */
    public void refresh(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        if (match == null) {
            phaseValue.setText("(no match)");
            return;
        }
        ParticipantId pid = (playerId == null) ? ParticipantId.PLAYER_A : playerId;
        MatchDebugSnapshot snap;
        List<MatchEventLogEntry> recent;
        try {
            snap = match.toDebugSnapshot();
            recent = match.getRecentEvents(40);
        } catch (RuntimeException ex) {
            phaseValue.setText("(snapshot error)");
            return;
        }

        // Status
        defconValue.setText("DEFCON " + snap.defconLevel());
        defconValue.setForeground(defconColor(snap.defconLevel()));
        MatchPhase phase = snap.currentPhase();
        phaseValue.setText(phase == null ? "?" : phase.name());

        ParticipantSummary self = pickSelf(snap, pid);
        if (self == null) return;

        // Arsenal
        nukeName.setText(safe(self.currentNukeDisplayName()));
        nukeChargeBar.setText(self.currentNukeCharge() + "/" + self.requiredNukeCharge());
        nukeChargeBar.setForeground(self.currentNukeCharge() >= self.requiredNukeCharge()
                ? MabUiTheme.SUCCESS : MabUiTheme.INFO);
        armedValue.setText(self.armed() ? "YES" : "no");
        armedValue.setForeground(self.armed() ? MabUiTheme.SUCCESS : MabUiTheme.TEXT_MUTED);
        siloValue.setText(self.siloIntegrity() + "/100");
        siloValue.setForeground(siloColor(self.siloIntegrity()));
        launchReady.setText(formatLaunchReady(self));
        launchReady.setForeground(launchReadyColor(self));

        // Incoming
        int incoming = self.incomingThreatCount();
        int impactReady = self.impactReadyThreatCount();
        incomingValue.setText(String.valueOf(incoming));
        incomingValue.setForeground(incoming > 0 ? MabUiTheme.WARNING : MabUiTheme.TEXT);
        impactReadyValue.setText(String.valueOf(impactReady));
        impactReadyValue.setForeground(impactReady > 0 ? MabUiTheme.CRITICAL : MabUiTheme.TEXT);
        if (self.firstIncomingThreatId() != null) {
            firstWarn.setText(String.valueOf(self.firstIncomingThreatWarningPiecesRemaining()));
            firstWarn.setForeground(MabUiTheme.WARNING);
        } else {
            firstWarn.setText("-");
            firstWarn.setForeground(MabUiTheme.TEXT_MUTED);
        }
        suggestedResponse.setText(suggestResponse(self));
        suggestedResponse.setForeground(impactReady > 0 ? MabUiTheme.CRITICAL
                : (incoming > 0 ? MabUiTheme.WARNING : MabUiTheme.TEXT_MUTED));

        // Step 21 — simplified strategic readout (replaces action-code UI).
        com.tetris.mab.clear.MabSimplifiedStrategicState simp = null;
        try {
            ParticipantState ps = match.getParticipant(pid);
            if (ps != null) simp = ps.getSimplifiedState();
        } catch (RuntimeException ignored) {}

        if (simp != null) {
            int cur = simp.chargeCurrent();
            int req = Math.max(1, simp.chargeRequired());
            actionName.setText(cur + "/" + req + (simp.nukeReady() ? "  READY" : ""));
            actionName.setForeground(simp.nukeReady()
                    ? MabUiTheme.SUCCESS : MabUiTheme.INFO);
            actionProgress.setText(simp.launchTetrisProgress() + "/"
                    + simp.launchTetrisGoal());
            actionProgress.setForeground(simp.nukeReady()
                    ? MabUiTheme.INFO : MabUiTheme.TEXT_MUTED);
            actionNextClear.setText(simp.launchSpinProgress() + "/"
                    + simp.launchSpinGoal());
            actionNextClear.setForeground(simp.nukeReady()
                    ? MabUiTheme.INFO : MabUiTheme.TEXT_MUTED);
            String status;
            if (incoming > 0) {
                status = "INCOMING \u2014 SPIN TO INTERCEPT";
                actionEntered.setForeground(MabUiTheme.CRITICAL);
            } else if (simp.nukeReady()) {
                status = "Fire: 4 Tetrises OR 2 Spins";
                actionEntered.setForeground(MabUiTheme.SUCCESS);
            } else {
                status = "Build charge with line clears";
                actionEntered.setForeground(MabUiTheme.TEXT_MUTED);
            }
            actionEntered.setText(status);
            String last = simp.lastClearText();
            int gain = simp.lastChargeGained();
            actionSpinAllowed.setText(
                    (last == null || last.isEmpty()) ? "-"
                            : last + (gain > 0 ? "" : ""));
            actionSpinAllowed.setForeground(MabUiTheme.TEXT);
        } else {
            actionName.setText("-");
            actionProgress.setText("-");
            actionNextClear.setText("-");
            actionEntered.setText(" ");
            actionSpinAllowed.setText("-");
        }
        if (incoming > 0) {
            actionConfirm.setText("INCOMING \u2014 SPIN TO INTERCEPT");
            actionConfirm.setForeground(MabUiTheme.CRITICAL);
        } else {
            actionConfirm.setText(" ");
        }

        // Step 20 Third Refinement — derive last action-code feedback
        // line from recent events and surface it both inside the
        // Action Code card and (via getter) in the top notification
        // banner of MabPveGamePanel.
        latestFeedback = MabActionFeedback.latest(recent, pid);
        if (latestFeedback.isEmpty()) {
            actionFeedback.setText(" ");
        } else {
            actionFeedback.setText(latestFeedback.text);
            actionFeedback.setForeground(MabActionFeedback.colorOf(latestFeedback.severity));
        }

        // Step 20 Fourth Refinement — always-visible "last clear" row
        // sourced from the most recent INPUT_LINE_CLEAR event for
        // this player. Persists between attempts so the player can
        // verify their input was observed.
        String lastClear = "-";
        for (int i = recent.size() - 1; i >= 0; i--) {
            com.tetris.mab.MatchEventLogEntry ev = recent.get(i);
            if (ev == null || !"INPUT_LINE_CLEAR".equals(ev.eventType())) continue;
            if (ev.participantId() != null && ev.participantId() != pid) continue;
            Object cnt = ev.metadata() == null ? null : ev.metadata().get("count");
            int n = (cnt instanceof Number) ? ((Number) cnt).intValue() : 0;
            lastClear = (n > 0 ? n + "-line" : "(line)");
            break;
        }
        actionLastClear.setText(lastClear);

        // Intel
        intelLevel.setText(String.valueOf(self.lastIntelLevel()));
        intelConf.setText(String.valueOf(self.lastIntelConfidence()));
        intelStale.setText(self.intelStale() ? "STALE" : "current");
        intelStale.setForeground(self.intelStale() ? MabUiTheme.WARNING : MabUiTheme.SUCCESS);
        scansValue.setText(self.successfulRadarScans() + "/" + self.totalRadarScans());

        // Events
        StringBuilder sb = new StringBuilder();
        int rows = 0;
        for (String row : MabHudFormatter.formatEventFeed(recent, 10)) {
            sb.append(row).append('\n');
            rows++;
            if (rows >= 10) break;
        }
        eventsArea.setText(sb.toString());
        eventsArea.setCaretPosition(0);

        // Controls enabled state
        boolean inUpgrade = phase == MatchPhase.UPGRADE_PAUSE;
        openUpgradeBtn.setEnabled(!inUpgrade && phase != MatchPhase.GAME_OVER);
        closeUpgradeBtn.setEnabled(inUpgrade);
    }

    private static String formatLaunchReady(ParticipantSummary p) {
        if (p.impactReadyLaunchCount() > 0) return "IMPACT READY";
        if (p.activeLaunchCount() > 0) return "Launch in flight";
        if (p.armed()) return "ARMED — enter launch code";
        if (p.requiredNukeCharge() > 0) return "Charging";
        return "Not enough charge";
    }

    private static Color launchReadyColor(ParticipantSummary p) {
        if (p.impactReadyLaunchCount() > 0) return MabUiTheme.SUCCESS;
        if (p.activeLaunchCount() > 0) return MabUiTheme.INFO;
        if (p.armed()) return MabUiTheme.WARNING;
        return MabUiTheme.TEXT_MUTED;
    }

    private static String suggestResponse(ParticipantSummary p) {
        if (p.impactReadyThreatCount() > 0) return "Intercept or civil defense NOW";
        if (p.incomingThreatCount() > 0) return "Charge intercept (1,2,1)";
        return "Build charge / scan";
    }

    private static Color defconColor(int level) {
        if (level <= 1) return MabUiTheme.CRITICAL;
        if (level == 2) return MabUiTheme.WARNING;
        if (level == 3) return MabUiTheme.INFO;
        return MabUiTheme.SUCCESS;
    }

    private static Color siloColor(int integrity) {
        if (integrity <= 25) return MabUiTheme.CRITICAL;
        if (integrity <= 60) return MabUiTheme.WARNING;
        return MabUiTheme.SUCCESS;
    }

    private static ParticipantSummary pickSelf(MatchDebugSnapshot snap, ParticipantId pid) {
        if (snap == null) return null;
        if (pid == ParticipantId.PLAYER_A) return snap.playerA();
        if (pid == ParticipantId.PLAYER_B) return snap.playerB();
        return null;
    }

    private static String safe(String s) { return s == null ? "?" : s; }

    /** Step 20 Third Refinement — latest action-code feedback line.
     *  Used by {@link MabPveGamePanel} to render a top notification banner. */
    public MabActionFeedback.Message getLatestFeedback() { return latestFeedback; }

    private static String formatEnteredTokens(ActionCodeAttempt attempt) {
        if (attempt == null) return "-";
        java.util.List<ActionClearToken> done = attempt.getCompletedTokens();
        if (done == null || done.isEmpty()) return "(none yet)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < done.size(); i++) {
            ActionClearToken t = done.get(i);
            if (i > 0) sb.append(" \u2192 ");
            sb.append(t.lineCount());
            if (t.spinKind() != null && t.spinKind() != com.tetris.mab.action.SpinKind.NONE) {
                sb.append("S");
            }
        }
        return sb.toString();
    }

    private static String formatSpinAllowed(ActionCodeAttempt attempt) {
        if (attempt == null) return "-";
        ActionCodeTokenRequirement next = attempt.getExpectedNextRequirement();
        if (next == null) return "-";
        return next.spinMayReplace() ? "yes" : "no";
    }
}
