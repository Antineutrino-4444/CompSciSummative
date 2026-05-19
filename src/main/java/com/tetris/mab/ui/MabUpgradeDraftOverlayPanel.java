package com.tetris.mab.ui;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeCategory;
import com.tetris.mab.upgrade.draft.MabUpgradeDraft;
import com.tetris.mab.upgrade.draft.MabUpgradeInventory;
import com.tetris.model.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Step 24 Refinement — DOCTRINE LOADOUT overlay shown when a participant
 * levels up in MAB. Presents three choice cards horizontally; selecting
 * one fires the supplied callback.
 *
 * <p>Visual redesign: cold-war doctrine-terminal aesthetic matching
 * MabBattleShellPanel. Category is shown as a clear word (CHARGE,
 * DEFENSE, …) with a short badge (CHG, DEF, …). Rarity is color-coded.
 * Descriptions use HTML-wrapped JLabel at TERM_MED (13 pt) to prevent
 * clipping. No abstract icon glyphs.
 *
 * <p>Input contract: overlay owns Left/Right/Enter while shown.
 * MabBattleShellPanel.showUpgradeOverlay / hideUpgradeOverlay both call
 * clearHeldKeys() before touching this panel.
 */
public final class MabUpgradeDraftOverlayPanel extends JPanel {

    // Width used for the HTML body wrap inside each description JLabel.
    // Must be narrower than card content area (300 - 14*2 padding - 2*2 border = 268).
    private static final int DESC_HTML_WIDTH = 240;

    private final JPanel cardRow;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JLabel queueLabel;
    private final List<JPanel> renderedCards = new ArrayList<>();
    private final List<MabUpgradeCard> renderedChoices = new ArrayList<>();
    private final List<JLabel> selectionIndicators = new ArrayList<>();
    private Consumer<MabUpgradeCard> pickCallback;
    private MabUpgradeDraft activeDraft;
    private int selectedIndex = 0;

    // KeyEventDispatcher installed while the overlay is showing so navigation
    // keys are guaranteed to reach us before focus-based routing (which can
    // miss the first event on a newly-visible panel).
    private final KeyEventDispatcher overlayDispatcher = this::handleKeyEvent;
    private boolean dispatcherInstalled = false;

    // Auto-repeat guard: tracks keys currently held down so repeated KEY_PRESSED
    // events (OS auto-repeat) don't cycle cards when the user just taps once.
    private final Set<Integer> navKeysHeld = new HashSet<>();

    public MabUpgradeDraftOverlayPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setFocusable(true);
        installKeyboardActions();

