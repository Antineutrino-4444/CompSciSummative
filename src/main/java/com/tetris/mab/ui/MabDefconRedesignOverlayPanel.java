package com.tetris.mab.ui;

import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.nuke.MabNukeBuilderBridge;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.upgrade.NukeRedesignRetentionRules;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * Full-screen DEFCON redesign flow used by the battle shell.
 *
 * <p>Hosts the real editable {@link NukeBuilderDialog} (slot/part
 * builder) inside the main game frame — there is no preset list, no
 * separate JFrame/JDialog, and no popup window. The player edits a
 * custom design; BEFORE shows the participant's current MAB design and
 * AFTER updates live from the builder's design-change listener.
 *
 * <p>The overlay is intentionally keyboard conservative: only Enter and
 * Escape are bound. Gameplay keys such as hard drop, rotate, and hold
 * are not confirmation shortcuts — the embedded builder additionally
 * quarantines those keys, and callers clear held input before showing
 * the overlay.
 */
public final class MabDefconRedesignOverlayPanel extends JPanel {

    private static final Font TITLE = MabUiTheme.STENCIL_HEADLINE.deriveFont(24f);
    private static final Font HEAD = MabUiTheme.STENCIL_SMALL.deriveFont(14f);
    private static final Font TERM = MabUiTheme.TERM_MED.deriveFont(12f);
    private static final Font TERM_BIG = MabUiTheme.TERM_BIG.deriveFont(18f);

    private final MabNukeBuilderBridge bridge = new MabNukeBuilderBridge();

    private final JPanel content = new JPanel(new BorderLayout(12, 12));
    private final JLabel titleLabel = new JLabel("DEFCON REDESIGN", SwingConstants.CENTER);
    private final JLabel subLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel countdownLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel body = new JPanel(new BorderLayout(12, 12));
    private final JTextArea beforeArea = textArea();
    private final JTextArea afterArea = textArea();
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton cancelButton = new JButton("CANCEL");

    /** The embedded editable builder. One instance retained for the
     *  lifetime of the overlay; its working design is replaced via
     *  {@link NukeBuilderDialog#loadDesign} every time the review is
     *  reopened. */
    private NukeBuilderDialog builder;
    private Runnable builderChangeHook;

    private Timer countdownTimer;
    private Timer autoConfirmTimer;
    private int secondsRemaining;
    private MutuallyAssuredBlocksMatch match;
    private ParticipantId participantId;
    private int defconLevel = 5;

    /** The exact previous live MAB design. Preserved here and never
     *  mutated until {@link #confirmSelection()} fires — cancel always
     *  exits without touching match state. */
    private NukeDesign currentDesign;
    private int currentCharge;

    private Consumer<MabNukeDesignSelection> confirmHandler;
    private Runnable cancelHandler;
    private boolean confirmFired;

    /** When AI-controlled and non-null, this MAB design is applied on
     *  auto-confirm — the AI bypasses the builder UI but still flows
     *  through the same overlay path. Ignored when human-controlled. */
    private NukeDesign aiFinalDesign;
    private boolean aiControlled;

    public MabDefconRedesignOverlayPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setFocusable(true);

