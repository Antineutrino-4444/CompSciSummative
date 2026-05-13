package com.tetris.controller;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchMode;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.debugui.MabDebugController;
import com.tetris.mab.debugui.MabDebugFrame;
import com.tetris.mab.ui.MabHudPanel;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabLocalPvpInputAdapter;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.mab.ui.MabPlayerFacingController;
import com.tetris.mab.ui.MabPveConfig;
import com.tetris.mab.ui.MabPveGamePanel;
import com.tetris.model.GameState;
import com.tetris.model.Settings;
import com.tetris.view.GameView;
import com.tetris.view.MainFrame;
import com.tetris.view.SettingsPanel;

import com.tetris.view.DevConsolePanel;

import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;

/**
 * GameController.java
 * ====================
 * The central controller that connects the game model (GameState) with
 * the view (MainFrame). Runs the game loop and dispatches input actions.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * GAME LOOP ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════
 * Two separate Swing Timers, both running on the EDT (no concurrency):
 *
 *   physicsTimer — fixed 120 Hz (8 ms)
 *     Calls gameState.update() only. Physics runs at a constant rate
 *     regardless of display refresh rate, ensuring consistent gravity,
 *     lock delay, and collision behaviour across all machines.
 *
 *   renderTimer — variable, matched to the primary display refresh rate
 *     (e.g. 60 / 120 / 144 / 240 Hz; falls back to 60 Hz if unknown).
 *     Samples input (DAS/ARR), applies IRS/IHS on newly-spawned pieces,
 *     then repaints. Input is processed once per render frame so
 *     high-refresh monitors feel more responsive without affecting physics.
 *
 * Why Swing Timers instead of a Thread.sleep loop?
 *   Both timers fire on the Event Dispatch Thread (EDT), so Swing
 *   components can be touched safely with no locking required.
 *
 * FRAME RATE:
 *   Physics: fixed 120 Hz. Render: display refresh rate (auto-detected).
 *   All time-sensitive logic (gravity, lock delay) uses System.currentTimeMillis(),
 *   not frame counts, so behaviour is correct even if frames are dropped.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY SEPARATION
 * ═══════════════════════════════════════════════════════════════════════
 *   - GameController: owns the game loop timer, routes input → model
 *   - GameState: all game logic (movement validation, scoring, etc.)
 *   - InputHandler: keyboard event capture + DAS/ARR processing
 *   - MainFrame/Panels: pure rendering (reads GameState, draws pixels)
 *
 * The controller never directly modifies the board or score — it only
 * calls methods on GameState, which encapsulates all rules.
 */
public class GameController {

    /** Physics simulation: fixed 120 Hz. */
    private static final int PHYSICS_INTERVAL_MS = 8;

    /** Render interval (ms) matched to the primary display refresh rate. */
    private final int renderIntervalMs;

    // ─────────────────────── Components ─────────────────────────

    private GameState gameState;
    private MainFrame mainFrame;
    private GameView gameView; // populated when started in embedded mode
    private InputHandler inputHandler;
    private Timer physicsTimer;
    private Timer renderTimer;

    // ─────────────── Dev console ──────────────────────────────────
    private DevConsolePanel devConsole;

    // ─────────────── FPS tracking ─────────────────────────────────
    private long   fpsBucketStart  = 0;
    private int    fpsFrameCount   = 0;
    private int    lastRenderFps   = 0;

    // Step 12: visible MAB debug HUD (vertical slice). Opt-in via
    // -Dmab.debug.hud=true (default off as of Step 15).
    private MutuallyAssuredBlocksMatch mabMatch;
    private GameState mabPlayerBState; // visible Player B board (Step 20)
    private MabDebugFrame mabDebugFrame;
    // Step 15: player-facing MAB HUD controller (PvE mode only).
    private MabPlayerFacingController mabPlayerHud;
    // Step 20: deterministic visible-board AI for Player B + repaint timer
    // for the embedded opponent board / alert banner.
    private MabBoardAiDriver mabBoardAiDriver;
    private Timer mabBoardAiTimer;
    private MabPveGamePanel mabPveGamePanel;
    private com.tetris.mab.ui.MabBattleShellPanel mabBattleShell;
    private Timer mabEmbeddedRefreshTimer;
    private LocalInputRouter localPvpInputRouter;
    private MabLocalPvpInputAdapter localPvpInputAdapter;
    /** Tracks DEFCON level between ticks so we can detect escalations. */
    private int pvpLastKnownDefcon = 5;

    /** Starting level (remembered for restarts). */
    private final int startLevel;

    /** Step 15: which experience this controller drives. */
    private final GameLaunchMode launchMode;

    /** Step 18 — PvE configuration when launchMode == MAB_PVE; defaults otherwise. */
    private final MabPveConfig mabPveConfig;
    private final MabLocalPvpConfig mabLocalPvpConfig;

    /** Step 18 — callbacks the player-facing result dialog can invoke. */
    private Runnable mabRestartCallback;
    private Runnable mabBackToMenuCallback;
    /** Wired in embedded mode so EXIT_STAGE key returns to the parent screen. */
    private Runnable exitStageCallback;
    private long lastEscapePauseToggleMs;

    // ─────────────────────── Constructor ─────────────────────────

    /**
     * Creates a new GameController in {@link GameLaunchMode#NORMAL_TETRIS}.
     *
     * @param startLevel the level to start at (1+)
     */
    public GameController(int startLevel) {
        this(startLevel, GameLaunchMode.NORMAL_TETRIS);
    }

