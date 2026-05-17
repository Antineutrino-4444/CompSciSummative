package com.tetris.mab.ui;

import com.tetris.controller.InputHandler;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.model.GameState;
import com.tetris.model.Settings;
import com.tetris.view.GamePanel;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;

/**
 * Step 23 Refinement \u2014 root cold-war battle shell for MAB PvE
 * (and local PvP). Replaces the legacy seven-card dashboard. The
 * structure mirrors {@code mab-battle-shell-mockup.html}:
 *
 * <pre>
 *   +--------------------------------------------------------------+
 *   |  [P1 BANNER]              [DEFCON]            [P2 BANNER]    |  88px
 *   |--------------------------------------------------------------|
 *   |  [BAY] [BOARD P1]   [OPS DECK 224px]   [BOARD P2] [BAY]      |  1fr
 *   |--------------------------------------------------------------|
 *   |  [P1 TACTICAL]          [MODE PLATE]      [P2 TACTICAL]      |  96px
 *   +--------------------------------------------------------------+
 * </pre>
 *
 * <p>Layout is a {@link JLayeredPane} that lays its layers manually so
 * the children always have non-zero bounds (a problem with the first
 * Step 23 attempt where children depended on a delayed
 * {@code componentResized} event and had {@code 0\u00d70} bounds at
 * mount-time \u2014 making the player board invisible).
 *
 * <p><b>Offline-only.</b>
 */
public final class MabBattleShellPanel extends JLayeredPane {

    private final MabStationBannerPanel playerBanner;
    private final MabStationBannerPanel opponentBanner;
    private final MabOpsDeckPanel opsDeck;
    private final MabTacticalStripPanel playerStrip;
    private final MabTacticalStripPanel opponentStrip;
    private final MabBoardHostPanel playerBoardHost;
    private final MabBoardHostPanel opponentBoardHost;
    private final MabPieceBayPanel playerBay;
    private final JComponent opponentBay;
    private final MabOpponentBoardPanel opponentBoardPanel;
    private final GamePanel playerGamePanel;
    private final GameState playerGameState;
    private final ParticipantId humanSide;
    private final ParticipantId opponentSideId;
    private final boolean opponentIsAi;
    private final long startMillis = System.currentTimeMillis();

    private final JPanel mainGrid;
    private final DefconEscalationBar defconBar = new DefconEscalationBar();
    private final ResultOverlay resultOverlay;
    private final MabUpgradeDraftOverlayPanel upgradeOverlay;
    private final MabActiveDoctrineOverlayPanel activeDoctrineOverlay;
    private final MabDoctrineStatusOverlayPanel doctrineStatusOverlay;
    private final JLabel toastLabel;
    private final JLabel pauseLabel;
    private boolean diagLogged = false;
    private MabBattleShellInputAdapter inputAdapter;
    private MabLocalPvpInputAdapter localPvpInputAdapter;
    private MutuallyAssuredBlocksMatch currentMatch;

    public MabBattleShellPanel(GamePanel playerGamePanel,
                               GameState playerGameState,
                               MabOpponentBoardPanel opponentBoardPanel,
                               ParticipantId humanSide,
                               ParticipantId opponentSideId,
                               String playerStationTitle,
                               String opponentStationTitle,
                               String modeLine,
                               String modeSub,
                               Runnable onBackToMenu) {
        this(playerGamePanel, playerGameState, opponentBoardPanel,
                humanSide, opponentSideId, playerStationTitle,
                opponentStationTitle, modeLine, modeSub, onBackToMenu,
                true, null);
    }

