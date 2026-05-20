package com.tetris.mab;

import com.tetris.events.GameEventListener;
import com.tetris.mab.action.ActionClearToken;
import com.tetris.mab.action.ActionCodeDefinition;
import com.tetris.mab.action.ActionCodeDifficultyRules;
import com.tetris.mab.action.ActionCodeManager;
import com.tetris.mab.action.ActionCodeMatchMode;
import com.tetris.mab.action.ActionCodeRegistry;
import com.tetris.mab.action.ActionCodeResult;
import com.tetris.mab.action.ActionCodeTokenRequirement;
import com.tetris.mab.action.ActionType;
import com.tetris.mab.defense.CivilDefenseMitigation;
import com.tetris.mab.defense.ImpactGracePolicy;
import com.tetris.mab.impact.ImpactResolutionStatus;
import com.tetris.mab.impact.ImpactResolver;
import com.tetris.mab.impact.ImpactResult;
import com.tetris.mab.impact.ImpactWaveState;
import com.tetris.mab.impact.RadiationGarbagePatternGenerator;
import com.tetris.mab.impact.RadiationLevel;
import com.tetris.mab.intel.RadarScanCalculator;
import com.tetris.mab.intel.RadarScanResult;
import com.tetris.mab.intel.RadarScanType;
import com.tetris.mab.intel.StaleIntelSnapshot;
import com.tetris.mab.decoy.ActiveDecoyState;
import com.tetris.mab.decoy.DecoyDefinition;
import com.tetris.mab.decoy.DecoyRegistry;
import com.tetris.mab.decoy.DecoyResolutionResult;
import com.tetris.mab.decoy.DecoyResolver;
import com.tetris.mab.decoy.DecoyType;
import com.tetris.mab.intercept.InterceptDefinition;
import com.tetris.mab.intercept.InterceptOutcome;
import com.tetris.mab.intercept.InterceptRegistry;
import com.tetris.mab.intercept.InterceptResolver;
import com.tetris.mab.intercept.InterceptResult;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeSizeCategory;
import com.tetris.mab.nuke.BuilderNukeSpec;
import com.tetris.mab.nuke.NukeBuilderAdapter;
import com.tetris.mab.upgrade.NukeRedesignRetentionRules;
import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeChoiceSet;
import com.tetris.mab.upgrade.UpgradeDefinition;
import com.tetris.mab.upgrade.UpgradeRegistry;
import com.tetris.mab.upgrade.UpgradeType;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineAvailability;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineType;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineUseResult;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;
import com.tetris.model.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Match coordinator for the "Mutually Assured Blocks" strategy layer.
 *
 * <p>Sits ABOVE the existing single-player Tetris engine. Each
 * participant retains its own untouched {@link GameState}; this class
 * subscribes to engine events through the Step-1 {@link GameEventListener}
 * facade and aggregates them into the strategic state.
 *
 * <p>Step 2 only wires the structural foundation:
 * <ul>
 *   <li>two {@link ParticipantState}s,</li>
 *   <li>shared {@link DefconState},</li>
 *   <li>{@link PieceTimerManager} for piece-driven countdowns,</li>
 *   <li>provisional escalation / nuke-charge bookkeeping,</li>
 *   <li>top-out → winner attribution,</li>
 *   <li>readable debug snapshot.</li>
 * </ul>
 * Real nuke designs, launches, impacts, and AI driving are intentionally
 * not implemented yet.
 */
public class MutuallyAssuredBlocksMatch {

    /** Maximum number of recent log entries kept for the debug snapshot. */
    private static final int LOG_RING_SIZE = 200;

    private final MatchMode matchMode;
    private final MatchDifficulty difficulty;
    private final ParticipantState playerA;
    private final ParticipantState playerB;
    private final DefconState defconState = new DefconState();
    private final PieceTimerManager pieceTimerManager = new PieceTimerManager();
    private final ActionCodeRegistry actionCodeRegistry = ActionCodeRegistry.createDefault();
    private final List<MatchEventLogEntry> eventLog = new ArrayList<>();
    private final List<ImpactWaveState> pendingImpactWaves = new ArrayList<>();
    private final ImpactResolver impactResolver = new ImpactResolver();
    private final RadiationGarbagePatternGenerator radiationGenerator = new RadiationGarbagePatternGenerator();
    private final InterceptRegistry interceptRegistry = InterceptRegistry.createDefault();
    private final InterceptResolver interceptResolver = new InterceptResolver();
    private final ImpactGracePolicy impactGracePolicy = new ImpactGracePolicy();
    private final UpgradeRegistry upgradeRegistry = UpgradeRegistry.createDefault();
    private final NukeBuilderAdapter nukeBuilderAdapter = new NukeBuilderAdapter();
    private final RadarScanCalculator radarScanCalculator = new RadarScanCalculator();
    private long radarScanSequence;
    private final DecoyRegistry decoyRegistry = DecoyRegistry.createDefault();
    private final DecoyResolver decoyResolver = new DecoyResolver();
    private long decoySequence;
    /** DEFCON levels (4/3/2/1) for which a global upgrade-point bonus has been awarded. */
    private final Set<Integer> awardedDefconLevels = new HashSet<>();
    /** Lines-cleared milestone (in 20-line blocks) already rewarded per participant. */
    private int playerAUpgradeLineMilestone;
    private int playerBUpgradeLineMilestone;
    private static final int LINE_CLEAR_UPGRADE_INTERVAL = 20;
    /** Default civil-defense shield duration applied by the action code. */
    private static final int CIVIL_DEFENSE_SHIELD_PIECES = 12;
    private long impactSequenceNumber;

    private MatchPhase currentPhase = MatchPhase.SETUP;
    private boolean paused;
    private ParticipantId winner;
    private long eventSequenceNumber;
    private int launchCounter;

    private final GameEventListener listenerA;
    private final GameEventListener listenerB;
    /** Step 21 \u2014 shared deterministic 7-bag for MAB modes; null for sims that
     *  build the match via the legacy {@link #createLocalPvp} factory. */
    private com.tetris.mab.pieces.MabSharedPieceSequence sharedPieceSequence;

    /** Step 24 \u2014 deterministic seed used by the level-up draft RNG.
     *  Set by the createXxxShared factories; defaults to 0 otherwise. */
    private long matchSeed;
    /** Step 24 \u2014 level-up draft manager. Lazy: only constructed
     *  on first {@link #getUpgradeDraftManager()} call so existing
     *  unit tests that don't touch upgrades are unaffected. */
    private com.tetris.mab.upgrade.draft.MabUpgradeDraftManager upgradeDraftManager;
    private final com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry upgradeDraftRegistry =
            new com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry();
    private long matchStartedAtMs;
    private long matchEndedAtMs;

    // ─────────────────────────── Construction ────────────────────

    private MutuallyAssuredBlocksMatch(MatchMode mode, MatchDifficulty difficulty,
                                       GameState gameA, GameState gameB) {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (difficulty == null) throw new IllegalArgumentException("difficulty");
        if (gameA == null || gameB == null) throw new IllegalArgumentException("gameState");
        if (gameA == gameB) throw new IllegalArgumentException("playerA and playerB must use distinct GameState instances");

        this.matchMode = mode;
        this.difficulty = difficulty;
        this.playerA = new ParticipantState(ParticipantId.PLAYER_A, gameA);
        this.playerB = new ParticipantState(ParticipantId.PLAYER_B, gameB);

        this.listenerA = buildListener(playerA);
        this.listenerB = buildListener(playerB);
        gameA.addListener(listenerA);
        gameB.addListener(listenerB);
        applyDefconGravityToBoards();
    }

    /** Local PvP match between two human-driven {@link GameState} instances. */
    public static MutuallyAssuredBlocksMatch createLocalPvp(GameState gameA, GameState gameB,
                                                            MatchDifficulty difficulty) {
        return new MutuallyAssuredBlocksMatch(MatchMode.PVP_LOCAL, difficulty, gameA, gameB);
    }

    /**
     * Step 21 \u2014 MAB local PvP with a shared deterministic 7-bag piece
     * sequence. Both players consume the SAME ordered sequence (Player A's
     * piece #n equals Player B's piece #n) using independent cursors.
     * Offline-only; the seed is generated locally.
     */
    public static MutuallyAssuredBlocksMatch createLocalPvpShared(
            GameState gameA, GameState gameB, MatchDifficulty difficulty, long seed) {
        com.tetris.mab.pieces.MabSharedPieceSequence shared =
                new com.tetris.mab.pieces.MabSharedPieceSequence(seed);
        gameA.replacePieceSourceForMabSharedSequence(shared.newStream());
        gameB.replacePieceSourceForMabSharedSequence(shared.newStream());
        MutuallyAssuredBlocksMatch m = new MutuallyAssuredBlocksMatch(
                MatchMode.PVP_LOCAL, difficulty, gameA, gameB);
        m.sharedPieceSequence = shared;
        m.matchSeed = seed;
        return m;
    }

    /** Step 21 \u2014 same as {@link #createLocalPvpShared} but for PvE mode. */
    public static MutuallyAssuredBlocksMatch createPveShared(
            GameState humanGame, GameState aiGame, MatchDifficulty difficulty, long seed) {
        com.tetris.mab.pieces.MabSharedPieceSequence shared =
                new com.tetris.mab.pieces.MabSharedPieceSequence(seed);
        humanGame.replacePieceSourceForMabSharedSequence(shared.newStream());
        aiGame.replacePieceSourceForMabSharedSequence(shared.newStream());
        MutuallyAssuredBlocksMatch m = new MutuallyAssuredBlocksMatch(
                MatchMode.PVE, difficulty, humanGame, aiGame);
        m.sharedPieceSequence = shared;
        m.matchSeed = seed;
        return m;
    }

    /**
     * PvE match. Step 2 just labels the mode; the AI driver itself is not
     * implemented yet — both GameStates still need to be ticked by callers
     * (e.g. an AI controller fed into {@code aiGame}) for anything to happen.
     */
    public static MutuallyAssuredBlocksMatch createPve(GameState humanGame, GameState aiGame,
                                                       MatchDifficulty difficulty) {
        return new MutuallyAssuredBlocksMatch(MatchMode.PVE, difficulty, humanGame, aiGame);
    }

    // ─────────────────────────── Lifecycle ───────────────────────

    /**
     * Transitions SETUP → ACTIVE. Idempotent and phase-safe.
     * Calls in any other phase are logged but do not mutate match state.
     */
    public void startMatch() {
        switch (currentPhase) {
            case SETUP -> {
                currentPhase = MatchPhase.ACTIVE;
                matchStartedAtMs = System.currentTimeMillis();
                matchEndedAtMs = 0L;
                log("MATCH_STARTED", null, "mode=" + matchMode + " difficulty=" + difficulty, null);
            }
            case ACTIVE ->
                log("START_IGNORED_ALREADY_ACTIVE", null, "already active", null);
            case UPGRADE_PAUSE ->
                log("START_IGNORED_UPGRADE_PAUSE", null, "in upgrade pause", null);
            case GAME_OVER ->
                log("START_IGNORED_GAME_OVER", null, "match already finished", null);
        }
    }