        content.setOpaque(true);
        content.setBackground(MabUiTheme.SHELL_BG);
        content.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.C_AMBER, 2),
                new EmptyBorder(12, 16, 12, 16)));
        content.setPreferredSize(new Dimension(1320, 720));
        content.setMinimumSize(new Dimension(1280, 700));
        content.setMaximumSize(new Dimension(1366, 740));

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
        header.setOpaque(false);
        titleLabel.setFont(TITLE);
        titleLabel.setForeground(MabUiTheme.C_AMBER);
        subLabel.setFont(TERM);
        subLabel.setForeground(MabUiTheme.TEXT);
        countdownLabel.setFont(TERM_BIG);
        countdownLabel.setForeground(MabUiTheme.C_RED);
        header.add(titleLabel);
        header.add(subLabel);
        header.add(countdownLabel);
        content.add(header, BorderLayout.NORTH);

        body.setOpaque(false);
        content.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        statusLabel.setFont(TERM);
        statusLabel.setForeground(MabUiTheme.TEXT_FAINT);
        footer.add(statusLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        styleButton(cancelButton, false);
        buttons.add(cancelButton);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(8, 12, 8, 12);
        add(content, gc);

        cancelButton.addActionListener(e -> cancelSelection());
        installKeyboardActions();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        g.setColor(new Color(2, 6, 11, 226));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    public void showCountdown(int previousDefcon, int nextDefcon, double nextProgress,
                              double gravityMultiplier, Runnable onFinished) {
        stopTimers();
        this.match = null;
        this.participantId = null;
        this.confirmHandler = null;
        this.cancelHandler = null;
        teardownBuilder();
        body.removeAll();
        titleLabel.setText("DEFCON " + previousDefcon + " -> " + nextDefcon);
        subLabel.setText(String.format(
                "Progress %.0f%% reached. Gravity %.2fx. Boards paused for redesign.",
                Math.max(0.0, Math.min(1.0, nextProgress)) * 100.0,
                gravityMultiplier));
        secondsRemaining = 3;
        countdownLabel.setText("REDESIGN REVIEW IN 3");
        statusLabel.setText("Held gameplay input cleared. Confirm key shown in builder top-right.");
        cancelButton.setVisible(false);
        body.add(centerMessage("Both boards paused. Strategic timers are frozen."), BorderLayout.CENTER);
        revalidate();
        repaint();
        playCountdownCue(secondsRemaining);
        countdownTimer = new Timer(1000, e -> {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                playCountdownCue(0);
                stopCountdown();
                if (onFinished != null) onFinished.run();
            } else {
                countdownLabel.setText("REDESIGN REVIEW IN " + secondsRemaining);
                playCountdownCue(secondsRemaining);
            }
        });
        countdownTimer.setRepeats(true);
        countdownTimer.start();
        requestFocusLater();
    }

    /**
     * Open the editable redesign review.
     *
     * @param builderSeed optional builder-model design to seed the
     *        embedded builder with as the closest editable starting
     *        point — typically {@link MabNukeDesignSelection#getBuilderSource()}.
     *        Null leaves the builder at its default placeholder. Either
     *        way, the exact previous MAB design is preserved internally
     *        and BEFORE/AFTER displays compute against it.
     * @param aiFinalDesign optional MAB-model design auto-applied on
     *        confirm when {@code aiControlled} is true; ignored
     *        otherwise.
     */
    public void showReview(MutuallyAssuredBlocksMatch match,
                           ParticipantId participantId,
                           String title,
                           int defconLevel,
                           com.tetris.model.nuke.NukeDesign builderSeed,
                           NukeDesign aiFinalDesign,
                           boolean aiControlled,
                           Consumer<MabNukeDesignSelection> onConfirm,
                           Runnable onCancel) {
        stopTimers();
        this.match = match;
        this.participantId = participantId == null ? ParticipantId.PLAYER_A : participantId;
        this.defconLevel = Math.max(1, Math.min(5, defconLevel));
        this.confirmHandler = onConfirm;
        this.cancelHandler = onCancel;
        this.confirmFired = false;
        this.aiFinalDesign = aiFinalDesign;
        this.aiControlled = aiControlled;
        readCurrentDesign();

        titleLabel.setText(title == null ? "WARHEAD REDESIGN" : title);
        subLabel.setText("DEFCON " + this.defconLevel
                + " design review. Edit the builder; BEFORE shows current, AFTER updates live.");
        countdownLabel.setText(aiControlled ? "AUTOMATED REVIEW" : "EDIT YOUR WARHEAD");
        statusLabel.setText(aiControlled
                ? "AI review is using the builder path."
                : "Cancel keeps the current design and charge unchanged.");

        buildReviewBody(aiControlled, builderSeed);
        cancelButton.setVisible(!aiControlled);
        if (builder != null) builder.setEnabled(!aiControlled);
        revalidate();
        repaint();
        requestFocusLater();

        if (aiControlled) {
            autoConfirmTimer = new Timer(1200, e -> confirmSelection());
            autoConfirmTimer.setRepeats(false);
            autoConfirmTimer.start();
        }
    }

    public void dismiss() {
        stopTimers();
        teardownBuilder();
        setVisible(false);
    }

    private void buildReviewBody(boolean aiControlled,
                                 com.tetris.model.nuke.NukeDesign suggestedSeed) {
        teardownBuilder();
        body.removeAll();

        builder = NukeBuilderDialog.createEmbedded(() -> { /* ESC handled by overlay */ });
        if (suggestedSeed != null) {
            builder.loadDesign(suggestedSeed);
        } else {
            builder.resetBuild();
        }
        builderChangeHook = this::refreshAfterPreview;
        builder.addDesignChangeListener(builderChangeHook);
        // Bind the configured hard-drop key to confirm. The hint
        // appears in the builder's top-right key-hint label. Skip
        // for AI-controlled reviews — those auto-confirm via timer.
        if (!aiControlled) {
            builder.setConfirmKeyHandler(this::confirmSelection);
        }

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(true);
        left.setBackground(MabUiTheme.SHELL_PANEL_BG);
        left.setBorder(new LineBorder(MabUiTheme.GRID_LINE_HI, 1));
        JLabel builderHead = new JLabel("BUILDER");
        builderHead.setFont(HEAD);
        builderHead.setForeground(MabUiTheme.C_CYAN);
        builderHead.setBorder(new EmptyBorder(6, 10, 4, 10));
        left.add(builderHead, BorderLayout.NORTH);
        left.add(builder, BorderLayout.CENTER);

        JPanel comparison = new JPanel(new GridLayout(2, 1, 0, 8));
        comparison.setOpaque(false);
        comparison.add(panel("BEFORE (current)", beforeArea));
        comparison.add(panel("AFTER (edited)",  afterArea));
        comparison.setPreferredSize(new Dimension(310, 0));

        JPanel split = new JPanel(new BorderLayout(10, 0));
        split.setOpaque(false);
        split.add(left, BorderLayout.CENTER);
        split.add(comparison, BorderLayout.EAST);
        body.add(split, BorderLayout.CENTER);

        refreshAfterPreview();
    }

    /** Recompute BEFORE/AFTER text. BEFORE comes from the immutable
     *  {@link #currentDesign}; AFTER is the bridge translation of the
     *  builder's live design. */
    private void refreshAfterPreview() {
        beforeArea.setText(describeDesign(currentDesign, currentCharge, currentCharge, 1.0));
        com.tetris.model.nuke.NukeDesign edited = builder == null
                ? null : builder.getDesignForIntegration();
        NukeDesign next = edited == null ? currentDesign : bridge.toMabDesign(edited);
        double ratio = NukeRedesignRetentionRules.suggestedRetentionRatio(currentDesign, next);
        int retained = (int) Math.floor(currentCharge * ratio);
        afterArea.setText(describeDesign(next, retained, currentCharge, ratio));
        beforeArea.setCaretPosition(0);
        afterArea.setCaretPosition(0);
    }

    private String describeDesign(NukeDesign design, int visibleCharge,
                                  int originalCharge, double retainedRatio) {
        if (design == null) return "(no design)";
        int requirement = design.effectiveBuildChargeRequired(defconLevel);
        boolean armed = visibleCharge >= requirement;
        StringBuilder sb = new StringBuilder();
        sb.append("Design          : ").append(design.getDisplayName()).append('\n');
        sb.append("Payload         : ").append(design.getDoctrineType().displayLabel()).append('\n');
        sb.append("Charge          : ").append(visibleCharge).append(" / ").append(requirement).append('\n');
        sb.append("Retained charge : ").append(visibleCharge)
          .append(" of ").append(originalCharge)
          .append(String.format(" (%.0f%%)", retainedRatio * 100.0)).append('\n');
        sb.append("DEFCON required : ").append(requirement).append('\n');
        sb.append("Armed state     : ").append(armed ? "ARMED" : "NOT ARMED").append('\n');
        sb.append("Route targets   : Tetris ")
          .append(design.effectiveLaunchTetrisGoal(defconLevel))
          .append(" / Spin ")
          .append(design.effectiveLaunchSpinGoal(defconLevel)).append('\n');
        sb.append("Countdown       : ")
          .append(design.effectiveLaunchTimePieces(defconLevel)).append(" pieces").append('\n');
        sb.append("Impact delay    : ")
          .append(design.effectiveImpactDelayPieces(defconLevel)).append(" pieces").append('\n');
        sb.append("BLAST           : ").append(design.getBlastRating()).append('\n');
        sb.append("RAD             : ").append(design.getRadiationRating()).append('\n');
        sb.append("EMP             : ").append(design.getEmpRating()).append('\n');
        sb.append("DISARM          : ").append(design.getDisarmRating()).append('\n');
        sb.append("SILO            : ").append(design.getSiloDamageRating()).append('\n');
        sb.append("Intercept diff. : ").append(design.interceptDifficultyRating()).append('\n');
        return sb.toString();
    }

    private void confirmSelection() {
        stopAutoConfirm();
        // Confirm must apply exactly once. Repeated key/click events
        // (e.g. a held Enter) cannot fire a second redesign.
        if (confirmFired) return;
        confirmFired = true;
        Consumer<MabNukeDesignSelection> handler = confirmHandler;
        confirmHandler = null;
        cancelHandler = null;
        if (handler == null) return;
        MabNukeDesignSelection selection;
        if (aiControlled && aiFinalDesign != null) {
            // AI bypasses the builder UI; apply its MAB-domain design
            // directly so AI strategy is not lost.
            selection = MabNukeDesignSelection.fromMabDesign(aiFinalDesign);
        } else {
            com.tetris.model.nuke.NukeDesign edited = builder == null
                    ? null : builder.exportBuilderDesign();
            selection = edited == null
                    ? MabNukeDesignSelection.fromMabDesign(currentDesign)
                    : MabNukeDesignSelection.fromBuilderDesign(edited);
        }
        handler.accept(selection);
    }

    private void cancelSelection() {
        stopAutoConfirm();
        // Cancel preserves the exact previous live design — we never
        // mutated the participant's NukeBuildState, so simply
        // releasing the handlers is enough.
        Runnable handler = cancelHandler;
        confirmHandler = null;
        cancelHandler = null;
        if (handler != null) handler.run();
    }

    private void readCurrentDesign() {
        currentDesign = null;
        currentCharge = 0;
        if (match == null) return;
        ParticipantState p = match.getParticipant(participantId);
        if (p == null) return;
        NukeBuildState nb = p.getNukeBuildState();
        if (nb == null) return;
        currentDesign = nb.getCurrentDesign();
        currentCharge = nb.getCurrentBuildCharge();
    }

    private void installKeyboardActions() {
        // Confirm is bound on the embedded builder via setConfirmKeyHandler
        // (the configured hard-drop key), so the overlay only owns the
        // ESC binding for cancel.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "cancelRedesign");
        getActionMap().put("cancelRedesign", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (isVisible() && cancelButton.isVisible()) cancelSelection();
            }
        });
    }

    private void teardownBuilder() {
        if (builder != null && builderChangeHook != null) {
            builder.removeDesignChangeListener(builderChangeHook);
        }
        builderChangeHook = null;
        builder = null;
    }

    /** Test/probe hook — returns the embedded builder if one is
     *  currently mounted, else {@code null}. */
    public NukeBuilderDialog getEmbeddedBuilderForProbe() { return builder; }

    /** Test/probe hook — triggers the same confirm path the hard-drop
     *  key would. The one-shot guard still applies. */
    public void triggerConfirmForProbe() { confirmSelection(); }

    private static JPanel centerMessage(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        JLabel label = new JLabel(text == null ? "" : text, SwingConstants.CENTER);
        label.setFont(TERM_BIG);
        label.setForeground(MabUiTheme.TEXT);
        p.add(label);
        return p;
    }

    private static JPanel panel(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(true);
        p.setBackground(MabUiTheme.SHELL_PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.GRID_LINE_HI, 1),
                new EmptyBorder(6, 8, 6, 8)));
        JLabel label = new JLabel(title);
        label.setFont(HEAD);
        label.setForeground(MabUiTheme.C_CYAN);
        p.add(label, BorderLayout.NORTH);
        p.add(area, BorderLayout.CENTER);
        return p;
    }

    private static JTextArea textArea() {
        JTextArea a = new JTextArea(14, 28);
        a.setEditable(false);
        a.setLineWrap(false);
        a.setFont(TERM);
        a.setBackground(MabUiTheme.SHELL_DEEP_BG);
        a.setForeground(MabUiTheme.TEXT);
        a.setBorder(new EmptyBorder(6, 8, 6, 8));
        return a;
    }

    private static void styleButton(JButton b, boolean primary) {
        b.setFont(MabUiTheme.STENCIL_SMALL);
        b.setForeground(primary ? MabUiTheme.C_AMBER : MabUiTheme.C_CYAN);
        b.setBackground(MabUiTheme.SHELL_PANEL_BG);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(primary ? MabUiTheme.C_AMBER : MabUiTheme.C_CYAN_DIM, 1),
                new EmptyBorder(6, 14, 6, 14)));
    }

    private void requestFocusLater() {
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void playCountdownCue(int seconds) {
        SoundEffect cue = switch (seconds) {
            case 5 -> SoundEffect.COUNTDOWN5;
            case 4 -> SoundEffect.COUNTDOWN4;
            case 3 -> SoundEffect.COUNTDOWN3;
            case 2 -> SoundEffect.COUNTDOWN2;
            case 1 -> SoundEffect.COUNTDOWN1;
            case 0 -> SoundEffect.GO;
            default -> null;
        };
        if (cue != null) SoundEffectManager.shared().play(cue);
    }

    private void stopTimers() {
        stopCountdown();
        stopAutoConfirm();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void stopAutoConfirm() {
        if (autoConfirmTimer != null) {
            autoConfirmTimer.stop();
            autoConfirmTimer = null;
        }
    }
}
