package com.tetris.model;

import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Settings.java
 * =============
 * Centralized, persistent settings store for the Tetris game.
 * Implements the Singleton pattern so all components read from the same instance.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CATEGORIES
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   HANDLING — Controls the responsiveness of piece movement.
 *     DAS (Delayed Auto Shift) ... ms before auto-repeat starts
 *     ARR (Auto Repeat Rate) .... ms between repeated moves
 *     SDF (Soft Drop Factor) .... multiplier on gravity speed (0 = instant)
 *
 *   GAMEPLAY — Core game mechanics.
 *     Lock Delay ................ ms before a grounded piece locks
 *     Max Lock Resets ........... how many times the timer can reset
 *     Preview Count ............. number of next pieces shown
 *     IRS Mode .................. initial rotation system (off/tap/hold)
 *     IHS Mode .................. initial hold system (off/tap/hold)
 *
 *   VISUAL — Rendering options.
 *     Grid Opacity .............. 0.0–1.0, grid line visibility
 *     Board Opacity ............. 0.0–1.0, playfield background dimness
 *     Ghost Opacity ............. 0.0–1.0, ghost piece visibility
 *
 *   CONTROLS — Key bindings for every action.
 *     Each action supports a primary and alternate key binding.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * PERSISTENCE
 * ═══════════════════════════════════════════════════════════════════════
 * Settings are saved as a Java Properties file at:
 *   {user.home}/.modern-tetris/settings.properties
 *
 * On first launch, defaults are used and no file exists until the user
 * explicitly saves from the Settings dialog (or the game auto-saves).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DEFAULTS
 * ═══════════════════════════════════════════════════════════════════════
 * Defaults are sourced from a TETR.IO config file (config.ttc) and
 * the Tetris Guideline specification:
 *   DAS = 167ms (TETR.IO das=10 frames × 16.67ms)
 *   ARR = 33ms  (TETR.IO arr=2 frames × 16.67ms)
 *   SDF = 6     (TETR.IO sdf=6)
 */
public class Settings {

    // ─────────────────────── Singleton ───────────────────────────

    private static Settings instance;

