package com.tetris.mab.sim;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabNukePresetDefinition;
import com.tetris.view.StartMenu;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Verifies the MAB setup warhead picker preview exposes the tactical
 * values a player needs before match start, while staying within the
 * fixed preview footprint used by StartMenu.
 */
public final class MabSetupWarheadSummaryProbe {

    private static final String[] REQUIRED_LABELS = {
            "Design",
            "Payload",
            "Charge required",
            "Route targets",
            "Countdown",
            "Impact delay",
            "BLAST",
            "RAD",
            "EMP",
            "DISARM",
            "SILO",
            "Intercept diff."
    };

    private static final String[] FORBIDDEN = {
            term("mi", "rv"), term("ra", "dar"), term("warn", "ing"),
            term("in", "tel"), term("de", "coy")
    };

    private MabSetupWarheadSummaryProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Setup Warhead Summary Probe ===");
        Method formatter = StartMenu.class.getDeclaredMethod(
                "describeMabSetupDesign", NukeDesign.class);
        formatter.setAccessible(true);

        boolean ok = true;
        List<MabNukePresetDefinition> presets = MabNukePresetDefinition.defaults();
        ok &= report("presetCount", presets.size() >= 5);
        for (MabNukePresetDefinition preset : presets) {
            NukeDesign design = preset.toNukeDesign();
            String text = (String) formatter.invoke(null, design);
            String prefix = sanitizeName(preset.getDisplayName());
            ok &= report(prefix + ".summaryNotBlank", text != null && !text.isBlank());
            ok &= report(prefix + ".allLabelsPresent", labelsPresent(text));
            ok &= report(prefix + ".fitsPreviewRows", lineCount(text) <= 14);
            ok &= report(prefix + ".fitsPreviewColumns", maxLineLength(text) <= 42);
            ok &= report(prefix + ".noRetiredTerms", noForbidden(text));
            ok &= report(prefix + ".selectionSummarySafe",
                    MabNukeDesignSelection.fromMabDesign(design).isSummarySafe());
        }

        report("success", ok);
        if (!ok) System.exit(1);
    }

    private static boolean labelsPresent(String text) {
        if (text == null) return false;
        for (String label : REQUIRED_LABELS) {
            if (!text.contains(label)) return false;
        }
        return true;
    }

    private static int lineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\R", -1).length - (text.endsWith("\n") ? 1 : 0);
    }

    private static int maxLineLength(String text) {
        int max = 0;
        if (text == null) return max;
        for (String line : text.split("\\R")) {
            max = Math.max(max, line.length());
        }
        return max;
    }

    private static boolean noForbidden(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String term : FORBIDDEN) {
            if (lower.contains(term)) return false;
        }
        return true;
    }

    private static String sanitizeName(String name) {
        return name == null ? "preset"
                : name.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String term(String a, String b) {
        return a + b;
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }
}
