package com.tetris.mab.ui;

import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Presentation-only event VFX for the MAB battle shell.
 *
 * <p>The layer watches the match event log and renders short-lived effects
 * above the shell. It does not mutate match state or board state; every cue is
 * derived from events that already happened in the game.
 */
public final class MabVisualEffectsLayer extends JComponent {

    private static final int TIMER_MS = 16;
    private static final int RECENT_EVENT_WINDOW = 80;
    private static final int MAX_ACTIVE_EFFECTS = 10;

    private final List<VisualEffect> effects = new ArrayList<>();
    private final Random rng = new Random(0x5EEDB10CL);
    private final Timer timer;

    private boolean initialized = false;
    private long lastSequenceSeen = -1L;
    private Rectangle playerBoard = new Rectangle();
    private Rectangle opponentBoard = new Rectangle();
    private ParticipantId playerId = ParticipantId.PLAYER_A;
    private ParticipantId opponentId = ParticipantId.PLAYER_B;

    public MabVisualEffectsLayer() {
        setOpaque(false);
        setFocusable(false);
        setEnabled(false);
        timer = new Timer(TIMER_MS, e -> tick());
        timer.setRepeats(true);
    }

    public void observe(MutuallyAssuredBlocksMatch match,
                        ParticipantId playerId,
                        ParticipantId opponentId,
                        Rectangle playerBoard,
                        Rectangle opponentBoard) {
        if (playerId != null) this.playerId = playerId;
        if (opponentId != null) this.opponentId = opponentId;
        this.playerBoard = playerBoard == null ? new Rectangle() : new Rectangle(playerBoard);
        this.opponentBoard = opponentBoard == null ? new Rectangle() : new Rectangle(opponentBoard);

        if (match == null) return;
        List<MatchEventLogEntry> recent = match.getRecentEvents(RECENT_EVENT_WINDOW);
        long latest = lastSequenceSeen;
        for (MatchEventLogEntry e : recent) {
            if (e != null) latest = Math.max(latest, e.sequenceNumber());
        }
        if (!initialized) {
            initialized = true;
            lastSequenceSeen = latest;
            return;
        }

        boolean spawned = false;
        for (MatchEventLogEntry e : recent) {
            if (e == null || e.sequenceNumber() <= lastSequenceSeen) continue;
            spawned |= handleEvent(e);
            latest = Math.max(latest, e.sequenceNumber());
        }
        lastSequenceSeen = Math.max(lastSequenceSeen, latest);
        if (spawned) startTimer();
    }

    public void playDebugNukeImpact(ParticipantId target) {
        playDebugNukeImpact(target, "");
    }

    public void playDebugNukeImpact(ParticipantId target, String variant) {
        playNukeImpact(target, ImpactProfile.debug(variant, "DEBUG YIELD"), true);
    }

    public void playDebugNukeImpactBoth() {
        playDebugNukeImpactBoth("");
    }

    public void playDebugNukeImpactBoth(String variant) {
        playNukeImpact(playerId, ImpactProfile.debug(variant, "DEBUG YIELD P1"), true);
        playNukeImpact(opponentId, ImpactProfile.debug(variant, "DEBUG YIELD P2"), false);
    }

    private void playNukeImpact(ParticipantId target, ImpactProfile profile,
                                boolean globalFlash) {
        addEffect(new NuclearImpactEffect(targetFor(target), profile, rng, globalFlash));
    }

    private void addEffect(VisualEffect effect) {
        if (effect == null) return;
        while (effects.size() >= MAX_ACTIVE_EFFECTS) {
            effects.remove(0);
        }
        effects.add(effect);
        startTimer();
    }

