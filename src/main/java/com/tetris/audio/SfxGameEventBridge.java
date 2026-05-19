package com.tetris.audio;

import com.tetris.events.GameEventListener;
import com.tetris.events.PieceLockResult;
import com.tetris.model.TetrominoType;

import javax.swing.Timer;
import java.util.EnumSet;
import java.util.Set;

/**
 * Translates the engine's {@link GameEventListener} stream into
 * {@link SoundEffect} calls on a shared {@link SoundEffectManager}. One bridge
 * instance is registered with each {@link com.tetris.model.GameState} that we
 * want to sonify; the bridge itself holds no engine reference.
 *
 * <p>The bridge is the single source of truth for engine-driven SFX policy:
 * piece-spawn voice, B2B chain follow-up, combo level/power selection,
 * perfect-clear chimes, top-out cue, and the wall-rub sidehit follow-up. It
 * never blocks the EDT — all decisions are pure logic and the playback layer
 * runs on the manager's executor.
 */
public final class SfxGameEventBridge implements GameEventListener {

    private static final Set<TetrominoType> SUPPORTED_SPAWN = EnumSet.allOf(TetrominoType.class);

    private final SoundEffectManager sfx;
    private final String side;
    private int lastSeenCombo = -1;
    private boolean lastLockBackToBack;
    private boolean played;

    public SfxGameEventBridge(SoundEffectManager sfx, String side) {
        this.sfx = sfx == null ? SoundEffectManager.shared() : sfx;
        this.side = side == null ? "" : side;
    }

    public SfxGameEventBridge(SoundEffectManager sfx) {
        this(sfx, "");
    }

    public SfxGameEventBridge() {
        this(SoundEffectManager.shared(), "");
    }

    // ───── piece spawn ─────────────────────────────────────────

    @Override
    public void onPieceSpawned(PieceSpawnedEvent e) {
        if (e == null || e.type() == null) return;
        SoundEffect voice = voiceForPiece(e.type());
        if (voice != null) sfx.play(voice, 0.85f);
    }

    private SoundEffect voiceForPiece(TetrominoType type) {
        if (type == null) return null;
        return switch (type) {
            case I -> SoundEffect.PIECE_I;
            case J -> SoundEffect.PIECE_J;
            case L -> SoundEffect.PIECE_L;
            case O -> SoundEffect.PIECE_O;
            case S -> SoundEffect.PIECE_S;
            case T -> SoundEffect.PIECE_T;
            case Z -> SoundEffect.PIECE_Z;
        };
    }

    // ───── piece move / rotate ─────────────────────────────────

    @Override
    public void onPieceMoved(PieceMovedEvent e) {
        if (e == null || e.kind() == null) return;
        switch (e.kind()) {
            case LEFT, RIGHT -> sfx.play(SoundEffect.MOVE, 0.6f);
            case SOFT_DROP -> sfx.play(SoundEffect.SOFTDROP, 0.55f);
            case HARD_DROP -> sfx.play(SoundEffect.HARDDROP, 1f);
            case ROTATE_CW, ROTATE_CCW, ROTATE_180 -> sfx.play(SoundEffect.ROTATE, 0.7f);
            case GRAVITY -> { /* gravity is silent */ }
        }
    }

    /**
     * Engine-level failed-movement hook. Tetris dispatches no event when a
     * shift is blocked, so InputHandler / controllers invoke this directly
     * when {@code moveLeft}/{@code moveRight} returns false to trigger the
     * sidehit wall-rub cue. Throttled inside the manager.
     */
    public void onFailedSideMove() {
        sfx.play(SoundEffect.SIDEHIT, 0.55f);
    }

    // ───── hold ────────────────────────────────────────────────

    @Override
    public void onHoldUsed(HoldUsedEvent e) {
        sfx.play(SoundEffect.HOLD);
    }

    // ───── pause / topout ──────────────────────────────────────

    @Override
    public void onTopOut(TopOutEvent e) {
        sfx.play(SoundEffect.TOPOUT);
    }

    @Override
    public void onPauseChanged(PauseChangedEvent e) {
        if (e == null) return;
        sfx.play(SoundEffect.MENU_TAP);
    }

    @Override
    public void onGarbageInserted(GarbageInsertedEvent e) {
        if (e == null) return;
        int rows = Math.max(0, e.rows());
        if (rows == 0) return;
        SoundEffect cue;
        if (rows >= 5) cue = SoundEffect.GARBAGE_IN_LARGE;
        else if (rows >= 3) cue = SoundEffect.GARBAGE_IN_MEDIUM;
        else cue = SoundEffect.GARBAGE_IN_SMALL;
        sfx.play(cue);

        // Heavier loads also play the rise + damage chain.
        if (rows >= 2) sfx.play(SoundEffect.GARBAGE_RISE, 0.85f);
        if (rows >= 4) {
            sfx.play(SoundEffect.GARBAGE_SMASH, 0.95f);
        }
        SoundEffect dmg;
        if (rows >= 5) dmg = SoundEffect.DAMAGE_LARGE;
        else if (rows >= 3) dmg = SoundEffect.DAMAGE_MEDIUM;
        else dmg = SoundEffect.DAMAGE_SMALL;
        sfx.play(dmg);
    }

    // ───── lock-driven clears / combos / B2B / PC ──────────────

