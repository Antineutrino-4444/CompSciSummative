package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.controller.GameController;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.debugui.MabDebugController;
import com.tetris.mab.debugui.MabDebugFrame;
import com.tetris.view.NukeBuilderDialog;
import com.tetris.view.StartMenu;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Walks key Swing component trees and verifies rendered text avoids
 * retired MAB terminology. This complements grep-based checks by
 * inspecting actual labels, buttons, text areas, combo/list items,
 * table headers, tooltips, and titled borders.
 */
public final class MabVisibleTextProbe {

    private static final String[] FORBIDDEN = {
            term("mi", "rv"), term("ra", "dar"), term("warn", "ing"),
            term("in", "tel"), term("de", "coy")
    };

    private MabVisibleTextProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Visible Text Probe ===");
        List<String> failures = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            try {
                NukeBuilderDialog builder = NukeBuilderDialog.createEmbedded(() -> {});
                collectForbidden("NukeBuilderDialog", builder, failures);
                System.out.println("builderSurfaceChecked=true");
            } catch (Throwable t) {
                failures.add("NukeBuilderDialog exception: " + t);
                System.out.println("builderSurfaceChecked=false");
            }

            boolean debugChecked = false;
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvp(
                            new com.tetris.model.GameState(0),
                            new com.tetris.model.GameState(0),
                            MatchDifficulty.NORMAL);
                    match.startMatch();
                    MabDebugFrame frame = new MabDebugFrame(new MabDebugController(match));
                    collectForbidden("MabDebugFrame.title", frame.getTitle(), failures);
                    collectForbidden("MabDebugFrame", frame.getContentPane(), failures);
                    frame.shutdown();
                    debugChecked = true;
                } catch (Throwable t) {
                    failures.add("MabDebugFrame exception: " + t);
                }
            }
            System.out.println("debugSurfaceChecked=" + debugChecked);

            boolean startMenuChecked = false;
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    exerciseStartMenu(failures);
                    startMenuChecked = true;
                } catch (Throwable t) {
                    failures.add("StartMenu exception: " + t);
                }
            }
            System.out.println("startMenuSurfaceChecked=" + startMenuChecked);
        });

        for (String f : failures) {
            System.out.println("forbiddenVisibleText=" + f);
        }
        boolean success = failures.isEmpty();
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static void collectForbidden(String path, Component c, List<String> failures) {
        if (c == null) return;

        if (c instanceof JLabel label) {
            collectForbidden(path + ".JLabel", label.getText(), failures);
        }
        if (c instanceof AbstractButton button) {
            collectForbidden(path + ".Button", button.getText(), failures);
        }
        if (c instanceof JTextComponent text) {
            collectForbidden(path + ".Text", text.getText(), failures);
        }
        if (c instanceof JComboBox<?> combo) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object item = combo.getItemAt(i);
                collectForbidden(path + ".Combo[" + i + "]", String.valueOf(item), failures);
            }
        }
        if (c instanceof JList<?> list) {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                Object item = list.getModel().getElementAt(i);
                collectForbidden(path + ".List[" + i + "]", String.valueOf(item), failures);
            }
        }
        if (c instanceof JTable table) {
            for (int i = 0; i < table.getColumnCount(); i++) {
                collectForbidden(path + ".TableColumn[" + i + "]",
                        table.getColumnName(i), failures);
            }
            int rows = Math.min(10, table.getRowCount());
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < table.getColumnCount(); col++) {
                    collectForbidden(path + ".Table[" + r + "," + col + "]",
                            String.valueOf(table.getValueAt(r, col)), failures);
                }
            }
        }
        if (c instanceof JComponent jc) {
            collectForbidden(path + ".ToolTip", jc.getToolTipText(), failures);
            collectForbidden(path + ".Border", borderTitle(jc.getBorder()), failures);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectForbidden(path + "/" + child.getClass().getSimpleName(), child, failures);
            }
        }
    }

    private static void exerciseStartMenu(List<String> failures) throws Exception {
        StartMenu menu = new StartMenu(1,
                (level, mode) -> new GameController(level, mode));
        menu.setSize(1366, 768);
        try {
            invokeCard(menu, "showMenuCard");
            invokeCard(menu, "showMabSelectCard");
            invokeCard(menu, "showMabPveConfigCard");
            invokeCard(menu, "showMabLocalPvpConfigCard");
            invokeCard(menu, "showMabAiVsAiConfigCard");
            invokeCard(menu, "showNukeCard");
            collectForbidden("StartMenu", menu.getContentPane(), failures);
            verifyDifficultySelectors(menu.getContentPane(), failures);
        } finally {
            stopTimer(menu);
            menu.dispose();
        }
    }

    private static void invokeCard(StartMenu menu, String methodName) throws Exception {
        Method method = StartMenu.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(menu);
        menu.doLayout();
        menu.validate();
    }

    private static void stopTimer(StartMenu menu) {
        try {
            Field field = StartMenu.class.getDeclaredField("animTimer");
            field.setAccessible(true);
            Object value = field.get(menu);
            if (value instanceof Timer timer) timer.stop();
        } catch (ReflectiveOperationException ignored) {
            // Probe cleanup only; failure is not gameplay behavior.
        }
    }

    private static void verifyDifficultySelectors(Component root, List<String> failures) {
        List<List<MabAiDifficulty>> selectors = new ArrayList<>();
        collectDifficultySelectors(root, selectors);
        List<MabAiDifficulty> expected = Arrays.asList(
                MabAiDifficulty.EASY,
                MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD,
                MabAiDifficulty.EXPERT,
                MabAiDifficulty.MASTER);
        if (selectors.size() < 3) {
            failures.add("StartMenu difficulty selector count expected >=3, got "
                    + selectors.size());
            return;
        }
        for (int i = 0; i < selectors.size(); i++) {
            if (!selectors.get(i).equals(expected)) {
                failures.add("StartMenu difficulty selector " + i
                        + " options=" + selectors.get(i));
            }
        }
        System.out.println("startMenuDifficultySelectors=" + selectors.size());
    }

    @SuppressWarnings("unchecked")
    private static void collectDifficultySelectors(Component c,
                                                   List<List<MabAiDifficulty>> out) {
        if (c == null) return;
        if ("CycleSelector".equals(c.getClass().getSimpleName())) {
            try {
                Field optionsField = c.getClass().getDeclaredField("options");
                optionsField.setAccessible(true);
                Object value = optionsField.get(c);
                if (value instanceof List<?> options
                        && !options.isEmpty()
                        && options.get(0) instanceof MabAiDifficulty) {
                    out.add((List<MabAiDifficulty>) options);
                }
            } catch (ReflectiveOperationException ignored) {
                // Non-fatal; the count check will fail if this breaks.
            }
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectDifficultySelectors(child, out);
            }
        }
    }

    private static String borderTitle(Border border) {
        if (border instanceof TitledBorder titled) {
            return titled.getTitle();
        }
        return null;
    }

    private static void collectForbidden(String path, String text, List<String> failures) {
        if (text == null || text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : FORBIDDEN) {
            if (lower.contains(term)) {
                failures.add(path + " contains '" + term + "': " + compact(text));
            }
        }
    }

    private static String compact(String s) {
        String oneLine = s.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 140 ? oneLine : oneLine.substring(0, 137) + "...";
    }

    private static String term(String a, String b) {
        return a + b;
    }
}
