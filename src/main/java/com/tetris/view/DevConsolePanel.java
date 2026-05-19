package com.tetris.view;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * In-game developer console, toggled by the backtick / tilde key.
 *
 * <p>Slides in from the bottom of its parent container. Accepts text
 * commands and outputs results in a scrolling monospace area. The caller
 * supplies a {@link Function} that maps a command string to an output string;
 * the console itself has no game knowledge.
 *
 * <p>Mount this panel in a {@link JLayeredPane} at {@link JLayeredPane#POPUP_LAYER}
 * and call {@link #setBounds(int, int, int, int)} to fill the desired area.
 */
public final class DevConsolePanel extends JPanel {

    // ── Visual constants ──────────────────────────────────────────────────
    private static final Color BG          = new Color(0x0A, 0x12, 0x0A, 230);
    private static final Color BG_INPUT    = new Color(0x04, 0x08, 0x04, 255);
    private static final Color FG_PROMPT   = new Color(0x00, 0xFF, 0x41);   // matrix green
    private static final Color FG_OUTPUT   = new Color(0xA0, 0xE8, 0xA0);
    private static final Color FG_ERROR    = new Color(0xFF, 0x55, 0x55);
    private static final Color FG_MUTED    = new Color(0x40, 0x80, 0x40);
    private static final Color BORDER_COL  = new Color(0x00, 0xCC, 0x33);
    private static final Font  MONO        = new Font("Monospaced", Font.PLAIN, 13);

    // ── Components ────────────────────────────────────────────────────────
    private final JTextPane outputPane;
    private final StyleContext styles      = new StyleContext();
    private final StyledDocument doc;
    private final JTextField inputField;

    // ── Command history ───────────────────────────────────────────────────
    private final List<String> history    = new ArrayList<>();
    private int historyIndex              = -1;

    // ── Command handler ───────────────────────────────────────────────────
    private final Function<String, String> commandHandler;

    public DevConsolePanel(Function<String, String> commandHandler) {
        this.commandHandler = commandHandler != null ? commandHandler : cmd -> "unknown command";

        setOpaque(true);
        setBackground(BG);
        setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, BORDER_COL));
        setLayout(new BorderLayout(0, 0));

        // ── Output area ──
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setOpaque(true);
        outputPane.setBackground(BG);
        outputPane.setFont(MONO);
        outputPane.setFocusable(false);
        doc = outputPane.getStyledDocument();

        setupStyles();

        JScrollPane scroll = new JScrollPane(outputPane);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        styleScrollBar(scroll.getVerticalScrollBar());
        add(scroll, BorderLayout.CENTER);

        // ── Input row ──
        JPanel inputRow = new JPanel(new BorderLayout(0, 0));
        inputRow.setOpaque(true);
        inputRow.setBackground(BG_INPUT);
        inputRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        JLabel prompt = new JLabel("> ");
        prompt.setFont(MONO);
        prompt.setForeground(FG_PROMPT);
        inputRow.add(prompt, BorderLayout.WEST);

        inputField = new JTextField();
        inputField.setOpaque(false);
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(FG_PROMPT);
        inputField.setCaretColor(FG_PROMPT);
        inputField.setFont(MONO);
        inputField.setBorder(BorderFactory.createEmptyBorder());
        inputField.addActionListener(e -> submitCommand());
        inputField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit-console-command");
        inputField.getActionMap().put("submit-console-command", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                submitCurrentInput();
            }
        });
        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_UP)   { navigateHistory(-1); e.consume(); }
                if (code == KeyEvent.VK_DOWN)  { navigateHistory(+1); e.consume(); }
                if (code == KeyEvent.VK_BACK_QUOTE) e.consume(); // don't echo the toggle key
            }
        });
        inputRow.add(inputField, BorderLayout.CENTER);
        add(inputRow, BorderLayout.SOUTH);

        printLine("Dev Console  —  ` to close  |  type 'help' for commands", "muted");
        printLine("─".repeat(60), "muted");
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Make the console visible and immediately focused. */
    public void open() {
        setVisible(true);
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
    }

    /** Hide the console. */
    public void close() {
        setVisible(false);
    }

    public boolean isOpen() { return isVisible(); }

    public void toggle() {
        if (isOpen()) close(); else open();
    }

    public void submitCurrentInput() {
        submitCommand();
    }

    public void focusInput() {
        inputField.requestFocusInWindow();
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private void submitCommand() {
        String raw = inputField.getText().trim();
        inputField.setText("");
        if (raw.isEmpty()) return;

        history.add(0, raw);
        historyIndex = -1;

        printLine("> " + raw, "prompt");

        String result = dispatchBuiltin(raw);
        if (result == null) result = commandHandler.apply(raw);
        if (result != null && !result.isEmpty()) {
            printMultiline(result, "output");
        }
        printLine("", "output"); // blank separator
        scrollToBottom();
    }

    private String dispatchBuiltin(String cmd) {
        return switch (cmd.toLowerCase().trim()) {
            case "help" -> """
                    CONSOLE COMMANDS
                    defcon max      - prime DEFCON so one single clear escalates
                    ────────────────────────────────
                    help            — show this message
                    clear           — clear console output
                    fps             — current render FPS
                    gc              — run garbage collector
                    freeze          — freeze piece gravity
                    unfreeze        — unfreeze piece gravity
                    debug           — freeze gravity + open cheat menu
                    ────────────────────────────────
                    MAB MATCH COMMANDS (PvE / PvP only)
                    state           — show P1 charge / doctrine / threat state
                    charge full     — fill P1 nuke charge to armed threshold
                    charge <N>      — add N charge to P1 nuke
                    launch          — force P1 launch (normal, arms nuke if needed)
                    launch override — fire P1 MANUAL OVERRIDE doctrine launch
                    override        — fire P1 MANUAL OVERRIDE active doctrine directly
                    emp             — fire P1 EMP active doctrine
                    threat          — inject a test incoming threat against P1
                    resolve         — resolve all impact-ready threats now
                    points [N]      — add N upgrade points to P1 (default 100)
                    clock [N]       — advance P1 strategic clock N pieces (default 10)
                    ai pause        — pause all AI activity
                    ai resume       — resume AI activity
                    ai status       — show AI pause state
                    ────────────────────────────────
                    SHORTCUTS
                    ` / ~           — toggle this console
                    F3              — toggle performance overlay
                    """;
            case "clear" -> { clearOutput(); yield ""; }
            default -> null;
        };
    }

    private void navigateHistory(int delta) {
        int newIndex = historyIndex + delta;
        if (newIndex < -1 || newIndex >= history.size()) return;
        historyIndex = newIndex;
        inputField.setText(historyIndex < 0 ? "" : history.get(historyIndex));
    }

    private void printLine(String text, String styleName) {
        try {
            doc.insertString(doc.getLength(), text + "\n", styles.getStyle(styleName));
        } catch (BadLocationException ignored) {}
    }

    private void printMultiline(String text, String styleName) {
        for (String line : text.split("\n", -1)) printLine(line, styleName);
    }

    void printError(String text) { printLine(text, "error"); scrollToBottom(); }

    private void clearOutput() {
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = ((JScrollPane) outputPane.getParent().getParent()).getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void setupStyles() {
        Style def = styles.addStyle("default", null);
        StyleConstants.setFontFamily(def, "Monospaced");
        StyleConstants.setFontSize(def, 13);

        Style prompt = styles.addStyle("prompt", def);
        StyleConstants.setForeground(prompt, FG_PROMPT);

        Style output = styles.addStyle("output", def);
        StyleConstants.setForeground(output, FG_OUTPUT);

        Style error = styles.addStyle("error", def);
        StyleConstants.setForeground(error, FG_ERROR);

        Style muted = styles.addStyle("muted", def);
        StyleConstants.setForeground(muted, FG_MUTED);
    }

    private static void styleScrollBar(JScrollBar sb) {
        sb.setPreferredSize(new Dimension(6, 6));
        sb.setBackground(BG);
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = new Color(0x00, 0x99, 0x22);
                this.trackColor = BG;
            }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }
}
