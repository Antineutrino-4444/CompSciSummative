package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

import javax.swing.*;
import java.awt.*;

/**
 * Step 15 — owns the player-facing MAB HUD window and the hidden
 * Player B AI driver for the offline PvE slice.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #start()} builds a companion {@link JFrame} containing
 *       a {@link MabHudPanel}, enables the AI for Player B, and
 *       starts two Swing timers — one for the HUD refresh and one
 *       for the AI tick.</li>
 *   <li>{@link #shutdown()} stops both timers, disables the AI, and
 *       closes the companion frame.</li>
 * </ol>
 *
 * <p>This class is display + lifecycle glue only. It does not call
 * any {@code debug*} mutator on the match.
 *
 * <p><b>Offline-only.</b>
 */
public class MabPlayerFacingController {

    private static final int HUD_REFRESH_MS = 400;
    private static final int AI_TICK_MS = 500;

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId humanId;
    private final MabAiDriver aiDriver;
    private final MabAiArchetype aiArchetype;
    private final MabAiDifficulty aiDifficulty;
    private final MabBalanceProfile balanceProfile;

    private JFrame frame;
    private MabHudPanel hudPanel;
    private MabUpgradeWindow upgradeWindow;
    private MabCommandGuideWindow commandGuideWindow;
    private MabMatchResultDialog resultDialog;
    private Timer hudTimer;
    private Timer aiTimer;
    private boolean started;
    private boolean resultShown;
    private Runnable onRestart;
    private Runnable onBackToMenu;
    /** Step 20-refinement: when set, post-match result is delivered here
     *  (embedded into the main PvE window) instead of opening the
     *  separate {@link MabMatchResultDialog} JFrame. */
    private java.util.function.Consumer<MabMatchResultSummary> embeddedResultSink;

    public MabPlayerFacingController(MutuallyAssuredBlocksMatch match, ParticipantId humanId) {
        this(match, humanId, MabAiArchetype.BALANCED, MabAiDifficulty.NORMAL,
                MabBalanceProfiles.standardPve());
    }

    public MabPlayerFacingController(MutuallyAssuredBlocksMatch match,
                                     ParticipantId humanId,
                                     MabAiArchetype archetype,
                                     MabAiDifficulty difficulty) {
        this(match, humanId, archetype, difficulty, MabBalanceProfiles.standardPve());
    }

    public MabPlayerFacingController(MutuallyAssuredBlocksMatch match,
                                     ParticipantId humanId,
                                     MabAiArchetype archetype,
                                     MabAiDifficulty difficulty,
                                     MabBalanceProfile balanceProfile) {
        if (match == null) throw new IllegalArgumentException("match");
        this.match = match;
        this.humanId = (humanId == null) ? ParticipantId.PLAYER_A : humanId;
        this.aiArchetype = (archetype == null) ? MabAiArchetype.BALANCED : archetype;
        this.aiDifficulty = (difficulty == null) ? MabAiDifficulty.NORMAL : difficulty;
        this.balanceProfile = (balanceProfile == null)
                ? MabBalanceProfiles.standardPve() : balanceProfile;
        ParticipantId aiId = (this.humanId == ParticipantId.PLAYER_A)
                ? ParticipantId.PLAYER_B : ParticipantId.PLAYER_A;
        this.aiDriver = new MabAiDriver(match, aiId, this.aiArchetype, this.aiDifficulty,
                this.balanceProfile);
    }

    /** Step 18 — callbacks for the post-match result dialog. */
    public void setRestartCallback(Runnable r) { this.onRestart = r; }
    public void setBackToMenuCallback(Runnable r) { this.onBackToMenu = r; }

    /** Step 20-refinement: install an embedded sink for the post-match
     *  result. When non-null, the legacy {@link MabMatchResultDialog}
     *  JFrame is suppressed and the summary is forwarded to the sink. */
    public void setEmbeddedResultSink(java.util.function.Consumer<MabMatchResultSummary> sink) {
        this.embeddedResultSink = sink;
    }

    /**
     * Step 20 — initialise the HUD panel + supporting windows + timers
     * without opening a companion {@link JFrame}. Returns the embedded
     * {@link MabHudPanel} so callers can drop it into a {@link MabPveGamePanel}.
     */
    public MabHudPanel startEmbedded() {
        if (started) return hudPanel;
        started = true;
        initSharedUiAndTimers();
        return hudPanel;
    }

    /** Builds the HUD companion window and starts AI + refresh timers. */
    public void start() {
        if (started) return;
        started = true;

        initSharedUiAndTimers();

        frame = new JFrame("Mutually Assured Blocks — PvE HUD");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setContentPane(hudPanel);
        frame.pack();
        // Place to the right of the primary screen if possible.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = Math.max(0, screen.width - frame.getWidth() - 32);
        frame.setLocation(x, 64);
        frame.setVisible(true);
    }

    private void initSharedUiAndTimers() {
        hudPanel = new MabHudPanel();

        upgradeWindow = new MabUpgradeWindow(match, humanId);
        upgradeWindow.setClosePauseAction(this::requestCloseUpgradePause);
        commandGuideWindow = new MabCommandGuideWindow(match, humanId);
        resultDialog = new MabMatchResultDialog();
        resultDialog.setRestartCallback(() -> {
            resultDialog.setVisible(false);
            if (onRestart != null) onRestart.run();
        });
        resultDialog.setBackToMenuCallback(() -> {
            resultDialog.setVisible(false);
            if (onBackToMenu != null) onBackToMenu.run();
        });
        hudPanel.setOpenUpgradeAction(this::requestUpgradePause);
        hudPanel.setCloseUpgradeAction(this::requestCloseUpgradePause);
        hudPanel.setOpenCommandGuideAction(this::openCommandGuide);
        hudPanel.setOpponentInfo(aiArchetype, aiDifficulty);
        hudPanel.setBalanceProfileName(balanceProfile.getDisplayName());

        aiDriver.setEnabled(true);

        hudTimer = new Timer(HUD_REFRESH_MS, e -> {
            if (hudPanel != null) hudPanel.refresh(match, humanId);
            if (upgradeWindow != null && upgradeWindow.isVisible()) upgradeWindow.refresh();
            maybeShowResult();
        });
        hudTimer.setRepeats(true);
        hudTimer.start();

        aiTimer = new Timer(AI_TICK_MS, e -> {
            try { aiDriver.tick(); } catch (RuntimeException ignored) {}
        });
        aiTimer.setRepeats(true);
        aiTimer.start();

        // Initial paint.
        hudPanel.refresh(match, humanId);
    }

