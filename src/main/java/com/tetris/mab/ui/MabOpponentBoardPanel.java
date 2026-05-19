package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.ai.MabBoardAiPlan;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;

import javax.swing.*;
import java.awt.*;

/**
 * Step 20 — visible opponent (Player B) board panel for MAB PvE.
 *
 * <p>Wraps a render-only {@link GamePanel} bound to the opponent's
 * {@link GameState}. The same {@code GameState} is the one registered
 * with {@link MutuallyAssuredBlocksMatch}, so any garbage / damage
 * inflicted by the player or by the strategic AI is visible here.
 *
 * <p>No input is wired — the panel is display-only. A small status
 * strip below the board shows AI archetype/difficulty/profile and
 * the most useful opponent stats for understanding what is going on.
 *
 * <p><b>Offline-only.</b>
 */
public class MabOpponentBoardPanel extends JPanel {

    private final GameState opponent;
    private final GamePanel boardView;
    private final JPanel northPanel;
    private final JPanel boardWrap;
    private final JPanel southPanel;
    private final JLabel headerLabel = new JLabel("Opponent AI", SwingConstants.CENTER);
    private final JLabel profileLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel statsLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    // Step 20 Second Refinement — visible board-AI intent strip.
    private final JLabel aiPlanLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel aiPhaseLabel = new JLabel(" ", SwingConstants.CENTER);
    // Step 20 Third Refinement — PPS target + cumulative hard-drop count.
    private final JLabel aiPpsLabel = new JLabel(" ", SwingConstants.CENTER);

    private MabAiArchetype archetype;
    private MabAiDifficulty difficulty;
    private String balanceProfileName = "";
    private MabBoardAiDriver boardAi;
    private boolean shellBoardMode = false;

    public MabOpponentBoardPanel(GameState opponent) {
        super(new BorderLayout(4, 4));
        if (opponent == null) throw new IllegalArgumentException("opponent");
        this.opponent = opponent;
        setBorder(MabUiTheme.padding(6, 6, 6, 6));
        MabUiTheme.styleAsPanel(this);

        headerLabel.setFont(MabUiTheme.BIG_FONT);
        headerLabel.setForeground(MabUiTheme.TITLE);
        profileLabel.setFont(MabUiTheme.BODY_FONT);
        profileLabel.setForeground(MabUiTheme.TEXT_MUTED);
        statsLabel.setFont(MabUiTheme.BODY_FONT);
        statsLabel.setForeground(MabUiTheme.TEXT);
        statusLabel.setFont(MabUiTheme.BODY_BOLD);
        statusLabel.setForeground(MabUiTheme.CRITICAL);
        aiPlanLabel.setFont(MabUiTheme.BODY_FONT);
        aiPlanLabel.setForeground(MabUiTheme.INFO);
        aiPhaseLabel.setFont(MabUiTheme.BODY_FONT);
        aiPhaseLabel.setForeground(MabUiTheme.TEXT_MUTED);
        aiPpsLabel.setFont(MabUiTheme.BODY_FONT);
        aiPpsLabel.setForeground(MabUiTheme.TEXT_MUTED);

        northPanel = new JPanel(new GridLayout(0, 1));
        northPanel.setOpaque(false);
        northPanel.add(headerLabel);
        northPanel.add(profileLabel);
        add(northPanel, BorderLayout.NORTH);

        boardView = new GamePanel(opponent);
        boardView.setPreferredSize(new Dimension(280, 560));
        boardView.setFocusable(false);
        boardWrap = new JPanel(new BorderLayout());
        boardWrap.setBackground(MabUiTheme.ROOT_BG);
        boardWrap.setBorder(BorderFactory.createLineBorder(MabUiTheme.DIVIDER, 1));
        boardWrap.add(boardView, BorderLayout.CENTER);
        add(boardWrap, BorderLayout.CENTER);

        southPanel = new JPanel(new GridLayout(0, 1));
        southPanel.setOpaque(false);
        southPanel.add(statsLabel);
        southPanel.add(aiPlanLabel);
        southPanel.add(aiPhaseLabel);
        southPanel.add(aiPpsLabel);
        southPanel.add(statusLabel);
        add(southPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(320, 700));
    }

    /**
     * The battle shell already provides station chrome and tactical readouts.
     * In that context this panel should act as a pure board surface so the
     * opponent playfield matches the player's field and old AI/debug rows do
     * not steal space from the 10x20 board.
     */
    public void setShellBoardMode(boolean enabled) {
        if (shellBoardMode == enabled) return;
        shellBoardMode = enabled;
        northPanel.setVisible(!enabled);
        southPanel.setVisible(!enabled);
        setBorder(enabled ? BorderFactory.createEmptyBorder()
                : MabUiTheme.padding(6, 6, 6, 6));
        setOpaque(!enabled);
        setBackground(enabled ? MabUiTheme.SHELL_DEEP_BG : MabUiTheme.PANEL_BG);
        boardWrap.setBorder(enabled ? BorderFactory.createEmptyBorder()
                : BorderFactory.createLineBorder(MabUiTheme.DIVIDER, 1));
        boardWrap.setOpaque(!enabled);
        revalidate();
        repaint();
    }

