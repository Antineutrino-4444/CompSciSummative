package com.tetris.mab.sim;

import com.tetris.view.DevConsolePanel;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Headless smoke probe for the developer console Enter submission path. */
public final class DevConsoleEnterProbe {
    private DevConsoleEnterProbe() {}

    public static void main(String[] args) throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        DevConsolePanel panel = new DevConsolePanel(cmd -> {
            received.set(cmd);
            return "ok";
        });

        Field field = DevConsolePanel.class.getDeclaredField("inputField");
        field.setAccessible(true);
        JTextField input = (JTextField) field.get(panel);

        Object binding = input.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        boolean enterBound = "submit-console-command".equals(binding);

        Action action = input.getActionMap().get("submit-console-command");
        input.setText("probe");
        if (action != null) {
            action.actionPerformed(new ActionEvent(input,
                    ActionEvent.ACTION_PERFORMED, ""));
        }

        boolean submitted = "probe".equals(received.get());
        boolean cleared = input.getText().isEmpty();
        boolean ok = enterBound && action != null && submitted && cleared;

        System.out.println("enterBound=" + enterBound);
        System.out.println("actionPresent=" + (action != null));
        System.out.println("submitted=" + submitted);
        System.out.println("cleared=" + cleared);
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }
}
