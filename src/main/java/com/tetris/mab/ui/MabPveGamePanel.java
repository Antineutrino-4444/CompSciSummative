package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.view.GameView;

import javax.swing.*;
import java.awt.*;

/**
 * Step 20 — embedded MAB PvE main gameplay layout.
 *
 * <p>Composes the existing playable {@link GameView} (Player A) with a
 * visible opponent board ({@link MabOpponentBoardPanel}) and the core
 * strategic HUD ({@link MabHudPanel}) inside a single focused window
 * surface. NORMAL_TETRIS continues to use {@code GameView} on its own
 * — this layout is only built when the controller is running in
 * {@code MAB_PVE} mode.
 *
 * <p>The panel does not own any timers itself. The owning
 * {@link com.tetris.mab.ui.MabPlayerFacingController} drives HUD and
 * opponent-board refresh; the {@link com.tetris.controller.GameController}
 * drives the player-A game loop; a separate
 * {@link com.tetris.mab.ai.MabBoardAiDriver} timer drives Player B.
 *
 * <p><b>Offline-only.</b>
 */
public class MabPveGamePanel extends JPanel {

    private static final String CARD_PLAY = "play";
    private static final String CARD_RESULT = "result";

    private final GameView playerView;
    private final MabOpponentBoardPanel opponentPanel;
    private final MabCompactHudPanel compactHud;
    /** Step 22 \u2014 dramatic large MAB stage indicator. */
    private final MabStageStripPanel stageStrip = new MabStageStripPanel();
    private final JLabel alertBanner = new JLabel(" ", SwingConstants.CENTER);
    /** Step 20 Third Refinement — second banner row showing the latest
     *  action-code event so invalid/reset/rejected feedback is
     *  unmissable on the main gameplay screen. */
    private final JLabel commandNotification = new JLabel(" ", SwingConstants.CENTER);
    private long lastNotificationSeq = -1L;

    // Step 20 Second Refinement — SOUTH region cards: live compact HUD
    // and embedded match-result summary, swapped via CardLayout.
    private final CardLayout southCards = new CardLayout();
    private final JPanel southHost = new JPanel();
    private final JTextArea resultBody = new JTextArea();
    private final JLabel resultTitle = new JLabel(" ", SwingConstants.CENTER);
    private final JButton resultRestart = new JButton("Restart MAB PvE");
    private final JButton resultBack = new JButton("Back to Menu");
    private final JButton resultClose = new JButton("Hide Result");
    private boolean resultVisible = false;

    public MabPveGamePanel(GameView playerView,
                           MabOpponentBoardPanel opponentPanel,
                           MabCompactHudPanel compactHud) {
        super(new BorderLayout(0, 0));
        if (playerView == null) throw new IllegalArgumentException("playerView");
        if (opponentPanel == null) throw new IllegalArgumentException("opponentPanel");
        if (compactHud == null) throw new IllegalArgumentException("compactHud");
        this.playerView = playerView;
        this.opponentPanel = opponentPanel;
        this.compactHud = compactHud;

        MabUiTheme.styleAsRoot(this);

        // ── NORTH: large dark alert banner + command notification ──
        alertBanner.setFont(MabUiTheme.BIG_FONT);
        alertBanner.setForeground(MabUiTheme.WARNING);
        alertBanner.setOpaque(true);
        alertBanner.setBackground(MabUiTheme.BANNER_BG);
        alertBanner.setBorder(MabUiTheme.padding(8, 12, 4, 12));
        commandNotification.setFont(MabUiTheme.BODY_BOLD);
        commandNotification.setForeground(MabUiTheme.TEXT_MUTED);
        commandNotification.setOpaque(true);
        commandNotification.setBackground(MabUiTheme.BANNER_BG);
        commandNotification.setBorder(MabUiTheme.padding(0, 12, 8, 12));
        JPanel north = new JPanel(new GridLayout(0, 1));
        north.setOpaque(false);
        north.add(stageStrip);
        north.add(alertBanner);
        north.add(commandNotification);
        add(north, BorderLayout.NORTH);

        // ── CENTER: player board (left) + opponent board (right) ──
        JPanel boards = new JPanel(new GridBagLayout());
        MabUiTheme.styleAsRoot(boards);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 8, 0, 4);
        gc.gridx = 0;
        gc.weightx = 1.0;
        boards.add(playerView, gc);
        gc.gridx = 1;
        gc.weightx = 0.0;
        gc.insets = new Insets(0, 4, 0, 8);
        boards.add(opponentPanel, gc);
        add(boards, BorderLayout.CENTER);