        // ── Centered modal panel ──
        JPanel modal = new JPanel(new BorderLayout(0, 14));
        modal.setOpaque(true);
        modal.setBackground(MabUiTheme.SHELL_BG);
        modal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.C_CYAN, 2),
                new EmptyBorder(22, 30, 18, 30)));
        modal.setFocusable(false);

        // ── Header ──
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        titleLabel = new JLabel("DOCTRINE LOADOUT");
        titleLabel.setFont(MabUiTheme.STENCIL_HEADLINE);
        titleLabel.setForeground(MabUiTheme.C_CYAN);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        subtitleLabel = new JLabel("LEVEL ?  -  SELECT ONE DOCTRINE  -  BOTH BOARDS PAUSED");
        subtitleLabel.setFont(MabUiTheme.STENCIL_SMALL);
        subtitleLabel.setForeground(MabUiTheme.TEXT_FAINT);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        queueLabel = new JLabel("");
        queueLabel.setFont(MabUiTheme.TERM_TINY);
        queueLabel.setForeground(MabUiTheme.TEXT_GHOST);
        queueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        queueLabel.setVisible(false);

        header.add(titleLabel);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitleLabel);
        header.add(Box.createVerticalStrut(3));
        header.add(queueLabel);
        modal.add(header, BorderLayout.NORTH);

        // ── Three-card row ──
        cardRow = new JPanel(new GridLayout(1, 3, 16, 0));
        cardRow.setOpaque(false);
        modal.add(cardRow, BorderLayout.CENTER);

        // ── Footer ──
        JLabel footer = new JLabel("ARROWS SELECT    HARD DROP / SPACE CONFIRM");
        footer.setFont(MabUiTheme.TERM_TINY);
        footer.setForeground(MabUiTheme.TEXT_GHOST);
        footer.setHorizontalAlignment(SwingConstants.CENTER);
        modal.add(footer, BorderLayout.SOUTH);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.weightx = 1.0; gc.weighty = 1.0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.CENTER;
        add(modal, gc);

        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Dark translucent veil over the battle shell.
        g.setColor(new Color(2, 6, 11, 215));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    public void show(MabUpgradeDraft draft, MabUpgradeInventory inv,
                     Consumer<MabUpgradeCard> onPick) {
        this.activeDraft = draft;
        this.pickCallback = onPick;
        boolean isP2 = draft.getParticipantId() == ParticipantId.PLAYER_B;
        titleLabel.setText(isP2 ? "PLAYER 2 DOCTRINE LOADOUT" : "PLAYER 1 DOCTRINE LOADOUT");
        bindPlayerKeys(isP2);
        subtitleLabel.setText((isP2 ? "PLAYER 2" : "PLAYER 1")
                + "  SELECTS ONE CARD  -  LV " + draft.getLevel()
                + "  -  BOTH BOARDS PAUSED");
        cardRow.removeAll();
        renderedCards.clear();
        renderedChoices.clear();
        selectionIndicators.clear();
        selectedIndex = 0;
        List<MabUpgradeCard> choices = draft.getChoices();
        for (int i = 0; i < choices.size(); i++) {
            MabUpgradeCard card = choices.get(i);
            JPanel panel = buildCard(card, inv, i);
            renderedCards.add(panel);
            renderedChoices.add(card);
            cardRow.add(panel);
        }
        updateSelectedCardStyles();
        setVisible(true);
        revalidate();
        repaint();
        if (!dispatcherInstalled) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(overlayDispatcher);
            dispatcherInstalled = true;
        }
        requestFocusInWindow();
    }

    public void dismiss() {
        if (dispatcherInstalled) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(overlayDispatcher);
            dispatcherInstalled = false;
        }
        navKeysHeld.clear();
        setVisible(false);
        cardRow.removeAll();
        renderedCards.clear();
        renderedChoices.clear();
        selectionIndicators.clear();
        selectedIndex = 0;
        activeDraft = null;
        pickCallback = null;
        queueLabel.setText("");
        queueLabel.setVisible(false);
    }

    public void setQueueInfo(String text) {
        if (text == null || text.isBlank()) {
            queueLabel.setVisible(false);
        } else {
            queueLabel.setText(text);
            queueLabel.setVisible(true);
        }
    }

    public boolean isShown() { return isVisible(); }
    public MabUpgradeDraft getActiveDraft() { return activeDraft; }

    // ── Card builder ─────────────────────────────────────────────────

    private JPanel buildCard(MabUpgradeCard card, MabUpgradeInventory inv, int index) {
        Color rarityCol  = rarityColor(card);
        Color catCol     = categoryColor(card.getCategory());
        Color cardNormal = MabUiTheme.CARD_BG;

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(cardNormal);
        p.setBorder(normalBorder(rarityCol));
        p.setPreferredSize(new Dimension(300, 340));
        p.setMinimumSize(new Dimension(270, 300));
        p.setFocusable(false);

        // ── Header strip: category word + rarity badge ──
        JPanel headerStrip = new JPanel(new BorderLayout(6, 0));
        headerStrip.setOpaque(false);
        headerStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerStrip.setMaximumSize(new Dimension(Short.MAX_VALUE, 20));

        JLabel catWord = new JLabel(categoryWord(card.getCategory()));
        catWord.setFont(MabUiTheme.STENCIL_SMALL);
        catWord.setForeground(catCol);
        headerStrip.add(catWord, BorderLayout.WEST);

        JLabel rarityBadge = new JLabel(card.getRarity().name());
        rarityBadge.setFont(MabUiTheme.TERM_TINY);
        rarityBadge.setForeground(rarityCol);
        rarityBadge.setHorizontalAlignment(SwingConstants.RIGHT);
        headerStrip.add(rarityBadge, BorderLayout.EAST);

        p.add(headerStrip);

        // ── Short category badge line ──
        JLabel badge = new JLabel(categoryBadge(card.getCategory()));
        badge.setFont(MabUiTheme.TERM_TINY);
        badge.setForeground(dimColor(catCol));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(badge);
        p.add(Box.createVerticalStrut(8));

        // ── Thin divider ──
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(MabUiTheme.GRID_LINE_HI);
        sep.setMaximumSize(new Dimension(Short.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sep);
        p.add(Box.createVerticalStrut(10));

        // ── Card title ──
        JLabel nameLabel = new JLabel(
                "<html><body style='width:" + DESC_HTML_WIDTH + "px'>"
                + escape(card.getDisplayName()) + "</body></html>");
        nameLabel.setFont(MabUiTheme.STENCIL_MID);
        nameLabel.setForeground(MabUiTheme.TEXT_BRIGHT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(nameLabel);
        p.add(Box.createVerticalStrut(10));

        // ── Description (wrapping, large, readable) ──
        // HTML body style='width:Npx' is the reliable Swing wrapping mechanism.
        // Font is applied via the component, not inline HTML (avoids size issues).
        JLabel desc = new JLabel(
                "<html><body style='width:" + DESC_HTML_WIDTH + "px'>"
                + escape(card.getOneLineDescription()) + "</body></html>");
        desc.setFont(MabUiTheme.TERM_MED);
        desc.setForeground(new Color(0xC4, 0xD4, 0xE8));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(desc);

        p.add(Box.createVerticalGlue());

        // ── Status / stack tag ──
        String statusText = buildStatusText(card, inv);
        Color  statusCol  = card.isRepeatable() ? MabUiTheme.C_AMBER : MabUiTheme.TEXT_GHOST;
        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(MabUiTheme.TERM_TINY);
        statusLabel.setForeground(statusCol);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(statusLabel);

        p.add(Box.createVerticalStrut(8));

        // ── Selection indicator (hidden until this card is selected) ──
        JLabel selInd = new JLabel(" ");
        selInd.setFont(MabUiTheme.STENCIL_SMALL);
        selInd.setForeground(rarityCol);
        selInd.setHorizontalAlignment(SwingConstants.CENTER);
        selInd.setAlignmentX(Component.LEFT_ALIGNMENT);
        selInd.setMaximumSize(new Dimension(Short.MAX_VALUE, 20));
        p.add(selInd);
        selectionIndicators.add(selInd);

        // ── Hover / click ──
        p.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                confirmSelection(index);
            }
            @Override public void mouseEntered(MouseEvent e) {
                setSelectedIndex(index);
            }
            @Override public void mouseExited(MouseEvent e) {
                p.setBackground(cardNormal);
                updateSelectedCardStyles();
            }
        });
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return p;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void installKeyboardActions() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        // Fixed navigation keys — always active regardless of player bindings.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "selectPrevious");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0), "selectPrevious");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "selectNext");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0), "selectNext");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "confirmSelection");

        am.put("selectPrevious", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelection(-1);
            }
        });
        am.put("selectNext", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelection(+1);
            }
        });
        am.put("confirmSelection", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                confirmSelection(selectedIndex);
            }
        });
    }

    /**
     * Adds the player's configured move-left, move-right, and hard-drop keys
     * as additional aliases for the three overlay actions. Called each time
     * {@link #show} fires so bindings stay current with any re-calibration.
     */
    private void bindPlayerKeys(boolean player2) {
        Settings s = Settings.get();
        int left    = player2 ? s.getKeyP2MoveLeft()  : s.getKeyMoveLeft();
        int right   = player2 ? s.getKeyP2MoveRight() : s.getKeyMoveRight();
        int up      = player2 ? s.getKeyP2MoveUp()    : s.getKeyMoveUp();
        int down    = player2 ? s.getKeyP2MoveDown()  : s.getKeyMoveDown();
        int confirm = player2 ? s.getKeyP2HardDrop()  : s.getKeyHardDrop();

        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (left    != 0) im.put(KeyStroke.getKeyStroke(left,    0), "selectPrevious");
        if (up      != 0) im.put(KeyStroke.getKeyStroke(up,      0), "selectPrevious");
        if (right   != 0) im.put(KeyStroke.getKeyStroke(right,   0), "selectNext");
        if (down    != 0) im.put(KeyStroke.getKeyStroke(down,    0), "selectNext");
        if (confirm != 0) im.put(KeyStroke.getKeyStroke(confirm, 0), "confirmSelection");
    }

    private boolean handleKeyEvent(KeyEvent e) {
        if (!isVisible()) return false;
        int code = e.getKeyCode();

        // Release: clear held-key tracking; don't consume so other listeners stay happy.
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            navKeysHeld.remove(code);
            return false;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED) return false;

        // Determine whether this key is one we act on before checking repeat.
        boolean isNav     = (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_UP
                          || code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_DOWN);
        boolean isConfirm = (code == KeyEvent.VK_SPACE);

        // Player-configured bindings.
        if (!isNav && !isConfirm && activeDraft != null) {
            Settings s = Settings.get();
            boolean p2  = activeDraft.getParticipantId() == ParticipantId.PLAYER_B;
            int left    = p2 ? s.getKeyP2MoveLeft()  : s.getKeyMoveLeft();
            int right   = p2 ? s.getKeyP2MoveRight() : s.getKeyMoveRight();
            int up      = p2 ? s.getKeyP2MoveUp()    : s.getKeyMoveUp();
            int down    = p2 ? s.getKeyP2MoveDown()  : s.getKeyMoveDown();
            int confirm = p2 ? s.getKeyP2HardDrop()  : s.getKeyHardDrop();
            if (left    != 0 && (code == left  || code == up))    isNav     = true;
            if (right   != 0 && (code == right || code == down))  isNav     = true;
            if (confirm != 0 &&  code == confirm)                  isConfirm = true;
        }

        if (!isNav && !isConfirm) return false;

        // Consume auto-repeat silently: only act on the first press of each key.
        boolean repeated = !navKeysHeld.add(code);
        if (repeated) return true;

        // Act on first press only.
        if (isConfirm) {
            confirmSelection(selectedIndex);
            return true;
        }
        // isNav
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_UP) {
            moveSelection(-1);
        } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_DOWN) {
            moveSelection(+1);
        } else {
            // Player-bound nav key: determine direction.
            Settings s = Settings.get();
            boolean p2 = activeDraft != null && activeDraft.getParticipantId() == ParticipantId.PLAYER_B;
            int left  = p2 ? s.getKeyP2MoveLeft()  : s.getKeyMoveLeft();
            int up    = p2 ? s.getKeyP2MoveUp()     : s.getKeyMoveUp();
            if (code == left || code == up) moveSelection(-1);
            else                            moveSelection(+1);
        }
        return true;
    }

    private void moveSelection(int delta) {
        if (!isVisible() || renderedChoices.isEmpty()) return;
        int n = renderedChoices.size();
        selectedIndex = ((selectedIndex + delta) % n + n) % n;
        updateSelectedCardStyles();
        requestFocusInWindow();
    }

    private void setSelectedIndex(int index) {
        if (!isVisible() || index < 0 || index >= renderedChoices.size()) return;
        selectedIndex = index;
        updateSelectedCardStyles();
        requestFocusInWindow();
    }

    private void confirmSelection(int index) {
        if (!isVisible() || index < 0 || index >= renderedChoices.size()) return;
        Consumer<MabUpgradeCard> cb = pickCallback;
        if (cb == null) return;
        pickCallback = null; // single-shot guard
        cb.accept(renderedChoices.get(index));
    }

    private void updateSelectedCardStyles() {
        for (int i = 0; i < renderedCards.size(); i++) {
            JPanel panel = renderedCards.get(i);
            Color rarity = rarityColor(renderedChoices.get(i));
            boolean selected = i == selectedIndex;
            // Selected: vivid rarity-tinted background clearly distinct from CARD_BG
            panel.setBackground(selected ? selectedBg(rarity) : MabUiTheme.CARD_BG);
            panel.setBorder(selected ? selectedBorder(rarity) : normalBorder(rarity));
            if (i < selectionIndicators.size()) {
                JLabel ind = selectionIndicators.get(i);
                if (selected) {
                    ind.setText(">>  ARMED  <<");
                    ind.setForeground(brighten(rarity, 80));
                } else {
                    ind.setText(" ");
                }
            }
            panel.repaint();
        }
    }

    private static Color selectedBg(Color rarity) {
        // Blend rarity color at ~20% into a deep navy so the card is clearly highlighted
        int r = 0x08 + rarity.getRed()   / 6;
        int g = 0x14 + rarity.getGreen() / 6;
        int b = 0x30 + rarity.getBlue()  / 6;
        return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private static Color brighten(Color c, int amount) {
        return new Color(
                Math.min(255, c.getRed()   + amount),
                Math.min(255, c.getGreen() + amount),
                Math.min(255, c.getBlue()  + amount));
    }

    private static javax.swing.border.Border normalBorder(Color rarity) {
        return BorderFactory.createCompoundBorder(
                new LineBorder(rarity, 2),
                new EmptyBorder(12, 14, 12, 14));
    }

    private static javax.swing.border.Border selectedBorder(Color rarity) {
        // Outer 3px bright ring + inner 2px dim ring; padding reduced by 3 so total
        // inset (3+2+11 = 16) stays identical to normalBorder (2+14 = 16), preventing
        // any content-area shift when toggling selection.
        Color bright = brighten(rarity, 80);
        Color inner  = dimColor(bright);
        return BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(bright, 3),
                        new LineBorder(inner, 2)),
                new EmptyBorder(9, 11, 9, 11));
    }

    private static String buildStatusText(MabUpgradeCard card, MabUpgradeInventory inv) {
        if (card.isRepeatable() && inv != null) {
            int stacks = inv.getStacks(card.getId());
            return "STACK " + stacks + " / " + card.getMaxStacks();
        }
        List<String> tags = card.getEffectTags();
        for (String t : tags) {
            if (t.startsWith("route_"))         return "ROUTE MODIFIER";
            if (t.equals("defense_dead_hand"))    return "REACTIVE";
            if (t.equals("charge_intercept"))    return "REACTIVE";
        }
        for (String t : tags) {
            if (t.startsWith("defense_"))        return "PASSIVE";
        }
        return "NEW DOCTRINE";
    }

    private static String categoryWord(MabUpgradeCategory cat) {
        return switch (cat) {
            case CHARGE       -> "CHARGE";
            case TETRIS_ROUTE -> "TETRIS";
            case SPIN_ROUTE   -> "SPIN";
            case DEFENSE      -> "DEFENSE";
            case POWER        -> "POWER";
            case TEMPO        -> "TEMPO";
        };
    }

    private static String categoryBadge(MabUpgradeCategory cat) {
        return switch (cat) {
            case CHARGE       -> "CHG";
            case TETRIS_ROUTE -> "TET";
            case SPIN_ROUTE   -> "SPN";
            case DEFENSE      -> "DEF";
            case POWER        -> "PWR";
            case TEMPO        -> "TMP";
        };
    }

    private static Color categoryColor(MabUpgradeCategory cat) {
        return switch (cat) {
            case CHARGE       -> MabUiTheme.C_CYAN;
            case TETRIS_ROUTE -> MabUiTheme.C_GREEN;
            case SPIN_ROUTE   -> new Color(0x70, 0xA8, 0xFF);
            case DEFENSE      -> new Color(0x60, 0xD0, 0x90);
            case POWER        -> MabUiTheme.C_RED;
            case TEMPO        -> MabUiTheme.C_MAGENTA;
        };
    }

    private static Color rarityColor(MabUpgradeCard c) {
        return switch (c.getRarity()) {
            case STANDARD -> MabUiTheme.INFO;      // soft blue #80B8FF
            case ADVANCED -> MabUiTheme.C_AMBER;   // amber #FFB300
            case CRITICAL -> MabUiTheme.C_MAGENTA; // magenta #FF3DC9
        };
    }

    /** Return a dim (darker) version of a color for secondary badge text. */
    private static Color dimColor(Color c) {
        return new Color(
                Math.max(0, c.getRed()   - 80),
                Math.max(0, c.getGreen() - 80),
                Math.max(0, c.getBlue()  - 80));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
