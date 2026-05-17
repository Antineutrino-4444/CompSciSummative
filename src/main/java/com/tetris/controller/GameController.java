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
import com.tetris.view.PerfOverlayPanel;

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

    // ─────────────── F3 performance overlay ───────────────────────
    private PerfOverlayPanel perfOverlay;

    // ─────────────── Cheat menu ───────────────────────────────────
    private com.tetris.view.CheatMenuPanel cheatMenu;

    // ─────────────── Global debug dispatcher (embedded mode) ──────
    private java.awt.KeyEventDispatcher globalDebugDispatcher;
    private final StringBuilder consoleHwBuf = new StringBuilder();
    private static final String CONSOLE_HOTWORD = "debug";

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
        if (globalDebugDispatcher != null) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(globalDebugDispatcher);
            globalDebugDispatcher = null;
        }
        if (cheatMenu != null) {
            cheatMenu.setVisible(false);
            cheatMenu = null;
        }
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

        // Install global dispatcher first (FIFO — fires before any MAB adapter
        // added later) so F3, backtick, and the "debug" hotword work in all modes.
        globalDebugDispatcher = e -> {
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;
            int code = e.getKeyCode();
            if (code == java.awt.event.KeyEvent.VK_F3) {
                togglePerfOverlay();
                return true;
            }
            if (code == java.awt.event.KeyEvent.VK_BACK_QUOTE) {
                consoleHwBuf.setLength(0);
                toggleDevConsole();
                return true;
            }
            if (isDevConsoleOpen()) {
                if (code == java.awt.event.KeyEvent.VK_ENTER) {
                    devConsole.submitCurrentInput();
                    return true;
                }
                if (devConsole != null) {
                    SwingUtilities.invokeLater(devConsole::focusInput);
                }
            }
            // Hotword tracking — returns false so game controls still receive letters.
            if (code >= java.awt.event.KeyEvent.VK_A
                    && code <= java.awt.event.KeyEvent.VK_Z
                    && !isDevConsoleOpen()) {
                char c = (char) ('a' + (code - java.awt.event.KeyEvent.VK_A));
                consoleHwBuf.append(c);
                if (consoleHwBuf.length() > CONSOLE_HOTWORD.length())
                    consoleHwBuf.deleteCharAt(0);
                if (consoleHwBuf.toString().equals(CONSOLE_HOTWORD)) {
                    consoleHwBuf.setLength(0);
                    toggleDevConsole();
                }
            } else if (code < java.awt.event.KeyEvent.VK_A
                    || code > java.awt.event.KeyEvent.VK_Z) {
                consoleHwBuf.setLength(0);
            }
            return false;
        };
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(globalDebugDispatcher);

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
                        if (mabBackToMenuCallback != null) {
                            SwingUtilities.invokeLater(mabBackToMenuCallback);
                        }
                    },
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
                        boolean devConsoleOpen = isDevConsoleOpen();
                        if (!devConsoleOpen && !mabMatch.isPaused()) {
                            mabMatch.resolveAllImpactReady();
                            mabMatch.pruneCompletedThreats();
                        }
                        shellRef.refreshAll(mabMatch);
                        if (!devConsoleOpen
                                && !mabMatch.isPaused()
                                && !shellRef.isUpgradeOverlayVisible()
                                && !shellRef.isResultVisible()) {
                            var draft = mabMatch.tickUpgradeDrafts();
                            if (draft != null) {
                                final var matchRef = mabMatch;
                                matchRef.openUpgradePause("upgrade_draft");
                                var inv = matchRef
                                        .getParticipant(draft.getParticipantId())
                                        .getUpgradeInventory();
                                final com.tetris.mab.ParticipantId draftOwner = draft.getParticipantId();
                                shellRef.showUpgradeOverlay(draft, inv, card -> {
                                    if (card.hasTag("redesign_nuke")) {
                                        java.awt.Window owner =
                                                javax.swing.SwingUtilities.getWindowAncestor(shellRef);
                                        String modalTitle = draftOwner == ParticipantId.PLAYER_A
                                                ? "PLAYER 1 — REDESIGN YOUR NUKE"
                                                : "PLAYER 2 — REDESIGN YOUR NUKE";
                                        MabNukeDesignSelection newDesign = showNukeBuilderModal(modalTitle, owner);
                                        int defcon = matchRef.getDefconState() != null
                                                ? matchRef.getDefconState().getLevel() : 5;
                                        try {
                                            matchRef.getParticipant(draftOwner)
                                                    .getNukeBuildState()
                                                    .redesign(newDesign.getDesign(), defcon, 0.5);
                                        } catch (RuntimeException ignored2) {}
                                    }
                                    matchRef.applyHumanUpgradeChoice(card);
                                    shellRef.hideUpgradeOverlay();
                                    matchRef.closeUpgradePause("upgrade_draft");
                                    shellRef.requestGameFocus();
                                });
                            }
                            // Detect DEFCON escalation → both players redesign their nuke.
                            int currentDefcon = mabMatch.getDefconState() != null
                                    ? mabMatch.getDefconState().getLevel() : pvpLastKnownDefcon;
                            if (currentDefcon < pvpLastKnownDefcon) {
                                pvpLastKnownDefcon = currentDefcon;
                                final var matchRef = mabMatch;
                                matchRef.openUpgradePause("nuke_redesign");
                                // Reset redesign card availability for next DEFCON level.
                                resetNukeRedesignCardAvailability(matchRef);
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
                            boolean devConsoleOpen = isDevConsoleOpen();
                            if (!devConsoleOpen && !mabMatch.isPaused()) {
                                mabMatch.resolveAllImpactReady();
                                mabMatch.pruneCompletedThreats();
                            }
                            shellRef.refreshAll(mabMatch);
                            if (!devConsoleOpen
                                    && !mabMatch.isPaused()
                                    && !shellRef.isUpgradeOverlayVisible()
                                    && !shellRef.isResultVisible()) {
                                var draft = mabMatch.tickUpgradeDrafts();
                                if (draft != null) {
                                    final var matchRef = mabMatch;
                                    matchRef.openUpgradePause("upgrade_draft");
                                    var inv = matchRef
                                            .getParticipant(draft.getParticipantId())
                                            .getUpgradeInventory();
                                    final com.tetris.mab.ParticipantId draftOwner = draft.getParticipantId();
                                    shellRef.showUpgradeOverlay(draft, inv, card -> {
                                        if (card.hasTag("redesign_nuke")) {
                                            java.awt.Window owner =
                                                    javax.swing.SwingUtilities.getWindowAncestor(shellRef);
                                            MabNukeDesignSelection newDesign = showNukeBuilderModal(
                                                    "PLAYER 1 — REDESIGN YOUR NUKE", owner);
                                            int defcon = matchRef.getDefconState() != null
                                                    ? matchRef.getDefconState().getLevel() : 5;
                                            try {
                                                matchRef.getParticipant(draftOwner)
                                                        .getNukeBuildState()
                                                        .redesign(newDesign.getDesign(), defcon, 0.5);
                                            } catch (RuntimeException ignored2) {}
                                        }
                                        matchRef.applyHumanUpgradeChoice(card);
                                        shellRef.hideUpgradeOverlay();
                                        matchRef.closeUpgradePause("upgrade_draft");
                                        shellRef.requestGameFocus();
                                    });
                                }
                                // Detect DEFCON escalation → player 1 redesigns their nuke.
                                int currentDefcon = mabMatch.getDefconState() != null
                                        ? mabMatch.getDefconState().getLevel() : pvpLastKnownDefcon;
                                if (currentDefcon < pvpLastKnownDefcon) {
                                    pvpLastKnownDefcon = currentDefcon;
                                    final var matchRef = mabMatch;
                                    matchRef.openUpgradePause("nuke_redesign");
                                    resetNukeRedesignCardAvailability(matchRef);
                                    java.awt.Window owner =
                                            javax.swing.SwingUtilities.getWindowAncestor(shellRef);
                                    MabNukeDesignSelection p1 = showNukeBuilderModal(
                                            "PLAYER 1 — REDESIGN NUKE (DEFCON " + currentDefcon + ")",
                                            owner);
                                    int defcon = currentDefcon;
                                    try {
                                        matchRef.getParticipant(ParticipantId.PLAYER_A)
                                                .getNukeBuildState()
                                                .redesign(p1.getDesign(), defcon, 0.5);
                                    } catch (RuntimeException ignored) {}
                                    matchRef.closeUpgradePause("nuke_redesign");
                                    shellRef.requestGameFocus();
                                } else {
                                    pvpLastKnownDefcon = currentDefcon;
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
                            if (isDevConsoleOpen()) return;
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

    /** Removes the redesign_nuke card from both participants' inventories so
     *  it can be offered again at the new DEFCON level. */
    private void resetNukeRedesignCardAvailability(MutuallyAssuredBlocksMatch match) {
        if (match == null) return;
        try {
            var invA = match.getParticipant(ParticipantId.PLAYER_A).getUpgradeInventory();
            if (invA != null) invA.removeUpgrade("redesign_nuke");
            var invB = match.getParticipant(ParticipantId.PLAYER_B).getUpgradeInventory();
            if (invB != null) invB.removeUpgrade("redesign_nuke");
        } catch (RuntimeException ignored) {}
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
        if (isDevConsoleOpen()) return;
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

        // 1. Process input (DAS/ARR for held keys).
        // Skip entirely when a keyboard-modal overlay is visible: processInput() calls
        // drainPendingKeyEvents() which removes events from the AWT queue before they
        // reach the KeyEventDispatcher chain, so the overlay's dispatcher never sees
        // them and navigation appears broken.  The overlay has its own dispatcher that
        // handles input correctly when processInput() is not draining the queue.
        boolean modalOverlayUp = mabBattleShell != null
                && mabBattleShell.isKeyboardModalOverlayVisible();
        boolean devConsoleOpen = isDevConsoleOpen();
        if (!modalOverlayUp && !devConsoleOpen) {
            if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && localPvpInputRouter != null) {
                localPvpInputRouter.processInput();
            } else {
                inputHandler.processInput();
            }
        }

        // 2. IRS/IHS: apply queued rotation/hold on freshly spawned pieces
        if (!devConsoleOpen && launchMode != GameLaunchMode.MAB_LOCAL_PVP
                && gameState.wasJustSpawned()) {
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
        if (perfOverlay != null && perfOverlay.isVisible()) {
            perfOverlay.update(buildPerfString());
        }
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
     * <p>Reset-key behavior:
     * <ul>
     *   <li>MAB modes restart the entire match by invoking the menu's
     *       {@code mabRestartCallback}. Falls back to a single-board
     *       reset when no callback is wired.</li>
     *   <li>Normal Tetris resets the single board in place.</li>
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

    // ─────────────────────── Dev console & perf overlay ──────────────────

    public boolean isDevConsoleOpen() {
        return devConsole != null && devConsole.isOpen();
    }

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
        boolean opening = !devConsole.isOpen();
        if (opening) releaseGameplayInputs();
        devConsole.toggle();
    }

    private void releaseGameplayInputs() {
        if (inputHandler != null) inputHandler.releaseAll();
        if (localPvpInputRouter != null) localPvpInputRouter.releaseAll();
    }

    /** Toggle the F3 performance overlay. Called by InputHandler on F3 key. */
    public void togglePerfOverlay() {
        if (perfOverlay == null) {
            perfOverlay = new PerfOverlayPanel();
        }
        java.awt.Window win = resolveWindow();
        if (win != null) {
            javax.swing.RootPaneContainer rpc = (win instanceof javax.swing.RootPaneContainer r) ? r : null;
            if (rpc != null) {
                JLayeredPane lp = rpc.getRootPane().getLayeredPane();
                if (perfOverlay.getParent() != lp) {
                    lp.add(perfOverlay, JLayeredPane.DRAG_LAYER);
                }
                // Fill the full layered pane so paintComponent can position the block.
                perfOverlay.setBounds(0, 0, lp.getWidth(), lp.getHeight());
                perfOverlay.revalidate();
            }
        }
        boolean nowVisible = !perfOverlay.isVisible();
        perfOverlay.setVisible(nowVisible);
        if (nowVisible) perfOverlay.update(buildPerfString());
    }

    private String buildPerfString() {
        Runtime rt = Runtime.getRuntime();
        long heapUsed  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapTotal = rt.totalMemory() / (1024 * 1024);
        long heapMax   = rt.maxMemory()   / (1024 * 1024);
        com.tetris.model.ScoreSystem sc = gameState.getScoreSystem();

        String modeStr = (mabMatch != null)
                ? launchMode + "  match-paused=" + mabMatch.isPaused()
                : launchMode.toString();

        return "§h F3  PERFORMANCE\n"
             + String.format(" FPS      %d  (target %d Hz)", lastRenderFps, 1000 / Math.max(1, renderIntervalMs)) + "\n"
             + String.format(" Frame    %d ms  |  Physics 120 Hz", renderIntervalMs) + "\n"
             + "§h\n"
             + String.format(" Level    %d  |  Lines %d", sc.getLevel(), sc.getTotalLinesCleared()) + "\n"
             + String.format(" Score    %,d", sc.getScore()) + "\n"
             + String.format(" Gravity  %d ms/row  |  Paused %b", getGravityInterval(), gameState.isPaused()) + "\n"
             + "§h\n"
             + String.format(" Heap     %d / %d MB  (max %d MB)", heapUsed, heapTotal, heapMax) + "\n"
             + String.format(" Mode     %s", modeStr);
    }

    private java.awt.Window resolveWindow() {
        if (mainFrame != null) return mainFrame;
        if (gameView != null) {
            java.awt.Window w = SwingUtilities.getWindowAncestor(gameView);
            if (w != null) return w;
        }
        if (mabBattleShell != null) {
            java.awt.Window w = SwingUtilities.getWindowAncestor(mabBattleShell);
            if (w != null) return w;
        }
        return null;
    }

    private String handleConsoleCommand(String raw) {
        String[] parts = raw.trim().split("\\s+", 2);
        String verb = parts[0].toLowerCase();
        String arg  = parts.length > 1 ? parts[1].trim() : "";
        return switch (verb) {
            case "fps"      -> lastRenderFps + " fps  (render interval " + renderIntervalMs + " ms)";
            case "gc"       -> { Runtime.getRuntime().gc(); yield "GC requested."; }
            case "debug"    -> {
                gameState.setGravityFrozen(true);
                SwingUtilities.invokeLater(this::toggleCheatMenu);
                yield "Gravity frozen. Cheat menu opened.";
            }
            case "freeze"   -> { gameState.setGravityFrozen(true);  yield "Gravity frozen."; }
            case "unfreeze" -> { gameState.setGravityFrozen(false); yield "Gravity unfrozen."; }
            case "state"    -> mabMatch == null ? "Not in a MAB match." : consoleMabState();
            case "charge"   -> consoleCharge(arg);
            case "launch"   -> consoleLaunch(arg);
            case "override" -> consoleActiveDoctrine("override");
            case "emp"      -> consoleActiveDoctrine("emp");
            case "threat"   -> consoleThreat();
            case "resolve"  -> consoleResolve();
            case "points"   -> consolePoints(arg);
            case "clock"    -> consoleClock(arg);
            default         -> "unknown command: " + raw + "  (type 'help' for commands)";
        };
    }

    private boolean requireMab(StringBuilder err) {
        if (mabMatch == null) { err.append("Not in a MAB match."); return false; }
        return true;
    }

    private String consoleMabState() {
        com.tetris.mab.ParticipantState p =
                mabMatch.getParticipant(com.tetris.mab.ParticipantId.PLAYER_A);
        if (p == null) return "P1 state unavailable.";
        com.tetris.mab.NukeBuildState nb = p.getNukeBuildState();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("── P1 CHARGE ──%n  %d / %d  armed=%b%n",
                nb.getCurrentBuildCharge(),
                nb.getEffectiveBuildChargeRequired(),
                nb.isArmed()));
        sb.append(String.format("── P1 UPGRADE POINTS ──%n  %d%n",
                p.getUpgradeState().getUpgradePoints()));
        sb.append("── DOCTRINES ──\n");
        for (var opt : mabMatch.getActiveDoctrineOptions(com.tetris.mab.ParticipantId.PLAYER_A)) {
            sb.append(String.format("  %-16s owned=%-5b available=%-5b  %s%n",
                    opt.type().displayName(), opt.owned(), opt.available(), opt.reason()));
        }
        int inc = mabMatch.countLiveIncomingThreats(com.tetris.mab.ParticipantId.PLAYER_A);
        int imp = mabMatch.countImpactReadyThreats(com.tetris.mab.ParticipantId.PLAYER_A);
        sb.append(String.format("── THREATS ──%n  live=%d  impact-ready=%d%n", inc, imp));
        com.tetris.mab.DefconState ds = mabMatch.getDefconState();
        if (ds != null)
            sb.append(String.format("── DEFCON ──%n  level=%d  progress=%.0f%%%n",
                    ds.getLevel(), ds.getProgressToNextThreshold() * 100));
        return sb.toString().trim();
    }

    private String consoleCharge(String arg) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        if (arg.equalsIgnoreCase("full") || arg.isEmpty()) {
            boolean ok = mabMatch.debugArmCurrentNuke(com.tetris.mab.ParticipantId.PLAYER_A);
            return ok ? "P1 nuke charge filled to armed threshold." : "Failed (wrong phase?).";
        }
        try {
            int n = Integer.parseInt(arg);
            boolean ok = mabMatch.debugAddNukeCharge(com.tetris.mab.ParticipantId.PLAYER_A, n);
            return ok ? "Added " + n + " charge to P1." : "Failed (wrong phase or amount ≤ 0).";
        } catch (NumberFormatException e) {
            return "Usage: charge full  |  charge <amount>";
        }
    }

    private String consoleLaunch(String arg) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        if (arg.equalsIgnoreCase("override") || arg.equalsIgnoreCase("mo")) {
            return consoleActiveDoctrine("override");
        }
        boolean ok = mabMatch.debugStartLaunch(com.tetris.mab.ParticipantId.PLAYER_A);
        return ok ? "P1 launch started (normal)." : "Launch failed (wrong phase?).";
    }

    private String consoleActiveDoctrine(String type) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        com.tetris.mab.upgrade.draft.MabActiveDoctrineType dt =
                type.equalsIgnoreCase("emp")
                        ? com.tetris.mab.upgrade.draft.MabActiveDoctrineType.EMP
                        : com.tetris.mab.upgrade.draft.MabActiveDoctrineType.MANUAL_OVERRIDE;
        com.tetris.mab.upgrade.draft.MabActiveDoctrineUseResult r =
                mabMatch.useActiveDoctrine(com.tetris.mab.ParticipantId.PLAYER_A, dt);
        return r.title() + " :: " + r.detail();
    }

    private String consoleThreat() {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        String id = mabMatch.debugInjectIncomingThreatForTesting(com.tetris.mab.ParticipantId.PLAYER_A);
        return id == null ? "Threat injection failed (wrong phase?)." : "Threat injected: " + id;
    }

    private String consoleResolve() {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        java.util.List<?> results = mabMatch.debugResolveAllImpacts();
        return results.isEmpty() ? "No impact-ready threats." : "Resolved " + results.size() + " impact(s).";
    }

    private String consolePoints(String arg) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        int n = 100;
        if (!arg.isEmpty()) {
            try { n = Integer.parseInt(arg); }
            catch (NumberFormatException e) { return "Usage: points [amount]  (default 100)"; }
        }
        boolean ok = mabMatch.debugAddUpgradePoints(com.tetris.mab.ParticipantId.PLAYER_A, n);
        return ok ? "Added " + n + " upgrade points to P1." : "Failed.";
    }

    private String consoleClock(String arg) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        int n = 10;
        if (!arg.isEmpty()) {
            try { n = Integer.parseInt(arg); }
            catch (NumberFormatException e) { return "Usage: clock [pieces]  (default 10)"; }
        }
        int advanced = mabMatch.debugAdvanceStrategicClockOnly(com.tetris.mab.ParticipantId.PLAYER_A, n);
        return "Advanced strategic clock by " + advanced + " piece(s) for P1.";
    }

    private void toggleCheatMenu() {
        if (cheatMenu != null && cheatMenu.isVisible()) {
            cheatMenu.setVisible(false);
            return;
        }
        java.awt.Window win = resolveWindow();
        if (win == null) return;
        javax.swing.RootPaneContainer rpc =
                (win instanceof javax.swing.RootPaneContainer r) ? r : null;
        if (rpc == null) return;
        JLayeredPane lp = rpc.getRootPane().getLayeredPane();

        if (cheatMenu == null) {
            java.util.List<com.tetris.mab.upgrade.draft.MabUpgradeCard> cards = null;
            java.util.function.Consumer<com.tetris.mab.upgrade.draft.MabUpgradeCard> applyFn =
                    card -> {};
            if (mabMatch != null) {
                try {
                    cards = new com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry()
                            .getAllCards();
                    final var inv = mabMatch.getParticipant(ParticipantId.PLAYER_A)
                            .getUpgradeInventory();
                    applyFn = inv::addUpgrade;
                } catch (RuntimeException ignored) {}
            }
            final var cardsFinal = cards;
            final var applyFinal = applyFn;
            cheatMenu = new com.tetris.view.CheatMenuPanel(
                    () -> gameState.isGravityFrozen(),
                    frozen -> gameState.setGravityFrozen(frozen),
                    cardsFinal,
                    applyFinal,
                    () -> { if (cheatMenu != null) cheatMenu.setVisible(false); });
        }
        if (cheatMenu.getParent() != lp) {
            lp.add(cheatMenu, JLayeredPane.POPUP_LAYER);
        }
        int cw = Math.min(lp.getWidth() - 40, 860);
        int ch = Math.min(lp.getHeight() - 40, 520);
        cheatMenu.setBounds((lp.getWidth() - cw) / 2, 20, cw, ch);
        cheatMenu.revalidate();
        cheatMenu.setVisible(true);
    }

}