    @Override
    public void onPieceLockedDetailed(PieceLockResult e) {
        if (e == null) return;
        played = false;

        boolean wasB2bArmed = lastLockBackToBack;
        lastLockBackToBack = e.backToBack();

        // No-line lock: only combo break, never combo number, never clear sounds.
        if (e.linesCleared() == 0) {
            if (lastSeenCombo > 0) {
                sfx.play(SoundEffect.COMBO_BREAK);
            }
            lastSeenCombo = -1;
            return;
        }

        // Primary clear voice (spin > btb > quad > line).
        if (e.spin()) {
            sfx.play(SoundEffect.CLEAR_SPIN);
            played = true;
        } else if (e.backToBack()) {
            sfx.play(SoundEffect.CLEAR_BTB);
            played = true;
        } else if (e.tetris()) {
            sfx.play(SoundEffect.CLEAR_QUAD);
            played = true;
        }
        if (!played && e.linesCleared() >= 1 && e.linesCleared() <= 3) {
            sfx.play(SoundEffect.CLEAR_LINE);
            played = true;
        }

        // B2B chain ladder follow-up after the clear voice. Single shot per lock.
        if (e.backToBack()) {
            SoundEffect chain = b2bChainSound(e.comboCount());
            if (chain != null) deferred(180, () -> sfx.play(chain));
        } else if (wasB2bArmed) {
            // B2B chain just broke.
            sfx.play(SoundEffect.BTB_BREAK);
        }

        // Combo voice — clamp to 1..16, choose _power for multi-line clears.
        if (e.comboCount() >= 1) {
            SoundEffect combo = comboSound(e.comboCount(), e.linesCleared());
            if (combo != null) sfx.play(combo);
        }
        lastSeenCombo = e.comboCount();

        // Perfect clear chime after the main voice.
        if (e.perfectClear()) {
            deferred(250, () -> sfx.play(SoundEffect.ALL_CLEAR));
        }
    }

    private SoundEffect b2bChainSound(int comboCount) {
        int chain = Math.max(1, Math.min(3, Math.max(1, comboCount)));
        return switch (chain) {
            case 1 -> SoundEffect.BTB_1;
            case 2 -> SoundEffect.BTB_2;
            default -> SoundEffect.BTB_3;
        };
    }

    private SoundEffect comboSound(int comboCount, int linesCleared) {
        int n = Math.max(1, Math.min(16, comboCount));
        boolean power = linesCleared >= 2;
        if (power) {
            return switch (n) {
                case 1  -> SoundEffect.COMBO_1_POWER;
                case 2  -> SoundEffect.COMBO_2_POWER;
                case 3  -> SoundEffect.COMBO_3_POWER;
                case 4  -> SoundEffect.COMBO_4_POWER;
                case 5  -> SoundEffect.COMBO_5_POWER;
                case 6  -> SoundEffect.COMBO_6_POWER;
                case 7  -> SoundEffect.COMBO_7_POWER;
                case 8  -> SoundEffect.COMBO_8_POWER;
                case 9  -> SoundEffect.COMBO_9_POWER;
                case 10 -> SoundEffect.COMBO_10_POWER;
                case 11 -> SoundEffect.COMBO_11_POWER;
                case 12 -> SoundEffect.COMBO_12_POWER;
                case 13 -> SoundEffect.COMBO_13_POWER;
                case 14 -> SoundEffect.COMBO_14_POWER;
                case 15 -> SoundEffect.COMBO_15_POWER;
                default -> SoundEffect.COMBO_16_POWER;
            };
        }
        return switch (n) {
            case 1  -> SoundEffect.COMBO_1;
            case 2  -> SoundEffect.COMBO_2;
            case 3  -> SoundEffect.COMBO_3;
            case 4  -> SoundEffect.COMBO_4;
            case 5  -> SoundEffect.COMBO_5;
            case 6  -> SoundEffect.COMBO_6;
            case 7  -> SoundEffect.COMBO_7;
            case 8  -> SoundEffect.COMBO_8;
            case 9  -> SoundEffect.COMBO_9;
            case 10 -> SoundEffect.COMBO_10;
            case 11 -> SoundEffect.COMBO_11;
            case 12 -> SoundEffect.COMBO_12;
            case 13 -> SoundEffect.COMBO_13;
            case 14 -> SoundEffect.COMBO_14;
            case 15 -> SoundEffect.COMBO_15;
            default -> SoundEffect.COMBO_16;
        };
    }

    private static void deferred(int millis, Runnable action) {
        Timer t = new Timer(millis, e -> action.run());
        t.setRepeats(false);
        t.start();
    }

    // ───── probe helpers ───────────────────────────────────────

    /** Probe-facing helper — direct mapping of combo n + lines to its SFX. */
    public SoundEffect comboSoundForProbe(int comboCount, int linesCleared) {
        return comboSound(comboCount, linesCleared);
    }

    /** Probe-facing helper — direct mapping of B2B chain n to its SFX. */
    public SoundEffect b2bChainForProbe(int comboCount) {
        return b2bChainSound(comboCount);
    }

    /** Probe-facing helper — voice for a given piece type. */
    public SoundEffect pieceVoiceForProbe(TetrominoType type) {
        return voiceForPiece(type);
    }

    public String side() { return side; }
}