        // ── SOUTH: full-width compact dashboard, with embedded result
        // panel swapped in on game-over via CardLayout. ──
        southHost.setLayout(southCards);
        MabUiTheme.styleAsRoot(southHost);
        southHost.add(compactHud, CARD_PLAY);
        southHost.add(buildResultCard(), CARD_RESULT);
        southCards.show(southHost, CARD_PLAY);
        add(southHost, BorderLayout.SOUTH);
    }

    private JPanel buildResultCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(MabUiTheme.PANEL_BG);
        card.setBorder(MabUiTheme.padding(8, 12, 8, 12));

        resultTitle.setFont(MabUiTheme.HUGE_FONT);
        resultTitle.setForeground(MabUiTheme.TITLE);
        card.add(resultTitle, BorderLayout.NORTH);

        resultBody.setEditable(false);
        resultBody.setFont(MabUiTheme.BODY_FONT);
        resultBody.setBackground(MabUiTheme.CARD_BG);
        resultBody.setForeground(MabUiTheme.TEXT);
        resultBody.setBorder(MabUiTheme.padding(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(resultBody,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(MabUiTheme.DIVIDER, 1));
        scroll.getViewport().setBackground(MabUiTheme.CARD_BG);
        card.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(resultClose);
        buttons.add(resultBack);
        buttons.add(resultRestart);
        resultClose.addActionListener(e -> hideEmbeddedResult());
        card.add(buttons, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(1280, 220));
        return card;
    }

    /**
     * Step 20-refinement: show the match-result summary inside the main
     * PvE window, replacing the opponent+HUD column. Buttons run the
     * supplied callbacks; any may be {@code null} to disable.
     */
    public void showEmbeddedResult(String title, String body,
                                   Runnable onRestart,
                                   Runnable onBackToMenu) {
        resultTitle.setText(title == null ? "Match Result" : title);
        resultBody.setText(body == null ? "" : body);
        resultBody.setCaretPosition(0);
        for (java.awt.event.ActionListener l : resultRestart.getActionListeners()) {
            resultRestart.removeActionListener(l);
        }
        for (java.awt.event.ActionListener l : resultBack.getActionListeners()) {
            resultBack.removeActionListener(l);
        }
        resultRestart.setEnabled(onRestart != null);
        resultBack.setEnabled(onBackToMenu != null);
        if (onRestart != null) resultRestart.addActionListener(e -> onRestart.run());
        if (onBackToMenu != null) resultBack.addActionListener(e -> onBackToMenu.run());
        southCards.show(southHost, CARD_RESULT);
        resultVisible = true;
        revalidate();
        repaint();
        System.out.println("[MAB-PVE] embedded result panel shown");
    }

    public void hideEmbeddedResult() {
        southCards.show(southHost, CARD_PLAY);
        resultVisible = false;
        revalidate();
        repaint();
    }

    public boolean isResultVisible() { return resultVisible; }

    /**
     * Step 22 \u2014 refresh the dramatic stage strip. Safe to call from
     * the same Swing timer that drives {@link #refreshAlertBanner}.
     */
    public void refreshStageStrip(MutuallyAssuredBlocksMatch match,
                                  ParticipantId humanId) {
        stageStrip.refresh(match, humanId, /*pveMode*/ true);
    }

    /** Updates the persistent top alert banner from the live match. */
    public void refreshAlertBanner(MutuallyAssuredBlocksMatch match,
                                   ParticipantId humanId) {        if (match == null || humanId == null) {
            alertBanner.setText(" ");
            return;
        }
        try {
            var participant = match.getParticipant(humanId);
            int incoming = (participant != null && participant.getIncomingThreats() != null)
                    ? participant.getIncomingThreats().size() : 0;
            boolean upgradePause = match.getCurrentPhase()
                    == com.tetris.mab.MatchPhase.UPGRADE_PAUSE;
            if (match.isGameOver()) {
                alertBanner.setForeground(MabUiTheme.INFO);
                alertBanner.setText("MATCH ENDED \u2014 see result panel below");
            } else if (upgradePause) {
                alertBanner.setForeground(MabUiTheme.INFO);
                alertBanner.setText("UPGRADE PAUSE \u2014 click Open Upgrades in the dashboard");
            } else if (incoming > 0) {
                alertBanner.setForeground(MabUiTheme.CRITICAL);
                alertBanner.setText("INCOMING IMPACT \u2014 " + incoming
                        + " inbound \u00b7 SPIN TO INTERCEPT");
            } else {
                alertBanner.setForeground(MabUiTheme.TEXT_MUTED);
                alertBanner.setText("MAB PvE \u2014 dashboard below");
            }
        } catch (RuntimeException ex) {
            alertBanner.setText(" ");
        }
        // Step 20 Third Refinement — mirror the compact HUD's latest
        // action-code feedback into a top notification line. Persistent
        // until replaced by a newer action-code event.
        try {
            MabActionFeedback.Message m = compactHud.getLatestFeedback();
            if (m == null || m.isEmpty()) {
                if (lastNotificationSeq < 0) {
                    commandNotification.setText(" ");
                }
            } else if (m.sequenceNumber != lastNotificationSeq) {
                commandNotification.setText(m.text);
                commandNotification.setForeground(MabActionFeedback.colorOf(m.severity));
                lastNotificationSeq = m.sequenceNumber;
            }
        } catch (RuntimeException ignored) {}
    }

    public GameView getPlayerView() { return playerView; }
    public MabOpponentBoardPanel getOpponentPanel() { return opponentPanel; }
    public MabCompactHudPanel getCompactHud() { return compactHud; }

    public void requestGameFocus() {
        playerView.requestGameFocus();
    }
}
