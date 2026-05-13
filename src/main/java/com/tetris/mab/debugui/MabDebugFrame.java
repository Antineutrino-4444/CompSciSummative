package com.tetris.mab.debugui;

import com.tetris.mab.decoy.DecoyType;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Step 12 — Swing debug window for the Mutually Assured Blocks
 * vertical slice. Opens as a separate, non-modal {@link JFrame}
 * alongside the main game window so existing single-player UI is
 * untouched.
 *
 * <p><b>NOT FINAL UI.</b> The title bar and an in-panel banner make
 * this clear so the panel is never mistaken for production UX.
 */
public final class MabDebugFrame extends JFrame {

    private static final int REFRESH_MS = 400;
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final MabDebugController controller;
    private final JTextArea snapshotArea = new JTextArea();
    private final JTextArea eventsArea = new JTextArea();
    private final Timer refreshTimer;

    public MabDebugFrame(MabDebugController controller) {
        super("MAB Debug / Vertical Slice — NOT final UI");
        if (controller == null) throw new IllegalArgumentException("controller");
        this.controller = controller;

        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(new Dimension(820, 720));
        setLocationByPlatform(true);

        JLabel banner = new JLabel(
                "MAB Debug / Vertical Slice — these controls are debug-only and not final game UX.",
                SwingConstants.CENTER);
        banner.setOpaque(true);
        banner.setBackground(new Color(0x442200));
        banner.setForeground(Color.WHITE);
        banner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        snapshotArea.setEditable(false);
        snapshotArea.setFont(MONO);
        snapshotArea.setLineWrap(false);

        eventsArea.setEditable(false);
        eventsArea.setFont(MONO);
        eventsArea.setLineWrap(false);

        JScrollPane snapshotScroll = new JScrollPane(snapshotArea);
        snapshotScroll.setBorder(BorderFactory.createTitledBorder("Match snapshot"));

        JScrollPane eventsScroll = new JScrollPane(eventsArea);
        eventsScroll.setBorder(BorderFactory.createTitledBorder("Recent events (oldest → newest)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, snapshotScroll, eventsScroll);
        split.setResizeWeight(0.65);
        split.setDividerLocation(440);

        JComponent buttonBar = buildButtonBar();
        JTextArea help = new JTextArea(controller.helpText());
        help.setEditable(false);
        help.setFont(MONO);
        help.setBackground(new Color(0xF4F4F4));
        help.setBorder(BorderFactory.createTitledBorder("Help"));

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttonBar, BorderLayout.NORTH);
        south.add(help, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(banner, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        installKeyShortcuts(root);

        refreshTimer = new Timer(REFRESH_MS, e -> refresh());
        refreshTimer.setRepeats(true);
        refresh();
    }

    /** Starts the refresh loop and shows the window. */
    public void start() {
        refreshTimer.start();
        setVisible(true);
    }

    /** Stops refresh and disposes the window. */
    public void shutdown() {
        refreshTimer.stop();
        dispose();
    }

    private void refresh() {
        // Step 13: auto-tick AI before redrawing so the displayed
        // snapshot reflects the most recent decision.
        controller.tickAiIfEnabled();
        snapshotArea.setText(controller.formattedSnapshotWithAi());
        snapshotArea.setCaretPosition(0);
        List<String> rows = controller.formattedEvents();
        StringBuilder sb = new StringBuilder();
        for (String r : rows) sb.append(r).append('\n');
        eventsArea.setText(sb.toString());
        eventsArea.setCaretPosition(eventsArea.getDocument().getLength());
    }

    private JComponent buildButtonBar() {
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setBorder(BorderFactory.createTitledBorder("Debug controls"));

        grid.add(button("Add Charge A", e -> { controller.addChargeA(); refresh(); }));
        grid.add(button("Add Charge B", e -> { controller.addChargeB(); refresh(); }));
        grid.add(button("Arm A",        e -> { controller.armA();        refresh(); }));
        grid.add(button("Arm B",        e -> { controller.armB();        refresh(); }));

        grid.add(button("Launch A",     e -> { controller.launchA();     refresh(); }));
        grid.add(button("Launch B",     e -> { controller.launchB();     refresh(); }));
        grid.add(button("Radar A",      e -> { controller.radarA();      refresh(); }));
        grid.add(button("Radar B",      e -> { controller.radarB();      refresh(); }));

        grid.add(button("CivDef A",     e -> { controller.civilDefenseA(); refresh(); }));
        grid.add(button("CivDef B",     e -> { controller.civilDefenseB(); refresh(); }));
        grid.add(button("Decoy A",      e -> { controller.decoyA(DecoyType.DECOY_LAUNCH); refresh(); }));
        grid.add(button("Decoy B",      e -> { controller.decoyB(DecoyType.DECOY_LAUNCH); refresh(); }));

        grid.add(button("Resolve Impacts",   e -> { controller.resolveImpacts();   refresh(); }));
        grid.add(button("Open Upgrade Pause", e -> { controller.openUpgradePause();  refresh(); }));
        grid.add(button("Close Upgrade Pause", e -> { controller.closeUpgradePause(); refresh(); }));
        grid.add(button("Refresh now",       e -> refresh()));

        // Step 13: PvE AI controls.
        grid.add(button("Toggle AI B",  e -> { controller.toggleAi();   refresh(); }));
        grid.add(button("AI Tick Once", e -> { controller.tickAiOnce(); refresh(); }));

        // Step 14: headless smoke simulation.
        grid.add(button("Run Smoke Sim", e -> {
            String result = controller.runHeadlessSmokeSimulation();
            javax.swing.JOptionPane.showMessageDialog(this, result,
                    "Headless Smoke Simulation",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            refresh();
        }));

        // Wrap so the bar is left-aligned inside its parent.
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.add(grid);
        wrap.add(Box.createVerticalStrut(4));
        return wrap;
    }

    private static JButton button(String label, java.awt.event.ActionListener l) {
        JButton b = new JButton(label);
        b.addActionListener(l);
        b.setFocusable(false); // keep focus on the frame for shortcuts
        return b;
    }

    private void installKeyShortcuts(JComponent root) {
        bind(root, "F5",  KeyEvent.VK_F5,  () -> {});
        bind(root, "F6",  KeyEvent.VK_F6,  controller::addChargeA);
        bind(root, "F7",  KeyEvent.VK_F7,  controller::armA);
        bind(root, "F8",  KeyEvent.VK_F8,  controller::launchA);
        bind(root, "F9",  KeyEvent.VK_F9,  controller::radarA);
        bind(root, "F10", KeyEvent.VK_F10, controller::resolveImpacts);
        bind(root, "ESC", KeyEvent.VK_ESCAPE, () -> setVisible(false));
    }

    private void bind(JComponent root, String name, int keyCode, Runnable r) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); refresh(); }
        });
    }
}
