package com.tetris.view;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabPveConfig;
import com.tetris.mab.ui.MabPveSetupDialog;
import com.tetris.model.Settings;
import com.tetris.model.Position;
import com.tetris.model.TetrominoType;
import com.tetris.view.theme.BlockRenderer;
import com.tetris.view.theme.Components;
import com.tetris.view.theme.Components.ButtonStyle;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * StartMenu.java — decorated, resizable launcher window with an
 * inline (continuous) navigation model.
 *
 * Rather than spawning a separate modal dialog for each entry, the
 * Settings and Nuke Builder are embedded as alternate "cards" inside
 * the same window. Clicking Back returns to the main menu. The
 * window itself never closes until the player explicitly quits.
 *
 * Cards (managed by a {@link CardLayout}):
 *   "menu"     — the main entry buttons + falling-piece marquee
 *   "settings" — embedded SettingsPanel + Back button
 *   "nuke"     — embedded NukeBuilderDialog + Back button
 *
 * Class name is preserved so {@link com.tetris.Main} doesn't change.
 */
public class StartMenu extends JFrame {

    private static final String CARD_MENU           = "menu";
    private static final String CARD_SETTINGS       = "settings";
    private static final String CARD_NUKE           = "nuke";
    private static final String CARD_GAME           = "game";
    private static final String CARD_MAB_SELECT     = "mab_select";
    private static final String CARD_MAB_PVE_CONFIG = "mab_pve_config";
    private static final String CARD_MAB_PVP_CONFIG = "mab_pvp_config";
    private static final String CARD_CONTROLS       = "controls";

    private final CardLayout cards;
    private final JPanel cardHost;
    private final MarqueePanel marquee;
    private final Timer animTimer;
    private final java.util.function.IntFunction<GameController> controllerFactory;
    /** Step 15 — optional factory used when launching MAB PvE. */
    private final java.util.function.BiFunction<Integer, GameLaunchMode, GameController> modedFactory;
    /** Step 18 — optional factory that constructs a controller from a {@link MabPveConfig}. */
    private java.util.function.Function<MabPveConfig, GameController> mabPveFactory;
    private java.util.function.Function<MabLocalPvpConfig, GameController> mabLocalPvpFactory;
    /** Step 18 — last-used PvE config so Restart can re-launch with the same selections. */
    private MabPveConfig lastMabPveConfig = MabPveConfig.defaults();
    private MabLocalPvpConfig lastMabLocalPvpConfig = MabLocalPvpConfig.defaults();
    private MabNukeDesignSelection currentMabNukeSelection = MabNukeDesignSelection.defaultSelection();
    /** Currently mounted game controller, if the game card is active. */
    private GameController activeController;
    private JButton firstMenuButton;
    private final int startLevel;

    /**
     * Legacy constructor: kept so external callers that handed us a
     * {@code Runnable} for "play" still compile. The runnable is invoked
     * but the embedded-game flow is unavailable in this mode.
     */
    public StartMenu(Runnable onPlayTetris) {
        this(onPlayTetris, 1, null);
    }

    /**
     * Step 15 — launcher entry point that supports the new
     * {@link GameLaunchMode}. The supplied {@code modedFactory} is
     * used both for normal Play (mode=NORMAL_TETRIS) and for the new
     * "Mutually Assured Blocks — PvE" button.
     */
    public StartMenu(int startLevel,
                     java.util.function.BiFunction<Integer, GameLaunchMode, GameController> modedFactory) {
        this(null, startLevel, lvl -> modedFactory.apply(lvl, GameLaunchMode.NORMAL_TETRIS),
                modedFactory);
    }

    /**
     * Preferred constructor — when the caller hands us a controller
     * factory, the Play button hosts the game inside this same window
     * (continuous menu) instead of opening a new {@link MainFrame}.
     *
     * @param onPlayTetrisFallback used only when controllerFactory is null
     * @param startLevel           starting level passed to new controllers
     * @param controllerFactory    builds a fresh controller per match,
     *                             or {@code null} to fall back to the
     *                             legacy windowed flow
     */
    public StartMenu(Runnable onPlayTetrisFallback, int startLevel,
                     java.util.function.IntFunction<GameController> controllerFactory) {
        this(onPlayTetrisFallback, startLevel, controllerFactory, null);
    }

    /** Internal full-arg constructor (Step 15). */
    private StartMenu(Runnable onPlayTetrisFallback, int startLevel,
                      java.util.function.IntFunction<GameController> controllerFactory,
                      java.util.function.BiFunction<Integer, GameLaunchMode, GameController> modedFactory) {
        super("Modern Tetris");
        this.startLevel = Math.max(1, startLevel);
        this.controllerFactory = controllerFactory;
        this.modedFactory = modedFactory;
        Runnable onPlayTetris = onPlayTetrisFallback != null ? onPlayTetrisFallback : () -> {};
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // Fullscreen-only: borderless, maximized to fill the screen.
        setUndecorated(true);
        setResizable(false);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screen);
        setLocation(0, 0);
        getContentPane().setBackground(Theme.BG_0);

        // Layered pane keeps the marquee behind the cards on the main
        // page only — Settings / Nuke each get an opaque card so the
        // marquee animation doesn't bleed through their UI.
        JLayeredPane root = new JLayeredPane();
        root.setBackground(Theme.BG_0);
        root.setOpaque(true);
        setContentPane(root);

        marquee = new MarqueePanel();
        marquee.setBounds(0, 0, getWidth(), getHeight());
        root.add(marquee, JLayeredPane.DEFAULT_LAYER);