    /**
     * Step 15 — creates a new controller in the given launch mode.
     */
    public GameController(int startLevel, GameLaunchMode launchMode) {
        this(startLevel, launchMode, null, null);
    }

    /**
     * Step 18 — creates a controller pre-configured for MAB PvE. The
     * launch mode is implicitly {@link GameLaunchMode#MAB_PVE} and the
     * supplied {@link MabPveConfig} drives AI archetype/difficulty,
     * starting level, and HUD opt-ins.
     */
    public GameController(MabPveConfig config) {
        this((config == null ? 1 : config.getStartLevel()),
                GameLaunchMode.MAB_PVE,
                (config == null ? MabPveConfig.defaults() : config),
                null);
    }

    /** Step 26 - creates a configured same-keyboard, offline local PvP controller. */
    public GameController(MabLocalPvpConfig config) {
        this((config == null ? 1 : config.getStartLevel()),
                GameLaunchMode.MAB_LOCAL_PVP,
                null,
                (config == null ? MabLocalPvpConfig.defaults() : config));
    }

    /** Step 18 — full constructor; {@code config} may be null for non-PvE modes. */
    public GameController(int startLevel, GameLaunchMode launchMode, MabPveConfig config) {
        this(startLevel, launchMode, config, null);
    }

    private GameController(int startLevel, GameLaunchMode launchMode,
                           MabPveConfig config,
                           MabLocalPvpConfig localPvpConfig) {
        this.startLevel = Math.max(1, startLevel);
        this.launchMode = (launchMode == null) ? GameLaunchMode.NORMAL_TETRIS : launchMode;
        this.mabPveConfig = config == null ? MabPveConfig.defaults() : config;
        this.mabLocalPvpConfig = localPvpConfig == null
                ? MabLocalPvpConfig.defaults()
                : localPvpConfig;
        this.gameState = new GameState(this.startLevel);
        this.inputHandler = new InputHandler(this);
        this.renderIntervalMs = detectRenderIntervalMs();
    }

    public GameLaunchMode getLaunchMode() { return launchMode; }

    /** Step 18 — returns the PvE configuration used by this controller. */
    public MabPveConfig getMabPveConfig() { return mabPveConfig; }

    /** Step 26 - returns the local PvP configuration used by this controller. */
    public MabLocalPvpConfig getMabLocalPvpConfig() { return mabLocalPvpConfig; }

    /** Step 18 — wires callbacks that the post-match result dialog will invoke. */
    public void setMabPveCallbacks(Runnable onRestart, Runnable onBackToMenu) {
        this.mabRestartCallback = onRestart;
        this.mabBackToMenuCallback = onBackToMenu;
        if (mabPlayerHud != null) {
            mabPlayerHud.setRestartCallback(onRestart);
            mabPlayerHud.setBackToMenuCallback(onBackToMenu);
        }
    }

    // ─────────────────────── Lifecycle ───────────────────────────

