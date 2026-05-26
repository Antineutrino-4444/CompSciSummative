package com.tetris.controller;

import com.tetris.audio.MusicDirector;
import com.tetris.audio.SfxGameEventBridge;
import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;
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
import com.tetris.view.FpsOverlayPanel;
import com.tetris.view.SettingsPanel;
import com.tetris.view.SwingPaintDiagnostics;

import com.tetris.view.DebugOverlay;

import javax.swing.JLayeredPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
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
    // Live AI search budget. Caps each replan to keep EDT stalls bounded.
    // Desktop keeps a moderate 16 ms budget; Linux ARM boards default lower
    // because that same search can monopolize the EDT on Raspberry Pi-class CPUs.
    // Override with -Dmab.ai.searchBudgetMs=<ms> when profiling.
    private static final int LIVE_AI_SEARCH_BUDGET_MS =
            Math.max(2, Integer.getInteger("mab.ai.searchBudgetMs",
                    defaultLiveAiSearchBudgetMillis()));

    private static int defaultLiveAiSearchBudgetMillis() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean linuxArm = os.contains("linux")
                && (arch.contains("arm") || arch.contains("aarch"));
        return linuxArm ? 8 : 16;
    }

    /** Render interval (ms) matched to the primary display refresh rate. */
    private final int renderIntervalMs;

    // ─────────────────────── Components ─────────────────────────

    private GameState gameState;
    private MainFrame mainFrame;
    private GameView gameView; // populated when started in embedded mode
    private InputHandler inputHandler;
    private FixedRateEdtLoop physicsLoop;
    private FixedRateEdtLoop renderLoop;

    // ─────────────── Dev console + F3 overlay ─────────────────────
    // The console and performance overlay are owned by the global
    // DebugOverlay singleton so they work on every screen (menu,
    // settings, key bindings, in-game). This controller only registers
    // its game-specific command handler and perf read-out with it.

    // ─────────────── Cheat menu ───────────────────────────────────
    private com.tetris.view.CheatMenuPanel cheatMenu;

    // ─────────────── FPS tracking ─────────────────────────────────
    private long   fpsBucketStartNs = -1L;
    private int    fpsFrameCount   = 0;
    private int    lastRenderFps   = 0;
    private final CopyOnWriteArrayList<LongConsumer> renderFrameProbeListeners =
            new CopyOnWriteArrayList<>();
    private final TimingStats physicsP1Timing = new TimingStats();
    private final TimingStats physicsP2Timing = new TimingStats();
    private final TimingStats renderInputTiming = new TimingStats();
    private final TimingStats renderSpawnAssistTiming = new TimingStats();
    private final TimingStats renderRepaintRequestTiming = new TimingStats();
    private final TimingStats renderDebugOverlayTiming = new TimingStats();
    private final TimingStats renderMusicTiming = new TimingStats();
    private long gcWindowLastSampleNs = -1L;
    private long gcWindowLastCount = 0L;
    private long gcWindowLastTimeMs = 0L;
    private long gcWindowDeltaCount = 0L;
    private long gcWindowDeltaTimeMs = 0L;
    private long gcWindowDurationNs = 0L;
    private FpsOverlayPanel fpsOverlay;

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
    private final SoundEffectManager sfx = SoundEffectManager.shared();
    private final SfxGameEventBridge sfxBridge = new SfxGameEventBridge(sfx, "p1");
    private final SfxGameEventBridge sfxBridgeP2 = new SfxGameEventBridge(sfx, "p2");
    private boolean mabMusicLaunchActive;
    private boolean mabMusicHighStackActive;
    private boolean mabMusicResultPlayed;

    // ─────────────── MAB SFX bookkeeping ──────────────────────────
    private int sfxLastDefconLevel = 0;
    private double sfxLastDefconProgress = 0.0;
    private int sfxLastWindupStage = -1;
    private boolean sfxLaunchSfxFired;
    private boolean sfxHighStackAlertFired;
    private int sfxLastP1Garbage;
    private int sfxLastP2Garbage;

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
        // In AI-vs-AI mode Player A's board is AI-driven, so pin it to
        // level 1 gravity (slow). Slow gravity keeps the AI survivable and
        // also acts as a safety net: if the placement planner ever wedges,
        // gravity still drifts the piece down so the game progresses
        // instead of stalling forever. Human-driven boards keep startLevel.
        int playerALevel = (this.launchMode == GameLaunchMode.MAB_AI_VS_AI)
                ? 1 : this.startLevel;
        this.gameState = new GameState(playerALevel);
        this.gameState.addListener(sfxBridge);
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
        registerDebugContext();

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
        DebugOverlay.shared().clearContext();
        detachFpsOverlay();
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

        // Register this game's console commands + perf read-out with the
        // global DebugOverlay (which already handles F3, backtick, and the
        // "debug" hotword everywhere).
        registerDebugContext();

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
                // PvE/AI-vs-AI: Player B is an AI. Pin it to level 1
                // gravity so the AI's survival floor is independent of the
                // player's chosen startLevel and of DEFCON gravity
                // escalation. Slow gravity is survivable for every tier
                // (the AI positions and hard-drops well within a row's
                // worth of fall time) and doubles as a safety net: a
                // wedged placement planner still drifts the piece down,
                // so the game progresses instead of stalling.
                // Local PvP: Player B is a human; honour the player's
                // chosen start level just like Player A.
                boolean playerBIsAi = pve || aiVsAi;
                int playerBStartLevel = playerBIsAi ? 1 : startLevel;
                mabPlayerBState = new GameState(playerBStartLevel);
                mabPlayerBState.addListener(sfxBridgeP2);
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
        // respect armed mode and incoming threats. Both boards run on
        // slow level-1 gravity (Player A pinned in the constructor,
        // Player B in openMabIntegrations), which keeps each AI survivable
        // and nudges any wedged placement down instead of stalling.
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

        refreshMabTacticalSfx();
    }

    /**
     * Tactical SFX cues that mirror MAB state transitions (DEFCON escalation,
     * inbound launch wind-up, sustained high-stack danger). Called once per
     * frame from {@link #refreshMabMusicState()}.
     */
    private void refreshMabTacticalSfx() {
        if (mabMatch == null) return;

        // DEFCON escalation → one zenith_levelup tick on the new band.
        com.tetris.mab.DefconState ds = mabMatch.getDefconState();
        int defcon = ds == null ? 5 : ds.getLevel();
        double progress = ds == null ? 0.0 : ds.getProgressToNextThreshold();
        if (sfxLastDefconLevel == 0) {
            sfxLastDefconLevel = defcon;
            sfxLastDefconProgress = progress;
        } else if (defcon < sfxLastDefconLevel) {
            // DEFCON numbers count *down* as danger rises (5 → 1).
            sfx.play(pickZenithLevelup());
            sfxLastDefconLevel = defcon;
            sfxLastDefconProgress = progress;
        } else if (defcon == sfxLastDefconLevel
                && progress - sfxLastDefconProgress >= 0.33) {
            // Sub-tier ramp within a single DEFCON band.
            sfx.play(pickZenithLevelup(), 0.7f);
            sfxLastDefconProgress = progress;
        }

        // Inbound-launch wind-up. Stage 1-4 buckets based on warning pieces
        // remaining: the closer to impact, the higher the wind-up severity.
        int stage = -1;
        for (ParticipantId pid : new ParticipantId[] {
                ParticipantId.PLAYER_A, ParticipantId.PLAYER_B }) {
            try {
                var p = mabMatch.getParticipant(pid);
                if (p == null) continue;
                for (var threat : p.getIncomingThreats()) {
                    if (threat == null) continue;
                    int remaining = threat.getWarningPiecesRemaining();
                    int total = Math.max(1, threat.getWarningPiecesTotal());
                    double frac = 1.0 - ((double) remaining / total);
                    int s;
                    if (frac >= 0.85) s = 4;
                    else if (frac >= 0.65) s = 3;
                    else if (frac >= 0.4) s = 2;
                    else s = 1;
                    if (s > stage) stage = s;
                }
            } catch (RuntimeException ignored) {}
        }
        if (stage > 0 && stage != sfxLastWindupStage) {
            sfxLastWindupStage = stage;
            SoundEffect cue = switch (stage) {
                case 4 -> SoundEffect.GARBAGE_WINDUP_4;
                case 3 -> SoundEffect.GARBAGE_WINDUP_3;
                case 2 -> SoundEffect.GARBAGE_WINDUP_2;
                default -> SoundEffect.GARBAGE_WINDUP_1;
            };
            sfx.play(cue);
        } else if (stage < 0) {
            sfxLastWindupStage = -1;
        }

        // Launch in flight (no warning pieces remaining) → smash + damage alert.
        boolean unresolved = hasUnresolvedMabLaunch();
        if (unresolved && !sfxLaunchSfxFired) {
            sfxLaunchSfxFired = true;
        } else if (!unresolved) {
            sfxLaunchSfxFired = false;
        }

        // High-stack danger pings the alert sound (manager throttles it).
        if (mabMusicHighStackActive) {
            if (!sfxHighStackAlertFired) {
                sfxHighStackAlertFired = true;
                sfx.play(SoundEffect.DAMAGE_ALERT);
            }
        } else {
            sfxHighStackAlertFired = false;
        }
    }

    private SoundEffect pickZenithLevelup() {
        SoundEffect[] options = {
                SoundEffect.ZENITH_LEVELUP_A,
                SoundEffect.ZENITH_LEVELUP_AHALFSHARP,
                SoundEffect.ZENITH_LEVELUP_B,
                SoundEffect.ZENITH_LEVELUP_C,
                SoundEffect.ZENITH_LEVELUP_E,
                SoundEffect.ZENITH_LEVELUP_FSHARP,
                SoundEffect.ZENITH_LEVELUP_G
        };
        int idx = (int) Math.floorMod(System.nanoTime() / 1_000_000L, options.length);
        return options[idx];
    }

    /**
     * Hook for the DEFCON redesign countdown widget to fire countdown SFX
     * for each displayed number. Called once per visible number; the manager
     * does not need throttle because we only invoke it on visible changes.
     */
    public void onMabCountdownTick(int secondsRemaining) {
        SoundEffect cue = switch (secondsRemaining) {
            case 5 -> SoundEffect.COUNTDOWN5;
            case 4 -> SoundEffect.COUNTDOWN4;
            case 3 -> SoundEffect.COUNTDOWN3;
            case 2 -> SoundEffect.COUNTDOWN2;
            case 1 -> SoundEffect.COUNTDOWN1;
            case 0 -> SoundEffect.GO;
            default -> null;
        };
        if (cue != null) sfx.play(cue);
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
                this::renderFrame);
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
        physicsP1Timing.reset();
        physicsP2Timing.reset();
        renderInputTiming.reset();
        renderSpawnAssistTiming.reset();
        renderRepaintRequestTiming.reset();
        renderDebugOverlayTiming.reset();
        renderMusicTiming.reset();
        gcWindowLastSampleNs = -1L;
        gcWindowLastCount = 0L;
        gcWindowLastTimeMs = 0L;
        gcWindowDeltaCount = 0L;
        gcWindowDeltaTimeMs = 0L;
        gcWindowDurationNs = 0L;
    }

    // ─────────────────────── Game loop ───────────────────────────────

    /** Physics tick — runs at fixed 120 Hz. Updates game state only. */
    private void physicsTick() {
        if (isDevConsoleOpen()) return;
        long p1StartNs = System.nanoTime();
        try {
            gameState.update();
        } finally {
            physicsP1Timing.record(System.nanoTime() - p1StartNs);
        }
        if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && mabPlayerBState != null) {
            long p2StartNs = System.nanoTime();
            try {
                mabPlayerBState.update();
            } finally {
                physicsP2Timing.record(System.nanoTime() - p2StartNs);
            }
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
        long inputStartNs = System.nanoTime();
        try {
            if (!modalOverlayUp && !devConsoleOpen) {
                if (launchMode == GameLaunchMode.MAB_LOCAL_PVP && localPvpInputRouter != null) {
                    localPvpInputRouter.processInput();
                } else {
                    inputHandler.processInput();
                }
            }
        } finally {
            renderInputTiming.record(System.nanoTime() - inputStartNs);
        }

        // 2. IRS/IHS: apply queued rotation/hold on freshly spawned pieces
        long assistStartNs = System.nanoTime();
        try {
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
        } finally {
            renderSpawnAssistTiming.record(System.nanoTime() - assistStartNs);
        }

        // 3. Repaint
        updateFpsOverlay();
        long repaintStartNs = System.nanoTime();
        try {
            if (mainFrame != null) mainFrame.repaint();
            if (gameView  != null) gameView.repaint();
            if (mabBattleShell != null) mabBattleShell.repaint();
        } finally {
            renderRepaintRequestTiming.record(System.nanoTime() - repaintStartNs);
        }
        long debugStartNs = System.nanoTime();
        try {
            DebugOverlay.shared().refreshPerf();
        } finally {
            renderDebugOverlayTiming.record(System.nanoTime() - debugStartNs);
        }
        long musicStartNs = System.nanoTime();
        try {
            refreshMabMusicState();
        } finally {
            renderMusicTiming.record(System.nanoTime() - musicStartNs);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT ACTION METHODS
    // ═══════════════════════════════════════════════════════════════
    // Called by InputHandler when keys are pressed.
    // These simply delegate to GameState.

    public void moveLeft() {
        if (!gameState.moveLeft()) {
            sfxBridge.onFailedSideMove();
        }
    }

    public void moveRight() {
        if (!gameState.moveRight()) {
            sfxBridge.onFailedSideMove();
        }
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
                gameState.addListener(sfxBridge);
                if (mainFrame != null) mainFrame.setGameState(gameState);
                if (gameView  != null) gameView.setGameState(gameState);
            }
            return;
        }
        // NORMAL_TETRIS / debug : standard board reset.
        gameState = new GameState(startLevel);
        gameState.addListener(sfxBridge);
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
        return DebugOverlay.shared().isConsoleOpen();
    }

    /**
     * Registers this game's console command handler, perf read-out, and the
     * console-open hook with the global {@link DebugOverlay}. The overlay owns
     * the key dispatcher and panels so they work on every screen.
     */
    private void registerDebugContext() {
        DebugOverlay overlay = DebugOverlay.shared();
        overlay.install();
        SwingPaintDiagnostics.reset();
        overlay.setContextHandler(this::handleConsoleCommand);
        overlay.setPerfSupplier(this::buildPerfString);
        overlay.setConsoleOpenHook(this::releaseGameplayInputs);
    }

    private void releaseGameplayInputs() {
        if (inputHandler != null) inputHandler.releaseAll();
        if (localPvpInputRouter != null) localPvpInputRouter.releaseAll();
    }

    private void updateFpsOverlay() {
        JLayeredPane lp = activeGameLayeredPane();
        if (lp == null) return;
        if (fpsOverlay == null) {
            fpsOverlay = new FpsOverlayPanel();
        }
        if (fpsOverlay.getParent() != lp) {
            Container prev = fpsOverlay.getParent();
            if (prev != null) {
                prev.remove(fpsOverlay);
                prev.repaint();
            }
            lp.add(fpsOverlay, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
        }
        fpsOverlay.setFps(displayFps());
        Dimension size = fpsOverlay.getPreferredSize();
        int x = Math.max(8, lp.getWidth() - size.width - 12);
        int y = 12;
        fpsOverlay.setBounds(x, y, size.width, size.height);
        fpsOverlay.setVisible(true);
        fpsOverlay.repaint();
    }

    private JLayeredPane activeGameLayeredPane() {
        java.awt.Window window = resolveWindow();
        if (!(window instanceof RootPaneContainer rpc)) return null;
        JLayeredPane lp = rpc.getRootPane().getLayeredPane();
        if (lp == null || lp.getWidth() <= 0 || lp.getHeight() <= 0) return null;
        return lp;
    }

    private int displayFps() {
        return lastRenderFps > 0 ? lastRenderFps : fpsFrameCount;
    }

    private void detachFpsOverlay() {
        if (fpsOverlay == null) return;
        Container parent = fpsOverlay.getParent();
        if (parent != null) {
            parent.remove(fpsOverlay);
            parent.repaint();
        }
        fpsOverlay = null;
    }

    /** Toggle the dev console open/closed. Called by InputHandler on ` / ~ key. */
    public void toggleDevConsole() {
        DebugOverlay.shared().toggleConsole();
    }

    /** Toggle the F3 performance overlay. Called by InputHandler on F3 key. */
    public void togglePerfOverlay() {
        DebugOverlay.shared().togglePerf();
    }

    private String buildPerfString() {
        return buildRichPerfString();
    }

    private String buildRichPerfString() {
        FixedRateEdtLoop.TelemetrySnapshot renderStats =
                renderLoop == null ? null : renderLoop.snapshot();
        FixedRateEdtLoop.TelemetrySnapshot physicsStats =
                physicsLoop == null ? null : physicsLoop.snapshot();
        SwingPaintDiagnostics.Snapshot paintStats = SwingPaintDiagnostics.snapshot();
        long gcCount = totalGcCount();
        long gcTimeMs = totalGcTimeMillis();
        sampleGcWindow(System.nanoTime(), gcCount, gcTimeMs);

        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapCommitted = rt.totalMemory();
        long heapMax = rt.maxMemory();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        com.tetris.model.ScoreSystem sc = gameState.getScoreSystem();
        Settings settings = Settings.get();

        String modeStr = (mabMatch != null)
                ? launchMode + " match-paused=" + mabMatch.isPaused()
                : launchMode.toString();

        StringBuilder sb = new StringBuilder(2600);
        sb.append("\u00a7h F3 PERFORMANCE\n");
        sb.append(String.format(" FPS      %d / %d target  |  frame budget %.2f ms",
                lastRenderFps, FrameRate.RENDER_FPS, ms(FrameRate.RENDER_INTERVAL_NS))).append('\n');
        sb.append(String.format(" Mode     %s  |  paused=%b  gameOver=%b",
                modeStr, gameState.isPaused(), gameState.isGameOver())).append('\n');
        sb.append(String.format(" Score    %,d  L%d  lines=%d  pieces=%d  PPS=%.2f",
                sc.getScore(), sc.getLevel(), sc.getTotalLinesCleared(),
                sc.getPiecesPlaced(), sc.getPiecesPerSecond())).append('\n');
        sb.append(String.format(" Gravity  %d ms/row  |  music=%s",
                getGravityInterval(), musicDirector.currentSoundtrackDisplay())).append('\n');

        appendRichHotspots(sb, renderStats, physicsStats, paintStats,
                heapUsed, heapMax);

        sb.append("\u00a7h\n");
        sb.append("\u00a7h LOOP TIMING  last/avg/max\n");
        sb.append(formatRichLoopTelemetry("Render", renderStats)).append('\n');
        sb.append(formatRichLoopTelemetry("Physics", physicsStats)).append('\n');
        appendTimingLine(sb, "Phys P1", physicsP1Timing.snapshot(),
                FrameRate.PHYSICS_INTERVAL_NS);
        if (launchMode == GameLaunchMode.MAB_LOCAL_PVP) {
            appendTimingLine(sb, "Phys P2", physicsP2Timing.snapshot(),
                    FrameRate.PHYSICS_INTERVAL_NS);
        }
        appendTimingLine(sb, "Input", renderInputTiming.snapshot(),
                FrameRate.RENDER_INTERVAL_NS);
        appendTimingLine(sb, "IRS/IHS", renderSpawnAssistTiming.snapshot(),
                FrameRate.RENDER_INTERVAL_NS);
        appendTimingLine(sb, "Repaint", renderRepaintRequestTiming.snapshot(),
                FrameRate.RENDER_INTERVAL_NS);
        appendTimingLine(sb, "F3 text", renderDebugOverlayTiming.snapshot(),
                FrameRate.RENDER_INTERVAL_NS);
        appendTimingLine(sb, "Music", renderMusicTiming.snapshot(),
                FrameRate.RENDER_INTERVAL_NS);

        sb.append("\u00a7h\n");
        sb.append("\u00a7h PAINT / REPAINT\n");
        sb.append(String.format(" Swing   pass %,d  dirty %,d  dirty/pass %.1f",
                paintStats.paintPasses, paintStats.dirtyRequests,
                ratio(paintStats.dirtyRequests, paintStats.paintPasses))).append('\n');
        sb.append(String.format(" Paint   %.2f/%.2f/%.2f ms  budget %.2f ms",
                ms(paintStats.lastPaintNs), ms(paintStats.averagePaintNs()),
                ms(paintStats.maxPaintNs), ms(FrameRate.RENDER_INTERVAL_NS))).append('\n');
        sb.append(String.format(" Backdrop %.3f/%.3f/%.3f ms  passes %,d",
                ms(paintStats.lastBackdropPaintNs),
                ms(paintStats.averageBackdropPaintNs()),
                ms(paintStats.maxBackdropPaintNs),
                paintStats.backdropPasses)).append('\n');
        appendComponentPaintLines(sb, paintStats);

        sb.append("\u00a7h\n");
        sb.append("\u00a7h MEMORY / GC\n");
        sb.append(String.format(" Heap    %d / %d MB committed  max %d MB  used %.0f%%",
                mb(heapUsed), mb(heapCommitted), mb(heapMax),
                percent(heapUsed, heapMax))).append('\n');
        sb.append(String.format(" NonHeap %d / %d MB committed  max %s",
                mb(nonHeap.getUsed()), mb(nonHeap.getCommitted()),
                nonHeap.getMax() < 0L ? "n/a" : mb(nonHeap.getMax()) + " MB")).append('\n');
        sb.append(String.format(" GC      total %,d collections / %,d ms  recent +%,d / +%,d ms over %.1fs",
                gcCount, gcTimeMs, gcWindowDeltaCount, gcWindowDeltaTimeMs,
                gcWindowDurationNs <= 0L ? 0.0 : gcWindowDurationNs / 1_000_000_000.0)).append('\n');

        sb.append("\u00a7h\n");
        sb.append("\u00a7h GAME / INPUT\n");
        appendGameStateDiagnostics(sb);
        sb.append(String.format(" Input   DAS %d ms (%df)  ARR %d ms (%df)  SDF %dx",
                settings.getDasDelay(),
                FrameRate.framesForMillisCeil(settings.getDasDelay()),
                settings.getArrInterval(),
                settings.getArrInterval() == 0 ? 0
                        : FrameRate.framesForMillisRounded(settings.getArrInterval()),
                settings.getSoftDropFactor())).append('\n');
        sb.append(String.format(" Lock    delay %d ms  resets %d  preview %d",
                settings.getLockDelay(), settings.getMaxLockResets(),
                settings.getPreviewCount())).append('\n');
        sb.append(String.format(" Render  grid %.0f%%  board %.0f%%  ghost %.0f%% opacity",
                settings.getGridOpacity() * 100.0,
                settings.getBoardOpacity() * 100.0,
                settings.getGhostOpacity() * 100.0)).append('\n');

        appendRichAiPerfLine(sb, "AI-P1", mabBoardAiDriverA);
        appendRichAiPerfLine(sb, "AI-P2", mabBoardAiDriver);

        sb.append("\u00a7h\n");
        sb.append("\u00a7h SYSTEM\n");
        appendSystemDiagnostics(sb);
        sb.append(String.format(" AI cap  %d ms/search (-Dmab.ai.searchBudgetMs)",
                LIVE_AI_SEARCH_BUDGET_MS)).append('\n');
        sb.append("\u00a7l Legend  q=EDT wait, cb=loop callback, dirty=repaint requests, max=worst since F3 reset");
        return sb.toString();
    }

    private void appendRichHotspots(StringBuilder sb,
                                    FixedRateEdtLoop.TelemetrySnapshot renderStats,
                                    FixedRateEdtLoop.TelemetrySnapshot physicsStats,
                                    SwingPaintDiagnostics.Snapshot paintStats,
                                    long heapUsed,
                                    long heapMax) {
        sb.append("\u00a7h\n");
        sb.append("\u00a7h HOTSPOTS\n");
        int warnings = 0;
        double renderBudgetMs = ms(FrameRate.RENDER_INTERVAL_NS);
        double physicsBudgetMs = ms(FrameRate.PHYSICS_INTERVAL_NS);
        if (lastRenderFps > 0 && lastRenderFps < FrameRate.RENDER_FPS - 3) {
            warnings++;
            sb.append(String.format("\u00a7w FPS below target: %d/%d",
                    lastRenderFps, FrameRate.RENDER_FPS)).append('\n');
        }
        warnings += appendLoopWarnings(sb, "Render", renderStats, renderBudgetMs);
        warnings += appendLoopWarnings(sb, "Physics", physicsStats, physicsBudgetMs);
        if (ms(paintStats.maxPaintNs) > renderBudgetMs) {
            warnings++;
            sb.append(String.format("\u00a7w Paint max %.2f ms exceeds %.2f ms frame budget",
                    ms(paintStats.maxPaintNs), renderBudgetMs)).append('\n');
        }
        if (gcWindowDeltaTimeMs >= 25L) {
            warnings++;
            sb.append(String.format("\u00a7w GC recently used %,d ms over %.1fs",
                    gcWindowDeltaTimeMs,
                    gcWindowDurationNs <= 0L ? 0.0 : gcWindowDurationNs / 1_000_000_000.0))
                    .append('\n');
        }
        if (heapMax > 0L && percent(heapUsed, heapMax) >= 85.0) {
            warnings++;
            sb.append(String.format("\u00a7w Heap is %.0f%% of max", percent(heapUsed, heapMax)))
                    .append('\n');
        }
        if (warnings == 0) {
            sb.append(" No threshold warnings right now").append('\n');
        }
    }

    private int appendLoopWarnings(StringBuilder sb, String label,
                                   FixedRateEdtLoop.TelemetrySnapshot s,
                                   double budgetMs) {
        if (s == null) return 0;
        int warnings = 0;
        if (s.skippedTicks > 0L) {
            warnings++;
            sb.append(String.format("\u00a7w %s coalesced %,d stale tick(s)",
                    label, s.skippedTicks)).append('\n');
        }
        if (ms(s.maxQueueDelayNs) > budgetMs) {
            warnings++;
            sb.append(String.format("\u00a7w %s EDT queue max %.2f ms exceeds %.2f ms budget",
                    label, ms(s.maxQueueDelayNs), budgetMs)).append('\n');
        }
        if (ms(s.maxCallbackNs) > budgetMs) {
            warnings++;
            sb.append(String.format("\u00a7w %s callback max %.2f ms exceeds %.2f ms budget",
                    label, ms(s.maxCallbackNs), budgetMs)).append('\n');
        }
        return warnings;
    }

    private static String formatRichLoopTelemetry(String label,
                                                  FixedRateEdtLoop.TelemetrySnapshot s) {
        if (s == null) return String.format(" %-7s stopped", label);
        double intervalMs = ms(s.intervalNs);
        double avgCallbackMs = ms(s.averageCallbackNs());
        return String.format(" %-7s target %.2f  q %.2f/%.2f/%.2f  cb %.2f/%.2f/%.2f  duty %.0f%%  exec %,d skip %,d",
                label,
                intervalMs,
                ms(s.lastQueueDelayNs),
                ms(s.averageQueueDelayNs()),
                ms(s.maxQueueDelayNs),
                ms(s.lastCallbackNs),
                avgCallbackMs,
                ms(s.maxCallbackNs),
                intervalMs <= 0.0 ? 0.0 : (avgCallbackMs / intervalMs) * 100.0,
                s.executedTicks,
                s.skippedTicks);
    }

    private static void appendTimingLine(StringBuilder sb, String label,
                                         TimingSnapshot s, long budgetNs) {
        if (s == null || s.samples <= 0L) {
            sb.append(String.format(" %-7s no samples", label)).append('\n');
            return;
        }
        double avgMs = ms(s.averageNs());
        double budgetMs = ms(budgetNs);
        sb.append(String.format(" %-7s %.3f/%.3f/%.3f ms  duty %.1f%%  samples %,d",
                label,
                ms(s.lastNs),
                avgMs,
                ms(s.maxNs),
                budgetMs <= 0.0 ? 0.0 : (avgMs / budgetMs) * 100.0,
                s.samples)).append('\n');
    }

    private static void appendComponentPaintLines(StringBuilder sb,
                                                  SwingPaintDiagnostics.Snapshot paintStats) {
        int shown = 0;
        for (SwingPaintDiagnostics.ComponentSnapshot s : paintStats.componentPaints) {
            if (shown++ >= 6) break;
            sb.append(String.format(" %-8s %.3f/%.3f/%.3f ms  pass %,d",
                    trimLabel(s.name, 8),
                    ms(s.lastNs),
                    ms(s.averageNs()),
                    ms(s.maxNs),
                    s.passes)).append('\n');
        }
        if (shown == 0) {
            sb.append(" Components no paint samples yet").append('\n');
        }
    }

    private void appendGameStateDiagnostics(StringBuilder sb) {
        String piece = "none";
        String ghost = "n/a";
        try {
            var current = gameState.getCurrentPiece();
            if (current != null) {
                var pos = current.getBoardPosition();
                piece = current.getType() + " x=" + pos.getX()
                        + " y=" + pos.getY()
                        + " r=" + current.getRotationState();
                var ghostPiece = gameState.getGhostPiece();
                if (ghostPiece != null) {
                    ghost = String.valueOf(
                            ghostPiece.getBoardPosition().getY() - pos.getY());
                }
            }
        } catch (RuntimeException ignored) {
            piece = "unavailable";
        }
        sb.append(String.format(" Board   height=%d/%d  listeners=%d  spinPending=%b",
                gameState.getBoardHeight(), com.tetris.model.Board.VISIBLE_HEIGHT,
                gameState.getListenerCount(), gameState.isSpinCandidatePending())).append('\n');
        sb.append(String.format(" Piece   %s  hold=%s used=%b  ghostDrop=%s",
                piece,
                gameState.getHoldPiece() == null ? "none" : gameState.getHoldPiece(),
                gameState.isHoldUsed(),
                ghost)).append('\n');
    }

    private void appendRichAiPerfLine(StringBuilder sb, String label,
                                      MabBoardAiDriver driver) {
        if (driver == null) return;
        sb.append(String.format(" %s tick %.2f/%.2f/%.2f ms  search %.2f/%.2f/%.2f ms",
                label,
                ms(driver.getLastTickNs()),
                ms(driver.getAverageTickNs()),
                ms(driver.getMaxTickNs()),
                ms(driver.getLastSearchNs()),
                ms(driver.getAverageSearchNs()),
                ms(driver.getMaxSearchNs()))).append('\n');
        sb.append(String.format(" %s enabled=%b targetPPS=%.2f measuredPPS=%.2f calls %,d candidates %,d last d%d/c%d",
                label,
                driver.isEnabled(),
                driver.getTargetPps(),
                driver.getMeasuredPps(),
                driver.getSearchCalls(),
                driver.getSearchCandidates(),
                driver.getLastSearchDepthReached(),
                driver.getLastSearchCandidates())).append('\n');
    }

    private void appendSystemDiagnostics(StringBuilder sb) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        java.lang.management.OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();
        sb.append(String.format(" Threads live=%d daemon=%d peak=%d",
                threads.getThreadCount(),
                threads.getDaemonThreadCount(),
                threads.getPeakThreadCount())).append('\n');
        sb.append(String.format(" CPU     cores=%d  loadAvg=%s  process=%s  system=%s",
                Runtime.getRuntime().availableProcessors(),
                os.getSystemLoadAverage() < 0.0
                        ? "n/a"
                        : String.format("%.2f", os.getSystemLoadAverage()),
                cpuLoadString(os, true),
                cpuLoadString(os, false))).append('\n');
        sb.append(String.format(" JVM     %s  uptime %.1fs  %s/%s",
                System.getProperty("java.version", "?"),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0,
                System.getProperty("os.name", "?"),
                System.getProperty("os.arch", "?"))).append('\n');
    }

    @SuppressWarnings("deprecation")
    private static String cpuLoadString(java.lang.management.OperatingSystemMXBean os,
                                        boolean process) {
        if (!(os instanceof com.sun.management.OperatingSystemMXBean sunOs)) {
            return "n/a";
        }
        double value = process ? sunOs.getProcessCpuLoad() : sunOs.getSystemCpuLoad();
        return value < 0.0 ? "n/a" : String.format("%.0f%%", value * 100.0);
    }

    private void sampleGcWindow(long nowNs, long count, long timeMs) {
        if (gcWindowLastSampleNs < 0L) {
            gcWindowLastSampleNs = nowNs;
            gcWindowLastCount = count;
            gcWindowLastTimeMs = timeMs;
            return;
        }
        long elapsedNs = nowNs - gcWindowLastSampleNs;
        if (elapsedNs < 1_000_000_000L) return;
        gcWindowDeltaCount = Math.max(0L, count - gcWindowLastCount);
        gcWindowDeltaTimeMs = Math.max(0L, timeMs - gcWindowLastTimeMs);
        gcWindowDurationNs = elapsedNs;
        gcWindowLastSampleNs = nowNs;
        gcWindowLastCount = count;
        gcWindowLastTimeMs = timeMs;
    }

    private static double ratio(long num, long den) {
        return den <= 0L ? 0.0 : (double) num / den;
    }

    private static double percent(long used, long max) {
        return max <= 0L ? 0.0 : ((double) used / (double) max) * 100.0;
    }

    private static long mb(long bytes) {
        return Math.max(0L, bytes) / (1024L * 1024L);
    }

    private static String trimLabel(String s, int width) {
        if (s == null) return "";
        return s.length() <= width ? s : s.substring(0, width);
    }

    private static String stripRichPerfMarkup(String text) {
        if (text == null) return "";
        return text
                .replace("\u00c2\u00a7h ", "")
                .replace("\u00c2\u00a7h", "")
                .replace("\u00c2\u00a7w ", "WARN ")
                .replace("\u00c2\u00a7w", "WARN ")
                .replace("\u00c2\u00a7l ", "")
                .replace("\u00c2\u00a7l", "")
                .replace("\u00a7h ", "")
                .replace("\u00a7h", "")
                .replace("\u00a7w ", "WARN ")
                .replace("\u00a7w", "WARN ")
                .replace("\u00a7l ", "")
                .replace("\u00a7l", "");
    }

    private static final class TimingStats {
        private long samples;
        private long lastNs;
        private long maxNs;
        private long totalNs;

        void record(long elapsedNs) {
            long safeNs = Math.max(0L, elapsedNs);
            samples++;
            lastNs = safeNs;
            totalNs += safeNs;
            if (safeNs > maxNs) maxNs = safeNs;
        }

        TimingSnapshot snapshot() {
            return new TimingSnapshot(samples, lastNs, maxNs, totalNs);
        }

        void reset() {
            samples = 0L;
            lastNs = 0L;
            maxNs = 0L;
            totalNs = 0L;
        }
    }

    private static final class TimingSnapshot {
        final long samples;
        final long lastNs;
        final long maxNs;
        final long totalNs;

        TimingSnapshot(long samples, long lastNs, long maxNs, long totalNs) {
            this.samples = samples;
            this.lastNs = lastNs;
            this.maxNs = maxNs;
            this.totalNs = totalNs;
        }

        long averageNs() {
            return samples <= 0L ? 0L : totalNs / samples;
        }
    }

    private String buildLegacyPerfString() {
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
             + String.format(" Mode     %s", modeStr) + "\n"
             + String.format(" Music    %s", musicDirector.currentSoundtrackDisplay())
             + "\n" + buildDetailedPerfString();
    }

    private String buildDetailedPerfString() {
        FixedRateEdtLoop.TelemetrySnapshot renderStats =
                renderLoop == null ? null : renderLoop.snapshot();
        FixedRateEdtLoop.TelemetrySnapshot physicsStats =
                physicsLoop == null ? null : physicsLoop.snapshot();
        SwingPaintDiagnostics.Snapshot paintStats = SwingPaintDiagnostics.snapshot();

        StringBuilder sb = new StringBuilder(700);
        sb.append("Â§h\n");
        if (renderStats != null && renderStats.skippedTicks > 0L) {
            sb.append(String.format("Â§w Render   coalesced %,d stale tick(s)",
                    renderStats.skippedTicks)).append('\n');
        }
        sb.append(formatLoopTelemetry("Render", renderStats)).append('\n');
        sb.append(formatLoopTelemetry("Physics", physicsStats)).append('\n');
        sb.append(String.format(" Paint    last %.1f ms  avg %.1f  max %.1f  dirty %,d",
                ms(paintStats.lastPaintNs),
                ms(paintStats.averagePaintNs()),
                ms(paintStats.maxPaintNs),
                paintStats.dirtyRequests)).append('\n');
        sb.append(String.format(" Backdrop last %.3f ms  avg %.3f  max %.3f",
                ms(paintStats.lastBackdropPaintNs),
                ms(paintStats.averageBackdropPaintNs()),
                ms(paintStats.maxBackdropPaintNs))).append('\n');
        appendAiPerfLine(sb, "AI-P1", mabBoardAiDriverA);
        appendAiPerfLine(sb, "AI-P2", mabBoardAiDriver);
        sb.append("Â§h\n");
        sb.append(String.format(" GC       count %,d  time %,d ms",
                totalGcCount(), totalGcTimeMillis())).append('\n');
        sb.append(String.format(" Threads  %d  |  CPU cores %d",
                Thread.activeCount(), Runtime.getRuntime().availableProcessors())).append('\n');
        sb.append(String.format(" JVM      %s  %s/%s",
                System.getProperty("java.version", "?"),
                System.getProperty("os.name", "?"),
                System.getProperty("os.arch", "?"))).append('\n');
        sb.append(String.format(" AI cap   %d ms/search  (-Dmab.ai.searchBudgetMs)",
                LIVE_AI_SEARCH_BUDGET_MS));
        return sb.toString();
    }

    private void appendAiPerfLine(StringBuilder sb, String label, MabBoardAiDriver driver) {
        if (driver == null) return;
        sb.append(String.format(" %-7s tick %.1f/%.1f ms  search %.1f/%.1f/%.1f ms  d%d c%d",
                label,
                ms(driver.getLastTickNs()),
                ms(driver.getMaxTickNs()),
                ms(driver.getLastSearchNs()),
                ms(driver.getAverageSearchNs()),
                ms(driver.getMaxSearchNs()),
                driver.getLastSearchDepthReached(),
                driver.getLastSearchCandidates())).append('\n');
    }

    private static String formatLoopTelemetry(String label,
                                              FixedRateEdtLoop.TelemetrySnapshot s) {
        if (s == null) return String.format(" %-7s stopped", label);
        return String.format(" %-7s q %.1f/%.1f ms  cb %.1f/%.1f ms  exec %,d skip %,d",
                label,
                ms(s.lastQueueDelayNs),
                ms(s.maxQueueDelayNs),
                ms(s.lastCallbackNs),
                ms(s.maxCallbackNs),
                s.executedTicks,
                s.skippedTicks);
    }

    private static double ms(long ns) {
        return ns / 1_000_000.0;
    }

    private static String stripPerfMarkup(String text) {
        if (text == null) return "";
        return text
                .replace("Â§h ", "")
                .replace("Â§h", "")
                .replace("Â§w ", "WARN ")
                .replace("Â§w", "WARN ")
                .replace("Â§l ", "")
                .replace("Â§l", "");
    }

    private static long totalGcCount() {
        long total = 0L;
        for (java.lang.management.GarbageCollectorMXBean bean :
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0L) total += count;
        }
        return total;
    }

    private static long totalGcTimeMillis() {
        long total = 0L;
        for (java.lang.management.GarbageCollectorMXBean bean :
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0L) total += time;
        }
        return total;
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
            case "perf", "diag", "diagnostics" -> stripRichPerfMarkup(buildPerfString());
            case "gc"       -> { Runtime.getRuntime().gc(); yield "GC requested."; }
            case "debug"    -> {
                String lowerArg = arg.toLowerCase();
                if (lowerArg.equals("nuke") || lowerArg.startsWith("nuke ")
                        || lowerArg.equals("impact") || lowerArg.startsWith("impact ")) {
                    yield consoleImpactFx(stripFirstWord(arg));
                }
                gameState.setGravityFrozen(true);
                SwingUtilities.invokeLater(this::toggleCheatMenu);
                yield "Gravity frozen. Cheat menu opened.";
            }
            case "freeze"   -> { gameState.setGravityFrozen(true);  yield "Gravity frozen."; }
            case "unfreeze" -> { gameState.setGravityFrozen(false); yield "Gravity unfrozen."; }
            case "impactfx", "nukefx", "nukeimpact" -> consoleImpactFx(arg);
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

    private String consoleImpactFx(String arg) {
        if (mabBattleShell == null || mabBattleShell.getVisualEffectsLayer() == null) {
            return "Nuke impact VFX is only available in the MAB battle shell.";
        }
        String cleaned = arg == null ? "" : arg.trim().toLowerCase();
        if (cleaned.isEmpty()) {
            return "Usage: impactfx p1 | impactfx p2 | impactfx both [type]";
        }
        String[] words = cleaned.split("\\s+", 2);
        String target = words[0];
        String variant = words.length > 1 ? words[1].trim() : "";
        var layer = mabBattleShell.getVisualEffectsLayer();
        Runnable play;
        String label;
        switch (target) {
            case "1", "p1", "player1", "player_1", "a", "player_a" -> {
                play = () -> layer.playDebugNukeImpact(ParticipantId.PLAYER_A, variant);
                label = "P1";
            }
            case "2", "p2", "player2", "player_2", "b", "player_b" -> {
                play = () -> layer.playDebugNukeImpact(ParticipantId.PLAYER_B, variant);
                label = "P2";
            }
            case "both", "all" -> {
                play = () -> layer.playDebugNukeImpactBoth(variant);
                label = "P1 and P2";
            }
            default -> {
                return "Usage: impactfx p1 | impactfx p2 | impactfx both [type]";
            }
        }
        if (SwingUtilities.isEventDispatchThread()) play.run();
        else SwingUtilities.invokeLater(play);
        return "Playing nuke impact VFX on " + label
                + (variant.isBlank() ? "." : " (" + variant + ").");
    }

    private static String stripFirstWord(String text) {
        if (text == null) return "";
        String s = text.trim();
        int i = s.indexOf(' ');
        return i < 0 ? "" : s.substring(i + 1).trim();
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