    public MabBattleShellPanel(GamePanel playerGamePanel,
                               GameState playerGameState,
                               MabOpponentBoardPanel opponentBoardPanel,
                               ParticipantId humanSide,
                               ParticipantId opponentSideId,
                               String playerStationTitle,
                               String opponentStationTitle,
                               String modeLine,
                               String modeSub,
                               Runnable onBackToMenu,
                               boolean opponentIsAi,
                               GameState opponentGameState) {
        if (playerGamePanel == null) throw new IllegalArgumentException("playerGamePanel");
        if (opponentBoardPanel == null) throw new IllegalArgumentException("opponentBoardPanel");
        this.playerGamePanel = playerGamePanel;
        this.playerGameState = playerGameState;
        this.opponentBoardPanel = opponentBoardPanel;
        this.humanSide = humanSide;
        this.opponentSideId = opponentSideId;
        this.opponentIsAi = opponentIsAi;
        this.opponentBoardPanel.setShellBoardMode(true);

        setOpaque(true);
        setBackground(MabUiTheme.SHELL_BG);

        playerBanner = new MabStationBannerPanel(playerStationTitle, /*mirrored*/ false);
        opponentBanner = new MabStationBannerPanel(opponentStationTitle, /*mirrored*/ true);
        opsDeck = new MabOpsDeckPanel();
        opsDeck.setModeLine(modeLine, modeSub);
        playerStrip = new MabTacticalStripPanel("P1 :: TACTICAL", /*mirrored*/ false);
        opponentStrip = new MabTacticalStripPanel(
                opponentIsAi ? "AI :: TACTICAL" : "P2 :: TACTICAL",
                /*mirrored*/ true);

        playerBoardHost = new MabBoardHostPanel(playerGamePanel, "FIELD P1", false);
        opponentBoardHost = new MabBoardHostPanel(opponentBoardPanel,
                opponentIsAi ? "FIELD AI" : "FIELD P2", true);
        playerBay = new MabPieceBayPanel(playerGameState, /*mirrored*/ false);
        opponentBay = opponentIsAi || opponentGameState == null
                ? buildIntelBay()
                : new MabPieceBayPanel(opponentGameState, /*mirrored*/ true);

        mainGrid = buildMainGrid();
        add(mainGrid, JLayeredPane.DEFAULT_LAYER);

        resultOverlay = new ResultOverlay();
        resultOverlay.setVisible(false);
        add(resultOverlay, JLayeredPane.PALETTE_LAYER);

        upgradeOverlay = new MabUpgradeDraftOverlayPanel();
        upgradeOverlay.setVisible(false);
        add(upgradeOverlay, JLayeredPane.MODAL_LAYER);

        activeDoctrineOverlay = new MabActiveDoctrineOverlayPanel();
        activeDoctrineOverlay.setVisible(false);
        add(activeDoctrineOverlay, JLayeredPane.MODAL_LAYER);

        doctrineStatusOverlay = new MabDoctrineStatusOverlayPanel();
        doctrineStatusOverlay.setVisible(false);
        add(doctrineStatusOverlay, JLayeredPane.MODAL_LAYER);

        toastLabel = new JLabel(" ", SwingConstants.CENTER);
        toastLabel.setOpaque(true);
        toastLabel.setBackground(new Color(2, 6, 11, 225));
        toastLabel.setForeground(MabUiTheme.C_AMBER);
        toastLabel.setFont(MabUiTheme.STENCIL_SMALL);
        toastLabel.setBorder(new LineBorder(MabUiTheme.C_AMBER_DIM, 1));
        toastLabel.setVisible(false);
        add(toastLabel, JLayeredPane.POPUP_LAYER);

        pauseLabel = new JLabel("PAUSED - PRESS PAUSE TO RESUME", SwingConstants.CENTER);
        pauseLabel.setOpaque(true);
        pauseLabel.setBackground(new Color(4, 8, 14, 230));
        pauseLabel.setForeground(MabUiTheme.C_AMBER);
        pauseLabel.setFont(MabUiTheme.STENCIL_SMALL);
        pauseLabel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.C_AMBER_DIM, 1),
                new EmptyBorder(4, 12, 4, 12)));
        pauseLabel.setVisible(false);
        add(pauseLabel, JLayeredPane.POPUP_LAYER);

        // Decorative panels must NEVER steal focus or take part in
        // focus traversal — otherwise an arrow key on a focused
        // child would jump focus instead of moving the piece.
        playerBanner.setFocusable(false);
        opponentBanner.setFocusable(false);
        opsDeck.setFocusable(false);
        playerStrip.setFocusable(false);
        opponentStrip.setFocusable(false);
        playerBoardHost.setFocusable(false);
        opponentBoardHost.setFocusable(false);
        playerBay.setFocusable(false);
        opponentBay.setFocusable(false);
        opponentBoardPanel.setFocusable(false);

        setPreferredSize(new Dimension(1366, 768));
        setMinimumSize(new Dimension(1100, 640));
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.doLayout();
            return;
        }
        // Layers fill the entire panel.
        if (mainGrid != null) mainGrid.setBounds(0, 0, w, h);
        if (resultOverlay != null) resultOverlay.setBounds(0, 0, w, h);
        if (upgradeOverlay != null) upgradeOverlay.setBounds(0, 0, w, h);
        if (activeDoctrineOverlay != null) activeDoctrineOverlay.setBounds(0, 0, w, h);
        if (doctrineStatusOverlay != null) doctrineStatusOverlay.setBounds(0, 0, w, h);
        if (toastLabel != null) {
            int tw = Math.min(460, Math.max(260, w / 3));
            int ty = pauseLabel != null && pauseLabel.isVisible() ? 60 : 34;
            toastLabel.setBounds((w - tw) / 2, ty, tw, 28);
        }
        if (pauseLabel != null) {
            int pw = Math.min(360, Math.max(230, w / 4));
            pauseLabel.setBounds((w - pw) / 2, 30, pw, 24);
        }
        if (!diagLogged) {
            diagLogged = true;
            // Defer the diag log to the next event so child layouts have
            // resolved. Helpful for verifying the board got real bounds.
            SwingUtilities.invokeLater(this::logDiagnostics);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, MabUiTheme.SHELL_BG,
                0, Math.max(1, h), MabUiTheme.SHELL_DEEP_BG));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(0, 229, 224, 10));
        for (int x = 0; x < w; x += 48) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 48) g2.drawLine(0, y, w, y);

        g2.setColor(new Color(255, 179, 0, 22));
        g2.drawLine(12, 28, Math.max(12, w - 12), 28);
        g2.dispose();
    }

    private void logDiagnostics() {
        Dimension shell = getSize();
        Dimension pSize = playerGamePanel.getSize();
        Dimension oSize = opponentBoardPanel.getSize();
        System.out.println("[MAB-SHELL] mounted root=MabBattleShellPanel");
        System.out.println("[MAB-SHELL] shell actual=" + shell.width + "x" + shell.height);
        System.out.println("[MAB-SHELL] player board visible="
                + playerGamePanel.isShowing()
                + " size=" + pSize.width + "x" + pSize.height
                + " parent=" + (playerGamePanel.getParent() == null
                        ? "null"
                        : playerGamePanel.getParent().getClass().getSimpleName()));
        System.out.println("[MAB-SHELL] opponent board visible="
                + opponentBoardPanel.isShowing()
                + " size=" + oSize.width + "x" + oSize.height);
        System.out.println("[MAB-SHELL] focus requested player board");
    }

    private String buildComboHintForBanner(MutuallyAssuredBlocksMatch match,
                                           ParticipantId pid) {
        Settings s = Settings.get();
        String cw   = KeyEvent.getKeyText(s.getKeyRotateCW());
        String ccw  = KeyEvent.getKeyText(s.getKeyRotateCCW());
        String drop = KeyEvent.getKeyText(s.getKeyHardDrop());
        StringBuilder sb = new StringBuilder();
        for (com.tetris.mab.upgrade.draft.MabActiveDoctrineAvailability opt
                : match.getActiveDoctrineOptions(pid)) {
            if (!opt.owned()) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            switch (opt.type()) {
                case MANUAL_OVERRIDE ->
                    sb.append("OVERRIDE: ").append(cw).append(" ").append(ccw)
                      .append(" ").append(cw).append(" ").append(ccw)
                      .append(" ").append(drop);
                case EMP ->
                    sb.append("EMP: ").append(ccw).append(" ").append(cw)
                      .append(" ").append(ccw).append(" ").append(cw)
                      .append(" ").append(drop);
            }
        }
        return sb.toString();
    }

    private JPanel buildMainGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(4, 12, 12, 12));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(5, 5, 5, 5);

        // Row 0 banners (cols 0 and 2). Center column (col 1) spans all rows.
        gc.gridy = 0; gc.weighty = 0.0; gc.ipady = 0;
        gc.gridx = 0; gc.weightx = 1.0;
        playerBanner.setPreferredSize(new Dimension(420, 88));
        grid.add(playerBanner, gc);
        gc.gridx = 2; gc.weightx = 1.0;
        opponentBanner.setPreferredSize(new Dimension(420, 88));
        grid.add(opponentBanner, gc);

        // Center ops deck spans all rows, fixed 224 px wide.
        GridBagConstraints opsGc = new GridBagConstraints();
        opsGc.gridx = 1; opsGc.gridy = 0; opsGc.gridheight = 3;
        opsGc.fill = GridBagConstraints.BOTH;
        opsGc.weightx = 0.0; opsGc.weighty = 1.0;
        opsGc.insets = new Insets(5, 5, 5, 5);
        opsDeck.setPreferredSize(new Dimension(224, 600));
        opsDeck.setMinimumSize(new Dimension(224, 400));
        grid.add(opsDeck, opsGc);

        // Row 1 play areas.
        gc.gridy = 1; gc.weighty = 1.0;
        gc.gridx = 0; gc.weightx = 1.0;
        grid.add(buildPlayerPlayCell(), gc);
        gc.gridx = 2; gc.weightx = 1.0;
        grid.add(buildOpponentPlayCell(), gc);

        // Row 2 tactical strips.
        gc.gridy = 2; gc.weighty = 0.0;
        gc.gridx = 0; gc.weightx = 1.0;
        playerStrip.setPreferredSize(new Dimension(420, 96));
        grid.add(playerStrip, gc);
        gc.gridx = 2; gc.weightx = 1.0;
        opponentStrip.setPreferredSize(new Dimension(420, 96));
        grid.add(opponentStrip, gc);

        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(12, 0, 0, 0));
        wrapper.add(defconBar, BorderLayout.NORTH);
        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildPlayerPlayCell() {
        JPanel cell = new JPanel(new BorderLayout(8, 0));
        cell.setOpaque(false);
        playerBay.setPreferredSize(new Dimension(132, 480));
        cell.add(playerBay, BorderLayout.WEST);
        cell.add(playerBoardHost, BorderLayout.CENTER);
        return cell;
    }

    private JPanel buildOpponentPlayCell() {
        JPanel cell = new JPanel(new BorderLayout(8, 0));
        cell.setOpaque(false);
        cell.add(opponentBoardHost, BorderLayout.CENTER);
        opponentBay.setPreferredSize(new Dimension(132, 480));
        cell.add(opponentBay, BorderLayout.EAST);
        return cell;
    }

    private JComponent buildIntelBay() {
        JPanel bay = new JPanel(new BorderLayout());
        bay.setOpaque(true);
        bay.setBackground(MabUiTheme.SHELL_PANEL_BG);
        bay.setBorder(new LineBorder(MabUiTheme.GRID_LINE, 1));
        JLabel header = new JLabel("INTEL", SwingConstants.RIGHT);
        header.setFont(MabUiTheme.TERM_TINY);
        header.setForeground(MabUiTheme.TEXT_FAINT);
        header.setBorder(new EmptyBorder(8, 8, 4, 12));
        bay.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new java.awt.GridLayout(0, 1, 0, 6));
        body.setBorder(new EmptyBorder(6, 10, 10, 10));
        for (String row : new String[]{"AI :: ONLINE", "PROFILE :: NORMAL", "RNG :: SHARED",
                "DOCTRINE :: BAL", "RADAR :: PASSIVE"}) {
            JLabel l = new JLabel(row, SwingConstants.RIGHT);
            l.setFont(MabUiTheme.TERM_TINY);
            l.setForeground(MabUiTheme.TEXT_FAINT);
            body.add(l);
        }
        bay.add(body, BorderLayout.CENTER);
        return bay;
    }

    /** Single fan-out refresh entry point for the controller's tick. */
    public void refreshAll(MutuallyAssuredBlocksMatch match) {
        if (match == null) return;
        currentMatch = match;
        playerBanner.refresh(match, humanSide, true);
        opponentBanner.refresh(match, opponentSideId, false);
        opsDeck.refresh(match, humanSide, opponentSideId);
        playerStrip.refresh(match, humanSide);
        opponentStrip.refresh(match, opponentSideId);
        opponentBoardPanel.refresh(match, opponentSideId);

        // Live board-station meta lines.
        if (playerGameState != null) {
            int lv = playerGameState.getScoreSystem() == null ? 0
                    : playerGameState.getScoreSystem().getLevel();
            int ln = playerGameState.getScoreSystem() == null ? 0
                    : playerGameState.getScoreSystem().getTotalLinesCleared();
            playerBoardHost.setMeta(String.format("LV %02d \u00B7 LINES %03d", lv, ln));
        }
        // Bay repaint (next/hold may have advanced).
        playerBay.repaint();
        opponentBay.repaint();

        com.tetris.mab.DefconState ds = match.getDefconState();
        if (ds != null) defconBar.refresh(ds.getLevel(), ds.getProgressToNextThreshold());
        pauseLabel.setVisible(match.isPaused()
                && !isKeyboardModalOverlayVisible()
                && !match.isGameOver());

        long ms = System.currentTimeMillis() - startMillis;
        opsDeck.setMatchClock(ms);
        playerBanner.setComboHint(buildComboHintForBanner(match, humanSide));
    }

    public void requestGameFocus() {
        SwingUtilities.invokeLater(() -> {
            // With the global key dispatcher installed, focus owner does
            // not matter for input. We still focus the player board so
            // any stray KeyListener path keeps working.
            playerGamePanel.setFocusable(true);
            playerGamePanel.requestFocusInWindow();
        });
    }

    /** Step 23 control refinement — wire the global key dispatcher
     *  using the controller's existing {@link InputHandler}. Must be
     *  called by the controller before the shell becomes visible. */
    public void setInputHandler(InputHandler handler) {
        if (inputAdapter != null) inputAdapter.shutdown();
        if (handler == null) { inputAdapter = null; return; }
        inputAdapter = new MabBattleShellInputAdapter(handler, this);
        // If we're already showing, install immediately. Otherwise the
        // first addNotify will install.
        if (isDisplayable()) inputAdapter.install();
    }

    public MabBattleShellInputAdapter getInputAdapter() { return inputAdapter; }

    public void setLocalPvpInputAdapter(MabLocalPvpInputAdapter adapter) {
        this.localPvpInputAdapter = adapter;
        if (isDisplayable() && localPvpInputAdapter != null
                && !localPvpInputAdapter.isInstalled()) {
            localPvpInputAdapter.install();
        }
    }

    public MabLocalPvpInputAdapter getLocalPvpInputAdapter() {
        return localPvpInputAdapter;
    }

    private void clearHeldInputs() {
        if (inputAdapter != null) inputAdapter.clearHeldKeys();
        if (localPvpInputAdapter != null) localPvpInputAdapter.clearHeldKeys();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (inputAdapter != null) inputAdapter.install();
        if (localPvpInputAdapter != null) localPvpInputAdapter.install();
    }

    @Override
    public void removeNotify() {
        if (inputAdapter != null) inputAdapter.shutdown();
        if (localPvpInputAdapter != null) localPvpInputAdapter.shutdown();
        super.removeNotify();
    }

    public void showResultOverlay(String title, String cause, String statsText,
                                  Runnable onRestart, Runnable onBack) {
        // Always drop any held movement so the player can't keep
        // shifting after the match ends or while interacting with the
        // result modal.
        clearHeldInputs();
        resultOverlay.show(title, cause, statsText,
                onRestart == null ? null : () -> {
                    clearHeldInputs();
                    onRestart.run();
                },
                onBack == null ? null : () -> {
                    clearHeldInputs();
                    onBack.run();
                });
        resultOverlay.setVisible(true);
        moveToFront(resultOverlay);
        revalidate();
        repaint();
    }

    public void hideResultOverlay() {
        clearHeldInputs();
        resultOverlay.setVisible(false);
        revalidate();
        repaint();
        requestGameFocus();
    }

    public boolean isResultVisible() { return resultOverlay.isVisible(); }

    /** Step 24 — show the level-up draft overlay. Pauses the held
     *  movement keys so the player doesn't keep shifting while picking. */
    public void showUpgradeOverlay(com.tetris.mab.upgrade.draft.MabUpgradeDraft draft,
                                   com.tetris.mab.upgrade.draft.MabUpgradeInventory inv,
                                   java.util.function.Consumer<
                                           com.tetris.mab.upgrade.draft.MabUpgradeCard> onPick) {
        clearHeldInputs();
        upgradeOverlay.show(draft, inv, card -> {
            clearHeldInputs();
            if (onPick != null) onPick.accept(card);
        });
        upgradeOverlay.setVisible(true);
        moveToFront(upgradeOverlay);
        revalidate();
        repaint();
    }

    public void hideUpgradeOverlay() {
        clearHeldInputs();
        upgradeOverlay.dismiss();
        revalidate();
        repaint();
        requestGameFocus();
    }

    public boolean isUpgradeOverlayVisible() { return upgradeOverlay.isVisible(); }
    public MabUpgradeDraftOverlayPanel getUpgradeOverlay() { return upgradeOverlay; }
    public MabActiveDoctrineOverlayPanel getActiveDoctrineOverlay() { return activeDoctrineOverlay; }
    public MabDoctrineStatusOverlayPanel getDoctrineStatusOverlay() { return doctrineStatusOverlay; }

    public void showDoctrineStatusOverlay() {
        clearHeldInputs();
        if (currentMatch == null) {
            flashToast("STATUS UNAVAILABLE");
            requestGameFocus();
            return;
        }
        doctrineStatusOverlay.showStatus(currentMatch, humanSide, this::hideDoctrineStatusOverlay);
        moveToFront(doctrineStatusOverlay);
        revalidate();
        repaint();
    }

    public void hideDoctrineStatusOverlay() {
        clearHeldInputs();
        doctrineStatusOverlay.dismiss();
        revalidate();
        repaint();
        requestGameFocus();
    }

    public void showActiveDoctrineOverlay() {
        showActiveCommandsOverlay(humanSide);
    }

    public void showActiveCommandsOverlay(ParticipantId forPlayer) {
        clearHeldInputs();
        if (isKeyboardModalOverlayVisible()) { requestGameFocus(); return; }
        if (currentMatch == null) {
            flashToast("ACTIVE COMMANDS UNAVAILABLE");
            requestGameFocus();
            return;
        }
        if (currentMatch.isGameOver()) { requestGameFocus(); return; }
        if (!currentMatch.hasAnyActiveDoctrineOwned(forPlayer)) {
            String who = forPlayer == ParticipantId.PLAYER_A ? "P1" : "P2";
            flashToast("NO ACTIVE COMMANDS LOADED — " + who);
            requestGameFocus();
            return;
        }
        String title = forPlayer == ParticipantId.PLAYER_A
                ? "PLAYER 1 ACTIVE COMMANDS"
                : "PLAYER 2 ACTIVE COMMANDS";
        activeDoctrineOverlay.showOptions(
                title,
                currentMatch.getActiveDoctrineOptions(forPlayer),
                type -> {
                    clearHeldInputs();
                    var result = currentMatch.useActiveDoctrine(forPlayer, type);
                    hideActiveDoctrineOverlay();
                    flashToast(result.success()
                            ? result.title() + " :: " + result.detail()
                            : result.title() + " :: " + result.detail());
                },
                this::hideActiveDoctrineOverlay);
        moveToFront(activeDoctrineOverlay);
        revalidate();
        repaint();
    }

    public void hideActiveDoctrineOverlay() {
        clearHeldInputs();
        activeDoctrineOverlay.dismiss();
        revalidate();
        repaint();
        requestGameFocus();
    }

    public boolean isActiveDoctrineOverlayVisible() {
        return activeDoctrineOverlay != null && activeDoctrineOverlay.isVisible();
    }

    public boolean isDoctrineStatusOverlayVisible() {
        return doctrineStatusOverlay != null && doctrineStatusOverlay.isVisible();
    }

    public void handleActiveDoctrineHotkey() {
        showActiveDoctrineOverlay();
    }

    public void fireActiveDoctrine(ParticipantId playerId,
                                   com.tetris.mab.upgrade.draft.MabActiveDoctrineType type) {
        if (currentMatch == null || currentMatch.isGameOver()) return;
        com.tetris.mab.upgrade.draft.MabActiveDoctrineUseResult result =
                currentMatch.useActiveDoctrine(playerId, type);
        String who = playerId == ParticipantId.PLAYER_A ? "P1" : "P2";
        flashToast(who + " :: " + type.displayName() + " :: "
                + (result.success() ? "ACTIVATED" : result.detail()));
    }

    public void setNukeDesignStatus(String playerDesign, String opponentDesign) {
        playerBanner.setNukeDesignLine(playerDesign == null ? "" : playerDesign);
        opponentBanner.setNukeDesignLine(opponentDesign == null ? "" : opponentDesign);
    }

    public void showInputToast(String message) {
        clearHeldInputs();
        flashToast(message);
        requestGameFocus();
    }

    public boolean isKeyboardModalOverlayVisible() {
        return (upgradeOverlay != null && upgradeOverlay.isVisible())
                || (resultOverlay != null && resultOverlay.isVisible())
                || (activeDoctrineOverlay != null && activeDoctrineOverlay.isVisible())
                || (doctrineStatusOverlay != null && doctrineStatusOverlay.isVisible());
    }

    private void flashToast(String message) {
        toastLabel.setText(message == null ? "" : message);
        toastLabel.setVisible(true);
        moveToFront(toastLabel);
        Timer t = new Timer(1600, e -> {
            toastLabel.setVisible(false);
            repaint();
        });
        t.setRepeats(false);
        t.start();
        repaint();
    }

    // ── Accessors used by layout probe ────────────────────────────
    public MabStationBannerPanel getPlayerBanner() { return playerBanner; }
    public MabStationBannerPanel getOpponentBanner() { return opponentBanner; }
    public MabOpsDeckPanel getOpsDeck() { return opsDeck; }
    public MabTacticalStripPanel getPlayerStrip() { return playerStrip; }
    public MabTacticalStripPanel getOpponentStrip() { return opponentStrip; }
    public MabBoardHostPanel getPlayerBoardHost() { return playerBoardHost; }
    public MabBoardHostPanel getOpponentBoardHost() { return opponentBoardHost; }
    public MabOpponentBoardPanel getOpponentBoardPanel() { return opponentBoardPanel; }
    public GamePanel getPlayerGamePanel() { return playerGamePanel; }
    public MabPieceBayPanel getPlayerBay() { return playerBay; }
    public boolean isResultOverlayMounted() { return resultOverlay != null; }
    public boolean isResultOverlayShown() { return resultOverlay != null && resultOverlay.isVisible(); }
    public boolean isActiveDoctrineOverlayMounted() { return activeDoctrineOverlay != null; }
    public boolean isDoctrineStatusOverlayMounted() { return doctrineStatusOverlay != null; }

    public static String defaultModeLine(MabAiDifficulty diff) {
        return "PVE :: " + (diff == null ? "NORMAL" : diff.name()) + " AI";
    }

    public static String defaultModeSub() {
        return "SHARED SEQ // OFFLINE";
    }

    /** In-window result overlay panel (translucent veil + framed modal). */
    private static final class ResultOverlay extends JPanel {
        private final JLabel title = new JLabel(" ", SwingConstants.CENTER);
        private final JLabel cause = new JLabel(" ", SwingConstants.CENTER);
        private final JTextArea stats = new JTextArea();
        private final JButton restart = new JButton("RESTART");
        private final JButton back = new JButton("BACK TO MENU");

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            g.setColor(new Color(2, 6, 11, 180));
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }

        ResultOverlay() {
            super(new GridBagLayout());
            setOpaque(false);
            setFocusable(true);

            JPanel modal = new JPanel(new BorderLayout(0, 12));
            modal.setOpaque(true);
            modal.setBackground(MabUiTheme.SHELL_PANEL_BG);
            modal.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(MabUiTheme.C_CYAN_DIM, 2),
                    new EmptyBorder(20, 32, 20, 32)));

            title.setFont(MabUiTheme.STENCIL_HEADLINE.deriveFont(38f));
            title.setForeground(MabUiTheme.C_CYAN);
            modal.add(title, BorderLayout.NORTH);

            JPanel body = new JPanel(new BorderLayout(0, 8));
            body.setOpaque(false);
            cause.setFont(MabUiTheme.TERM_SMALL);
            cause.setForeground(MabUiTheme.TEXT_FAINT);
            body.add(cause, BorderLayout.NORTH);

            stats.setEditable(false);
            stats.setFont(MabUiTheme.TERM_MED);
            stats.setBackground(MabUiTheme.SHELL_DEEP_BG);
            stats.setForeground(MabUiTheme.TEXT);
            stats.setBorder(new EmptyBorder(10, 14, 10, 14));
            JScrollPane scroll = new JScrollPane(stats,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(new LineBorder(MabUiTheme.GRID_LINE, 1));
            scroll.getViewport().setBackground(MabUiTheme.SHELL_DEEP_BG);
            scroll.setPreferredSize(new Dimension(560, 200));
            body.add(scroll, BorderLayout.CENTER);
            modal.add(body, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            actions.setOpaque(false);
            styleBtn(restart, true);
            styleBtn(back, false);
            actions.add(restart);
            actions.add(back);
            modal.add(actions, BorderLayout.SOUTH);

            add(modal);
            installKeyboardActions();
        }

        void show(String t, String c, String s, Runnable onRestart, Runnable onBack) {
            title.setText(t == null ? "MATCH OVER" : t.toUpperCase());
            cause.setText(c == null ? " " : c);
            stats.setText(s == null ? "" : s);
            stats.setCaretPosition(0);
            for (var l : restart.getActionListeners()) restart.removeActionListener(l);
            for (var l : back.getActionListeners()) back.removeActionListener(l);
            restart.setEnabled(onRestart != null);
            back.setEnabled(onBack != null);
            if (onRestart != null) restart.addActionListener(e -> onRestart.run());
            if (onBack != null) back.addActionListener(e -> onBack.run());
            SwingUtilities.invokeLater(() -> {
                if (restart.isEnabled()) restart.requestFocusInWindow();
                else if (back.isEnabled()) back.requestFocusInWindow();
                else requestFocusInWindow();
            });
        }

        private void installKeyboardActions() {
            InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = getActionMap();
            im.put(javax.swing.KeyStroke.getKeyStroke("LEFT"), "resultPrev");
            im.put(javax.swing.KeyStroke.getKeyStroke("UP"), "resultPrev");
            im.put(javax.swing.KeyStroke.getKeyStroke("RIGHT"), "resultNext");
            im.put(javax.swing.KeyStroke.getKeyStroke("DOWN"), "resultNext");
            im.put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "resultConfirm");
            am.put("resultPrev", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    focusResultButton(-1);
                }
            });
            am.put("resultNext", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    focusResultButton(+1);
                }
            });
            am.put("resultConfirm", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (!isVisible()) return;
                    JButton b = restart.isFocusOwner() ? restart : (back.isFocusOwner() ? back : null);
                    if (b == null) b = restart.isEnabled() ? restart : back;
                    if (b != null && b.isEnabled()) b.doClick();
                }
            });
        }

        private void focusResultButton(int delta) {
            if (!isVisible()) return;
            JButton[] buttons = { restart, back };
            int idx = restart.isFocusOwner() ? 0 : (back.isFocusOwner() ? 1 : -1);
            if (idx < 0) idx = delta < 0 ? buttons.length : -1;
            for (int step = 1; step <= buttons.length; step++) {
                int next = ((idx + delta * step) % buttons.length + buttons.length) % buttons.length;
                if (buttons[next].isEnabled()) {
                    buttons[next].requestFocusInWindow();
                    return;
                }
            }
        }

        private static void styleBtn(JButton b, boolean primary) {
            b.setFont(MabUiTheme.STENCIL_SMALL);
            b.setForeground(MabUiTheme.C_CYAN);
            b.setBackground(MabUiTheme.SHELL_PANEL_BG);
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(primary ? MabUiTheme.C_CYAN : MabUiTheme.C_CYAN_DIM, 1),
                    new EmptyBorder(8, 18, 8, 18)));
        }
    }

    /** Thin horizontal bar at the top showing progress to the next DEFCON level. */
    private static final class DefconEscalationBar extends JPanel {
        private int defconLevel = 5;
        private double fraction = 0.0;

        DefconEscalationBar() {
            setOpaque(true);
            setBackground(MabUiTheme.SHELL_BG);
            setPreferredSize(new java.awt.Dimension(0, 7));
            setMinimumSize(new java.awt.Dimension(0, 7));
            setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 7));
        }

        void refresh(int level, double frac) {
            if (this.defconLevel == level && Math.abs(this.fraction - frac) < 0.001) return;
            this.defconLevel = level;
            this.fraction = frac;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            // Track background
            g.setColor(MabUiTheme.SHELL_PANEL_BG);
            g.fillRect(0, 0, w, h);
            // Fill
            int fillW = defconLevel == 1 ? w : (int)(w * Math.max(0.0, Math.min(1.0, fraction)));
            if (fillW > 0) {
                g.setColor(colorForDefcon(defconLevel));
                g.fillRect(0, 0, fillW, h);
            }
            // Bottom edge divider
            g.setColor(MabUiTheme.GRID_LINE);
            g.fillRect(0, h - 1, w, 1);
        }

        private static java.awt.Color colorForDefcon(int level) {
            return switch (level) {
                case 5  -> MabUiTheme.C_GREEN;
                case 4  -> new java.awt.Color(0xA8, 0xD4, 0x00);
                case 3  -> MabUiTheme.C_AMBER;
                case 2  -> new java.awt.Color(0xFF, 0x70, 0x20);
                default -> MabUiTheme.C_RED;
            };
        }
    }
}