    /**
     * Initializes the view and starts the game loop.
     * Called once from Main.
     */
    public void start() {
        // Create the main window, passing this controller's input handler
        mainFrame = new MainFrame(gameState, inputHandler);
        mainFrame.setVisible(true);

        // Stop the game loop when the window is closed so the menu can
        // come back cleanly without a stray timer ticking forever.
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { stop(); }
        });

        startTimers();
        openMabIntegrations();
    }

    /** Stops both timers. Safe to call multiple times. */
    public void stop() {
        closeMabIntegrations();
        stopTimers();
        if (gameView != null) {
            gameView.shutdown();
        }
    }

    /**
     * Embedded-mode entry point: builds a {@link GameView} JPanel that the
     * caller can drop into an existing window (e.g. the StartMenu's
     * CardLayout) and starts the game loop. {@code onExit} is invoked
     * when the player clicks the toolbar's Back button.
     *
     * @param onExit callback fired when the player wants to leave the game
     * @return a JComponent containing the full game UI. In NORMAL_TETRIS
     *         this is the {@link GameView} directly; in MAB_PVE this is a
     *         {@link MabPveGamePanel} wrapping the player board, the
     *         opponent board, and the embedded MAB HUD.
     */
    public javax.swing.JComponent startEmbedded(Runnable onExit) {
        Runnable exitAction = () -> { stop(); if (onExit != null) onExit.run(); };
        this.exitStageCallback = exitAction;
        gameView = new GameView(gameState, inputHandler, exitAction);
        startTimers();
        openMabIntegrations();
        if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && mabPlayerBState != null && mabMatch != null) {
            MabOpponentBoardPanel opponentPanel = new MabOpponentBoardPanel(mabPlayerBState);
            opponentPanel.setLocalPlayerInfo(
                    mabLocalPvpConfig.getPlayerBName(),
                    mabLocalPvpConfig.getBalanceProfile());

            String playerTitle = "STATION P1 :: "
                    + mabLocalPvpConfig.getPlayerAName().toUpperCase();
            String oppTitle = "STATION P2 :: "
                    + mabLocalPvpConfig.getPlayerBName().toUpperCase();
            final Runnable backRef = mabBackToMenuCallback;
            mabBattleShell = new com.tetris.mab.ui.MabBattleShellPanel(
                    gameView.getGamePanel(),
                    gameState,
                    opponentPanel,
                    ParticipantId.PLAYER_A,
                    ParticipantId.PLAYER_B,
                    playerTitle, oppTitle,
                    "LOCAL PvP :: SAME KEYBOARD",
                    "OFFLINE DUEL - TWO HUMAN BOARDS",
                    backRef,
                    false,
                    mabPlayerBState);

            final com.tetris.mab.ui.MabBattleShellPanel shellRef = mabBattleShell;
            localPvpInputRouter = new LocalInputRouter(
                    gameState,
                    mabPlayerBState,
                    Settings.get(),
                    () -> toggleMabMatchPause("local-pvp"),
                    () -> {
                        if (localPvpInputRouter != null) localPvpInputRouter.releaseAll();
                        if (mabRestartCallback != null) {
                            SwingUtilities.invokeLater(mabRestartCallback);
                        }
                    });
            localPvpInputAdapter = new MabLocalPvpInputAdapter(localPvpInputRouter, shellRef);
            shellRef.setLocalPvpInputAdapter(localPvpInputAdapter);

            mabEmbeddedRefreshTimer = new Timer(100, e -> {
                try {
                    if (mabMatch != null) {
                        if (!mabMatch.isPaused()) {
                            mabMatch.resolveAllImpactReady();
                            mabMatch.pruneCompletedThreats();
                        }
                        shellRef.refreshAll(mabMatch);
                        if (!mabMatch.isPaused()
                                && !shellRef.isUpgradeOverlayVisible()
                                && !shellRef.isResultVisible()) {
                            var draft = mabMatch.tickUpgradeDrafts();
                            if (draft != null) {
                                final var matchRef = mabMatch;
                                matchRef.openUpgradePause("upgrade_draft");
                                var inv = matchRef
                                        .getParticipant(draft.getParticipantId())
                                        .getUpgradeInventory();
                                shellRef.showUpgradeOverlay(draft, inv, card -> {
                                    matchRef.applyHumanUpgradeChoice(card);
                                    shellRef.hideUpgradeOverlay();
                                    matchRef.closeUpgradePause("upgrade_draft");
                                });
                            }
                            // Detect DEFCON escalation → show nuke redesign for each player.
                            int currentDefcon = mabMatch.getDefconState() != null
                                    ? mabMatch.getDefconState().getLevel() : pvpLastKnownDefcon;
                            if (currentDefcon < pvpLastKnownDefcon) {
                                pvpLastKnownDefcon = currentDefcon;
                                final var matchRef = mabMatch;
                                matchRef.openUpgradePause("nuke_redesign");
                                java.awt.Window owner =
                                        javax.swing.SwingUtilities.getWindowAncestor(shellRef);
                                MabNukeDesignSelection p1 = showNukeBuilderModal(
                                        "PLAYER 1 — REDESIGN NUKE (DEFCON " + currentDefcon + ")",
                                        owner);
                                MabNukeDesignSelection p2 = showNukeBuilderModal(
                                        "PLAYER 2 — REDESIGN NUKE (DEFCON " + currentDefcon + ")",
                                        owner);
                                int defcon = currentDefcon;
                                try {
                                    matchRef.getParticipant(ParticipantId.PLAYER_A)
                                            .getNukeBuildState()
                                            .redesign(p1.getDesign(), defcon, 0.5);
                                    matchRef.getParticipant(ParticipantId.PLAYER_B)
                                            .getNukeBuildState()
                                            .redesign(p2.getDesign(), defcon, 0.5);
                                } catch (RuntimeException ignored) {}
                                matchRef.closeUpgradePause("nuke_redesign");
                                shellRef.requestGameFocus();
                            } else {
                                pvpLastKnownDefcon = currentDefcon;
                            }
                        }
                        if (mabMatch.getWinner() != null && !shellRef.isResultVisible()) {
                            var summary = com.tetris.mab.ui.MabMatchResultSummary.from(
                                    mabMatch, ParticipantId.PLAYER_A);
                            String title = com.tetris.mab.ui.MabMatchResultFormatter
                                    .formatTitle(summary);
                            String body = com.tetris.mab.ui.MabMatchResultFormatter
                                    .formatBody(summary);
                            String cause = summary == null ? "" : "Cause :: " + summary.getReason();
                            shellRef.showResultOverlay(title, cause, body,
                                    mabRestartCallback, mabBackToMenuCallback);
                        }
                    }
                } catch (RuntimeException ignored) {}
            });
            mabEmbeddedRefreshTimer.setRepeats(true);
            mabEmbeddedRefreshTimer.start();
            return mabBattleShell;
        }
        if (launchMode == GameLaunchMode.MAB_PVE && mabPlayerHud != null
                && mabPlayerBState != null) {
            MabHudPanel embeddedHud = mabPlayerHud.getHudPanel();
            if (embeddedHud != null) {
                MabOpponentBoardPanel opponentPanel = new MabOpponentBoardPanel(mabPlayerBState);
                opponentPanel.setOpponentInfo(
                        mabPveConfig.getAiArchetype(),
                        mabPveConfig.getAiDifficulty(),
                        mabPveConfig.getBalanceProfile());
                if (mabBoardAiDriver != null) {
                    opponentPanel.setBoardAi(mabBoardAiDriver);
                }
                System.out.println("[MAB-PVE] opponent board panel created");

                // Step 23 \u2014 mount the cold-war battle shell instead
                // of the old seven-card dashboard layout. The shell
                // re-uses the existing GamePanel + NextPanel for the
                // player and MabOpponentBoardPanel for the opponent.
                String playerTitle = "STATION P1 :: COMMANDER";
                String oppTitle = "STATION AI :: "
                        + (mabPveConfig.getAiArchetype() == null
                                ? "AI"
                                : mabPveConfig.getAiArchetype().name());
                String modeLine = com.tetris.mab.ui.MabBattleShellPanel
                        .defaultModeLine(mabPveConfig.getAiDifficulty());
                String modeSub = com.tetris.mab.ui.MabBattleShellPanel.defaultModeSub();
                final Runnable backRef = mabBackToMenuCallback;
                mabBattleShell = new com.tetris.mab.ui.MabBattleShellPanel(
                        gameView.getGamePanel(),
                        gameState,
                        opponentPanel,
                        ParticipantId.PLAYER_A,
                        ParticipantId.PLAYER_B,
                        playerTitle, oppTitle,
                        modeLine, modeSub,
                        backRef);
                // Step 23 control refinement — install the global
                // key dispatcher so input is robust to focus changes
                // and held keys never get stuck.
                mabBattleShell.setInputHandler(inputHandler);
                System.out.println("[MAB-PVE] mounted root = MabBattleShellPanel (Step 23)");

                final com.tetris.mab.ui.MabBattleShellPanel shellRef = mabBattleShell;
                mabPlayerHud.setEmbeddedResultSink(summary -> {
                    String title = com.tetris.mab.ui.MabMatchResultFormatter.formatTitle(summary);
                    String body = com.tetris.mab.ui.MabMatchResultFormatter.formatBody(summary);
                    String cause = summary == null ? "" : "Cause :: " + summary.getReason();
                    Runnable restart = mabRestartCallback;
                    Runnable back = mabBackToMenuCallback;
                    shellRef.showResultOverlay(title, cause, body, restart, back);
                });
                final MabOpponentBoardPanel opponentRef = opponentPanel;
                // Refresh the entire battle shell ~10 Hz, auto-resolve
                // any IMPACT_READY launches so the live PvE actually
                // applies impact damage (Step 23 fix), then prune
                // completed threats so the warning clears.
                mabEmbeddedRefreshTimer = new Timer(100, e -> {
                    try {
                        if (mabMatch != null) {
                            if (!mabMatch.isPaused()) {
                                mabMatch.resolveAllImpactReady();
                                mabMatch.pruneCompletedThreats();
                            }
                            shellRef.refreshAll(mabMatch);
                            // Step 24 — level-up upgrade draft polling.
                            if (!mabMatch.isPaused()
                                    && !shellRef.isUpgradeOverlayVisible()
                                    && !shellRef.isResultVisible()) {
                                var draft = mabMatch.tickUpgradeDrafts();
                                if (draft != null) {
                                    final var matchRef = mabMatch;
                                    matchRef.openUpgradePause("upgrade_draft");
                                    var inv = matchRef
                                            .getParticipant(draft.getParticipantId())
                                            .getUpgradeInventory();
                                    shellRef.showUpgradeOverlay(draft, inv, card -> {
                                        matchRef.applyHumanUpgradeChoice(card);
                                        shellRef.hideUpgradeOverlay();
                                        matchRef.closeUpgradePause("upgrade_draft");
                                    });
                                }
                            }
                        }
                    } catch (RuntimeException ignored) {}
                });
                mabEmbeddedRefreshTimer.setRepeats(true);
                mabEmbeddedRefreshTimer.start();
                return mabBattleShell;
            } else {
                System.err.println("[MAB-PVE] WARNING embedded HUD null — falling back to bare GameView");
            }
        }
        return gameView;
    }

    /** Step 20 — focus the playable Tetris board (player A). */
    public void requestGameFocus() {
        if (mabBattleShell != null) {
            mabBattleShell.requestGameFocus();
        } else if (mabPveGamePanel != null) {
            mabPveGamePanel.requestGameFocus();
        } else if (gameView != null) {
            gameView.requestGameFocus();
        }
    }

    // ─────────────── Step 12 MAB debug HUD wiring ────────────

    /**
     * Step 15 — opens MAB-related windows according to the launch mode
     * and the {@code -Dmab.debug.hud} system property.
     *
     * <ul>
     *   <li>The match itself is created when either the player-facing
     *       PvE HUD or the developer debug HUD is requested. A single
     *       match instance backs both surfaces.</li>
     *   <li>{@link GameLaunchMode#MAB_PVE} always opens the player-facing
     *       HUD; the debug HUD is opt-in via
     *       {@code -Dmab.debug.hud=true}.</li>
     *   <li>{@link GameLaunchMode#NORMAL_TETRIS} opens nothing unless
     *       {@code -Dmab.debug.hud=true}, in which case only the debug
     *       HUD opens (legacy Step 12 behaviour, now opt-in).</li>
     * </ul>
     */
    private void openMabIntegrations() {
        boolean pve = launchMode == GameLaunchMode.MAB_PVE;
        boolean localPvp = launchMode == GameLaunchMode.MAB_LOCAL_PVP;
        boolean debugHudOptIn = (pve && mabPveConfig != null && mabPveConfig.isShowDebugHud())
                || "true".equalsIgnoreCase(System.getProperty("mab.debug.hud", "false"));
        if (!pve && !localPvp && !debugHudOptIn) return;
        try {
            if (mabMatch == null) {
                mabPlayerBState = new GameState(startLevel);
                // Step 21: shared deterministic 7-bag — both players consume
                // the SAME piece sequence in MAB modes (Player A's piece #n
                // == Player B's piece #n). Offline-only; seed is local.
                long mabSeed = System.nanoTime() ^ 0xC0FFEEL;
                if (pve) {
                    mabMatch = MutuallyAssuredBlocksMatch.createPveShared(
                            gameState, mabPlayerBState, MatchDifficulty.NORMAL, mabSeed);
                } else {
                    mabMatch = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                            gameState, mabPlayerBState, MatchDifficulty.NORMAL, mabSeed);
                }
                applyConfiguredMabNukeDesigns();
                mabMatch.startMatch();
            }
            if (pve && mabPlayerHud == null && mabPveConfig != null && mabPveConfig.isShowPlayerHud()) {
                mabPlayerHud = new MabPlayerFacingController(
                        mabMatch, ParticipantId.PLAYER_A,
                        mabPveConfig.getAiArchetype(),
                        mabPveConfig.getAiDifficulty(),
                        mabPveConfig.getBalanceProfile());
                mabPlayerHud.setRestartCallback(mabRestartCallback);
                mabPlayerHud.setBackToMenuCallback(mabBackToMenuCallback);
                final MabPlayerFacingController hud = mabPlayerHud;
                // Step 20: PvE now embeds the HUD into the main gameplay
                // window. We MUST initialise it synchronously here — the
                // embedded mount path below reads hud.getHudPanel() right
                // after this call, so a deferred startEmbedded() would
                // hand back null and silently fall through to a bare
                // GameView (the bug observed in the Step 20 playtest).
                boolean openCompanion = "true".equalsIgnoreCase(
                        System.getProperty("mab.pve.companionHud", "false"));
                hud.startEmbedded();
                System.out.println("[MAB-PVE] embedded HUD attached");
                if (openCompanion) {
                    SwingUtilities.invokeLater(hud::start);
                    System.out.println("[MAB-PVE] companion HUD opened");
                }
                // Step 20: visible board AI drives Player B; with real piece
                // locks now firing on Player B's GameState, the strategic AI
                // must NOT also debug-advance the hidden strategic clock,
                // otherwise the clock advances twice per conceptual turn.
                hud.getAiDriver().setAdvanceHiddenClock(false);
                if (mabBoardAiDriver == null && mabPlayerBState != null) {
                    mabBoardAiDriver = new MabBoardAiDriver(mabPlayerBState);
                    // Step 20 Third Refinement — difficulty drives PPS,
                    // pacing, and scoring weights all at once.
                    mabBoardAiDriver.setDifficulty(mabPveConfig.getAiDifficulty());
                    System.out.println("[MAB-PVE] board AI difficulty="
                            + mabPveConfig.getAiDifficulty()
                            + " targetPps="
                            + String.format("%.2f", mabBoardAiDriver.getTargetPps()));
                    final MabBoardAiDriver driverRef = mabBoardAiDriver;
                    final long[] lastLog = { 0L };
                    mabBoardAiTimer = new Timer(PHYSICS_INTERVAL_MS, e -> {
                        try {
                            driverRef.tick();
                            // Heartbeat ~ once per second so users can see
                            // the visible-board AI is actually ticking.
                            long t = driverRef.getTickCount();
                            if (t - lastLog[0] >= 60) {
                                lastLog[0] = t;
                                System.out.println("[MAB-PVE] board AI tick count = " + t);
                            }
                        } catch (RuntimeException ignored) {}
                    });
                    mabBoardAiTimer.setRepeats(true);
                    mabBoardAiTimer.start();
                }
            }
            if (debugHudOptIn && mabDebugFrame == null) {
                MabDebugController ctrl = new MabDebugController(mabMatch);
                mabDebugFrame = new MabDebugFrame(ctrl);
                SwingUtilities.invokeLater(mabDebugFrame::start);
            }
        } catch (RuntimeException ex) {
            // Never block normal gameplay if any MAB surface fails.
            System.err.println("[MAB] integration failed to start: " + ex.getMessage());
            closeMabIntegrations();
        }
    }

    private void applyConfiguredMabNukeDesigns() {
        if (mabMatch == null) return;
        int defconLevel = 5;
        try {
            if (mabMatch.getDefconState() != null) {
                defconLevel = mabMatch.getDefconState().getLevel();
            }
        } catch (RuntimeException ignored) {}
        try {
            if (launchMode == GameLaunchMode.MAB_LOCAL_PVP) {
                MabNukeDesignSelection p1 = mabLocalPvpConfig.getNukeDesignP1();
                MabNukeDesignSelection p2 = mabLocalPvpConfig.getNukeDesignP2();
                if (p1 != null && p1.getDesign() != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_A)
                            .getNukeBuildState()
                            .setDesign(p1.getDesign(), defconLevel);
                }
                if (p2 != null && p2.getDesign() != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_B)
                            .getNukeBuildState()
                            .setDesign(p2.getDesign(), defconLevel);
                }
            } else {
                MabNukeDesignSelection selection = mabPveConfig.getNukeDesignSelection();
                if (selection != null && selection.getDesign() != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_A)
                            .getNukeBuildState()
                            .setDesign(selection.getDesign(), defconLevel);
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("[MAB] nuke selection ignored: " + ex.getMessage());
        }
    }

    /** Shows a blocking modal nuke builder dialog and returns the chosen design. */
    private MabNukeDesignSelection showNukeBuilderModal(String title, java.awt.Window owner) {
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner, title,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        com.tetris.view.NukeBuilderDialog builder =
                com.tetris.view.NukeBuilderDialog.createEmbedded(null);
        MabNukeDesignSelection[] result = {MabNukeDesignSelection.defaultSelection()};

        javax.swing.JButton confirm = new javax.swing.JButton("USE DESIGN");
        javax.swing.JButton useDefault = new javax.swing.JButton("USE DEFAULT");
        confirm.addActionListener(ev -> {
            result[0] = MabNukeDesignSelection.fromBuilderDesign(
                    builder.getDesignForIntegration());
            dialog.dispose();
        });
        useDefault.addActionListener(ev -> {
            result[0] = MabNukeDesignSelection.defaultSelection();
            dialog.dispose();
        });

        javax.swing.JPanel btnRow = new javax.swing.JPanel();
        btnRow.add(confirm);
        btnRow.add(useDefault);

        javax.swing.JPanel content = new javax.swing.JPanel(new java.awt.BorderLayout());
        content.add(builder, java.awt.BorderLayout.CENTER);
        content.add(btnRow, java.awt.BorderLayout.SOUTH);

        // Hard-drop key (confirm) closes and accepts the current design.
        int hardDropCode = Settings.get().getKeyHardDrop();
        javax.swing.KeyStroke hardDropKs =
                javax.swing.KeyStroke.getKeyStroke(hardDropCode, 0);
        dialog.getRootPane()
                .getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(hardDropKs, "nukeConfirm");
        dialog.getRootPane().getActionMap().put("nukeConfirm",
                new javax.swing.AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                        confirm.doClick();
                    }
                });

        dialog.setContentPane(content);
        dialog.setSize(1200, 820);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    private void closeMabIntegrations() {
        if (mabEmbeddedRefreshTimer != null) {
            mabEmbeddedRefreshTimer.stop();
            mabEmbeddedRefreshTimer = null;
        }
        if (mabBoardAiTimer != null) {
            mabBoardAiTimer.stop();
            mabBoardAiTimer = null;
        }
        if (mabBoardAiDriver != null) {
            mabBoardAiDriver.setEnabled(false);
            mabBoardAiDriver = null;
        }
        if (localPvpInputAdapter != null) {
            try { localPvpInputAdapter.shutdown(); } catch (RuntimeException ignored) {}
            localPvpInputAdapter = null;
        }
        if (localPvpInputRouter != null) {
            try { localPvpInputRouter.releaseAll(); } catch (RuntimeException ignored) {}
            localPvpInputRouter = null;
        }
        mabPveGamePanel = null;
        if (mabBattleShell != null) {
            try { mabBattleShell.getOpsDeck().shutdown(); } catch (RuntimeException ignored) {}
            try {
                if (mabBattleShell.getInputAdapter() != null) {
                    mabBattleShell.getInputAdapter().shutdown();
                }
            } catch (RuntimeException ignored) {}
            mabBattleShell = null;
        }
        // Step 23 control refinement — always release any held keys
        // when an MAB integration tears down so a stuck direction
        // cannot leak across modes / restarts.
        if (inputHandler != null) inputHandler.releaseAll();
        if (mabPlayerHud != null) {
            try { mabPlayerHud.shutdown(); } catch (RuntimeException ignored) {}
            mabPlayerHud = null;
        }
        if (mabDebugFrame != null) {
            mabDebugFrame.shutdown();
            mabDebugFrame = null;
        }
        if (mabMatch != null) {
            try { mabMatch.shutdown(); } catch (RuntimeException ignored) {}
            mabMatch = null;
        }
        mabPlayerBState = null;
    }

    /** Exposes the main window so the launcher can listen for its close. */
    public MainFrame getMainFrame() {
        return mainFrame;
    }

    // ─────────────────────── Timer helpers ──────────────────────────

    private static int detectRenderIntervalMs() {
        try {
            int hz = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDisplayMode()
                    .getRefreshRate();
            if (hz != DisplayMode.REFRESH_RATE_UNKNOWN && hz > 0) {
                return Math.max(1, 1000 / hz);
            }
        } catch (RuntimeException ignored) {}
        return 1000 / 60; // fallback: 60 Hz
    }

    private void startTimers() {
        stopTimers(); // guard against double-start
        physicsTimer = new Timer(PHYSICS_INTERVAL_MS, e -> physicsTick());
        physicsTimer.setRepeats(true);
        physicsTimer.start();

        renderTimer = new Timer(renderIntervalMs, e -> renderFrame());
        renderTimer.setRepeats(true);
        renderTimer.start();
    }

    private void stopTimers() {
        if (physicsTimer != null) { physicsTimer.stop(); physicsTimer = null; }
        if (renderTimer  != null) { renderTimer.stop();  renderTimer  = null; }
    }

    // ─────────────────────── Game loop ───────────────────────────────

    /** Physics tick — runs at fixed 120 Hz. Updates game state only. */
    private void physicsTick() {
        gameState.update();
        if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && mabPlayerBState != null) {
            mabPlayerBState.update();
        }
    }

    /** Render frame — runs at display refresh rate. Samples input, applies IRS/IHS, repaints. */
    private void renderFrame() {
        // FPS tracking (rolling 1-second window)
        long now = System.currentTimeMillis();
        if (fpsBucketStart == 0) fpsBucketStart = now;
        if (now - fpsBucketStart >= 1000) {
            lastRenderFps    = fpsFrameCount;
            fpsFrameCount    = 0;
            fpsBucketStart   = now;
        }
        fpsFrameCount++;

        // 1. Process input (DAS/ARR for held keys)
        if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && localPvpInputRouter != null) {
            localPvpInputRouter.processInput();
        } else {
            inputHandler.processInput();
        }

        // 2. IRS/IHS: apply queued rotation/hold on freshly spawned pieces
        if (launchMode != GameLaunchMode.MAB_LOCAL_PVP && gameState.wasJustSpawned()) {
            Settings s = Settings.get();
            String irsMode = s.getIrsMode();
            String ihsMode = s.getIhsMode();

            // IRS (Initial Rotation System)
            if (!"off".equals(irsMode)) {
                boolean useTap = "tap".equals(irsMode);
                if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyRotateCW())
                           : inputHandler.isKeyHeld(s.getKeyRotateCW())) {
                    gameState.rotateCW();
                    inputHandler.consumeKey(s.getKeyRotateCW());
                } else if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyRotateCCW())
                                  : inputHandler.isKeyHeld(s.getKeyRotateCCW())) {
                    gameState.rotateCCW();
                    inputHandler.consumeKey(s.getKeyRotateCCW());
                }
            }

            // IHS (Initial Hold System)
            if (!"off".equals(ihsMode)) {
                boolean useTap = "tap".equals(ihsMode);
                if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyHold())
                           : inputHandler.isKeyHeld(s.getKeyHold())) {
                    gameState.hold();
                    inputHandler.consumeKey(s.getKeyHold());
                } else if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyHoldAlt())
                                  : inputHandler.isKeyHeld(s.getKeyHoldAlt())) {
                    gameState.hold();
                    inputHandler.consumeKey(s.getKeyHoldAlt());
                }
            }

            gameState.clearJustSpawned();
        }

        // 3. Repaint
        if (mainFrame != null) mainFrame.repaint();
        if (gameView  != null) gameView.repaint();
        if (mabBattleShell != null) mabBattleShell.repaint();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT ACTION METHODS
    // ═══════════════════════════════════════════════════════════════
    // Called by InputHandler when keys are pressed.
    // These simply delegate to GameState.

    public void moveLeft() {
        gameState.moveLeft();
    }

    public void moveRight() {
        gameState.moveRight();
    }

    public void softDrop() {
        gameState.softDrop();
    }

    public void hardDrop() {
        gameState.hardDrop();
    }

    public void rotateCW() {
        gameState.rotateCW();
    }

    public void rotateCCW() {
        gameState.rotateCCW();
    }

    public void hold() {
        gameState.hold();
    }

    public void togglePause() {
        if (launchMode != null && launchMode.isMabMode() && mabMatch != null) {
            toggleMabMatchPause("input");
        } else {
            gameState.togglePause();
        }
    }

    /**
     * Escape is intentionally mode-specific: in MAB PvE it pauses the
     * whole match; outside PvE it is ignored by gameplay.
     */
    public void handleEscapePause() {
        long now = System.currentTimeMillis();
        if (now - lastEscapePauseToggleMs < 100L) return;
        lastEscapePauseToggleMs = now;
        if (launchMode != null && launchMode.isMabMode() && mabMatch != null) {
            toggleMabMatchPause("escape");
        }
    }

    private void toggleMabMatchPause(String reason) {
        if (mabMatch.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE) return;
        if (mabMatch.isPaused()) {
            mabMatch.resumeMatch(reason);
        } else {
            mabMatch.pauseMatch(reason);
        }
    }

    /**
     * Forces exit from the current stage back to the menu.
     * In MAB modes uses the back-to-menu callback; in embedded normal Tetris
     * uses the exit action wired at startEmbedded() time.
     */
    public void exitStage() {
        if (launchMode != null && launchMode.isMabMode() && mabBackToMenuCallback != null) {
            Runnable r = mabBackToMenuCallback;
            if (inputHandler != null) inputHandler.releaseAll();
            if (localPvpInputRouter != null) localPvpInputRouter.releaseAll();
            SwingUtilities.invokeLater(r);
        } else if (exitStageCallback != null) {
            Runnable r = exitStageCallback;
            if (inputHandler != null) inputHandler.releaseAll();
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * Restarts the game with a fresh state.
     *
     * <p>Step 24 reset-key behaviour:
     * <ul>
     *   <li>{@code MAB_PVE} — pressing R now restarts the entire MAB
     *       match (both player and AI boards) by invoking the menu's
     *       {@code mabRestartCallback}. Falls back to the legacy
     *       single-board restart if no callback is wired (offline
     *       headless tests).</li>
     *   <li>All other modes (NORMAL_TETRIS / "PvP" single-player and
     *       any future local 1v1 PvP) — pressing R does nothing.
     *       Players asked for an explicit no-op so accidental R
     *       presses can't wipe their game.</li>
     * </ul>
     */
    public void restart() {
        if (launchMode != null && launchMode.isMabMode()) {
            if (mabRestartCallback != null) {
                Runnable r = mabRestartCallback;
                if (inputHandler != null) inputHandler.releaseAll();
                if (localPvpInputRouter != null) localPvpInputRouter.releaseAll();
                javax.swing.SwingUtilities.invokeLater(r);
            } else {
                // Headless / no menu wired — fall back so tests still work.
                gameState = new GameState(startLevel);
                if (mainFrame != null) mainFrame.setGameState(gameState);
                if (gameView  != null) gameView.setGameState(gameState);
            }
            return;
        }
        // NORMAL_TETRIS / debug : standard board reset.
        gameState = new GameState(startLevel);
        if (mainFrame != null) mainFrame.setGameState(gameState);
        if (gameView  != null) gameView.setGameState(gameState);
    }

    /**
     * Opens the settings dialog. Pauses the game while the dialog is open.
     * After the dialog closes, focus returns to the game panel.
     */
    public void openSettings() {
        boolean wasPaused = gameState.isPaused();
        if (!wasPaused && !gameState.isGameOver()) {
            gameState.togglePause();
        }
        java.awt.Window owner = null;
        if (mainFrame != null) owner = mainFrame;
        else if (gameView != null) owner = javax.swing.SwingUtilities.getWindowAncestor(gameView);
        SettingsPanel.showDialog(owner instanceof javax.swing.JFrame f ? f : null);
        if (mainFrame != null) mainFrame.requestGameFocus();
        if (gameView  != null) gameView.requestGameFocus();
        if (!wasPaused && !gameState.isGameOver()) {
            gameState.togglePause();
        }
    }

    /**
     * Returns the current gravity interval in milliseconds.
     * Used by InputHandler to calculate SDF-based soft drop speed.
     */
    public int getGravityInterval() {
        return gameState.getScoreSystem().getGravityInterval();
    }

    // ─────────────────────── Dev console ─────────────────────────────────

    /** Toggle the dev console open/closed. Called by InputHandler on ` / ~ key. */
    public void toggleDevConsole() {
        if (devConsole == null) {
            devConsole = new DevConsolePanel(this::handleConsoleCommand);
        }
        // Mount into the root pane's layered pane if not already there.
        java.awt.Window win = resolveWindow();
        if (win != null) {
            javax.swing.RootPaneContainer rpc = (win instanceof javax.swing.RootPaneContainer r) ? r : null;
            if (rpc != null) {
                JLayeredPane lp = rpc.getRootPane().getLayeredPane();
                if (devConsole.getParent() != lp) {
                    lp.add(devConsole, JLayeredPane.POPUP_LAYER);
                }
                // Position: bottom 40% of the window.
                int w = lp.getWidth();
                int h = lp.getHeight();
                int consH = Math.max(240, h * 2 / 5);
                devConsole.setBounds(0, h - consH, w, consH);
                devConsole.revalidate();
            }
        }
        devConsole.toggle();
    }

    private java.awt.Window resolveWindow() {
        if (mainFrame != null) return mainFrame;
        if (gameView  != null) return SwingUtilities.getWindowAncestor(gameView);
        if (mabBattleShell != null) return SwingUtilities.getWindowAncestor(mabBattleShell);
        return null;
    }

    private String handleConsoleCommand(String cmd) {
        return switch (cmd.toLowerCase().trim()) {
            case "debug" -> buildDebugString();
            default -> "unknown command: " + cmd + "  (type 'help' for commands)";
        };
    }

    private String buildDebugString() {
        Runtime rt = Runtime.getRuntime();
        long heapUsed  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapTotal = rt.totalMemory() / (1024 * 1024);
        long heapMax   = rt.maxMemory()   / (1024 * 1024);

        com.tetris.model.ScoreSystem sc = gameState.getScoreSystem();

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append("║                  DEBUG STATS                    ║\n");
        sb.append("╠══════════════════════════════════════════════════╣\n");

        sb.append("║ [TIMING]                                         \n");
        sb.append(String.format("║   Physics timer   : 120 Hz  (8 ms fixed)%n"));
        sb.append(String.format("║   Render interval : %d ms  (detected %d Hz)%n",
                renderIntervalMs, 1000 / Math.max(1, renderIntervalMs)));
        sb.append(String.format("║   Render FPS      : %d fps  (last second)%n", lastRenderFps));

        sb.append("║ [GAME STATE]                                     \n");
        sb.append(String.format("║   Level           : %d%n", sc.getLevel()));
        sb.append(String.format("║   Score           : %,d%n", sc.getScore()));
        sb.append(String.format("║   Lines cleared   : %d%n", sc.getTotalLinesCleared()));
        sb.append(String.format("║   Gravity interval: %d ms/row%n", getGravityInterval()));
        sb.append(String.format("║   Paused          : %b%n", gameState.isPaused()));
        sb.append(String.format("║   Game over       : %b%n", gameState.isGameOver()));

        sb.append("║ [MAB STATE]                                      \n");
        if (mabMatch != null) {
            sb.append(String.format("║   Mode            : MAB PvE (%s)%n", launchMode));
            sb.append(String.format("║   Match paused    : %b%n", mabMatch.isPaused()));
        } else {
            sb.append(String.format("║   Mode            : %s%n", launchMode));
        }

        sb.append("║ [MEMORY]                                         \n");
        sb.append(String.format("║   Heap used       : %d MB%n", heapUsed));
        sb.append(String.format("║   Heap total      : %d MB%n", heapTotal));
        sb.append(String.format("║   Heap max        : %d MB%n", heapMax));
        sb.append("╚══════════════════════════════════════════════════╝");
        return sb.toString();
    }
}
