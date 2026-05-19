package com.tetris.model;

import com.tetris.system.AppPaths;

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
 * Settings are saved as a Java Properties file in the OS app data directory
 * selected by {@code AppPaths}.
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

    /** DAS: Delayed Auto Shift in milliseconds (0–500). Default = TETR.IO
     *  factory value of 10 frames ≈ 167 ms. */
    private int dasDelay = 167;

    /** ARR: Auto Repeat Rate in milliseconds (0–200). 0 = instant.
     *  Default = TETR.IO factory value of 2 frames ≈ 33 ms. */
    private int arrInterval = 33;

    /** DCD: DAS Cut Delay in milliseconds (0–500). Time to wait
     *  after a piece spawns / direction changes before re-applying DAS.
     *  TETR.IO factory default is 0. */
    private int dasCutDelay = 0;

    /** SDF: Soft Drop Factor (1–40, or 0 for instant). Default = TETR.IO
     *  factory value of 6× gravity. */
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
    private double ghostOpacity = 0.55;

    // ═══════════════════════════════════════════════════════════════
    // AUDIO
    // ═══════════════════════════════════════════════════════════════

    /** Master SFX volume (0.0 = silent, 1.0 = full). Independent of music. */
    private double sfxVolume = 0.75;

    /** True when SFX should be silenced even with a non-zero volume. */
    private boolean sfxMuted = false;

    // ═══════════════════════════════════════════════════════════════
    // KEY BINDINGS
    // ═══════════════════════════════════════════════════════════════
    // Each action has a primary key and optional alternate key.
    // A value of 0 means "unbound".

    private int keyMoveLeft     = KeyEvent.VK_A;
    private int keyMoveRight    = KeyEvent.VK_D;
    private int keyMoveDown     = KeyEvent.VK_S;
    private int keyMoveUp       = KeyEvent.VK_W;
    private int keyHardDrop     = KeyEvent.VK_R;
    private int keyRotateCW     = KeyEvent.VK_T;
    private int keyRotateCCW    = KeyEvent.VK_F;
    private int keyHold         = KeyEvent.VK_E;
    private int keyHoldAlt      = 0;
    private int keyPause        = KeyEvent.VK_K;
    private int keyPauseAlt     = 0;
    private int keySettings     = KeyEvent.VK_F1;
    private int keyExitStage    = KeyEvent.VK_Z;
    private int keyReset        = KeyEvent.VK_X;

    private int keyP2MoveLeft   = KeyEvent.VK_LEFT;
    private int keyP2MoveRight  = KeyEvent.VK_RIGHT;
    private int keyP2MoveDown   = KeyEvent.VK_DOWN;
    private int keyP2MoveUp     = KeyEvent.VK_UP;
    private int keyP2HardDrop   = KeyEvent.VK_P;
    private int keyP2RotateCW   = KeyEvent.VK_U;
    private int keyP2RotateCCW  = KeyEvent.VK_O;
    private int keyP2Hold       = KeyEvent.VK_I;
    private int keyP2ExitStage  = KeyEvent.VK_Z;
    private int keyP2Reset      = KeyEvent.VK_J;

    private boolean controlsWizardCompleted = false;

    // ─────────────────────── Constructor (private) ──────────────

    private Settings() {}

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE (load / save / reset)
    // ═══════════════════════════════════════════════════════════════

    private static final Path SETTINGS_FILE = AppPaths.settingsFile();

    /**
     * Loads settings from the properties file on disk.
     * If the file doesn't exist or a property is missing, defaults are kept.
     */
    public void load() {
        migrateLegacySettingsIfNeeded();
        if (!Files.exists(SETTINGS_FILE)) return;

        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            Properties p = new Properties();
            p.load(in);

            // Handling
            dasDelay       = intProp(p, "handling.das",       dasDelay,       0, 500);
            arrInterval    = intProp(p, "handling.arr",       arrInterval,    0, 200);
            dasCutDelay    = intProp(p, "handling.dcd",       dasCutDelay,    0, 500);
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

            // Audio
            sfxVolume      = dblProp(p, "audio.sfxVolume",     sfxVolume,    0.0, 1.0);
            sfxMuted       = boolProp(p, "audio.sfxMuted",     sfxMuted);

            // Key bindings
            keyMoveLeft    = intProp(p, "keys.moveLeft",    keyMoveLeft,    0, 65535);
            keyMoveRight   = intProp(p, "keys.moveRight",   keyMoveRight,   0, 65535);
            keyMoveDown    = intProp(p, "keys.moveDown",    keyMoveDown,    0, 65535);
            keyMoveUp      = intProp(p, "keys.moveUp",      keyMoveUp,      0, 65535);
            keyHardDrop    = intProp(p, "keys.hardDrop",    keyHardDrop,    0, 65535);
            keyRotateCW    = intProp(p, "keys.rotateCW",    keyRotateCW,    0, 65535);
            keyRotateCCW   = intProp(p, "keys.rotateCCW",   keyRotateCCW,   0, 65535);
            keyHold        = intProp(p, "keys.hold",        keyHold,        0, 65535);
            keyHoldAlt     = intProp(p, "keys.holdAlt",     keyHoldAlt,     0, 65535);
            keyPause       = intProp(p, "keys.pause",       keyPause,       0, 65535);
            keyPauseAlt    = intProp(p, "keys.pauseAlt",    keyPauseAlt,    0, 65535);
            keySettings    = intProp(p, "keys.settings",    keySettings,    0, 65535);
            keyExitStage   = intProp(p, "keys.exitStage",   keyExitStage,   0, 65535);
            keyReset       = intProp(p, "keys.reset",       keyReset,       0, 65535);

            keyP2MoveLeft  = intProp(p, "keys.p2.moveLeft",  keyP2MoveLeft,  0, 65535);
            keyP2MoveRight = intProp(p, "keys.p2.moveRight", keyP2MoveRight, 0, 65535);
            keyP2MoveDown  = intProp(p, "keys.p2.moveDown",  keyP2MoveDown,  0, 65535);
            keyP2MoveUp    = intProp(p, "keys.p2.moveUp",    keyP2MoveUp,    0, 65535);
            keyP2HardDrop  = intProp(p, "keys.p2.hardDrop",  keyP2HardDrop,  0, 65535);
            keyP2RotateCW  = intProp(p, "keys.p2.rotateCW",  keyP2RotateCW,  0, 65535);
            keyP2RotateCCW = intProp(p, "keys.p2.rotateCCW", keyP2RotateCCW, 0, 65535);
            keyP2Hold      = intProp(p, "keys.p2.hold",      keyP2Hold,      0, 65535);
            keyP2ExitStage = intProp(p, "keys.p2.exitStage", keyP2ExitStage, 0, 65535);
            keyP2Reset     = intProp(p, "keys.p2.reset",     keyP2Reset,     0, 65535);

            controlsWizardCompleted = boolProp(p, "controls.wizardCompleted",
                    controlsWizardCompleted);
            reserveMabActiveCommandKey();

        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }

    /**
     * Saves all current settings to disk (creates the directory if needed).
     */
    public void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());

            Properties p = new Properties();

            // Handling
            p.setProperty("handling.das",          String.valueOf(dasDelay));
            p.setProperty("handling.arr",          String.valueOf(arrInterval));
            p.setProperty("handling.dcd",          String.valueOf(dasCutDelay));
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

            // Audio
            p.setProperty("audio.sfxVolume",       String.valueOf(sfxVolume));
            p.setProperty("audio.sfxMuted",        String.valueOf(sfxMuted));

            // Key bindings
            p.setProperty("keys.moveLeft",    String.valueOf(keyMoveLeft));
            p.setProperty("keys.moveRight",   String.valueOf(keyMoveRight));
            p.setProperty("keys.moveDown",    String.valueOf(keyMoveDown));
            p.setProperty("keys.moveUp",      String.valueOf(keyMoveUp));
            p.setProperty("keys.hardDrop",    String.valueOf(keyHardDrop));
            p.setProperty("keys.rotateCW",    String.valueOf(keyRotateCW));
            p.setProperty("keys.rotateCCW",   String.valueOf(keyRotateCCW));
            p.setProperty("keys.hold",        String.valueOf(keyHold));
            p.setProperty("keys.holdAlt",     String.valueOf(keyHoldAlt));
            p.setProperty("keys.pause",       String.valueOf(keyPause));
            p.setProperty("keys.pauseAlt",    String.valueOf(keyPauseAlt));
            p.setProperty("keys.settings",    String.valueOf(keySettings));
            p.setProperty("keys.exitStage",   String.valueOf(keyExitStage));
            p.setProperty("keys.reset",       String.valueOf(keyReset));

            p.setProperty("keys.p2.moveLeft",  String.valueOf(keyP2MoveLeft));
            p.setProperty("keys.p2.moveRight", String.valueOf(keyP2MoveRight));
            p.setProperty("keys.p2.moveDown",  String.valueOf(keyP2MoveDown));
            p.setProperty("keys.p2.moveUp",    String.valueOf(keyP2MoveUp));
            p.setProperty("keys.p2.hardDrop",  String.valueOf(keyP2HardDrop));
            p.setProperty("keys.p2.rotateCW",  String.valueOf(keyP2RotateCW));
            p.setProperty("keys.p2.rotateCCW", String.valueOf(keyP2RotateCCW));
            p.setProperty("keys.p2.hold",      String.valueOf(keyP2Hold));
            p.setProperty("keys.p2.exitStage", String.valueOf(keyP2ExitStage));
            p.setProperty("keys.p2.reset",     String.valueOf(keyP2Reset));
            p.setProperty("controls.wizardCompleted", String.valueOf(controlsWizardCompleted));

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
        dasDelay = 167;  arrInterval = 33;  dasCutDelay = 0;  softDropFactor = 6;
        lockDelay = 500;  maxLockResets = 15;  previewCount = 5;
        irsMode = "tap";  ihsMode = "tap";
        gridOpacity = 0.1;  boardOpacity = 0.85;  ghostOpacity = 0.55;
        sfxVolume = 0.75;   sfxMuted = false;
        keyMoveLeft = KeyEvent.VK_A;      keyMoveRight = KeyEvent.VK_D;
        keyMoveDown = KeyEvent.VK_S;      keyMoveUp = KeyEvent.VK_W;
        keyHardDrop = KeyEvent.VK_R;
        keyRotateCW = KeyEvent.VK_T;      keyRotateCCW = KeyEvent.VK_F;
        keyHold = KeyEvent.VK_E;          keyHoldAlt = 0;
        keyPause = KeyEvent.VK_K;         keyPauseAlt = 0;
        keySettings = KeyEvent.VK_F1;
        keyExitStage = KeyEvent.VK_Z;     keyReset = KeyEvent.VK_X;
        keyP2MoveLeft = KeyEvent.VK_LEFT; keyP2MoveRight = KeyEvent.VK_RIGHT;
        keyP2MoveDown = KeyEvent.VK_DOWN; keyP2MoveUp = KeyEvent.VK_UP;
        keyP2HardDrop = KeyEvent.VK_P;
        keyP2RotateCW = KeyEvent.VK_U;    keyP2RotateCCW = KeyEvent.VK_O;
        keyP2Hold = KeyEvent.VK_I;
        keyP2ExitStage = KeyEvent.VK_Z;   keyP2Reset = KeyEvent.VK_J;
        controlsWizardCompleted = false;
    }

    /** Resets only keyboard mappings and first-run calibration state. */
    public void resetControlMappingsToDefaults() {
        keyMoveLeft = KeyEvent.VK_A;      keyMoveRight = KeyEvent.VK_D;
        keyMoveDown = KeyEvent.VK_S;      keyMoveUp = KeyEvent.VK_W;
        keyHardDrop = KeyEvent.VK_R;
        keyRotateCW = KeyEvent.VK_T;      keyRotateCCW = KeyEvent.VK_F;
        keyHold = KeyEvent.VK_E;          keyHoldAlt = 0;
        keyPause = KeyEvent.VK_K;         keyPauseAlt = 0;
        keySettings = KeyEvent.VK_F1;
        keyExitStage = KeyEvent.VK_Z;     keyReset = KeyEvent.VK_X;
        keyP2MoveLeft = KeyEvent.VK_LEFT; keyP2MoveRight = KeyEvent.VK_RIGHT;
        keyP2MoveDown = KeyEvent.VK_DOWN; keyP2MoveUp = KeyEvent.VK_UP;
        keyP2HardDrop = KeyEvent.VK_P;
        keyP2RotateCW = KeyEvent.VK_U;    keyP2RotateCCW = KeyEvent.VK_O;
        keyP2Hold = KeyEvent.VK_I;
        keyP2ExitStage = KeyEvent.VK_Z;   keyP2Reset = KeyEvent.VK_J;
        controlsWizardCompleted = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    // Handling
    public int getDasDelay()       { return dasDelay; }
    public int getArrInterval()    { return arrInterval; }
    public int getDasCutDelay()    { return dasCutDelay; }
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

    // Audio
    public double getSfxVolume()    { return sfxVolume; }
    public boolean isSfxMuted()     { return sfxMuted; }

    // Key bindings
    public int getKeyMoveLeft()   { return keyMoveLeft; }
    public int getKeyMoveRight()  { return keyMoveRight; }
    public int getKeyMoveDown()   { return keyMoveDown; }
    public int getKeyMoveUp()     { return keyMoveUp; }
    public int getKeyHardDrop()   { return keyHardDrop; }
    public int getKeyRotateCW()   { return keyRotateCW; }
    public int getKeyRotateCCW()  { return keyRotateCCW; }
    public int getKeyHold()       { return keyHold; }
    public int getKeyHoldAlt()    { return keyHoldAlt; }
    public int getKeyPause()      { return keyPause; }
    public int getKeyPauseAlt()   { return keyPauseAlt; }
    public int getKeySettings()   { return keySettings; }
    public int getKeyExitStage()  { return keyExitStage; }
    public int getKeyReset()      { return keyReset; }

    public int getKeyP2MoveLeft()   { return keyP2MoveLeft; }
    public int getKeyP2MoveRight()  { return keyP2MoveRight; }
    public int getKeyP2MoveDown()   { return keyP2MoveDown; }
    public int getKeyP2MoveUp()     { return keyP2MoveUp; }
    public int getKeyP2HardDrop()   { return keyP2HardDrop; }
    public int getKeyP2RotateCW()   { return keyP2RotateCW; }
    public int getKeyP2RotateCCW()  { return keyP2RotateCCW; }
    public int getKeyP2Hold()           { return keyP2Hold; }
    public int getKeyP2ExitStage()  { return keyP2ExitStage; }
    public int getKeyP2Reset()      { return keyP2Reset; }
    public boolean isControlsWizardCompleted() { return controlsWizardCompleted; }

    // ═══════════════════════════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════════════════════════

    // Handling
    public void setDasDelay(int v)       { dasDelay = clamp(v, 0, 500); }
    public void setArrInterval(int v)    { arrInterval = clamp(v, 0, 200); }
    public void setDasCutDelay(int v)    { dasCutDelay = clamp(v, 0, 500); }
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

    // Audio
    public void setSfxVolume(double v)    { sfxVolume = clampD(v, 0.0, 1.0); }
    public void setSfxMuted(boolean v)    { sfxMuted = v; }

    // Key bindings
    public void setKeyMoveLeft(int v)   { keyMoveLeft = v; }
    public void setKeyMoveRight(int v)  { keyMoveRight = v; }
    public void setKeyMoveDown(int v)   { keyMoveDown = v; }
    public void setKeyMoveUp(int v)     { keyMoveUp = v; }
    public void setKeyHardDrop(int v)   { keyHardDrop = v; }
    public void setKeyRotateCW(int v)   { keyRotateCW = v; }
    public void setKeyRotateCCW(int v)  { keyRotateCCW = v; }
    public void setKeyHold(int v)       { keyHold = v; }
    public void setKeyHoldAlt(int v)    { keyHoldAlt = v; }
    public void setKeyPause(int v)      { keyPause = v; }
    public void setKeyPauseAlt(int v)   { keyPauseAlt = v; }
    public void setKeySettings(int v)   { keySettings = v; }
    public void setKeyExitStage(int v)  { keyExitStage = v; }
    public void setKeyReset(int v)      { keyReset = v; }

    public void setKeyP2MoveLeft(int v)   { keyP2MoveLeft = v; }
    public void setKeyP2MoveRight(int v)  { keyP2MoveRight = v; }
    public void setKeyP2MoveDown(int v)   { keyP2MoveDown = v; }
    public void setKeyP2MoveUp(int v)     { keyP2MoveUp = v; }
    public void setKeyP2HardDrop(int v)   { keyP2HardDrop = v; }
    public void setKeyP2RotateCW(int v)   { keyP2RotateCW = v; }
    public void setKeyP2RotateCCW(int v)  { keyP2RotateCCW = v; }
    public void setKeyP2Hold(int v)             { keyP2Hold = v; }
    public void setKeyP2ExitStage(int v)  { keyP2ExitStage = v; }
    public void setKeyP2Reset(int v)      { keyP2Reset = v; }
    public void setControlsWizardCompleted(boolean completed) {
        controlsWizardCompleted = completed;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY: checks if a key code matches any binding for an action
    // ═══════════════════════════════════════════════════════════════

    /** Returns true if the key code matches MoveLeft. */
    public boolean isMoveLeft(int code)  { return code == keyMoveLeft; }
    public boolean isMoveRight(int code) { return code == keyMoveRight; }
    public boolean isMoveDown(int code)  { return code == keyMoveDown; }
    public boolean isMoveUp(int code)    { return code == keyMoveUp; }
    public boolean isHardDrop(int code)  { return code == keyHardDrop; }
    public boolean isRotateCW(int code)  { return code == keyRotateCW; }
    public boolean isRotateCCW(int code) { return code == keyRotateCCW; }
    public boolean isHold(int code)      { return code == keyHold || code == keyHoldAlt; }
    public boolean isPause(int code)     { return code == keyPause || code == keyPauseAlt; }
    public boolean isSettings(int code)  { return code == keySettings; }
    public boolean isExitStage(int code) { return code == keyExitStage; }
    public boolean isReset(int code)     { return code == keyReset; }

    public boolean isP2MoveLeft(int code)  { return code == keyP2MoveLeft; }
    public boolean isP2MoveRight(int code) { return code == keyP2MoveRight; }
    public boolean isP2MoveDown(int code)  { return code == keyP2MoveDown; }
    public boolean isP2MoveUp(int code)    { return code == keyP2MoveUp; }
    public boolean isP2HardDrop(int code)  { return code == keyP2HardDrop; }
    public boolean isP2RotateCW(int code)  { return code == keyP2RotateCW; }
    public boolean isP2RotateCCW(int code) { return code == keyP2RotateCCW; }
    public boolean isP2Hold(int code)           { return code == keyP2Hold; }
    public boolean isP2ExitStage(int code) { return code == keyP2ExitStage; }
    public boolean isP2Reset(int code)     { return code == keyP2Reset; }

    // ─────────────────────── Private helpers ─────────────────────

    private void reserveMabActiveCommandKey() {
        int q = KeyEvent.VK_Q;
        if (keyMoveLeft == q) keyMoveLeft = KeyEvent.VK_A;
        if (keyMoveRight == q) keyMoveRight = KeyEvent.VK_D;
        if (keyMoveDown == q) keyMoveDown = KeyEvent.VK_S;
        if (keyHardDrop == q) keyHardDrop = KeyEvent.VK_R;
        if (keyRotateCW == q) keyRotateCW = KeyEvent.VK_T;
        if (keyRotateCCW == q) keyRotateCCW = KeyEvent.VK_F;
        if (keyHold == q) keyHold = KeyEvent.VK_E;
        if (keyHoldAlt == q) keyHoldAlt = 0;
        if (keyPause == q) keyPause = KeyEvent.VK_K;
        if (keyPauseAlt == q) keyPauseAlt = 0;
        if (keyExitStage == q) keyExitStage = KeyEvent.VK_Z;
        if (keySettings == q) keySettings = KeyEvent.VK_F1;
    }

    private static void migrateLegacySettingsIfNeeded() {
        Path legacy = AppPaths.legacySettingsFile();
        if (Files.exists(SETTINGS_FILE) || !Files.isRegularFile(legacy)) return;
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Files.copy(legacy, SETTINGS_FILE);
            System.out.println("[settings] Migrated settings to " + SETTINGS_FILE);
        } catch (IOException ex) {
            System.err.println("[settings] Unable to migrate legacy settings: "
                    + ex.getMessage());
        }
    }

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

    private static boolean boolProp(Properties p, String key, boolean defaultVal) {
        String s = p.getProperty(key);
        if (s == null) return defaultVal;
        s = s.trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        return defaultVal;
    }
}
