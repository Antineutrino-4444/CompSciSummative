package com.tetris.mab.sim;

import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.balance.MabBalanceReport;
import com.tetris.mab.balance.MabBalanceReporter;

/**
 * Step 14 — command-line entry point that runs a single named
 * scenario via {@link MabHeadlessSimulation} and prints the result.
 *
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabSimulationRunner [scenario] [ticks] [--profile id]
 * </pre>
 *
 * Scenarios: {@code smoke}, {@code launch-impact}, {@code radar-decoy},
 * {@code civil-defense}, {@code upgrade-flow}, {@code ai-vs-dummy},
 * {@code ai-vs-ai}, {@code balance}. With no args, runs {@code smoke}.
 *
 * <p>Step 19 added optional {@code --profile <id>} (standard-pve,
 * gentle-pve, high-pressure-pve, debug-fast) and the {@code balance}
 * command which runs ai-vs-ai 160 once per profile and prints a
 * compact balance report for each.
 */
public final class MabSimulationRunner {

    private MabSimulationRunner() {}

    public static void main(String[] args) {
        String scenario = "smoke";
        Integer ticks = null;
        String profileId = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            if ("--profile".equalsIgnoreCase(a) && i + 1 < args.length) {
                profileId = args[++i];
            } else if (i == 0) {
                scenario = a.trim().toLowerCase();
            } else if (ticks == null) {
                Integer parsed = tryParseInt(a);
                if (parsed != null) ticks = parsed;
            }
        }

        if ("balance".equals(scenario)) {
            runBalanceCommand(ticks == null ? 160 : ticks);
            return;
        }
        if ("balance-report".equals(scenario)) {
            runBalanceReportCommand(ticks == null ? 160 : ticks);
            return;
        }
        if ("nuke-builder-balance".equals(scenario)) {
            runNukeBuilderBalanceCommand(ticks == null ? 160 : ticks);
            return;
        }

        MabSimulationConfig cfg = switch (scenario) {
            case "smoke"          -> MabSimulationConfig.smoke();
            case "launch-impact"  -> MabSimulationConfig.launchImpact();
            case "radar-decoy"    -> MabSimulationConfig.radarDecoy();
            case "civil-defense"  -> MabSimulationConfig.civilDefense();
            case "upgrade-flow"   -> MabSimulationConfig.upgradeFlow();
            case "ai-vs-dummy"    -> MabSimulationConfig.aiVsDummy(ticks == null ? 60 : ticks);
            case "ai-vs-ai"       -> MabSimulationConfig.aiVsAi(
                    ticks == null ? 80 : ticks,
                    MabAiArchetype.BALANCED, MabAiArchetype.TACTICAL_SPAMMER,
                    MabAiDifficulty.NORMAL);
            default               -> {
                System.out.println("Unknown scenario '" + scenario + "', running smoke.");
                yield MabSimulationConfig.smoke();
            }
        };

