package com.tetris.controller;

import com.tetris.model.Settings;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mutable key map for one local player. The map is deliberately small
 * and local-only: it stores Java key codes for same-machine play and
 * writes them through the existing {@link Settings} persistence layer.
 */
public final class LocalPlayerInputBindings {

    public record Conflict(LocalPlayerAction existingAction,
                           LocalPlayerAction requestedAction,
                           int keyCode,
                           boolean requiresExplicitConfirmation,
                           String message) {}

    private final int playerNumber;
    private final EnumMap<LocalPlayerAction, Integer> keys =
            new EnumMap<>(LocalPlayerAction.class);

    private LocalPlayerInputBindings(int playerNumber) {
        this.playerNumber = playerNumber == 2 ? 2 : 1;
    }

    public int getPlayerNumber() { return playerNumber; }

    public static LocalPlayerInputBindings player1Defaults() {
        LocalPlayerInputBindings b = new LocalPlayerInputBindings(1);
        b.keys.put(LocalPlayerAction.MOVE_LEFT,      KeyEvent.VK_A);
        b.keys.put(LocalPlayerAction.MOVE_RIGHT,     KeyEvent.VK_D);
        b.keys.put(LocalPlayerAction.MOVE_DOWN,      KeyEvent.VK_S);
        b.keys.put(LocalPlayerAction.MOVE_UP,        KeyEvent.VK_W);
        b.keys.put(LocalPlayerAction.HARD_DROP,      KeyEvent.VK_R);
        b.keys.put(LocalPlayerAction.ROTATE_CW,      KeyEvent.VK_Q);
        b.keys.put(LocalPlayerAction.ROTATE_CCW,     KeyEvent.VK_F);
        b.keys.put(LocalPlayerAction.HOLD,           KeyEvent.VK_E);
        b.keys.put(LocalPlayerAction.PAUSE,          KeyEvent.VK_K);
        b.keys.put(LocalPlayerAction.EXIT_STAGE,     KeyEvent.VK_Z);
        b.keys.put(LocalPlayerAction.RESET,          KeyEvent.VK_X);
        return b;
    }

    public static LocalPlayerInputBindings player2Defaults() {
        LocalPlayerInputBindings b = new LocalPlayerInputBindings(2);
        b.keys.put(LocalPlayerAction.MOVE_LEFT,  KeyEvent.VK_LEFT);
        b.keys.put(LocalPlayerAction.MOVE_RIGHT, KeyEvent.VK_RIGHT);
        b.keys.put(LocalPlayerAction.MOVE_DOWN,  KeyEvent.VK_DOWN);
        b.keys.put(LocalPlayerAction.MOVE_UP,    KeyEvent.VK_UP);
        b.keys.put(LocalPlayerAction.HARD_DROP,  KeyEvent.VK_P);
        b.keys.put(LocalPlayerAction.ROTATE_CW,  KeyEvent.VK_U);
        b.keys.put(LocalPlayerAction.ROTATE_CCW, KeyEvent.VK_O);
        b.keys.put(LocalPlayerAction.HOLD,       KeyEvent.VK_I);
        b.keys.put(LocalPlayerAction.EXIT_STAGE, KeyEvent.VK_Z);
        b.keys.put(LocalPlayerAction.RESET,      KeyEvent.VK_J);
        return b;
    }

    public static LocalPlayerInputBindings fromSettingsPlayer1(Settings s) {
        LocalPlayerInputBindings b = player1Defaults();
        if (s == null) return b;
        b.keys.put(LocalPlayerAction.MOVE_LEFT,      s.getKeyMoveLeft());
        b.keys.put(LocalPlayerAction.MOVE_RIGHT,     s.getKeyMoveRight());
        b.keys.put(LocalPlayerAction.MOVE_DOWN,      s.getKeyMoveDown());
        b.keys.put(LocalPlayerAction.MOVE_UP,        s.getKeyMoveUp());
        b.keys.put(LocalPlayerAction.HARD_DROP,      s.getKeyHardDrop());
        b.keys.put(LocalPlayerAction.ROTATE_CW,      s.getKeyRotateCW());
        b.keys.put(LocalPlayerAction.ROTATE_CCW,     s.getKeyRotateCCW());
        b.keys.put(LocalPlayerAction.HOLD,           s.getKeyHold());
        b.keys.put(LocalPlayerAction.PAUSE,          s.getKeyPause());
        b.keys.put(LocalPlayerAction.EXIT_STAGE,     s.getKeyExitStage());
        b.keys.put(LocalPlayerAction.RESET,          s.getKeyReset());
        return b;
    }

    public static LocalPlayerInputBindings fromSettingsPlayer2(Settings s) {
        LocalPlayerInputBindings b = player2Defaults();
        if (s == null) return b;
        b.keys.put(LocalPlayerAction.MOVE_LEFT,  s.getKeyP2MoveLeft());
        b.keys.put(LocalPlayerAction.MOVE_RIGHT, s.getKeyP2MoveRight());
        b.keys.put(LocalPlayerAction.MOVE_DOWN,  s.getKeyP2MoveDown());
        b.keys.put(LocalPlayerAction.MOVE_UP,    s.getKeyP2MoveUp());
        b.keys.put(LocalPlayerAction.HARD_DROP,  s.getKeyP2HardDrop());
        b.keys.put(LocalPlayerAction.ROTATE_CW,  s.getKeyP2RotateCW());
        b.keys.put(LocalPlayerAction.ROTATE_CCW, s.getKeyP2RotateCCW());
        b.keys.put(LocalPlayerAction.HOLD,       s.getKeyP2Hold());
        b.keys.put(LocalPlayerAction.EXIT_STAGE, s.getKeyP2ExitStage());
        b.keys.put(LocalPlayerAction.RESET,      s.getKeyP2Reset());
        return b;
    }