    /** Returns the global Settings instance, loading from disk on first call. */
    public static Settings get() {
        if (instance == null) {
            instance = new Settings();
            instance.load();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLING
    // ═══════════════════════════════════════════════════════════════

    /** DAS: Delayed Auto Shift in milliseconds (0–500). */
    private int dasDelay = 167;

    /** ARR: Auto Repeat Rate in milliseconds (0–200). 0 = instant. */
    private int arrInterval = 33;

    /** SDF: Soft Drop Factor (1–40, or 0 for instant). */
    private int softDropFactor = 6;

    // ═══════════════════════════════════════════════════════════════
    // GAMEPLAY
    // ═══════════════════════════════════════════════════════════════

    /** Lock delay in milliseconds (100–2000). */
    private int lockDelay = 500;

    /** Maximum lock resets per piece (0–30). */
    private int maxLockResets = 15;

    /** Number of preview pieces to show (1–6). */
    private int previewCount = 5;

    /** IRS mode: "off", "tap", or "hold". */
    private String irsMode = "tap";

    /** IHS mode: "off", "tap", or "hold". */
    private String ihsMode = "tap";

    // ═══════════════════════════════════════════════════════════════
    // VISUAL
    // ═══════════════════════════════════════════════════════════════

    /** Grid line opacity (0.0 = invisible, 1.0 = fully visible). */
    private double gridOpacity = 0.1;

    /** Board background opacity (0.0 = transparent, 1.0 = opaque). */
    private double boardOpacity = 0.85;

    /** Ghost piece opacity (0.0 = invisible, 1.0 = fully opaque). */
    private double ghostOpacity = 0.15;

    // ═══════════════════════════════════════════════════════════════
    // KEY BINDINGS
    // ═══════════════════════════════════════════════════════════════
    // Each action has a primary key and optional alternate key.
    // A value of 0 means "unbound".

    private int keyMoveLeft     = KeyEvent.VK_LEFT;
    private int keyMoveRight    = KeyEvent.VK_RIGHT;
    private int keySoftDrop     = KeyEvent.VK_DOWN;
    private int keyHardDrop     = KeyEvent.VK_SPACE;
    private int keyRotateCW     = KeyEvent.VK_UP;
    private int keyRotateCCW    = KeyEvent.VK_Z;
    private int keyRotate180    = KeyEvent.VK_A;
    private int keyHold         = KeyEvent.VK_C;
    private int keyHoldAlt      = KeyEvent.VK_SHIFT;
    private int keyPause        = KeyEvent.VK_P;
    private int keyPauseAlt     = KeyEvent.VK_ESCAPE;
    private int keyReset        = KeyEvent.VK_R;
    private int keySettings     = KeyEvent.VK_F1;

    // ─────────────────────── Constructor (private) ──────────────

    private Settings() {}

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE (load / save / reset)
    // ═══════════════════════════════════════════════════════════════

    private static final Path SETTINGS_DIR =
            Paths.get(System.getProperty("user.home"), ".modern-tetris");
    private static final Path SETTINGS_FILE =
            SETTINGS_DIR.resolve("settings.properties");

    /**
     * Loads settings from the properties file on disk.
     * If the file doesn't exist or a property is missing, defaults are kept.
     */
    public void load() {
        if (!Files.exists(SETTINGS_FILE)) return;

        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            Properties p = new Properties();
            p.load(in);

            // Handling
            dasDelay       = intProp(p, "handling.das",       dasDelay,       0, 500);
            arrInterval    = intProp(p, "handling.arr",       arrInterval,    0, 200);
            softDropFactor = intProp(p, "handling.sdf",       softDropFactor, 0, 40);

            // Gameplay
            lockDelay      = intProp(p, "gameplay.lockDelay",    lockDelay,      100, 2000);
            maxLockResets  = intProp(p, "gameplay.maxLockResets", maxLockResets,  0,   30);
            previewCount   = intProp(p, "gameplay.previewCount",  previewCount,   1,   6);
            irsMode        = strProp(p, "gameplay.irs",          irsMode,  new String[]{"off","tap","hold"});
            ihsMode        = strProp(p, "gameplay.ihs",          ihsMode,  new String[]{"off","tap","hold"});

            // Visual
            gridOpacity    = dblProp(p, "visual.gridOpacity",  gridOpacity,  0.0, 1.0);
            boardOpacity   = dblProp(p, "visual.boardOpacity", boardOpacity, 0.0, 1.0);
            ghostOpacity   = dblProp(p, "visual.ghostOpacity", ghostOpacity, 0.0, 1.0);

            // Key bindings
            keyMoveLeft    = intProp(p, "keys.moveLeft",    keyMoveLeft,    0, 65535);
            keyMoveRight   = intProp(p, "keys.moveRight",   keyMoveRight,   0, 65535);
            keySoftDrop    = intProp(p, "keys.softDrop",    keySoftDrop,    0, 65535);
            keyHardDrop    = intProp(p, "keys.hardDrop",    keyHardDrop,    0, 65535);
            keyRotateCW    = intProp(p, "keys.rotateCW",    keyRotateCW,    0, 65535);
            keyRotateCCW   = intProp(p, "keys.rotateCCW",   keyRotateCCW,   0, 65535);
            keyRotate180   = intProp(p, "keys.rotate180",   keyRotate180,   0, 65535);
            keyHold        = intProp(p, "keys.hold",        keyHold,        0, 65535);
            keyHoldAlt     = intProp(p, "keys.holdAlt",     keyHoldAlt,     0, 65535);
            keyPause       = intProp(p, "keys.pause",       keyPause,       0, 65535);
            keyPauseAlt    = intProp(p, "keys.pauseAlt",    keyPauseAlt,    0, 65535);
            keyReset       = intProp(p, "keys.reset",       keyReset,       0, 65535);
            keySettings    = intProp(p, "keys.settings",    keySettings,    0, 65535);

        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }

    /**
     * Saves all current settings to disk (creates the directory if needed).
     */
    public void save() {
        try {
            Files.createDirectories(SETTINGS_DIR);

            Properties p = new Properties();

            // Handling
            p.setProperty("handling.das",          String.valueOf(dasDelay));
            p.setProperty("handling.arr",          String.valueOf(arrInterval));
            p.setProperty("handling.sdf",          String.valueOf(softDropFactor));

            // Gameplay
            p.setProperty("gameplay.lockDelay",    String.valueOf(lockDelay));
            p.setProperty("gameplay.maxLockResets", String.valueOf(maxLockResets));
            p.setProperty("gameplay.previewCount",  String.valueOf(previewCount));
            p.setProperty("gameplay.irs",          irsMode);
            p.setProperty("gameplay.ihs",          ihsMode);

            // Visual
            p.setProperty("visual.gridOpacity",    String.valueOf(gridOpacity));
            p.setProperty("visual.boardOpacity",   String.valueOf(boardOpacity));
            p.setProperty("visual.ghostOpacity",   String.valueOf(ghostOpacity));

            // Key bindings
            p.setProperty("keys.moveLeft",    String.valueOf(keyMoveLeft));
            p.setProperty("keys.moveRight",   String.valueOf(keyMoveRight));
            p.setProperty("keys.softDrop",    String.valueOf(keySoftDrop));
            p.setProperty("keys.hardDrop",    String.valueOf(keyHardDrop));
            p.setProperty("keys.rotateCW",    String.valueOf(keyRotateCW));
            p.setProperty("keys.rotateCCW",   String.valueOf(keyRotateCCW));
            p.setProperty("keys.rotate180",   String.valueOf(keyRotate180));
            p.setProperty("keys.hold",        String.valueOf(keyHold));
            p.setProperty("keys.holdAlt",     String.valueOf(keyHoldAlt));
            p.setProperty("keys.pause",       String.valueOf(keyPause));
            p.setProperty("keys.pauseAlt",    String.valueOf(keyPauseAlt));
            p.setProperty("keys.reset",       String.valueOf(keyReset));
            p.setProperty("keys.settings",    String.valueOf(keySettings));

            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                p.store(out, "Modern Tetris Settings — do not edit manually");
            }
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    /**
     * Resets all settings to their factory defaults.
     */
    public void resetToDefaults() {
        dasDelay = 167;  arrInterval = 33;  softDropFactor = 6;
        lockDelay = 500;  maxLockResets = 15;  previewCount = 5;
        irsMode = "tap";  ihsMode = "tap";
        gridOpacity = 0.1;  boardOpacity = 0.85;  ghostOpacity = 0.15;
        keyMoveLeft = KeyEvent.VK_LEFT;  keyMoveRight = KeyEvent.VK_RIGHT;
        keySoftDrop = KeyEvent.VK_DOWN;  keyHardDrop = KeyEvent.VK_SPACE;
        keyRotateCW = KeyEvent.VK_UP;    keyRotateCCW = KeyEvent.VK_Z;
        keyRotate180 = KeyEvent.VK_A;    keyHold = KeyEvent.VK_C;
        keyHoldAlt = KeyEvent.VK_SHIFT;  keyPause = KeyEvent.VK_P;
        keyPauseAlt = KeyEvent.VK_ESCAPE; keyReset = KeyEvent.VK_R;
        keySettings = KeyEvent.VK_F1;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    // Handling
    public int getDasDelay()       { return dasDelay; }
    public int getArrInterval()    { return arrInterval; }
    public int getSoftDropFactor() { return softDropFactor; }

    // Gameplay
    public int getLockDelay()      { return lockDelay; }
    public int getMaxLockResets()  { return maxLockResets; }
    public int getPreviewCount()   { return previewCount; }
    public String getIrsMode()     { return irsMode; }
    public String getIhsMode()     { return ihsMode; }

    // Visual
    public double getGridOpacity()  { return gridOpacity; }
    public double getBoardOpacity() { return boardOpacity; }
    public double getGhostOpacity() { return ghostOpacity; }

    // Key bindings
    public int getKeyMoveLeft()   { return keyMoveLeft; }
    public int getKeyMoveRight()  { return keyMoveRight; }
    public int getKeySoftDrop()   { return keySoftDrop; }
    public int getKeyHardDrop()   { return keyHardDrop; }
    public int getKeyRotateCW()   { return keyRotateCW; }
    public int getKeyRotateCCW()  { return keyRotateCCW; }
    public int getKeyRotate180()  { return keyRotate180; }
    public int getKeyHold()       { return keyHold; }
    public int getKeyHoldAlt()    { return keyHoldAlt; }
    public int getKeyPause()      { return keyPause; }
    public int getKeyPauseAlt()   { return keyPauseAlt; }
    public int getKeyReset()      { return keyReset; }
    public int getKeySettings()   { return keySettings; }

    // ═══════════════════════════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════════════════════════

    // Handling
    public void setDasDelay(int v)       { dasDelay = clamp(v, 0, 500); }
    public void setArrInterval(int v)    { arrInterval = clamp(v, 0, 200); }
    public void setSoftDropFactor(int v) { softDropFactor = clamp(v, 0, 40); }

    // Gameplay
    public void setLockDelay(int v)      { lockDelay = clamp(v, 100, 2000); }
    public void setMaxLockResets(int v)  { maxLockResets = clamp(v, 0, 30); }
    public void setPreviewCount(int v)   { previewCount = clamp(v, 1, 6); }
    public void setIrsMode(String v)     { irsMode = v; }
    public void setIhsMode(String v)     { ihsMode = v; }

    // Visual
    public void setGridOpacity(double v)  { gridOpacity = clampD(v, 0.0, 1.0); }
    public void setBoardOpacity(double v) { boardOpacity = clampD(v, 0.0, 1.0); }
    public void setGhostOpacity(double v) { ghostOpacity = clampD(v, 0.0, 1.0); }

    // Key bindings
    public void setKeyMoveLeft(int v)   { keyMoveLeft = v; }
    public void setKeyMoveRight(int v)  { keyMoveRight = v; }
    public void setKeySoftDrop(int v)   { keySoftDrop = v; }
    public void setKeyHardDrop(int v)   { keyHardDrop = v; }
    public void setKeyRotateCW(int v)   { keyRotateCW = v; }
    public void setKeyRotateCCW(int v)  { keyRotateCCW = v; }
    public void setKeyRotate180(int v)  { keyRotate180 = v; }
    public void setKeyHold(int v)       { keyHold = v; }
    public void setKeyHoldAlt(int v)    { keyHoldAlt = v; }
    public void setKeyPause(int v)      { keyPause = v; }
    public void setKeyPauseAlt(int v)   { keyPauseAlt = v; }
    public void setKeyReset(int v)      { keyReset = v; }
    public void setKeySettings(int v)   { keySettings = v; }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY: checks if a key code matches any binding for an action
    // ═══════════════════════════════════════════════════════════════

    /** Returns true if the key code matches MoveLeft. */
    public boolean isMoveLeft(int code)  { return code == keyMoveLeft; }
    public boolean isMoveRight(int code) { return code == keyMoveRight; }
    public boolean isSoftDrop(int code)  { return code == keySoftDrop; }
    public boolean isHardDrop(int code)  { return code == keyHardDrop; }
    public boolean isRotateCW(int code)  { return code == keyRotateCW; }
    public boolean isRotateCCW(int code) { return code == keyRotateCCW; }
    public boolean isRotate180(int code) { return code == keyRotate180; }
    public boolean isHold(int code)      { return code == keyHold || code == keyHoldAlt; }
    public boolean isPause(int code)     { return code == keyPause || code == keyPauseAlt; }
    public boolean isReset(int code)     { return code == keyReset; }
    public boolean isSettings(int code)  { return code == keySettings; }

    // ─────────────────────── Private helpers ─────────────────────

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double clampD(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /** Parses an int property with clamping, falling back to defaultVal. */
    private static int intProp(Properties p, String key, int defaultVal, int min, int max) {
        String s = p.getProperty(key);
        if (s == null) return defaultVal;
        try {
            return clamp(Integer.parseInt(s.trim()), min, max);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /** Parses a double property with clamping. */
    private static double dblProp(Properties p, String key, double defaultVal, double min, double max) {
        String s = p.getProperty(key);
        if (s == null) return defaultVal;
        try {
            return clampD(Double.parseDouble(s.trim()), min, max);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /** Parses a string property, validating against allowed values. */
    private static String strProp(Properties p, String key, String defaultVal, String[] allowed) {
        String s = p.getProperty(key);
        if (s == null) return defaultVal;
        s = s.trim().toLowerCase();
        for (String a : allowed) {
            if (a.equals(s)) return s;
        }
        return defaultVal;
    }
}