    /** Stops timers, disables AI, and disposes the companion window. */
    public void shutdown() {
        if (hudTimer != null) { hudTimer.stop(); hudTimer = null; }
        if (aiTimer != null) { aiTimer.stop(); aiTimer = null; }
        try { aiDriver.setEnabled(false); } catch (RuntimeException ignored) {}
        if (upgradeWindow != null) {
            upgradeWindow.setVisible(false);
            upgradeWindow.dispose();
            upgradeWindow = null;
        }
        if (commandGuideWindow != null) {
            commandGuideWindow.setVisible(false);
            commandGuideWindow.dispose();
            commandGuideWindow = null;
        }
        if (resultDialog != null) {
            resultDialog.setVisible(false);
            resultDialog.dispose();
            resultDialog = null;
        }
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
            frame = null;
        }
        hudPanel = null;
        started = false;
    }

    /** Opens UPGRADE_PAUSE on the match and shows the upgrade window. */
    public void requestUpgradePause() {
        boolean ok;
        try { ok = match.openUpgradePause("player-facing-upgrade-ui"); }
        catch (RuntimeException ex) { ok = false; }
        if (ok && upgradeWindow != null) {
            upgradeWindow.refresh();
            upgradeWindow.setVisible(true);
            upgradeWindow.toFront();
        }
    }

    /** Closes UPGRADE_PAUSE on the match and hides the upgrade window. */
    public void requestCloseUpgradePause() {
        try { match.closeUpgradePause("player-facing-upgrade-ui"); }
        catch (RuntimeException ignored) {}
        if (upgradeWindow != null) {
            upgradeWindow.refresh();
            upgradeWindow.setVisible(false);
        }
    }

    public MabUpgradeWindow getUpgradeWindow() { return upgradeWindow; }

    /** Opens the command guide window (or focuses it if already open). */
    public void openCommandGuide() {
        if (commandGuideWindow == null) return;
        commandGuideWindow.refresh();
        commandGuideWindow.setVisible(true);
        commandGuideWindow.toFront();
    }

    public MabCommandGuideWindow getCommandGuideWindow() { return commandGuideWindow; }

    /** Step 18 — if the match has ended and we have not yet shown the
     *  result dialog, build a summary and show it once. */
    private void maybeShowResult() {
        if (resultShown || resultDialog == null) return;
        if (!match.isGameOver()) return;
        resultShown = true;
        try { aiDriver.setEnabled(false); } catch (RuntimeException ignored) {}
        if (aiTimer != null) { aiTimer.stop(); }
        try {
            MabMatchResultSummary summary = MabMatchResultSummary.from(match, humanId);
            // Step 18 refinement: emit one MATCH_ENDED player-facing event so
            // the in-match log and HUD feed include an explicit end-state row.
            // Guarded by `resultShown` above, so this fires at most once per
            // completed match.
            try {
                java.util.LinkedHashMap<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("winner", String.valueOf(summary.getWinner()));
                meta.put("playerId", String.valueOf(summary.getPlayerId()));
                meta.put("title", summary.getTitle());
                meta.put("reason", summary.getReason() == null ? "" : summary.getReason());
                meta.put("defconLevel", summary.getDefconLevel());
                meta.put("totalEvents", summary.getTotalEvents());
                meta.put("launchesAuthorized", summary.getLaunchesAuthorized());
                meta.put("impactsResolved", summary.getImpactsResolved());
                meta.put("radarScans", summary.getRadarScans());
                meta.put("decoysActivated", summary.getDecoysActivated());
                meta.put("civilDefenseActivations", summary.getCivilDefenseActivations());
                meta.put("upgradesApplied", summary.getUpgradesApplied());
                meta.put("finalNukeDesign", summary.getFinalNukeDesign() == null ? "" : summary.getFinalNukeDesign());
                meta.put("source", "mab-pve-result");
                match.recordPlayerFacingEvent("MATCH_ENDED", humanId, summary.getTitle(), meta);
                // Rebuild summary so the highlight events include MATCH_ENDED.
                summary = MabMatchResultSummary.from(match, humanId);
            } catch (RuntimeException emitEx) {
                System.err.println("[MAB] MATCH_ENDED emit failed: " + emitEx.getMessage());
            }
            if (embeddedResultSink != null) {
                try { embeddedResultSink.accept(summary); }
                catch (RuntimeException sinkEx) {
                    System.err.println("[MAB] embedded result sink failed: " + sinkEx.getMessage());
                }
            } else {
                resultDialog.show(summary);
            }
        } catch (RuntimeException ex) {
            // If anything goes wrong building the summary, log to stderr
            // but never let it cascade into the player-facing UI.
            System.err.println("[MAB] result summary failed: " + ex.getMessage());
        }
    }

    public MabAiDriver getAiDriver() { return aiDriver; }
    public MabHudPanel getHudPanel() { return hudPanel; }
}