    public void applyToSettingsPlayer1(Settings s) {
        if (s == null) return;
        s.setKeyMoveLeft(get(LocalPlayerAction.MOVE_LEFT));
        s.setKeyMoveRight(get(LocalPlayerAction.MOVE_RIGHT));
        s.setKeyMoveDown(get(LocalPlayerAction.MOVE_DOWN));
        s.setKeyMoveUp(get(LocalPlayerAction.MOVE_UP));
        s.setKeyHardDrop(get(LocalPlayerAction.HARD_DROP));
        s.setKeyRotateCW(get(LocalPlayerAction.ROTATE_CW));
        s.setKeyRotateCCW(get(LocalPlayerAction.ROTATE_CCW));
        s.setKeyHold(get(LocalPlayerAction.HOLD));
        s.setKeyPause(get(LocalPlayerAction.PAUSE));
        s.setKeyExitStage(get(LocalPlayerAction.EXIT_STAGE));
        s.setKeyReset(get(LocalPlayerAction.RESET));
    }

    public void applyToSettingsPlayer2(Settings s) {
        if (s == null) return;
        s.setKeyP2MoveLeft(get(LocalPlayerAction.MOVE_LEFT));
        s.setKeyP2MoveRight(get(LocalPlayerAction.MOVE_RIGHT));
        s.setKeyP2MoveDown(get(LocalPlayerAction.MOVE_DOWN));
        s.setKeyP2MoveUp(get(LocalPlayerAction.MOVE_UP));
        s.setKeyP2HardDrop(get(LocalPlayerAction.HARD_DROP));
        s.setKeyP2RotateCW(get(LocalPlayerAction.ROTATE_CW));
        s.setKeyP2RotateCCW(get(LocalPlayerAction.ROTATE_CCW));
        s.setKeyP2Hold(get(LocalPlayerAction.HOLD));
        s.setKeyP2ExitStage(get(LocalPlayerAction.EXIT_STAGE));
        s.setKeyP2Reset(get(LocalPlayerAction.RESET));
    }

    public boolean isAvailable(LocalPlayerAction action) {
        if (action == null) return false;
        return playerNumber == 1 || action.isPlayer2Supported();
    }

    public int get(LocalPlayerAction action) {
        Integer key = keys.get(action);
        return key == null ? 0 : key;
    }

    public void set(LocalPlayerAction action, int keyCode) {
        if (!isAvailable(action)) return;
        keys.put(action, keyCode);
    }

    public Map<LocalPlayerAction, Integer> snapshot() {
        return Map.copyOf(keys);
    }

    public void resetToDefaults() {
        LocalPlayerInputBindings d = playerNumber == 2 ? player2Defaults() : player1Defaults();
        keys.clear();
        keys.putAll(d.keys);
    }

    public Conflict findConflict(LocalPlayerAction requestedAction, int keyCode) {
        if (!isAvailable(requestedAction) || keyCode == 0) return null;
        for (Map.Entry<LocalPlayerAction, Integer> e : keys.entrySet()) {
            LocalPlayerAction existing = e.getKey();
            if (existing == requestedAction) continue;
            if (e.getValue() != null && e.getValue() == keyCode) {
                return new Conflict(existing, requestedAction, keyCode,
                        requiresExplicitConfirmation(existing, requestedAction),
                        buildConflictMessage(existing, requestedAction, keyCode));
            }
        }
        return null;
    }

    public boolean rebind(LocalPlayerAction action, int keyCode, boolean allowConflict) {
        Conflict conflict = findConflict(action, keyCode);
        if (conflict != null && !allowConflict) return false;
        set(action, keyCode);
        return true;
    }

    public boolean swap(LocalPlayerAction action, int keyCode) {
        Conflict conflict = findConflict(action, keyCode);
        if (conflict == null) {
            set(action, keyCode);
            return true;
        }
        int oldKey = get(action);
        set(action, keyCode);
        set(conflict.existingAction(), oldKey);
        return true;
    }

    public String keyText(LocalPlayerAction action) {
        int code = get(action);
        return code == 0 ? "UNBOUND" : KeyEvent.getKeyText(code).toUpperCase();
    }

    private static boolean requiresExplicitConfirmation(LocalPlayerAction a,
                                                         LocalPlayerAction b) {
        if (a == null || b == null) return true;
        if (a.isOppositeOf(b)) return true;
        return a == LocalPlayerAction.PAUSE || b == LocalPlayerAction.PAUSE
                || a == LocalPlayerAction.EXIT_STAGE || b == LocalPlayerAction.EXIT_STAGE
                || a == LocalPlayerAction.RESET || b == LocalPlayerAction.RESET;
    }

    private static String buildConflictMessage(LocalPlayerAction existing,
                                               LocalPlayerAction requested,
                                               int keyCode) {
        String key = KeyEvent.getKeyText(keyCode).toUpperCase();
        return key + " is already assigned to " + existing.getDisplayName()
                + ". Swap it with " + requested.getDisplayName() + " or choose another key.";
    }
}
