package com.tetris.controller;

/**
 * Local, same-machine control actions used by the keyboard calibration
 * wizard and by the two-player MAB input router.
 *
 * <p>Offline-only: this enum models keys on one keyboard. It is not a
 * network command schema and does not reserve any online behavior.</p>
 */
public enum LocalPlayerAction {
    MOVE_LEFT("Move left",   true,  false),
    MOVE_RIGHT("Move right", true,  false),
    MOVE_DOWN("Move down",   true,  false),
    MOVE_UP("Move up",       true,  false),
    HARD_DROP("Hard drop / Confirm", true, false),
    ROTATE_CW("Rotate clockwise",      true,  false),
    ROTATE_CCW("Rotate counterclockwise", true, false),
    HOLD("Hold",             true,  false),
    PAUSE("Pause",           false, true),
    EXIT_STAGE("Exit stage", true,  true),
    RESET("Reset",           true,  true);

    private final String displayName;
    private final boolean player2Supported;
    private final boolean sharedOrP1Only;

    LocalPlayerAction(String displayName, boolean player2Supported, boolean sharedOrP1Only) {
        this.displayName = displayName;
        this.player2Supported = player2Supported;
        this.sharedOrP1Only = sharedOrP1Only;
    }

    public String getDisplayName() { return displayName; }
    public boolean isPlayer2Supported() { return player2Supported; }
    public boolean isSharedOrP1Only() { return sharedOrP1Only; }

    public boolean isMovement() {
        return this == MOVE_LEFT || this == MOVE_RIGHT
                || this == MOVE_DOWN || this == MOVE_UP;
    }

    public boolean isOppositeOf(LocalPlayerAction other) {
        return (this == MOVE_LEFT  && other == MOVE_RIGHT)
                || (this == MOVE_RIGHT && other == MOVE_LEFT)
                || (this == MOVE_UP    && other == MOVE_DOWN)
                || (this == MOVE_DOWN  && other == MOVE_UP);
    }

    public static LocalPlayerAction[] player1Actions() {
        return values();
    }

    public static LocalPlayerAction[] player2Actions() {
        return new LocalPlayerAction[] {
                MOVE_LEFT, MOVE_RIGHT, MOVE_DOWN, MOVE_UP,
                HARD_DROP, ROTATE_CW, ROTATE_CCW, HOLD,
                EXIT_STAGE, RESET
        };
    }
}
