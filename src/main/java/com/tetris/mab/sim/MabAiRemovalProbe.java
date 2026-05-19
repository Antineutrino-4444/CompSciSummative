package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.ai.search.AiBoardModel;
import com.tetris.mab.ai.search.AiEvaluator;
import com.tetris.mab.ai.search.AiMoveGenerator;
import com.tetris.mab.ai.search.AiSearch;
import com.tetris.mab.ai.search.AiSearchSettings;
import com.tetris.model.GameState;
import com.tetris.model.Tetromino;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the old AI policy/driver/heuristic code is gone (the new
 * implementation lives in the {@code com.tetris.mab.ai.search} package
 * and reuses the public surface), and that shared-piece fairness is
 * preserved end-to-end when both sides have an AI attached.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiRemovalProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiRemovalProbe {

    public static void main(String[] args) throws Exception {
        List<String> findings = new ArrayList<>();
        int passed = 0, failed = 0;

        // 1. The new search package must exist with the expected pieces.
        if (classExists("com.tetris.mab.ai.search.AiBoardModel")) passed++;
        else { findings.add("missing AiBoardModel"); failed++; }
        if (classExists("com.tetris.mab.ai.search.AiMoveGenerator")) passed++;
        else { findings.add("missing AiMoveGenerator"); failed++; }
        if (classExists("com.tetris.mab.ai.search.AiEvaluator")) passed++;
        else { findings.add("missing AiEvaluator"); failed++; }
        if (classExists("com.tetris.mab.ai.search.AiSearch")) passed++;
        else { findings.add("missing AiSearch"); failed++; }
        if (classExists("com.tetris.mab.ai.search.AiSearchSettings")) passed++;
        else { findings.add("missing AiSearchSettings"); failed++; }

        // 2. New difficulty tiers must be present on the enum.
        if (hasEnumValue(MabAiDifficulty.class, "EXPERT")) passed++;
        else { findings.add("MabAiDifficulty.EXPERT missing"); failed++; }
        if (hasEnumValue(MabAiDifficulty.class, "MASTER")) passed++;
        else { findings.add("MabAiDifficulty.MASTER missing"); failed++; }
        if (hasEnumValue(MabAiDifficulty.class, "MEDIUM")) passed++;
        else { findings.add("MabAiDifficulty.MEDIUM missing"); failed++; }

        // 3. The new board driver must NOT carry the old field name set —
        //    the old MabBoardAiDriver had a public method "buildPlan" or
        //    private int wAggregate; we check for the canonical new
        //    field "evaluator".
        try {
            Field evaluatorField = MabBoardAiDriver.class.getDeclaredField("evaluator");
            if (evaluatorField.getType().equals(AiEvaluator.class)) passed++;
            else { findings.add("evaluator field type mismatch"); failed++; }
        } catch (NoSuchFieldException ex) {
            findings.add("MabBoardAiDriver missing evaluator field");
            failed++;
        }
        // The old driver embedded "wAggregate" + co. weights as fields;
        // the new driver must NOT carry that legacy state.
        for (String legacy : new String[] { "wAggregate", "wHoles", "wBumpiness",
                "rotationCandidates" }) {
            try {
                MabBoardAiDriver.class.getDeclaredField(legacy);
                findings.add("legacy field " + legacy + " still present");
                failed++;
            } catch (NoSuchFieldException ex) {
                passed++;
            }
        }

        // 4. Smoke-test the new search: produce a move on an empty board.
        AiBoardModel m = new AiBoardModel();
        AiEvaluator ev = new AiEvaluator();
        AiMoveGenerator gen = new AiMoveGenerator(false, false);
        Tetromino spawn = AiBoardModel.spawn(com.tetris.model.TetrominoType.I);
        if (m.fits(spawn)) passed++;
        else { findings.add("AiBoardModel rejected legal I spawn"); failed++; }
        int simpleMoves = gen.generate(m, com.tetris.model.TetrominoType.I, null, false).size();
        if (simpleMoves >= 7) passed++;
        else { findings.add("simple I generator yielded only " + simpleMoves); failed++; }

        // 5. Shared-sequence fairness: with a shared PvE match and both
        //    AIs running, Player A's nth piece must equal Player B's nth.
        boolean fair = runFairnessCheck();
        if (fair) passed++;
        else { findings.add("shared piece fairness broken"); failed++; }

        // 6. AiSearch difficulty profiles must monotonically expand.
        int[] beams = new int[MabAiDifficulty.values().length];
        int idx = 0;
        for (MabAiDifficulty d : new MabAiDifficulty[] {
                MabAiDifficulty.EASY, MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD, MabAiDifficulty.EXPERT,
                MabAiDifficulty.MASTER }) {
            beams[idx++] = AiSearchSettings.forDifficulty(d).beamWidth;
        }
        boolean monotonic = true;
        for (int i = 1; i < idx; i++) if (beams[i] < beams[i - 1]) monotonic = false;
        if (monotonic) passed++;
        else { findings.add("difficulty beam widths not monotonic: " + java.util.Arrays.toString(beams)); failed++; }

        System.out.println("MabAiRemovalProbe: passed=" + passed + " failed=" + failed);
        for (String f : findings) System.out.println("  - " + f);
        System.out.println("success=" + (failed == 0));
        if (failed != 0) System.exit(1);
    }

    private static boolean classExists(String fqn) {
        try { Class.forName(fqn); return true; }
        catch (ClassNotFoundException ex) { return false; }
    }

    private static boolean hasEnumValue(Class<? extends Enum<?>> cls, String name) {
        for (Object v : cls.getEnumConstants()) {
            if (((Enum<?>) v).name().equals(name)) return true;
        }
        return false;
    }

    private static boolean runFairnessCheck() {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match =
                MutuallyAssuredBlocksMatch.createPveShared(a, b, MatchDifficulty.NORMAL, 9001L);
        match.startMatch();
        MabBoardAiDriver aiA = new MabBoardAiDriver(a);
        aiA.setDifficulty(MabAiDifficulty.MEDIUM);
        MabBoardAiDriver aiB = new MabBoardAiDriver(b);
        aiB.setDifficulty(MabAiDifficulty.EXPERT);
        // Capture each player's first N piece types as they are spawned.
        List<com.tetris.model.TetrominoType> seqA = new ArrayList<>();
        List<com.tetris.model.TetrominoType> seqB = new ArrayList<>();
        a.addListener(new com.tetris.events.GameEventListener() {
            @Override public void onPieceSpawned(PieceSpawnedEvent e) {
                if (seqA.size() < 24) seqA.add(e.type());
            }
        });
        b.addListener(new com.tetris.events.GameEventListener() {
            @Override public void onPieceSpawned(PieceSpawnedEvent e) {
                if (seqB.size() < 24) seqB.add(e.type());
            }
        });
        for (int i = 0; i < 4000 && (seqA.size() < 14 || seqB.size() < 14); i++) {
            aiA.tick();
            aiB.tick();
            try { Thread.sleep(4); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            }
        }
        if (seqA.isEmpty() || seqB.isEmpty()) return false;
        int compare = Math.min(seqA.size(), seqB.size());
        if (compare < 8) return false;
        for (int i = 0; i < compare; i++) {
            if (seqA.get(i) != seqB.get(i)) return false;
        }
        return true;
    }

    /** Reflective walk used to confirm the legacy AI fields are gone. */
    @SuppressWarnings("unused")
    private static List<Method> publicMethods(Class<?> cls) {
        List<Method> ms = new ArrayList<>();
        for (Method m : cls.getMethods()) ms.add(m);
        return ms;
    }

    private MabAiRemovalProbe() {}
}