    /** Pauses both engine instances and marks the match paused. Idempotent and phase-safe. */
    public void pauseMatch(String reason) {
        if (currentPhase == MatchPhase.GAME_OVER) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            log("PAUSE_IGNORED_GAME_OVER", null, "match already finished", meta);
            return;
        }
        if (paused) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            log("PAUSE_IGNORED_ALREADY_PAUSED", null, "match already paused", meta);
            return;
        }
        paused = true;
        playerA.getGameState().pause(reason == null ? "match" : reason);
        playerB.getGameState().pause(reason == null ? "match" : reason);
        log("MATCH_PAUSED", null, reason == null ? "" : reason, null);
    }

    /** Resumes both engine instances and marks the match unpaused. Idempotent and phase-safe. */
    public void resumeMatch(String reason) {
        if (currentPhase == MatchPhase.GAME_OVER) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            log("RESUME_IGNORED_GAME_OVER", null, "match already finished", meta);
            return;
        }
        if (!paused) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            log("RESUME_IGNORED_NOT_PAUSED", null, "match was not paused", meta);
            return;
        }
        paused = false;
        playerA.getGameState().resume(reason == null ? "match" : reason);
        playerB.getGameState().resume(reason == null ? "match" : reason);
        log("MATCH_RESUMED", null, reason == null ? "" : reason, null);
    }

    /**
     * Enters the upgrade-pause phase. Allowed only from {@link MatchPhase#ACTIVE}.
     * Both engines are paused while in this phase.
     */
    public void enterUpgradePause(String reason) {
        if (currentPhase != MatchPhase.ACTIVE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            meta.put("phase", currentPhase);
            log("UPGRADE_PAUSE_IGNORED", null, "can only enter from ACTIVE", meta);
            return;
        }
        currentPhase = MatchPhase.UPGRADE_PAUSE;
        paused = true;
        playerA.getGameState().pause(reason == null ? "upgrade" : reason);
        playerB.getGameState().pause(reason == null ? "upgrade" : reason);
        log("UPGRADE_PAUSE_ENTERED", null, reason == null ? "" : reason, null);
    }

    /**
     * Exits the upgrade-pause phase back to {@link MatchPhase#ACTIVE}.
     * Allowed only from {@link MatchPhase#UPGRADE_PAUSE}.
     */
    public void exitUpgradePause(String reason) {
        if (currentPhase != MatchPhase.UPGRADE_PAUSE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reason", reason);
            meta.put("phase", currentPhase);
            log("UPGRADE_PAUSE_EXIT_IGNORED", null, "not in UPGRADE_PAUSE", meta);
            return;
        }
        currentPhase = MatchPhase.ACTIVE;
        paused = false;
        playerA.getGameState().resume(reason == null ? "upgrade" : reason);
        playerB.getGameState().resume(reason == null ? "upgrade" : reason);
        log("UPGRADE_PAUSE_EXITED", null, reason == null ? "" : reason, null);
    }

    /** Detaches listeners. Safe to call after a match has ended. */
    public void shutdown() {
        playerA.getGameState().removeListener(listenerA);
        playerB.getGameState().removeListener(listenerB);
    }

    /**
     * @return true iff gameplay events are allowed to mutate strategic state
     *         (i.e. the match is ACTIVE, not paused, and has no winner).
     */
    private boolean isGameplayMutationAllowed() {
        return currentPhase == MatchPhase.ACTIVE && !paused && winner == null;
    }

    /** Populates an ignored-event metadata map with the standard guard context. */
    private Map<String, Object> ignoredMeta(ParticipantId pid) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("phase", currentPhase);
        meta.put("paused", paused);
        meta.put("winner", winner);
        meta.put("participant", pid);
        return meta;
    }

    // ─────────────────────────── Accessors ───────────────────────

    public MatchMode getMatchMode() { return matchMode; }
    public MatchDifficulty getDifficulty() { return difficulty; }
    public ParticipantState getParticipant(ParticipantId id) {
        if (id == null) return null;
        return id == ParticipantId.PLAYER_A ? playerA : playerB;
    }
    public ParticipantState getOpponent(ParticipantId id) {
        return getParticipant(id.opponent());
    }
    public DefconState getDefconState() { return defconState; }
    public PieceTimerManager getPieceTimerManager() { return pieceTimerManager; }
    public ActionCodeRegistry getActionCodeRegistry() { return actionCodeRegistry; }
    public MatchPhase getCurrentPhase() { return currentPhase; }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return currentPhase == MatchPhase.GAME_OVER; }
    public ParticipantId getWinner() { return winner; }
    public long getElapsedMatchMillis() {
        if (matchStartedAtMs <= 0L) return 0L;
        long end = matchEndedAtMs > 0L ? matchEndedAtMs : System.currentTimeMillis();
        return Math.max(0L, end - matchStartedAtMs);
    }
    public List<MatchEventLogEntry> getEventLog() { return Collections.unmodifiableList(eventLog); }

    public List<MabActiveDoctrineAvailability> getActiveDoctrineOptions(ParticipantId id) {
        List<MabActiveDoctrineAvailability> out = new ArrayList<>();
        out.add(getActiveDoctrineAvailability(id, MabActiveDoctrineType.MANUAL_OVERRIDE));
        out.add(getActiveDoctrineAvailability(id, MabActiveDoctrineType.EMP));
        return Collections.unmodifiableList(out);
    }

    public MabActiveDoctrineAvailability getActiveDoctrineAvailability(
            ParticipantId id, MabActiveDoctrineType type) {
        ParticipantState p = getParticipant(id);
        if (p == null || type == null) {
            return new MabActiveDoctrineAvailability(type, false, false,
                    "ACTIVE LOCKED", "INVALID STATION", type == null ? 0 : type.chargeCost());
        }
        boolean owned = p.getUpgradeInventory().hasTag(type.effectTag());
        if (!owned) {
            return new MabActiveDoctrineAvailability(type, false, false,
                    "ACTIVE LOCKED", "NOT LOADED", type.chargeCost());
        }
        if (!isGameplayMutationAllowed()) {
            return new MabActiveDoctrineAvailability(type, true, false,
                    "ACTIVE LOCKED", "MATCH NOT ACTIVE", type.chargeCost());
        }
        return switch (type) {
            case MANUAL_OVERRIDE -> manualOverrideAvailability(p);
            case EMP -> empAvailability(p);
        };
    }

    public boolean hasAnyActiveDoctrineOwned(ParticipantId id) {
        ParticipantState p = getParticipant(id);
        if (p == null) return false;
        for (MabActiveDoctrineType t : MabActiveDoctrineType.values()) {
            if (p.getUpgradeInventory().hasTag(t.effectTag())) return true;
        }
        return false;
    }

    public boolean hasAnyActiveDoctrineAvailable(ParticipantId id) {
        for (MabActiveDoctrineAvailability a : getActiveDoctrineOptions(id)) {
            if (a.available()) return true;
        }
        return false;
    }

    public MabActiveDoctrineUseResult useActiveDoctrine(
            ParticipantId id, MabActiveDoctrineType type) {
        MabActiveDoctrineAvailability availability = getActiveDoctrineAvailability(id, type);
        if (!availability.available()) {
            return new MabActiveDoctrineUseResult(type, false,
                    availability.displayName() + " LOCKED",
                    availability.reason(), null, null, 0);
        }
        ParticipantState p = getParticipant(id);
        return switch (type) {
            case MANUAL_OVERRIDE -> useManualOverride(p);
            case EMP -> useEmp(p);
        };
    }

    public int countActiveOutgoingLaunches(ParticipantId id) {
        ParticipantState p = getParticipant(id);
        if (p == null) return 0;
        int n = 0;
        for (ActiveLaunchState l : p.getActiveLaunches()) {
            if (isLiveLaunchForActiveDoctrine(l)) n++;
        }
        return n;
    }

    public int getEarliestIncomingWarningPieces(ParticipantId id) {
        ParticipantState p = getParticipant(id);
        if (p == null) return -1;
        int best = Integer.MAX_VALUE;
        for (IncomingThreatState t : p.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.WARNING_ACTIVE) {
                best = Math.min(best, t.getWarningPiecesRemaining());
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private MabActiveDoctrineAvailability manualOverrideAvailability(ParticipantState p) {
        var state = p.getSimplifiedState();
        if (!state.nukeReady() || !p.getNukeBuildState().isArmed()) {
            return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, false,
                    "ACTIVE LOCKED", "NOT READY");
        }
        if (state.shouldFireTetrisLaunch() || state.shouldFireSpinLaunch()) {
            return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, false,
                    "ACTIVE LOCKED", "ROUTE COMPLETE");
        }
        if (hasLiveOutgoingLaunch(p)) {
            return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, false,
                    "COOLDOWN", "LAUNCH ACTIVE");
        }
        if (p.getActiveDoctrineState().isManualOverrideUsedThisCycle()) {
            return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, false,
                    "USED", "USED THIS CYCLE");
        }
        if (getOpponent(p.getId()) == null) {
            return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, false,
                    "ACTIVE LOCKED", "NO OPPONENT");
        }
        return activeRow(MabActiveDoctrineType.MANUAL_OVERRIDE, true, true,
                "ACTIVE READY", "HALF-POWER LAUNCH");
    }

    private MabActiveDoctrineAvailability empAvailability(ParticipantState p) {
        if (p.getActiveDoctrineState().isEmpUsedThisMatch()) {
            return activeRow(MabActiveDoctrineType.EMP, true, false,
                    "ONCE PER MATCH USED", "USED");
        }
        if (p.getNukeBuildState().getCurrentBuildCharge() < MabActiveDoctrineType.EMP.chargeCost()) {
            return activeRow(MabActiveDoctrineType.EMP, true, false,
                    "ACTIVE LOCKED", "INSUFFICIENT CHARGE");
        }
        if (findEmpLaunchTarget(p) == null) {
            return activeRow(MabActiveDoctrineType.EMP, true, false,
                    "ACTIVE LOCKED", "NO TARGET");
        }
        return activeRow(MabActiveDoctrineType.EMP, true, true,
                "ACTIVE READY", "LAUNCH CANCEL");
    }

    private MabActiveDoctrineAvailability activeRow(MabActiveDoctrineType type,
                                                     boolean owned,
                                                     boolean available,
                                                     String status,
                                                     String reason) {
        return new MabActiveDoctrineAvailability(type, owned, available,
                status, reason, type.chargeCost());
    }

    private MabActiveDoctrineUseResult useManualOverride(ParticipantState p) {
        String launchId = authorizeLaunchInternal(p, "manual_override",
                "active-doctrine-manual-override");
        if (launchId == null) {
            return new MabActiveDoctrineUseResult(MabActiveDoctrineType.MANUAL_OVERRIDE,
                    false, "MANUAL OVERRIDE LOCKED", "LAUNCH REJECTED",
                    null, null, 0);
        }
        ActiveLaunchState launch = findLaunchById(launchId);
        if (launch != null) launch.markManualOverride();

        var strategic = p.getSimplifiedState();
        strategic.onLaunchFired(true);
        p.setTetrisDoctrineUsedThisCycle(false);
        p.setSpinsThisCycle(0);
        p.setManualOverrideReadyThisCycle(false);
        p.getActiveDoctrineState().setManualOverrideUsedThisCycle(true);
        p.getActiveDoctrineState().recordUse(MabActiveDoctrineType.MANUAL_OVERRIDE,
                "HALF-POWER LAUNCH");

        int refund = MabUpgradeEffectResolver.onLaunchFiredChargeRefund(p.getUpgradeInventory());
        if (refund > 0) {
            p.getNukeBuildState().addCharge(refund);
            enforceCap(p);
        }
        strategic.syncFromNuke(
                p.getNukeBuildState().getCurrentBuildCharge(),
                Math.max(1, p.getNukeBuildState().getEffectiveBuildChargeRequired()));

        log("MAB_ACTIVE_DOCTRINE_USED", p.getId(),
                "MANUAL OVERRIDE FIRED",
                metaOf("doctrine", "manual_override",
                        "launchId", launchId,
                        "effect", "half_power_launch"));
        return new MabActiveDoctrineUseResult(MabActiveDoctrineType.MANUAL_OVERRIDE,
                true, "MANUAL OVERRIDE FIRED", "HALF-POWER LAUNCH",
                launchId, null, 0);
    }

    private MabActiveDoctrineUseResult useEmp(ParticipantState p) {
        ActiveLaunchState target = findEmpLaunchTarget(p);
        if (target == null) {
            return new MabActiveDoctrineUseResult(MabActiveDoctrineType.EMP,
                    false, "EMP LOCKED", "NO TARGET", null, null, 0);
        }
        IncomingThreatState threat = findThreatByLaunchId(target.getLaunchId());
        p.getNukeBuildState().reduceCharge(MabActiveDoctrineType.EMP.chargeCost());
        p.getSimplifiedState().syncFromNuke(
                p.getNukeBuildState().getCurrentBuildCharge(),
                Math.max(1, p.getNukeBuildState().getEffectiveBuildChargeRequired()));

        if (target.getLaunchTimerId() != null) {
            pieceTimerManager.cancelTimer(target.getLaunchTimerId());
        }
        if (target.getFlightTimerId() != null) {
            pieceTimerManager.cancelTimer(target.getFlightTimerId());
        }
        target.markCancelled();
        if (threat != null) threat.markCancelled();
        p.getActiveDoctrineState().setEmpUsedThisMatch(true);
        p.getActiveDoctrineState().recordUse(MabActiveDoctrineType.EMP,
                "LAUNCH CANCELLED");

        log("MAB_ACTIVE_DOCTRINE_USED", p.getId(),
                "EMP DISCHARGE",
                metaOf("doctrine", "emp",
                        "launchId", target.getLaunchId(),
                        "threatId", threat == null ? null : threat.getThreatId(),
                        "chargeSpent", MabActiveDoctrineType.EMP.chargeCost(),
                        "effect", "launch_cancelled"));
        pruneCompletedThreats();
        return new MabActiveDoctrineUseResult(MabActiveDoctrineType.EMP,
                true, "EMP DISCHARGE", "LAUNCH CANCELLED",
                target.getLaunchId(),
                threat == null ? null : threat.getThreatId(),
                MabActiveDoctrineType.EMP.chargeCost());
    }

    private boolean hasLiveOutgoingLaunch(ParticipantState p) {
        if (p == null) return false;
        for (ActiveLaunchState l : p.getActiveLaunches()) {
            if (isLiveLaunchForActiveDoctrine(l)) return true;
        }
        return false;
    }

    private boolean isLiveLaunchForActiveDoctrine(ActiveLaunchState l) {
        if (l == null) return false;
        return l.getPhase() == LaunchPhase.AUTHORIZED
                || l.getPhase() == LaunchPhase.COUNTDOWN
                || l.getPhase() == LaunchPhase.IN_FLIGHT
                || l.getPhase() == LaunchPhase.IMPACT_READY;
    }

    private ActiveLaunchState findEmpLaunchTarget(ParticipantState user) {
        if (user == null) return null;
        for (IncomingThreatState t : user.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.WARNING_ACTIVE) {
                ActiveLaunchState launch = findLaunchById(t.getLaunchId());
                if (launch != null && launch.getPhase() == LaunchPhase.IN_FLIGHT) {
                    return launch;
                }
            }
        }
        ParticipantState opponent = getOpponent(user.getId());
        if (opponent == null) return null;
        for (ActiveLaunchState l : opponent.getActiveLaunches()) {
            if (l.getPhase() == LaunchPhase.COUNTDOWN
                    || l.getPhase() == LaunchPhase.IN_FLIGHT) {
                return l;
            }
        }
        return null;
    }

    // ───────────── Step 24: level-up upgrade draft ─────────────

    /** Lazy accessor for the level-up draft manager. */
    public com.tetris.mab.upgrade.draft.MabUpgradeDraftManager getUpgradeDraftManager() {
        if (upgradeDraftManager == null) {
            upgradeDraftManager = new com.tetris.mab.upgrade.draft.MabUpgradeDraftManager(
                    upgradeDraftRegistry, matchSeed);
        }
        return upgradeDraftManager;
    }

    /**
     * Step 24 \u2014 polls participants for level-ups, immediately
     * applies AI picks, and returns the next pending human draft (or
     * {@code null}). Caller is responsible for opening the overlay and
     * the upgrade pause when a non-null draft is returned.
     */
    public com.tetris.mab.upgrade.draft.MabUpgradeDraft tickUpgradeDrafts() {
        if (currentPhase == MatchPhase.GAME_OVER) return null;
        var mgr = getUpgradeDraftManager();
        boolean localPvp = matchMode == MatchMode.PVP_LOCAL;
        var aiDrafts = mgr.checkLevelUps(playerA, true, playerB, localPvp);
        for (var d : aiDrafts) {
            var pick = com.tetris.mab.upgrade.draft.MabAiUpgradePicker.pick(d, difficulty, matchSeed);
            if (pick == null) continue;
            mgr.applyDirect(playerB, pick);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("level", d.getLevel());
            meta.put("cardId", pick.getId());
            meta.put("category", pick.getCategory().name());
            meta.put("rarity", pick.getRarity().name());
            log("MAB_AI_UPGRADE_SELECTED", playerB.getId(),
                    "AI picked " + pick.getDisplayName(), meta);
        }
        return mgr.pollNextHumanDraft();
    }

    /**
     * Step 24 \u2014 commits a human player's selection from the active
     * draft. Safe to call even if no draft is active (no-op).
     */
    public void applyHumanUpgradeChoice(com.tetris.mab.upgrade.draft.MabUpgradeCard chosen) {
        if (chosen == null) return;
        var mgr = getUpgradeDraftManager();
        if (!mgr.hasActiveHumanDraft()) return;
        var active = mgr.getActiveHumanDraft();
        ParticipantState owner = active == null ? playerA : getParticipant(active.getParticipantId());
        var card = mgr.applyHumanSelection(owner, chosen);
        if (card == null) return;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardId", card.getId());
        meta.put("category", card.getCategory().name());
        meta.put("rarity", card.getRarity().name());
        log("MAB_UPGRADE_SELECTED", owner == null ? playerA.getId() : owner.getId(),
                "Player picked " + card.getDisplayName(), meta);
    }

    public long getMatchSeed() { return matchSeed; }
    public boolean hasSharedPieceSequence() { return sharedPieceSequence != null; }

    /**
     * Step 12: returns the most-recent {@code maxEntries} log entries
     * as an unmodifiable list (oldest-first within the returned slice).
     * The internal mutable list is never exposed. {@code maxEntries <= 0}
     * returns an empty list.
     */
    public List<MatchEventLogEntry> getRecentEvents(int maxEntries) {
        if (maxEntries <= 0) return Collections.emptyList();
        int size = eventLog.size();
        int from = Math.max(0, size - maxEntries);
        return Collections.unmodifiableList(new ArrayList<>(eventLog.subList(from, size)));
    }

    // ─────────────────────────── Debug ───────────────────────────

    public MatchDebugSnapshot toDebugSnapshot() {
        return new MatchDebugSnapshot(
                matchMode, difficulty, currentPhase, paused, winner,
                defconState.getLevel(), defconState.getEscalationMeter(),
                MatchDebugSnapshot.ParticipantSummary.of(playerA, countPendingWavesFor(playerA.getId())),
                MatchDebugSnapshot.ParticipantSummary.of(playerB, countPendingWavesFor(playerB.getId())),
                pieceTimerManager.getActiveTimers().size(),
                eventLog.size());
    }

    private int countPendingWavesFor(ParticipantId defender) {
        int n = 0;
        for (ImpactWaveState w : pendingImpactWaves) {
            if (!w.isApplied() && w.getDefender() == defender) n++;
        }
        return n;
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Mutually Assured Blocks ===\n");
        sb.append("mode=").append(matchMode)
          .append(" difficulty=").append(difficulty)
          .append(" phase=").append(currentPhase)
          .append(paused ? " [PAUSED]" : "")
          .append(winner != null ? " winner=" + winner : "")
          .append('\n');
        sb.append(defconState.toDebugString()).append('\n');
        sb.append(playerA.toDebugString()).append('\n');
        sb.append(playerB.toDebugString()).append('\n');
        sb.append("activeTimers=").append(pieceTimerManager.getActiveTimers().size())
          .append(" loggedEvents=").append(eventLog.size())
          .append('\n');
        return sb.toString();
    }

    // ─────────────────────────── Event routing ───────────────────

    private GameEventListener buildListener(ParticipantState participant) {
        return new GameEventListener() {
            @Override
            public void onPieceLocked(PieceLockedEvent e) {
                if (!isGameplayMutationAllowed()) {
                    Map<String, Object> meta = ignoredMeta(participant.getId());
                    meta.put("piece", e.type());
                    log("IGNORED_PIECE_LOCKED", participant.getId(), e.type().toString(), meta);
                    return;
                }
                advanceStrategicPieceClockFor(participant, "engine");
                participant.refreshMaxStackHeight();
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("piece", e.type());
                meta.put("totalPieces", participant.getPiecesLocked());
                log("PIECE_LOCKED", participant.getId(), e.type().toString(), meta);
            }

            @Override
            public void onPieceLockedDetailed(com.tetris.events.PieceLockResult r) {
                // Step 22 \u2014 unified per-lock router. Handles 0-line
                // spins (charge / launch progress / intercept), so the
                // line-clear listener does NOT need to duplicate this.
                if (!isGameplayMutationAllowed()) return;
                processSimplifiedClearForLockResult(participant, r);
            }

            @Override
            public void onLinesCleared(LinesClearedEvent e) {
                if (!isGameplayMutationAllowed()) {
                    Map<String, Object> meta = ignoredMeta(participant.getId());
                    meta.put("count", e.count());
                    log("IGNORED_LINES_CLEARED", participant.getId(),
                            e.count() + " lines", meta);
                    return;
                }
                participant.addLinesCleared(e.count());
                participant.refreshMaxStackHeight();

                // Step 22: simplified MAB routing has moved to
                // onPieceLockedDetailed so 0-line spins also count.
                // This listener now only updates DEFCON escalation,
                // upgrade-point milestones, and event logging.

                int escalation = lineClearEscalationFor(e);
                DefconChangeResult dcr = (escalation > 0)
                        ? defconState.addEscalation(escalation, "lines:" + e.count())
                        : null;
                handleDefconChange(dcr);

                // Step 9: line-clear upgrade-point milestones (every 20 lines).
                maybeAwardLineClearUpgradePoints(participant);

                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("count", e.count());
                meta.put("tetris", e.tetris());
                meta.put("tSpin", e.tSpin());
                meta.put("tSpinMini", e.tSpinMini());
                meta.put("backToBack", e.backToBack());
                meta.put("perfectClear", e.perfectClear());
                meta.put("combo", e.combo());
                meta.put("escalationAdded", escalation);
                log("LINES_CLEARED", participant.getId(),
                        e.count() + (e.tetris() ? " (Tetris)" : "")
                                + (e.backToBack() ? " B2B" : "")
                                + (e.perfectClear() ? " PC" : ""), meta);
            }

            @Override
            public void onGarbageInserted(GarbageInsertedEvent e) {
                if (!isGameplayMutationAllowed()) {
                    Map<String, Object> meta = ignoredMeta(participant.getId());
                    meta.put("rows", e.rows());
                    meta.put("source", e.source());
                    meta.put("holeColumnsByRow", e.holeColumnsByRow());
                    log("IGNORED_GARBAGE_INSERTED", participant.getId(),
                            e.rows() + " rows from " + e.source(), meta);
                    return;
                }
                participant.addGarbageReceived(e.rows());
                participant.refreshMaxStackHeight();
                int escalation = e.rows() * 2;
                DefconChangeResult dcr = (escalation > 0)
                        ? defconState.addEscalation(escalation, "garbage")
                        : null;
                handleDefconChange(dcr);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("rows", e.rows());
                meta.put("source", e.source());
                meta.put("holeColumnsByRow", e.holeColumnsByRow());
                meta.put("escalationAdded", escalation);
                log("GARBAGE_INSERTED", participant.getId(),
                        e.rows() + " rows from " + e.source(), meta);
            }

            @Override
            public void onTopOut(TopOutEvent e) {
                if (currentPhase == MatchPhase.GAME_OVER || winner != null) {
                    Map<String, Object> meta = ignoredMeta(participant.getId());
                    meta.put("reason", e.reason());
                    meta.put("playerId", e.playerId());
                    log("IGNORED_TOP_OUT_AFTER_GAME_OVER", participant.getId(),
                            String.valueOf(e.reason()), meta);
                    return;
                }
                if (currentPhase == MatchPhase.SETUP || currentPhase == MatchPhase.UPGRADE_PAUSE) {
                    // Top-out arrived outside ACTIVE play. Don't decide a winner
                    // from a phase where strategic state should be frozen.
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("phase", currentPhase);
                    meta.put("participant", participant.getId());
                    meta.put("reason", e.reason());
                    meta.put("playerId", e.playerId());
                    log("TOP_OUT_OUTSIDE_ACTIVE", participant.getId(),
                            String.valueOf(e.reason()), meta);
                    return;
                }
                // Phase is ACTIVE and there is no winner yet.
                participant.markToppedOut();
                participant.refreshMaxStackHeight();
                winner = participant.getId().opponent();
                currentPhase = MatchPhase.GAME_OVER;
                matchEndedAtMs = System.currentTimeMillis();
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("reason", e.reason());
                meta.put("playerId", e.playerId());
                meta.put("winner", winner);
                log("TOP_OUT", participant.getId(), String.valueOf(e.reason()), meta);
            }

            @Override
            public void onPauseChanged(PauseChangedEvent e) {
                // Per-board pause from a single GameState should not flip the
                // match-level paused flag. We just record it.
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("paused", e.paused());
                meta.put("reason", e.reason());
                log("BOARD_PAUSE_CHANGED", participant.getId(),
                        (e.paused() ? "paused" : "resumed") + " (" + e.reason() + ")", meta);
            }
        };
    }
    // ───────────────────────── DEFCON refresh ────────────────

    /**
     * Called after every escalation event. If DEFCON crossed a
     * threshold, refresh both participants' effective build-charge
     * requirement and emit a {@code DEFCON_CHANGED} log entry.
     */
    private void handleDefconChange(DefconChangeResult dcr) {
        if (dcr == null || !dcr.changed()) return;
        int newLevel = dcr.currentLevel();
        playerA.getNukeBuildState().refreshForDefcon(newLevel);
        playerB.getNukeBuildState().refreshForDefcon(newLevel);
        applyDefconGravityToBoards();
        // Step 26 (Nuke Builder integration) \u2014 refresh the simplified
        // strategic state so the live HUD shows the new design-specific
        // charge requirement and route goals after a DEFCON drop.
        applyDesignToSimplifiedState(playerA);
        applyDesignToSimplifiedState(playerB);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("previousLevel", dcr.previousLevel());
        meta.put("currentLevel", newLevel);
        meta.put("escalationMeter", dcr.escalationMeter());
        meta.put("reason", dcr.reason());
        log("DEFCON_CHANGED", null,
                "DEFCON " + dcr.previousLevel() + " \u2192 " + newLevel, meta);
        // Step 9: award global upgrade points the first time we hit
        // DEFCON 4 / 3 / 2 / 1.
        for (int level = Math.max(1, newLevel); level <= dcr.previousLevel() - 1; level++) {
            // walk all crossed levels (e.g. 5 -> 3 awards both 4 and 3).
            maybeAwardDefconUpgradePoints(level);
        }
        if (newLevel <= dcr.previousLevel()) {
            maybeAwardDefconUpgradePoints(newLevel);
        }
    }

    private void applyDefconGravityToBoards() {
        double multiplier = defconState.getGravityMultiplier();
        playerA.getGameState().setMabGravityMultiplier(multiplier);
        playerB.getGameState().setMabGravityMultiplier(multiplier);
    }

    // ─────────────────── Step 9: upgrade-point earning ─────────

    /**
     * Step 26 (Nuke Builder integration) — mirror the participant's
     * currently equipped {@link com.tetris.mab.nuke.NukeDesign} into the
     * simplified strategic state. Sets the design-specific charge
     * requirement and launch-route goals (Tetris / spin) so the live HUD
     * and probes always see the design's effective values.
     */
    public void applyDesignToSimplifiedState(ParticipantState participant) {
        if (participant == null) return;
        com.tetris.mab.NukeBuildState nb = participant.getNukeBuildState();
        com.tetris.mab.clear.MabSimplifiedStrategicState s = participant.getSimplifiedState();
        if (nb == null || s == null) return;
        s.syncFromNuke(nb.getCurrentBuildCharge(),
                Math.max(1, nb.getEffectiveBuildChargeRequired()));
        s.setLaunchTetrisGoal(nb.getEffectiveLaunchTetrisGoal());
        s.setLaunchSpinGoal(nb.getEffectiveLaunchSpinGoal());
    }

    /**
     * Step 26 (Nuke Builder integration) — add raw escalation and
     * trigger a full DEFCON refresh, including build-charge / route
     * recomputation for both participants. Used by probes and any
     * caller that needs to apply DEFCON pressure without going through
     * a piece-clear event.
     */
    public DefconChangeResult addEscalationAndRefresh(int amount, String reason) {
        DefconChangeResult dcr = defconState.addEscalation(amount, reason);
        handleDefconChange(dcr);
        return dcr;
    }

    /**
     * Step 26 (Nuke Builder integration) — apply an entry-side warhead
     * design from the setup screen. Updates {@link NukeBuildState} for
     * the participant, then mirrors the design into the simplified
     * strategic state so launch routes and charge requirement are
     * design-specific from the very first piece.
     */
    public void applyWarheadDesign(ParticipantId pid,
                                   com.tetris.mab.nuke.NukeDesign design) {
        ParticipantState p = getParticipant(pid);
        if (p == null || design == null) return;
        p.getNukeBuildState().setDesign(design, defconState.getLevel());
        applyDesignToSimplifiedState(p);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("designId", design.getId());
        meta.put("doctrine", design.getDoctrineType().name());
        meta.put("size", design.getSizeCategory().name());
        meta.put("chargeReq", p.getNukeBuildState().getEffectiveBuildChargeRequired());
        meta.put("tetrisGoal", p.getNukeBuildState().getEffectiveLaunchTetrisGoal());
        meta.put("spinGoal", p.getNukeBuildState().getEffectiveLaunchSpinGoal());
        log("MAB_WARHEAD_DESIGN_APPLIED", pid,
                design.getDisplayName(), meta);
    }

    private void maybeAwardLineClearUpgradePoints(ParticipantState participant) {
        int total = participant.getLinesClearedTotal();
        int newMilestone = total / LINE_CLEAR_UPGRADE_INTERVAL;
        int prev = participant.getId() == ParticipantId.PLAYER_A
                ? playerAUpgradeLineMilestone : playerBUpgradeLineMilestone;
        if (newMilestone > prev) {
            int delta = newMilestone - prev;
            participant.getUpgradeState().addUpgradePoints(delta);
            if (participant.getId() == ParticipantId.PLAYER_A) playerAUpgradeLineMilestone = newMilestone;
            else playerBUpgradeLineMilestone = newMilestone;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("totalLinesCleared", total);
            meta.put("milestoneInterval", LINE_CLEAR_UPGRADE_INTERVAL);
            meta.put("pointsAwarded", delta);
            meta.put("upgradePoints", participant.getUpgradeState().getUpgradePoints());
            log("UPGRADE_POINT_EARNED", participant.getId(),
                    "+" + delta + " from line clears", meta);
        }
    }

    private void maybeAwardDefconUpgradePoints(int newLevel) {
        if (newLevel > 4 || newLevel < 1) return;
        if (!awardedDefconLevels.add(newLevel)) return;
        int award = (newLevel == 1) ? 2 : 1;
        playerA.getUpgradeState().addUpgradePoints(award);
        playerB.getUpgradeState().addUpgradePoints(award);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("defconLevel", newLevel);
        meta.put("pointsAwarded", award);
        meta.put("playerAPoints", playerA.getUpgradeState().getUpgradePoints());
        meta.put("playerBPoints", playerB.getUpgradeState().getUpgradePoints());
        log("UPGRADE_POINTS_EARNED_GLOBAL_DEFCON", null,
                "DEFCON " + newLevel + " bonus +" + award + " each", meta);
    }

    // ─────────────────── Step 9: upgrade pause + apply API ─────

    /** Public guarded wrapper around {@link #enterUpgradePause(String)}. */
    public boolean openUpgradePause(String reason) {
        if (currentPhase != MatchPhase.ACTIVE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("phase", currentPhase);
            meta.put("reason", reason);
            log("UPGRADE_PAUSE_OPEN_REJECTED", null, "not ACTIVE", meta);
            return false;
        }
        enterUpgradePause(reason);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", reason);
        log("UPGRADE_PAUSE_OPENED", null, reason == null ? "" : reason, meta);
        return true;
    }

    /** Public guarded wrapper around {@link #exitUpgradePause(String)}. */
    public boolean closeUpgradePause(String reason) {
        if (currentPhase != MatchPhase.UPGRADE_PAUSE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("phase", currentPhase);
            meta.put("reason", reason);
            log("UPGRADE_PAUSE_CLOSE_REJECTED", null, "not in UPGRADE_PAUSE", meta);
            return false;
        }
        exitUpgradePause(reason);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", reason);
        log("UPGRADE_PAUSE_CLOSED", null, reason == null ? "" : reason, meta);
        return true;
    }

    /** Returns the upgrade choices available for {@code participantId} now. */
    public UpgradeChoiceSet getUpgradeChoices(ParticipantId participantId) {
        ParticipantState p = getParticipant(participantId);
        List<UpgradeDefinition> choices = upgradeRegistry.getAvailableFor(
                p.getUpgradeState(), defconState.getLevel());
        return new UpgradeChoiceSet(participantId, defconState.getLevel(),
                p.getUpgradeState().getUpgradePoints(), choices);
    }

    /** Apply a single upgrade — only allowed during UPGRADE_PAUSE. */
    public UpgradeApplicationResult applyUpgrade(ParticipantId participantId, UpgradeType type) {
        if (currentPhase != MatchPhase.UPGRADE_PAUSE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("phase", currentPhase);
            meta.put("participant", participantId);
            meta.put("upgradeType", type);
            log("UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE", participantId,
                    "must be in UPGRADE_PAUSE", meta);
            return UpgradeApplicationResult.failed(type, "not in UPGRADE_PAUSE");
        }
        UpgradeDefinition def = upgradeRegistry.get(type);
        if (def == null) {
            log("UPGRADE_APPLY_REJECTED", participantId, "unknown type " + type, null);
            return UpgradeApplicationResult.failed(type, "unknown upgrade type");
        }
        if (defconState.getLevel() > def.requiredDefconMaximum()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("defconLevel", defconState.getLevel());
            meta.put("requiredDefconMax", def.requiredDefconMaximum());
            log("UPGRADE_APPLY_REJECTED", participantId,
                    "DEFCON gating not met for " + type, meta);
            return UpgradeApplicationResult.failed(type, "DEFCON gating not met");
        }
        ParticipantState p = getParticipant(participantId);
        UpgradeApplicationResult result = p.getUpgradeState().applyUpgrade(def);
        if (!result.success()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("upgradeType", type);
            meta.put("reason", result.message());
            log("UPGRADE_APPLY_REJECTED", participantId,
                    "rejected: " + result.message(), meta);
            return result;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("upgradeType", type);
        meta.put("newLevel", result.newLevel());
        meta.put("costPaid", result.costPaid());
        meta.put("upgradePointsAfter", p.getUpgradeState().getUpgradePoints());
        log("UPGRADE_APPLIED", participantId,
                type + " -> level " + result.newLevel(), meta);
        applyUpgradeSideEffects(p, type, result.newLevel());
        return result;
    }

    private void applyUpgradeSideEffects(ParticipantState p, UpgradeType type, int newLevel) {
        SiloState silo = p.getSiloState();
        CivilDefenseState cds = p.getCivilDefenseState();
        boolean handled = true;
        switch (type) {
            case HARDENED_SILO         -> silo.setHardeningLevel(newLevel);
            case DEEP_BUNKER           -> silo.setDeepBunkerLevel(newLevel);
            case DISTRIBUTED_STOCKPILE -> silo.setDistributedStockpileLevel(newLevel);
            case RAPID_ASSEMBLY_LINE   -> silo.setAssemblySpeedLevel(newLevel);
            case SECURE_LAUNCH_CHAIN   -> silo.setLaunchSecurityLevel(newLevel);
            case BLAST_DOORS           -> silo.setBlastDoorLevel(newLevel);
            case SILO_CAMOUFLAGE       -> silo.setCamouflageLevel(newLevel);
            case SHELTERS              -> cds.setShelterLevel(newLevel);
            case GARBAGE_CONTROL       -> cds.setGarbageControlLevel(newLevel);
            case EMERGENCY_PROTOCOLS   -> cds.setEmergencyProtocolLevel(newLevel);
            default -> handled = false; // tempo / nuke / MAD / radar — handled at use sites later
        }
        if (handled) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("upgradeType", type);
            meta.put("newLevel", newLevel);
            log("UPGRADE_SIDE_EFFECT_APPLIED", p.getId(),
                    type + " applied to participant state", meta);
        }
    }

    /** Redesign the participant's nuke during UPGRADE_PAUSE. */
    public boolean redesignNukeDuringUpgradePause(ParticipantId participantId,
                                                  NukeDesign newDesign,
                                                  double retainedChargeRatio) {
        if (currentPhase != MatchPhase.UPGRADE_PAUSE) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("phase", currentPhase);
            log("NUKE_REDESIGN_REJECTED", participantId, "not in UPGRADE_PAUSE", meta);
            return false;
        }
        if (newDesign == null) {
            log("NUKE_REDESIGN_REJECTED", participantId, "null design", null);
            return false;
        }
        ParticipantState p = getParticipant(participantId);
        NukeDesign oldDesign = p.getNukeBuildState().getCurrentDesign();
        p.getNukeBuildState().redesign(newDesign, defconState.getLevel(), retainedChargeRatio);
        applyDesignToSimplifiedState(p);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("oldDesignId", oldDesign == null ? null : oldDesign.getId());
        meta.put("newDesignId", newDesign.getId());
        meta.put("retainedChargeRatio", retainedChargeRatio);
        meta.put("defconLevel", defconState.getLevel());
        log("NUKE_REDESIGNED", participantId,
                (oldDesign == null ? "?" : oldDesign.getId()) + " -> " + newDesign.getId(), meta);
        // Step 10: opponent intel about us is now stale.
        markOpponentIntelStale(p, "redesign:" + newDesign.getId());
        return true;
    }

    /** Redesign convenience overload: builder spec + retention rules. */
    public boolean redesignNukeFromBuilderSpecDuringUpgradePause(ParticipantId participantId,
                                                                  BuilderNukeSpec spec) {
        if (spec == null) {
            log("NUKE_REDESIGN_REJECTED", participantId, "null spec", null);
            return false;
        }
        NukeDesign newDesign = nukeBuilderAdapter.fromBuilderSpec(spec);
        ParticipantState p = getParticipant(participantId);
        NukeDesign oldDesign = p.getNukeBuildState().getCurrentDesign();
        double ratio = NukeRedesignRetentionRules.suggestedRetentionRatio(oldDesign, newDesign);
        return redesignNukeDuringUpgradePause(participantId, newDesign, ratio);
    }

    // ─────────────────────── Action-code public API ──────────

    /**
     * Public radar-scan request (BASIC type). Gated by
     * {@link #isGameplayMutationAllowed()}.
     */
    public RadarScanResult performRadarScan(ParticipantId scannerId) {
        return performRadarScan(scannerId, RadarScanType.BASIC);
    }

    /** Public radar-scan request with explicit scan type. */
    public RadarScanResult performRadarScan(ParticipantId scannerId, RadarScanType type) {
        ParticipantState scanner = getParticipant(scannerId);
        if (scanner == null) {
            log("ROUTE_SCAN_REJECTED", scannerId, "unknown scanner", null);
            return RadarScanResult.failed(scannerId, null,
                    type == null ? RadarScanType.BASIC : type,
                    (int) (radarScanSequence + 1), "unknown scanner");
        }
        if (!isGameplayMutationAllowed()) {
            log("ROUTE_SCAN_REJECTED", scannerId,
                    "phase=" + currentPhase, metaOf("phase", currentPhase));
            return RadarScanResult.failed(scannerId, null,
                    type == null ? RadarScanType.BASIC : type,
                    (int) (radarScanSequence + 1),
                    "gameplay mutation not allowed in " + currentPhase);
        }
        return performRadarScan(scanner, type == null ? RadarScanType.BASIC : type,
                "public-api");
    }

    /** Snapshot of stored intel about scanner's opponent. */
    public StaleIntelSnapshot getEnemyIntelSnapshot(ParticipantId scannerId) {
        ParticipantState s = getParticipant(scannerId);
        return s == null ? null : s.getRadarIntel().getEnemyIntel();
    }

    /** Last raw scan result by scanner (success or failure). */
    public RadarScanResult getLastRadarScan(ParticipantId scannerId) {
        ParticipantState s = getParticipant(scannerId);
        return s == null ? null : s.getRadarIntel().getLastScan();
    }

    /** Internal: run the radar scan and record/log it. */
    private RadarScanResult performRadarScan(ParticipantState scanner,
                                              RadarScanType type,
                                              String source) {
        ParticipantState target = getOpponent(scanner.getId());
        if (target == null) {
            log("ROUTE_SCAN_REJECTED", scanner.getId(),
                    "no opponent", metaOf("source", source));
            RadarScanResult fail = RadarScanResult.failed(scanner.getId(), null, type,
                    (int) (radarScanSequence + 1), "no opponent");
            scanner.getRadarIntel().recordScan(fail);
            return fail;
        }
        radarScanSequence++;
        log("ROUTE_SCAN_STARTED", scanner.getId(), type.name(),
                metaOf("scanner", scanner.getId(),
                        "target", target.getId(),
                        "scanType", type,
                        "sequence", radarScanSequence,
                        "source", source));

        RadarScanResult result = radarScanCalculator.scan(scanner, target,
                defconState.getLevel(), (int) radarScanSequence, type);
        scanner.getRadarIntel().recordScan(result);

        log("ROUTE_SCAN_COMPLETED", scanner.getId(),
                result.intelLevel() + " conf=" + result.confidencePercent(),
                metaOf("scanner", scanner.getId(),
                        "target", target.getId(),
                        "scanType", type,
                        "readoutLevel", result.intelLevel(),
                        "confidence", result.confidencePercent(),
                        "success", result.success(),
                        "stale", result.staleImmediately(),
                        "source", source));
        log("ROUTE_READOUT_UPDATED", scanner.getId(),
                result.intelLevel().name(),
                metaOf("scanner", scanner.getId(),
                        "target", target.getId(),
                        "readoutLevel", result.intelLevel(),
                        "confidence", result.confidencePercent(),
                        "designId", result.targetNukeDesignId(),
                        "doctrine", result.targetDoctrineType(),
                        "size", result.targetSizeCategory(),
                        "stale", result.staleImmediately()));

        // Step 11 refinement: emit ROUTE_FEINT_EFFECT_APPLIED whenever the
        // scan succeeds against a target that has at least one active feint.
        // This is informational/debug; it does not mutate feint state, even
        // if the scan pierced their signatures.
        if (result.success()) {
            int activeFeintCount = 0;
            int falseLaunchSig = 0;
            int falseThreatSig = 0;
            int decoyConfPenalty = 0;
            for (ActiveDecoyState d : target.getActiveDecoys()) {
                if (!d.isActive()) continue;
                activeFeintCount++;
                decoyConfPenalty += d.getConfidencePenalty();
                if (d.createsFalseLaunchSignature()) falseLaunchSig += d.getFalseLaunchCount();
                if (d.createsFalseThreatSignature()) falseThreatSig += d.getFalseThreatCount();
            }
            if (activeFeintCount > 0) {
                log("ROUTE_FEINT_EFFECT_APPLIED", scanner.getId(),
                        result.message(),
                        metaOf("scanner", scanner.getId(),
                                "target", target.getId(),
                                "scanSequenceNumber", result.scanSequenceNumber(),
                                "scanType", type,
                                "readoutLevel", result.intelLevel(),
                                "confidence", result.confidencePercent(),
                                "activeFeintCount", activeFeintCount,
                                "falseLaunchSignatureCount", falseLaunchSig,
                                "falseThreatSignatureCount", falseThreatSig,
                                "activeFeintConfidencePenalty", decoyConfPenalty,
                                "message", result.message()));
            }
        }
        return result;
    }

    /**
     * Mark stored intel held by participant's opponent (which describes
     * the participant) as stale, e.g. after the participant launches or
     * redesigns.
     */
    private void markOpponentIntelStale(ParticipantState participant, String reason) {
        if (participant == null) return;
        ParticipantState opp = getOpponent(participant.getId());
        if (opp == null) return;
        if (opp.getRadarIntel().getEnemyIntel() == null) return;
        opp.getRadarIntel().markEnemyIntelStale(reason);
        log("ROUTE_READOUT_MARKED_STALE", opp.getId(), reason,
                metaOf("scanner", opp.getId(),
                        "target", participant.getId(),
                        "reason", reason));
    }

    // ─────────────────────── Decoys (Step 11) ─────────────────

    private String nextDecoyId(DecoyType type) {
        decoySequence++;
        String prefix = type == null ? "feint" : type.name().toLowerCase();
        return prefix + "-" + decoySequence;
    }

    /** Tick + log expirations for a single participant's decoys. */
    private void tickAndExpireDecoys(ParticipantState owner) {
        if (owner == null) return;
        List<ActiveDecoyState> snapshot = new ArrayList<>();
        for (ActiveDecoyState d : owner.getActiveDecoys()) {
            if (d.isActive()) snapshot.add(d);
        }
        for (ActiveDecoyState d : snapshot) {
            int before = d.getDurationPiecesRemaining();
            d.tickPiece();
            if (before > 0 && d.isExpired()) {
                log("FEINT_EXPIRED", owner.getId(), d.getDecoyId(),
                        metaOf("owner", owner.getId(),
                                "target", d.getTarget(),
                                "feintId", d.getDecoyId(),
                                "type", d.getType(),
                                "linkedLaunchId", d.getLinkedLaunchId()));
                markOpponentIntelStale(owner, "feint-expired:" + d.getDecoyId());
            }
        }
    }

    /**
     * Activate a decoy from a completed action-code attempt. For
     * {@code MASKED_LAUNCH}, the caller passes the linked real launch id;
     * for other decoys this is null.
     */
    private DecoyResolutionResult activateDecoyFromAction(ParticipantState owner,
                                                           ActionCodeDefinition def,
                                                           String linkedLaunchId) {
        if (owner == null || def == null) {
            return DecoyResolutionResult.failed(null, null,
                    owner == null ? null : owner.getId(), null,
                    "null owner/definition");
        }
        DecoyDefinition ddef = decoyRegistry.getByActionType(def.getActionType());
        if (ddef == null) {
            log("FEINT_MANUAL_ACTIVATION_REJECTED", owner.getId(),
                    "no feint definition for " + def.getActionType(),
                    metaOf("actionId", def.getId(),
                            "actionType", def.getActionType()));
            return DecoyResolutionResult.failed(null, def.getActionType(),
                    owner.getId(), null,
                    "no feint definition for " + def.getActionType());
        }
        ParticipantState target = getOpponent(owner.getId());
        String decoyId = nextDecoyId(ddef.type());
        DecoyResolutionResult result = decoyResolver.activateDecoy(
                ddef, owner, target, decoyId, linkedLaunchId);
        if (result.success()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("owner", owner.getId());
            meta.put("target", target == null ? null : target.getId());
            meta.put("feintId", decoyId);
            meta.put("type", ddef.type());
            meta.put("actionType", def.getActionType());
            meta.put("durationPieces", ddef.durationPieces());
            meta.put("falseLaunchCount", ddef.falseLaunchCount());
            meta.put("falseThreatCount", ddef.falseThreatCount());
            meta.put("confidencePenalty", ddef.confidencePenalty());
            meta.put("linkedLaunchId", linkedLaunchId);
            meta.put("source", "action:" + def.getId());
            log("FEINT_ACTIVATED", owner.getId(), decoyId, meta);
            markOpponentIntelStale(owner, "feint:" + decoyId);
        }
        return result;
    }

    /** Public read-only view of one participant's active decoys. */
    public List<ActiveDecoyState> getActiveDecoys(ParticipantId participantId) {
        ParticipantState p = getParticipant(participantId);
        if (p == null) return Collections.emptyList();
        return p.getActiveDecoys();
    }

    /**
     * Programmatic decoy activation for tests / debugging. Honours the
     * gameplay phase guard. {@link DecoyType#MASKED_LAUNCH} is rejected
     * because it requires a real launch authorization to link to.
     */
    public DecoyResolutionResult activateDecoyManually(ParticipantId ownerId, DecoyType type) {
        ParticipantState owner = getParticipant(ownerId);
        if (owner == null || type == null) {
            log("FEINT_MANUAL_ACTIVATION_REJECTED", ownerId,
                    "invalid owner/type",
                    metaOf("owner", ownerId, "type", type));
            return DecoyResolutionResult.failed(type, null, ownerId, null,
                    "invalid owner/type");
        }
        if (!isGameplayMutationAllowed()) {
            log("FEINT_MANUAL_ACTIVATION_REJECTED", ownerId,
                    "phase=" + currentPhase,
                    metaOf("owner", ownerId, "type", type, "phase", currentPhase));
            return DecoyResolutionResult.failed(type, null, ownerId, null,
                    "phase " + currentPhase + " not mutation-allowed");
        }
        if (type == DecoyType.MASKED_LAUNCH) {
            log("FEINT_MANUAL_ACTIVATION_REJECTED", ownerId,
                    "MASKED_LAUNCH requires a real launch",
                    metaOf("owner", ownerId, "type", type));
            return DecoyResolutionResult.failed(type, ActionType.MASKED_LAUNCH,
                    ownerId, null,
                    "MASKED_LAUNCH cannot be activated manually");
        }
        DecoyDefinition ddef = decoyRegistry.getByType(type);
        if (ddef == null) {
            log("FEINT_MANUAL_ACTIVATION_REJECTED", ownerId,
                    "no definition for " + type,
                    metaOf("owner", ownerId, "type", type));
            return DecoyResolutionResult.failed(type, null, ownerId, null,
                    "no definition for " + type);
        }
        ParticipantState target = getOpponent(ownerId);
        String decoyId = nextDecoyId(type);
        DecoyResolutionResult result = decoyResolver.activateDecoy(
                ddef, owner, target, decoyId, null);
        if (result.success()) {
            log("FEINT_ACTIVATED", ownerId, decoyId,
                    metaOf("owner", ownerId,
                            "target", target == null ? null : target.getId(),
                            "feintId", decoyId,
                            "type", type,
                            "actionType", ddef.actionType(),
                            "durationPieces", ddef.durationPieces(),
                            "falseLaunchCount", ddef.falseLaunchCount(),
                            "falseThreatCount", ddef.falseThreatCount(),
                            "confidencePenalty", ddef.confidencePenalty(),
                            "source", "manual"));
            markOpponentIntelStale(owner, "feint:" + decoyId);
        }
        return result;
    }

    /**
     * Begin a default action attempt by {@link ActionType}. Returns
     * false (and logs an ignored/rejected entry) if the match is not
     * mutating, the action is unknown, or prerequisites fail.
     */
    public boolean startActionAttempt(ParticipantId participantId, ActionType actionType) {
        if (!isGameplayMutationAllowed()) {
            Map<String, Object> meta = ignoredMeta(participantId);
            meta.put("actionType", actionType);
            log("ACTION_START_IGNORED", participantId,
                    actionType == null ? "null" : actionType.name(), meta);
            return false;
        }
        if (actionType == null) {
            log("ACTION_START_IGNORED", participantId, "null actionType", null);
            return false;
        }
        ParticipantState participant = getParticipant(participantId);
        ActionCodeDefinition def = actionCodeRegistry.getByType(actionType);
        if (def == null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("actionType", actionType);
            log("ACTION_START_IGNORED", participantId,
                    "unknown actionType " + actionType, meta);
            return false;
        }
        return startAttemptInternal(participant, def);
    }

    /**
     * Begin a launch attempt for the participant's currently armed
     * nuke, using the design's effective launch code at the current
     * DEFCON level. Confirmation mode is KEYBOARD_CONFIRM.
     */
    public boolean startLaunchAttemptForCurrentNuke(ParticipantId participantId) {
        return startLaunchAttemptInternal(participantId, false);
    }

    /**
     * Same as {@link #startLaunchAttemptForCurrentNuke(ParticipantId)},
     * but the sequence is extended with a non-replaceable hard 4-line
     * confirmation token (HARD_FOUR_CONFIRM mode).
     */
    public boolean startLaunchAttemptForCurrentNukeWithHardFourConfirm(ParticipantId participantId) {
        return startLaunchAttemptInternal(participantId, true);
    }

    private boolean startLaunchAttemptInternal(ParticipantId participantId, boolean hardFour) {
        if (!isGameplayMutationAllowed()) {
            log("ACTION_START_IGNORED", participantId, "phase/paused/winner",
                    ignoredMeta(participantId));
            return false;
        }
        ParticipantState participant = getParticipant(participantId);
        if (!participant.getNukeBuildState().isArmed()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("design", participant.getNukeBuildState().getCurrentDesign().getId());
            log("ACTION_START_REJECTED_NOT_ARMED", participantId,
                    "nuke not armed", meta);
            return false;
        }
        ActionCodeDefinition def = hardFour
                ? actionCodeRegistry.launchDefinitionForNukeWithHardFourConfirm(
                        participant.getNukeBuildState().getCurrentDesign(), defconState.getLevel())
                : actionCodeRegistry.launchDefinitionForNuke(
                        participant.getNukeBuildState().getCurrentDesign(), defconState.getLevel());
        return startAttemptInternal(participant, def);
    }

    private boolean startAttemptInternal(ParticipantState participant, ActionCodeDefinition def) {
        if (def.requiresArmedNuke() && !participant.getNukeBuildState().isArmed()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("actionId", def.getId());
            log("ACTION_START_REJECTED_NOT_ARMED", participant.getId(),
                    def.getId() + " requires armed nuke", meta);
            return false;
        }
        if (def.requiresIncomingThreat()) {
            if (participant.getIncomingThreats().isEmpty()) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                log("ACTION_START_REJECTED_NO_THREAT", participant.getId(),
                        def.getId() + " requires incoming threat", meta);
                return false;
            }
            boolean anyActive = false;
            for (IncomingThreatState t : participant.getIncomingThreats()) {
                if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) {
                    anyActive = true; break;
                }
            }
            if (!anyActive) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                log("ACTION_START_REJECTED_NO_ACTIVE_THREAT", participant.getId(),
                    def.getId() + " requires impact-pending threat", meta);
                return false;
            }
        }
        participant.getActionCodeManager().startAttempt(def, participant.getPiecesLocked());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("actionId", def.getId());
        meta.put("actionName", def.getDisplayName());
        meta.put("actionType", def.getActionType());
        meta.put("category", def.getCategory());
        meta.put("sequenceLength", def.sequenceLength());
        meta.put("confirmationMode", def.getConfirmationMode());
        log("ACTION_STARTED", participant.getId(), def.getDisplayName(), meta);
        return true;
    }

    /** Confirm the participant's pending KEYBOARD_CONFIRM action. */
    public boolean confirmActionAttempt(ParticipantId participantId) {
        if (!isGameplayMutationAllowed()) {
            log("ACTION_CONFIRM_IGNORED", participantId, "phase/paused/winner",
                    ignoredMeta(participantId));
            return false;
        }
        ParticipantState participant = getParticipant(participantId);
        ActionCodeManager mgr = participant.getActionCodeManager();
        if (!mgr.hasPendingConfirmation()) {
            log("ACTION_CONFIRM_IGNORED_NONE_PENDING", participantId,
                    "no pending confirmation", null);
            return false;
        }
        com.tetris.mab.action.ActionCodeAttempt pending = mgr.getPendingConfirmationAttempt();
        ActionCodeDefinition def = pending.getDefinition();
        ActionCodeResult res = mgr.confirmPending();
        if (res == ActionCodeResult.COMPLETED) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("actionId", def.getId());
            meta.put("actionName", def.getDisplayName());
            meta.put("actionType", def.getActionType());
            meta.put("category", def.getCategory());
            log("ACTION_CONFIRMED", participantId, def.getDisplayName(), meta);
            log("ACTION_COMPLETED", participantId, def.getDisplayName(), meta);
            dispatchCompletedAction(participant, def);
            return true;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("actionId", def.getId());
        meta.put("result", res);
        log("ACTION_CONFIRM_FAILED", participantId, def.getDisplayName(), meta);
        return false;
    }

    /** Cancel any active or pending action for the participant. */
    public boolean cancelActionAttempt(ParticipantId participantId, String reason) {
        ParticipantState participant = getParticipant(participantId);
        ActionCodeManager mgr = participant.getActionCodeManager();
        if (!mgr.hasActiveAttempt() && !mgr.hasPendingConfirmation()) {
            log("ACTION_CANCEL_IGNORED_NONE_ACTIVE", participantId,
                    reason == null ? "" : reason, null);
            return false;
        }
        mgr.cancelAttempt(reason == null ? "cancelled" : reason);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", reason);
        log("ACTION_CANCELLED", participantId, reason == null ? "" : reason, meta);
        return true;
    }

    private void processActionForLineClear(ParticipantState participant,
                                           GameEventListener.LinesClearedEvent e) {
        ActionCodeManager mgr = participant.getActionCodeManager();
        ActionClearToken token = ActionClearToken.fromLinesClearedEvent(e);
        ActionCodeMatchMode mode = ActionCodeDifficultyRules.matchModeFor(difficulty);

        // Step 20 Fourth Refinement — visible "input received" notification.
        // Always log so the player knows their clear was observed by the
        // action-code system, even when nothing matches.
        Map<String, Object> inputMeta = new LinkedHashMap<>();
        inputMeta.put("count", e.count());
        inputMeta.put("token", token.toDebugString());
        log("INPUT_LINE_CLEAR", participant.getId(),
                e.count() + "-line clear received", inputMeta);

        // Step 20 Fourth Refinement — if no attempt is active, try to
        // auto-start one whose first required token matches the player's
        // clear. This is the routing fix: previously the player could
        // never start an attempt without a developer keybind, so every
        // line clear was silently dropped.
        if (!mgr.hasActiveAttempt()) {
            ActionCodeDefinition autoDef = pickAutoStartDefinition(participant, token, mode);
            if (autoDef == null) {
                Map<String, Object> noMatchMeta = new LinkedHashMap<>();
                noMatchMeta.put("count", e.count());
                noMatchMeta.put("token", token.toDebugString());
                log("ACTION_NO_MATCH", participant.getId(),
                        e.count() + "-line clear matched no command", noMatchMeta);
                return;
            }
            mgr.startAttempt(autoDef, participant.getPiecesLocked());
            Map<String, Object> startMeta = new LinkedHashMap<>();
            startMeta.put("actionId", autoDef.getId());
            startMeta.put("actionName", autoDef.getDisplayName());
            startMeta.put("actionType", autoDef.getActionType());
            startMeta.put("category", autoDef.getCategory());
            startMeta.put("sequenceLength", autoDef.sequenceLength());
            startMeta.put("token", token.toDebugString());
            log("ACTION_AUTO_STARTED", participant.getId(), autoDef.getDisplayName(), startMeta);
            // fall through to advance the attempt with the same clear
        }

        ActionCodeDefinition def = mgr.getActiveAttempt().getDefinition();
        // Note: piecesLocked is incremented in onPieceLocked which fires
        // before onLinesCleared on the same lock, so this count already
        // includes the piece that produced this clear.
        ActionCodeResult res = mgr.processLineClear(token, mode, participant.getPiecesLocked());
        switch (res) {
            case ADVANCED -> {
                ActionCodeTokenRequirement next = mgr.hasActiveAttempt()
                        ? mgr.getActiveAttempt().getExpectedNextRequirement() : null;
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                meta.put("actionName", def.getDisplayName());
                meta.put("progress", mgr.hasActiveAttempt()
                        ? mgr.getActiveAttempt().getProgressIndex() : def.sequenceLength());
                meta.put("required", def.sequenceLength());
                meta.put("expectedNext", next == null ? null : next.requiredLineCount());
                meta.put("token", token.toDebugString());
                log("ACTION_ADVANCED", participant.getId(), def.getDisplayName(), meta);
            }
            case PENDING_CONFIRMATION -> {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                meta.put("actionName", def.getDisplayName());
                meta.put("actionType", def.getActionType());
                meta.put("category", def.getCategory());
                log("ACTION_PENDING_CONFIRMATION", participant.getId(),
                        def.getDisplayName(), meta);
            }
            case COMPLETED -> {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                meta.put("actionName", def.getDisplayName());
                meta.put("actionType", def.getActionType());
                meta.put("category", def.getCategory());
                log("ACTION_COMPLETED", participant.getId(), def.getDisplayName(), meta);
                dispatchCompletedAction(participant, def);
            }
            case FAILED_RESET -> {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("actionId", def.getId());
                meta.put("actionName", def.getDisplayName());
                meta.put("required", def.toDebugString());
                StringBuilder done = new StringBuilder();
                for (ActionClearToken t :
                        mgr.getCompletedAttempts().isEmpty()
                                ? java.util.List.<ActionClearToken>of()
                                : mgr.getCompletedAttempts()
                                        .get(mgr.getCompletedAttempts().size() - 1)
                                        .getCompletedTokens()) {
                    if (done.length() > 0) done.append(',');
                    done.append(t.toDebugString());
                }
                meta.put("completedSoFar", done.toString());
                meta.put("breakingToken", token.toDebugString());
                log("ACTION_FAILED_RESET", participant.getId(), def.getDisplayName(), meta);
            }
            default -> { /* NOT_ACTIVE / IGNORED / others: no log */ }
        }
    }

    /**
     * Step 20 Fourth Refinement — pick a deterministic action-code
     * definition to auto-start when the player clears lines and no
     * attempt is active. Walks the registry in registration order and
     * returns the first definition whose first token requirement is
     * satisfied by {@code token} <i>and</i> whose static preconditions
     * (armed nuke, incoming threat) are met.
     *
     * <p>Returns {@code null} if no definition matches; the caller then
     * logs {@code ACTION_NO_MATCH} so the HUD can show
     * "INPUT: N-line clear did not match — no command".
     *
     * <p>Launch definitions ({@code requiresArmedNuke=true}) are skipped
     * unless the participant's nuke is actually armed, so they don't
     * monopolise auto-start when the player isn't ready to launch.
     */
    private ActionCodeDefinition pickAutoStartDefinition(ParticipantState participant,
                                                         ActionClearToken token,
                                                         ActionCodeMatchMode mode) {
        if (token == null || token.lineCount() <= 0) return null;
        boolean armed = participant.getNukeBuildState().isArmed();
        boolean hasActiveThreat = false;
        for (IncomingThreatState t : participant.getIncomingThreats()) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) {
                hasActiveThreat = true;
                break;
            }
        }
        for (ActionCodeDefinition def : actionCodeRegistry.getAll()) {
            if (def.sequenceLength() == 0) continue;
            if (def.requiresArmedNuke() && !armed) continue;
            if (def.requiresIncomingThreat() && !hasActiveThreat) continue;
            ActionCodeTokenRequirement first = def.getSequence().get(0);
            if (com.tetris.mab.action.ActionCodeMatcher.matches(
                    token, first, mode, def.isStrategicAction())) {
                return def;
            }
        }
        return null;
    }

    /**
     * Step 20 Fourth Refinement — package-visible test entry point used
     * by {@link com.tetris.mab.sim.MabActionCodeInputProbe}. Synthesises
     * a {@link GameEventListener.LinesClearedEvent} for {@code lines}
     * lines and routes it through the same listener path a real Player
     * line clear would take, including phase gating, auto-start, and
     * action-code processing.
     *
     * <p>Returns the latest {@link ActionCodeResult}, or
     * {@link ActionCodeResult#IGNORED} when the phase gate dropped the
     * input so the probe can fail loudly.
     *
     * <p><b>Offline-only.</b> Not invoked by the production game loop.
     */
    public ActionCodeResult debugFeedLineClear(ParticipantId pid, int lines) {
        if (lines <= 0) return ActionCodeResult.IGNORED;
        ParticipantState p = getParticipant(pid);
        if (p == null) return ActionCodeResult.IGNORED;
        if (!isGameplayMutationAllowed()) {
            return ActionCodeResult.IGNORED;
        }
        GameEventListener.LinesClearedEvent ev = new GameEventListener.LinesClearedEvent(
                lines, java.util.List.of(),
                lines == 4, false, false,
                false, false, 0);
        // Mirror the real listener: bump piece-locked counter so the
        // attempt records realistic startedAtPieceCount values.
        p.incrementPiecesLocked();
        p.addLinesCleared(lines);
        processActionForLineClear(p, ev);
        ActionCodeManager mgr = p.getActionCodeManager();
        if (mgr.hasPendingConfirmation()) return ActionCodeResult.PENDING_CONFIRMATION;
        if (mgr.hasActiveAttempt()) return ActionCodeResult.ADVANCED;
        // Inspect the most recently archived attempt.
        java.util.List<com.tetris.mab.action.ActionCodeAttempt> done = mgr.getCompletedAttempts();
        if (!done.isEmpty()) {
            com.tetris.mab.action.ActionCodeAttempt last = done.get(done.size() - 1);
            if (last.isCompleted())   return ActionCodeResult.COMPLETED;
            if (last.isFailed())      return ActionCodeResult.FAILED_RESET;
        }
        return ActionCodeResult.IGNORED;
    }
    // ─────────────────────────── Step 21 — Simplified MAB clear router ──

    /**
     * Step 21 \u2014 simplified MAB clear router. Replaces the action-code
     * launch/intercept path for normal PvE and local 1v1.
     *
     * <p>Priority order:
     * <ol>
     *   <li>If this participant has any incoming threat in
     *       {@link com.tetris.mab.launch.ThreatStatus#WARNING_ACTIVE} and
     *       the clear is a spin: trigger an intercept whose strength is
     *       chosen by the spin's line count, and skip charge/launch
     *       progress for this clear.</li>
     *   <li>Otherwise: add charge from the simplified formula. If the
     *       nuke is now armed, route Tetrises to {@code launchTetrisProgress}
     *       and spins to {@code launchSpinProgress}.</li>
     *   <li>If launch progress reaches 4 Tetrises or 2 spins: fire a
     *       launch and reset charge / progress.</li>
     * </ol>
     *
     * <p>Logs MAB_CLEAR_CHARGE_GAINED, MAB_NUKE_READY,
     * MAB_LAUNCH_PROGRESS_TETRIS, MAB_LAUNCH_PROGRESS_SPIN,
     * MAB_LAUNCH_FIRED_SIMPLIFIED, MAB_SPIN_INTERCEPT_TRIGGERED, and
     * MAB_INTERCEPT_RESOLVED_SIMPLIFIED so the HUD and event banner can
     * surface them.
     */
    private void processSimplifiedClearForLineClear(ParticipantState participant,
                                                    GameEventListener.LinesClearedEvent e) {
        com.tetris.mab.clear.MabClearResult clear =
                com.tetris.mab.clear.MabChargeCalculator.fromEvent(participant.getId(), e);
        com.tetris.mab.clear.MabSimplifiedStrategicState state =
                participant.getSimplifiedState();

        // 1. Intercept priority — spin during incoming threat.
        if (clear.isSpin() && hasActiveIncomingThreat(participant)) {
            triggerSimplifiedSpinIntercept(participant, clear);
            // record but skip charge/launch progress for this clear.
            state.recordClear(clear.displayText(), 0, e.backToBack());
            state.onSpinIntercept();
            return;
        }

        // 2. Charge gain (Step 24 — apply upgrade modifiers).
        int effectiveCharge = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .applyChargeModifiers(participant.getUpgradeInventory(), clear, clear.chargeGained());
        // Stateful multipliers (Wellsmith × Adrenaline × Hair Trigger).
        double fCharge = effectiveCharge * upgradeChargeMult(participant);
        // Streak Stoker: N bonus charge on the Nth consecutive clear.
        int newStreak = participant.getStreakCount() + 1;
        participant.setStreakCount(newStreak);
        fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .streakStokerBonus(participant.getUpgradeInventory(), newStreak);
        // Spin Network: escalating bonus per spin in this launch cycle.
        if (clear.isSpin()) {
            int priorSpins = participant.getSpinsThisCycle();
            fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .spinNetworkBonus(participant.getUpgradeInventory(), priorSpins);
            participant.setSpinsThisCycle(priorSpins + 1);
        }
        // Quiet Storm: arm after 10 s idle; fire +20 on next clear.
        long nowMs = System.currentTimeMillis();
        if (!participant.isQuietStormArmed()
                && participant.getLastClearTimeMs() >= 0
                && (nowMs - participant.getLastClearTimeMs()) >= 10_000L) {
            participant.setQuietStormArmed(true);
        }
        if (participant.isQuietStormArmed()) {
            fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .quietStormBonus(participant.getUpgradeInventory(), true);
            participant.setQuietStormArmed(false);
        }
        participant.setLastClearTimeMs(nowMs);
        effectiveCharge = Math.max(0, (int) Math.round(fCharge));
        if (effectiveCharge > 0) {
            participant.getNukeBuildState().addCharge(effectiveCharge);
            enforceCap(participant);
        }
        boolean wasReady = state.nukeReady();
        state.syncFromNuke(
                participant.getNukeBuildState().getCurrentBuildCharge(),
                Math.max(1, participant.getNukeBuildState().getEffectiveBuildChargeRequired()));
        state.recordClear(clear.displayText(), effectiveCharge, e.backToBack());

        Map<String, Object> chargeMeta = new LinkedHashMap<>();
        chargeMeta.put("chargeGained", effectiveCharge);
        chargeMeta.put("chargeCurrent", state.chargeCurrent());
        chargeMeta.put("chargeRequired", state.chargeRequired());
        chargeMeta.put("clear", clear.displayText());
        log("MAB_CLEAR_CHARGE_GAINED", participant.getId(),
                clear.displayText(), chargeMeta);

        if (!wasReady && state.nukeReady()) {
            state.markNukeReady();
            Map<String, Object> readyMeta = new LinkedHashMap<>();
            readyMeta.put("chargeRequired", state.chargeRequired());
            log("MAB_NUKE_READY", participant.getId(), "nuke ready", readyMeta);
            // Manual Override: light the indicator when charge first hits 100 this cycle.
            if (!participant.isManualOverrideReadyThisCycle()
                    && participant.getUpgradeInventory().hasTag("tempo_manual_override")) {
                participant.setManualOverrideReadyThisCycle(true);
                participant.getActiveDoctrineState().resetManualOverrideCycle();
            }
        }

        // 3. Launch progress (only counts after charge is full).
        if (state.nukeReady()) {
            if (clear.tetris()) {
                state.incrementTetrisProgress();
                // Step 24 — Tetris Doctrine: once per launch cycle, a
                // post-ready Tetris counts as +1 extra route pip.
                if (com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                        .tetrisDoctrineActive(participant.getUpgradeInventory())
                        && !participant.isTetrisDoctrineUsedThisCycle()) {
                    state.incrementTetrisProgress();
                    participant.setTetrisDoctrineUsedThisCycle(true);
                }
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("progress", state.launchTetrisProgress());
                pm.put("goal", state.launchTetrisGoal());
                log("MAB_LAUNCH_PROGRESS_TETRIS", participant.getId(),
                        "Tetrises " + state.launchTetrisProgress() + "/"
                                + state.launchTetrisGoal(), pm);
            } else if (clear.isSpin()) {
                state.incrementSpinProgress();
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("progress", state.launchSpinProgress());
                pm.put("goal", state.launchSpinGoal());
                log("MAB_LAUNCH_PROGRESS_SPIN", participant.getId(),
                        "Spins " + state.launchSpinProgress() + "/"
                                + state.launchSpinGoal(), pm);
            }
            if (MabUpgradeEffectResolver.cascadeReactorTriggers(
                    participant.getUpgradeInventory(), state.nukeReady(), clear.comboCount())) {
                if (clear.isSpin()) state.incrementSpinProgress();
                else state.incrementTetrisProgress();
                log("UPGRADE_CASCADE_REACTOR", participant.getId(),
                        "combo route pip",
                        metaOf("combo", clear.comboCount(),
                                "route", clear.isSpin() ? "spin" : "tetris",
                                "tetrisProgress", state.launchTetrisProgress(),
                                "spinProgress", state.launchSpinProgress()));
            }

            // 4. Fire launch if route satisfied.
            if (state.shouldFireTetrisLaunch()) {
                fireSimplifiedLaunch(participant, state, true);
            } else if (state.shouldFireSpinLaunch()) {
                fireSimplifiedLaunch(participant, state, false);
            }
        }
    }

    // ─── Step 24 upgrade helpers ────────────────────────────────────────────

    /** Enforce the per-participant charge cap (100 base, 130 with Stockpile),
     *  but never cap below what the currently equipped nuke actually requires. */
    private void enforceCap(ParticipantState p) {
        int cap = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .effectiveChargeCap(p.getUpgradeInventory());
        int required = p.getNukeBuildState().getEffectiveBuildChargeRequired();
        cap = Math.max(cap, required);
        int cur = p.getNukeBuildState().getCurrentBuildCharge();
        if (cur > cap) p.getNukeBuildState().reduceCharge(cur - cap);
    }

    /**
     * Combined charge-gain multiplier from Wellsmith (board well condition),
     * Adrenaline Sequence (high stack), and Hair Trigger.
     * Stack height from {@link com.tetris.model.Board#getStackHeight()} is
     * measured in rows from the bottom of the board; the spec's "row N"
     * references are 1-indexed from the bottom.
     */
    private double upgradeChargeMult(ParticipantState p) {
        int stackHeight = p.getGameState().getBoard().getStackHeight();
        double m = 1.0;
        m *= com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .wellsmithMultiplier(p.getUpgradeInventory(), isWellReady(p));
        m *= com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .adrenalineMultiplier(p.getUpgradeInventory(), stackHeight >= 14);
        m *= com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .hairTriggerMultiplier(p.getUpgradeInventory());
        return m;
    }

    /**
     * True when the participant's board has a Tetris-ready well: a single
     * column whose topmost occupied row is at least 4 rows deeper than both
     * adjacent columns (or the one adjacent column for edge columns).
     * Uses {@link com.tetris.model.Board#getGridCopy()} so it never
     * mutates game state.
     */
    private boolean isWellReady(ParticipantState p) {
        java.awt.Color[][] grid = p.getGameState().getBoard().getGridCopy();
        int rows = grid.length, cols = 10;
        int[] top = new int[cols];
        for (int x = 0; x < cols; x++) {
            top[x] = rows; // default: empty column
            for (int y = 0; y < rows; y++) {
                if (grid[y][x] != null) { top[x] = y; break; }
            }
        }
        for (int x = 0; x < cols; x++) {
            boolean leftOk  = (x == 0)        || (top[x] - top[x - 1] >= 4);
            boolean rightOk = (x == cols - 1)  || (top[x] - top[x + 1] >= 4);
            if (leftOk && rightOk) return true;
        }
        return false;
    }

    /**
     * Apply Hair Trigger charge bleed to one participant.
     * Call from the controller's refresh timer; {@code elapsedMs} is the
     * milliseconds since the last call.
     * Bleed is 1 charge per second while the player hasn't cleared in the
     * last 1000 ms. Cannot reduce charge below 0.
     */
    public void tickHairTriggerBleed(ParticipantState p, long elapsedMs) {
        if (p == null || elapsedMs <= 0) return;
        if (com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .hairTriggerBleedPerSec(p.getUpgradeInventory()) <= 0) return;
        long lastClear = p.getLastClearTimeMs();
        if (lastClear >= 0 && (System.currentTimeMillis() - lastClear) < 1_000L) return;
        int bleedUnits = (int) (elapsedMs / 1000L);
        if (bleedUnits <= 0) return;
        p.getNukeBuildState().reduceCharge(bleedUnits);
    }

    private boolean hasActiveIncomingThreat(ParticipantState participant) {
        for (IncomingThreatState t : participant.getIncomingThreats()) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Step 23 \u2014 count of live incoming threats (WARNING_ACTIVE only)
     * for the given participant. Used by HUD/stage UI so resolved or
     * impact-ready threats no longer appear as a stale "INCOMING" warning.
     */
    public int countLiveIncomingThreats(ParticipantId participantId) {
        ParticipantState p = getParticipant(participantId);
        if (p == null) return 0;
        int n = 0;
        for (IncomingThreatState t : p.getIncomingThreats()) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) n++;
        }
        return n;
    }

    /**
     * Step 23 \u2014 count of impact-ready threats waiting to be resolved
     * for the given participant. The PvE timer turns these into actual
     * impacts each tick via {@link #resolveAllImpactReady()}.
     */
    public int countImpactReadyThreats(ParticipantId participantId) {
        ParticipantState p = getParticipant(participantId);
        if (p == null) return 0;
        int n = 0;
        for (IncomingThreatState t : p.getIncomingThreats()) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.IMPACT_READY) n++;
        }
        return n;
    }

    /**
     * Step 23 \u2014 prune threats that have completed their lifecycle
     * (RESOLVED / INTERCEPTED / CANCELLED) so they no longer occupy the
     * incomingThreats list. Without this, the simplified HUD counted
     * those entries as live "INCOMING" forever, which made the warning
     * stick after impact and pinned the spin-launch route in intercept
     * priority. Idempotent; safe to call from a Swing tick.
     */
    public void pruneCompletedThreats() {
        pruneThreatsFor(playerA);
        pruneThreatsFor(playerB);
    }

    private void pruneThreatsFor(ParticipantState p) {
        if (p == null) return;
        p.getIncomingThreats().removeIf(t ->
                t.getStatus() == com.tetris.mab.launch.ThreatStatus.RESOLVED
                || t.getStatus() == com.tetris.mab.launch.ThreatStatus.INTERCEPTED
                || t.getStatus() == com.tetris.mab.launch.ThreatStatus.CANCELLED);
        // Also clear out any completed launches the attacker still
        // remembers \u2014 keeps the active-launch list in sync with the
        // visible threat lifecycle for the simplified PvE HUD.
        p.getActiveLaunches().removeIf(l ->
                l.getPhase() == LaunchPhase.RESOLVED
                || l.getPhase() == LaunchPhase.CANCELLED);
    }

    /**
     * Step 22 \u2014 unified per-lock router. Replaces the line-clear-only
     * Step 21 path so that 0-line spins also count for charge / launch
     * progress / intercept routing.
     *
     * <p>Skips entirely when neither a line was cleared nor a spin was
     * detected (a normal lock with no game-meaningful event for MAB).
     */
    private void processSimplifiedClearForLockResult(ParticipantState participant,
                                                     com.tetris.events.PieceLockResult lr) {
        if (lr == null) return;
        if (lr.linesCleared() == 0 && !lr.spin()) {
            // Plain placement \u2014 nothing for the simplified router to do.
            return;
        }
        com.tetris.mab.clear.MabClearResult clear =
                com.tetris.mab.clear.MabChargeCalculator.fromLockResult(participant.getId(), lr);
        com.tetris.mab.clear.MabSimplifiedStrategicState state =
                participant.getSimplifiedState();

        // 1. Intercept priority \u2014 any spin during incoming threat.
        if (clear.isSpin() && hasActiveIncomingThreat(participant)) {
            triggerSimplifiedSpinIntercept(participant, clear);
            state.recordClear(clear.displayText(), 0, lr.backToBack());
            state.onSpinIntercept();
            return;
        }

        // 2. Charge gain (Step 24 — apply upgrade modifiers).
        int effectiveCharge = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .applyChargeModifiers(participant.getUpgradeInventory(), clear, clear.chargeGained());
        // Stateful multipliers (Wellsmith × Adrenaline × Hair Trigger).
        double fCharge = effectiveCharge * upgradeChargeMult(participant);
        // Streak Stoker: N bonus on the Nth consecutive clear; 0-line spins reset the streak.
        if (lr.linesCleared() > 0) {
            int newStreak = participant.getStreakCount() + 1;
            participant.setStreakCount(newStreak);
            fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .streakStokerBonus(participant.getUpgradeInventory(), newStreak);
        } else {
            participant.setStreakCount(0);
        }
        // Spin Network: escalating bonus per spin in this launch cycle.
        if (clear.isSpin()) {
            int priorSpins = participant.getSpinsThisCycle();
            fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .spinNetworkBonus(participant.getUpgradeInventory(), priorSpins);
            participant.setSpinsThisCycle(priorSpins + 1);
        }
        // Quiet Storm: arm after 10 s idle; fire +20 on next non-zero clear.
        long nowMs = System.currentTimeMillis();
        if (!participant.isQuietStormArmed()
                && participant.getLastClearTimeMs() >= 0
                && (nowMs - participant.getLastClearTimeMs()) >= 10_000L) {
            participant.setQuietStormArmed(true);
        }
        if (participant.isQuietStormArmed() && lr.linesCleared() > 0) {
            fCharge += com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .quietStormBonus(participant.getUpgradeInventory(), true);
            participant.setQuietStormArmed(false);
        }
        if (lr.linesCleared() > 0) participant.setLastClearTimeMs(nowMs);
        effectiveCharge = Math.max(0, (int) Math.round(fCharge));
        if (effectiveCharge > 0) {
            participant.getNukeBuildState().addCharge(effectiveCharge);
            enforceCap(participant);
        }
        boolean wasReady = state.nukeReady();
        state.syncFromNuke(
                participant.getNukeBuildState().getCurrentBuildCharge(),
                Math.max(1, participant.getNukeBuildState().getEffectiveBuildChargeRequired()));
        state.recordClear(clear.displayText(), effectiveCharge, lr.backToBack());

        Map<String, Object> chargeMeta = new LinkedHashMap<>();
        chargeMeta.put("chargeGained", effectiveCharge);
        chargeMeta.put("chargeCurrent", state.chargeCurrent());
        chargeMeta.put("chargeRequired", state.chargeRequired());
        chargeMeta.put("clear", clear.displayText());
        chargeMeta.put("piece", lr.pieceType());
        chargeMeta.put("spin", lr.spin());
        chargeMeta.put("lines", lr.linesCleared());
        log("MAB_CLEAR_CHARGE_GAINED", participant.getId(),
                clear.displayText(), chargeMeta);

        if (!wasReady && state.nukeReady()) {
            state.markNukeReady();
            Map<String, Object> readyMeta = new LinkedHashMap<>();
            readyMeta.put("chargeRequired", state.chargeRequired());
            log("MAB_NUKE_READY", participant.getId(), "nuke ready", readyMeta);
            // Manual Override: light the indicator when charge first hits 100 this cycle.
            if (!participant.isManualOverrideReadyThisCycle()
                    && participant.getUpgradeInventory().hasTag("tempo_manual_override")) {
                participant.setManualOverrideReadyThisCycle(true);
                participant.getActiveDoctrineState().resetManualOverrideCycle();
            }
        }

        // 3. Launch progress (only counts after charge is full).
        if (state.nukeReady()) {
            if (clear.tetris()) {
                state.incrementTetrisProgress();
                if (com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                        .tetrisDoctrineActive(participant.getUpgradeInventory())
                        && !participant.isTetrisDoctrineUsedThisCycle()) {
                    state.incrementTetrisProgress();
                    participant.setTetrisDoctrineUsedThisCycle(true);
                }
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("progress", state.launchTetrisProgress());
                pm.put("goal", state.launchTetrisGoal());
                log("MAB_LAUNCH_PROGRESS_TETRIS", participant.getId(),
                        "Tetrises " + state.launchTetrisProgress() + "/"
                                + state.launchTetrisGoal(), pm);
            } else if (clear.isSpin()) {
                // Step 22: 0-line spins also advance the spin route.
                state.incrementSpinProgress();
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("progress", state.launchSpinProgress());
                pm.put("goal", state.launchSpinGoal());
                pm.put("spinLines", lr.spinLines());
                log("MAB_LAUNCH_PROGRESS_SPIN", participant.getId(),
                        "Spins " + state.launchSpinProgress() + "/"
                                + state.launchSpinGoal(), pm);
            }
            if (MabUpgradeEffectResolver.cascadeReactorTriggers(
                    participant.getUpgradeInventory(), state.nukeReady(), clear.comboCount())) {
                if (clear.isSpin()) state.incrementSpinProgress();
                else state.incrementTetrisProgress();
                log("UPGRADE_CASCADE_REACTOR", participant.getId(),
                        "combo route pip",
                        metaOf("combo", clear.comboCount(),
                                "route", clear.isSpin() ? "spin" : "tetris",
                                "tetrisProgress", state.launchTetrisProgress(),
                                "spinProgress", state.launchSpinProgress()));
            }

            if (state.shouldFireTetrisLaunch()) {
                fireSimplifiedLaunch(participant, state, true);
            } else if (state.shouldFireSpinLaunch()) {
                fireSimplifiedLaunch(participant, state, false);
            }
        }
    }

    private void triggerSimplifiedSpinIntercept(ParticipantState defender,
                                                com.tetris.mab.clear.MabClearResult clear) {
        com.tetris.mab.intercept.InterceptType type;
        int spinLines = clear.spinLines();
        if (clear.perfectClear() || spinLines >= 3) {
            type = com.tetris.mab.intercept.InterceptType.FULL;
        } else if (spinLines == 2) {
            type = com.tetris.mab.intercept.InterceptType.STANDARD;
        } else {
            type = com.tetris.mab.intercept.InterceptType.EMERGENCY;
        }
        com.tetris.mab.intercept.InterceptDefinition idef =
                interceptRegistry.getByInterceptType(type);
        if (idef == null) {
            return;
        }
        Map<String, Object> trigMeta = new LinkedHashMap<>();
        trigMeta.put("spinLines", spinLines);
        trigMeta.put("interceptType", type.name());
        trigMeta.put("clear", clear.displayText());
        log("MAB_SPIN_INTERCEPT_TRIGGERED", defender.getId(),
                "Spin intercept (" + type.name() + ")", trigMeta);

        com.tetris.mab.intercept.InterceptResult result =
                resolveInterceptCore(defender, idef, /*sourceActionId*/ null);
        Map<String, Object> resMeta = new LinkedHashMap<>();
        resMeta.put("outcome", result == null ? "NULL" : String.valueOf(result.outcome()));
        resMeta.put("interceptType", type.name());
        log("MAB_INTERCEPT_RESOLVED_SIMPLIFIED", defender.getId(),
                "Intercept " + (result == null ? "no-op" : result.outcome()), resMeta);
        // Step 23 \u2014 fully-intercepted threats become INTERCEPTED;
        // drop them so the simplified HUD stops showing INCOMING.
        pruneCompletedThreats();
    }

    /**
     * Step 21 \u2014 fire a simplified launch using the existing
     * authorize-launch pipeline, then reset the simplified state.
     */
    private void fireSimplifiedLaunch(ParticipantState attacker,
                                      com.tetris.mab.clear.MabSimplifiedStrategicState state,
                                      boolean tetrisRoute) {
        int beforeTetris = state.launchTetrisProgress();
        int beforeSpin = state.launchSpinProgress();
        // authorizeLaunchInternal requires the nuke to be armed; charge
        // accumulation through addCharge() arms the nuke automatically
        // once chargeRequired is reached.
        String launchId = authorizeLaunchInternal(attacker,
                tetrisRoute ? "simplified-tetris" : "simplified-spin",
                tetrisRoute ? "simplified-route-tetris" : "simplified-route-spin");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("launchId", launchId);
        meta.put("route", tetrisRoute ? "TETRIS_4" : "SPIN_2");
        meta.put("tetrisProgress", beforeTetris);
        meta.put("spinProgress", beforeSpin);
        meta.put("chargeRequired", state.chargeRequired());
        log("MAB_LAUNCH_FIRED_SIMPLIFIED", attacker.getId(),
                "LAUNCH FIRED — " + (tetrisRoute ? "4 Tetrises" : "2 Spins"), meta);
        // Reset simplified state regardless of authorize outcome — the
        // player paid the charge/progress cost; a rejection (e.g. phase
        // gate) is logged via authorizeLaunchInternal itself.
        state.onLaunchFired(tetrisRoute);
        // Step 24 — reset per-cycle flags and apply post-launch upgrade effects.
        attacker.setTetrisDoctrineUsedThisCycle(false);
        attacker.setSpinsThisCycle(0);
        attacker.setManualOverrideReadyThisCycle(false);
        attacker.getActiveDoctrineState().resetManualOverrideCycle();
        // Rapid Assembly: +10 charge refund after firing.
        int refund = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                .onLaunchFiredChargeRefund(attacker.getUpgradeInventory());
        if (refund > 0) {
            attacker.getNukeBuildState().addCharge(refund);
            enforceCap(attacker);
        }
        state.syncFromNuke(
                attacker.getNukeBuildState().getCurrentBuildCharge(),
                Math.max(1, attacker.getNukeBuildState().getEffectiveBuildChargeRequired()));
    }

    /**
     * Step 21 \u2014 test entry point used by
     * {@link com.tetris.mab.sim.MabSimplifiedCoreProbe}. Synthesises a
     * line-clear event with the given properties and routes it through
     * the simplified clear router. Bypasses the listener / phase gate so
     * probes can exercise the routing deterministically.
     */
    public void debugSimplifiedFeedClear(ParticipantId pid,
                                         int lines,
                                         boolean spin,
                                         boolean perfectClear,
                                         boolean backToBack,
                                         int combo) {
        ParticipantState p = getParticipant(pid);
        if (p == null) return;
        boolean tetris = lines == 4 && !spin;
        boolean tSpin = spin;
        GameEventListener.LinesClearedEvent ev = new GameEventListener.LinesClearedEvent(
                lines, java.util.List.of(),
                tetris, tSpin, false,
                backToBack, perfectClear, combo);
        p.incrementPiecesLocked();
        p.addLinesCleared(lines);
        processSimplifiedClearForLineClear(p, ev);
    }

    /**
     * Step 22 \u2014 probe-only feeder for the unified per-lock router.
     * Synthesises a {@link com.tetris.events.PieceLockResult} and routes
     * it through {@link #processSimplifiedClearForLockResult}, which is
     * the production path for both line-clear and 0-line spin events.
     */
    public void debugSimplifiedFeedLockResult(ParticipantId pid,
                                              int lines,
                                              boolean spin,
                                              String spinName,
                                              boolean perfectClear,
                                              boolean backToBack,
                                              int combo) {
        ParticipantState p = getParticipant(pid);
        if (p == null) return;
        boolean tSpin = spin && spinName != null && spinName.equalsIgnoreCase("T Spin");
        boolean tSpinMini = spin && spinName != null && spinName.equalsIgnoreCase("T Spin Mini");
        com.tetris.events.PieceLockResult lr = new com.tetris.events.PieceLockResult(
                /*pieceType*/ null,
                lines,
                spin,
                tSpin,
                tSpinMini,
                spinName == null ? "" : spinName,
                perfectClear,
                backToBack,
                combo,
                /*hardDropped*/ false,
                p.getPiecesLocked() + 1);
        p.incrementPiecesLocked();
        if (lines > 0) p.addLinesCleared(lines);
        processSimplifiedClearForLockResult(p, lr);
    }

    /**
     * Step 21 \u2014 inject a synthetic incoming threat against
     * {@code defenderId} so probes can verify spin-intercept priority
     * without running the full launch countdown pipeline. Offline-only.
     *
     * <p>The threat is synthesised with a placeholder launchId so the
     * intercept resolver can find a matching launch. Returns the threat
     * id so the probe can assert mitigation/cancellation if desired.
     */
    public String debugInjectIncomingThreatForTesting(ParticipantId defenderId) {
        ParticipantState defender = getParticipant(defenderId);
        if (defender == null) return null;
        ParticipantState attacker = getOpponent(defenderId);
        if (attacker == null) return null;
        // Synthesize an active launch + threat pair so the intercept
        // resolver can find a matching launch via findLaunchById.
        String launchId = nextLaunchId();
        com.tetris.mab.nuke.NukeDesign design =
                attacker.getNukeBuildState().getCurrentDesign();
        String warnTimerId = flightTimerId(launchId);
        ActiveLaunchState launch = new ActiveLaunchState(
                launchId,
                attacker.getId(), defender.getId(),
                design.getId(), design.getDisplayName(),
                design.getDoctrineType(), design.getSizeCategory(),
                design,
                defconState.getLevel(),
                /*countdownPieces*/ 1,
                /*warnPieces*/ 5,
                /*launchTimerId*/ "debug-launch-timer-" + launchId,
                LaunchPhase.COUNTDOWN);
        launch.markInFlight(warnTimerId);
        attacker.getActiveLaunches().add(launch);
        IncomingThreatState threat = new IncomingThreatState(
                threatIdForLaunch(launchId),
                launchId,
                attacker.getId(), defender.getId(),
                design.getDoctrineType() == null ? null : design.getDoctrineType().name(),
                design.getId(), design.getDisplayName(),
                design.getDoctrineType(), design.getSizeCategory(),
                /*warningPiecesTotal*/ 5,
                warnTimerId);
        defender.getIncomingThreats().add(threat);
        log("DEBUG_THREAT_INJECTED", defenderId, threat.getThreatId(),
                metaOf("launchId", launchId, "threatId", threat.getThreatId()));
        return threat.getThreatId();
    }

    // ─────────────────────────── Provisional values ──────────────

    private static int lineClearChargeFor(GameEventListener.LinesClearedEvent e) {
        if (e.perfectClear()) return 12;
        if (e.tetris()) return e.backToBack() ? 10 : 8;
        return switch (e.count()) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> 0;
        };
    }

    private static int lineClearEscalationFor(GameEventListener.LinesClearedEvent e) {
        if (e.perfectClear()) return 10;
        if (e.tetris()) return e.backToBack() ? 7 : 5;
        return switch (e.count()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 0;
        };
    }

    // ─────────────────────────── Launch lifecycle ───────────────

    private String nextLaunchId() {
        return "launch-" + (++launchCounter);
    }

    private static String launchCountdownTimerId(String launchId) {
        return "launch_countdown:" + launchId;
    }

    private static String flightTimerId(String launchId) {
        return "flight_impact_delay:" + launchId;
    }

    private static String threatIdForLaunch(String launchId) {
        return "threat-" + launchId;
    }

    /**
     * Public read-only view of a participant's active launches.
     */
    public List<ActiveLaunchState> getActiveLaunches(ParticipantId participantId) {
        return Collections.unmodifiableList(
                new ArrayList<>(getParticipant(participantId).getActiveLaunches()));
    }

    /** Public read-only view of a participant's incoming threats. */
    public List<IncomingThreatState> getIncomingThreats(ParticipantId participantId) {
        return Collections.unmodifiableList(
                new ArrayList<>(getParticipant(participantId).getIncomingThreats()));
    }

    /** All launches across both participants currently in IMPACT_READY phase. */
    public List<ActiveLaunchState> getImpactReadyLaunches() {
        List<ActiveLaunchState> out = new ArrayList<>();
        for (ActiveLaunchState l : playerA.getActiveLaunches()) {
            if (l.getPhase() == LaunchPhase.IMPACT_READY) out.add(l);
        }
        for (ActiveLaunchState l : playerB.getActiveLaunches()) {
            if (l.getPhase() == LaunchPhase.IMPACT_READY) out.add(l);
        }
        return Collections.unmodifiableList(out);
    }

    /** All threats across both participants currently in IMPACT_READY status. */
    public List<IncomingThreatState> getImpactReadyThreats() {
        List<IncomingThreatState> out = new ArrayList<>();
        for (IncomingThreatState t : playerA.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.IMPACT_READY) out.add(t);
        }
        for (IncomingThreatState t : playerB.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.IMPACT_READY) out.add(t);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Authorize a real launch from a completed launch action. Runs the
     * Step-5 authorization sequence: validate guards, build the
     * {@link ActiveLaunchState}, register the launch countdown timer,
     * consume nuke charge, and apply DEFCON escalation.
     */
    private String authorizeLaunchFromAction(ParticipantState attacker,
                                              com.tetris.mab.action.ActionCodeDefinition definition) {
        return authorizeLaunchInternal(attacker, definition.getId(), "action");
    }

    /**
     * Step 12: shared launch-authorization core used by both the
     * action-code path ({@link #authorizeLaunchFromAction}) and the
     * debug/test entry point ({@link #debugStartLaunch}).
     *
     * <p>Validates phase, requires armed nuke, builds the
     * {@link ActiveLaunchState}, registers the launch countdown timer,
     * consumes nuke charge, and applies DEFCON escalation. {@code actionId}
     * may be {@code null} (debug path); {@code source} is recorded in
     * metadata to distinguish caller origin.
     */
    private String authorizeLaunchInternal(ParticipantState attacker,
                                            String actionId,
                                            String source) {
        if (!isGameplayMutationAllowed()) {
            log("LAUNCH_AUTHORIZATION_REJECTED_PHASE", attacker.getId(),
                    actionId == null ? source : actionId, ignoredMeta(attacker.getId()));
            return null;
        }
        if (!attacker.getNukeBuildState().isArmed()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("actionId", actionId);
            meta.put("source", source);
            meta.put("design", attacker.getNukeBuildState().getCurrentDesign().getId());
            log("LAUNCH_AUTHORIZATION_REJECTED_NOT_ARMED", attacker.getId(),
                    "nuke not armed", meta);
            return null;
        }

        ParticipantState defender = getOpponent(attacker.getId());
        NukeDesign design = attacker.getNukeBuildState().getCurrentDesign();
        int defcon = defconState.getLevel();
        int countdownPieces = 0; // nuke goes in-flight immediately on fire
        int warnPieces = design.effectiveWarningTimePieces(defcon);

        String launchId = nextLaunchId();
        String launchTimerId = launchCountdownTimerId(launchId);
        ActiveLaunchState launch = new ActiveLaunchState(
                launchId,
                attacker.getId(), defender.getId(),
                design.getId(), design.getDisplayName(),
                design.getDoctrineType(), design.getSizeCategory(),
                design,
                defcon, countdownPieces, warnPieces,
                launchTimerId, LaunchPhase.COUNTDOWN);
        int outgoingBonus = MabUpgradeEffectResolver.extraOutgoingGarbage(
                attacker.getUpgradeInventory());
        int fuseBonus = MabUpgradeEffectResolver.lightTheFuseBonus(
                attacker.getUpgradeInventory(), attacker.isLightTheFuseUsed());
        if (fuseBonus > 0) {
            attacker.setLightTheFuseUsed(true);
            outgoingBonus += fuseBonus;
        }
        if (outgoingBonus > 0) {
            launch.addExtraGarbageLines(outgoingBonus);
        }
        attacker.getActiveLaunches().add(launch);

        // Launch countdown timer ticks on the attacker's own piece locks.
        // With countdownPieces=0 this fires immediately on addTimer, so flush now.
        pieceTimerManager.addTimer(new PieceCountdownTimer(
                launchTimerId, attacker.getId(), TimerAdvanceMode.OWNER_PIECES,
                countdownPieces, "launch_countdown:" + launchId));
        for (PieceCountdownTimer t : pieceTimerManager.consumeCompletedTimers()) {
            routeCompletedTimer(t);
        }

        // Consume nuke charge but preserve the design selection.
        attacker.getNukeBuildState().resetCharge();

        // DEFCON escalation tied to launch authorization (size-based).
        int escalation = launchEscalationForSize(design.getSizeCategory());
        DefconChangeResult dcr = (escalation > 0)
                ? defconState.addEscalation(escalation, "launch:" + design.getSizeCategory())
                : null;
        handleDefconChange(dcr);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("launchId", launchId);
        meta.put("actionId", actionId);
        meta.put("source", source);
        meta.put("designId", design.getId());
        meta.put("designName", design.getDisplayName());
        meta.put("doctrine", design.getDoctrineType());
        meta.put("size", design.getSizeCategory());
        meta.put("defcon", defcon);
        meta.put("countdownPieces", countdownPieces);
        meta.put("impactDelayPieces", warnPieces);
        meta.put("escalationAdded", escalation);
        meta.put("extraGarbageLines", outgoingBonus);
        log("LAUNCH_AUTHORIZED", attacker.getId(), design.getDisplayName(), meta);
        log("LAUNCH_COUNTDOWN_STARTED", attacker.getId(), launchTimerId, meta);
        if (fuseBonus > 0) {
            log("UPGRADE_LIGHT_THE_FUSE", attacker.getId(),
                    "first launch +1 line",
                    metaOf("launchId", launchId, "bonus", fuseBonus));
        }
        if (outgoingBonus > fuseBonus) {
            log("UPGRADE_OUTGOING_GARBAGE_APPLIED", attacker.getId(),
                    "outgoing launch bonus",
                    metaOf("launchId", launchId, "bonus", outgoingBonus - fuseBonus));
        }
        // Step 10: opponent's stored intel about us is now stale.
        markOpponentIntelStale(attacker, "launch:" + launchId);
        return launchId;
    }

    private static int launchEscalationForSize(NukeSizeCategory size) {
        if (size == null) return 25;
        return switch (size) {
            case MICRO, TACTICAL              -> 25;
            case THEATER, STRATEGIC           -> 50;
            case SUPERHEAVY, DOOMSDAY_SCALE   -> 100;
        };
    }

    /** Routes a completed timer to the appropriate launch-lifecycle handler. */
    private void routeCompletedTimer(PieceCountdownTimer timer) {
        String reason = timer.getReason();
        if (reason == null) return;
        if (reason.startsWith("launch_countdown:")) {
            String launchId = reason.substring("launch_countdown:".length());
            handleLaunchCountdownCompleted(launchId);
        } else if (reason.startsWith("flight_impact_delay:")) {
            String launchId = reason.substring("flight_impact_delay:".length());
            handleFlightWarningCompleted(launchId);
        } else if (reason.startsWith("impact_garbage_wave:")) {
            handleImpactGarbageWaveTimer(timer.getId());
        }
    }

    private ActiveLaunchState findLaunchById(String launchId) {
        for (ActiveLaunchState l : playerA.getActiveLaunches()) {
            if (launchId.equals(l.getLaunchId())) return l;
        }
        for (ActiveLaunchState l : playerB.getActiveLaunches()) {
            if (launchId.equals(l.getLaunchId())) return l;
        }
        return null;
    }

    private IncomingThreatState findThreatByLaunchId(String launchId) {
        for (IncomingThreatState t : playerA.getIncomingThreats()) {
            if (launchId.equals(t.getLaunchId())) return t;
        }
        for (IncomingThreatState t : playerB.getIncomingThreats()) {
            if (launchId.equals(t.getLaunchId())) return t;
        }
        return null;
    }

    private void handleLaunchCountdownCompleted(String launchId) {
        ActiveLaunchState launch = findLaunchById(launchId);
        if (launch == null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("launchId", launchId);
            log("LAUNCH_TIMER_ORPHANED", null, launchId, meta);
            return;
        }
        if (launch.getPhase() != LaunchPhase.COUNTDOWN
                && launch.getPhase() != LaunchPhase.AUTHORIZED) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("launchId", launchId);
            meta.put("phase", launch.getPhase());
            log("LAUNCH_TIMER_IGNORED_PHASE", launch.getAttacker(), launchId, meta);
            return;
        }
        ParticipantState defender = getParticipant(launch.getDefender());

        String warnId = flightTimerId(launchId);
        launch.markInFlight(warnId);

        IncomingThreatState threat = new IncomingThreatState(
                threatIdForLaunch(launchId),
                launchId,
                launch.getAttacker(), launch.getDefender(),
                launch.getDoctrineType() == null ? null : launch.getDoctrineType().name(),
                launch.getNukeDesignId(), launch.getNukeDisplayName(),
                launch.getDoctrineType(), launch.getSizeCategory(),
                launch.getWarningPieces(), warnId);
        defender.getIncomingThreats().add(threat);

        // Impact-delay timer owned by defender; ticks on defender's own piece
        // locks (so a 5-piece impact delay means the defender places 5 of
        // their pieces before impact).
        pieceTimerManager.addTimer(new PieceCountdownTimer(
                warnId, defender.getId(), TimerAdvanceMode.OWNER_PIECES,
                launch.getWarningPieces(), "flight_impact_delay:" + launchId));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("launchId", launchId);
        meta.put("threatId", threat.getThreatId());
        meta.put("attacker", launch.getAttacker());
        meta.put("defender", launch.getDefender());
        meta.put("designId", launch.getNukeDesignId());
        meta.put("designName", launch.getNukeDisplayName());
        meta.put("doctrine", launch.getDoctrineType());
        meta.put("size", launch.getSizeCategory());
        meta.put("impactDelayPieces", launch.getWarningPieces());
        log("LAUNCH_IN_FLIGHT", launch.getAttacker(), launchId, meta);
        log("INCOMING_THREAT_CREATED", launch.getDefender(), threat.getThreatId(), meta);
    }

    private void handleFlightWarningCompleted(String launchId) {
        ActiveLaunchState launch = findLaunchById(launchId);
        IncomingThreatState threat = findThreatByLaunchId(launchId);
        if (launch == null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("launchId", launchId);
            log("FLIGHT_TIMER_ORPHANED", null, launchId, meta);
            return;
        }
        if (launch.getPhase() == LaunchPhase.RESOLVED
                || launch.getPhase() == LaunchPhase.CANCELLED) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("launchId", launchId);
            meta.put("phase", launch.getPhase());
            log("FLIGHT_TIMER_IGNORED_PHASE", launch.getAttacker(), launchId, meta);
            return;
        }
        if (threat == null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("launchId", launchId);
            log("THREAT_TIMER_ORPHANED", launch.getDefender(), launchId, meta);
        }
        launch.markImpactReady();
        if (threat != null) threat.markImpactReady();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("launchId", launchId);
        meta.put("threatId", threat == null ? null : threat.getThreatId());
        meta.put("attacker", launch.getAttacker());
        meta.put("defender", launch.getDefender());
        meta.put("designId", launch.getNukeDesignId());
        meta.put("designName", launch.getNukeDisplayName());
        meta.put("doctrine", launch.getDoctrineType());
        meta.put("size", launch.getSizeCategory());
        log("IMPACT_READY", launch.getDefender(), launchId, meta);
    }

    // ─────────────────────────── Interception (Step 7) ───────────

    /** Routes a completed action to launch authorization, intercept resolution, or no-op. */
    private void dispatchCompletedAction(ParticipantState participant, ActionCodeDefinition def) {
        ActionType type = def.getActionType();
        if (type == ActionType.MASKED_LAUNCH) {
            // Authorize a real launch first, then create a masking decoy linked to it.
            String launchId = authorizeLaunchFromAction(participant, def);
            if (launchId != null) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("launchId", launchId);
                meta.put("actionId", def.getId());
                log("MASKED_LAUNCH_AUTHORIZED", participant.getId(), launchId, meta);
                activateDecoyFromAction(participant, def, launchId);
            }
            return;
        }
        if (type.isLaunch()) {
            authorizeLaunchFromAction(participant, def);
            return;
        }
        if (type == ActionType.CIVIL_DEFENSE) {
            activateCivilDefense(participant, "action:" + def.getId());
            return;
        }
        if (type == ActionType.ROUTE_SCAN) {
            performRadarScan(participant, RadarScanType.BASIC, "action:" + def.getId());
            return;
        }
        if (type.isDecoy()) {
            activateDecoyFromAction(participant, def, null);
            return;
        }
        InterceptDefinition idef = interceptRegistry.getByActionType(type);
        if (idef != null) {
            resolveInterceptFromAction(participant, def, idef);
        }
        // Other action types (BUILD, etc.) currently have no side effect here.
    }

    /** Select an incoming threat as the next intercept target. */
    public boolean selectInterceptTarget(ParticipantId defenderId, String threatId) {
        ParticipantState participant = getParticipant(defenderId);
        if (participant == null || threatId == null) {
            log("INTERCEPT_TARGET_SELECT_FAILED", defenderId, "invalid params",
                    metaOf("threatId", threatId, "reason", "invalid_params"));
            return false;
        }
        IncomingThreatState threat = null;
        for (IncomingThreatState t : participant.getIncomingThreats()) {
            if (threatId.equals(t.getThreatId())) { threat = t; break; }
        }
        if (threat == null) {
            log("INTERCEPT_TARGET_SELECT_FAILED", defenderId, threatId,
                    metaOf("threatId", threatId, "reason", "not_found"));
            return false;
        }
        if (threat.getStatus() != com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) {
            log("INTERCEPT_TARGET_SELECT_FAILED", defenderId, threatId,
                    metaOf("threatId", threatId, "status", threat.getStatus(),
                            "reason", "not_impact_pending"));
            return false;
        }
        participant.setSelectedInterceptThreatId(threatId);
        log("INTERCEPT_TARGET_SELECTED", defenderId, threatId,
                metaOf("threatId", threatId,
                        "launchId", threat.getLaunchId(),
                        "impactDelayPiecesRemaining", threat.getWarningPiecesRemaining()));
        return true;
    }

    /** Clear any previously-selected intercept target. */
    public boolean clearInterceptTarget(ParticipantId defenderId) {
        ParticipantState participant = getParticipant(defenderId);
        if (participant == null) return false;
        String prev = participant.getSelectedInterceptThreatId();
        participant.clearSelectedInterceptThreatId();
        log("INTERCEPT_TARGET_CLEARED", defenderId, prev,
                metaOf("previousThreatId", prev));
        return true;
    }

    /**
     * Test/debug helper: resolve an intercept of {@code actionType} against
     * the participant's selected (or first available) WARNING_ACTIVE threat,
     * without going through the action-code system.
     */
    public InterceptResult resolveInterceptForSelectedThreat(ParticipantId defenderId,
                                                             ActionType actionType) {
        ParticipantState participant = getParticipant(defenderId);
        InterceptDefinition idef = interceptRegistry.getByActionType(actionType);
        if (idef == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_INVALID_TARGET, null,
                    null, null, defenderId, "no intercept def for " + actionType);
        }
        return resolveInterceptCore(participant, idef, /*sourceActionId*/ null);
    }

    /** Action-completion path: intercept actions execute their resolver here. */
    private InterceptResult resolveInterceptFromAction(ParticipantState defender,
                                                       ActionCodeDefinition actionDef,
                                                       InterceptDefinition idef) {
        log("INTERCEPT_STARTED", defender.getId(), idef.displayName(),
                metaOf("actionId", actionDef.getId(),
                        "actionType", actionDef.getActionType(),
                        "interceptType", idef.interceptType(),
                        "interceptPower", idef.interceptPower()));
        InterceptResult result = resolveInterceptCore(defender, idef, actionDef.getId());
        if (result.outcome() == InterceptOutcome.FULLY_INTERCEPTED
                || result.outcome() == InterceptOutcome.PARTIALLY_INTERCEPTED) {
            log("INTERCEPT_RESOLVED", defender.getId(), idef.displayName(),
                    metaOfResult(result, actionDef));
        } else {
            log("INTERCEPT_FAILED", defender.getId(), idef.displayName(),
                    metaOfResult(result, actionDef));
        }
        return result;
    }

    private InterceptResult resolveInterceptCore(ParticipantState defender,
                                                 InterceptDefinition idef,
                                                 String sourceActionId) {
        if (defender == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_INVALID_TARGET, idef,
                    null, null, null, "null defender");
        }
        IncomingThreatState threat = pickInterceptTarget(defender);
        if (threat == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_NO_THREAT, idef,
                    null, null, defender.getId(), "no impact-pending threat");
        }
        ActiveLaunchState launch = findLaunchById(threat.getLaunchId());
        ParticipantState attacker = launch == null ? null : getParticipant(launch.getAttacker());

        InterceptResult result = interceptResolver.resolveIntercept(
                idef, launch, threat, defender, attacker);

        if (result.outcome() == InterceptOutcome.FULLY_INTERCEPTED) {
            // Cancel the warning timer so it cannot fire IMPACT_READY.
            String warnTimerId = threat.getFlightTimerId();
            if (warnTimerId != null && pieceTimerManager.cancelTimer(warnTimerId)) {
                log("INTERCEPT_TIMER_CANCELLED", defender.getId(), warnTimerId,
                        metaOf("timerId", warnTimerId,
                                "launchId", threat.getLaunchId(),
                                "threatId", threat.getThreatId()));
            }
            log("THREAT_FULLY_INTERCEPTED", defender.getId(), threat.getThreatId(),
                    metaOfResult(result, null));
            // Step 24 — Counterspin Training (+10) and Cold Steel (+5 when stack low).
            boolean stackLow = defender.getGameState().getBoard().getStackHeight() <= 8;
            int interceptRefund = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .onInterceptResolvedCharge(defender.getUpgradeInventory(), stackLow);
            if (interceptRefund > 0) {
                defender.getNukeBuildState().addCharge(interceptRefund);
                enforceCap(defender);
                log("UPGRADE_INTERCEPT_REFUND", defender.getId(), "intercept charge refund",
                        metaOf("refund", interceptRefund, "stackLow", stackLow));
            }
        } else if (result.outcome() == InterceptOutcome.PARTIALLY_INTERCEPTED) {
            log("THREAT_PARTIALLY_INTERCEPTED", defender.getId(), threat.getThreatId(),
                    metaOfResult(result, null));
        }

        // Clear selected target if it matches the resolved threat.
        if (threat.getThreatId().equals(defender.getSelectedInterceptThreatId())) {
            defender.clearSelectedInterceptThreatId();
        }
        return result;
    }

    /** Selected target if still warning-active, else first WARNING_ACTIVE threat. */
    private IncomingThreatState pickInterceptTarget(ParticipantState defender) {
        String sel = defender.getSelectedInterceptThreatId();
        if (sel != null) {
            for (IncomingThreatState t : defender.getIncomingThreats()) {
                if (sel.equals(t.getThreatId())
                        && t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) {
                    return t;
                }
            }
        }
        for (IncomingThreatState t : defender.getIncomingThreats()) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) return t;
        }
        return null;
    }

    private Map<String, Object> metaOfResult(InterceptResult r, ActionCodeDefinition actionDef) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (actionDef != null) {
            m.put("actionId", actionDef.getId());
            m.put("actionType", actionDef.getActionType());
        }
        m.put("interceptType", r.interceptType());
        m.put("outcome", r.outcome());
        m.put("launchId", r.launchId());
        m.put("threatId", r.threatId());
        m.put("attacker", r.attacker());
        m.put("defender", r.defender());
        m.put("interceptPower", r.interceptPower());
        m.put("threatResistance", r.threatResistance());
        m.put("blastReduction", r.blastReductionApplied());
        m.put("radiationReduction", r.radiationReductionApplied());
        m.put("disarmReduction", r.disarmReductionApplied());
        m.put("siloDamageReduction", r.siloDamageReductionApplied());
        m.put("message", r.message());
        return m;
    }

    // ─────────────────────────── Impact resolution (Step 6) ─────

    /** Public read-only view of all currently pending impact garbage waves. */
    public List<ImpactWaveState> getPendingImpactWaves() {
        return Collections.unmodifiableList(new ArrayList<>(pendingImpactWaves));
    }

    /**
     * Explicitly resolve a single IMPACT_READY launch into garbage,
     * disarm damage, and silo damage. Returns the {@link ImpactResult}
     * describing what happened (including {@link ImpactResolutionStatus}
     * skip reasons). This is the ONLY entry point for impact resolution
     * apart from {@link #resolveAllImpactReady()} — flight-warning
     * completion never auto-resolves.
     */
    public ImpactResult resolveImpact(String launchId) {
        if (launchId == null) {
            return ImpactResult.skipped(ImpactResolutionStatus.LAUNCH_NOT_FOUND, null, null,
                    "null launchId");
        }
        ActiveLaunchState launch = findLaunchById(launchId);
        if (launch == null) {
            log("IMPACT_SKIPPED", null, launchId,
                    metaOf("launchId", launchId, "reason", "launch_not_found"));
            return ImpactResult.skipped(ImpactResolutionStatus.LAUNCH_NOT_FOUND, launchId, null,
                    "launch not found");
        }
        IncomingThreatState threat = findThreatByLaunchId(launchId);
        ParticipantState attacker = getParticipant(launch.getAttacker());
        ParticipantState defender = getParticipant(launch.getDefender());

        long seq = ++impactSequenceNumber;
        log("IMPACT_RESOLUTION_STARTED", launch.getDefender(), launchId,
                metaOf("launchId", launchId,
                        "threatId", threat == null ? null : threat.getThreatId(),
                        "attacker", launch.getAttacker(),
                        "defender", launch.getDefender(),
                        "seq", seq));

        // Step 8: civil defense is only consumed when the impact will
        // actually resolve. Pre-check readiness here to avoid spending
        // protection on a SKIPPED_* result.
        CivilDefenseMitigation cdMitigation = CivilDefenseMitigation.none();
        boolean readyForImpact = defender != null
                && launch.getPhase() == LaunchPhase.IMPACT_READY
                && threat != null
                && threat.getStatus() == ThreatStatus.IMPACT_READY;
        if (readyForImpact && defender.getCivilDefenseState().hasProtection()) {
            cdMitigation = defender.getCivilDefenseState().consumeForImpact();
            if (cdMitigation.active()) {
                log("CIVIL_DEFENSE_CONSUMED", defender.getId(), launchId,
                        metaOf("launchId", launchId,
                                "source", cdMitigation.source(),
                                "chargesConsumed", cdMitigation.chargesConsumed(),
                                "shieldPiecesRemaining",
                                        defender.getCivilDefenseState().getShieldPiecesRemaining(),
                                "activeChargesRemaining",
                                        defender.getCivilDefenseState().getActiveCharges()));
            }
        }
        boolean bunkerWasUsed = defender != null && defender.isBunkerUsed();
        boolean emergencyWasUsed = defender != null && defender.isEmergencyProtocolsUsed();
        boolean hardenedWasUsed = defender != null && defender.isHardenedSilosUsed();
        boolean deadHandWasUsed = defender != null && defender.isDeadHandUsed();

        ImpactResult result = impactResolver.resolveImpact(
                launch, threat, attacker, defender, pieceTimerManager, seq,
                this::addPendingImpactWave, cdMitigation, impactGracePolicy);

        if (result.status() != ImpactResolutionStatus.RESOLVED) {
            log("IMPACT_SKIPPED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "status", result.status(),
                            "message", result.message()));
            return result;
        }

        // Sub-event logs for the resolved case.
        if (result.civilDefenseApplied()) {
            // Track cumulative reduction stats on the defender.
            NukeDesign design = launch.getNukeDesign();
            int origBlast    = design == null ? 0 : design.getBlastRating();
            int origRad      = design == null ? 0 : design.getRadiationRating();
            int origDisarm   = design == null ? 0 : design.getDisarmRating();
            int origSilo     = design == null ? 0 : design.getSiloDamageRating();
            int blastDelta   = Math.max(0, origBlast    - result.blastRating());
            int radDelta     = Math.max(0, origRad      - result.radiationRating());
            int disarmDelta  = Math.max(0, origDisarm   - result.disarmRating());
            int siloDelta    = Math.max(0, origSilo     - result.siloDamageRating());
            defender.getCivilDefenseState().addReductionStats(
                    blastDelta, radDelta, disarmDelta, siloDelta);
            log("CIVIL_DEFENSE_MITIGATION_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "chargesConsumed", result.civilDefenseChargesConsumed(),
                            "immediateRowsReduced", result.civilDefenseImmediateRowsReduced(),
                            "blastReduced", blastDelta,
                            "radiationReduced", radDelta,
                            "disarmReduced", disarmDelta,
                            "siloDamageReduced", siloDelta));
        }
        if (result.siloUpgradeDisarmReduced() > 0 || result.siloUpgradeDamageReduced() > 0) {
            log("SILO_UPGRADE_MITIGATION_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "disarmReduced", result.siloUpgradeDisarmReduced(),
                            "siloDamageReduced", result.siloUpgradeDamageReduced(),
                            "hardeningLevel", defender.getSiloState().getHardeningLevel(),
                            "deepBunkerLevel", defender.getSiloState().getDeepBunkerLevel(),
                            "distributedStockpileLevel", defender.getSiloState().getDistributedStockpileLevel(),
                            "blastDoorLevel", defender.getSiloState().getBlastDoorLevel()));
        }
        if (result.graceDeferredRows() > 0
                || result.allowedImmediateRowsAfterGrace() < result.requestedImmediateRowsBeforeGrace()) {
            log("IMPACT_GRACE_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "requestedImmediateRows", result.requestedImmediateRowsBeforeGrace(),
                            "allowedImmediateRows", result.allowedImmediateRowsAfterGrace(),
                            "deferredRows", result.graceDeferredRows()));
            if (result.graceDeferredRows() > 0) {
                log("IMPACT_GRACE_DEFERRED_GARBAGE", launch.getDefender(), launchId,
                        metaOf("launchId", launchId,
                                "deferredRows", result.graceDeferredRows(),
                                "piecesBetweenWaves", 2));
            }
            if (result.requestedImmediateRowsBeforeGrace() > 0
                    && result.allowedImmediateRowsAfterGrace() == 0) {
                log("IMPACT_GARBAGE_IMMEDIATE_SKIPPED_BY_GRACE",
                        launch.getDefender(), launchId,
                        metaOf("launchId", launchId,
                                "requestedImmediateRows", result.requestedImmediateRowsBeforeGrace(),
                                "deferredRows", result.graceDeferredRows()));
            }
        }
        if (result.garbageLinesAppliedImmediately() > 0) {
            log("IMPACT_GARBAGE_IMMEDIATE_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "rows", result.garbageLinesAppliedImmediately(),
                            "radiation", result.radiationLevel()));
        }
        if (result.garbageLinesDelayed() > 0) {
            int delayedWaveCount = countPendingWavesFor(launch.getDefender());
            log("IMPACT_GARBAGE_WAVE_SCHEDULED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "totalDelayedRows", result.garbageLinesDelayed(),
                            "pendingWavesForDefender", delayedWaveCount,
                            "radiation", result.radiationLevel()));
        }
        if (result.disarmAmountApplied() > 0 || result.defenderNukeFullyDisarmed()) {
            log("IMPACT_DISARM_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "chargeBefore", result.defenderChargeBefore(),
                            "chargeAfter", result.defenderChargeAfter(),
                            "amountApplied", result.disarmAmountApplied(),
                            "fullyDisarmed", result.defenderNukeFullyDisarmed()));
        }
        if (result.siloDamageApplied() > 0) {
            log("IMPACT_SILO_DAMAGE_APPLIED", launch.getDefender(), launchId,
                    metaOf("launchId", launchId,
                            "integrityBefore", result.siloIntegrityBefore(),
                            "integrityAfter", result.siloIntegrityAfter(),
                            "damageApplied", result.siloDamageApplied(),
                            "damageState", defender.getSiloState().getDamageState()));
        }
        log("IMPACT_RESOLVED", launch.getDefender(), launchId,
                metaOf("launchId", launchId,
                        "threatId", result.threatId(),
                        "attacker", result.attacker(),
                        "defender", result.defender(),
                        "designId", result.nukeDesignId(),
                        "designName", result.nukeDisplayName(),
                        "doctrine", result.doctrineType(),
                        "size", result.sizeCategory(),
                        "blast", result.blastRating(),
                        "radiationRating", result.radiationRating(),
                        "radiation", result.radiationLevel(),
                        "emp", launch.getNukeDesign() == null ? 0
                                : launch.getNukeDesign().getEmpRating(),
                        "disarmApplied", result.disarmAmountApplied(),
                        "siloDamageApplied", result.siloDamageApplied(),
                        "immediateRows", result.garbageLinesAppliedImmediately(),
                        "delayedRows", result.garbageLinesDelayed()));
        // Step 24 \u2014 post-impact upgrade effects (applied after the base impact resolves).
        if (defender != null
                && (result.garbageLinesAppliedImmediately() > 0 || result.garbageLinesDelayed() > 0)) {
            // Retaliation Doctrine: +20 charge after taking any garbage impact.
            int retaliationCharge = com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .onImpactTakenCharge(defender.getUpgradeInventory());
            if (retaliationCharge > 0) {
                defender.getNukeBuildState().addCharge(retaliationCharge);
                enforceCap(defender);
                log("UPGRADE_RETALIATION_DOCTRINE", defender.getId(),
                        "impact \u2192 +" + retaliationCharge + " charge",
                        metaOf("launchId", launchId, "charge", retaliationCharge));
            }
            // Reactive Plating: signal that the 6-second doubled-intercept window starts.
            if (com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver
                    .reactivePlatingActive(defender.getUpgradeInventory())) {
                log("UPGRADE_REACTIVE_PLATING", defender.getId(),
                        "intercept window doubled for 6 s",
                        metaOf("launchId", launchId));
                // TODO: wire intercept window doubling into InterceptResolver
                //       once the intercept tier ladder is specified.
            }
            if (!bunkerWasUsed && defender.isBunkerUsed()) {
                log("UPGRADE_BUNKER_TRIGGERED", defender.getId(),
                        "first impact halved",
                        metaOf("launchId", launchId));
            }
            if (!emergencyWasUsed && defender.isEmergencyProtocolsUsed()) {
                log("UPGRADE_EMERGENCY_PROTOCOLS_TRIGGERED", defender.getId(),
                        "high-stack impact softened",
                        metaOf("launchId", launchId));
            }
        }
        if (defender != null && !hardenedWasUsed && defender.isHardenedSilosUsed()) {
            log("MAB_FATAL_IMPACT_PREVENTED", defender.getId(),
                    "HARDENED SILOS HELD",
                    metaOf("launchId", launchId));
        }
        if (defender != null && !deadHandWasUsed && defender.isDeadHandUsed()) {
            log("MAB_DEAD_HAND_TRIGGERED", defender.getId(),
                    "DEAD HAND TRIGGERED",
                    metaOf("launchId", launchId, "counterLaunch", "deferred-v1"));
        }
        // Step 10: stored intel about the defender (held by attacker)
        // is now stale because defender just took damage / disarm.
        if (result.disarmAmountApplied() > 0 || result.siloDamageApplied() > 0
                || result.garbageLinesAppliedImmediately() > 0
                || result.garbageLinesDelayed() > 0) {
            markOpponentIntelStale(defender, "impact-hit:" + launchId);
        }
        // Step 23 \u2014 the threat & launch just transitioned to
        // RESOLVED inside ImpactResolver. Drop them from the per-side
        // lists so the simplified HUD stops showing a stale "INCOMING".
        pruneCompletedThreats();
        return result;
    }

    /** Resolve every IMPACT_READY launch currently in flight. */
    public List<ImpactResult> resolveAllImpactReady() {
        List<ImpactResult> out = new ArrayList<>();
        // Snapshot the launch IDs so resolveImpact's mutations don't
        // disturb iteration.
        List<String> ids = new ArrayList<>();
        for (ActiveLaunchState l : getImpactReadyLaunches()) {
            ids.add(l.getLaunchId());
        }
        for (String id : ids) {
            out.add(resolveImpact(id));
        }
        return out;
    }

    private void addPendingImpactWave(ImpactWaveState wave) {
        if (wave == null) return;
        pendingImpactWaves.add(wave);
    }

    private void handleImpactGarbageWaveTimer(String timerId) {
        ImpactWaveState wave = null;
        for (ImpactWaveState w : pendingImpactWaves) {
            if (timerId.equals(w.getTimerId())) { wave = w; break; }
        }
        if (wave == null) {
            log("IMPACT_WAVE_TIMER_ORPHANED", null, timerId,
                    metaOf("timerId", timerId));
            return;
        }
        if (wave.isApplied()) {
            log("IMPACT_WAVE_ALREADY_APPLIED", wave.getDefender(), wave.getWaveId(),
                    metaOf("waveId", wave.getWaveId()));
            return;
        }
        ParticipantState defender = getParticipant(wave.getDefender());
        if (defender == null) {
            log("IMPACT_WAVE_TIMER_ORPHANED", null, timerId,
                    metaOf("waveId", wave.getWaveId(), "reason", "defender_not_found"));
            return;
        }
        var patterns = radiationGenerator.generate(
                wave.getRows(), com.tetris.model.Board.WIDTH, wave.getRadiationLevel(),
                wave.getLaunchId() + ":wave:" + wave.getWaveIndex());
        String source = "nuke:" + wave.getLaunchId()
                + ":wave:" + (wave.getWaveIndex() + 1)
                + "/" + wave.getTotalWaves()
                + ":" + wave.getRadiationLevel().name().toLowerCase();
        defender.getGameState().insertGarbagePattern(patterns, source);
        wave.markApplied();
        log("IMPACT_GARBAGE_WAVE_APPLIED", wave.getDefender(), wave.getWaveId(),
                metaOf("waveId", wave.getWaveId(),
                        "launchId", wave.getLaunchId(),
                        "waveIndex", wave.getWaveIndex() + 1,
                        "totalWaves", wave.getTotalWaves(),
                        "rows", wave.getRows(),
                        "radiation", wave.getRadiationLevel()));
    }

    private static Map<String, Object> metaOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ─────────────────────────── Civil defense (Step 8) ──────────

    /** Read-only access to a participant's civil-defense state. */
    public CivilDefenseState getCivilDefenseState(ParticipantId participantId) {
        ParticipantState p = getParticipant(participantId);
        return p == null ? null : p.getCivilDefenseState();
    }

    /**
     * Manually activate civil defense for a participant outside the
     * action-code path. Intended for tests/debug overlays. Obeys the
     * standard phase guard (ACTIVE, not paused, no winner).
     */
    public boolean activateCivilDefenseManually(ParticipantId participantId) {
        if (!isGameplayMutationAllowed()) {
            log("CIVIL_DEFENSE_MANUAL_ACTIVATION_IGNORED", participantId,
                    "phase guard",
                    metaOf("phase", currentPhase, "paused", paused, "winner", winner));
            return false;
        }
        ParticipantState p = getParticipant(participantId);
        if (p == null) {
            log("CIVIL_DEFENSE_MANUAL_ACTIVATION_IGNORED", participantId,
                    "unknown participant", metaOf("reason", "unknown_participant"));
            return false;
        }
        activateCivilDefense(p, "manual");
        return true;
    }

    private void activateCivilDefense(ParticipantState participant, String source) {
        CivilDefenseState cds = participant.getCivilDefenseState();
        cds.activateBasic(CIVIL_DEFENSE_SHIELD_PIECES);
        log("CIVIL_DEFENSE_ACTIVATED", participant.getId(), source,
                metaOf("source", source,
                        "activeCharges", cds.getActiveCharges(),
                        "maxCharges", cds.getMaxCharges(),
                        "shieldPiecesRemaining", cds.getShieldPiecesRemaining(),
                        "totalActivations", cds.getTotalActivations()));
    }

    // ─────────────────────────── Logging ─────────────────────────

    private void log(String type, ParticipantId pid, String message, Map<String, Object> metadata) {
        eventLog.add(new MatchEventLogEntry(++eventSequenceNumber, type, pid, message, metadata));
        // Trim to a bounded ring so the log doesn't grow without limit.
        if (eventLog.size() > LOG_RING_SIZE) {
            eventLog.subList(0, eventLog.size() - LOG_RING_SIZE).clear();
        }
    }

    // ─────────────── Step 12 debug / vertical-slice hooks ─────────────
    //
    // These methods exist to make Mutually Assured Blocks visibly testable
    // from the debug HUD. They are NOT final-game hooks; every emitted log
    // type is prefixed with `DEBUG_` so they are easy to identify in
    // recordings or test traces. Each method honours the standard phase
    // guard (ACTIVE only, except where noted) so it cannot create state
    // during SETUP or after GAME_OVER.

    private boolean debugPhaseAllowsCharge() {
        return (currentPhase == MatchPhase.ACTIVE && !paused && winner == null)
                || currentPhase == MatchPhase.UPGRADE_PAUSE;
    }

    /**
     * Step 12: directly add nuke build charge to a participant. Allowed
     * during {@code ACTIVE} or {@code UPGRADE_PAUSE}. Returns {@code false}
     * if the phase guard fails or {@code amount <= 0}.
     */
    public boolean debugAddNukeCharge(ParticipantId participantId, int amount) {
        if (amount <= 0) return false;
        if (!debugPhaseAllowsCharge()) {
            log("DEBUG_NUKE_CHARGE_REJECTED", participantId,
                    "phase=" + currentPhase,
                    metaOf("phase", currentPhase, "paused", paused, "amount", amount));
            return false;
        }
        ParticipantState p = getParticipant(participantId);
        if (p == null) return false;
        NukeBuildState nb = p.getNukeBuildState();
        int before = nb.getCurrentBuildCharge();
        nb.addCharge(amount);
        p.getSimplifiedState().syncFromNuke(
                nb.getCurrentBuildCharge(),
                Math.max(1, nb.getEffectiveBuildChargeRequired()));
        if (nb.isArmed()
                && p.getUpgradeInventory().hasTag("tempo_manual_override")) {
            p.setManualOverrideReadyThisCycle(true);
            p.getActiveDoctrineState().resetManualOverrideCycle();
        }
        log("DEBUG_NUKE_CHARGE_ADDED", participantId,
                "+" + amount + " -> " + nb.getCurrentBuildCharge() + "/" + nb.getEffectiveBuildChargeRequired(),
                metaOf("participant", participantId,
                        "amount", amount,
                        "chargeBefore", before,
                        "chargeAfter", nb.getCurrentBuildCharge(),
                        "required", nb.getEffectiveBuildChargeRequired(),
                        "armed", nb.isArmed()));
        return true;
    }

    /**
     * Step 12: top off the current nuke design to its armed threshold.
     * Returns {@code true} if the nuke is armed after the call.
     */
    public boolean debugArmCurrentNuke(ParticipantId participantId) {
        if (!debugPhaseAllowsCharge()) {
            log("DEBUG_NUKE_ARM_REJECTED", participantId,
                    "phase=" + currentPhase,
                    metaOf("phase", currentPhase, "paused", paused));
            return false;
        }
        ParticipantState p = getParticipant(participantId);
        if (p == null) return false;
        NukeBuildState nb = p.getNukeBuildState();
        int needed = Math.max(0, nb.getEffectiveBuildChargeRequired() - nb.getCurrentBuildCharge());
        if (needed > 0) nb.addCharge(needed);
        p.getSimplifiedState().syncFromNuke(
                nb.getCurrentBuildCharge(),
                Math.max(1, nb.getEffectiveBuildChargeRequired()));
        if (nb.isArmed()
                && p.getUpgradeInventory().hasTag("tempo_manual_override")) {
            p.setManualOverrideReadyThisCycle(true);
            p.getActiveDoctrineState().resetManualOverrideCycle();
        }
        log("DEBUG_NUKE_ARMED", participantId,
                nb.getCurrentDesign().getId(),
                metaOf("participant", participantId,
                        "designId", nb.getCurrentDesign().getId(),
                        "chargeAdded", needed,
                        "currentCharge", nb.getCurrentBuildCharge(),
                        "required", nb.getEffectiveBuildChargeRequired(),
                        "armed", nb.isArmed()));
        return nb.isArmed();
    }

    /**
     * Step 12: directly authorize a launch from the debug HUD. If the
     * current nuke is not armed it will be armed first (charge is added).
     * Returns {@code true} if an {@link ActiveLaunchState} was created.
     */
    public boolean debugStartLaunch(ParticipantId participantId) {
        if (!isGameplayMutationAllowed()) {
            log("DEBUG_LAUNCH_REJECTED", participantId,
                    "phase=" + currentPhase, ignoredMeta(participantId));
            return false;
        }
        ParticipantState p = getParticipant(participantId);
        if (p == null) return false;
        if (!p.getNukeBuildState().isArmed()) {
            // Auto-arm to make the debug button do what the player expects.
            int needed = Math.max(0, p.getNukeBuildState().getEffectiveBuildChargeRequired()
                    - p.getNukeBuildState().getCurrentBuildCharge());
            if (needed > 0) p.getNukeBuildState().addCharge(needed);
        }
        String launchId = authorizeLaunchInternal(p, null, "debug");
        if (launchId == null) {
            log("DEBUG_LAUNCH_REJECTED", participantId,
                    "authorization failed",
                    metaOf("participant", participantId));
            return false;
        }
        log("DEBUG_LAUNCH_STARTED", participantId, launchId,
                metaOf("participant", participantId, "launchId", launchId));
        return true;
    }

    /**
     * Step 12: resolve every {@code IMPACT_READY} launch/threat. Wraps
     * {@link #resolveAllImpactReady()} and emits a {@code DEBUG_IMPACTS_RESOLVED}
     * log entry summarising the count.
     */
    public List<ImpactResult> debugResolveAllImpacts() {
        List<ImpactResult> results = resolveAllImpactReady();
        log("DEBUG_IMPACTS_RESOLVED", null,
                "resolved=" + results.size(),
                metaOf("count", results.size()));
        return results;
    }

    /**
     * Step 12: perform an unconditional {@code DEBUG} radar scan from the
     * given scanner. Falls back to a failure result if gameplay mutation
     * is not allowed.
     */
    public RadarScanResult debugRadarScan(ParticipantId scannerId) {
        RadarScanResult r = performRadarScan(scannerId, RadarScanType.DEBUG);
        log("DEBUG_ROUTE_SCAN", scannerId,
                r == null ? "null" : (r.intelLevel() + " conf=" + r.confidencePercent()),
                metaOf("scanner", scannerId,
                        "success", r != null && r.success(),
                        "readoutLevel", r == null ? null : r.intelLevel(),
                        "confidence", r == null ? -1 : r.confidencePercent()));
        return r;
    }

    /** Step 12: civil-defence activation from the debug HUD. */
    public boolean debugActivateCivilDefense(ParticipantId participantId) {
        boolean ok = activateCivilDefenseManually(participantId);
        if (ok) {
            log("DEBUG_CIVIL_DEFENSE_ACTIVATED", participantId,
                    "ok", metaOf("participant", participantId));
        }
        return ok;
    }

    /** Step 12: decoy activation from the debug HUD. */
    public DecoyResolutionResult debugActivateDecoy(ParticipantId participantId, DecoyType type) {
        DecoyResolutionResult r = activateDecoyManually(participantId, type);
        if (r != null && r.success()) {
            log("DEBUG_FEINT_ACTIVATED", participantId,
                    type == null ? "null" : type.name(),
                    metaOf("participant", participantId,
                            "type", type,
                            "feintId", r.decoyId()));
        }
        return r;
    }

    // ─────────────── Step 13: strategic-clock helper + AI hooks ───────

    /**
     * Step 13: shared strategic-clock side effects performed when a
     * participant "locks" a piece. Called by both the engine event
     * listener (real visible pieces) and
     * {@link #debugAdvanceStrategicClockOnly(ParticipantId, int)}
     * (hidden PvE simulation only). Never touches {@code GameState}.
     */
    private void advanceStrategicPieceClockFor(ParticipantState participant, String source) {
        participant.incrementPiecesLocked();
        // Radar staleness piece ticks (silent).
        participant.getRadarIntel().tickScannerPiece();
        ParticipantState opp = getOpponent(participant.getId());
        if (opp != null) opp.getRadarIntel().tickTargetPiece();
        // Feint expirations (logs FEINT_EXPIRED internally).
        tickAndExpireDecoys(participant);
        // Civil-defence shield decay.
        CivilDefenseState cds = participant.getCivilDefenseState();
        int shieldBefore = cds.getShieldPiecesRemaining();
        cds.tickPiece();
        if (shieldBefore > 0 && cds.getShieldPiecesRemaining() == 0) {
            log("CIVIL_DEFENSE_EXPIRED", participant.getId(),
                    "shield expired",
                    metaOf("activeCharges", cds.getActiveCharges(),
                            "totalActivations", cds.getTotalActivations(),
                            "source", source));
        }
        // Defender warning ticks for WARNING_ACTIVE incoming threats.
        for (IncomingThreatState threat : participant.getIncomingThreats()) {
            if (threat.getStatus() == ThreatStatus.WARNING_ACTIVE) {
                threat.decrementWarningPieces();
            }
        }
        pieceTimerManager.advanceForPieceLocked(participant.getId());
        List<PieceCountdownTimer> done = pieceTimerManager.consumeCompletedTimers();
        for (PieceCountdownTimer t : done) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("timerId", t.getId());
            meta.put("owner", t.getOwner());
            meta.put("mode", t.getAdvanceMode());
            meta.put("reason", t.getReason());
            meta.put("source", source);
            log("TIMER_COMPLETED", t.getOwner(), t.toString(), meta);
            routeCompletedTimer(t);
        }
    }

    /**
     * Step 13: advances ONLY the strategic / MAB-side clock for a
     * participant (typically the hidden PvE opponent). Does not call
     * any {@code GameState} method, so it never moves visible pieces
     * or alters score. Honours the standard phase guard. Returns the
     * number of strategic ticks actually applied.
     */
    public int debugAdvanceStrategicClockOnly(ParticipantId participantId, int pieces) {
        if (pieces <= 0) return 0;
        if (!isGameplayMutationAllowed()) {
            log("DEBUG_STRATEGIC_CLOCK_REJECTED", participantId,
                    "phase=" + currentPhase,
                    metaOf("phase", currentPhase, "paused", paused, "pieces", pieces));
            return 0;
        }
        ParticipantState p = getParticipant(participantId);
        if (p == null) return 0;
        for (int i = 0; i < pieces; i++) {
            advanceStrategicPieceClockFor(p, "debug-strategic-clock");
        }
        log("AI_STRATEGIC_CLOCK_ADVANCED", participantId,
                "+" + pieces + " strategic pieces",
                metaOf("participant", participantId,
                        "pieces", pieces,
                        "totalPieces", p.getPiecesLocked()));
        return pieces;
    }

    /**
     * Step 13: external-caller logging hook. Used by the PvE AI driver
     * (and any other in-process debug overlay) to add events to the
     * match log without touching the internal mutable list. Event types
     * should be prefixed (e.g. {@code AI_*}, {@code DEBUG_*}).
     */
    public void debugLogEvent(String eventType, ParticipantId participantId,
                              String message, Map<String, Object> metadata) {
        if (eventType == null || eventType.isBlank()) return;
        log(eventType, participantId, message == null ? "" : message,
                metadata == null ? Collections.emptyMap() : new LinkedHashMap<>(metadata));
    }

    /**
     * Step 18 refinement — records a non-debug, player-facing event from
     * an in-process UI/controller (e.g. {@code MATCH_ENDED} from the
     * MAB PvE result detection flow). Unlike {@link #debugLogEvent},
     * this method is intended for events that the player-facing feed
     * surfaces, so the type is not prefixed with {@code DEBUG_}.
     *
     * <p>Local in-process only — never carries network payloads. The
     * metadata map is defensively copied; the internal mutable event log
     * is never exposed.
     */
    public void recordPlayerFacingEvent(String eventType, ParticipantId participantId,
                                        String message, Map<String, Object> metadata) {
        if (eventType == null || eventType.isBlank()) return;
        log(eventType, participantId, message == null ? "" : message,
                metadata == null ? Collections.emptyMap() : new LinkedHashMap<>(metadata));
    }

    /**
     * Step 14: debug helper that grants upgrade points to a participant
     * outside the normal line-clear / DEFCON milestone path. Sim and
     * debug contexts only.
     */
    public boolean debugAddUpgradePoints(ParticipantId participantId, int points) {
        if (points <= 0) return false;
        ParticipantState p = getParticipant(participantId);
        if (p == null) return false;
        p.getUpgradeState().addUpgradePoints(points);
        log("DEBUG_UPGRADE_POINTS_ADDED", participantId,
                "+" + points + " upgrade points",
                metaOf("participant", participantId,
                        "added", points,
                        "totalAfter", p.getUpgradeState().getUpgradePoints()));
        return true;
    }
}
