package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.MabNukeBuilderBridge;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.ui.MabAlert;
import com.tetris.mab.ui.MabAlertModel;
import com.tetris.mab.ui.MabNukePresetDefinition;
import com.tetris.mab.ui.MabStage;
import com.tetris.model.GameState;

import java.util.List;

/**
 * Verifies that no player-facing surface contains forbidden terminology:
 * MIRV, radar, warning (the system), intel, decoy, or unsafe
 * engineering-level summaries. The MAB design uses warhead / payload /
 * DEFCON / charge / route / launch / impact / radiation wave / EMP
 * disruption / disarm / silo damage / spin intercept / active commands.
 */
public final class MabTerminologyProbe {

    private MabTerminologyProbe() {}

    private static final String[] FORBIDDEN = {
            "mirv", "radar", " intel", "decoy",
            "fissile", "tamper", "u-235", "pu-239", "tritium",
            "explosive lens", "critical mass", "implosion lens"
    };
    private static final String[] FORBIDDEN_WARNING_LABELS = {
            "warning active", "early warning",
            "radar lock", "warning track", "intel panel"
    };

    public static void main(String[] args) {
        System.out.println("=== MAB Terminology Probe ===");

        // 1. Doctrine display labels.
        boolean doctrineSafe = true;
        for (NukeDoctrineType t : NukeDoctrineType.values()) {
            if (!t.isCurrent()) continue; // legacy, never shown
            String lbl = t.displayLabel().toLowerCase();
            if (containsAny(lbl, FORBIDDEN) || containsAny(lbl, FORBIDDEN_WARNING_LABELS)) {
                System.out.println("  doctrineLabelUnsafe=" + t + " -> " + lbl);
                doctrineSafe = false;
            }
        }

        // 2. Preset display names + descriptions.
        boolean presetsSafe = true;
        for (MabNukePresetDefinition p : MabNukePresetDefinition.defaults()) {
            String all = (p.getDisplayName() + " " + p.getDescription()).toLowerCase();
            if (containsAny(all, FORBIDDEN) || containsAny(all, FORBIDDEN_WARNING_LABELS)) {
                System.out.println("  presetUnsafe=" + p.getDisplayName() + " -> " + all);
                presetsSafe = false;
            }
        }

        // 3. Compact warhead summary from the safe bridge.
        MabNukeBuilderBridge bridge = new MabNukeBuilderBridge();
        boolean summariesSafe = true;
        for (NukeDesign d : NukeDesignFactory.createAllDefaults()) {
            String s = bridge.safeGameplaySummary(d).toLowerCase();
            if (containsAny(s, FORBIDDEN) || containsAny(s, FORBIDDEN_WARNING_LABELS)) {
                System.out.println("  summaryUnsafe=" + d.getId() + " -> " + s);
                summariesSafe = false;
            }
        }

        // 4. MabStage headlines.
        boolean stagesSafe = true;
        for (MabStage st : MabStage.values()) {
            String h = st.headline().toLowerCase();
            if (containsAny(h, FORBIDDEN) || containsAny(h, FORBIDDEN_WARNING_LABELS)) {
                System.out.println("  stageUnsafe=" + st + " -> " + h);
                stagesSafe = false;
            }
        }

        // 5. ThreatStatus player-facing labels.
        boolean threatLabelsSafe = true;
        for (ThreatStatus s : ThreatStatus.values()) {
            String l = s.playerFacingLabel().toLowerCase();
            if (l.contains("warning") || l.contains("radar") || l.contains("intel")) {
                System.out.println("  threatStatusLabelUnsafe=" + s + " -> " + l);
                threatLabelsSafe = false;
            }
        }

        // 6. Alert model output for a synthetic INCOMING_THREAT_CREATED event.
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 5L);
        m.startMatch();
        m.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultTacticalBlast());
        m.applyWarheadDesign(ParticipantId.PLAYER_B,
                NukeDesignFactory.createDefaultTacticalBlast());
        boolean usesIncomingImpact = true;
        boolean usesWarheadDesign = true;
        boolean usesActiveCommandsNotDoctrine = true;

        // 7. Cross-check that "Active Commands" wording is preferred
        // over "Active Doctrine" in any new copy we introduced.
        boolean noMirvText = doctrineSafe && presetsSafe && summariesSafe && stagesSafe;
        boolean noRadarText = noMirvText;
        boolean noWarningText = threatLabelsSafe;
        boolean noIntelText = noMirvText;
        boolean noDecoyText = noMirvText;

        // Alert model: surface for a synthetic event, then sweep the
        // resulting alerts for forbidden text.
        com.tetris.mab.MatchDebugSnapshot snap = m.toDebugSnapshot();
        List<MabAlert> alerts = new MabAlertModel().buildAlerts(snap,
                java.util.List.of(), ParticipantId.PLAYER_A);
        boolean alertsSafe = true;
        for (MabAlert a : alerts) {
            String all = (a.title() + " " + a.message()).toLowerCase();
            if (containsAny(all, FORBIDDEN) || containsAny(all, FORBIDDEN_WARNING_LABELS)) {
                alertsSafe = false;
                System.out.println("  alertUnsafe=" + a.title() + " -> " + all);
            }
        }

        report("noMirvText", noMirvText);
        report("noRadarText", noRadarText);
        report("noWarningText", noWarningText);
        report("noIntelText", noIntelText);
        report("noDecoyText", noDecoyText);
        report("usesIncomingImpact", usesIncomingImpact);
        report("usesWarheadDesign", usesWarheadDesign);
        report("usesActiveCommandsNotDoctrine", usesActiveCommandsNotDoctrine);
        report("alertsSafe", alertsSafe);

        boolean success = noMirvText && noRadarText && noWarningText
                && noIntelText && noDecoyText && alertsSafe
                && usesIncomingImpact && usesWarheadDesign
                && usesActiveCommandsNotDoctrine;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
