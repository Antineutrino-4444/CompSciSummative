package com.tetris.controller;

import com.tetris.audio.MusicDirector;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchMode;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDesignPicker;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.debugui.MabDebugController;
import com.tetris.mab.debugui.MabDebugFrame;
import com.tetris.mab.ui.MabHudPanel;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabLocalPvpInputAdapter;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.mab.ui.MabPlayerFacingController;
import com.tetris.mab.ui.MabAiVsAiConfig;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongConsumer;

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
    private static final int PHYSICS_INTERVAL_MS = FrameRate.PHYSICS_INTERVAL_MS_ROUNDED;
    private static final int AI_FRAME_INTERVAL_MS = FrameRate.RENDER_INTERVAL_MS_ROUNDED;
    private static final int LIVE_AI_SEARCH_BUDGET_MS = 4;

    /** Render interval (ms) matched to the primary display refresh rate. */
    private final int renderIntervalMs;

    // ─────────────────────── Components ─────────────────────────

    private GameState gameState;
    private MainFrame mainFrame;
    private GameView gameView; // populated when started in embedded mode
    private InputHandler inputHandler;
    private FixedRateEdtLoop physicsLoop;
    private FixedRateEdtLoop renderLoop;

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
    private long   fpsBucketStartNs = -1L;
    private int    fpsFrameCount   = 0;
    private int    lastRenderFps   = 0;
    private final CopyOnWriteArrayList<LongConsumer> renderFrameProbeListeners =
            new CopyOnWriteArrayList<>();

    // Step 12: visible MAB debug HUD (vertical slice). Opt-in via
    // -Dmab.debug.hud=true (default off as of Step 15).
    private MutuallyAssuredBlocksMatch mabMatch;
    private GameState mabPlayerBState; // visible Player B board (Step 20)
    private MabDebugController mabDebugController;
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
    /** True while the automatic DEFCON redesign countdown/review is running. */
    private boolean mabDefconRedesignFlowActive;

    /** Starting level (remembered for restarts). */
    private final int startLevel;

    /** Step 15: which experience this controller drives. */
    private final GameLaunchMode launchMode;

    /** Step 18 — PvE configuration when launchMode == MAB_PVE; defaults otherwise. */
    private final MabPveConfig mabPveConfig;
    private final MabLocalPvpConfig mabLocalPvpConfig;
    /** Offline AI-vs-AI configuration when launchMode == MAB_AI_VS_AI. */
    private final MabAiVsAiConfig mabAiVsAiConfig;
    /** Second board driver for AI-vs-AI (Player A). */
    private MabBoardAiDriver mabBoardAiDriverA;
    /** Optional strategic drivers for the AI-vs-AI watcher. */
    private com.tetris.mab.ai.MabAiDriver mabStrategicAiA;
    private com.tetris.mab.ai.MabAiDriver mabStrategicAiB;
    private Timer mabBoardAiTimerA;
    private Timer mabAiVsAiRefreshTimer;
    private final MusicDirector musicDirector = MusicDirector.shared();
    private boolean mabMusicLaunchActive;
    private boolean mabMusicHighStackActive;
    private boolean mabMusicResultPlayed;

    /** Set by the dev-console {@code ai pause} command. When true, AI
     *  tick sites skip their work while match bookkeeping (impact
     *  resolution, threat pruning) continues. */
    private boolean aiPausedByConsole = false;

    /** Step 18 — callbacks the player-facing result dialog can invoke. */
    private Runnable mabRestartCallback;
    private Runnable mabBackToMenuCallback;
    private Runnable mabBackToSetupCallback;
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
        this(startLevel, launchMode, null, null, null);
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
                null,
                null);
    }

    /** Step 26 - creates a configured same-keyboard, offline local PvP controller. */
    public GameController(MabLocalPvpConfig config) {
        this((config == null ? 1 : config.getStartLevel()),
                GameLaunchMode.MAB_LOCAL_PVP,
                null,
                (config == null ? MabLocalPvpConfig.defaults() : config),
                null);
    }

    /** Creates a configured AI-vs-AI watcher controller. */
    public GameController(MabAiVsAiConfig config) {
        this((config == null ? 1 : config.getStartLevel()),
                GameLaunchMode.MAB_AI_VS_AI,
                null,
                null,
                (config == null ? MabAiVsAiConfig.defaults() : config));
    }

    /** Step 18 — full constructor; {@code config} may be null for non-PvE modes. */
    public GameController(int startLevel, GameLaunchMode launchMode, MabPveConfig config) {
        this(startLevel, launchMode, config, null, null);
    }

    private GameController(int startLevel, GameLaunchMode launchMode,
                           MabPveConfig config,
                           MabLocalPvpConfig localPvpConfig,
                           MabAiVsAiConfig aiVsAiConfig) {
        this.startLevel = Math.max(1, startLevel);
        this.launchMode = (launchMode == null) ? GameLaunchMode.NORMAL_TETRIS : launchMode;
        this.mabPveConfig = config == null ? MabPveConfig.defaults() : config;
        this.mabLocalPvpConfig = localPvpConfig == null
                ? MabLocalPvpConfig.defaults()
                : localPvpConfig;
        this.mabAiVsAiConfig = aiVsAiConfig == null
                ? MabAiVsAiConfig.defaults()
                : aiVsAiConfig;
        this.gameState = new GameState(this.startLevel);
        this.inputHandler = new InputHandler(this);
        this.renderIntervalMs = FrameRate.RENDER_INTERVAL_MS_ROUNDED;
    }

    public GameLaunchMode getLaunchMode() { return launchMode; }

    /** Step 18 — returns the PvE configuration used by this controller. */
    public MabPveConfig getMabPveConfig() { return mabPveConfig; }

    /** Step 26 - returns the local PvP configuration used by this controller. */
    public MabLocalPvpConfig getMabLocalPvpConfig() { return mabLocalPvpConfig; }

    /** Returns the AI-vs-AI configuration used by this controller. */
    public MabAiVsAiConfig getMabAiVsAiConfig() { return mabAiVsAiConfig; }

    /** Step 18 — wires callbacks that the post-match result dialog will invoke. */
    public void setMabPveCallbacks(Runnable onRestart, Runnable onBackToMenu) {
        setMabPveCallbacks(onRestart, onBackToMenu, onBackToMenu);
    }

    public void setMabPveCallbacks(Runnable onRestart, Runnable onBackToMenu,
                                   Runnable onBackToSetup) {
        this.mabRestartCallback = onRestart;
        this.mabBackToMenuCallback = onBackToMenu;
        this.mabBackToSetupCallback = onBackToSetup;
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
                                            handleRedesignUpgradeChoice(matchRef, shellRef,
                                                    draftOwner, card);
                                            return;
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
                                int previousDefcon = pvpLastKnownDefcon;
                                pvpLastKnownDefcon = currentDefcon;
                                beginDefconRedesignFlow(mabMatch, shellRef,
                                        previousDefcon, currentDefcon, true);
                            } else if (!mabDefconRedesignFlowActive) {
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
                                    mabRestartCallback, mabBackToSetupCallback,
                                    mabBackToMenuCallback);
                        }
                    }
                } catch (RuntimeException ignored) {}
            });
            mabEmbeddedRefreshTimer.setRepeats(true);
            mabEmbeddedRefreshTimer.start();
            return mabBattleShell;
        }
        if (launchMode == GameLaunchMode.MAB_AI_VS_AI && mabPlayerBState != null
                && mabMatch != null) {
            return startAiVsAiEmbedded();
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
                    Runnable setup = mabBackToSetupCallback;
                    Runnable back = mabBackToMenuCallback;
                    shellRef.showResultOverlay(title, cause, body, restart, setup, back);
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
                                            handleRedesignUpgradeChoice(matchRef, shellRef,
                                                    draftOwner, card);
                                            return;
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
                                    int previousDefcon = pvpLastKnownDefcon;
                                    pvpLastKnownDefcon = currentDefcon;
                                    beginDefconRedesignFlow(mabMatch, shellRef,
                                            previousDefcon, currentDefcon, false);
                                } else if (!mabDefconRedesignFlowActive) {
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
                System.err.println("[MAB-PVE] embedded HUD unavailable - falling back to bare GameView");
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
        boolean aiVsAi = launchMode == GameLaunchMode.MAB_AI_VS_AI;
        boolean debugHudOptIn = (pve && mabPveConfig != null && mabPveConfig.isShowDebugHud())
                || "true".equalsIgnoreCase(System.getProperty("mab.debug.hud", "false"));
        if (!pve && !localPvp && !aiVsAi && !debugHudOptIn) return;
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
                    // Local PvP and AI-vs-AI both use the shared-bag PvP factory.
                    mabMatch = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                            gameState, mabPlayerBState, MatchDifficulty.NORMAL, mabSeed);
                }
                applyConfiguredMabNukeDesigns();
                mabMatch.startMatch();
                mabMusicLaunchActive = false;
                mabMusicHighStackActive = false;
                mabMusicResultPlayed = false;
                musicDirector.playGameplayDefcon(
                        mabMatch.getDefconState() == null ? 5 : mabMatch.getDefconState().getLevel());
            }
            if (pve && mabPlayerHud == null && mabPveConfig != null && mabPveConfig.isShowPlayerHud()) {
                mabPlayerHud = new MabPlayerFacingController(
                        mabMatch, ParticipantId.PLAYER_A,
                        mabPveConfig.getAiArchetype(),
                        mabPveConfig.getAiDifficulty(),
                        mabPveConfig.getBalanceProfile());
                mabPlayerHud.setAiPausedSupplier(() -> aiPausedByConsole);
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
                    mabBoardAiDriver.capSearchTimeBudgetMillis(LIVE_AI_SEARCH_BUDGET_MS);
                    // Pipe the live MAB state into the search so armed
                    // mode + incoming threats actually influence placement.
                    mabBoardAiDriver.attachStrategicContext(mabMatch,
                            ParticipantId.PLAYER_B);
                    System.out.println("[MAB-PVE] board AI difficulty="
                            + mabPveConfig.getAiDifficulty()
                            + " targetPps="
                            + String.format("%.2f", mabBoardAiDriver.getTargetPps()));
                    final MabBoardAiDriver driverRef = mabBoardAiDriver;
                    final long[] lastLog = { 0L };
                    mabBoardAiTimer = new Timer(AI_FRAME_INTERVAL_MS, e -> {
                        try {
                            if (isDevConsoleOpen()) return;
                            if (aiPausedByConsole) return;
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
                mabDebugController = new MabDebugController(mabMatch);
                mabDebugController.setAiPausedSupplier(() -> aiPausedByConsole);
                mabDebugFrame = new MabDebugFrame(mabDebugController);
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
                    if (p1.getBuilderSource() != null) {
                        lastBuilderSource.put(ParticipantId.PLAYER_A, p1.getBuilderSource());
                    }
                }
                if (p2 != null && p2.getDesign() != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_B)
                            .getNukeBuildState()
                            .setDesign(p2.getDesign(), defconLevel);
                    if (p2.getBuilderSource() != null) {
                        lastBuilderSource.put(ParticipantId.PLAYER_B, p2.getBuilderSource());
                    }
                }
            } else if (launchMode == GameLaunchMode.MAB_AI_VS_AI) {
                com.tetris.mab.nuke.NukeDesign aDesign = MabAiDesignPicker.pick(
                        mabAiVsAiConfig.getPlayerAArchetype(),
                        mabAiVsAiConfig.getPlayerADifficulty());
                com.tetris.mab.nuke.NukeDesign bDesign = MabAiDesignPicker.pick(
                        mabAiVsAiConfig.getPlayerBArchetype(),
                        mabAiVsAiConfig.getPlayerBDifficulty());
                if (aDesign != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_A)
                            .getNukeBuildState()
                            .setDesign(aDesign, defconLevel);
                }
                if (bDesign != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_B)
                            .getNukeBuildState()
                            .setDesign(bDesign, defconLevel);
                }
                System.out.println("[MAB-AIvAI] A=" + (aDesign == null ? "?" : aDesign.getId())
                        + " B=" + (bDesign == null ? "?" : bDesign.getId()));
            } else {
                MabNukeDesignSelection selection = mabPveConfig.getNukeDesignSelection();
                if (selection != null && selection.getDesign() != null) {
                    mabMatch.getParticipant(ParticipantId.PLAYER_A)
                            .getNukeBuildState()
                            .setDesign(selection.getDesign(), defconLevel);
                    if (selection.getBuilderSource() != null) {
                        lastBuilderSource.put(ParticipantId.PLAYER_A, selection.getBuilderSource());
                    }
                }
                // Player B (the AI) gets a design that matches the
                // configured archetype + difficulty so it actually plays
                // like the strategic identity the player picked.
                try {
                    com.tetris.mab.nuke.NukeDesign aiDesign = MabAiDesignPicker.pick(
                            mabPveConfig.getAiArchetype(),
                            mabPveConfig.getAiDifficulty());
                    if (aiDesign != null) {
                        mabMatch.getParticipant(ParticipantId.PLAYER_B)
                                .getNukeBuildState()
                                .setDesign(aiDesign, defconLevel);
                        System.out.println("[MAB-PVE] AI Player B design = "
                                + aiDesign.getId());
                    }
                } catch (RuntimeException ex) {
                    System.err.println("[MAB] AI design pick failed: " + ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("[MAB] nuke selection ignored: " + ex.getMessage());
        }
    }

    private void beginDefconRedesignFlow(MutuallyAssuredBlocksMatch matchRef,
                                         com.tetris.mab.ui.MabBattleShellPanel shellRef,
                                         int previousDefcon,
                                         int currentDefcon,
                                         boolean localPvp) {
        if (matchRef == null || shellRef == null || mabDefconRedesignFlowActive) return;
        mabDefconRedesignFlowActive = true;
        clearAllMabHeldInputs(shellRef);
        boolean opened = false;
        try {
            opened = matchRef.openUpgradePause("defcon_redesign");
            if (!opened) {
                mabDefconRedesignFlowActive = false;
                return;
            }
            boolean redesignOnly = hasRedesignUpgradeOwned(matchRef, ParticipantId.PLAYER_A);
            musicDirector.playMidgameBuilder(redesignOnly);
            resetNukeRedesignCardAvailability(matchRef);
            matchRef.recordPlayerFacingEvent("DEFCON_REDESIGN_STARTED", null,
                    "DEFCON " + previousDefcon + " -> " + currentDefcon,
                    java.util.Map.of("previousDefcon", previousDefcon,
                            "currentDefcon", currentDefcon,
                            "mode", localPvp ? "local_pvp" : "pve"));
            double gravity = com.tetris.mab.DefconState.gravityMultiplierForLevel(currentDefcon);
            boolean aiVsAi = launchMode == GameLaunchMode.MAB_AI_VS_AI;
            ParticipantId[] order = new ParticipantId[] {
                    ParticipantId.PLAYER_A, ParticipantId.PLAYER_B };
            boolean[] aiOrder = aiVsAi
                    ? new boolean[] { true, true }
                    : (localPvp
                            ? new boolean[] { false, false }
                            : new boolean[] { false, true });
            shellRef.showDefconRedesignCountdown(previousDefcon, currentDefcon,
                    1.0, gravity,
                    () -> showDefconRedesignStep(matchRef, shellRef, currentDefcon,
                            localPvp, order, aiOrder, 0));
        } catch (RuntimeException ex) {
            if (opened) {
                try { matchRef.closeUpgradePause("defcon_redesign"); }
                catch (RuntimeException ignored) {}
            }
            mabDefconRedesignFlowActive = false;
            shellRef.hideDefconRedesignOverlay();
            if (matchRef != null && matchRef.getDefconState() != null) {
                musicDirector.playGameplayDefcon(matchRef.getDefconState().getLevel());
            }
        }
    }

    private void handleRedesignUpgradeChoice(MutuallyAssuredBlocksMatch matchRef,
                                             com.tetris.mab.ui.MabBattleShellPanel shellRef,
                                             ParticipantId draftOwner,
                                             com.tetris.mab.upgrade.draft.MabUpgradeCard card) {
        if (matchRef == null || shellRef == null || draftOwner == null) return;
        shellRef.hideUpgradeOverlay();
        clearAllMabHeldInputs(shellRef);
        musicDirector.playMidgameBuilder(true);
        int defcon = matchRef.getDefconState() == null
                ? 5 : matchRef.getDefconState().getLevel();
        String title = (draftOwner == ParticipantId.PLAYER_A ? "PLAYER 1" : "PLAYER 2")
                + " - WARHEAD REVIEW (DEFCON " + defcon + ")";
        com.tetris.model.nuke.NukeDesign humanSeed = lastBuilderSourceFor(draftOwner);
        shellRef.showDefconRedesignReview(matchRef, draftOwner, title, defcon,
                humanSeed, null, false,
                selection -> {
                    applyRedesignSelection(matchRef, draftOwner, selection);
                    if (card != null) matchRef.applyHumanUpgradeChoice(card);
                    shellRef.hideDefconRedesignOverlay();
                    matchRef.closeUpgradePause("upgrade_draft");
                    musicDirector.playGameplayDefcon(defcon);
                    shellRef.requestGameFocus();
                },
                () -> {
                    shellRef.hideDefconRedesignOverlay();
                    matchRef.closeUpgradePause("upgrade_draft");
                    musicDirector.playGameplayDefcon(defcon);
                    shellRef.requestGameFocus();
                });
    }

    private void showDefconRedesignStep(MutuallyAssuredBlocksMatch matchRef,
                                        com.tetris.mab.ui.MabBattleShellPanel shellRef,
                                        int currentDefcon,
                                        boolean localPvp,
                                        ParticipantId[] order,
                                        boolean[] aiControlled,
                                        int index) {
        if (matchRef == null || shellRef == null || order == null
                || aiControlled == null || index >= order.length) {
            finishDefconRedesignFlow(matchRef, shellRef);
            return;
        }
        ParticipantId pid = order[index];
        boolean ai = aiControlled[index];
        musicDirector.playMidgameBuilder(hasRedesignUpgradeOwned(matchRef, pid));
        boolean aiVsAi = launchMode == GameLaunchMode.MAB_AI_VS_AI;
        String who;
        if (aiVsAi) {
            who = pid == ParticipantId.PLAYER_A ? "AI A" : "AI B";
        } else {
            who = pid == ParticipantId.PLAYER_A ? "PLAYER 1" : (localPvp ? "PLAYER 2" : "AI");
        }
        String title = who + " - WARHEAD REVIEW (DEFCON " + currentDefcon + ")";
        com.tetris.mab.nuke.NukeDesign aiPick = ai ? pickAiRedesign(pid) : null;
        com.tetris.model.nuke.NukeDesign humanSeed = ai ? null : lastBuilderSourceFor(pid);
        shellRef.showDefconRedesignReview(matchRef, pid, title, currentDefcon,
                humanSeed, aiPick, ai,
                selection -> {
                    applyRedesignSelection(matchRef, pid, selection);
                    showDefconRedesignStep(matchRef, shellRef, currentDefcon,
                            localPvp, order, aiControlled, index + 1);
                },
                () -> showDefconRedesignStep(matchRef, shellRef, currentDefcon,
                        localPvp, order, aiControlled, index + 1));
    }

    private void finishDefconRedesignFlow(MutuallyAssuredBlocksMatch matchRef,
                                          com.tetris.mab.ui.MabBattleShellPanel shellRef) {
        if (shellRef != null) {
            shellRef.hideDefconRedesignOverlay();
            clearAllMabHeldInputs(shellRef);
        }
        if (matchRef != null) {
            try {
                matchRef.recordPlayerFacingEvent("DEFCON_REDESIGN_FINISHED",
                        null, "resume", java.util.Map.of());
                matchRef.closeUpgradePause("defcon_redesign");
            } catch (RuntimeException ignored) {}
            if (matchRef.getDefconState() != null) {
                musicDirector.playGameplayDefcon(matchRef.getDefconState().getLevel());
            }
        }
        mabDefconRedesignFlowActive = false;
        if (shellRef != null) shellRef.requestGameFocus();
    }

    private void applyRedesignSelection(MutuallyAssuredBlocksMatch matchRef,
                                        ParticipantId pid,
                                        MabNukeDesignSelection selection) {
        if (matchRef == null || pid == null || selection == null
                || selection.getDesign() == null) return;
        try {
            com.tetris.mab.ParticipantState p = matchRef.getParticipant(pid);
            com.tetris.mab.nuke.NukeDesign oldDesign =
                    p == null || p.getNukeBuildState() == null
                            ? null
                            : p.getNukeBuildState().getCurrentDesign();
            double ratio = com.tetris.mab.upgrade.NukeRedesignRetentionRules
                    .suggestedRetentionRatio(oldDesign, selection.getDesign());
            matchRef.redesignNukeDuringUpgradePause(pid, selection.getDesign(), ratio);
            // Remember the builder source so the next mid-game redesign
            // can re-seed the integrated builder with the player's own
            // last design instead of a default placeholder.
            if (selection.getBuilderSource() != null) {
                lastBuilderSource.put(pid, selection.getBuilderSource());
            }
        } catch (RuntimeException ignored) {}
    }

    /** Builder-model designs the player most recently confirmed, keyed
     *  by participant. Used as the {@code builderSeed} on mid-game
     *  redesign so the integrated builder loads the closest editable
     *  approximation of the current design. */
    private final java.util.EnumMap<ParticipantId, com.tetris.model.nuke.NukeDesign>
            lastBuilderSource = new java.util.EnumMap<>(ParticipantId.class);

    /** Returns the most recent builder-model design the player
     *  confirmed for {@code pid}, or {@code null} if none was
     *  recorded yet (e.g. AI participant or default-doctrine match). */
    private com.tetris.model.nuke.NukeDesign lastBuilderSourceFor(ParticipantId pid) {
        return pid == null ? null : lastBuilderSource.get(pid);
    }

    private com.tetris.mab.nuke.NukeDesign pickAiRedesign(ParticipantId pid) {
        try {
            if (launchMode == GameLaunchMode.MAB_AI_VS_AI && mabAiVsAiConfig != null) {
                return pid == ParticipantId.PLAYER_A
                        ? MabAiDesignPicker.pick(mabAiVsAiConfig.getPlayerAArchetype(),
                                mabAiVsAiConfig.getPlayerADifficulty())
                        : MabAiDesignPicker.pick(mabAiVsAiConfig.getPlayerBArchetype(),
                                mabAiVsAiConfig.getPlayerBDifficulty());
            }
            return MabAiDesignPicker.pick(
                    mabPveConfig == null ? MabAiArchetype.BALANCED : mabPveConfig.getAiArchetype(),
                    mabPveConfig == null ? MabAiDifficulty.NORMAL : mabPveConfig.getAiDifficulty());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void clearAllMabHeldInputs(com.tetris.mab.ui.MabBattleShellPanel shellRef) {
        try { if (inputHandler != null) inputHandler.releaseAll(); }
        catch (RuntimeException ignored) {}
        try { if (localPvpInputRouter != null) localPvpInputRouter.releaseAll(); }
        catch (RuntimeException ignored) {}
        try { if (shellRef != null) shellRef.clearHeldInputs(); }
        catch (RuntimeException ignored) {}
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

    /**
     * Builds the AI-vs-AI watcher panel: two opponent boards, two AI
     * drivers, and a refresh timer that resolves impacts and auto-picks
     * upgrade drafts. No human input is wired.
     */
    private javax.swing.JComponent startAiVsAiEmbedded() {
        MabAiArchetype archA = mabAiVsAiConfig.getPlayerAArchetype();
        MabAiDifficulty diffA = mabAiVsAiConfig.getPlayerADifficulty();
        MabAiArchetype archB = mabAiVsAiConfig.getPlayerBArchetype();
        MabAiDifficulty diffB = mabAiVsAiConfig.getPlayerBDifficulty();

        MabOpponentBoardPanel panelA = new MabOpponentBoardPanel(gameState);
        panelA.setOpponentInfo(archA, diffA, mabAiVsAiConfig.getBalanceProfile());
        MabOpponentBoardPanel panelB = new MabOpponentBoardPanel(mabPlayerBState);
        panelB.setOpponentInfo(archB, diffB, mabAiVsAiConfig.getBalanceProfile());

        // Board AIs — one per side. Strategic context wired so they
        // respect armed mode and incoming threats.
        mabBoardAiDriverA = new MabBoardAiDriver(gameState);
        mabBoardAiDriverA.setDifficulty(diffA);
        mabBoardAiDriverA.capSearchTimeBudgetMillis(LIVE_AI_SEARCH_BUDGET_MS);
        mabBoardAiDriverA.attachStrategicContext(mabMatch, ParticipantId.PLAYER_A);
        mabBoardAiDriver = new MabBoardAiDriver(mabPlayerBState);
        mabBoardAiDriver.setDifficulty(diffB);
        mabBoardAiDriver.capSearchTimeBudgetMillis(LIVE_AI_SEARCH_BUDGET_MS);
        mabBoardAiDriver.attachStrategicContext(mabMatch, ParticipantId.PLAYER_B);
        panelA.setBoardAi(mabBoardAiDriverA);
        panelB.setBoardAi(mabBoardAiDriver);

        // Strategic AIs handle civil-defence, upgrades, and arming.
        mabStrategicAiA = new com.tetris.mab.ai.MabAiDriver(mabMatch,
                ParticipantId.PLAYER_A, archA, diffA,
                mabAiVsAiConfig.getBalanceProfile());
        mabStrategicAiB = new com.tetris.mab.ai.MabAiDriver(mabMatch,
                ParticipantId.PLAYER_B, archB, diffB,
                mabAiVsAiConfig.getBalanceProfile());
        mabStrategicAiA.setEnabled(true);
        mabStrategicAiB.setEnabled(true);
        // Real piece locks already advance the strategic clock — no
        // hidden bookkeeping needed.
        mabStrategicAiA.setAdvanceHiddenClock(false);
        mabStrategicAiB.setAdvanceHiddenClock(false);

        String aTitle = "AI A :: " + archA.displayName() + " / " + diffA.displayName();
        String bTitle = "AI B :: " + archB.displayName() + " / " + diffB.displayName();
        String modeLine = "EXHIBITION :: AI vs AI";
        String modeSub = "BOTH STATIONS AUTOMATED";
        final Runnable backRef = mabBackToMenuCallback;

        // Use the existing battle shell but pass the LEFT side as an
        // opponent panel via a thin GamePanel adapter — actually, the
        // shell expects a live GamePanel for the player. We do not have
        // one for AI A (we want a read-only view). Simplest path: build
        // the shell with the gameView's GamePanel (which is bound to
        // gameState) — that gives a live render of A's board, and the
        // AI just controls gameState directly.
        mabBattleShell = new com.tetris.mab.ui.MabBattleShellPanel(
                gameView.getGamePanel(),
                gameState,
                panelB,
                ParticipantId.PLAYER_A,
                ParticipantId.PLAYER_B,
                aTitle, bTitle,
                modeLine, modeSub,
                backRef,
                /*opponentIsAi*/ true,
                mabPlayerBState);
        System.out.println("[MAB-AIvAI] mounted root = MabBattleShellPanel");

        final com.tetris.mab.ui.MabBattleShellPanel shellRef = mabBattleShell;
        final MabBoardAiDriver boardA = mabBoardAiDriverA;
        final MabBoardAiDriver boardB = mabBoardAiDriver;
        final com.tetris.mab.ai.MabAiDriver stratA = mabStrategicAiA;
        final com.tetris.mab.ai.MabAiDriver stratB = mabStrategicAiB;
        pvpLastKnownDefcon = mabMatch.getDefconState() == null
                ? 5 : mabMatch.getDefconState().getLevel();

        // Board AI tick timers — separate so each AI keeps its own pace.
        mabBoardAiTimerA = new Timer(AI_FRAME_INTERVAL_MS, e -> {
            try { if (!isDevConsoleOpen() && !aiPausedByConsole) boardA.tick(); }
            catch (RuntimeException ignored) {}
        });
        mabBoardAiTimerA.setRepeats(true);
        mabBoardAiTimerA.start();
        mabBoardAiTimer = new Timer(AI_FRAME_INTERVAL_MS, e -> {
            try { if (!isDevConsoleOpen() && !aiPausedByConsole) boardB.tick(); }
            catch (RuntimeException ignored) {}
        });
        mabBoardAiTimer.setRepeats(true);
        mabBoardAiTimer.start();

        // Shared refresh timer ticks the strategic AIs, resolves
        // impacts, prunes finished threats, and auto-picks upgrades for
        // whichever side currently has a draft open.
        mabAiVsAiRefreshTimer = new Timer(120, e -> {
            try {
                if (mabMatch == null) return;
                boolean devConsoleOpen = isDevConsoleOpen();
                if (!devConsoleOpen && !mabMatch.isPaused()) {
                    mabMatch.resolveAllImpactReady();
                    mabMatch.pruneCompletedThreats();
                    if (!aiPausedByConsole) {
                        try { stratA.tick(); } catch (RuntimeException ignored) {}
                        try { stratB.tick(); } catch (RuntimeException ignored) {}
                        autoPickAiVsAiUpgradeDraft();
                        int currentDefcon = mabMatch.getDefconState() == null
                                ? pvpLastKnownDefcon : mabMatch.getDefconState().getLevel();
                        if (currentDefcon < pvpLastKnownDefcon
                                && !shellRef.isResultVisible()
                                && !shellRef.isDefconRedesignOverlayVisible()) {
                            int previousDefcon = pvpLastKnownDefcon;
                            pvpLastKnownDefcon = currentDefcon;
                            beginDefconRedesignFlow(mabMatch, shellRef,
                                    previousDefcon, currentDefcon, false);
                        } else if (!mabDefconRedesignFlowActive) {
                            pvpLastKnownDefcon = currentDefcon;
                        }
                    }
                }
                shellRef.refreshAll(mabMatch);
                if (mabMatch.getWinner() != null && !shellRef.isResultVisible()) {
                    var summary = com.tetris.mab.ui.MabMatchResultSummary.from(
                            mabMatch, ParticipantId.PLAYER_A);
                    String title = com.tetris.mab.ui.MabMatchResultFormatter
                            .formatTitle(summary);
                    String body = com.tetris.mab.ui.MabMatchResultFormatter
                            .formatBody(summary);
                    String cause = summary == null ? "" : "Cause :: " + summary.getReason();
                    shellRef.showResultOverlay(title, cause, body,
                            mabRestartCallback, mabBackToSetupCallback,
                            mabBackToMenuCallback);
                }
            } catch (RuntimeException ignored) {}
        });
        mabAiVsAiRefreshTimer.setRepeats(true);
        mabAiVsAiRefreshTimer.start();
        return mabBattleShell;
    }

    /**
     * If any side currently has a level-up draft open, auto-pick a card
     * using {@link com.tetris.mab.upgrade.draft.MabAiUpgradePicker}. The
     * watcher never opens a modal — picks are silent and apply through
     * the normal match API.
     */
    private void autoPickAiVsAiUpgradeDraft() {
        if (mabMatch == null) return;
        try {
            var draft = mabMatch.tickUpgradeDrafts();
            if (draft == null) return;
            mabMatch.openUpgradePause("ai_vs_ai_draft");
            com.tetris.mab.MatchDifficulty matchDiff = com.tetris.mab.MatchDifficulty.NORMAL;
            long seed = (long) draft.getParticipantId().ordinal() * 31L
                    + draft.getLevel() * 7L;
            var card = com.tetris.mab.upgrade.draft.MabAiUpgradePicker.pick(
                    draft, matchDiff, seed);
            if (card != null) {
                mabMatch.applyHumanUpgradeChoice(card);
            }
            mabMatch.closeUpgradePause("ai_vs_ai_draft");
        } catch (RuntimeException ignored) {}
    }

    private void closeMabIntegrations() {
        if (mabBoardAiTimerA != null) {
            mabBoardAiTimerA.stop();
            mabBoardAiTimerA = null;
        }
        if (mabAiVsAiRefreshTimer != null) {
            mabAiVsAiRefreshTimer.stop();
            mabAiVsAiRefreshTimer = null;
        }
        if (mabStrategicAiA != null) {
            try { mabStrategicAiA.setEnabled(false); } catch (RuntimeException ignored) {}
            mabStrategicAiA = null;
        }
        if (mabStrategicAiB != null) {
            try { mabStrategicAiB.setEnabled(false); } catch (RuntimeException ignored) {}
            mabStrategicAiB = null;
        }
        if (mabBoardAiDriverA != null) {
            try { mabBoardAiDriverA.setEnabled(false); } catch (RuntimeException ignored) {}
            mabBoardAiDriverA = null;
        }
        closeMabIntegrationsInner();
    }

    private void closeMabIntegrationsInner() {
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
        mabDebugController = null;
        if (mabMatch != null) {
            try { mabMatch.shutdown(); } catch (RuntimeException ignored) {}
            mabMatch = null;
        }
        mabPlayerBState = null;
        mabMusicLaunchActive = false;
        mabMusicHighStackActive = false;
        mabMusicResultPlayed = false;
        if (launchMode != null && launchMode.isMabMode()) {
            musicDirector.stopAll();
        }
    }

    private void refreshMabMusicState() {
        if (launchMode == null || !launchMode.isMabMode() || mabMatch == null) return;

        if (mabMatch.getWinner() != null) {
            if (!mabMusicResultPlayed) {
                mabMusicResultPlayed = true;
                if (launchMode == GameLaunchMode.MAB_PVE) {
                    musicDirector.playResultPvE(mabMatch.getWinner() == ParticipantId.PLAYER_A);
                } else {
                    musicDirector.playResultPvP(mabMatch.getWinner());
                }
            }
            return;
        }

        if (mabMatch.getCurrentPhase() != MatchPhase.UPGRADE_PAUSE) {
            int defcon = mabMatch.getDefconState() == null
                    ? 5 : mabMatch.getDefconState().getLevel();
            musicDirector.playGameplayDefcon(defcon);
        }

        boolean launch = hasUnresolvedMabLaunch();
        if (launch != mabMusicLaunchActive) {
            mabMusicLaunchActive = launch;
            if (launch) musicDirector.playLaunchUntilImpact();
            else musicDirector.onImpactResolved();
        }

        boolean danger = isMabHighStackDanger();
        if (danger != mabMusicHighStackActive) {
            mabMusicHighStackActive = danger;
            musicDirector.setHighStackDanger(danger);
        }
    }

    private boolean hasUnresolvedMabLaunch() {
        if (mabMatch == null) return false;
        return hasUnresolvedLaunch(ParticipantId.PLAYER_A)
                || hasUnresolvedLaunch(ParticipantId.PLAYER_B);
    }

    private boolean hasUnresolvedLaunch(ParticipantId pid) {
        try {
            var p = mabMatch.getParticipant(pid);
            if (p == null || p.getActiveLaunches() == null) return false;
            for (var launch : p.getActiveLaunches()) {
                var phase = launch.getPhase();
                if (phase != com.tetris.mab.launch.LaunchPhase.RESOLVED
                        && phase != com.tetris.mab.launch.LaunchPhase.CANCELLED) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    private boolean isMabHighStackDanger() {
        return stackDanger(gameState) || stackDanger(mabPlayerBState);
    }

    private boolean stackDanger(GameState state) {
        try {
            return state != null
                    && state.getBoard() != null
                    && state.getBoard().getStackHeight() >= 14;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean hasRedesignUpgradeOwned(MutuallyAssuredBlocksMatch matchRef,
                                            ParticipantId pid) {
        try {
            var p = matchRef == null ? null : matchRef.getParticipant(pid);
            var inv = p == null ? null : p.getUpgradeInventory();
            return inv != null && inv.hasUpgrade("redesign_nuke");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Exposes the main window so the launcher can listen for its close. */
    public MainFrame getMainFrame() {
        return mainFrame;
    }

    public int getTargetRenderFps() {
        return FrameRate.RENDER_FPS;
    }

    public int getLastRenderFpsForProbe() {
        return lastRenderFps;
    }

    public int getRenderFrameCountForProbe() {
        return fpsFrameCount;
    }

    public void addRenderFrameProbeListener(LongConsumer listener) {
        if (listener != null) renderFrameProbeListeners.add(listener);
    }

    public void removeRenderFrameProbeListener(LongConsumer listener) {
        if (listener != null) renderFrameProbeListeners.remove(listener);
    }

    // ─────────────────────── Timer helpers ──────────────────────────

    private void startTimers() {
        stopTimers(); // guard against double-start
        resetFpsTelemetry();
        physicsLoop = new FixedRateEdtLoop(
                "tetris-physics-120hz",
                FrameRate.PHYSICS_INTERVAL_NS,
                this::physicsTick);
        physicsLoop.start();

        renderLoop = new FixedRateEdtLoop(
                "tetris-render-60fps",
                FrameRate.RENDER_INTERVAL_NS,
                this::renderFrame,
                false);
        renderLoop.start();
    }

    private void stopTimers() {
        if (physicsLoop != null) { physicsLoop.stop(); physicsLoop = null; }
        if (renderLoop  != null) { renderLoop.stop();  renderLoop  = null; }
    }

    private void resetFpsTelemetry() {
        fpsBucketStartNs = -1L;
        fpsFrameCount = 0;
        lastRenderFps = 0;
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

    /** Render frame - runs at fixed 60 FPS. Samples input, applies IRS/IHS, repaints. */
    private void renderFrame() {
        renderFrame(System.nanoTime());
    }

    private void renderFrame(long nowNs) {
        // FPS tracking (rolling 1-second window)
        if (fpsBucketStartNs < 0L) fpsBucketStartNs = nowNs;
        while (nowNs - fpsBucketStartNs >= 1_000_000_000L) {
            lastRenderFps    = fpsFrameCount;
            fpsFrameCount    = 0;
            fpsBucketStartNs += 1_000_000_000L;
        }
        fpsFrameCount++;
        for (LongConsumer listener : renderFrameProbeListeners) {
            listener.accept(nowNs);
        }

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
        refreshMabMusicState();
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
        return gameState.getEffectiveGravityInterval();
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
             + String.format(" FPS      %d  (target %d Hz)", lastRenderFps, FrameRate.RENDER_FPS) + "\n"
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
            case "fps"      -> lastRenderFps + " fps  (target " + FrameRate.RENDER_FPS
                    + " Hz, frame " + renderIntervalMs + " ms)";
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
            case "ai"       -> consoleAi(arg);
            case "defcon"   -> consoleDefcon(arg);
            default         -> "unknown command: " + raw + "  (type 'help' for commands)";
        };
    }

    private String consoleAi(String arg) {
        String sub = arg == null ? "" : arg.trim().toLowerCase();
        if (sub.isEmpty() || sub.equals("pause")) {
            aiPausedByConsole = true;
            releaseGameplayInputs();
            return "AI activity paused.";
        }
        if (sub.equals("resume") || sub.equals("unpause")) {
            aiPausedByConsole = false;
            return "AI activity resumed.";
        }
        if (sub.equals("status")) {
            return "AI activity is " + (aiPausedByConsole ? "paused." : "running.");
        }
        return "Usage: ai pause | ai resume | ai status";
    }

    private String consoleDefcon(String arg) {
        StringBuilder err = new StringBuilder();
        if (!requireMab(err)) return err.toString();
        String sub = arg == null ? "" : arg.trim().toLowerCase();
        if (!sub.isEmpty() && !sub.equals("max") && !sub.equals("prime")) {
            return "Usage: defcon max";
        }
        com.tetris.mab.DefconState ds = mabMatch.getDefconState();
        if (ds == null) return "DEFCON state unavailable.";
        int next = ds.getNextThreshold();
        if (next < 0) {
            return "Already at DEFCON " + ds.getLevel()
                    + "; no higher alert threshold remains.";
        }
        int add = Math.max(0, (next - 1) - ds.getEscalationMeter());
        if (add > 0) {
            mabMatch.addEscalationAndRefresh(add, "console:defcon-max");
        }
        return "DEFCON primed: level " + ds.getLevel()
                + ", escalation " + ds.getEscalationMeter() + "/" + next
                + ". A single-line clear will add +1 and trigger the next alert.";
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