        cards = new CardLayout();
        cardHost = new JPanel(cards);
        cardHost.setOpaque(false);
        cardHost.setBounds(0, 0, getWidth(), getHeight());

        cardHost.add(buildMenuCard(onPlayTetris), CARD_MENU);
        // Settings & Nuke cards are built lazily so we don't pay the
        // cost (or risk init bugs) until the user actually opens them.
        root.add(cardHost, JLayeredPane.PALETTE_LAYER);

        // Keep both layers full-size on resize.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                marquee.setBounds(0, 0, getWidth(), getHeight());
                cardHost.setBounds(0, 0, getWidth(), getHeight());
            }
        });

        animTimer = new Timer(33, e -> marquee.tick());
        animTimer.start();

        addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {
                animTimer.start();
                if (CARD_MENU.equals(currentCard)) focusButton(firstMenuButton);
            }
            @Override public void windowLostFocus  (java.awt.event.WindowEvent e) { animTimer.stop();  }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getRootPane().getActionMap().put("esc", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (CARD_GAME.equals(currentCard)) {
                    if (activeController != null) activeController.handleEscapePause();
                } else if (CARD_MAB_PVE_CONFIG.equals(currentCard)
                        || CARD_MAB_PVP_CONFIG.equals(currentCard)) {
                    showMabSelectCard();
                } else if (CARD_CONTROLS.equals(currentCard)) {
                    showMenuCard();
                } else if (currentCard != null && !CARD_MENU.equals(currentCard)) {
                    showMenuCard();
                } else {
                    confirmQuit();
                }
            }
        });

        SwingUtilities.invokeLater(() -> showControlsWizardCard(true));
    }

    /** Tracks the currently-displayed card so ESC behaves correctly. */
    private String currentCard = CARD_MENU;

    // ─────────────────────── Card switching ──────────────────────

    private void showMenuCard() {
        activeController = null;
        currentCard = CARD_MENU;
        marquee.setVisible(true);
        animTimer.start();
        cards.show(cardHost, CARD_MENU);
        SwingUtilities.invokeLater(() -> focusButton(firstMenuButton));
    }

    private void showSettingsCard() {
        // Always rebuild fresh so changes from previous sessions are
        // re-loaded from disk.
        for (Component c : cardHost.getComponents()) {
            if (CARD_SETTINGS.equals(c.getName())) cardHost.remove(c);
        }
        SettingsPanel settingsPanel = SettingsPanel.createEmbedded(this::showMenuCard);
        JPanel settingsCard = buildEmbeddedCard("Settings", settingsPanel);
        settingsCard.setName(CARD_SETTINGS);
        cardHost.add(settingsCard, CARD_SETTINGS);

        currentCard = CARD_SETTINGS;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_SETTINGS);
        SwingUtilities.invokeLater(settingsPanel::focusInitialControl);
    }

    private void showControlsWizardCard(boolean firstRun) {
        for (Component c : cardHost.getComponents()) {
            if (CARD_CONTROLS.equals(c.getName())) cardHost.remove(c);
        }
        KeyMappingWizardPanel wizard = new KeyMappingWizardPanel(
                this::showMenuCard,
                firstRun ? null : this::showMenuCard);
        JPanel controlsCard = buildEmbeddedCard(
                "Control Calibration",
                wizard,
                firstRun ? null : this::showMenuCard);
        controlsCard.setName(CARD_CONTROLS);
        cardHost.add(controlsCard, CARD_CONTROLS);

        currentCard = CARD_CONTROLS;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_CONTROLS);
        SwingUtilities.invokeLater(wizard::requestInitialFocus);
    }

    private void showNukeCard() {
        for (Component c : cardHost.getComponents()) {
            if (CARD_NUKE.equals(c.getName())) cardHost.remove(c);
        }
        NukeBuilderDialog nuke = NukeBuilderDialog.createEmbedded(this::showMenuCard);
        JButton reset = Components.button("RESET  \u21BA", ButtonStyle.SECONDARY);
        reset.addActionListener(e -> nuke.resetBuild());
        JPanel nukeCard = buildEmbeddedCard("Nuke Builder", nuke, reset);
        nukeCard.setName(CARD_NUKE);
        cardHost.add(nukeCard, CARD_NUKE);

        currentCard = CARD_NUKE;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_NUKE);
        SwingUtilities.invokeLater(nuke::requestFocusInWindow);
    }

    private void showMabNukeBuilderCard(Runnable returnToSetup) {
        for (Component c : cardHost.getComponents()) {
            if (CARD_NUKE.equals(c.getName())) cardHost.remove(c);
        }
        Runnable back = returnToSetup == null ? this::showMenuCard : returnToSetup;
        NukeBuilderDialog nuke = NukeBuilderDialog.createEmbedded(back);
        JButton useDesign = Components.button("USE DESIGN", ButtonStyle.PRIMARY_BLUE);
        JButton reset = Components.button("RESET", ButtonStyle.SECONDARY);
        JButton defaultDesign = Components.button("DEFAULT", ButtonStyle.SECONDARY);
        useDesign.addActionListener(e -> {
            currentMabNukeSelection = MabNukeDesignSelection.fromBuilderDesign(
                    nuke.getDesignForIntegration());
            back.run();
        });
        reset.addActionListener(e -> nuke.resetBuild());
        defaultDesign.addActionListener(e -> {
            currentMabNukeSelection = MabNukeDesignSelection.defaultSelection();
            back.run();
        });
        JPanel nukeCard = buildEmbeddedCard("MAB Nuke Design", nuke, back,
                useDesign, reset, defaultDesign);
        nukeCard.setName(CARD_NUKE);
        cardHost.add(nukeCard, CARD_NUKE);

        currentCard = CARD_NUKE;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_NUKE);
        SwingUtilities.invokeLater(nuke::requestFocusInWindow);
    }

    /** Shows the inline MAB mode-selection card (PvP / PvE / Back). */
    private void showMabSelectCard() {
        for (Component c : cardHost.getComponents()) {
            if (CARD_MAB_SELECT.equals(c.getName())) cardHost.remove(c);
        }

        JButton pvp = Components.button("LOCAL PvP  :: SAME KEYBOARD", ButtonStyle.SECONDARY);
        JButton pve = Components.button("☢  PvE  —  vs AI", ButtonStyle.PRIMARY_BLUE);
        JButton back = Components.button("◂  BACK", ButtonStyle.SECONDARY);

        for (JButton b : new JButton[]{pvp, pve, back}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(340, 50));
            b.setPreferredSize(new Dimension(340, 50));
        }

        pve.addActionListener(e -> showMabPveConfigCard());
        pvp.addActionListener(e -> showMabLocalPvpConfigCard());
        back.addActionListener(e -> showMenuCard());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(Theme.SPACE_XXL, Theme.SPACE_XL,
                                       Theme.SPACE_XXL, Theme.SPACE_XL));
        body.add(Components.vSpacer(Theme.SPACE_XL));
        body.add(pve);
        body.add(Components.vSpacer(Theme.SPACE_M));
        body.add(pvp);
        body.add(Components.vSpacer(Theme.SPACE_XL));
        body.add(back);

        JPanel mabCard = buildEmbeddedCard("Mutually Assured Blocks", body);
        mabCard.setName(CARD_MAB_SELECT);
        cardHost.add(mabCard, CARD_MAB_SELECT);

        currentCard = CARD_MAB_SELECT;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_MAB_SELECT);
        SwingUtilities.invokeLater(() -> focusButton(pve));
        installButtonNavigation(body, pve, pvp, back);
    }

    /** Shows the inline MAB PvE configuration card (difficulty, archetype, etc.). */
    private void showMabPveConfigCard() {
        for (Component c : cardHost.getComponents()) {
            if (CARD_MAB_PVE_CONFIG.equals(c.getName())) cardHost.remove(c);
        }

        com.tetris.mab.ai.MabAiArchetype[] archetypes =
                com.tetris.mab.ai.MabAiArchetype.values();
        com.tetris.mab.ai.MabAiDifficulty[] difficulties =
                com.tetris.mab.ai.MabAiDifficulty.values();
        java.util.List<com.tetris.mab.balance.MabBalanceProfile> profiles =
                com.tetris.mab.balance.MabBalanceProfiles.all();

        com.tetris.mab.ui.MabPveConfig seed =
                lastMabPveConfig != null ? lastMabPveConfig : com.tetris.mab.ui.MabPveConfig.defaults();

        int archIdx = 0, diffIdx = 0, profIdx = 0;
        for (int i = 0; i < archetypes.length; i++)
            if (archetypes[i] == seed.getAiArchetype()) { archIdx = i; break; }
        for (int i = 0; i < difficulties.length; i++)
            if (difficulties[i] == seed.getAiDifficulty()) { diffIdx = i; break; }
        for (int i = 0; i < profiles.size(); i++)
            if (profiles.get(i).getId().equals(seed.getBalanceProfileId())) { profIdx = i; break; }

        java.util.List<Integer> levels = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) levels.add(i);
        int levelIdx = Math.max(0, Math.min(seed.getStartLevel() - 1, 19));

        CycleSelector<com.tetris.mab.ai.MabAiArchetype> archSel =
                new CycleSelector<>(java.util.Arrays.asList(archetypes), archIdx, v -> v.toString());
        CycleSelector<com.tetris.mab.ai.MabAiDifficulty> diffSel =
                new CycleSelector<>(java.util.Arrays.asList(difficulties), diffIdx, v -> v.toString());
        CycleSelector<Integer> levelSel =
                new CycleSelector<>(levels, levelIdx, i -> "Level " + i);
        CycleSelector<com.tetris.mab.balance.MabBalanceProfile> profSel =
                new CycleSelector<>(profiles, profIdx,
                        com.tetris.mab.balance.MabBalanceProfile::getDisplayName);

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets = new java.awt.Insets(8, 8, 8, 8);
        gc.anchor = java.awt.GridBagConstraints.WEST;
        gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        int row = 0;
        addFormRow(form, gc, row++, "AI Archetype",    archSel);
        addFormRow(form, gc, row++, "AI Difficulty",   diffSel);
        addFormRow(form, gc, row++, "Start Level",     levelSel);
        addFormRow(form, gc, row++, "Balance Profile", profSel);
        addFormRow(form, gc, row,   "Nuke Design",
                setupSummaryLabel(currentMabNukeSelection.getSummary()));

        JButton start = Components.button("▶  START PvE", ButtonStyle.PRIMARY_BLUE);
        JButton back  = Components.button("◂  BACK",       ButtonStyle.SECONDARY);
        JButton nukeBuilder = Components.button("OPEN NUKE BUILDER", ButtonStyle.SECONDARY);
        JButton nukeDefault = Components.button("RESET NUKE DEFAULT", ButtonStyle.SECONDARY);
        for (JButton b : new JButton[]{start, nukeBuilder, nukeDefault, back}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(340, 50));
            b.setPreferredSize(new Dimension(340, 50));
        }

        start.addActionListener(e -> {
            com.tetris.mab.balance.MabBalanceProfile p = profSel.current();
            com.tetris.mab.ui.MabPveConfig cfg = com.tetris.mab.ui.MabPveConfig.fromSelections(
                    levelSel.current(),
                    archSel.current(),
                    diffSel.current(),
                    false,
                    p.getId(),
                    currentMabNukeSelection);
            lastMabPveConfig = cfg;
            launchMabPveWithConfig(cfg);
        });
        nukeBuilder.addActionListener(e -> showMabNukeBuilderCard(this::showMabPveConfigCard));
        nukeDefault.addActionListener(e -> {
            currentMabNukeSelection = MabNukeDesignSelection.defaultSelection();
            showMabPveConfigCard();
        });
        back.addActionListener(e -> showMabSelectCard());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(Theme.SPACE_XL, Theme.SPACE_XL,
                                       Theme.SPACE_XL, Theme.SPACE_XL));
        body.add(form);
        body.add(Components.vSpacer(Theme.SPACE_XL));
        body.add(nukeBuilder);
        body.add(Components.vSpacer(Theme.SPACE_S));
        body.add(nukeDefault);
        body.add(Components.vSpacer(Theme.SPACE_M));
        body.add(start);
        body.add(Components.vSpacer(Theme.SPACE_M));
        body.add(back);

        JPanel pveCard = buildEmbeddedCard("MAB PvE Setup", body);
        pveCard.setName(CARD_MAB_PVE_CONFIG);
        cardHost.add(pveCard, CARD_MAB_PVE_CONFIG);

        currentCard = CARD_MAB_PVE_CONFIG;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_MAB_PVE_CONFIG);
        installMixedNavigation(body, archSel, diffSel, levelSel, profSel,
                nukeBuilder, nukeDefault, start, back);
        SwingUtilities.invokeLater(archSel::requestFocusInWindow);
    }

    private void showMabLocalPvpConfigCard() {
        for (Component c : cardHost.getComponents()) {
            if (CARD_MAB_PVP_CONFIG.equals(c.getName())) cardHost.remove(c);
        }

        java.util.List<Integer> levels = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) levels.add(i);
        java.util.List<com.tetris.mab.balance.MabBalanceProfile> profiles =
                com.tetris.mab.balance.MabBalanceProfiles.all();
        MabLocalPvpConfig seed = lastMabLocalPvpConfig == null
                ? MabLocalPvpConfig.defaults()
                : lastMabLocalPvpConfig;
        int levelIdx = Math.max(0, Math.min(seed.getStartLevel() - 1, 19));
        int profIdx = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(seed.getBalanceProfileId())) {
                profIdx = i;
                break;
            }
        }

        CycleSelector<Integer> levelSel =
                new CycleSelector<>(levels, levelIdx, i -> "Level " + i);
        CycleSelector<com.tetris.mab.balance.MabBalanceProfile> profSel =
                new CycleSelector<>(profiles, profIdx,
                        com.tetris.mab.balance.MabBalanceProfile::getDisplayName);

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets = new java.awt.Insets(8, 8, 8, 8);
        gc.anchor = java.awt.GridBagConstraints.WEST;
        gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        int row = 0;
        addFormRow(form, gc, row++, "Start Level", levelSel);
        addFormRow(form, gc, row, "Balance Profile", profSel);

        JButton start = Components.button("START LOCAL PvP", ButtonStyle.PRIMARY_BLUE);
        JButton back = Components.button("BACK", ButtonStyle.SECONDARY);
        for (JButton b : new JButton[]{start, back}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(340, 50));
            b.setPreferredSize(new Dimension(340, 50));
        }

        start.addActionListener(e -> {
            com.tetris.mab.balance.MabBalanceProfile p = profSel.current();
            Window owner = SwingUtilities.getWindowAncestor(this);
            MabNukeDesignSelection p1Design = showNukeBuilderModal("PLAYER 1 — DESIGN YOUR NUKE", owner);
            MabNukeDesignSelection p2Design = showNukeBuilderModal("PLAYER 2 — DESIGN YOUR NUKE", owner);
            MabLocalPvpConfig cfg = new MabLocalPvpConfig(
                    levelSel.current(),
                    "PLAYER 1",
                    "PLAYER 2",
                    p.getId(),
                    p1Design,
                    p2Design);
            lastMabLocalPvpConfig = cfg;
            launchMabLocalPvpWithConfig(cfg);
        });
        back.addActionListener(e -> showMabSelectCard());

        JLabel mode = setupSummaryLabel("LOCAL PvP - SAME KEYBOARD - OFFLINE DUEL");
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(Theme.SPACE_XL, Theme.SPACE_XL,
                                       Theme.SPACE_XL, Theme.SPACE_XL));
        body.add(mode);
        body.add(Components.vSpacer(Theme.SPACE_M));
        body.add(form);
        body.add(Components.vSpacer(Theme.SPACE_XL));
        body.add(start);
        body.add(Components.vSpacer(Theme.SPACE_M));
        body.add(back);

        JPanel pvpCard = buildEmbeddedCard("MAB Local PvP Setup", body,
                this::showMabSelectCard);
        pvpCard.setName(CARD_MAB_PVP_CONFIG);
        cardHost.add(pvpCard, CARD_MAB_PVP_CONFIG);

        currentCard = CARD_MAB_PVP_CONFIG;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_MAB_PVP_CONFIG);
        installMixedNavigation(body, levelSel, profSel, start, back);
        SwingUtilities.invokeLater(levelSel::requestFocusInWindow);
    }

    /** Helper: adds one label+field row to the PvE config form grid. */
    private static void addFormRow(JPanel form, java.awt.GridBagConstraints gc,
                                   int row, String label, JComponent field) {
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(Theme.TEXT_BODY);
        lbl.setFont(Theme.FONT_BODY);
        form.add(lbl, gc);
        gc.gridx = 1; gc.weightx = 1;
        form.add(field, gc);
    }

    /** Shows a blocking modal nuke builder dialog and returns the chosen design. */
    private MabNukeDesignSelection showNukeBuilderModal(String title, Window owner) {
        JDialog dialog = new JDialog(owner, title,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        NukeBuilderDialog builder = NukeBuilderDialog.createEmbedded(null);
        MabNukeDesignSelection[] result = {MabNukeDesignSelection.defaultSelection()};

        JButton confirm = Components.button("USE DESIGN", ButtonStyle.PRIMARY_BLUE);
        JButton useDefault = Components.button("USE DEFAULT", ButtonStyle.SECONDARY);
        confirm.addActionListener(e -> {
            result[0] = MabNukeDesignSelection.fromBuilderDesign(builder.getDesignForIntegration());
            dialog.dispose();
        });
        useDefault.addActionListener(e -> {
            result[0] = MabNukeDesignSelection.defaultSelection();
            dialog.dispose();
        });

        JPanel btnRow = new JPanel();
        btnRow.setBackground(Theme.BG_1);
        btnRow.setBorder(new EmptyBorder(Theme.SPACE_S, Theme.SPACE_M, Theme.SPACE_S, Theme.SPACE_M));
        btnRow.add(confirm);
        btnRow.add(javax.swing.Box.createHorizontalStrut(Theme.SPACE_M));
        btnRow.add(useDefault);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setFont(Theme.FONT_H1);
        titleLabel.setBorder(new EmptyBorder(Theme.SPACE_S, Theme.SPACE_M, Theme.SPACE_S, Theme.SPACE_M));

        JPanel content = new JPanel(new java.awt.BorderLayout());
        content.setBackground(Theme.BG_0);
        content.add(titleLabel, java.awt.BorderLayout.NORTH);
        content.add(builder, java.awt.BorderLayout.CENTER);
        content.add(btnRow, java.awt.BorderLayout.SOUTH);

        // Hard-drop key (confirm) closes and accepts the current design.
        int hardDropCode = Settings.get().getKeyHardDrop();
        javax.swing.KeyStroke hardDropKs = javax.swing.KeyStroke.getKeyStroke(hardDropCode, 0);
        dialog.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(hardDropKs, "nukeConfirm");
        dialog.getRootPane().getActionMap().put("nukeConfirm",
                new javax.swing.AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        confirm.doClick();
                    }
                });

        dialog.setContentPane(content);
        dialog.setSize(1200, 820);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    private static JLabel setupSummaryLabel(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setForeground(Theme.TEXT_BODY);
        label.setFont(Theme.FONT_BODY);
        return label;
    }

    private static JTextField setupTextField(String text) {
        JTextField field = new JTextField(text == null ? "" : text);
        field.setFont(Theme.FONT_BODY);
        field.setForeground(Theme.TEXT_BODY);
        field.setCaretColor(Theme.ACCENT);
        field.setBackground(Theme.BG_1);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_DIM, 1),
                new EmptyBorder(6, 8, 6, 8)));
        return field;
    }

    /** Step 15 — launches a fresh controller in the requested mode. */
    private void showGameCard(GameLaunchMode mode) {
        GameController ctrl;
        if (mode == GameLaunchMode.MAB_PVE && modedFactory != null) {
            ctrl = modedFactory.apply(startLevel, GameLaunchMode.MAB_PVE);
        } else if (modedFactory != null) {
            ctrl = modedFactory.apply(startLevel, GameLaunchMode.NORMAL_TETRIS);
        } else if (controllerFactory != null) {
            ctrl = controllerFactory.apply(startLevel);
        } else {
            return;
        }
        mountController(ctrl);
    }

    /** Step 18 — wires the optional MAB PvE config-aware factory. */
    public void setMabPveFactory(java.util.function.Function<MabPveConfig, GameController> factory) {
        this.mabPveFactory = factory;
    }

    /** Step 26 - wires the configured same-keyboard local PvP factory. */
    public void setMabLocalPvpFactory(
            java.util.function.Function<MabLocalPvpConfig, GameController> factory) {
        this.mabLocalPvpFactory = factory;
    }

    /** Step 18 — opens the PvE setup dialog and, on confirm, launches MAB PvE
     *  with the chosen {@link MabPveConfig}. Falls back to the legacy flow when
     *  no MAB PvE factory has been wired. */
    private void openMabPveSetup() {
        if (mabPveFactory == null) {
            // No config-aware factory wired — fall back to existing path.
            showGameCard(GameLaunchMode.MAB_PVE);
            return;
        }
        MabPveSetupDialog dialog = new MabPveSetupDialog(this, lastMabPveConfig, cfg -> {
            lastMabPveConfig = cfg;
            launchMabPveWithConfig(cfg);
        });
        dialog.setVisible(true);
    }

    /** Step 18 — builds a controller for the supplied PvE config and mounts it,
     *  wiring restart/back-to-menu callbacks. */
    private void launchMabPveWithConfig(MabPveConfig cfg) {
        if (cfg == null || mabPveFactory == null) return;
        GameController ctrl = mabPveFactory.apply(cfg);
        if (ctrl == null) return;
        Runnable restart = () -> {
            try { ctrl.stop(); } catch (RuntimeException ignored) {}
            launchMabPveWithConfig(cfg);
        };
        Runnable back = () -> {
            try { ctrl.stop(); } catch (RuntimeException ignored) {}
            showMenuCard();
        };
        ctrl.setMabPveCallbacks(restart, back);
        mountController(ctrl);
    }

    private void launchMabLocalPvpWithConfig(MabLocalPvpConfig cfg) {
        if (cfg == null || mabLocalPvpFactory == null) return;
        GameController ctrl = mabLocalPvpFactory.apply(cfg);
        if (ctrl == null) return;
        Runnable restart = () -> {
            try { ctrl.stop(); } catch (RuntimeException ignored) {}
            launchMabLocalPvpWithConfig(cfg);
        };
        Runnable back = () -> {
            try { ctrl.stop(); } catch (RuntimeException ignored) {}
            showMenuCard();
        };
        ctrl.setMabPveCallbacks(restart, back);
        mountController(ctrl);
    }

    /** Step 18 — extracted shared mount logic. */
    private void mountController(GameController ctrl) {
        activeController = ctrl;
        // Always rebuild a fresh controller + view so each match starts clean.
        for (Component c : cardHost.getComponents()) {
            if (CARD_GAME.equals(c.getName())) cardHost.remove(c);
        }
        // Step 20: GameController.startEmbedded now returns a JComponent so
        // MAB PvE can substitute a wrapper containing the player board, the
        // visible opponent board, and the embedded MAB HUD. NORMAL_TETRIS
        // still receives a plain GameView.
        JComponent view = ctrl.startEmbedded(this::showMenuCard);
        view.setName(CARD_GAME);
        cardHost.add(view, CARD_GAME);

        currentCard = CARD_GAME;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_GAME);
        SwingUtilities.invokeLater(ctrl::requestGameFocus);
    }

    // ─────────────────────── Cards ──────────────────────────────

    /** Wraps an embedded view in an opaque card with a Back button bar.
     *  Optional {@code extras} are placed on the right side of the bar
     *  (use the same secondary button style as Back to match). */
    private JPanel buildEmbeddedCard(String title, JComponent body, JButton... extras) {
        return buildEmbeddedCard(title, body, this::showMenuCard, extras);
    }

    private JPanel buildEmbeddedCard(String title, JComponent body,
                                     Runnable onBack,
                                     JButton... extras) {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(Theme.BG_0);
        card.setOpaque(true);

        // Top bar: Back ◂  Title  [extras]
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_1);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER),
                new EmptyBorder(Theme.SPACE_S, Theme.SPACE_M, Theme.SPACE_S, Theme.SPACE_M)));

        JButton back = Components.button("\u25C2  BACK", ButtonStyle.SECONDARY);
        back.addActionListener(e -> {
            if (onBack != null) onBack.run();
            else showMenuCard();
        });
        back.setVisible(onBack != null);
        bar.add(back, BorderLayout.WEST);

        JLabel titleLbl = Components.label(title.toUpperCase(),
                Theme.FONT_MONO_BOLD, Theme.ACCENT);
        titleLbl.setHorizontalAlignment(SwingConstants.CENTER);
        bar.add(titleLbl, BorderLayout.CENTER);

        // Right side: stack of extra action buttons, or a spacer that
        // matches the Back button's width so the title centres nicely.
        JComponent right;
        if (extras != null && extras.length > 0) {
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_S, 0));
            rightPanel.setOpaque(false);
            for (JButton b : extras) rightPanel.add(b);
            right = rightPanel;
        } else {
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            spacer.setPreferredSize(back.getPreferredSize());
            right = spacer;
        }
        bar.add(right, BorderLayout.EAST);
        installButtonNavigation(bar, navButtons(back, extras));

        card.add(bar, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMenuCard(Runnable onPlayTetris) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        root.setFocusable(true);
        root.setBorder(new EmptyBorder(Theme.SPACE_XXL, Theme.SPACE_XXL,
                                       Theme.SPACE_XXL, Theme.SPACE_XXL));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(Theme.alpha(Theme.BG_1, 230));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_DIM, 1, true),
                new EmptyBorder(Theme.SPACE_XXL, Theme.SPACE_XL,
                                Theme.SPACE_XXL, Theme.SPACE_XL)));

        JLabel eyebrow = new JLabel("LHC // CONTROL ROOM");
        eyebrow.setFont(Theme.FONT_MONO_BOLD);
        eyebrow.setForeground(Theme.ACCENT_DIM);
        eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("MODERN TETRIS");
        title.setFont(Theme.FONT_DISPLAY);
        title.setForeground(Theme.ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("A guideline-faithful Tetris client");
        sub.setFont(Theme.FONT_BODY);
        sub.setForeground(Theme.TEXT_BODY);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JComponent rule = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.ACCENT, 0),
                                              w / 2f, 0, Theme.ACCENT, true));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(0, h / 2, w, h / 2);
            }
        };
        rule.setPreferredSize(new Dimension(280, 8));
        rule.setMaximumSize(new Dimension(280, 8));
        rule.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(eyebrow);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(title);
        card.add(Components.vSpacer(Theme.SPACE_XS));
        card.add(rule);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(sub);
        card.add(Components.vSpacer(Theme.SPACE_XL));

        JButton mabPve   = Components.button("\u2622  START MAB",      ButtonStyle.PRIMARY_BLUE);
        JButton play     = Components.button("\u25B6  SINGLE PLAYER",  ButtonStyle.SECONDARY);
        JButton nuke     = Components.button("\u2622  NUKE BUILDER",   ButtonStyle.SECONDARY);
        JButton controls = Components.button("CONTROLS",              ButtonStyle.SECONDARY);
        JButton settings = Components.button("\u2699  SETTINGS",       ButtonStyle.SECONDARY);
        JButton quit     = Components.button("\u2715  QUIT",           ButtonStyle.TEXT);

        mabPve.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (JButton b : new JButton[]{mabPve, play, nuke, controls, settings, quit}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(360, 50));
            b.setPreferredSize(new Dimension(360, 50));
        }

        mabPve.addActionListener(e -> showMabSelectCard());
        play.addActionListener(e -> {
            if (controllerFactory != null || modedFactory != null) {
                showGameCard(GameLaunchMode.NORMAL_TETRIS);
            } else {
                animTimer.stop();
                SwingUtilities.invokeLater(onPlayTetris);
            }
        });
        nuke.addActionListener(e -> showNukeCard());
        controls.addActionListener(e -> showControlsWizardCard(false));
        settings.addActionListener(e -> showSettingsCard());
        quit.addActionListener(e -> confirmQuit());

        card.add(mabPve);
        card.add(Components.vSpacer(Theme.SPACE_M));
        card.add(play);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(nuke);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(controls);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(settings);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(quit);

        card.add(Components.vSpacer(Theme.SPACE_XL));
        JLabel footer = new JLabel("v1.0  \u2022  ARROWS SELECT  \u2022  ENTER CONFIRMS  \u2022  ESC BACK / QUIT");
        footer.setFont(Theme.FONT_CAPTION);
        footer.setForeground(Theme.TEXT_FAINT);
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(footer);

        firstMenuButton = mabPve;
        installButtonNavigation(root, mabPve, play, nuke, controls, settings, quit);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.weightx = 1; gc.weighty = 1;
        root.add(card, gc);
        return root;
    }

    private JButton[] navButtons(JButton first, JButton... rest) {
        int extra = rest == null ? 0 : rest.length;
        JButton[] out = new JButton[1 + extra];
        out[0] = first;
        if (extra > 0) System.arraycopy(rest, 0, out, 1, extra);
        return out;
    }

    private void installButtonNavigation(JComponent scope, JButton... buttons) {
        if (scope == null || buttons == null || buttons.length == 0) return;
        for (JButton button : buttons) {
            if (button == null) continue;
            button.setFocusable(true);
            InputMap bim = button.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap bam = button.getActionMap();
            bim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "menuPressEnter");
            bam.put("menuPressEnter", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    button.doClick();
                }
            });
        }

        InputMap im = scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = scope.getActionMap();
        bindButtonNavigation(im, am, "menuPrevUp", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), buttons, -1);
        bindButtonNavigation(im, am, "menuPrevLeft", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), buttons, -1);
        bindButtonNavigation(im, am, "menuNextDown", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), buttons, +1);
        bindButtonNavigation(im, am, "menuNextRight", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), buttons, +1);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "menuConfirm");
        am.put("menuConfirm", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                JButton focused = focusedButton(buttons);
                if (focused != null) focused.doClick();
            }
        });
    }

    private void bindButtonNavigation(InputMap im, ActionMap am, String name,
                                      KeyStroke key, JButton[] buttons, int delta) {
        im.put(key, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                moveButtonFocus(buttons, delta);
            }
        });
    }

    private void moveButtonFocus(JButton[] buttons, int delta) {
        int idx = focusedButtonIndex(buttons);
        if (idx < 0) {
            focusButton(firstUsableButton(buttons));
            return;
        }
        int n = buttons.length;
        for (int step = 1; step <= n; step++) {
            int next = ((idx + delta * step) % n + n) % n;
            JButton candidate = buttons[next];
            if (isUsableButton(candidate)) {
                focusButton(candidate);
                return;
            }
        }
    }

    private int focusedButtonIndex(JButton[] buttons) {
        JButton focused = focusedButton(buttons);
        if (focused == null) return -1;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == focused) return i;
        }
        return -1;
    }

    private JButton focusedButton(JButton[] buttons) {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus == null) return null;
        for (JButton button : buttons) {
            if (button == null) continue;
            if (focus == button || SwingUtilities.isDescendingFrom(focus, button)) return button;
        }
        return null;
    }

    private JButton firstUsableButton(JButton[] buttons) {
        for (JButton button : buttons) {
            if (isUsableButton(button)) return button;
        }
        return null;
    }

    private boolean isUsableButton(JButton button) {
        return button != null && button.isEnabled() && button.isVisible() && button.isShowing();
    }

    private void focusButton(JButton button) {
        if (button != null && button.isEnabled()) {
            button.requestFocusInWindow();
        }
    }

    private void confirmQuit() {
        // No prompt — quit immediately.
        System.exit(0);
    }

    // ─────────────────────── Mixed navigation (selectors + buttons) ──────

    private void installMixedNavigation(JComponent scope, JComponent... items) {
        InputMap im = scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = scope.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,   0), "mixedPrev");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0), "mixedNext");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "mixedConfirm");
        am.put("mixedPrev", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                moveMixedFocus(items, -1);
            }
        });
        am.put("mixedNext", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                moveMixedFocus(items, +1);
            }
        });
        am.put("mixedConfirm", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                for (JComponent item : items) {
                    if (item instanceof JButton btn &&
                            (focus == btn || SwingUtilities.isDescendingFrom(focus, btn))) {
                        btn.doClick();
                        return;
                    }
                }
            }
        });
        for (JComponent item : items) {
            item.setFocusable(true);
            if (item instanceof JButton btn) {
                InputMap bim = btn.getInputMap(JComponent.WHEN_FOCUSED);
                ActionMap bam = btn.getActionMap();
                bim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "btnConfirm");
                bam.put("btnConfirm", new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) { btn.doClick(); }
                });
            }
        }
    }

    private void moveMixedFocus(JComponent[] items, int delta) {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        int current = -1;
        for (int i = 0; i < items.length; i++) {
            JComponent item = items[i];
            if (focus == item || (focus != null && SwingUtilities.isDescendingFrom(focus, item))) {
                current = i;
                break;
            }
        }
        int n = items.length;
        if (current < 0) { items[0].requestFocusInWindow(); return; }
        for (int step = 1; step <= n; step++) {
            int next = ((current + delta * step) % n + n) % n;
            JComponent candidate = items[next];
            if (candidate != null && candidate.isEnabled() && candidate.isVisible()) {
                candidate.requestFocusInWindow();
                return;
            }
        }
    }

    // ════════════════════ Cycle selector ════════════════════════

    private static final class CycleSelector<T> extends JPanel {
        private final java.util.List<T> options;
        private int index;
        private final JLabel valueLabel;
        private final java.util.function.Function<T, String> display;

        CycleSelector(java.util.List<T> options, int initialIndex,
                      java.util.function.Function<T, String> display) {
            this.options = new java.util.ArrayList<>(options);
            this.index   = Math.max(0, Math.min(initialIndex, options.size() - 1));
            this.display = display;

            setOpaque(false);
            setFocusable(true);
            setLayout(new BorderLayout(Theme.SPACE_S, 0));

            JLabel prev = arrow("◄");
            JLabel next = arrow("►");

            valueLabel = new JLabel(labelText(), SwingConstants.CENTER);
            valueLabel.setFont(Theme.FONT_BODY);
            valueLabel.setForeground(Theme.TEXT_BODY);

            add(prev, BorderLayout.WEST);
            add(valueLabel, BorderLayout.CENTER);
            add(next, BorderLayout.EAST);

            prev.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    cycle(-1); requestFocusInWindow();
                }
            });
            next.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    cycle(+1); requestFocusInWindow();
                }
            });

            InputMap im = getInputMap(WHEN_FOCUSED);
            ActionMap am = getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "prev");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
            am.put("prev", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { cycle(-1); }
            });
            am.put("next", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { cycle(+1); }
            });

            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    valueLabel.setForeground(Theme.ACCENT);
                    repaint();
                }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    valueLabel.setForeground(Theme.TEXT_BODY);
                    repaint();
                }
            });
        }

        void cycle(int delta) {
            int n = options.size();
            if (n == 0) return;
            index = ((index + delta) % n + n) % n;
            valueLabel.setText(labelText());
        }

        T current() { return options.get(index); }

        private String labelText() {
            return options.isEmpty() ? "" : display.apply(options.get(index));
        }

        private static JLabel arrow(String text) {
            JLabel l = new JLabel(text, SwingConstants.CENTER);
            l.setFont(Theme.FONT_BODY);
            l.setForeground(Theme.ACCENT_DIM);
            l.setPreferredSize(new Dimension(28, 28));
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return l;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(Theme.ACCENT, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Theme.alpha(Theme.ACCENT, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                g2.dispose();
            }
        }
    }

    // ════════════════════ Marquee panel ═════════════════════════

    private static final class MarqueePanel extends JPanel {
        private static final int CELL = 28;
        private final List<FallingPiece> pieces = new ArrayList<>();
        private final Random rng = new Random();

        MarqueePanel() {
            setOpaque(true);
            setBackground(Theme.BG_0);
        }

        void tick() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (rng.nextInt(8) == 0 && pieces.size() < 18) {
                pieces.add(new FallingPiece(rng, w, CELL));
            }
            for (FallingPiece p : pieces) p.y += p.speed;
            pieces.removeIf(p -> p.y > h + CELL * 4);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            g2.setPaint(new RadialGradientPaint(
                    w / 2f, h / 2f, Math.max(w, h) / 1.2f,
                    new float[]{0f, 1f},
                    new Color[]{Theme.BG_1, Theme.BG_0}));
            g2.fillRect(0, 0, w, h);

            g2.setColor(Theme.alpha(Theme.ACCENT_DIM, 22));
            for (int x = 0; x < w; x += CELL) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += CELL) g2.drawLine(0, y, w, y);

            for (FallingPiece p : pieces) {
                Position[] cells = p.type.getCells(p.rotation);
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                for (Position c : cells) {
                    minX = Math.min(minX, c.getX());
                    minY = Math.min(minY, c.getY());
                }
                Color col = p.type.getColor();
                AlphaComposite prev = (AlphaComposite) g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.30f));
                for (Position c : cells) {
                    int x = p.x + (c.getX() - minX) * CELL;
                    int y = (int) p.y + (c.getY() - minY) * CELL;
                    BlockRenderer.draw(g2, x, y, CELL, col, BlockRenderer.Style.SOLID);
                }
                g2.setComposite(prev);
            }
        }

        private static final class FallingPiece {
            final TetrominoType type;
            final int rotation;
            final int x;
            double y;
            final double speed;
            FallingPiece(Random rng, int width, int cell) {
                TetrominoType[] vals = TetrominoType.values();
                this.type = vals[rng.nextInt(vals.length)];
                this.rotation = rng.nextInt(4);
                this.x = rng.nextInt(Math.max(1, width - cell * 4));
                this.y = -cell * 4;
                this.speed = 1.2 + rng.nextDouble() * 1.8;
            }
        }
    }
}