        if (profileId != null) {
            cfg = cfg.withBalanceProfileId(profileId);
        }
        System.out.println("Profile: " + cfg.balanceProfile().getDisplayName()
                + " [" + cfg.balanceProfileId() + "]");
        MabSimulationResult result = new MabHeadlessSimulation().run(cfg);
        printResult(result);
    }

    private static void runBalanceCommand(int ticks) {
        System.out.println("=== MAB Balance Comparison (ai-vs-ai, ticks=" + ticks + ") ===");
        for (MabBalanceProfile p : MabBalanceProfiles.all()) {
            MabSimulationConfig cfg = MabSimulationConfig.aiVsAi(
                    ticks, MabAiArchetype.BALANCED, MabAiArchetype.TACTICAL_SPAMMER,
                    MabAiDifficulty.NORMAL).withBalanceProfileId(p.getId());
            MabSimulationResult r = new MabHeadlessSimulation().run(cfg);
            MabBalanceReport report = MabBalanceReporter.fromSimulation(r, p);
            System.out.println();
            System.out.print(MabBalanceReporter.formatReport(report));
        }
    }

    private static void runBalanceReportCommand(int ticks) {
        System.out.println("=== MAB Balance Report (ticks=" + ticks + ") ===");
        int invariantFailures = 0;
        for (MabBalanceProfile p : MabBalanceProfiles.all()) {
            MabSimulationConfig cfg = MabSimulationConfig.aiVsAi(
                    ticks, MabAiArchetype.BALANCED, MabAiArchetype.TACTICAL_SPAMMER,
                    MabAiDifficulty.NORMAL).withBalanceProfileId(p.getId());
            MabSimulationResult r = new MabHeadlessSimulation().run(cfg);
            invariantFailures += r.invariantFailures().size();
            MabBalanceReport report = MabBalanceReporter.fromSimulation(r, p);
            System.out.println();
            System.out.print(MabBalanceReporter.formatReport(report));
        }
        MabSimulationResult smoke = new MabHeadlessSimulation().run(MabSimulationConfig.smoke());
        invariantFailures += smoke.invariantFailures().size();
        System.out.println();
        System.out.println("localPvpHeadlessSmoke=true");
        System.out.println("invariantFailures=" + invariantFailures);
        System.out.println("success=" + (invariantFailures == 0));
        if (invariantFailures != 0) System.exit(1);
    }

    /**
     * Step 26 — Nuke Builder balance scenarios. Runs a fixed set of
     * design-vs-design head-to-head matches and prints a compact
     * scorecard for each. The intent is to show that builder-derived
     * designs actually change the live MAB feel — different time-to-
     * armed, different launch counts, different impact mix.
     */
    private static void runNukeBuilderBalanceCommand(int ticks) {
        System.out.println("=== MAB Nuke Builder Balance Report (ticks=" + ticks + ") ===");
        com.tetris.mab.nuke.NukeDesign[][] pairs = {
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultTacticalBlast(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultTacticalBlast() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultTacticalBlast(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultHeavyBlast() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultDirtyPayload(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultCleanFusion() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultEmp(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultHeavyBlast() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultBunkerBuster(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultDirtyPayload() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultSaltedPayload(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultTacticalBlast() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultDoomsday(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultTacticalBlast() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultPlaceholder(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultPlaceholder() },
                { com.tetris.mab.nuke.NukeDesignFactory.createDefaultConcreteBlaster(),
                  com.tetris.mab.nuke.NukeDesignFactory.createDefaultDirtyPayload() }
        };
        int invariantFailures = 0;
        for (com.tetris.mab.nuke.NukeDesign[] pair : pairs) {
            MabSimulationConfig cfg = MabSimulationConfig.aiVsAi(
                    ticks, MabAiArchetype.BALANCED, MabAiArchetype.TACTICAL_SPAMMER,
                    MabAiDifficulty.NORMAL)
                    .withParticipantDesign(com.tetris.mab.ParticipantId.PLAYER_A, pair[0])
                    .withParticipantDesign(com.tetris.mab.ParticipantId.PLAYER_B, pair[1]);
            MabSimulationResult r = new MabHeadlessSimulation().run(cfg);
            invariantFailures += r.invariantFailures().size();
            System.out.println();
            System.out.println("scenario=" + pair[0].getId() + "_vs_" + pair[1].getId());
            System.out.println("  ticks=" + ticks + " runs=1");
            System.out.println("  launches=" + r.launchCount()
                    + " impacts=" + r.impactResolvedCount()
                    + " garbage=" + r.garbageRowsApplied());
            System.out.println("  chargeA=" + r.playerACharge()
                    + " chargeB=" + r.playerBCharge()
                    + " siloA=" + r.playerASiloIntegrity()
                    + " siloB=" + r.playerBSiloIntegrity());
            System.out.println("  invariantFailures=" + r.invariantFailures().size());
        }
        System.out.println();
        System.out.println("totalInvariantFailures=" + invariantFailures);
        System.out.println("success=" + (invariantFailures == 0));
        if (invariantFailures != 0) System.exit(1);
    }

    private static void printResult(MabSimulationResult r) {
        System.out.println("=== MAB Simulation: " + r.label() + " ===");
        System.out.println("success=" + r.success());
        System.out.println("summary: " + r.summary());
        if (!r.failureReason().isEmpty()) {
            System.out.println("failureReason: " + r.failureReason());
        }
        if (!r.invariantFailures().isEmpty()) {
            System.out.println("invariantFailures (" + r.invariantFailures().size() + "):");
            for (String f : r.invariantFailures()) System.out.println("  - " + f);
        }
        System.out.println("counts: launches=" + r.launchCount()
                + " impacts=" + r.impactResolvedCount()
                + " scans=" + r.radarScanCount()
                + " decoys=" + r.decoyActivatedCount()
                + " civDef=" + r.civilDefenseActivatedCount()
                + " upgrades=" + r.upgradeAppliedCount()
                + " aiExec=" + r.aiDecisionExecutedCount()
                + " aiSkip=" + r.aiDecisionSkippedCount()
                + " garbage=" + r.garbageRowsApplied());
        System.out.println("state: A charge=" + r.playerACharge()
                + " silo=" + r.playerASiloIntegrity()
                + " | B charge=" + r.playerBCharge()
                + " silo=" + r.playerBSiloIntegrity());
        if (!r.recentEvents().isEmpty()) {
            System.out.println("recent events (" + r.recentEvents().size() + "):");
            for (String row : r.recentEvents()) System.out.println("  " + row);
        }
    }

    private static Integer tryParseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
}

