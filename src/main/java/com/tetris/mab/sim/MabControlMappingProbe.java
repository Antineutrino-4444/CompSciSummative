package com.tetris.mab.sim;

import com.tetris.controller.LocalPlayerAction;
import com.tetris.controller.LocalPlayerInputBindings;
import com.tetris.model.Settings;

import java.awt.event.KeyEvent;

/** Step 26 - headless probe for startup/local PvP control mappings. */
public final class MabControlMappingProbe {

    private MabControlMappingProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Control Mapping Probe ===");
        Settings s = Settings.get();

        boolean player1Defaults = LocalPlayerInputBindings.player1Defaults()
                .get(LocalPlayerAction.MOVE_LEFT) == KeyEvent.VK_LEFT;
        boolean player2Defaults = LocalPlayerInputBindings.player2Defaults()
                .get(LocalPlayerAction.MOVE_LEFT) == KeyEvent.VK_J;

        LocalPlayerInputBindings p1 = LocalPlayerInputBindings.player1Defaults();
        boolean rebindingWorks = p1.rebind(LocalPlayerAction.MOVE_LEFT, KeyEvent.VK_B, false)
                && p1.get(LocalPlayerAction.MOVE_LEFT) == KeyEvent.VK_B;
        LocalPlayerInputBindings.Conflict conflict =
                p1.findConflict(LocalPlayerAction.MOVE_RIGHT, KeyEvent.VK_B);
        boolean duplicateDetected = conflict != null;
        p1.resetToDefaults();
        boolean resetDefaults = p1.get(LocalPlayerAction.MOVE_LEFT) == KeyEvent.VK_LEFT
                && p1.get(LocalPlayerAction.MOVE_RIGHT) == KeyEvent.VK_RIGHT;

        boolean originalWizardFlag = s.isControlsWizardCompleted();
        s.setControlsWizardCompleted(true);
        boolean wizardFlag = s.isControlsWizardCompleted();
        s.setControlsWizardCompleted(originalWizardFlag);

        LocalPlayerInputBindings settingsP1 = LocalPlayerInputBindings.fromSettingsPlayer1(s);
        LocalPlayerInputBindings settingsP2 = LocalPlayerInputBindings.fromSettingsPlayer2(s);
        boolean normalReadsP1 = settingsP1.get(LocalPlayerAction.HARD_DROP) == s.getKeyHardDrop();
        boolean pveReadsP1 = settingsP1.get(LocalPlayerAction.HOLD) == s.getKeyHold();
        boolean pvpReadsBoth = settingsP1.get(LocalPlayerAction.MOVE_LEFT) == s.getKeyMoveLeft()
                && settingsP2.get(LocalPlayerAction.MOVE_RIGHT) == s.getKeyP2MoveRight();

        boolean success = player1Defaults && player2Defaults && rebindingWorks
                && duplicateDetected && resetDefaults && wizardFlag
                && normalReadsP1 && pveReadsP1 && pvpReadsBoth;

        report("player1Defaults", player1Defaults);
        report("player2Defaults", player2Defaults);
        report("rebindingWorks", rebindingWorks);
        report("duplicateDetected", duplicateDetected);
        report("resetDefaults", resetDefaults);
        report("wizardFlag", wizardFlag);
        report("normalReadsP1", normalReadsP1);
        report("pveReadsP1", pveReadsP1);
        report("pvpReadsBoth", pvpReadsBoth);
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String name, boolean value) {
        System.out.println(name + "=" + value);
    }
}
