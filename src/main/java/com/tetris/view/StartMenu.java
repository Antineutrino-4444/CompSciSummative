package com.tetris.view;

import com.tetris.controller.GameController;
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

    private static final String CARD_MENU     = "menu";
    private static final String CARD_SETTINGS = "settings";
    private static final String CARD_NUKE     = "nuke";
    private static final String CARD_GAME     = "game";

    private final CardLayout cards;
    private final JPanel cardHost;
    private final MarqueePanel marquee;
    private final Timer animTimer;
    private final java.util.function.IntFunction<GameController> controllerFactory;
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
        super("Modern Tetris");
        this.startLevel = Math.max(1, startLevel);
        this.controllerFactory = controllerFactory;
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
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) { animTimer.start(); }
            @Override public void windowLostFocus  (java.awt.event.WindowEvent e) { animTimer.stop();  }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getRootPane().getActionMap().put("esc", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                // ESC: back to menu if not already there, otherwise quit prompt.
                if (currentCard != null && !CARD_MENU.equals(currentCard)) {
                    showMenuCard();
                } else {
                    confirmQuit();
                }
            }
        });
    }

    /** Tracks the currently-displayed card so ESC behaves correctly. */
    private String currentCard = CARD_MENU;

    // ─────────────────────── Card switching ──────────────────────

    private void showMenuCard() {
        currentCard = CARD_MENU;
        marquee.setVisible(true);
        animTimer.start();
        cards.show(cardHost, CARD_MENU);
    }

    private void showSettingsCard() {
        // Always rebuild fresh so changes from previous sessions are
        // re-loaded from disk.
        for (Component c : cardHost.getComponents()) {
            if (CARD_SETTINGS.equals(c.getName())) cardHost.remove(c);
        }
        JPanel settingsCard = buildEmbeddedCard(
                "Settings",
                SettingsPanel.createEmbedded(this::showMenuCard));
        settingsCard.setName(CARD_SETTINGS);
        cardHost.add(settingsCard, CARD_SETTINGS);

        currentCard = CARD_SETTINGS;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_SETTINGS);
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
    }

    private void showGameCard() {
        if (controllerFactory == null) return;
        // Always rebuild a fresh controller + view so each match starts clean.
        for (Component c : cardHost.getComponents()) {
            if (CARD_GAME.equals(c.getName())) cardHost.remove(c);
        }
        GameController ctrl = controllerFactory.apply(startLevel);
        GameView view = ctrl.startEmbedded(this::showMenuCard);
        view.setName(CARD_GAME);
        cardHost.add(view, CARD_GAME);

        currentCard = CARD_GAME;
        marquee.setVisible(false);
        animTimer.stop();
        cards.show(cardHost, CARD_GAME);
        SwingUtilities.invokeLater(view::requestGameFocus);
    }

    // ─────────────────────── Cards ──────────────────────────────

    /** Wraps an embedded view in an opaque card with a Back button bar.
     *  Optional {@code extras} are placed on the right side of the bar
     *  (use the same secondary button style as Back to match). */
    private JPanel buildEmbeddedCard(String title, JComponent body, JButton... extras) {
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
        back.addActionListener(e -> showMenuCard());
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

        card.add(bar, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMenuCard(Runnable onPlayTetris) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
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

        JButton play     = Components.button("\u25B6  PLAY TETRIS",   ButtonStyle.PRIMARY);
        JButton nuke     = Components.button("\u2622  NUKE BUILDER",  ButtonStyle.SECONDARY);
        JButton settings = Components.button("\u2699  SETTINGS",      ButtonStyle.SECONDARY);
        JButton quit     = Components.button("\u2715  QUIT",          ButtonStyle.TEXT);

        play.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (JButton b : new JButton[]{play, nuke, settings, quit}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(360, 50));
            b.setPreferredSize(new Dimension(360, 50));
        }

        play.addActionListener(e -> {
            if (controllerFactory != null) {
                showGameCard();
            } else {
                animTimer.stop();
                SwingUtilities.invokeLater(onPlayTetris);
            }
        });
        nuke.addActionListener(e -> showNukeCard());
        settings.addActionListener(e -> showSettingsCard());
        quit.addActionListener(e -> confirmQuit());

        card.add(play);
        card.add(Components.vSpacer(Theme.SPACE_M));
        card.add(nuke);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(settings);
        card.add(Components.vSpacer(Theme.SPACE_S));
        card.add(quit);

        card.add(Components.vSpacer(Theme.SPACE_XL));
        JLabel footer = new JLabel("v1.0  \u2022  ESC for back / quit");
        footer.setFont(Theme.FONT_CAPTION);
        footer.setForeground(Theme.TEXT_FAINT);
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(footer);

        getRootPane().setDefaultButton(play);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.weightx = 1; gc.weighty = 1;
        root.add(card, gc);
        return root;
    }

    private void confirmQuit() {
        // No prompt — quit immediately.
        System.exit(0);
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
