package com.tetris.audio;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

/**
 * Reusable Swing helpers that emit menu / UI sound effects in response to
 * focus changes, mouse motion, and clicks. Helpers attach lightweight
 * listeners that never block the EDT — they simply forward to
 * {@link SoundEffectManager#play(SoundEffect)}.
 */
public final class SfxUiHooks {

    private static final Random RANDOM = new Random();

    private SfxUiHooks() {}

    /**
     * Attaches the standard menu-button sound stack to a button: hover plays
     * {@code menuhover}, focus plays {@code menutap}, and a click fires
     * {@code menuclick}. Idempotent — calling twice attaches duplicate
     * listeners is avoided via a client-property flag.
     */
    public static void attachMenuButton(AbstractButton button) {
        if (button == null) return;
        if (Boolean.TRUE.equals(button.getClientProperty("sfx.attached"))) return;
        button.putClientProperty("sfx.attached", Boolean.TRUE);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_HOVER);
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_TAP);
            }
        });
        button.addActionListener(SfxUiHooks::onButtonClick);
    }

    /**
     * Variant for primary "confirm" actions — replaces {@code menuclick} with
     * {@code menuconfirm}. Use for Start / Apply / Save.
     */
    public static void attachMenuConfirm(AbstractButton button) {
        if (button == null) return;
        if (Boolean.TRUE.equals(button.getClientProperty("sfx.attached"))) return;
        button.putClientProperty("sfx.attached", Boolean.TRUE);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_HOVER);
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_TAP);
            }
        });
        button.addActionListener(e -> SoundEffectManager.shared().play(SoundEffect.MENU_CONFIRM));
    }

    /**
     * Variant for cancel / back buttons — replaces {@code menuclick} with
     * {@code menuback}.
     */
    public static void attachMenuBack(AbstractButton button) {
        if (button == null) return;
        if (Boolean.TRUE.equals(button.getClientProperty("sfx.attached"))) return;
        button.putClientProperty("sfx.attached", Boolean.TRUE);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_HOVER);
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (!button.isEnabled()) return;
                SoundEffectManager.shared().play(SoundEffect.MENU_TAP);
            }
        });
        button.addActionListener(e -> SoundEffectManager.shared().play(SoundEffect.MENU_BACK));
    }

    /**
     * Plays a random {@code menuhit1/2/3} sound to punctuate large panel
     * transitions (card swaps, modal opens). Safe to call from the EDT.
     */
    public static void playPanelTransition() {
        SoundEffect[] hits = {
                SoundEffect.MENU_HIT_1,
                SoundEffect.MENU_HIT_2,
                SoundEffect.MENU_HIT_3
        };
        SoundEffectManager.shared().play(hits[RANDOM.nextInt(hits.length)]);
    }

    /**
     * Walks the component tree rooted at {@code root} and attaches the
     * standard hover/focus/click stack to every {@link AbstractButton} that
     * does not have a more specific attach already applied.
     */
    public static void attachAllButtons(Component root) {
        if (root == null) return;
        if (root instanceof AbstractButton ab) {
            attachMenuButton(ab);
        }
        if (root instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                attachAllButtons(child);
            }
        }
    }

    private static void onButtonClick(ActionEvent e) {
        SoundEffectManager.shared().play(SoundEffect.MENU_CLICK);
    }
}