    public boolean isShellBoardMode() { return shellBoardMode; }

    /** Step 20 Second Refinement — bind the visible board AI driver so
     *  the panel can render the current placement plan on each refresh. */
    public void setBoardAi(MabBoardAiDriver boardAi) {
        this.boardAi = boardAi;
    }

    public void setOpponentInfo(MabAiArchetype archetype, MabAiDifficulty difficulty,
                                MabBalanceProfile profile) {
        this.archetype = archetype;
        this.difficulty = difficulty;
        this.balanceProfileName = (profile == null) ? "" : profile.getDisplayName();
        refreshHeader();
    }

    public void setLocalPlayerInfo(String playerName, MabBalanceProfile profile) {
        this.archetype = null;
        this.difficulty = null;
        this.balanceProfileName = (profile == null) ? "" : profile.getDisplayName();
        String name = (playerName == null || playerName.isBlank()) ? "Player 2" : playerName.trim();
        headerLabel.setText("Player 2 - " + name);
        profileLabel.setText(balanceProfileName == null || balanceProfileName.isBlank()
                ? "Same keyboard offline duel"
                : "Same keyboard: " + balanceProfileName);
        boardAi = null;
        aiPlanLabel.setText(" ");
        aiPhaseLabel.setText(" ");
        aiPpsLabel.setText(" ");
    }

    private void refreshHeader() {
        String a = archetype == null ? "AI" : archetype.displayName();
        String d = difficulty == null ? "" : difficulty.displayName();
        headerLabel.setText("Opponent AI — " + a + (d.isEmpty() ? "" : " / " + d));
        if (balanceProfileName != null && !balanceProfileName.isBlank()) {
            profileLabel.setText("Balance: " + balanceProfileName);
        } else {
            profileLabel.setText(" ");
        }
    }

    /**
     * Refreshes the status strip from the live match. Cheap; safe to
     * call from a Swing timer at 5–10 Hz.
     */
    public void refresh(MutuallyAssuredBlocksMatch match, ParticipantId opponentId) {
        boardView.repaint();
        if (match == null || opponentId == null) {
            statsLabel.setText(" ");
            statusLabel.setText(" ");
            return;
        }
        ParticipantState ps = match.getParticipant(opponentId);
        int lines = (ps != null) ? ps.getLinesClearedTotal() : 0;
        int pieces = (ps != null) ? ps.getPiecesLocked() : 0;
        int height = opponent.getBoard().getStackHeight();
        int charge = (ps != null && ps.getNukeBuildState() != null)
                ? ps.getNukeBuildState().getCurrentBuildCharge() : 0;
        int needed = (ps != null && ps.getNukeBuildState() != null)
                ? ps.getNukeBuildState().getEffectiveBuildChargeRequired() : 0;
        boolean armed = (ps != null && ps.getNukeBuildState() != null)
                && ps.getNukeBuildState().isArmed();
        int activeLaunches = (ps != null && ps.getActiveLaunches() != null)
                ? ps.getActiveLaunches().size() : 0;
        int incoming = (ps != null && ps.getIncomingThreats() != null)
                ? ps.getIncomingThreats().size() : 0;

        statsLabel.setText(String.format(
                "lines=%d  pieces=%d  height=%d  charge=%d/%d%s",
                lines, pieces, height, charge, needed, armed ? "  ARMED" : ""));

        if (boardAi != null) {
            MabBoardAiPlan plan = boardAi.getPlan();
            if (plan == null || plan.getTargetCol() < 0) {
                aiPlanLabel.setText("AI plan: (replanning)");
            } else {
                aiPlanLabel.setText("AI plan: col " + plan.getTargetCol()
                        + "  rot " + plan.getTargetRotation()
                        + "  score " + plan.getScore());
            }
            aiPhaseLabel.setText("AI phase: "
                    + (plan == null ? "-" : plan.getPhase().name()));
            // Step 20 Third Refinement — surface measured-target PPS
            // and cumulative hard-drop count so the player can verify
            // the AI is actually locking pieces.
            double pps = (plan == null) ? 0.0 : plan.getTargetPps();
            int drops = (plan == null) ? 0 : plan.getHardDropCount();
            aiPpsLabel.setText("PPS " + String.format("%.2f", pps)
                    + "  drops=" + drops);
        } else {
            aiPlanLabel.setText(" ");
            aiPhaseLabel.setText(" ");
            aiPpsLabel.setText(" ");
        }

        StringBuilder st = new StringBuilder();
        if (opponent.isGameOver() || (ps != null && ps.isToppedOut())) {
            st.append("TOP-OUT");
        }
        if (activeLaunches > 0) {
            if (st.length() > 0) st.append("  ");
            st.append("launches=").append(activeLaunches);
        }
        if (incoming > 0) {
            if (st.length() > 0) st.append("  ");
            st.append("incoming=").append(incoming);
        }
        statusLabel.setText(st.length() == 0 ? " " : st.toString());
    }

    public GamePanel getBoardView() { return boardView; }
}