    private boolean handleEvent(MatchEventLogEntry e) {
        String type = e.eventType();
        if (type == null || type.isEmpty()) return false;
        switch (type) {
            case "IMPACT_RESOLVED" -> {
                addEffect(new NuclearImpactEffect(targetFor(e.participantId()),
                        ImpactProfile.fromMetadata(e.metadata()), rng, true));
                return true;
            }
            case "IMPACT_GARBAGE_WAVE_APPLIED" -> {
                addEffect(new AftershockEffect(targetFor(e.participantId()),
                        intValue(e.metadata(), "rows", 1),
                        textValue(e.metadata(), "radiation", "FALLOUT")));
                return true;
            }
            case "IMPACT_READY" -> {
                addEffect(new TargetLockEffect(targetFor(e.participantId()),
                        "IMPACT IMMINENT"));
                return true;
            }
            case "LAUNCH_AUTHORIZED", "MAB_LAUNCH_FIRED_SIMPLIFIED" -> {
                ParticipantId attacker = e.participantId();
                addEffect(new LaunchTrailEffect(targetFor(attacker),
                        targetFor(oppositeOf(attacker)),
                        textValue(e.metadata(), "designName", "LAUNCH")));
                return true;
            }
            case "LAUNCH_IN_FLIGHT" -> {
                ParticipantId attacker = e.participantId();
                addEffect(new LaunchTrailEffect(targetFor(attacker),
                        targetFor(oppositeOf(attacker)),
                        "IN FLIGHT"));
                return true;
            }
            case "MAB_NUKE_READY" -> {
                addEffect(new BoardPulseEffect(targetFor(e.participantId()),
                        MabUiTheme.C_GREEN, "NUKE READY", 1_450_000_000L));
                return true;
            }
            case "MAB_LAUNCH_PROGRESS_TETRIS", "MAB_LAUNCH_PROGRESS_SPIN" -> {
                addEffect(new BoardPulseEffect(targetFor(e.participantId()),
                        MabUiTheme.C_CYAN, "ROUTE PROGRESS", 850_000_000L));
                return true;
            }
            case "MAB_SPIN_INTERCEPT_TRIGGERED", "MAB_INTERCEPT_RESOLVED_SIMPLIFIED",
                    "INTERCEPT_RESOLVED", "INTERCEPT_RESOLVED_BY_ACTION" -> {
                addEffect(new InterceptBurstEffect(targetFor(e.participantId()),
                        textValue(e.metadata(), "outcome", "INTERCEPT")));
                return true;
            }
            case "DEFCON_CHANGED" -> {
                addEffect(new DefconPulseEffect(textValue(e.metadata(),
                        "currentLevel", e.message())));
                return true;
            }
            case "MAB_UPGRADE_SELECTED", "MAB_AI_UPGRADE_SELECTED",
                    "UPGRADE_APPLIED", "UPGRADE_POINTS_EARNED_GLOBAL_DEFCON" -> {
                addEffect(new BoardPulseEffect(targetFor(e.participantId()),
                        MabUiTheme.C_MAGENTA, "UPGRADE", 1_150_000_000L));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private Rectangle targetFor(ParticipantId id) {
        if (id == null) return centeredFallback();
        if (id == playerId) return rectOrFallback(playerBoard, true);
        if (id == opponentId) return rectOrFallback(opponentBoard, false);
        if (id == ParticipantId.PLAYER_A) return rectOrFallback(playerBoard, true);
        if (id == ParticipantId.PLAYER_B) return rectOrFallback(opponentBoard, false);
        return centeredFallback();
    }

    private Rectangle rectOrFallback(Rectangle r, boolean left) {
        if (r != null && r.width > 8 && r.height > 8) return new Rectangle(r);
        int w = Math.max(160, getWidth() / 5);
        int h = Math.max(320, getHeight() / 2);
        int x = left ? Math.max(24, getWidth() / 6 - w / 2)
                : Math.max(24, getWidth() * 5 / 6 - w / 2);
        int y = Math.max(40, getHeight() / 2 - h / 2);
        return new Rectangle(x, y, w, h);
    }

    private Rectangle centeredFallback() {
        int w = Math.max(220, getWidth() / 4);
        int h = Math.max(300, getHeight() / 2);
        return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }

    private ParticipantId oppositeOf(ParticipantId id) {
        if (id == null) return null;
        if (id == playerId) return opponentId;
        if (id == opponentId) return playerId;
        return id.opponent();
    }

    private int impactMagnitude(Map<String, Object> meta) {
        int blast = intValue(meta, "blast", 2);
        int immediate = intValue(meta, "immediateRows", 0);
        int delayed = intValue(meta, "delayedRows", 0);
        int disarm = intValue(meta, "disarmApplied", 0);
        int silo = intValue(meta, "siloDamageApplied", 0);
        return Math.max(2, blast + immediate + delayed + disarm / 20 + silo / 20);
    }

    private static int intValue(Map<String, Object> meta, String key, int fallback) {
        if (meta == null || key == null) return fallback;
        Object v = meta.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String textValue(Map<String, Object> meta, String key, String fallback) {
        if (meta == null || key == null) return fallback;
        Object v = meta.get(key);
        if (v == null) return fallback;
        String s = v.toString();
        return s.isBlank() ? fallback : s;
    }

    private void startTimer() {
        if (!timer.isRunning()) timer.start();
        repaint();
    }

    private void tick() {
        long now = System.nanoTime();
        for (Iterator<VisualEffect> it = effects.iterator(); it.hasNext();) {
            if (it.next().isFinished(now)) it.remove();
        }
        repaint();
        if (effects.isEmpty()) timer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (effects.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long now = System.nanoTime();
        Dimension size = getSize();
        for (VisualEffect effect : effects) {
            effect.paint(g2, now, size);
        }
        g2.dispose();
    }

    private abstract static class VisualEffect {
        final long startNs;
        final long durationNs;

        VisualEffect(long durationNs) {
            this.startNs = System.nanoTime();
            this.durationNs = durationNs;
        }

        final float progress(long now) {
            return clamp((now - startNs) / (float) durationNs);
        }

        final boolean isFinished(long now) {
            return now - startNs >= durationNs;
        }

        abstract void paint(Graphics2D g2, long now, Dimension size);
    }

    private enum WeaponVisualFamily {
        TRAINING,
        TACTICAL,
        HEAVY,
        DIRTY,
        SALTED,
        EMP,
        BUNKER,
        CLEAN,
        DOOMSDAY
    }

    private static final class ImpactProfile {
        final String designName;
        final String doctrine;
        final String size;
        final String radiationLabel;
        final int blast;
        final int radiationRating;
        final int emp;
        final int disarm;
        final int silo;
        final int immediateRows;
        final int delayedRows;
        final int magnitude;
        final float sizeScale;
        final WeaponVisualFamily family;
        final Color hot;
        final Color mid;
        final Color dark;
        final Color dust;
        final Color fallout;
        final Color electric;

        private ImpactProfile(String designName, String doctrine, String size,
                              String radiationLabel, int blast, int radiationRating,
                              int emp, int disarm, int silo, int immediateRows,
                              int delayedRows) {
            this.designName = designName == null || designName.isBlank()
                    ? "NUKE IMPACT" : designName;
            this.doctrine = doctrine == null ? "" : doctrine;
            this.size = size == null ? "" : size;
            this.radiationLabel = radiationLabel == null || radiationLabel.isBlank()
                    ? "CLEAN" : radiationLabel;
            this.blast = Math.max(0, blast);
            this.radiationRating = Math.max(0, radiationRating);
            this.emp = Math.max(0, emp);
            this.disarm = Math.max(0, disarm);
            this.silo = Math.max(0, silo);
            this.immediateRows = Math.max(0, immediateRows);
            this.delayedRows = Math.max(0, delayedRows);
            this.sizeScale = sizeScaleFor(this.size);
            this.family = familyFor(this.doctrine, this.radiationLabel,
                    this.radiationRating, this.emp, this.silo, this.disarm);
            int sizeBonus = Math.round(this.sizeScale * 2f);
            this.magnitude = Math.max(2, Math.min(36,
                    this.blast + this.immediateRows + this.delayedRows
                            + this.radiationRating / 2 + this.emp / 2
                            + this.disarm / 18 + this.silo / 18 + sizeBonus));
            Color[] palette = paletteFor(this.family);
            this.hot = palette[0];
            this.mid = palette[1];
            this.dark = palette[2];
            this.dust = palette[3];
            this.fallout = palette[4];
            this.electric = palette[5];
        }

        static ImpactProfile fromMetadata(Map<String, Object> meta) {
            String doctrine = textValue(meta, "doctrine", "");
            String radiationLevel = textValue(meta, "radiation", "CLEAN");
            int inferredEmp = doctrine.toUpperCase().contains("EMP") ? 6 : 0;
            int inferredRad = switch (radiationLevel.toUpperCase()) {
                case "LIGHT" -> 2;
                case "DIRTY" -> 4;
                case "HOT" -> 5;
                case "SEVERE" -> 6;
                case "SALTED" -> 8;
                default -> 0;
            };
            return new ImpactProfile(
                    textValue(meta, "designName", "NUKE IMPACT"),
                    doctrine,
                    textValue(meta, "size", ""),
                    radiationLevel,
                    intValue(meta, "blast", 2),
                    intValue(meta, "radiationRating", inferredRad),
                    intValue(meta, "emp", inferredEmp),
                    intValue(meta, "disarmApplied", 0),
                    intValue(meta, "siloDamageApplied", 0),
                    intValue(meta, "immediateRows", 0),
                    intValue(meta, "delayedRows", 0));
        }

        static ImpactProfile debug(String variant, String label) {
            String v = variant == null ? "" : variant.trim().toLowerCase();
            if (v.isEmpty()) v = "doomsday";
            return switch (v) {
                case "training", "placeholder", "small" ->
                        new ImpactProfile(label, "PLACEHOLDER", "TACTICAL",
                                "CLEAN", 2, 1, 0, 1, 0, 2, 0);
                case "tactical", "blast", "light" ->
                        new ImpactProfile(label, "TACTICAL_BLAST", "TACTICAL",
                                "CLEAN", 4, 1, 0, 1, 0, 4, 0);
                case "heavy", "strategic" ->
                        new ImpactProfile(label, "HEAVY_BLAST", "STRATEGIC",
                                "LIGHT", 8, 2, 0, 3, 1, 7, 2);
                case "dirty", "dirty_payload" ->
                        new ImpactProfile(label, "DIRTY_PAYLOAD", "TACTICAL",
                                "DIRTY", 2, 5, 0, 1, 0, 3, 6);
                case "salted", "salt" ->
                        new ImpactProfile(label, "SALTED_PAYLOAD", "STRATEGIC",
                                "SALTED", 3, 8, 0, 2, 1, 4, 8);
                case "emp", "disruptor" ->
                        new ImpactProfile(label, "EMP_PAYLOAD", "THEATER",
                                "HOT", 1, 2, 8, 3, 2, 1, 2);
                case "bunker", "buster" ->
                        new ImpactProfile(label, "BUNKER_BUSTER", "THEATER",
                                "CLEAN", 3, 0, 0, 5, 7, 3, 1);
                case "concrete", "concrete_blaster" ->
                        new ImpactProfile(label, "CONCRETE_BLASTER", "STRATEGIC",
                                "LIGHT", 2, 1, 0, 6, 8, 2, 1);
                case "clean", "fusion" ->
                        new ImpactProfile(label, "CLEAN_FUSION", "STRATEGIC",
                                "CLEAN", 6, 1, 0, 3, 1, 5, 0);
                default ->
                        new ImpactProfile(label, "DOOMSDAY", "DOOMSDAY_SCALE",
                                "SEVERE", 10, 6, 4, 8, 8, 10, 6);
            };
        }

        long durationNs() {
            long base = switch (family) {
                case EMP -> 2_900_000_000L;
                case TACTICAL, TRAINING -> 3_050_000_000L;
                case DIRTY, SALTED -> 4_250_000_000L;
                case DOOMSDAY -> 4_650_000_000L;
                default -> 3_650_000_000L;
            };
            return base + Math.min(600_000_000L, magnitude * 12_000_000L);
        }

        boolean isRadiological() {
            return family == WeaponVisualFamily.DIRTY
                    || family == WeaponVisualFamily.SALTED
                    || radiationRating >= 4;
        }

        boolean isPenetrator() {
            return family == WeaponVisualFamily.BUNKER || silo >= 5 || disarm >= 5;
        }

        boolean isEmpHeavy() {
            return family == WeaponVisualFamily.EMP || emp >= 5;
        }

        float thermalScale() {
            return switch (family) {
                case EMP -> 0.42f;
                case CLEAN -> 1.12f;
                case DOOMSDAY -> 1.45f;
                case DIRTY, SALTED -> 0.78f;
                case BUNKER -> 0.64f;
                default -> 0.92f;
            };
        }

        float cloudScale() {
            return switch (family) {
                case EMP -> 0.32f;
                case BUNKER -> 0.55f;
                case CLEAN -> 0.72f;
                case DIRTY, SALTED -> 1.05f;
                case DOOMSDAY -> 1.42f;
                default -> 0.92f;
            };
        }

        float dustScale() {
            return switch (family) {
                case BUNKER -> 1.65f;
                case HEAVY, DOOMSDAY -> 1.25f;
                case EMP, CLEAN -> 0.62f;
                default -> 1f;
            };
        }

        float falloutScale() {
            return switch (family) {
                case SALTED -> 1.75f;
                case DIRTY -> 1.45f;
                case DOOMSDAY -> 1.18f;
                case CLEAN, BUNKER, TACTICAL, TRAINING -> 0.28f;
                case EMP -> 0.55f;
                default -> 0.75f;
            };
        }

        private static WeaponVisualFamily familyFor(String doctrine, String radLabel,
                                                    int radRating, int emp, int silo,
                                                    int disarm) {
            String d = doctrine == null ? "" : doctrine.toUpperCase();
            String r = radLabel == null ? "" : radLabel.toUpperCase();
            if (d.contains("DOOMSDAY")) return WeaponVisualFamily.DOOMSDAY;
            if (d.contains("EMP") || emp >= 5) return WeaponVisualFamily.EMP;
            if (d.contains("SALTED") || r.contains("SALTED")) return WeaponVisualFamily.SALTED;
            if (d.contains("DIRTY") || radRating >= 4 || r.contains("DIRTY")) {
                return WeaponVisualFamily.DIRTY;
            }
            if (d.contains("BUNKER") || d.contains("CONCRETE") || silo >= 5 || disarm >= 5) {
                return WeaponVisualFamily.BUNKER;
            }
            if (d.contains("CLEAN")) return WeaponVisualFamily.CLEAN;
            if (d.contains("HEAVY")) return WeaponVisualFamily.HEAVY;
            if (d.contains("TACTICAL")) return WeaponVisualFamily.TACTICAL;
            return WeaponVisualFamily.TRAINING;
        }

        private static float sizeScaleFor(String size) {
            String s = size == null ? "" : size.toUpperCase();
            return switch (s) {
                case "MICRO" -> 0.62f;
                case "TACTICAL" -> 0.82f;
                case "THEATER" -> 1.00f;
                case "STRATEGIC" -> 1.18f;
                case "SUPERHEAVY" -> 1.38f;
                case "DOOMSDAY_SCALE" -> 1.70f;
                default -> 1.0f;
            };
        }

        private static Color[] paletteFor(WeaponVisualFamily family) {
            return switch (family) {
                case EMP -> new Color[] {
                        new Color(210, 248, 255), new Color(76, 206, 255),
                        new Color(20, 72, 118), new Color(88, 104, 116),
                        new Color(110, 230, 255), new Color(80, 240, 255)
                };
                case DIRTY -> new Color[] {
                        new Color(255, 226, 134), new Color(192, 205, 72),
                        new Color(55, 78, 42), new Color(116, 98, 62),
                        new Color(130, 236, 92), new Color(176, 255, 130)
                };
                case SALTED -> new Color[] {
                        new Color(250, 240, 172), new Color(130, 230, 178),
                        new Color(44, 90, 72), new Color(102, 110, 98),
                        new Color(78, 255, 154), new Color(116, 255, 222)
                };
                case BUNKER -> new Color[] {
                        new Color(255, 236, 190), new Color(218, 142, 74),
                        new Color(58, 52, 50), new Color(145, 132, 116),
                        new Color(196, 172, 122), new Color(255, 206, 116)
                };
                case CLEAN -> new Color[] {
                        new Color(255, 255, 252), new Color(255, 214, 118),
                        new Color(100, 110, 118), new Color(130, 142, 146),
                        new Color(255, 232, 150), new Color(210, 248, 255)
                };
                case DOOMSDAY -> new Color[] {
                        new Color(255, 255, 232), new Color(255, 86, 48),
                        new Color(30, 24, 26), new Color(104, 86, 74),
                        new Color(144, 245, 86), new Color(112, 220, 255)
                };
                case HEAVY -> new Color[] {
                        new Color(255, 244, 202), new Color(255, 132, 54),
                        new Color(80, 58, 50), new Color(128, 112, 96),
                        new Color(238, 188, 96), new Color(255, 214, 110)
                };
                default -> new Color[] {
                        new Color(255, 248, 214), new Color(255, 166, 68),
                        new Color(86, 70, 62), new Color(132, 112, 92),
                        new Color(236, 188, 96), new Color(255, 210, 106)
                };
            };
        }
    }

    private static final class NuclearImpactEffect extends VisualEffect {
        private final Rectangle target;
        private final ImpactProfile profile;
        private final int magnitude;
        private final String designName;
        private final String radiation;
        private final boolean globalFlash;
        private final List<Particle> sparks = new ArrayList<>();
        private final List<Particle> dust = new ArrayList<>();
        private final List<Particle> ash = new ArrayList<>();
        private final List<Particle> ions = new ArrayList<>();
        private final List<Particle> debris = new ArrayList<>();

        NuclearImpactEffect(Rectangle target, ImpactProfile profile,
                            Random rng, boolean globalFlash) {
            super((profile == null ? ImpactProfile.debug("", "DEBUG YIELD") : profile).durationNs());
            this.target = new Rectangle(target == null ? new Rectangle() : target);
            this.profile = profile == null ? ImpactProfile.debug("", "DEBUG YIELD") : profile;
            this.magnitude = this.profile.magnitude;
            this.designName = this.profile.designName;
            this.radiation = this.profile.radiationLabel;
            this.globalFlash = globalFlash;

            Point c = centerOf(this.target);
            int base = Math.max(120, Math.max(this.target.width, this.target.height));
            float spread = base * (0.12f + 0.06f * this.profile.sizeScale);

            int sparkCount = Math.min(190, Math.round((46 + this.magnitude * 6)
                    * (this.profile.family == WeaponVisualFamily.EMP ? 0.55f : 1f)));
            for (int i = 0; i < sparkCount; i++) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                float speed = 86f + rng.nextFloat() * (155f + this.magnitude * 7f);
                float x = c.x + (rng.nextFloat() - 0.5f) * spread;
                float y = c.y + (rng.nextFloat() - 0.5f) * spread * 0.55f;
                float upward = 65f + rng.nextFloat() * 150f;
                Color col = sparkColor(i);
                sparks.add(new Particle(x, y,
                        (float) Math.cos(a) * speed,
                        (float) Math.sin(a) * speed * 0.55f - upward,
                        1.8f + rng.nextFloat() * 4.8f,
                        col,
                        rng.nextFloat() * 360f));
            }

            int dustCount = Math.min(150, Math.round((30 + this.magnitude * 5)
                    * this.profile.dustScale()));
            for (int i = 0; i < dustCount; i++) {
                int side = rng.nextBoolean() ? 1 : -1;
                float groundY = c.y + this.target.height * (0.18f + rng.nextFloat() * 0.18f);
                float speed = 58f + rng.nextFloat() * (105f + this.magnitude * 5f);
                Color col = mix(this.profile.dust, this.profile.dark, (i % 5) * 0.12f);
                dust.add(new Particle(
                        c.x + (rng.nextFloat() - 0.5f) * spread * 0.8f,
                        groundY,
                        side * speed * (0.65f + rng.nextFloat() * 0.75f),
                        -35f + rng.nextFloat() * 72f,
                        3.5f + rng.nextFloat() * 9f,
                        col,
                        rng.nextFloat() * 360f));
            }

            int ashCount = Math.min(120, Math.round((18 + this.magnitude * 3)
                    * (0.62f + this.profile.falloutScale())));
            for (int i = 0; i < ashCount; i++) {
                Color col = this.profile.isRadiological() && i % 4 == 0
                        ? this.profile.fallout
                        : (i % 2 == 0 ? new Color(105, 112, 108) : new Color(64, 70, 68));
                ash.add(new Particle(
                        c.x + (rng.nextFloat() - 0.5f) * base * 1.45f,
                        c.y - this.target.height * 0.34f - rng.nextFloat() * base * 0.36f,
                        -24f + rng.nextFloat() * 48f,
                        18f + rng.nextFloat() * 95f,
                        1.8f + rng.nextFloat() * 4.5f,
                        col,
                        rng.nextFloat() * 360f));
            }

            int ionCount = Math.min(92, Math.round(16 + this.profile.emp * 9
                    + (this.profile.family == WeaponVisualFamily.EMP ? 28 : 0)));
            for (int i = 0; i < ionCount; i++) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                float speed = 70f + rng.nextFloat() * 190f;
                ions.add(new Particle(
                        c.x + (rng.nextFloat() - 0.5f) * spread,
                        c.y + (rng.nextFloat() - 0.5f) * spread,
                        (float) Math.cos(a) * speed,
                        (float) Math.sin(a) * speed,
                        1.5f + rng.nextFloat() * 3.5f,
                        this.profile.electric,
                        rng.nextFloat() * 360f));
            }

            int debrisCount = Math.min(86, Math.round(10 + this.profile.silo * 7
                    + this.profile.disarm * 3));
            for (int i = 0; i < debrisCount; i++) {
                double a = Math.PI * (0.15 + rng.nextDouble() * 0.70);
                float speed = 70f + rng.nextFloat() * 210f;
                debris.add(new Particle(
                        c.x + (rng.nextFloat() - 0.5f) * spread * 0.65f,
                        c.y + this.target.height * (0.18f + rng.nextFloat() * 0.18f),
                        (float) Math.cos(a) * speed,
                        (float) Math.sin(a) * speed - 145f,
                        3.0f + rng.nextFloat() * 7.0f,
                        mix(this.profile.dust, this.profile.dark, 0.35f + rng.nextFloat() * 0.45f),
                        rng.nextFloat() * 360f));
            }
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            Point c = centerOf(target);
            int base = Math.max(120, Math.max(target.width, target.height));
            int maxDim = Math.max(size.width, size.height);

            Composite oldComposite = g2.getComposite();
            Stroke oldStroke = g2.getStroke();

            drawGlobalFlash(g2, size, t);
            drawThermalBloom(g2, c, base, size, t);
            drawShockRings(g2, c, base, maxDim, t);
            drawCleanHalo(g2, c, base, t);
            drawEmpSurge(g2, c, base, size, t);
            drawPenetratorLance(g2, c, base, t);
            drawDustFront(g2, c, base, t);
            drawMushroomCloud(g2, c, base, t);
            drawHeatDistortion(g2, target, t);
            drawParticleSwarm(g2, sparks, t, 210f, 0.18f, 0.82f, 0.74f, 1.8f, false);
            drawParticleSwarm(g2, debris, t, 180f, 0.22f, 0.94f, 0.46f, 1.7f, false);
            drawParticleSwarm(g2, dust, t, 58f, 0.24f, 1.00f, 0.30f, 2.3f, true);
            drawParticleSwarm(g2, ash, t, 12f, 0.42f, 1.00f, 0.28f, 1.2f, true);
            drawParticleSwarm(g2, ions, t, 0f, 0.16f, 0.72f, 0.52f, 1.3f, true);
            drawRadiationVeil(g2, size, c, base, t);
            drawFallout(g2, size, t, c);
            drawImpactLabel(g2, c, t, designName, radiation);
            drawEdgeVignette(g2, size, c, maxDim, t);

            g2.setStroke(oldStroke);
            g2.setComposite(oldComposite);
        }

        private void drawGlobalFlash(Graphics2D g2, Dimension size, float t) {
            if (!globalFlash) return;
            float flash = 1f - smoothstep(0.00f, 0.18f, t);
            if (flash <= 0f) return;
            float alpha = switch (profile.family) {
                case EMP -> 0.46f;
                case DIRTY, SALTED -> 0.62f;
                case DOOMSDAY -> 0.88f;
                default -> 0.78f;
            };
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * flash));
            g2.setColor(profile.family == WeaponVisualFamily.EMP
                    ? new Color(210, 248, 255) : Color.WHITE);
            g2.fillRect(0, 0, size.width, size.height);
        }

        private void drawThermalBloom(Graphics2D g2, Point c, int base,
                                      Dimension size, float t) {
            float heat = (1f - smoothstep(0.46f, 0.88f, t))
                    * smoothstep(0.00f, 0.06f, t);
            if (heat <= 0f) return;
            float r = base * (0.24f + easeOut(clamp(t / 0.48f)) * 1.58f
                    + magnitude * 0.018f) * profile.thermalScale();
            g2.setComposite(AlphaComposite.SrcOver.derive(
                    (profile.family == WeaponVisualFamily.EMP ? 0.42f : 0.70f) * heat));
            g2.setPaint(new RadialGradientPaint(
                    c.x, c.y, Math.max(80f, r),
                    new float[] {0f, 0.16f, 0.36f, 0.72f, 1f},
                    new Color[] {
                            withAlpha(profile.hot, 245),
                            withAlpha(mix(profile.hot, profile.mid, 0.35f), 225),
                            withAlpha(profile.mid, 165),
                            withAlpha(profile.dark, 70),
                            withAlpha(profile.dark, 0)
                    }));
            g2.fillRect(0, 0, size.width, size.height);

            float core = 1f - smoothstep(0.18f, 0.58f, t);
            if (core > 0f) {
                int cr = (int) (base * (0.12f + easeOut(t) * 0.34f));
                g2.setComposite(AlphaComposite.SrcOver.derive(0.74f * core));
                g2.setPaint(new RadialGradientPaint(
                        c.x, c.y, Math.max(18f, cr),
                        new float[] {0f, 0.45f, 1f},
                        new Color[] {
                                Color.WHITE,
                                withAlpha(mix(profile.hot, profile.mid, 0.25f), 210),
                                withAlpha(profile.mid, 0)
                        }));
                g2.fill(new Ellipse2D.Float(c.x - cr, c.y - cr, cr * 2f, cr * 2f));
            }
        }

        private void drawShockRings(Graphics2D g2, Point c, int base,
                                    int maxDim, float t) {
            for (int i = 0; i < 5; i++) {
                float local = clamp((t - i * 0.065f) / 0.78f);
                if (local <= 0f || local >= 1f) continue;
                float alpha = (1f - local) * (0.52f - i * 0.065f);
                float familyScale = profile.family == WeaponVisualFamily.EMP ? 0.92f
                        : (profile.family == WeaponVisualFamily.DOOMSDAY ? 1.18f : 1f);
                int r = (int) ((base * 0.20f
                        + maxDim * local * (0.62f + magnitude * 0.006f))
                        * familyScale);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setStroke(new BasicStroke(Math.max(1.4f, 8.5f - i * 1.25f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(i == 0 ? Color.WHITE
                        : (i % 2 == 0 ? profile.hot : profile.mid));
                g2.drawOval(c.x - r, c.y - r, r * 2, r * 2);
            }

            float front = smoothstep(0.05f, 0.62f, t) * (1f - smoothstep(0.58f, 1f, t));
            if (front > 0f) {
                int w = (int) (base * (0.85f + t * 4.8f));
                int h = Math.max(18, (int) (base * (0.14f + t * 0.36f)));
                int y = c.y + (int) (target.height * 0.22f) - h / 2;
                g2.setComposite(AlphaComposite.SrcOver.derive(0.33f * front));
                g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.setColor(mix(profile.hot, profile.dust, 0.34f));
                g2.draw(new Ellipse2D.Float(c.x - w / 2f, y, w, h));
            }
        }

        private void drawCleanHalo(Graphics2D g2, Point c, int base, float t) {
            if (profile.family != WeaponVisualFamily.CLEAN) return;
            float phase = smoothstep(0.04f, 0.34f, t) * (1f - smoothstep(0.58f, 1f, t));
            if (phase <= 0f) return;
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.38f * phase));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(235, 250, 255));
            for (int i = 0; i < 4; i++) {
                int rx = (int) (base * (0.28f + t * 1.18f + i * 0.12f));
                int ry = (int) (rx * (0.42f + i * 0.06f));
                g2.draw(new Ellipse2D.Float(c.x - rx, c.y - ry, rx * 2f, ry * 2f));
            }
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }

        private void drawEmpSurge(Graphics2D g2, Point c, int base, Dimension size, float t) {
            if (!profile.isEmpHeavy()) return;
            float surge = smoothstep(0.02f, 0.20f, t) * (1f - smoothstep(0.64f, 0.98f, t));
            if (surge <= 0f) return;
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();

            int rings = 3 + Math.min(3, profile.emp / 3);
            for (int i = 0; i < rings; i++) {
                float local = clamp((t - i * 0.055f) / 0.56f);
                if (local <= 0f || local >= 1f) continue;
                int r = (int) (base * (0.18f + local * (1.65f + profile.emp * 0.07f)));
                g2.setComposite(AlphaComposite.SrcOver.derive((1f - local) * 0.44f));
                g2.setStroke(new BasicStroke(2.2f + i, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.setColor(i % 2 == 0 ? profile.electric : new Color(220, 250, 255));
                g2.drawOval(c.x - r, c.y - r, r * 2, r * 2);
            }

            g2.setComposite(AlphaComposite.SrcOver.derive(0.17f * surge));
            g2.setColor(profile.electric);
            int step = Math.max(22, base / 8);
            int xOffset = (int) (Math.sin(t * 46f) * 8);
            for (int x = Math.floorMod(c.x, step) - step; x < size.width + step; x += step) {
                g2.drawLine(x + xOffset, 0, x - xOffset, size.height);
            }
            for (int y = Math.floorMod(c.y, step) - step; y < size.height + step; y += step) {
                g2.drawLine(0, y - xOffset, size.width, y + xOffset);
            }

            g2.setComposite(AlphaComposite.SrcOver.derive(0.48f * surge));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 18; i++) {
                double a = i * Math.PI * 2.0 / 18.0 + t * 4.2;
                int r1 = (int) (base * (0.14f + (i % 4) * 0.035f));
                int r2 = (int) (base * (0.50f + (i % 5) * 0.075f));
                int x1 = c.x + (int) (Math.cos(a) * r1);
                int y1 = c.y + (int) (Math.sin(a) * r1);
                int x2 = c.x + (int) (Math.cos(a + Math.sin(t * 12f + i) * 0.18) * r2);
                int y2 = c.y + (int) (Math.sin(a + Math.cos(t * 10f + i) * 0.18) * r2);
                g2.drawLine(x1, y1, x2, y2);
            }

            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }

        private void drawPenetratorLance(Graphics2D g2, Point c, int base, float t) {
            if (!profile.isPenetrator()) return;
            float lance = smoothstep(0.03f, 0.18f, t) * (1f - smoothstep(0.50f, 0.86f, t));
            if (lance <= 0f) return;
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();

            int top = c.y - (int) (base * (0.45f + 0.12f * profile.sizeScale));
            int bottom = c.y + (int) (base * (0.56f + 0.08f * profile.sizeScale));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.55f * lance));
            g2.setStroke(new BasicStroke(Math.max(5f, base * 0.035f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(mix(profile.hot, Color.WHITE, 0.28f));
            g2.drawLine(c.x, top, c.x, bottom);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.22f * lance));
            g2.setStroke(new BasicStroke(Math.max(14f, base * 0.10f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(profile.mid);
            g2.drawLine(c.x, top + base / 8, c.x, bottom);

            g2.setComposite(AlphaComposite.SrcOver.derive(0.36f * lance));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(profile.dust);
            int cracks = 10 + Math.min(10, profile.silo + profile.disarm / 2);
            for (int i = 0; i < cracks; i++) {
                double a = -Math.PI / 2.0 + (i - cracks / 2.0) * 0.16;
                int len = (int) (base * (0.22f + (i % 4) * 0.05f + t * 0.36f));
                int x2 = c.x + (int) (Math.cos(a) * len);
                int y2 = bottom + (int) (Math.sin(a) * len * 0.42f);
                g2.drawLine(c.x, bottom, x2, y2);
            }
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }

        private void drawDustFront(Graphics2D g2, Point c, int base, float t) {
            float dustPhase = smoothstep(0.08f, 0.72f, t) * (1f - smoothstep(0.72f, 1f, t));
            if (dustPhase <= 0f) return;
            int w = (int) (base * (0.70f + t * 2.45f + magnitude * 0.018f)
                    * profile.dustScale());
            int h = Math.max(18, (int) (base * (0.12f + t * 0.30f)
                    * (0.80f + profile.dustScale() * 0.22f)));
            int y = c.y + (int) (target.height * (0.20f + t * 0.10f));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.32f * dustPhase));
            g2.setPaint(new GradientPaint(
                    c.x - w / 2f, y, withAlpha(profile.dark, 30),
                    c.x, y + h, withAlpha(profile.dust, 180),
                    true));
            g2.fill(new Ellipse2D.Float(c.x - w / 2f, y - h / 2f, w, h));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.20f * dustPhase));
            g2.setColor(profile.dark);
            g2.fill(new Ellipse2D.Float(c.x - w * 0.33f, y - h * 0.12f,
                    w * 0.66f, h * 0.52f));
        }

        private void drawMushroomCloud(Graphics2D g2, Point c, int base, float t) {
            float rise = easeOut(clamp((t - 0.05f) / 0.78f));
            float fade = 1f - smoothstep(0.78f, 1f, t);
            if (rise <= 0f || fade <= 0f) return;

            float heat = 1f - smoothstep(0.22f, 0.60f, t);
            Color hot = mix(profile.hot, profile.dust, 1f - heat);
            Color mid = mix(profile.mid, profile.dust, 1f - heat);
            Color dark = mix(profile.dark, new Color(34, 38, 38), 1f - heat);
            float cloudScale = profile.cloudScale();

            int stemW = Math.max(10, (int) (base * (0.16f + magnitude * 0.0045f)
                    * rise * cloudScale));
            int stemH = Math.max(10, (int) (base * 0.92f * rise * cloudScale));
            int stemX = c.x - stemW / 2;
            int stemY = c.y - stemH / 2;

            g2.setComposite(AlphaComposite.SrcOver.derive(0.43f * fade));
            g2.setPaint(new GradientPaint(stemX, stemY, hot,
                    stemX + stemW, stemY + stemH, dark));
            g2.fillRoundRect(stemX, stemY, stemW, stemH,
                    Math.max(14, stemW), Math.max(14, stemW));

            int capW = Math.max(18, (int) (base * (0.78f + magnitude * 0.013f)
                    * rise * cloudScale));
            int capH = Math.max(14, (int) (base * 0.42f * rise * cloudScale));
            int capY = c.y - stemH / 2 - (int) (capH * 0.55f);

            for (int i = 0; i < 8; i++) {
                float lane = (i - 3.5f) / 3.5f;
                float wobble = (float) Math.sin(t * 13f + i * 0.9f) * base * 0.026f;
                int w = (int) (capW * (0.28f + (1f - Math.abs(lane)) * 0.30f));
                int h = (int) (capH * (0.54f + (i % 3) * 0.11f));
                int x = c.x + (int) (lane * capW * 0.36f + wobble) - w / 2;
                int y = capY + (int) (Math.abs(lane) * capH * 0.10f) + (i % 2) * capH / 14;
                float alpha = (0.28f + (1f - Math.abs(lane)) * 0.14f) * fade;
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(i % 3 == 0 ? hot : (i % 3 == 1 ? mid : dark));
                g2.fill(new Ellipse2D.Float(x, y, Math.max(1, w), Math.max(1, h)));
            }

            g2.setComposite(AlphaComposite.SrcOver.derive(0.25f * fade));
            g2.setColor(new Color(20, 24, 24));
            g2.fill(new Ellipse2D.Float(c.x - capW * 0.26f,
                    capY + capH * 0.30f, capW * 0.52f, capH * 0.34f));
        }

        private void drawHeatDistortion(Graphics2D g2, Rectangle r, float t) {
            if (r.width <= 0 || r.height <= 0) return;
            float alpha = 0.25f * (1f - smoothstep(0.38f, 0.92f, t));
            if (alpha <= 0f) return;
            int wobble = (int) (Math.sin(t * 88f) * 10);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            for (int y = r.y; y < r.y + r.height; y += 8) {
                int xShift = ((y / 8) % 2 == 0 ? wobble : -wobble);
                g2.setColor((y / 8) % 4 == 0
                        ? mix(profile.hot, Color.WHITE, 0.35f)
                        : profile.mid);
                g2.fillRect(r.x + xShift, y, r.width, 2);
            }
            g2.setColor(mix(profile.electric, Color.WHITE, 0.35f));
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawRect(r.x - 3, r.y - 3, r.width + 6, r.height + 6);
        }

        private void drawParticleSwarm(Graphics2D g2, List<Particle> swarm, float t,
                                       float gravity, float fadeStart, float fadeEnd,
                                       float alphaScale, float growth,
                                       boolean round) {
            float seconds = t * (durationNs / 1_000_000_000f);
            float fade = 1f - smoothstep(fadeStart, fadeEnd, t);
            if (fade <= 0f) return;
            for (Particle p : swarm) {
                float x = p.x + p.vx * seconds;
                float y = p.y + p.vy * seconds + gravity * seconds * seconds;
                float s = p.size * (1f + t * growth);
                g2.setComposite(AlphaComposite.SrcOver.derive(alphaScale * fade));
                g2.setColor(p.color);
                AffineTransform old = g2.getTransform();
                g2.translate(x, y);
                g2.rotate(Math.toRadians(p.angle + seconds * 145f));
                int is = Math.max(1, (int) s);
                if (round) {
                    g2.fill(new Ellipse2D.Float(-s / 2f, -s / 2f, s, s));
                } else {
                    g2.fillRect(-is / 2, -is / 2, is, is);
                }
                g2.setTransform(old);
            }
        }

        private void drawRadiationVeil(Graphics2D g2, Dimension size, Point c,
                                       int base, float t) {
            float strength = profile.falloutScale()
                    * smoothstep(0.28f, 0.72f, t)
                    * (1f - smoothstep(0.82f, 1f, t));
            if (strength <= 0.05f) return;
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();

            int bands = profile.family == WeaponVisualFamily.SALTED ? 8 : 5;
            g2.setComposite(AlphaComposite.SrcOver.derive(0.075f * Math.min(1.5f, strength)));
            g2.setColor(profile.fallout);
            for (int i = 0; i < bands; i++) {
                int r = (int) (base * (0.36f + i * 0.20f + t * (0.70f + i * 0.05f)));
                g2.drawOval(c.x - r, c.y - r, r * 2, r * 2);
            }

            g2.setComposite(AlphaComposite.SrcOver.derive(0.11f * Math.min(1.35f, strength)));
            g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int count = 18 + Math.min(46, profile.radiationRating * 6 + profile.delayedRows * 2);
            for (int i = 0; i < count; i++) {
                double angle = i * 2.3999632 + t * 3.4;
                int radius = (int) (base * (0.24f + (i % 9) * 0.08f + t * 0.86f));
                int x = c.x + (int) (Math.cos(angle) * radius);
                int y = c.y + (int) (Math.sin(angle * 0.72) * radius * 0.58);
                int len = 12 + i % 18;
                g2.drawLine(x, y, x + (int) (Math.cos(angle + 1.3) * len),
                        y + (int) (Math.sin(angle + 1.3) * len));
            }
            if (profile.family == WeaponVisualFamily.SALTED) {
                g2.setComposite(AlphaComposite.SrcOver.derive(0.07f * strength));
                g2.setColor(new Color(220, 255, 226));
                for (int y = Math.floorMod((int) (t * 420), 18) - 18;
                     y < size.height + 18; y += 18) {
                    g2.drawLine(0, y, size.width, y + 28);
                }
            }

            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }

        private void drawFallout(Graphics2D g2, Dimension size, float t, Point c) {
            float fallout = profile.falloutScale()
                    * smoothstep(0.44f, 0.92f, t)
                    * (1f - smoothstep(0.90f, 1f, t));
            if (fallout <= 0f) return;
            int count = 20 + Math.min(100,
                    magnitude * 3 + profile.radiationRating * 7 + profile.delayedRows * 2);
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.25f, 0.13f * fallout)));
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(profile.fallout);
            for (int i = 0; i < count; i++) {
                int x = Math.floorMod((int) (c.x + i * 101 + t * 380), Math.max(1, size.width));
                int y = Math.floorMod((int) (c.y + i * 61 + t * 760), Math.max(1, size.height));
                int len = 5 + i % 15;
                g2.drawLine(x, y, x - len / 2, y + len);
            }
        }

        private Color sparkColor(int i) {
            return switch (i % 7) {
                case 0 -> Color.WHITE;
                case 1 -> profile.hot;
                case 2 -> mix(profile.hot, profile.mid, 0.38f);
                case 3 -> profile.mid;
                case 4 -> mix(profile.mid, profile.dark, 0.35f);
                case 5 -> profile.isEmpHeavy() ? profile.electric : profile.dust;
                default -> profile.isRadiological() ? profile.fallout : mix(profile.hot, profile.dust, 0.25f);
            };
        }

        private void drawImpactLabel(Graphics2D g2, Point c, float t,
                                     String design, String radiation) {
            float alpha = smoothstep(0.06f, 0.18f, t) * (1f - smoothstep(0.66f, 1f, t));
            if (alpha <= 0f) return;
            String title = "NUKE IMPACT";
            String family = switch (profile.family) {
                case TRAINING -> "TRAINING";
                case TACTICAL -> "TACTICAL";
                case HEAVY -> "HEAVY";
                case DIRTY -> "DIRTY";
                case SALTED -> "SALTED";
                case EMP -> "EMP";
                case BUNKER -> "PENETRATOR";
                case CLEAN -> "CLEAN";
                case DOOMSDAY -> "DOOMSDAY";
            };
            String sub = trim(design, 22) + " // " + family + " // " + trim(radiation, 12);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g2.setFont(MabUiTheme.STENCIL_HEADLINE.deriveFont(Font.BOLD, 31f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(title);
            int y = c.y - Math.max(74, target.height / 4);
            g2.setColor(new Color(0, 0, 0, 205));
            g2.drawString(title, c.x - tw / 2 + 3, y + 3);
            g2.setColor(Color.WHITE);
            g2.drawString(title, c.x - tw / 2, y);

            g2.setFont(MabUiTheme.TERM_MED.deriveFont(Font.BOLD, 13f));
            fm = g2.getFontMetrics();
            int sw = fm.stringWidth(sub);
            g2.setColor(MabUiTheme.C_AMBER);
            g2.drawString(sub, c.x - sw / 2, y + 24);
        }

        private void drawEdgeVignette(Graphics2D g2, Dimension size, Point c,
                                      int maxDim, float t) {
            float alpha = smoothstep(0.10f, 0.42f, t) * (1f - smoothstep(0.76f, 1f, t));
            if (alpha <= 0f) return;
            g2.setComposite(AlphaComposite.SrcOver.derive(0.36f * alpha));
            g2.setPaint(new RadialGradientPaint(
                    c.x, c.y, Math.max(80f, maxDim * 0.72f),
                    new float[] {0f, 0.48f, 1f},
                    new Color[] {
                            new Color(0, 0, 0, 0),
                            new Color(0, 0, 0, 50),
                            new Color(0, 0, 0, 195)
                    }));
            g2.fillRect(0, 0, size.width, size.height);
        }
    }

    private static final class LaunchTrailEffect extends VisualEffect {
        private final Point from;
        private final Point to;
        private final String label;

        LaunchTrailEffect(Rectangle from, Rectangle to, String label) {
            super(1_350_000_000L);
            this.from = centerOf(from);
            this.to = centerOf(to);
            this.label = label == null ? "LAUNCH" : label;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float head = easeOut(clamp(t / 0.82f));
            float fade = 1f - smoothstep(0.72f, 1f, t);
            if (fade <= 0f) return;
            float x = lerp(from.x, to.x, head);
            float y = lerp(from.y, to.y, head) - (float) Math.sin(head * Math.PI) * 90f;

            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.62f * fade));
            g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(MabUiTheme.C_AMBER);
            g2.draw(new Line2D.Float(from.x, from.y, x, y));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.26f * fade));
            g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(MabUiTheme.C_RED);
            g2.draw(new Line2D.Float(from.x, from.y, x, y));

            g2.setComposite(AlphaComposite.SrcOver.derive(0.90f * fade));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(x - 5, y - 5, 10, 10));
            g2.setColor(MabUiTheme.C_CYAN);
            g2.draw(new Ellipse2D.Float(x - 13, y - 13, 26, 26));
            drawLabel(g2, label, (int) x, (int) y - 22, MabUiTheme.C_AMBER, fade);
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }
    }

    private static final class TargetLockEffect extends VisualEffect {
        private final Rectangle target;
        private final String label;

        TargetLockEffect(Rectangle target, String label) {
            super(1_300_000_000L);
            this.target = new Rectangle(target);
            this.label = label;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float fade = 1f - smoothstep(0.70f, 1f, t);
            if (fade <= 0f) return;
            Point c = centerOf(target);
            int r = (int) (Math.max(target.width, target.height) * (0.28f + t * 0.22f));
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.62f * fade));
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(MabUiTheme.C_RED);
            g2.drawOval(c.x - r, c.y - r, r * 2, r * 2);
            g2.drawLine(c.x - r - 14, c.y, c.x - r / 2, c.y);
            g2.drawLine(c.x + r / 2, c.y, c.x + r + 14, c.y);
            g2.drawLine(c.x, c.y - r - 14, c.x, c.y - r / 2);
            g2.drawLine(c.x, c.y + r / 2, c.x, c.y + r + 14);
            int sweepY = target.y + (int) (target.height * t);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.34f * fade));
            g2.fillRect(target.x, sweepY - 3, target.width, 6);
            drawLabel(g2, label, c.x, target.y + 24, MabUiTheme.C_RED, fade);
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }
    }

    private static final class InterceptBurstEffect extends VisualEffect {
        private final Rectangle target;
        private final String label;

        InterceptBurstEffect(Rectangle target, String label) {
            super(1_250_000_000L);
            this.target = new Rectangle(target);
            this.label = label == null ? "INTERCEPT" : label;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float fade = 1f - smoothstep(0.70f, 1f, t);
            if (fade <= 0f) return;
            Point c = centerOf(target);
            int radius = (int) (Math.max(target.width, target.height) * (0.18f + t * 0.62f));
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.54f * fade));
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(MabUiTheme.C_CYAN);
            g2.drawOval(c.x - radius, c.y - radius, radius * 2, radius * 2);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2.0 / 16.0 + t * 2.4;
                int x1 = c.x + (int) (Math.cos(a) * radius * 0.35);
                int y1 = c.y + (int) (Math.sin(a) * radius * 0.35);
                int x2 = c.x + (int) (Math.cos(a) * radius);
                int y2 = c.y + (int) (Math.sin(a) * radius);
                g2.drawLine(x1, y1, x2, y2);
            }
            drawLabel(g2, trim(label, 18), c.x, c.y, MabUiTheme.C_CYAN, fade);
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }
    }

    private static final class BoardPulseEffect extends VisualEffect {
        private final Rectangle target;
        private final Color color;
        private final String label;

        BoardPulseEffect(Rectangle target, Color color, String label, long durationNs) {
            super(durationNs);
            this.target = new Rectangle(target);
            this.color = color == null ? MabUiTheme.C_CYAN : color;
            this.label = label == null ? "" : label;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float fade = 1f - smoothstep(0.66f, 1f, t);
            if (fade <= 0f) return;
            Composite old = g2.getComposite();
            Stroke oldStroke = g2.getStroke();
            int grow = (int) (18f * easeOut(t));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.36f * fade));
            g2.setColor(color);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(target.x - grow, target.y - grow,
                    target.width + grow * 2, target.height + grow * 2);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.12f * fade));
            g2.fillRect(target.x, target.y, target.width, target.height);
            drawLabel(g2, label, target.x + target.width / 2,
                    target.y + Math.max(24, target.height / 6), color, fade);
            g2.setStroke(oldStroke);
            g2.setComposite(old);
        }
    }

    private static final class AftershockEffect extends VisualEffect {
        private final Rectangle target;
        private final int rows;
        private final String radiation;

        AftershockEffect(Rectangle target, int rows, String radiation) {
            super(1_200_000_000L);
            this.target = new Rectangle(target);
            this.rows = Math.max(1, rows);
            this.radiation = radiation == null ? "FALLOUT" : radiation;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float fade = 1f - smoothstep(0.65f, 1f, t);
            if (fade <= 0f) return;
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.24f * fade));
            g2.setColor(radiation.toUpperCase().contains("HEAVY")
                    ? MabUiTheme.C_GREEN : MabUiTheme.C_AMBER);
            int bandH = Math.max(6, target.height / 20);
            for (int i = 0; i < rows + 2; i++) {
                int y = target.y + target.height - (int) ((i + 1 + t * 5f) * bandH * 1.6f);
                g2.fillRect(target.x - 8, y, target.width + 16, bandH);
            }
            drawLabel(g2, "AFTERSHOCK", target.x + target.width / 2,
                    target.y + target.height - 24, MabUiTheme.C_AMBER, fade);
            g2.setComposite(old);
        }
    }

    private static final class DefconPulseEffect extends VisualEffect {
        private final String level;

        DefconPulseEffect(String level) {
            super(1_600_000_000L);
            this.level = level == null ? "" : level;
        }

        @Override
        void paint(Graphics2D g2, long now, Dimension size) {
            float t = progress(now);
            float fade = 1f - smoothstep(0.70f, 1f, t);
            if (fade <= 0f) return;
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.18f * fade));
            g2.setColor(MabUiTheme.C_RED);
            g2.fillRect(0, 0, size.width, size.height);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.32f * fade));
            g2.setColor(MabUiTheme.C_AMBER);
            int y = (int) (size.height * t);
            g2.fillRect(0, y - 4, size.width, 8);
            String text = level.isBlank() ? "DEFCON SHIFT" : "DEFCON " + level;
            drawLabel(g2, text, size.width / 2, Math.max(48, size.height / 7),
                    MabUiTheme.C_RED, fade);
            g2.setComposite(old);
        }
    }

    private static final class Particle {
        final float x;
        final float y;
        final float vx;
        final float vy;
        final float size;
        final Color color;
        final float angle;

        Particle(float x, float y, float vx, float vy, float size, Color color, float angle) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.angle = angle;
        }
    }

    private static Point centerOf(Rectangle r) {
        if (r == null) return new Point(0, 0);
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static void drawLabel(Graphics2D g2, String text, int cx, int y,
                                  Color color, float alpha) {
        if (text == null || text.isBlank() || alpha <= 0f) return;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, alpha)));
        g2.setFont(MabUiTheme.STENCIL_SMALL.deriveFont(Font.BOLD, 13f));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text);
        int h = fm.getHeight();
        int x = cx - w / 2;
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(x - 8, y - fm.getAscent() - 4, w + 16, h + 6);
        g2.setColor(color == null ? Color.WHITE : color);
        g2.drawString(text, x, y);
        g2.setComposite(old);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t);
    }

    private static float easeOut(float t) {
        t = clamp(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return x >= edge1 ? 1f : 0f;
        float t = clamp((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static Color mix(Color a, Color b, float t) {
        t = clamp(t);
        int r = (int) lerp(a.getRed(), b.getRed(), t);
        int g = (int) lerp(a.getGreen(), b.getGreen(), t);
        int bl = (int) lerp(a.getBlue(), b.getBlue(), t);
        int al = (int) lerp(a.getAlpha(), b.getAlpha(), t);
        return new Color(r, g, bl, al);
    }

    private static Color withAlpha(Color color, int alpha) {
        Color c = color == null ? Color.WHITE : color;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        String s = text.trim();
        if (s.length() <= max) return s;
        if (max <= 1) return s.substring(0, max);
        return s.substring(0, max - 1) + ".";
    }
}
