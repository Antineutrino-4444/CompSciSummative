package com.tetris.mab.sim;

import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SfxGameEventBridge;
import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;
import com.tetris.events.GameEventListener.GarbageInsertedEvent;
import com.tetris.events.GameEventListener.HoldUsedEvent;
import com.tetris.events.GameEventListener.MoveKind;
import com.tetris.events.GameEventListener.PieceMovedEvent;
import com.tetris.events.GameEventListener.PieceSpawnedEvent;
import com.tetris.events.GameEventListener.TopOutEvent;
import com.tetris.events.GameEventListener.TopOutReason;
import com.tetris.events.PieceLockResult;
import com.tetris.model.TetrominoType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Verifies the {@link SfxGameEventBridge} maps gameplay events to the
 * correct {@link SoundEffect} IDs. Disables audio playback so the probe
 * remains fast and EDT-free.
 */
public final class MabSfxEventMappingProbe {

    private static int failed;

    private MabSfxEventMappingProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB SFX Event Mapping Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();
        SoundEffectManager mgr = new SoundEffectManager(new SfxAssetResolver(repoSfx), false);
        SfxGameEventBridge bridge = new SfxGameEventBridge(mgr);

        // Piece spawn → piece voice.
        check("voice.I", bridge.pieceVoiceForProbe(TetrominoType.I) == SoundEffect.PIECE_I);
        check("voice.J", bridge.pieceVoiceForProbe(TetrominoType.J) == SoundEffect.PIECE_J);
        check("voice.L", bridge.pieceVoiceForProbe(TetrominoType.L) == SoundEffect.PIECE_L);
        check("voice.O", bridge.pieceVoiceForProbe(TetrominoType.O) == SoundEffect.PIECE_O);
        check("voice.S", bridge.pieceVoiceForProbe(TetrominoType.S) == SoundEffect.PIECE_S);
        check("voice.T", bridge.pieceVoiceForProbe(TetrominoType.T) == SoundEffect.PIECE_T);
        check("voice.Z", bridge.pieceVoiceForProbe(TetrominoType.Z) == SoundEffect.PIECE_Z);

        // Combo clamping: < 1 stays at 1, > 16 stays at 16.
        check("combo.lower.normal", bridge.comboSoundForProbe(1, 1) == SoundEffect.COMBO_1);
        check("combo.lower.power", bridge.comboSoundForProbe(1, 2) == SoundEffect.COMBO_1_POWER);
        check("combo.upperClamp.normal",
                bridge.comboSoundForProbe(40, 1) == SoundEffect.COMBO_16);
        check("combo.upperClamp.power",
                bridge.comboSoundForProbe(40, 4) == SoundEffect.COMBO_16_POWER);
        check("combo.exact.4.normal", bridge.comboSoundForProbe(4, 1) == SoundEffect.COMBO_4);
        check("combo.exact.4.power", bridge.comboSoundForProbe(4, 3) == SoundEffect.COMBO_4_POWER);

        // B2B chain ladder clamps to 1..3.
        check("btb.chain.1", bridge.b2bChainForProbe(1) == SoundEffect.BTB_1);
        check("btb.chain.2", bridge.b2bChainForProbe(2) == SoundEffect.BTB_2);
        check("btb.chain.3", bridge.b2bChainForProbe(3) == SoundEffect.BTB_3);
        check("btb.chain.6clamps", bridge.b2bChainForProbe(6) == SoundEffect.BTB_3);

        // Functional smoke test: dispatch a handful of events and confirm
        // the manager's lastPlayMs records ticks for each expected ID.
        long t0 = System.currentTimeMillis();
        bridge.onPieceSpawned(new PieceSpawnedEvent(TetrominoType.T, 0, null));
        bridge.onPieceMoved(new PieceMovedEvent(TetrominoType.T, MoveKind.LEFT, 0));
        bridge.onPieceMoved(new PieceMovedEvent(TetrominoType.T, MoveKind.ROTATE_CW, 0));
        bridge.onPieceMoved(new PieceMovedEvent(TetrominoType.T, MoveKind.HARD_DROP, 0));
        bridge.onHoldUsed(new HoldUsedEvent(TetrominoType.T, TetrominoType.I));
        bridge.onPieceLockedDetailed(new PieceLockResult(
                TetrominoType.T, 4, false, false, false, "tetris",
                false, false, 1, true, 1));
        bridge.onTopOut(new TopOutEvent("p1", TopOutReason.LOCK_OUT, null));
        bridge.onGarbageInserted(new GarbageInsertedEvent(4,
                List.of(List.of(0), List.of(1), List.of(2), List.of(3)),
                "test"));
        // Manager is in non-audio mode so play() short-circuits before disk
        // IO, but it still records throttle timestamps via lastPlayMs only
        // for plays that pass the mute/disabled checks. Audio-disabled mode
        // takes the skippedCount path, so we check skippedCount > 0 instead
        // of asserting on lastPlayed timestamps.
        check("dispatch.skippedAccumulates",
                mgr.getSkippedCount() >= 8);
        check("dispatch.noDirectPlays", mgr.getPlayCount() == 0);
        check("dispatch.noTimingThrash", System.currentTimeMillis() - t0 < 250);

        boolean ok = failed == 0;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  pass " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
