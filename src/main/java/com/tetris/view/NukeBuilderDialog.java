package com.tetris.view;

import com.tetris.model.nuke.NukeDesign;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;
import com.tetris.model.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NukeBuilderDialog.java
 * ======================
 * Visual, KSP-style modular constructor for nuclear weapons.
 *
 * Layout:
 *   ┌────────────────┬──────────────────────────────┬────────────────┐
 *   │ PARTS PALETTE  │   CROSS-SECTION SCHEMATIC    │  INFO / STATS  │
 *   │ (filtered to   │   (clickable layers/regions) │                │
 *   │  active slot)  │                              │                │
 *   └────────────────┴──────────────────────────────┴────────────────┘
 *
 * Click a region of the schematic to make that slot active. The palette
 * on the left then lists the available parts for that slot — click one
 * to install it. The schematic re-paints to show the chosen component
 * and the right-hand info panel updates with educational text and the
 * derived yield / damage estimate.
 *
 * All information shown is at the conceptual level of a public
 * encyclopedia article. There is deliberately no engineering data.
 */
public class NukeBuilderDialog extends JPanel {

    // Reuse the LHC palette from SettingsPanel for visual consistency.
    private static final Color DARK_BG    = new Color(8, 12, 20);
    private static final Color PANEL_BG   = new Color(14, 20, 32);
    private static final Color TEXT_FG    = new Color(190, 215, 225);
    private static final Color ACCENT     = new Color(0, 200, 220);
    private static final Color ACCENT_DIM = new Color(0, 140, 160);
    private static final Color WARN_FG    = new Color(255, 170, 70);
    private static final Color DANGER_FG  = new Color(255, 90, 90);
    private static final Color BTN_BG     = new Color(20, 30, 48);

    /** Per-slot fill colors used in the schematic. */
    private static final Map<String, Color> SLOT_COLORS = new HashMap<>();
    static {
        SLOT_COLORS.put("config",     new Color( 30,  60,  90));
        SLOT_COLORS.put("fissile",    new Color(220, 110,  60));   // hot orange = pit
        SLOT_COLORS.put("tamper",     new Color(140, 110,  70));   // bronze
        SLOT_COLORS.put("initiator",  new Color(240, 240, 120));   // bright neutron
        SLOT_COLORS.put("implosion",  new Color(120,  40,  40));   // explosive red
        SLOT_COLORS.put("boost",      new Color( 80, 200, 220));   // cyan gas
        SLOT_COLORS.put("secondary",  new Color(180,  90, 200));   // fusion violet
        SLOT_COLORS.put("casing",     new Color( 80,  90, 105));   // gun-metal grey
        SLOT_COLORS.put("safety",     new Color(120, 180, 120));
        SLOT_COLORS.put("delivery",   new Color( 60,  80, 120));
    }

    private final NukeDesign design = new NukeDesign();

    private NukeSlot activeSlot = NukeSlot.CONFIGURATION;

    /** When non-null, the user is editing one of the four fusion
     *  sub-design groups. The group acts like a virtual slot: it shows
     *  up in the slot list (only for Two-stage configurations) and its
     *  options are listed in the parts column for normal UP/DOWN
     *  selection. {@link #activeSlot} is forced to {@link NukeSlot#SECONDARY}
     *  while a fusion group is active so the schematic continues to
     *  highlight the secondary stage. */
    private FusionGroup activeFusionGroup = null;

    /** Which on-screen column the keyboard is currently driving.
     *  LEFT/RIGHT swaps between the two columns; UP/DOWN moves the
     *  cursor inside the focused column. The fusion sub-design lives
     *  inside the same two-column system — its option groups are just
     *  extra rows appended to the slots column when applicable. */
    private enum FocusCol { SLOTS, PARTS }
    private FocusCol focusedColumn = FocusCol.SLOTS;

    /** Sub-design groups for the Teller-Ulam secondary stage. Each
     *  group appears as an extra row in the slots column (only when a
     *  Two-stage configuration is selected) and exposes a small list
     *  of mutually-exclusive options that the user picks like any
     *  other part. Together these groups make the secondary into a
     *  small "nuke builder of its own": fuel choice, tamper choice,
     *  ignition choice, channel coupling, and the number of stacked
     *  stages (2-, 3- or 4-stage weapon). */
    private enum FusionGroup {
        STAGE_SELECT ("Fusion: Edit Stage",
                      "Picks which fusion stage you are currently " +
                      "editing. Each stacked stage (secondary, " +
                      "optional tertiary, optional quaternary) carries " +
                      "its own pusher, channel filler, fuel and spark " +
                      "plug. Only visible when the device has more " +
                      "than one fusion stage.",
                      new String[]{ "Edit Secondary",
                                    "Edit Tertiary",
                                    "Edit Quaternary" }),
        FUEL         ("Fusion: Fuel",
                      "The fusion fuel inside the secondary capsule. " +
                      "Lithium-6 deuteride is the canonical 'dry' choice " +
                      "used by every modern weapon; natural lithium " +
                      "deuteride is cheaper but less predictable " +
                      "(Castle Bravo, 1954).",
                      new String[]{ "Lithium-6 deuteride (dry)",
                                    "Natural LiD (Li-6 + Li-7)" }),
        PUSHER       ("Fusion: Pusher",
                      "Tamper material wrapped around the secondary " +
                      "fusion stage. A heavy fissionable jacket (U-238) " +
                      "fast-fissions in the 14-MeV neutron flux and adds " +
                      "huge yield; an inert pusher (lead, tungsten) is " +
                      "cleaner but loses that contribution.",
                      new String[]{ "U-238", "Lead", "Tungsten" }),
        CHANNEL      ("Fusion: Channel Filler",
                      "Material that fills the radiation channel between " +
                      "the primary and the secondary. Polystyrene foam " +
                      "ablates evenly under the X-ray pulse and is the " +
                      "canonical choice; a vacuum is theoretically " +
                      "slightly more efficient.",
                      new String[]{ "Polystyrene foam", "Vacuum" }),
        SPARK_PLUG   ("Fusion: Spark Plug",
                      "Optional Pu-239 rod down the central axis of the " +
                      "secondary that itself goes critical from the " +
                      "compression and dumps a flood of neutrons into " +
                      "the fusion fuel — reliably igniting it.",
                      new String[]{ "Pu-239 rod", "(none)" }),
        STAGES       ("Fusion: Stage Count",
                      "How many fusion stages are stacked in the device. " +
                      "A standard Teller-Ulam weapon is 2-stage (one " +
                      "fission primary + one fusion secondary). Adding a " +
                      "U-238-jacketed tertiary makes it 3-stage (Castle " +
                      "Bravo, ~15 Mt) — the jacket fast-fissions in the " +
                      "fusion neutron flux and roughly doubles yield, at " +
                      "the cost of catastrophic fallout. A 4-stage " +
                      "device is hypothetical — no working example has " +
                      "ever existed.",
                      new String[]{ "2-stage",
                                    "3-stage",
                                    "4-stage" });

        final String name;
        final String description;
        final String[] options;
        FusionGroup(String name, String description, String[] options) {
            this.name = name;
            this.description = description;
            this.options = options;
        }
    }

    /** Extra cosmetic + descriptive choices for a Teller-Ulam secondary.
     *  These mirror the four {@link FusionGroup}s and feed both the
     *  schematic painter and the back-end {@link NukeDesign} model. */
    private final FusionDetails fusion = new FusionDetails();

    /** Holds the player's choices in the Fusion Designer sub-builder.
     *  Each stacked fusion stage carries its own pusher / channel /
     *  fuel / spark plug, indexed 0 = secondary, 1 = tertiary,
     *  2 = quaternary. Defaults match the canonical modern
     *  thermonuclear secondary on stage 0; higher stages start with
     *  the same defaults except their spark plug is OFF (physically,
     *  only the secondary needs a spark plug — higher stages are
     *  ignited by the previous stage's neutron flux). */
    static final class FusionDetails {
        static final int MAX = 3;
        /** Fuel choice per stage. Stage 0 mirrors NukeSlot.SECONDARY. */
        final String[]  fuel          = { "Lithium-6 deuteride (dry)",
                                          "Lithium-6 deuteride (dry)",
                                          "Lithium-6 deuteride (dry)" };
        final String[]  pusher        = { "U-238", "U-238", "U-238" };
        final String[]  channelFiller = { "Polystyrene foam",
                                          "Polystyrene foam",
                                          "Polystyrene foam" };
        final boolean[] sparkPlug     = { true, false, false };
        /** Number of fusion stages. 1 = standard 2-stage Teller-Ulam,
         *  2 = 3-stage (adds U-238 jacketed tertiary), 3 = 4-stage. */
        int     stageCount    = 1;
        /** Which stage the user is currently editing in the sub-builder
         *  (0 = secondary, 1 = tertiary, 2 = quaternary). Always
         *  clamped to a stage that actually exists. */
        int     editingStage  = 0;
    }

    private SchematicPanel schematic;
    private JPanel partsListPanel;
    private JPanel slotsBar;        // rebuilt per-refresh; holds slot+fusion buttons
    private JLabel paletteHeading;
    private JLabel slotsHeading;
    private JLabel keyHint;
    private JPanel slotsBarOuter;   // wraps slot list (for focus border)
    private JPanel listWrapOuter;   // wraps parts list (for focus border)
    private JTextArea infoArea;
    private JLabel yieldLabel, complexityLabel;
    private JTextArea analogLabel, radiusLabel;
    private JTextArea effectsArea, warningsArea;
    private final Map<NukeSlot, JButton> slotButtons = new LinkedHashMap<>();
    private final Map<FusionGroup, JButton> fusionGroupButtons = new LinkedHashMap<>();

    /** Listeners fired at the end of every {@link #refresh()} so embedded
     *  hosts (e.g. the DEFCON redesign overlay) can update an AFTER
     *  preview live as the player edits slots/parts/fusion choices. */
    private final java.util.List<Runnable> designChangeListeners = new java.util.ArrayList<>();
    /** When true, {@link #refresh()} skips firing change listeners.
     *  Used by {@link #loadDesign(NukeDesign)} to coalesce a burst of
     *  internal mutations into a single notification at the end. */
    private boolean suppressChangeNotifications = false;

    /** When non-null, the configured hard-drop key fires this handler
     *  to confirm the current design. Set by an embedding host via
     *  {@link #setConfirmKeyHandler(Runnable)}; null means no confirm
     *  key is bound and the {@link #keyHint} omits the confirm hint. */
    private Runnable confirmKeyHandler;

    /** Callback invoked when the user dismisses the panel (Close button
     *  or Escape). Used by the dialog wrapper to dispose, or by an
     *  embedded host (start menu) to navigate back. */
    private final Runnable onClose;

    public NukeBuilderDialog(Runnable onClose) {
        this.onClose = onClose != null ? onClose : () -> {};
        setBackground(DARK_BG);
        setLayout(new BorderLayout(0, 0));

        // Top stack: header above the horizontal schematic strip.
        JPanel topStack = new JPanel(new BorderLayout());
        topStack.setBackground(DARK_BG);
        topStack.add(buildHeader(), BorderLayout.NORTH);
        topStack.add(buildCenter(), BorderLayout.CENTER);

        add(topStack, BorderLayout.NORTH);
        add(buildPalette(), BorderLayout.WEST);
        add(buildInfoSidebar(), BorderLayout.CENTER);
        add(buildStatsSidebar(), BorderLayout.EAST);

        installKeyboardShortcuts();
        refresh();
    }

    /** Backwards-compatible constructor — the {@code owner} is unused
     *  in panel mode. Prefer {@link #showDialog(JFrame)}. */
    public NukeBuilderDialog(JFrame ignored) { this(() -> {}); }

    // ─────────────────────── UI: top header ─────────────────────────

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DARK_BG);
        p.setBorder(new EmptyBorder(10, 14, 6, 14));

        JLabel title = new JLabel("MODULAR NUCLEAR WEAPON CONSTRUCTOR");
        title.setForeground(ACCENT);
        title.setFont(new Font("Monospaced", Font.BOLD, 16));
        p.add(title, BorderLayout.WEST);

        // Context-aware key hint, updated on every state change to show
        // ONLY the arrow-key meanings that apply right now.
        keyHint = new JLabel(" ");
        keyHint.setForeground(new Color(255, 200, 90));
        keyHint.setFont(new Font("Monospaced", Font.BOLD, 11));
        keyHint.setHorizontalAlignment(SwingConstants.RIGHT);
        p.add(keyHint, BorderLayout.EAST);
        return p;
    }

    // ─────────────────────── UI: parts palette (left) ───────────────

    private JComponent buildPalette() {
        // Two side-by-side columns:
        //   WEST   = slot selector buttons (with Reset / Close stacked
        //            at the bottom so they’re always visible). When the
        //            current configuration is Two-stage thermonuclear,
        //            four extra "Fusion: …" rows are appended so the
        //            sub-design uses the exact same arrow-key controls
        //            as every other slot.
        //   CENTER = parts list for the currently active slot (or for
        //            the currently active fusion group).
        // Nothing lives below either column, so neither can ever push
        // the other into needing a scroll bar.
        JPanel wrap = new JPanel(new BorderLayout(8, 0));
        wrap.setBackground(DARK_BG);
        // Tight top inset; 6 px bottom inset on the wrap. The slot
        // bordered box ends 6 px ABOVE this (because leftCol has a
        // 6 px vgap + empty SOUTH placeholder), so the parts list on
        // the right needs an extra 6 px bottom padding to land at
        // the same Y as the slot box.
        wrap.setBorder(new EmptyBorder(2, 12, 6, 4));
        // Palette is much narrower than before — the slot list only
        // holds a dozen short button labels, and the parts list is
        // never wider than its longest option name. The freed pixels
        // go to the centre column (component info).
        wrap.setPreferredSize(new Dimension(440, 0));

        // ── LEFT column: slot list + Reset/Close at its bottom ──
        slotsHeading = new JLabel(" STAGE / SLOT");
        slotsHeading.setForeground(ACCENT);
        slotsHeading.setFont(new Font("Monospaced", Font.BOLD, 11));
        slotsHeading.setBorder(new EmptyBorder(6, 4, 2, 4));

        slotsBar = new JPanel();
        slotsBar.setLayout(new BoxLayout(slotsBar, BoxLayout.Y_AXIS));
        slotsBar.setBackground(PANEL_BG);
        slotsBar.setBorder(new EmptyBorder(6, 6, 6, 6));

        // Wrap the slot list so its rows hug the top while the bordered
        // box extends to the full column height \u2014 matches the parts
        // list on the right and keeps the bottom edge aligned with the
        // other columns' bordered boxes.
        JPanel slotsBarHost = new JPanel(new BorderLayout());
        slotsBarHost.setBackground(PANEL_BG);
        slotsBarHost.setBorder(new LineBorder(ACCENT_DIM, 1));
        slotsBarHost.add(slotsBar, BorderLayout.NORTH);
        slotsBarOuter = slotsBarHost; // border target for focus highlighting

        JPanel slotsCol = new JPanel(new BorderLayout(0, 4));
        slotsCol.setBackground(DARK_BG);
        slotsCol.add(slotsHeading, BorderLayout.NORTH);
        slotsCol.add(slotsBarHost, BorderLayout.CENTER);

        JPanel leftCol = new JPanel(new BorderLayout(0, 6));
        leftCol.setBackground(DARK_BG);
        leftCol.setPreferredSize(new Dimension(200, 0));
        // Put slotsCol at CENTER so its bordered list expands to fill
        // the full column height \u2014 this is the alignment "mark" that
        // every other column's bordered box matches at the bottom.
        leftCol.add(slotsCol, BorderLayout.CENTER);
        leftCol.add(buildPaletteActions(), BorderLayout.SOUTH);
        wrap.add(leftCol, BorderLayout.WEST);

        // ── RIGHT column: parts list for the active slot ──
        JPanel listWrap = new JPanel(new BorderLayout(0, 4));
        listWrap.setBackground(DARK_BG);
        // 6 px bottom padding so the parts bordered box ends at the
        // exact same Y as the slot bordered box on the left (which is
        // 6 px above the column's bottom because of leftCol's empty
        // SOUTH placeholder + vgap).
        listWrap.setBorder(new EmptyBorder(0, 0, 6, 0));

        paletteHeading = new JLabel(" ");
        paletteHeading.setForeground(ACCENT);
        paletteHeading.setFont(new Font("Monospaced", Font.BOLD, 11));
        paletteHeading.setBorder(new EmptyBorder(6, 4, 2, 4));
        listWrap.add(paletteHeading, BorderLayout.NORTH);

        partsListPanel = new JPanel();
        partsListPanel.setLayout(new BoxLayout(partsListPanel, BoxLayout.Y_AXIS));
        partsListPanel.setBackground(PANEL_BG);
        partsListPanel.setBorder(new javax.swing.border.CompoundBorder(
                new LineBorder(ACCENT_DIM, 1), new EmptyBorder(6, 6, 6, 6)));
        // Host the list at NORTH so the option rows hug the top, but
        // wrap that host in an outer panel that owns the border and
        // fills CENTER \u2014 this way the bordered box reaches the bottom
        // of the column even when there are only a few options.
        JPanel partsInner = new JPanel(new BorderLayout());
        partsInner.setBackground(PANEL_BG);
        partsInner.add(partsListPanel, BorderLayout.NORTH);
        // Move the visible border from partsListPanel to its container
        // so it spans the full extended height.
        partsListPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
        partsInner.setBorder(new LineBorder(ACCENT_DIM, 1));
        listWrap.add(partsInner, BorderLayout.CENTER);
        listWrapOuter = partsInner; // border target for focus highlighting

        wrap.add(listWrap, BorderLayout.CENTER);

        // Build the initial slot list (rebuilt on every state change to
        // pick up applicability changes and the fusion-group rows).
        rebuildSlotList();
        return wrap;
    }

    /** Rebuild the slot column. Real {@link NukeSlot}s come first; when
     *  the current configuration is a two-stage thermonuclear design
     *  the four {@link FusionGroup}s are appended so they navigate
     *  with the exact same UP/DOWN/LEFT/RIGHT controls as everything
     *  else in the panel. */
    private void rebuildSlotList() {
        if (slotsBar == null) return;
        slotsBar.removeAll();
        slotButtons.clear();
        fusionGroupButtons.clear();

        for (NukeSlot s : NukeSlot.ALL) {
            // The SECONDARY slot's job (picking the fusion fuel) has
            // moved into the fusion sub-builder when in two-stage mode
            // — hide it from the main slot column so the two systems
            // aren't presenting the same choice twice.
            if (s == NukeSlot.SECONDARY && inFusionMode()) continue;
            JButton b = new JButton(s.getName());
            styleSlotButton(b);
            b.addActionListener(e -> setActiveSlot(s));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            slotsBar.add(b);
            slotsBar.add(Box.createVerticalStrut(3));
            slotButtons.put(s, b);
        }

        // Fusion sub-design rows are ALWAYS present so the slot column
        // doesn't change height when the user switches configuration —
        // they just stay disabled (greyed out by updateSlotAvailability)
        // until a Two-stage thermonuclear configuration is selected.
        slotsBar.add(Box.createVerticalStrut(4));
        JLabel divider = new JLabel(" — SUB-DESIGN —");
        divider.setForeground(new Color(180, 90, 200));   // matches secondary slot tint
        divider.setFont(new Font("Monospaced", Font.BOLD, 10));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setBorder(new EmptyBorder(2, 2, 2, 2));
        slotsBar.add(divider);
        slotsBar.add(Box.createVerticalStrut(2));

        for (FusionGroup g : FusionGroup.values()) {
            // STAGE_SELECT only matters when there's more than one
            // fusion stage to switch between — keep the slot column
            // tidy in the standard 2-stage case.
            if (g == FusionGroup.STAGE_SELECT && fusion.stageCount <= 1) continue;
            // Per-stage groups carry a plain label; the active stage
            // is communicated through STAGE_SELECT and the schematic.
            String label = g.name;
            JButton b = new JButton(label);
            styleSlotButton(b);
            b.addActionListener(e -> setActiveFusionGroup(g));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            slotsBar.add(b);
            slotsBar.add(Box.createVerticalStrut(3));
            fusionGroupButtons.put(g, b);
        }

        slotsBar.revalidate();
        slotsBar.repaint();
    }

    /** Reset the entire build. Public so the embedded host (StartMenu)
     *  can wire it to a button in its top bar. */
    public void resetBuild() {
        design.reset();
        resetFusionStruct();
        rebuildPartsList();
        refresh();
    }

    /** Returns the current conceptual builder model for MAB setup integration.
     *  The returned reference is the panel's live design — callers that
     *  need a stable snapshot should use {@link #exportBuilderDesign()}. */
    public NukeDesign getDesignForIntegration() {
        return design;
    }

    /** Returns a deep copy of the current builder design. Safe to retain
     *  across edits — subsequent player input will not mutate the copy.
     *  Use this when handing a design off to MAB at confirm time. */
    public NukeDesign exportBuilderDesign() {
        return copyOfBuilderDesign(design);
    }

    /** Seeds the builder with {@code source} as the new editable starting
     *  point. Slot selections and fusion sub-design state are copied
     *  field-for-field; the model→builder mapping here is lossless
     *  because both sides use the same {@link NukeDesign} type. If a
     *  caller has a non-builder design (e.g. a MAB {@code NukeDesign}),
     *  it must adapt to a builder design first — this method only
     *  accepts the closest editable representation. {@code null} resets
     *  the builder to its default placeholder.
     *
     *  <p>Change listeners fire once at the end of the load, not once
     *  per slot copy, so an AFTER preview will refresh exactly once.
     *
     *  <p>The exact previous design is the caller's responsibility to
     *  preserve externally for cancel — this panel only owns the
     *  editable working copy. */
    public void loadDesign(NukeDesign source) {
        if (source == null) {
            resetBuild();
            return;
        }
        suppressChangeNotifications = true;
        try {
            design.reset();
            resetFusionStruct();
            // Slot selections — exact copy.
            for (NukeSlot s : NukeSlot.ALL) {
                NukePart p = source.get(s);
                if (p != null) design.set(s, p);
            }
            // Fusion sub-design state — exact copy into both the model
            // and the FusionDetails UI struct so the controls reflect it.
            int stages = Math.max(1, Math.min(NukeDesign.MAX_FUSION_STAGES,
                    source.getFusionStageCount()));
            design.setFusionStageCount(stages);
            fusion.stageCount = stages;
            for (int i = 0; i < NukeDesign.MAX_FUSION_STAGES; i++) {
                String pusher  = source.getFusionPusher(i);
                String channel = source.getFusionChannelFiller(i);
                boolean spark  = source.getFusionSparkPlug(i);
                if (pusher  != null) { design.setFusionPusher(i, pusher);  fusion.pusher[i]        = pusher;  }
                if (channel != null) { design.setFusionChannelFiller(i, channel); fusion.channelFiller[i] = channel; }
                design.setFusionSparkPlug(i, spark);
                fusion.sparkPlug[i] = spark;
            }
            fusion.editingStage = 0;
            rebuildPartsList();
        } finally {
            suppressChangeNotifications = false;
        }
        refresh(); // single notification
    }

    /** Adds a listener fired at the end of every {@link #refresh()} —
     *  i.e. after any slot/part/fusion change. Safe to call from EDT.
     *  Returns the same {@code r} so callers can keep a handle for
     *  later removal. */
    public Runnable addDesignChangeListener(Runnable r) {
        if (r != null) designChangeListeners.add(r);
        return r;
    }

    /** Removes a previously-registered design change listener. */
    public void removeDesignChangeListener(Runnable r) {
        if (r != null) designChangeListeners.remove(r);
    }

    /** Binds the configured hard-drop key to a confirm action and adds
     *  a "&lt;hard-drop&gt; confirm" hint to the top-right key hint. Pass
     *  {@code null} to unbind. The host is responsible for guarding
     *  against double-invocation (e.g. via a one-shot flag) — this
     *  method binds the keystroke but does not enforce single-fire. */
    public void setConfirmKeyHandler(Runnable handler) {
        this.confirmKeyHandler = handler;
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        int code = Settings.get().getKeyHardDrop();
        KeyStroke ks = code == 0 ? null : KeyStroke.getKeyStroke(code, 0);
        if (ks != null) {
            if (handler == null) {
                // Restore the quarantine no-op for the hard-drop key
                // so a stale gameplay key can't leak through.
                String name = "builder.quarantine.hardDropRestore";
                im.put(ks, name);
                am.put(name, new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) { /* swallow */ }
                });
            } else {
                String name = "builder.confirm.hardDrop";
                im.put(ks, name);
                am.put(name, new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        Runnable h = confirmKeyHandler;
                        if (h != null) h.run();
                    }
                });
            }
        }
        refreshFocusChrome();
    }

    /** Deep-copy a builder {@link NukeDesign}, preserving every slot
     *  selection and the full fusion sub-design state. */
    private static NukeDesign copyOfBuilderDesign(NukeDesign src) {
        NukeDesign out = new NukeDesign();
        if (src == null) return out;
        for (NukeSlot s : NukeSlot.ALL) {
            NukePart p = src.get(s);
            if (p != null) out.set(s, p);
        }
        out.setFusionStageCount(src.getFusionStageCount());
        for (int i = 0; i < NukeDesign.MAX_FUSION_STAGES; i++) {
            String pusher  = src.getFusionPusher(i);
            String channel = src.getFusionChannelFiller(i);
            if (pusher  != null) out.setFusionPusher(i, pusher);
            if (channel != null) out.setFusionChannelFiller(i, channel);
            out.setFusionSparkPlug(i, src.getFusionSparkPlug(i));
        }
        return out;
    }

    /** Fire all registered design-change listeners. Exceptions in one
     *  listener don't stop the others. */
    private void fireDesignChanged() {
        if (suppressChangeNotifications) return;
        if (designChangeListeners.isEmpty()) return;
        for (Runnable r : new java.util.ArrayList<>(designChangeListeners)) {
            try { r.run(); } catch (RuntimeException ignored) {}
        }
    }

    /** Action row beneath the slot list. The Close button was removed
     *  (StartMenu provides a Back button in the top bar) and the Reset
     *  button was promoted to that same top bar — so this row is now
     *  empty and contributes nothing. Kept as a no-op spacer so the
     *  surrounding layout's preferred sizes don't shift. */
    private JComponent buildPaletteActions() {
        JPanel row = new JPanel();
        row.setBackground(DARK_BG);
        row.setPreferredSize(new Dimension(0, 0));
        return row;
    }

    /** A compact "what-is-currently-bolted-on" readout that fills the
     *  leftover space below the slot list. One row per slot, with the
     *  currently-selected part shown in slot-colour, or a dim "—" when
     *  empty / not applicable. Updated on every refresh(). */
    private JTextArea buildSummary;
    private JComponent buildSummaryPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(DARK_BG);

        JLabel head = new JLabel(" BUILD SUMMARY");
        head.setForeground(ACCENT);
        head.setFont(new Font("Monospaced", Font.BOLD, 11));
        head.setBorder(new EmptyBorder(6, 4, 2, 4));
        p.add(head, BorderLayout.NORTH);

        buildSummary = new JTextArea();
        buildSummary.setEditable(false);
        buildSummary.setFocusable(false);
        buildSummary.setLineWrap(true);
        buildSummary.setWrapStyleWord(true);
        buildSummary.setBackground(PANEL_BG);
        buildSummary.setForeground(TEXT_FG);
        buildSummary.setFont(new Font("Monospaced", Font.PLAIN, 11));
        buildSummary.setBorder(new javax.swing.border.CompoundBorder(
                new LineBorder(ACCENT_DIM, 1), new EmptyBorder(6, 8, 6, 8)));
        buildSummary.setText("(no parts selected)");
        p.add(buildSummary, BorderLayout.CENTER);
        return p;
    }

    /** Push the current selections into the build-summary readout. */
    private void refreshBuildSummary() {
        if (buildSummary == null) return;
        StringBuilder sb = new StringBuilder();
        for (NukeSlot s : NukeSlot.ALL) {
            NukePart p = design.get(s);
            String mark = (s == activeSlot) ? "\u25B6 " : "  ";
            String val;
            if (!slotApplicable(s))           val = "(n/a)";
            else if (p == NukePart.NONE)      val = "\u2014";
            else                               val = p.getName();
            // Truncate so long names don't blow the column width.
            if (val.length() > 22) val = val.substring(0, 21) + "\u2026";
            sb.append(mark).append(String.format("%-9s %s%n",
                    abbrevSlot(s.getName()), val));
        }
        buildSummary.setText(sb.toString().stripTrailing());
    }

    private static String abbrevSlot(String name) {
        // Keep it short so each row fits on one line at 10pt monospace
        // in the 180-px-wide left column.
        switch (name) {
            case "Weapon Configuration": return "Config";
            case "Fissile Material":     return "Fissile";
            case "Tamper / Reflector":   return "Tamper";
            case "Neutron Initiator":    return "Init";
            case "Implosion System":     return "Impl";
            case "Boost Gas":            return "Boost";
            case "Fusion Secondary":     return "Sec";
            case "Casing / Aeroshell":   return "Casing";
            case "Delivery System":      return "Delivery";
            case "Safety / PAL":         return "Safety";
            default:                     return name.length() > 8 ? name.substring(0, 8) : name;
        }
    }

    private void styleSlotButton(JButton b) {
        // Transparent fill so the slot row reads as a clean outlined
        // chip on the panel background — selection is communicated
        // through the border colour alone, never through a filled
        // background plate.
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBackground(PANEL_BG);
        b.setForeground(new Color(210, 220, 230));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false);
        // Non-focusable so the dialog’s window-scope arrow-key bindings
        // are never swallowed by button focus traversal.
        b.setFocusable(false);
        b.setBorder(new javax.swing.border.CompoundBorder(
                new LineBorder(ACCENT_DIM, 1), new EmptyBorder(4, 8, 4, 8)));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    }

    private void rebuildPartsList() {
        partsListPanel.removeAll();

        // ── Fusion sub-design row is active: show its options instead ──
        if (activeFusionGroup != null) {
            rebuildPartsListForFusion(activeFusionGroup);
            partsListPanel.revalidate();
            partsListPanel.repaint();
            return;
        }

        if (!slotApplicable(activeSlot)) {
            paletteHeading.setText(" " + activeSlot.getName().toUpperCase()
                    + " — N/A");
            JLabel msg = new JLabel(
                    "<html><div style='padding:8px;color:#cfd8e0;'>"
                    + "This slot is not used by the current configuration. "
                    + "Pick a different <b>Weapon Configuration</b> to enable it."
                    + "</div></html>");
            msg.setAlignmentX(Component.LEFT_ALIGNMENT);
            partsListPanel.add(msg);
            partsListPanel.revalidate();
            partsListPanel.repaint();
            return;
        }

        // Force the user to commit to a configuration first.
        if (activeSlot != NukeSlot.CONFIGURATION
                && design.get(NukeSlot.CONFIGURATION) == NukePart.NONE) {
            paletteHeading.setText(" SELECT A CONFIGURATION FIRST");
            JLabel msg = new JLabel(
                    "<html><div style='padding:8px;color:#ffaa46;'>"
                    + "You must choose a <b>Weapon Configuration</b> before "
                    + "selecting other components — different architectures "
                    + "(gun-type, implosion, two-stage, etc.) need different "
                    + "subsystems."
                    + "</div></html>");
            msg.setAlignmentX(Component.LEFT_ALIGNMENT);
            partsListPanel.add(msg);
            partsListPanel.revalidate();
            partsListPanel.repaint();
            return;
        }

        paletteHeading.setText("");  // headings now driven by refreshFocusChrome
        Color slotColor = SLOT_COLORS.getOrDefault(activeSlot.getId(), ACCENT_DIM);
        NukePart current = design.get(activeSlot);

        // Dim the parts list visually whenever the keyboard focus
        // is in another column, so it's obvious arrow keys won't move
        // parts here right now.
        boolean dimmed = focusedColumn != FocusCol.PARTS;

        // Per-part visibility filter: a few parts only make sense for one
        // specific weapon configuration and would otherwise mislead the
        // user into building physically nonsensical designs.
        NukePart cfg = design.get(NukeSlot.CONFIGURATION);
        String cfgName = (cfg == NukePart.NONE) ? "" : cfg.getName();
        boolean cfgIsSloika = cfgName.startsWith("Layer-cake");

        for (NukePart p : activeSlot.getOptions()) {
            if (activeSlot == NukeSlot.SECONDARY
                    && p.getName().startsWith("Spark-plug enhanced")
                    && cfgIsSloika) {
                continue; // spark-plug ignition is meaningless for layer-cake
            }
            JButton b = new JButton(p.getName());
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            b.setFont(new Font("SansSerif", Font.BOLD, 12));
            b.setFocusPainted(false);
            b.setFocusable(false); // arrow-key bindings must not be eaten
            // Transparent fill so the row reads as a clean outlined
            // chip on the panel background — selection is shown via
            // the border colour alone, never a filled plate. Border
            // thickness is held constant (idle 1 px / selected 2 px
            // with matching insets) so content never shifts.
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBackground(PANEL_BG);
            boolean selected = p == current;
            Color fg;
            if (dimmed) {
                fg = new Color(110, 120, 130);
            } else if (selected) {
                fg = new Color(245, 250, 255);
            } else {
                fg = new Color(210, 220, 230);
            }
            b.setForeground(fg);
            Color borderCol = dimmed ? new Color(60, 70, 85)
                                     : (selected ? ACCENT : ACCENT_DIM);
            int thick = (selected && !dimmed) ? 2 : 1;
            int pad   = (selected && !dimmed) ? 3 : 4;
            b.setBorder(new javax.swing.border.CompoundBorder(
                    new LineBorder(borderCol, thick),
                    new EmptyBorder(pad, pad + 4, pad, pad + 4)));
            b.addActionListener(e -> {
                // Click moves keyboard focus to this column too.
                focusedColumn = FocusCol.PARTS;
                design.set(activeSlot, p);
                showInfo(activeSlot, p);
                refresh();
            });
            partsListPanel.add(b);
            partsListPanel.add(Box.createVerticalStrut(3));
        }
        partsListPanel.revalidate();
        partsListPanel.repaint();
    }

    // ─────────────────────── UI: schematic (centre) ─────────────────

    private JComponent buildCenter() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(DARK_BG);
        wrap.setBorder(new EmptyBorder(4, 12, 6, 12));

        schematic = new SchematicPanel();
        schematic.setBorder(new LineBorder(ACCENT_DIM, 1));
        // Horizontal strip at the top of the window. Width is governed by
        // the parent layout; only the height needs to be fixed.
        schematic.setPreferredSize(new Dimension(0, 280));
        wrap.add(schematic, BorderLayout.CENTER);
        return wrap;
    }


    // ─────────────────────── UI: info sidebar (right) ───────────────

    private JComponent buildInfoSidebar() {
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setBackground(DARK_BG);
        // 12 px bottom inset so the WARNINGS panel ends at the same Y
        // as the slot bordered box (slot box is 12 px above root
        // bottom: 6 from palette wrap inset + 6 from leftCol vgap).
        wrap.setBorder(new EmptyBorder(6, 4, 12, 4));

        // The COMPONENT INFORMATION panel was removed at the user's
        // request — the right column now hosts only the WARNINGS
        // panel. infoArea is still instantiated (off-screen) so the
        // showInfo / showFusionInfo / autoFitTextArea callsites stay
        // valid without further surgery.
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFocusable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);

        JPanel warnPanel = new JPanel(new BorderLayout());
        warnPanel.setBackground(PANEL_BG);
        warnPanel.setBorder(new LineBorder(ACCENT_DIM, 1));
        JLabel wh = new JLabel("  DESIGN CHECKS");
        wh.setForeground(WARN_FG);
        wh.setFont(new Font("Monospaced", Font.BOLD, 11));
        wh.setBorder(new EmptyBorder(6, 6, 4, 6));
        warnPanel.add(wh, BorderLayout.NORTH);

        warningsArea = makeMultiline();
        warningsArea.setForeground(WARN_FG);
        warningsArea.setBorder(new EmptyBorder(4, 8, 8, 8));
        warnPanel.add(warningsArea, BorderLayout.CENTER);

        wrap.add(warnPanel, BorderLayout.CENTER);
        return wrap;
    }

    /** Right-hand stats column — compact numeric stats at the top, then
     *  a predicted-effects panel filling the rest of the column. The
     *  panel never uses a scroll bar (this is a keyboard-only UI with a
     *  limited key set); long content is wrapped by the text area and
     *  bounded by the available column height. */
    private JComponent buildStatsSidebar() {
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setBackground(DARK_BG);
        // 12 px bottom inset so the PREDICTED EFFECTS box ends at the
        // same Y as the slot bordered box (12 px above root bottom).
        wrap.setBorder(new EmptyBorder(6, 4, 12, 12));
        // Right column shrunk — the wide format strings have been
        // replaced by two-line stat blocks, and the freed width goes
        // to the centre (component info) column.
        wrap.setPreferredSize(new Dimension(330, 0));

        // ── Top: compact numeric stats. Sized to its preferred height.
        JPanel stats = new JPanel(new GridBagLayout());
        stats.setBackground(PANEL_BG);
        stats.setBorder(new LineBorder(ACCENT_DIM, 1));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 8, 2, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel sh = new JLabel("ESTIMATED PERFORMANCE");
        sh.setForeground(ACCENT);
        sh.setFont(new Font("Monospaced", Font.BOLD, 11));
        sh.setBorder(new EmptyBorder(4, 0, 4, 0));
        stats.add(sh, gc);

        gc.gridy++; yieldLabel      = makeStat("Yield: —");        stats.add(yieldLabel, gc);
        gc.gridy++; complexityLabel = makeStat("Complexity: —");   stats.add(complexityLabel, gc);
        gc.gridy++; radiusLabel     = makeStatArea("Severe-blast radius: —"); stats.add(radiusLabel, gc);
        gc.gridy++; analogLabel     = makeStatArea("Analog: —");   stats.add(analogLabel, gc);

        wrap.add(stats, BorderLayout.NORTH);

        // ── Bottom: predicted-effects panel filling the rest of the
        // column. (BUILD SUMMARY is intentionally NOT shown here — the
        // user rejected it; do not re-add. The buildSummaryPanel()
        // method is left in the source as dead code only because it is
        // referenced by tests/dev tooling.)
        JPanel effectsPanel = new JPanel(new BorderLayout());
        effectsPanel.setBackground(PANEL_BG);
        effectsPanel.setBorder(new LineBorder(ACCENT_DIM, 1));
        JLabel eh = new JLabel("  PREDICTED EFFECTS");
        eh.setForeground(ACCENT);
        eh.setFont(new Font("Monospaced", Font.BOLD, 11));
        eh.setBorder(new EmptyBorder(6, 6, 4, 6));
        effectsPanel.add(eh, BorderLayout.NORTH);

        effectsArea = makeMultiline();
        effectsArea.setBorder(new EmptyBorder(4, 8, 8, 8));
        effectsPanel.add(effectsArea, BorderLayout.CENTER);

        wrap.add(effectsPanel, BorderLayout.CENTER);
        return wrap;
    }

    private JTextArea makeMultiline() {
        JTextArea a = new JTextArea(3, 22);
        a.setEditable(false);
        a.setFocusable(false); // keep arrow-key bindings reaching the root pane
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBackground(PANEL_BG);
        a.setForeground(TEXT_FG);
        a.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return a;
    }

    private JLabel makeStat(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_FG);
        l.setFont(new Font("Monospaced", Font.PLAIN, 11));
        return l;
    }

    /** Like {@link #makeStat} but wraps long values across multiple
     *  lines so they can't push out of the right-hand column.
     *  Setting an explicit column count is critical: a JTextArea with
     *  {@code setLineWrap(true)} but no column hint reports a
     *  preferred width as if its whole text were on one line, which
     *  in turn forces the enclosing BorderLayout NORTH region to
     *  expand huge and hide whatever sits in CENTER (the PREDICTED
     *  EFFECTS panel). With columns set, getPreferredSize() correctly
     *  reports the wrapped-text height. */
    private JTextArea makeStatArea(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBackground(PANEL_BG);
        a.setForeground(TEXT_FG);
        a.setFont(new Font("SansSerif", Font.PLAIN, 11));
        a.setColumns(26);   // ≈ the right column's usable text width
        return a;
    }

    private void styleFooterButton(JButton b) {
        b.setBackground(BTN_BG);
        b.setForeground(ACCENT);
        b.setFocusPainted(false);
        b.setBorder(new LineBorder(ACCENT_DIM, 1));
    }

    // ─────────────────────── Configuration gating ───────────────────

    /**
     * Decides whether a slot is meaningful for the currently-selected
     * {@link NukeSlot#CONFIGURATION}. Slots that are not applicable are
     * hidden in the schematic, greyed out in the slot list, and skipped
     * by keyboard navigation.
     *
     * Until the user picks a configuration, only the CONFIGURATION slot
     * itself is selectable — this forces the user to commit to an
     * architecture before assembling components, which mirrors how real
     * weapon design proceeds.
     */
    private boolean slotApplicable(NukeSlot slot) {
        if (slot == NukeSlot.CONFIGURATION) return true;
        NukePart cfg = design.get(NukeSlot.CONFIGURATION);
        if (cfg == NukePart.NONE) return false;

        String c = cfg.getName();
        boolean isGun        = c.startsWith("Gun-type");
        boolean isImpl       = c.startsWith("Implosion");
        boolean isBoosted    = c.startsWith("Boosted");
        boolean isSloika     = c.startsWith("Layer-cake");
        boolean isTwoStage   = c.startsWith("Teller-Ulam");

        switch (slot.getId()) {
            case "fissile":   return true;
            case "tamper":    return true;
            case "initiator": return true;
            case "implosion": return !isGun;
            case "boost":     return isBoosted || isSloika || isTwoStage;
            case "secondary": return isSloika || isTwoStage;
            case "casing":
            case "delivery":  return true;
            case "safety":    return false;
            default:          return true;
        }
    }

    /**
     * Re-styles slot buttons (greyed if not applicable), clears parts in
     * slots that are no longer applicable, and snaps the active slot to
     * the first applicable one if the current active slot has just
     * become irrelevant.
     */
    private void updateSlotAvailability() {
        // Bright amber when the slots column has keyboard focus,
        // dim cyan otherwise. This prevents a stale yellow "selected"
        // border from sitting on the slots column after the user
        // moves focus over to the parts column — which read as
        // residue from the previous selection.
        Color slotActiveBorder   = focusedColumn == FocusCol.SLOTS
                ? new Color(255, 220, 80) : ACCENT_DIM;
        Color fusionActiveBorder = focusedColumn == FocusCol.SLOTS
                ? new Color(255, 220, 80) : new Color(140, 90, 170);
        for (NukeSlot s : NukeSlot.ALL) {
            JButton b = slotButtons.get(s);
            if (b == null) continue;
            boolean ok = slotApplicable(s);
            boolean isActive = (s == activeSlot && activeFusionGroup == null);
            b.setEnabled(ok);
            // All states share a transparent fill so the row never
            // looks like a filled highlight plate — only the border
            // colour / thickness communicates selection and state.
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBackground(PANEL_BG);
            if (!ok) {
                b.setForeground(new Color(95, 105, 115));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(new Color(60, 70, 85), 1),
                        new EmptyBorder(4, 8, 4, 8)));
                b.setToolTipText("Not applicable to current configuration");
            } else if (isActive) {
                // Selected: brighter text + thicker accent border.
                b.setForeground(new Color(245, 250, 255));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(slotActiveBorder, 2),
                        new EmptyBorder(3, 7, 3, 7)));
                b.setToolTipText(null);
            } else {
                b.setForeground(new Color(210, 220, 230));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(ACCENT_DIM, 1),
                        new EmptyBorder(4, 8, 4, 8)));
                b.setToolTipText(null);
            }
            // Drop any selection in slots that no longer apply
            if (!ok && design.get(s) != NukePart.NONE) {
                design.set(s, NukePart.NONE);
            }
        }
        // Style fusion sub-design buttons the same way as real slot
        // buttons. The "active" highlight tracks activeFusionGroup
        // instead of activeSlot. The rows are always present in the
        // column; when the configuration is not Two-stage they show
        // up greyed-out / locked so the user can see they exist.
        boolean fusionUnlocked = inFusionMode();
        for (Map.Entry<FusionGroup, JButton> e : fusionGroupButtons.entrySet()) {
            FusionGroup g = e.getKey();
            JButton b = e.getValue();
            boolean isActive = (g == activeFusionGroup);
            // Same transparent-fill treatment as the slot buttons:
            // selection is shown via border colour, not a fill plate.
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBackground(PANEL_BG);
            if (!fusionUnlocked) {
                // Locked: dim outlined chip + tooltip explaining how to unlock.
                b.setEnabled(false);
                b.setForeground(new Color(95, 105, 115));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(new Color(60, 70, 85), 1),
                        new EmptyBorder(4, 8, 4, 8)));
                b.setToolTipText("Unlocks for Two-stage thermonuclear configurations");
            } else if (isActive) {
                // Selected fusion row: bright text + thicker violet/yellow border.
                b.setEnabled(true);
                b.setForeground(new Color(240, 225, 255));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(fusionActiveBorder, 2),
                        new EmptyBorder(3, 7, 3, 7)));
                b.setToolTipText(null);
            } else {
                // Idle: violet outline marks them as members of the
                // SECONDARY family — visually grouped with that slot.
                b.setEnabled(true);
                b.setForeground(new Color(200, 175, 220));
                b.setBorder(new javax.swing.border.CompoundBorder(
                        new LineBorder(new Color(140, 90, 170), 1),
                        new EmptyBorder(4, 8, 4, 8)));
                b.setToolTipText(null);
            }
        }
        // If the active slot is no longer applicable, snap to a valid one
        if (!slotApplicable(activeSlot)) {
            for (NukeSlot s : NukeSlot.ALL) {
                if (slotApplicable(s)) { activeSlot = s; break; }
            }
        }
        // If a fusion group was active but the configuration is no
        // longer two-stage, drop back to a real slot.
        if (activeFusionGroup != null && !inFusionMode()) {
            activeFusionGroup = null;
        }
    }

    // ─────────────────────── State & refresh ────────────────────────

    private void setActiveSlot(NukeSlot s) {
        if (!slotApplicable(s)) {
            // Politely refuse — and steer the user toward configuration
            Toolkit.getDefaultToolkit().beep();
            if (design.get(NukeSlot.CONFIGURATION) == NukePart.NONE
                    && s != NukeSlot.CONFIGURATION) {
                s = NukeSlot.CONFIGURATION;
            } else {
                return;
            }
        }
        this.activeSlot = s;
        this.activeFusionGroup = null;
        // Selecting a slot moves keyboard focus to the slots column.
        focusedColumn = FocusCol.SLOTS;
        // If this slot still holds the implicit NONE placeholder, jump
        // to the first visible option so the user immediately sees a
        // concrete choice instead of an empty selection.
        if (design.get(s) == NukePart.NONE) {
            java.util.List<NukePart> opts = visibleParts(s);
            if (!opts.isEmpty()) {
                design.set(s, opts.get(0));
            }
        }
        rebuildSlotList();
        updateSlotAvailability();
        rebuildPartsList();
        showInfo(s, design.get(s));
        schematic.repaint();
        // Refill column headings + key hint after rebuildPartsList()
        // blanked paletteHeading. Without this the title for the parts
        // column (e.g. "SECONDARY › ...") stays empty until refresh()
        // runs for some other reason.
        refreshFocusChrome();
    }

    /** Pick a fusion sub-design group as the active row. The group's
     *  options are then listed in the parts column so the user can
     *  select one with the standard UP/DOWN keys. {@link #activeSlot}
     *  is forced to {@link NukeSlot#SECONDARY} so the schematic keeps
     *  highlighting the secondary stage that the group belongs to. */
    private void setActiveFusionGroup(FusionGroup g) {
        if (!inFusionMode()) {
            // The fusion sub-design only exists for two-stage configs.
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        this.activeSlot = NukeSlot.SECONDARY;
        this.activeFusionGroup = g;
        focusedColumn = FocusCol.SLOTS;
        rebuildSlotList();
        updateSlotAvailability();
        rebuildPartsList();
        showFusionInfo(g);
        schematic.repaint();
        refreshFocusChrome();
    }

    /** Populates the parts column with the option buttons for one
     *  fusion sub-design group. Mirrors the styling of the regular
     *  parts list so the two read as a single uniform menu system. */
    private void rebuildPartsListForFusion(FusionGroup g) {
        paletteHeading.setText("");  // headings now driven by refreshFocusChrome
        Color slotColor = SLOT_COLORS.getOrDefault(NukeSlot.SECONDARY.getId(), ACCENT_DIM);
        String current = currentFusionValue(g);
        boolean dimmed = focusedColumn != FocusCol.PARTS;

        // STAGE_SELECT only lists the stages that currently exist on
        // the device — no point offering "Edit Quaternary" on a 2- or
        // 3-stage build.
        String[] options = g.options;
        if (g == FusionGroup.STAGE_SELECT) {
            int n = Math.max(1, fusion.stageCount);
            String[] subset = new String[n];
            System.arraycopy(g.options, 0, subset, 0, n);
            options = subset;
        }

        for (String opt : options) {
            JButton b = new JButton(opt);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            b.setFont(new Font("SansSerif", Font.BOLD, 12));
            b.setFocusPainted(false);
            b.setFocusable(false);
            // Transparent fill — selection shown via border colour only.
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBackground(PANEL_BG);
            boolean selected = opt.equals(current);
            Color fg;
            if (dimmed) {
                fg = new Color(110, 120, 130);
            } else if (selected) {
                fg = new Color(245, 250, 255);
            } else {
                fg = new Color(210, 220, 230);
            }
            b.setForeground(fg);
            Color borderCol = dimmed ? new Color(60, 70, 85)
                                     : (selected ? ACCENT : ACCENT_DIM);
            int thick = (selected && !dimmed) ? 2 : 1;
            int pad   = (selected && !dimmed) ? 3 : 4;
            b.setBorder(new javax.swing.border.CompoundBorder(
                    new LineBorder(borderCol, thick),
                    new EmptyBorder(pad, pad + 4, pad, pad + 4)));
            b.addActionListener(e -> {
                focusedColumn = FocusCol.PARTS;
                applyFusionValue(g, opt);
                showFusionInfo(g);
                refresh();
            });
            partsListPanel.add(b);
            partsListPanel.add(Box.createVerticalStrut(3));
        }
    }

    /** Read the value currently assigned to a fusion sub-design group
     *  out of the {@link #fusion} struct. Per-stage groups read the
     *  array slot at {@code fusion.editingStage}. */
    private String currentFusionValue(FusionGroup g) {
        int s = clampedEditingStage();
        return switch (g) {
            case STAGE_SELECT -> stageEditLabel(s);
            case FUEL         -> fusion.fuel[s];
            case PUSHER       -> fusion.pusher[s];
            case CHANNEL      -> fusion.channelFiller[s];
            case SPARK_PLUG   -> fusion.sparkPlug[s] ? "Pu-239 rod"      : "(none)";
            case STAGES       -> switch (fusion.stageCount) {
                case 2  -> "3-stage";
                case 3  -> "4-stage";
                default -> "2-stage";
            };
        };
    }

    /** Write a value into the {@link #fusion} struct for one group.
     *  Per-stage groups write the array slot at
     *  {@code fusion.editingStage}. The FUEL group on stage 0 is also
     *  mirrored into {@link NukeSlot#SECONDARY} so the schematic
     *  painter and warnings logic continue to see a real secondary. */
    private void applyFusionValue(FusionGroup g, String value) {
        int s = clampedEditingStage();
        switch (g) {
            case STAGE_SELECT -> {
                if      (value.contains("Tertiary"))   fusion.editingStage = 1;
                else if (value.contains("Quaternary")) fusion.editingStage = 2;
                else                                   fusion.editingStage = 0;
                // Clamp to existing stages.
                if (fusion.editingStage >= fusion.stageCount) {
                    fusion.editingStage = fusion.stageCount - 1;
                }
            }
            case FUEL         -> {
                fusion.fuel[s] = value;
                if (s == 0) syncFuelToSecondarySlot();
            }
            case PUSHER       -> fusion.pusher[s] = value;
            case CHANNEL      -> fusion.channelFiller[s] = value;
            case SPARK_PLUG   -> fusion.sparkPlug[s] = "Pu-239 rod".equals(value);
            case STAGES       -> {
                if      (value.startsWith("3-stage")) fusion.stageCount = 2;
                else if (value.startsWith("4-stage")) fusion.stageCount = 3;
                else                                  fusion.stageCount = 1;
                // Snap the editing cursor back into the valid range
                // when the user collapses stages.
                if (fusion.editingStage >= fusion.stageCount) {
                    fusion.editingStage = fusion.stageCount - 1;
                }
            }
        }
    }

    /** Defensive read of {@link FusionDetails#editingStage} that
     *  clamps to the currently-existing stage range. */
    private int clampedEditingStage() {
        int s = fusion.editingStage;
        if (s < 0) s = 0;
        if (s >= fusion.stageCount) s = fusion.stageCount - 1;
        return s;
    }

    /** Human label for the stage-select dropdown row. */
    private static String stageEditLabel(int s) {
        return switch (s) {
            case 1  -> "Edit Tertiary";
            case 2  -> "Edit Quaternary";
            default -> "Edit Secondary";
        };
    }

    /** Pretty stage name (without the "Edit " prefix) for headers and
     *  info-panel breadcrumbs. */
    private static String stageName(int s) {
        return switch (s) {
            case 1  -> "Tertiary";
            case 2  -> "Quaternary";
            default -> "Secondary";
        };
    }

    /** Whether a fusion group is per-stage (its value is read/written
     *  through the editingStage index) vs. global to the whole device. */
    private static boolean isPerStageGroup(FusionGroup g) {
        return g == FusionGroup.FUEL
            || g == FusionGroup.PUSHER
            || g == FusionGroup.CHANNEL
            || g == FusionGroup.SPARK_PLUG;
    }

    /** Locate the NukePart in the SECONDARY slot whose name best matches
     *  the user's FUEL pick from the sub-builder, and write it into the
     *  design model. Keeps the schematic painter, build summary, warnings
     *  and historical-analog text working as before — they all still
     *  read {@code design.get(NukeSlot.SECONDARY)}. */
    private void syncFuelToSecondarySlot() {
        String f = fusion.fuel[0] == null ? "" : fusion.fuel[0];
        NukePart match = NukePart.NONE;
        for (NukePart p : NukeSlot.SECONDARY.getOptions()) {
            String n = p.getName();
            if (f.startsWith("Lithium-6")    && n.startsWith("Lithium-6"))    { match = p; break; }
            if (f.startsWith("Natural")      && n.startsWith("Natural"))      { match = p; break; }
        }
        design.set(NukeSlot.SECONDARY, match);
    }

    /** Render a description block for the active fusion sub-design
     *  group + currently-selected value into the right-hand info
     *  panel, following the same shape as {@link #showInfo}. Each
     *  option carries its own unique blurb (see
     *  {@link #fusionOptionDescription}) so the info panel never
     *  shows the same boilerplate twice. */
    private void showFusionInfo(FusionGroup g) {
        String current = currentFusionValue(g);
        StringBuilder sb = new StringBuilder();
        sb.append(g.name).append("  →  ").append(current).append("\n\n");
        sb.append("— The choice —\n");
        sb.append(g.description).append("\n\n");
        sb.append("— ").append(current).append(" —\n");
        sb.append(fusionOptionDescription(g, current)).append("\n\n");
        // STAGE_SELECT only lists stages that exist on the device.
        String[] opts = g.options;
        if (g == FusionGroup.STAGE_SELECT) {
            int n = Math.max(1, fusion.stageCount);
            String[] sub = new String[n];
            System.arraycopy(g.options, 0, sub, 0, n);
            opts = sub;
        }
        sb.append("All options:\n");
        for (String opt : opts) {
            sb.append("  ").append(opt.equals(current) ? "▶ " : "  ");
            sb.append(opt).append('\n');
        }
        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(0);
    }

    /** A short, unique educational paragraph for a particular value of a
     *  fusion sub-builder group. Lets the info panel show different
     *  text for every option instead of just repeating the group's
     *  generic description. */
    private String fusionOptionDescription(FusionGroup g, String opt) {
        switch (g) {
            case STAGE_SELECT: switch (opt) {
                case "Edit Secondary":
                    return "Switches the editor to the secondary fusion stage — " +
                           "the stage directly compressed by the fission primary's " +
                           "X-ray pulse. Its choices feed the design checks, historical " +
                           "analog and Castle Bravo synergy.";
                case "Edit Tertiary":
                    return "Switches the editor to the tertiary stage — added " +
                           "when the device is configured as 3-stage. Its U-238 " +
                           "jacket is the fast-fission contribution that defined " +
                           "Castle Bravo (1954) and dominates the fallout signature.";
                case "Edit Quaternary":
                    return "Switches the editor to the (hypothetical) quaternary " +
                           "stage — a fourth stage added on top of the tertiary. " +
                           "No real device has ever used one; the Soviet Tsar " +
                           "Bomba team explicitly stopped at three.";
            } break;
            case FUEL: switch (opt) {
                case "Lithium-6 deuteride (dry)":
                    return "Solid lithium-6 deuteride. The Li-6 absorbs a fast " +
                           "neutron and breeds tritium in-situ; the tritium then " +
                           "fuses with the deuterium. Storable at room " +
                           "temperature, light, and weaponizable — the canonical " +
                           "fuel of every modern thermonuclear weapon.";
                case "Natural LiD (Li-6 + Li-7)":
                    return "Cheaper unenriched LiD. The Castle Bravo (1954) " +
                           "designers assumed Li-7 would be inert; it wasn't. The " +
                           "unexpected Li-7 contribution overshot the predicted " +
                           "6 Mt yield by 2.5× (15 Mt actual) — the worst US " +
                           "radiological accident of the test era.";
            } break;
            case PUSHER: switch (opt) {
                case "U-238":
                    return "Depleted-uranium tamper. Cheap, very dense, and — " +
                           "crucially — fast-fissions in the 14-MeV neutron flux " +
                           "from D-T fusion. Typically contributes more than half " +
                           "the total yield of a 'dirty' thermonuclear weapon, but " +
                           "also the dominant fallout source.";
                case "Lead":
                    return "Inert Pb tamper. Heavy enough to confine the " +
                           "compressed fuel for a few extra nanoseconds without " +
                           "adding any fission yield. The basis of so-called " +
                           "'clean' designs (1958 Hardtack-Poplar, ~95 % fusion).";
                case "Tungsten":
                    return "Inert W tamper — even denser than lead and with a " +
                           "higher melting point, giving slightly better " +
                           "radiative confinement. Used in some compact modern " +
                           "warheads where every gram of pusher mass matters.";
            } break;
            case CHANNEL: switch (opt) {
                case "Polystyrene foam":
                    return "Plastic foam (CH) lining the radiation channel. " +
                           "Ablates evenly under the X-ray pulse from the primary, " +
                           "converting radiation into a smooth mechanical implosion " +
                           "of the secondary. The canonical Teller-Ulam choice.";
                case "Vacuum":
                    return "Empty channel — X-rays travel directly to the " +
                           "secondary's radiation case. Theoretically slightly " +
                           "more efficient (~5 %) but mechanically harder to " +
                           "hold open against the primary's blast pressure.";
            } break;
            case SPARK_PLUG: switch (opt) {
                case "Pu-239 rod":
                    return "A pencil-thin Pu-239 rod down the central axis of the " +
                           "secondary. When the surrounding fuel is compressed the " +
                           "rod itself goes super-critical and floods the LiD with " +
                           "neutrons — reliable ignition. Standard on every modern " +
                           "weapon.";
                case "(none)":
                    return "No central spark plug. The fuel must be heated to " +
                           "ignition entirely by radiation-driven implosion alone. " +
                           "Marginal in real designs — yield is typically only a " +
                           "fraction of the design intent.";
            } break;
            case STAGES: switch (opt) {
                case "2-stage":
                    return "One fission primary radiatively compresses one fusion " +
                           "secondary. The architecture of essentially every " +
                           "deployed strategic warhead since the late 1950s. " +
                           "Yield bounded mostly by the secondary's mass.";
                case "3-stage":
                    return "Wraps the secondary in a thick U-238 jacket. The " +
                           "14-MeV fusion neutrons fast-fission that jacket, " +
                           "adding a third (fission) stage that roughly doubles " +
                           "yield. Used by Castle Bravo (1954, 15 Mt) and Tsar " +
                           "Bomba's full-yield variant. Catastrophic fallout.";
                case "4-stage":
                    return "A hypothetical extrapolation: a second fusion stage " +
                           "ignited by the third stage's flux, then jacketed " +
                           "again. No working device has ever existed; the Soviet " +
                           "Tsar Bomba design team explicitly stopped at 3 stages " +
                           "because going further was 'no longer a weapon, only a " +
                           "contamination event.'";
            } break;
        }
        return "";
    }

    private void showInfo(NukeSlot slot, NukePart part) {
        StringBuilder sb = new StringBuilder();
        sb.append(slot.getName()).append("  →  ").append(part.getName()).append("\n\n");
        // Slot blurb only when no part is picked yet — once the user
        // has chosen a component, the part-specific text is what they
        // want to read; repeating the generic slot description on top
        // of every part is just visual noise.
        if (part == NukePart.NONE) {
            sb.append(slot.getDescription()).append("\n");
        } else {
            sb.append(part.getShortDescription()).append("\n\n");
            if (!part.getEducationalNotes().isEmpty()) {
                sb.append(part.getEducationalNotes());
            }
        }
        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(0);
    }

    private void refresh() {
        // Push the inline FUSION DESIGN choices into the model so yield,
        // mass and warnings reflect them. No-op for non-thermonuclear
        // configurations (NukeDesign ignores the values in that case).
        for (int i = 0; i < FusionDetails.MAX; i++) {
            design.setFusionPusher(i, fusion.pusher[i]);
            design.setFusionChannelFiller(i, fusion.channelFiller[i]);
            design.setFusionSparkPlug(i, fusion.sparkPlug[i]);
        }
        design.setFusionStageCount(fusion.stageCount);
        // Keep the SECONDARY slot in lock-step with the FUEL choice
        // from the sub-builder whenever we're in two-stage mode \u2014
        // the warnings/historical-analog/schematic painter all still
        // read design.get(SECONDARY) and would otherwise see NONE.
        if (inFusionMode()) {
            syncFuelToSecondarySlot();
        }

        // Configuration may have changed (e.g. user just picked a
        // two-stage design from the parts list) — rebuild the slot
        // column so the fusion sub-design rows appear or disappear.
        rebuildSlotList();
        updateSlotAvailability();
        rebuildPartsList();
        double kt = design.getEstimatedYieldKt();
        yieldLabel.setText("Yield:        " + formatYield(kt));
        complexityLabel.setText(String.format("Complexity:   %.1f / 25", design.getComplexityScore()));
        radiusLabel.setText(String.format("Blast radius: %.2f km (5 psi)", design.getDamageRadiusKm()));
        analogLabel.setText("Analog:       " + design.getHistoricalAnalog());
        effectsArea.setText(design.getEffectsSummary());

        List<String> warns = design.getWarnings();
        if (warns.isEmpty()) {
            warningsArea.setForeground(TEXT_FG);
            warningsArea.setText("(none — configuration is internally consistent)");
        } else {
            boolean fizzle = warns.stream().anyMatch(w -> w.startsWith("FIZZLE"));
            warningsArea.setForeground(fizzle ? DANGER_FG : WARN_FG);
            warningsArea.setText("• " + String.join("\n• ", warns));
        }
        // Warnings height changed — re-lay-out the centre column so the
        // info panel reclaims any space the warnings just vacated.
        Container warnPanel = warningsArea.getParent();
        if (warnPanel != null) {
            warnPanel.invalidate();
            Container host = warnPanel.getParent();
            if (host != null) { host.revalidate(); host.repaint(); }
        }
        schematic.repaint();
        refreshFocusChrome();
        refreshBuildSummary();

        // Long body-text areas (effects, warnings, analog, info) can blow
        // past the available column height once the user picks a megaton
        // configuration or a long historical analog. The dialog is
        // keyboard-only and deliberately has no scroll bars, so instead
        // we shrink the font on those areas until everything fits.
        // Deferred to invokeLater because the first refresh runs before
        // the parent layout has assigned pixel heights.
        SwingUtilities.invokeLater(() -> {
            autoFitTextArea(effectsArea,  9);
            autoFitTextArea(warningsArea, 9);
            autoFitTextArea(analogLabel,  9);
            autoFitTextArea(infoArea,     9);
        });
        // Notify embedded hosts that the design has changed. Fires once
        // per refresh; loadDesign() suppresses intermediate notifications.
        fireDesignChanged();
    }

    /** Shrink the font on a wrapping JTextArea until its preferred
     *  height (at the area's current width) fits inside the area's
     *  laid-out height. Caps at minPt so text never becomes unreadable.
     *  No-op until the parent layout has given the area a real size. */
    private static void autoFitTextArea(JTextArea a, int minPt) {
        if (a == null) return;
        int w = a.getWidth();
        int h = a.getHeight();
        if (w < 20 || h < 20) return; // not laid out yet
        // Start from a generous size and walk down. We rebuild the font
        // each iteration and ask the text area to re-measure.
        Font base = a.getFont();
        for (int pt = 12; pt >= minPt; pt--) {
            Font f = base.deriveFont((float) pt);
            a.setFont(f);
            // Force the area to re-measure at the column width.
            a.setSize(w, Short.MAX_VALUE);
            int needed = a.getPreferredSize().height;
            if (needed <= h) return; // fits
        }
        // Fell through at minPt — leave the smallest font and accept
        // that some clipping may still happen on absurdly long copy.
    }

    /** Single source of truth for "which column has the keyboard's
     *  attention right now" — updates column borders, headings, and
     *  the top-right key hint so the user can always see at a glance
     *  what UP/DOWN/LEFT/RIGHT will do. */
    private void refreshFocusChrome() {
        if (slotsBarOuter == null) return; // build not finished yet

        styleColumnFocus(slotsBarOuter,  slotsHeading,   "STAGE / SLOT",      focusedColumn == FocusCol.SLOTS);
        styleColumnFocus(listWrapOuter,  paletteHeading, partsHeadingText(),  focusedColumn == FocusCol.PARTS);
        // The slot/fusion buttons' active border colour also depends
        // on which column is focused (so an "out of focus" column
        // doesn't keep a bright residue from its previous selection),
        // so re-style them whenever focus changes.
        updateSlotAvailability();

        // Context-aware key hint. Always one short line, reads
        // left-to-right matching how the arrow keys are laid out on
        // the keyboard. When a host has bound a confirm key (the
        // hard-drop key), the hint also shows which key to press to
        // confirm the design.
        String navHint = switch (focusedColumn) {
            case SLOTS -> "\u2191\u2193 row    \u2190\u2192 column";
            case PARTS -> "\u2191\u2193 option \u2190\u2192 column";
        };
        StringBuilder text = new StringBuilder(navHint);
        if (confirmKeyHandler != null) {
            int code = Settings.get().getKeyHardDrop();
            String name = code == 0 ? "Hard drop"
                    : java.awt.event.KeyEvent.getKeyText(code);
            text.append("   ").append(name).append(" confirm");
        }
        text.append("   Esc close");
        keyHint.setText(text.toString());
    }

    private String partsHeadingText() {
        // The currently-selected option is already highlighted in the
        // list below, so don't repeat its name in the heading.
        if (activeFusionGroup != null) return activeFusionGroup.name.toUpperCase();
        return activeSlot.getName().toUpperCase();
    }

    /** Apply the focused-vs-unfocused chrome to a single column. */
    private void styleColumnFocus(JComponent body, JLabel heading, String headingText, boolean focused) {
        // The column outline stays a constant 1 px dim-cyan rectangle
        // at all times. Wrapping the column in an orange "focused"
        // border used to bleed across the full bottom edge — making
        // the slot column always look orange-bottomed and the parts
        // column's bottom edge mismatch the brighter cyan selection
        // colour of its active button. Focus is now communicated
        // exclusively through the heading marker (\u25B6) and through
        // the highlighted button inside the column.
        body.setBorder(new javax.swing.border.CompoundBorder(
                new LineBorder(ACCENT_DIM, 1),
                new EmptyBorder(6, 6, 6, 6)));
        body.setBackground(PANEL_BG);
        if (heading != null) {
            heading.setText((focused ? " \u25B6  " : "    ") + headingText);
            // Focused: glowing amber. Unfocused: still clearly legible
            // (cyan accent on panel grey).
            heading.setForeground(focused ? new Color(255, 200, 90) : ACCENT);
        }
    }

    private static String formatYield(double kt) {
        if (kt <= 0)       return "—";
        if (kt < 1)        return String.format("%.2f kt TNT-eq", kt);
        if (kt < 1000)     return String.format("%.1f kt TNT-eq", kt);
        return String.format("%.2f Mt TNT-eq", kt / 1000.0);
    }

    /**
     * Convenience opener used from menus. The launcher menu stays
     * visible behind the (modal, fullscreen) builder so the user can
     * see they're still inside the launcher; ESC simply disposes the
     * builder and focus returns to whatever owned it.
     */
    public static void showDialog(JFrame owner) {
        JDialog dlg = new JDialog(owner, "NUKE BUILDER // EDUCATIONAL DEMONSTRATION", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setResizable(true);
        NukeBuilderDialog form = new NukeBuilderDialog(dlg::dispose);
        dlg.setContentPane(form);
        Rectangle fs = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int w = Math.min(1400, fs.width  - 40);
        int h = Math.min( 900, fs.height - 40);
        dlg.setSize(w, h);
        dlg.setMinimumSize(new Dimension(960, 640));
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        if (owner != null) owner.toFront();
    }

    /** Embeddable factory for use inside e.g. the start menu's CardLayout. */
    public static NukeBuilderDialog createEmbedded(Runnable onBack) {
        return new NukeBuilderDialog(onBack);
    }

    // ════════════════════════════════════════════════════════════════
    // Keyboard navigation
    // ════════════════════════════════════════════════════════════════
    //  Left / Right → previous / next visible column
    //  Up   / Down  → previous / next item inside the focused column
    //  1..9         → jump to slot N (also focuses the slots column)
    //  Reset key    → reset all selections
    //  Esc          → close the dialog

    private void installKeyboardShortcuts() {
        // Bind on the panel itself (WHEN_IN_FOCUSED_WINDOW) so the
        // shortcuts work whether we're embedded in StartMenu's CardLayout
        // or wrapped in a JDialog by showDialog(). Using getRootPane()
        // here would NPE because a fresh JPanel has no root pane until
        // it's added to a window.
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        // Arrow-key interaction model (plus ENTER to confirm/focus the
        // highlighted column and ESC to close the dialog).
        bind(im, am, "prevItem",   KeyStroke.getKeyStroke("UP"),    e -> handleVertical(-1));
        bind(im, am, "nextItem",   KeyStroke.getKeyStroke("DOWN"),  e -> handleVertical(+1));
        bind(im, am, "prevHoriz",  KeyStroke.getKeyStroke("LEFT"),  e -> handleHorizontal(-1));
        bind(im, am, "nextHoriz",  KeyStroke.getKeyStroke("RIGHT"), e -> handleHorizontal(+1));
        bind(im, am, "confirm",    KeyStroke.getKeyStroke("ENTER"), e -> handleConfirm());
        bind(im, am, "close",      KeyStroke.getKeyStroke("ESCAPE"),e -> onClose.run());
        bindResetShortcut(im, am, "resetP1", Settings.get().getKeyReset());
        bindResetShortcut(im, am, "resetP2", Settings.get().getKeyP2Reset());

        // ── Input quarantine ──────────────────────────────────────────
        // Gameplay keys must NEVER act as confirm/cancel shortcuts in
        // the builder. We bind them to an explicit no-op so any held
        // key from the prior gameplay frame (hard drop, hold, rotate)
        // is consumed by this panel and does not bubble up to a parent
        // input handler that might re-interpret it. Builder navigation
        // keys (LEFT/RIGHT/UP/DOWN/ENTER/ESC, reset, slot digits) are
        // bound above and take precedence — quarantining only adds
        // bindings for keys that would otherwise be unhandled here.
        quarantineGameplayKeys(im, am);
    }

    /** Bind every gameplay key from {@link Settings} to a no-op consume
     *  action on this panel, so held gameplay input cannot leak into
     *  confirm/cancel handling while the builder is active. Skips key
     *  codes already bound by {@link #installKeyboardShortcuts()} (i.e.
     *  builder navigation keys) so we don't overwrite them. */
    private void quarantineGameplayKeys(InputMap im, ActionMap am) {
        Settings s = Settings.get();
        int[] codes = new int[] {
                // P1 movement / drop / rotate / hold
                s.getKeyMoveLeft(),  s.getKeyMoveRight(),
                s.getKeyMoveDown(),  s.getKeyMoveUp(),
                s.getKeyHardDrop(),
                s.getKeyRotateCW(),  s.getKeyRotateCCW(),
                s.getKeyHold(),      s.getKeyHoldAlt(),
                // P2 movement / drop / rotate / hold
                s.getKeyP2MoveLeft(),  s.getKeyP2MoveRight(),
                s.getKeyP2MoveDown(),  s.getKeyP2MoveUp(),
                s.getKeyP2HardDrop(),
                s.getKeyP2RotateCW(),  s.getKeyP2RotateCCW(),
                s.getKeyP2Hold(),
        };
        AbstractAction consume = new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { /* swallow */ }
        };
        int n = 0;
        for (int code : codes) {
            if (code == 0) continue;
            KeyStroke ks = KeyStroke.getKeyStroke(code, 0);
            if (ks == null) continue;
            // Don't overwrite already-bound builder navigation keys.
            if (im.get(ks) != null) continue;
            String name = "builder.quarantine." + (n++);
            im.put(ks, name);
            am.put(name, consume);
            // Also consume the key-released variant — some input
            // systems poll on release; a stale up-event must not fire
            // confirm on an embedded host.
            KeyStroke ksRel = KeyStroke.getKeyStroke(code, 0, true);
            if (ksRel != null && im.get(ksRel) == null) {
                String relName = name + ".rel";
                im.put(ksRel, relName);
                am.put(relName, consume);
            }
        }
    }

    /** UP / DOWN router: moves the cursor inside whichever column is
     *  focused. The fusion sub-design is treated as extra rows in the
     *  slots column, so no special FUSION case is needed any more. */
    private void handleVertical(int delta) {
        moveWithinColumn(delta);
    }

    /** LEFT / RIGHT router: switches between the two visible columns
     *  (slots ↔ parts). Same behaviour whether or not a fusion
     *  sub-design row is currently active. */
    private void handleHorizontal(int delta) {
        moveColumn(delta);
    }

    private void handleConfirm() {
        if (focusedColumn == FocusCol.SLOTS) {
            moveColumn(+1);
        } else {
            refreshFocusChrome();
        }
    }

    private static void bind(InputMap im, ActionMap am, String key,
                             KeyStroke ks, java.util.function.Consumer<java.awt.event.ActionEvent> action) {
        im.put(ks, key);
        am.put(key, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.accept(e); }
        });
    }

    private void bindResetShortcut(InputMap im, ActionMap am, String key, int keyCode) {
        if (keyCode == 0) return;
        bind(im, am, key, KeyStroke.getKeyStroke(keyCode, 0), e -> resetBuild());
    }

    /** LEFT / RIGHT — flip the keyboard focus between the two columns
     *  (slots ↔ parts). Wraps at the ends. */
    private void moveColumn(int delta) {
        FocusCol next = (focusedColumn == FocusCol.SLOTS) ? FocusCol.PARTS : FocusCol.SLOTS;
        if (delta == 0 || next == focusedColumn) return;
        focusedColumn = next;
        rebuildPartsList();    // re-skin parts (dimming follows focus)
        rebuildSlotList();     // same for slots column
        updateSlotAvailability();
        refreshFocusChrome();
    }

    /** UP / DOWN — move the cursor inside whichever column has focus.
     *  In SLOTS this walks the combined list of visible slots + fusion
     *  sub-design rows (when applicable). In PARTS it walks the
     *  options for whichever row is currently active — either the
     *  parts of a real slot, or the values of a fusion group. */
    private void moveWithinColumn(int delta) {
        switch (focusedColumn) {
            case SLOTS -> cycleSlotInternal(delta);
            case PARTS -> cyclePartInternal(delta);
        }
    }

    /** Walk the combined slot column. The list of selectable rows is
     *  the visible {@link NukeSlot}s, optionally followed by the four
     *  {@link FusionGroup}s (when the configuration is two-stage).
     *  Wraps at the ends. */
    private void cycleSlotInternal(int delta) {
        java.util.List<Object> rows = slotRows();
        if (rows.isEmpty()) return;
        int idx = currentSlotRowIndex(rows);
        int n   = rows.size();
        idx = ((idx + delta) % n + n) % n;
        Object next = rows.get(idx);
        if (next instanceof NukeSlot s) {
            setActiveSlot(s);
        } else if (next instanceof FusionGroup g) {
            setActiveFusionGroup(g);
        }
    }

    /** All selectable rows in the slot column, in display order:
     *  applicable {@link NukeSlot}s first, then {@link FusionGroup}s
     *  when the configuration is two-stage. SECONDARY is hidden in
     *  fusion mode (its role is delegated to the FUEL fusion group). */
    private java.util.List<Object> slotRows() {
        java.util.List<Object> out = new java.util.ArrayList<>();
        boolean fusion = inFusionMode();
        for (NukeSlot s : NukeSlot.ALL) {
            if (s == NukeSlot.SECONDARY && fusion) continue;
            if (slotApplicable(s)) out.add(s);
        }
        if (fusion) {
            for (FusionGroup g : FusionGroup.values()) {
                // Mirror the visibility rules from rebuildSlotList()
                // so keyboard navigation never lands on a row that
                // isn't actually rendered.
                if (g == FusionGroup.STAGE_SELECT && this.fusion.stageCount <= 1) continue;
                out.add(g);
            }
        }
        return out;
    }

    /** Index of the current selection inside {@link #slotRows()}. */
    private int currentSlotRowIndex(java.util.List<Object> rows) {
        Object current = (activeFusionGroup != null) ? activeFusionGroup : activeSlot;
        int idx = rows.indexOf(current);
        return idx < 0 ? 0 : idx;
    }

    /** Parts visible in the current configuration. Mirrors the same
     *  filter used by rebuildPartsList(): keyboard cycling must not
     *  land on parts that are hidden from view. */
    private java.util.List<NukePart> visibleParts(NukeSlot slot) {
        NukePart cfg = design.get(NukeSlot.CONFIGURATION);
        String cfgName = (cfg == NukePart.NONE) ? "" : cfg.getName();
        boolean cfgIsSloika = cfgName.startsWith("Layer-cake");
        java.util.List<NukePart> out = new java.util.ArrayList<>();
        for (NukePart p : slot.getOptions()) {
            if (slot == NukeSlot.SECONDARY
                    && p.getName().startsWith("Spark-plug enhanced")
                    && cfgIsSloika) continue;
            out.add(p);
        }
        return out;
    }

    /** UP / DOWN inside the parts column. If the current row is a
     *  fusion sub-design group, walk through its options instead. */
    private void cyclePartInternal(int delta) {
        if (activeFusionGroup != null) {
            String[] opts = activeFusionGroup.options;
            int n = opts.length;
            if (n == 0) return;
            int idx = 0;
            String cur = currentFusionValue(activeFusionGroup);
            for (int k = 0; k < n; k++) if (opts[k].equals(cur)) { idx = k; break; }
            idx = ((idx + delta) % n + n) % n;
            applyFusionValue(activeFusionGroup, opts[idx]);
            showFusionInfo(activeFusionGroup);
            refresh();
            return;
        }

        if (!slotApplicable(activeSlot)) return;
        java.util.List<NukePart> opts = visibleParts(activeSlot);
        int n = opts.size();
        if (n == 0) return;
        int idx = opts.indexOf(design.get(activeSlot));
        if (idx < 0) idx = 0;
        idx = ((idx + delta) % n + n) % n;
        NukePart picked = opts.get(idx);
        design.set(activeSlot, picked);
        showInfo(activeSlot, picked);
        refresh();
    }

    /** True when the current configuration supports a fusion
     *  sub-design (i.e. the four extra "Fusion: …" rows should appear
     *  in the slot list). */
    private boolean inFusionMode() {
        NukePart cfg = design.get(NukeSlot.CONFIGURATION);
        if (cfg == NukePart.NONE) return false;
        return cfg.getName().startsWith("Teller-Ulam");
    }

    /** Restore the fusion sub-design choices to their initial defaults
     *  so the UI matches the model after a Reset. */
    private void resetFusionStruct() {
        for (int i = 0; i < FusionDetails.MAX; i++) {
            fusion.fuel[i]          = "Lithium-6 deuteride (dry)";
            fusion.pusher[i]        = "U-238";
            fusion.channelFiller[i] = "Polystyrene foam";
            fusion.sparkPlug[i]     = (i == 0);
        }
        fusion.stageCount    = 1;
        fusion.editingStage  = 0;
        activeFusionGroup    = null;
        focusedColumn        = FocusCol.SLOTS;
    }

    /** Linear blend from a to b. t=0 returns a, t=1 returns b. */
    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed()   * (1 - t) + b.getRed()   * t);
        int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl= (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(r, g, bl);
    }

    // ════════════════════════════════════════════════════════════════
    // Schematic painting
    // ════════════════════════════════════════════════════════════════

    /**
     * Draws a stylised cross-section of the assembled device as a tall,
     * narrow missile silhouette with leader-line labels alternating on
     * the left and right side. Designed to be readable rather than
     * geometrically faithful.
     *
     *      ┌─ CONFIG BANNER (top) ──────────────────────────┐
     *      │                                                │
     *      │              [nose]                            │
     *      │   ─label◄────[primary sphere]────►label─       │
     *      │                  (rings)                       │
     *      │   ─label◄────[secondary cylinder]──►label─     │
     *      │              [tail / safety]                   │
     *      │                                                │
     *      └────────────────────────────────────────────────┘
     */
    private final class SchematicPanel extends JPanel {

        private final Map<NukeSlot, Shape> hitShapes = new LinkedHashMap<>();
        private final Map<NukeSlot, Shape> labelHitShapes = new LinkedHashMap<>();
        private final java.util.List<LabelAnchor> labels = new java.util.ArrayList<>();

        // Per-fusion-stage geometry captured during draw so the
        // sub-design highlight can light up the *specific* part of
        // the *specific* stage the user is currently editing. Index
        // 0 = secondary, 1 = tertiary, 2 = quaternary. Any null
        // entries are simply skipped.
        private final Shape[]       stageOuterShape = new Shape[FusionDetails.MAX];
        private final Rectangle2D[] stageFoamRect   = new Rectangle2D[FusionDetails.MAX];
        private final Rectangle2D[] stagePusherRect = new Rectangle2D[FusionDetails.MAX];
        private final Rectangle2D[] stageFuelRect   = new Rectangle2D[FusionDetails.MAX];
        private final Rectangle2D[] stagePlugRect   = new Rectangle2D[FusionDetails.MAX];

        /** Outline of the outer aeroshell, used as the CASING hit-shape. */
        private Shape envelopeShape;

        /** Virtual cross-axis width of the rotated drawing surface. */
        private int virtW;

        /** Consistent margin (px) on every side at the device's largest dimension. */
        private static final int DEV_MARGIN = 22;

        /** Where a leader line should attach to a region's outline. */
        private static final class LabelAnchor {
            final NukeSlot slot; final Point pivot; final boolean rightSide;
            LabelAnchor(NukeSlot s, Point pivot, boolean rightSide) {
                this.slot = s; this.pivot = pivot; this.rightSide = rightSide;
            }
        }

        SchematicPanel() {
            setBackground(new Color(6, 9, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    // Painting is rotated 90° clockwise so the device runs
                    // horizontally. Hit-shapes are stored in the original
                    // (vertical) coord space, so undo the rotation here.
                    int panelW = getWidth();
                    Point p = new Point(e.getY(), panelW - e.getX());
                    NukeSlot picked = null;
                    NukeSlot[] arr = hitShapes.keySet().toArray(new NukeSlot[0]);
                    // Inner / smaller shapes drawn last → win clicks
                    for (int i = arr.length - 1; i >= 0; i--) {
                        Shape s = hitShapes.get(arr[i]);
                        if (s != null && s.contains(p)) { picked = arr[i]; break; }
                    }
                    if (picked != null) setActiveSlot(picked);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hitShapes.clear();
            labelHitShapes.clear();
            labels.clear();
            for (int i = 0; i < FusionDetails.MAX; i++) {
                stageOuterShape[i] = null;
                stageFoamRect[i]   = null;
                stagePusherRect[i] = null;
                stageFuelRect[i]   = null;
                stagePlugRect[i]   = null;
            }

            // Rotate the entire painting 90° clockwise so the device
            // — originally drawn as a tall vertical missile — appears
            // horizontal across the top of the window. Inside this
            // transform, virtual width = panelHeight and virtual height
            // = panelWidth.
            int panelW = getWidth(), panelH = getHeight();
            g.translate(panelW, 0);
            g.rotate(Math.PI / 2.0);

            int w = panelH;       // virtual width  (was panel height)
            int h = panelW;       // virtual height (was panel width)

            // ── faint background grid (subtle, two-tone) ──
            g.setColor(new Color(14, 22, 34));
            for (int x = 0; x < w; x += 30) g.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 30) g.drawLine(0, y, w, y);
            // major lines every 5 cells, slightly brighter
            g.setColor(new Color(20, 32, 48));
            for (int x = 0; x < w; x += 150) g.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 150) g.drawLine(0, y, w, y);

            // Until a configuration is chosen, draw nothing else — the
            // left-hand menu and right-hand info panel guide the user.
            if (design.get(NukeSlot.CONFIGURATION) == NukePart.NONE) {
                g.dispose();
                return;
            }

            // ════════════════════════════════════════════════════════
            // Architecture-specific device rendering (no overlay text).
            // ════════════════════════════════════════════════════════
            virtW = w;
            int devTop = DEV_MARGIN;
            int devBot = h - DEV_MARGIN;
            int cx     = w / 2;

            String cfg = design.get(NukeSlot.CONFIGURATION).getName();
            if (cfg.startsWith("Gun-type")) {
                drawGunType(g, cx, devTop, devBot);
            } else if (cfg.startsWith("Implosion")) {
                drawImplosion(g, cx, devTop, devBot, false);
            } else if (cfg.startsWith("Boosted")) {
                drawImplosion(g, cx, devTop, devBot, true);
            } else if (cfg.startsWith("Layer-cake")) {
                drawSloika(g, cx, devTop, devBot);
            } else if (cfg.startsWith("Teller-Ulam")) {
                if (fusion.stageCount >= 2) {
                    drawMultiStage(g, cx, devTop, devBot, fusion.stageCount + 1);
                } else {
                    drawTellerUlam(g, cx, devTop, devBot);
                }
            } else {
                drawTellerUlam(g, cx, devTop, devBot);
            }

            // ── Active-slot / sub-design highlight ──
            // Sub-design (fusion sub-designer) highlight takes
            // precedence whenever the user is editing a fusion group:
            //   • An "overarching" highlight outlines the entire stage
            //     capsule (secondary / tertiary / quaternary) the user
            //     is editing, in a soft purple matching the SUB-DESIGN
            //     section header.
            //   • A focused highlight in the standard ACCENT cyan
            //     outlines the specific part inside that stage that
            //     corresponds to the active fusion group (FUEL,
            //     PUSHER, CHANNEL, SPARK_PLUG).
            // STAGE_SELECT and STAGES are stage-scoped settings, not
            // part-scoped, so for those we only draw the overarching
            // stage highlight.
            if (inFusionMode() && activeFusionGroup != null) {
                int stageIdx = clampedEditingStage();
                Shape stageShape = (stageIdx >= 0 && stageIdx < FusionDetails.MAX)
                        ? stageOuterShape[stageIdx] : null;
                if (stageShape != null) {
                    g.setStroke(new BasicStroke(2.0f));
                    g.setColor(new Color(180, 90, 200, 220));
                    g.draw(stageShape);
                }
                Rectangle2D partRect = null;
                switch (activeFusionGroup) {
                    case FUEL       -> partRect = stageFuelRect[stageIdx];
                    case PUSHER     -> partRect = stagePusherRect[stageIdx];
                    case CHANNEL    -> partRect = stageFoamRect[stageIdx];
                    case SPARK_PLUG -> partRect = stagePlugRect[stageIdx];
                    default         -> { /* STAGE_SELECT, STAGES — stage-scoped only */ }
                }
                if (partRect != null) {
                    g.setStroke(new BasicStroke(2.4f));
                    g.setColor(ACCENT);
                    g.draw(partRect);
                }
            } else {
                Shape act = hitShapes.get(activeSlot);
                if (act != null && activeSlot != NukeSlot.CONFIGURATION) {
                    g.setStroke(new BasicStroke(2.4f));
                    g.setColor(ACCENT);
                    g.draw(act);
                }
            }

            g.dispose();
        }

        private Ellipse2D circle(double cx, double cy, double r) {
            return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        }

        // ════════════════════════════════════════════════════════════
        // Architecture-specific renderers.
        //
        // Each one paints CASING / SAFETY / DELIVERY plus the
        // architecture-specific guts. They share a "stubby bomb" or
        // "tall missile" outer outline depending on the delivery
        // vehicle, but the *internal* geometry is what makes a
        // gun-type look like a gun, an implosion bomb look like a
        // sphere with shaped charges, etc.
        // ════════════════════════════════════════════════════════════

        /** Wide, stubby silhouette (Little Boy / Fat Man bombs). */
        private int[] stubbyBody(int cx, int devTop, int devBot) {
            int devH  = devBot - devTop;
            // Use the full available cross-axis (minus the standard
            // device margin) so the schematic visually fills the strip.
            int devW  = Math.min(virtW - 2 * DEV_MARGIN, devH / 2);
            return new int[]{ devW, 0, 0 };
        }

        /** Tall, narrow silhouette (RV / cruise / artillery). */
        private int[] slimBody(int cx, int devTop, int devBot) {
            int devH  = devBot - devTop;
            int devW  = Math.min((int) ((virtW - 2 * DEV_MARGIN) * 0.78), devH / 4);
            return new int[]{ devW, 0, 0 };
        }

        /**
         * Pick a body shape. The delivery slot has been removed, so all
         * architectures that previously varied with delivery now default
         * to the wider stubby silhouette.
         */
        private int[] bodyForDelivery(int cx, int devTop, int devBot) {
            return stubbyBody(cx, devTop, devBot);
        }

        /**
         * Paint the outer aeroshell. The silhouette is dispatched on the
         * casing choice — a Fat-Man bulb looks nothing like an RV cone or
         * an artillery shell. Returns the inner casing rectangle that
         * the inner-component renderers should fit inside.
         */
        private Rectangle2D paintEnvelope(Graphics2D g, int cx, int devTop, int devBot,
                                          int devW, int noseH, int tailH) {
            String c = design.get(NukeSlot.CASING).getName();
            if      (c.startsWith("Mk-III"))       return paintEnvelopeMk3(g, cx, devTop, devBot);
            else if (c.startsWith("Streamlined"))  return paintEnvelopeStreamlined(g, cx, devTop, devBot, devW, noseH, tailH);
            else if (c.startsWith("Re-entry"))     return paintEnvelopeRV(g, cx, devTop, devBot);
            else if (c.startsWith("Cylindrical"))  return paintEnvelopeShell(g, cx, devTop, devBot);
            else if (c.startsWith("Radiation"))    return paintEnvelopeStreamlined(g, cx, devTop, devBot, devW, noseH, tailH);
            else                                   return paintEnvelopeStreamlined(g, cx, devTop, devBot, devW, noseH, tailH);
        }

        // ── Streamlined gravity-bomb envelope (default — fins, ogive nose) ──
        private Rectangle2D paintEnvelopeStreamlined(Graphics2D g, int cx,
                int devTop, int devBot, int devW, int noseH, int tailH) {
            int leftX  = cx - devW / 2;
            int rightX = cx + devW / 2;
            Path2D delivery = new Path2D.Double();
            delivery.moveTo(leftX, devTop + noseH);
            delivery.curveTo(leftX, devTop + noseH * 0.2,
                             cx - 10, devTop, cx, devTop);
            delivery.curveTo(cx + 10, devTop,
                             rightX, devTop + noseH * 0.2,
                             rightX, devTop + noseH);
            delivery.lineTo(rightX, devBot - tailH);
            delivery.lineTo(rightX, devBot);
            delivery.lineTo(leftX, devBot);
            delivery.lineTo(leftX, devBot - tailH);
            delivery.closePath();
            envelopeShape = delivery;
            paintRegion(g, NukeSlot.DELIVERY, delivery, true);
            anchorLabel(NukeSlot.DELIVERY,
                    new Point(rightX, devBot - tailH / 2), true);

            g.setColor(new Color(40, 60, 90));
            int finW = 22;
            g.fillPolygon(
                    new int[]{leftX, leftX - finW, leftX - finW, leftX},
                    new int[]{devBot - tailH, devBot - 6, devBot, devBot}, 4);
            g.fillPolygon(
                    new int[]{rightX, rightX + finW, rightX + finW, rightX},
                    new int[]{devBot - tailH, devBot - 6, devBot, devBot}, 4);

            int casePad = 10;
            return new Rectangle2D.Double(
                    leftX + casePad, devTop + noseH + 4,
                    devW - 2 * casePad, devBot - tailH - 6 - (devTop + noseH + 4));
        }

        // ── Mk-III "Fat Man" — bulbous ellipsoid with riveted plates and box-fin tail ──
        //
        // Geometry (top → bottom inside the device band):
        //
        //          ╭───────────╮          ← bulb (near-spherical)
        //         │             │
        //         │   bay       │
        //         │             │
        //          ╰─────┬─────╯          ← bulb base
        //               ║║║              ← short tail collar
        //          ┌────╫────┐           ← fin shroud (square box w/ X bracing)
        //          │ \ ║║║ / │
        //          │  ╲╫╫╫╱  │
        //          └─────────┘
        //
        // The bulb + collar + fin shroud are combined into a single Area
        // so the silhouette is one continuous outline (no internal edges
        // poking into the oval). All leader-line anchors are placed on
        // the right edge of that silhouette so the pointers always land
        // on the actual visible component.
        private Rectangle2D paintEnvelopeMk3(Graphics2D g, int cx, int devTop, int devBot) {
            int devH = devBot - devTop;

            // Reserve vertical space for the fin assembly at the tail.
            int finBoxH  = Math.max(40, devH / 8);
            int collarH  = 14;                 // short stub between bulb and fins

            // The bulb itself fills the rest. Mk-III "Fat Man" was a
            // slightly elongated ellipsoid — taller than it was wide.
            int bulbBudget = devH - finBoxH - collarH - 10;
            int bulbH = Math.min(330, bulbBudget);
            int bulbW = (int) (bulbH * 0.78);   // elongated vertically
            int bulbTop = devTop + (bulbBudget - bulbH) / 2 + 4;
            int bulbLeft = cx - bulbW / 2;
            int bulbRight = cx + bulbW / 2;

            Ellipse2D bulb = new Ellipse2D.Double(bulbLeft, bulbTop, bulbW, bulbH);

            // Collar: short cylindrical stub from base of bulb down to fins.
            int collarW = bulbW / 4;
            int collarTop = bulbTop + bulbH - 6;
            int collarBot = collarTop + collarH;
            Rectangle2D collar = new Rectangle2D.Double(
                    cx - collarW / 2.0, collarTop, collarW, collarBot - collarTop);

            // Fin shroud: square box, slightly narrower than the bulb.
            int finBoxW = (int) (bulbW * 0.78);
            int finTop = collarBot - 2;        // slight overlap so it joins
            Rectangle2D finShroud = new Rectangle2D.Double(
                    cx - finBoxW / 2.0, finTop, finBoxW, finBoxH);

            // ── Combined silhouette (bulb + collar + fin shroud) ──
            Area silhouette = new Area(bulb);
            silhouette.add(new Area(collar));
            silhouette.add(new Area(finShroud));
            envelopeShape = silhouette;

            paintRegion(g, NukeSlot.DELIVERY, silhouette, true);

            // ── Specular highlight — a soft radial-gradient bright spot
            //    near the upper-left of the bulb so the casing reads as a
            //    polished 3D ellipsoid rather than a flat coloured blob.
            {
                java.awt.Paint oldPaint = g.getPaint();
                java.awt.Shape oldClip = g.getClip();
                g.setClip(silhouette);
                float gx = bulbLeft + bulbW * 0.28f;
                float gy = bulbTop  + bulbH * 0.22f;
                float gr = Math.max(bulbW, bulbH) * 0.55f;
                g.setPaint(new java.awt.RadialGradientPaint(
                        gx, gy, gr,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{
                            new Color(255, 255, 255, 70),
                            new Color(255, 255, 255, 18),
                            new Color(0, 0, 0, 0)
                        }));
                g.fill(silhouette);
                // Subtle terminator shadow on the lower-right.
                float sx = bulbLeft + bulbW * 0.78f;
                float sy = bulbTop  + bulbH * 0.78f;
                g.setPaint(new java.awt.RadialGradientPaint(
                        sx, sy, gr,
                        new float[]{0f, 1f},
                        new Color[]{ new Color(0, 0, 0, 60), new Color(0, 0, 0, 0) }));
                g.fill(silhouette);
                g.setPaint(oldPaint);
                g.setClip(oldClip);
            }

            // ── Decorative riveted plate seams (only on the bulb) ──
            g.setStroke(new BasicStroke(0.8f));
            g.setColor(new Color(70, 95, 130));
            for (int i = 1; i < 6; i++) {
                int y = bulbTop + bulbH * i / 6;
                double dy = (y - (bulbTop + bulbH / 2.0)) / (bulbH / 2.0);
                int half = (int) (Math.sqrt(Math.max(0, 1 - dy * dy)) * (bulbW / 2.0));
                g.drawLine(cx - half + 2, y, cx + half - 2, y);
            }

            // ── Fin shroud detail: X bracing + vertical centre fin ──
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(new Color(40, 60, 90));
            // box outline
            g.draw(finShroud);
            // diagonals
            g.drawLine((int) finShroud.getX(),    (int) finShroud.getY(),
                       (int) finShroud.getMaxX(), (int) finShroud.getMaxY());
            g.drawLine((int) finShroud.getMaxX(), (int) finShroud.getY(),
                       (int) finShroud.getX(),    (int) finShroud.getMaxY());
            // vertical centre fin
            g.drawLine(cx, (int) finShroud.getY(), cx, (int) finShroud.getMaxY());

            // ── Anchor the DELIVERY label at the right edge of the bulb,
            //    where the silhouette is unambiguously visible.
            anchorLabel(NukeSlot.DELIVERY,
                    new Point(bulbRight - 4, bulbTop + bulbH / 2 + bulbH / 4), true);

            // ── Inner bay: an oval-friendly rectangle inscribed in the bulb.
            //    Width = ~70% of bulb (so corners fit inside the curve).
            //    Vertical: from ~12% down to ~92% of bulb height.
            double bayW = bulbW * 0.70;
            double bayH = bulbH * 0.80;
            double bayX = cx - bayW / 2;
            double bayY = bulbTop + bulbH * 0.10;
            return new Rectangle2D.Double(bayX, bayY, bayW, bayH);
        }

        // ── Re-entry vehicle — sharp cone with ablative heat shield ──
        // The cone has been shortened (and widened) so the inner bay is
        // large enough for the implosion sphere to remain readable.
        private Rectangle2D paintEnvelopeRV(Graphics2D g, int cx, int devTop, int devBot) {
            int devH = devBot - devTop;
            int devW = Math.min(220, (int) (devH * 0.55));
            int leftX = cx - devW / 2, rightX = cx + devW / 2;
            int baseY = devBot - 10;
            // Apex is pushed down ~25% so the cone is shorter and wider
            // — there is far more interior area for the components.
            int apexY = devTop + (int) (devH * 0.22);

            Path2D cone = new Path2D.Double();
            cone.moveTo(cx, apexY);
            cone.lineTo(rightX, baseY);
            cone.lineTo(leftX, baseY);
            cone.closePath();
            envelopeShape = cone;
            paintRegion(g, NukeSlot.DELIVERY, cone, true);
            anchorLabel(NukeSlot.DELIVERY,
                    new Point(rightX - 4, baseY - 12), true);

            // Ablative heat shield band at the base
            g.setColor(new Color(110, 70, 50));
            g.fillRect(leftX + 4, baseY - 14, devW - 8, 10);
            g.setColor(new Color(180, 110, 80));
            g.drawRect(leftX + 4, baseY - 14, devW - 8, 10);

            // Inner bay: place near the *base* of the cone where there is
            // actually room. With the wider, shorter cone we can start
            // higher up (35%) and still keep generous interior width.
            int coneH      = baseY - apexY;
            int innerTop   = apexY + (int) (coneH * 0.35);
            int innerBot   = baseY - 22;
            double frac    = (innerTop - apexY) / (double) coneH;
            int innerHalf  = Math.max(10, (int) (frac * (devW / 2.0)) - 4);
            return new Rectangle2D.Double(
                    cx - innerHalf, innerTop, innerHalf * 2, innerBot - innerTop);
        }

        // ── Artillery shell — cylinder with ogive nose, copper driving band ──
        private Rectangle2D paintEnvelopeShell(Graphics2D g, int cx, int devTop, int devBot) {
            int devH = devBot - devTop;
            int devW = Math.min(100, devH / 5);
            int leftX = cx - devW / 2, rightX = cx + devW / 2;
            int noseH = 60;

            Path2D body = new Path2D.Double();
            body.moveTo(leftX, devTop + noseH);
            body.quadTo(leftX, devTop + noseH * 0.1, cx, devTop + 4);
            body.quadTo(rightX, devTop + noseH * 0.1, rightX, devTop + noseH);
            body.lineTo(rightX, devBot - 8);
            body.lineTo(leftX, devBot - 8);
            body.closePath();
            envelopeShape = body;
            paintRegion(g, NukeSlot.DELIVERY, body, true);
            anchorLabel(NukeSlot.DELIVERY,
                    new Point(rightX, devBot - 24), true);

            // Copper driving band
            g.setColor(new Color(160, 90, 50));
            g.fillRect(leftX, devBot - 28, devW, 8);
            g.setColor(new Color(220, 130, 70));
            g.drawRect(leftX, devBot - 28, devW, 8);

            // Base plug
            g.setColor(new Color(60, 70, 85));
            g.fillRect(leftX + 4, devBot - 14, devW - 8, 6);

            int casePad = 8;
            return new Rectangle2D.Double(
                    leftX + casePad, devTop + noseH + 4,
                    devW - 2 * casePad, devBot - 32 - (devTop + noseH + 4));
        }

        /**
         * Compute the inner free bay where architecture-specific guts
         * (gun barrel, primary sphere, secondary cylinder, etc.) go.
         * The casing and delivery aeroshell are all drawn
         * elsewhere now — this just hands back a generously-sized bay
         * with the standard 22 px margin already factored in by the
         * caller's choice of devTop / devBot / devW.
         */
        private Rectangle2D paintShell(Graphics2D g, int cx, int devTop, int devBot,
                                       int devW, int noseH, int tailH) {
            int leftX = cx - devW / 2;
            envelopeShape = null;
            return new Rectangle2D.Double(leftX, devTop, devW, devBot - devTop);
        }

        // ─── Gun-type: barrel + sub-critical projectile + sub-critical target ───
        private void drawGunType(Graphics2D g, int cx, int devTop, int devBot) {
            // Gun-type was a long, narrow tube ('Thin Man' / 'Little Boy').
            int devH  = devBot - devTop;
            int devW  = Math.min(virtW - 2 * DEV_MARGIN, Math.max(220, devH / 3));
            Rectangle2D bay = paintShell(g, cx, devTop, devBot, devW, 0, 0);

            // Barrel: a long vertical rectangle filling most of the bay.
            // Drawn first as the backing structure; everything else sits inside it.
            double barrelW = bay.getWidth() * 0.55;
            Rectangle2D barrel = new Rectangle2D.Double(
                    cx - barrelW / 2, bay.getY() + 6,
                    barrelW, bay.getHeight() - 12);
            g.setColor(new Color(70, 80, 95));
            g.fill(barrel);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(120, 135, 150));
            g.draw(barrel);

            // ── Target end (muzzle): tamper drawn FIRST so the smaller
            //    fissile target sits visibly nested inside it. The whole
            //    target/tamper assembly is inset from the barrel walls
            //    so it doesn't touch the steel sleeve. ──
            double sideGap = 12;            // free room between tamper and barrel
            double targetH = barrelW * 0.50;
            Rectangle2D tamper = new Rectangle2D.Double(
                    cx - barrelW / 2 + sideGap, barrel.getY() + 12,
                    barrelW - 2 * sideGap, targetH + 16);
            Rectangle2D target = new Rectangle2D.Double(
                    tamper.getX() + 10, tamper.getY() + 8,
                    tamper.getWidth() - 20, tamper.getHeight() - 16);
            paintRegion(g, NukeSlot.TAMPER, tamper, true);
            anchorLabel(NukeSlot.TAMPER,
                    new Point((int) tamper.getX(), (int) tamper.getCenterY()), false);
            paintRegion(g, NukeSlot.FISSILE, target, true);
            anchorLabel(NukeSlot.FISSILE,
                    new Point((int) target.getMaxX(), (int) target.getCenterY()), true);

            // Initiator pellets sit at the FRONT face of the target —
            // the muzzle end of the barrel, away from the projectile.
            // Historically ABNER/POLO initiators were a *stack* of small
            // polonium-beryllium pellets at the very tip of the gun, so
            // when the projectile slammed the target into them they all
            // crushed together at once and dumped a synchronised neutron
            // burst into the now-supercritical fissile mass.
            int pelletCount = 4;
            double pelletR  = Math.min(5, (target.getWidth() - 16) / (pelletCount * 2.4));
            double pelletGap = (target.getWidth() - 12 - pelletCount * pelletR * 2)
                    / (pelletCount + 1);
            double pelletY  = target.getMinY() - pelletR + 1;
            Area pellets = new Area();
            for (int i = 0; i < pelletCount; i++) {
                double pcx = target.getMinX() + 6 + pelletGap * (i + 1)
                        + pelletR * (2 * i + 1);
                pellets.add(new Area(circle(pcx, pelletY, pelletR)));
            }
            paintRegion(g, NukeSlot.INITIATOR, pellets, true);
            anchorLabel(NukeSlot.INITIATOR,
                    new Point((int) target.getMaxX(), (int) pelletY), true);

            // ── Breech end: propellant + sub-critical fissile projectile. ──
            double projH = barrelW * 0.40;
            Rectangle2D projectile = new Rectangle2D.Double(
                    cx - barrelW / 2 + 6, barrel.getMaxY() - projH - 28,
                    barrelW - 12, projH);
            // Reuse FISSILE colour but paint manually (one hit shape per slot).
            g.setColor(SLOT_COLORS.get("fissile").darker());
            g.fill(projectile);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(SLOT_COLORS.get("fissile"));
            g.draw(projectile);

            // Propellant charge sits below the projectile.
            Rectangle2D charge = new Rectangle2D.Double(
                    cx - barrelW / 2 + 6, projectile.getMaxY() + 4,
                    barrelW - 12, 18);
            g.setColor(new Color(120, 60, 40));
            g.fill(charge);
            g.setColor(new Color(180, 100, 70));
            g.draw(charge);
        }

        // ─── Spherical implosion (Fat Man / boosted) ───
        private void drawImplosion(Graphics2D g, int cx, int devTop, int devBot, boolean boosted) {
            int[] body = bodyForDelivery(cx, devTop, devBot);
            int devW = body[0], noseH = body[1], tailH = body[2];
            Rectangle2D bay = paintShell(g, cx, devTop, devBot, devW, noseH, tailH);

            double primCy = bay.getCenterY();
            double primR  = Math.min(bay.getWidth(), bay.getHeight()) * 0.42;

            // Shaped-charge "lens" segments around the outside
            paintLensRing(g, cx, primCy, primR);

            // Implosion lens shell (HE layer)
            Ellipse2D lens = circle(cx, primCy, primR);
            paintRegion(g, NukeSlot.IMPLOSION, lens, true);
            anchorLabel(NukeSlot.IMPLOSION,
                    new Point((int) (cx + primR * 0.95), (int) (primCy - primR * 0.6)), true);

            // Tamper
            Ellipse2D tamper = circle(cx, primCy, primR * 0.78);
            paintRegion(g, NukeSlot.TAMPER, tamper, true);
            anchorLabel(NukeSlot.TAMPER,
                    new Point((int) (cx - primR * 0.72), (int) (primCy - primR * 0.4)), false);

            if (boosted) {
                // Hollow pit with boost gas inside it
                Ellipse2D pit = circle(cx, primCy, primR * 0.45);
                paintRegion(g, NukeSlot.FISSILE, pit, true);
                anchorLabel(NukeSlot.FISSILE,
                        new Point((int) (cx - primR * 0.40), (int) (primCy + primR * 0.30)), false);

                Ellipse2D boost = circle(cx, primCy, primR * 0.30);
                paintRegion(g, NukeSlot.BOOST, boost, true);
                anchorLabel(NukeSlot.BOOST,
                        new Point((int) (cx + primR * 0.28), (int) (primCy + primR * 0.10)), true);
            } else {
                // Solid pit
                Ellipse2D pit = circle(cx, primCy, primR * 0.40);
                paintRegion(g, NukeSlot.FISSILE, pit, true);
                anchorLabel(NukeSlot.FISSILE,
                        new Point((int) (cx - primR * 0.36), (int) (primCy + primR * 0.30)), false);
            }

            // Initiator at the centre
            Ellipse2D init = circle(cx, primCy, primR * 0.10);
            paintRegion(g, NukeSlot.INITIATOR, init, true);
            anchorLabel(NukeSlot.INITIATOR,
                    new Point(cx, (int) (primCy - primR * 0.05)), true);

            // Specular highlight — a small white spot upper-left of the
            // sphere so the lens shell reads as a polished metal ball.
            java.awt.Paint oldP = g.getPaint();
            g.setPaint(new java.awt.RadialGradientPaint(
                    (float) (cx - primR * 0.45), (float) (primCy - primR * 0.45),
                    (float) (primR * 0.55),
                    new float[]{0f, 1f},
                    new Color[]{ new Color(255, 255, 255, 90), new Color(255, 255, 255, 0) }));
            g.fill(circle(cx, primCy, primR));
            g.setPaint(oldP);
        }

        /** Decorative tile pattern around the lens shell — the "32 lenses". */
        private void paintLensRing(Graphics2D g, double cx, double cy, double r) {
            int n = 16;
            double inner = r * 1.02, outer = r * 1.18;
            g.setStroke(new BasicStroke(1.0f));
            for (int i = 0; i < n; i++) {
                double a0 = (i / (double) n) * Math.PI * 2;
                double a1 = ((i + 1) / (double) n) * Math.PI * 2;
                Path2D wedge = new Path2D.Double();
                wedge.moveTo(cx + Math.cos(a0) * inner, cy + Math.sin(a0) * inner);
                wedge.lineTo(cx + Math.cos(a0) * outer, cy + Math.sin(a0) * outer);
                wedge.lineTo(cx + Math.cos(a1) * outer, cy + Math.sin(a1) * outer);
                wedge.lineTo(cx + Math.cos(a1) * inner, cy + Math.sin(a1) * inner);
                wedge.closePath();
                g.setColor((i % 2 == 0) ? new Color(90, 30, 30) : new Color(60, 20, 20));
                g.fill(wedge);
                g.setColor(new Color(160, 60, 50));
                g.draw(wedge);
                // Detonator dot on the outer face
                double am = (a0 + a1) / 2;
                g.setColor(new Color(255, 230, 120));
                g.fillOval((int) (cx + Math.cos(am) * outer) - 2,
                           (int) (cy + Math.sin(am) * outer) - 2, 4, 4);
            }
        }

        // ─── Sloika / layer-cake (single-stage thermonuclear) ───
        private void drawSloika(Graphics2D g, int cx, int devTop, int devBot) {
            int[] body = bodyForDelivery(cx, devTop, devBot);
            int devW = body[0], noseH = body[1], tailH = body[2];
            Rectangle2D bay = paintShell(g, cx, devTop, devBot, devW, noseH, tailH);

            double cy = bay.getCenterY();
            double R  = Math.min(bay.getWidth(), bay.getHeight()) * 0.42;

            // HE shell
            paintLensRing(g, cx, cy, R);
            paintRegion(g, NukeSlot.IMPLOSION, circle(cx, cy, R), true);
            anchorLabel(NukeSlot.IMPLOSION,
                    new Point((int) (cx + R * 0.95), (int) (cy - R * 0.6)), true);

            // Outer U-238 jacket = TAMPER
            paintRegion(g, NukeSlot.TAMPER, circle(cx, cy, R * 0.85), true);
            anchorLabel(NukeSlot.TAMPER,
                    new Point((int) (cx - R * 0.80), (int) (cy - R * 0.4)), false);

            // Fusion-fuel layer = SECONDARY (Li-D shell)
            paintRegion(g, NukeSlot.SECONDARY, circle(cx, cy, R * 0.70), true);
            anchorLabel(NukeSlot.SECONDARY,
                    new Point((int) (cx + R * 0.65), (int) cy), true);

            // Inner fissile shell = FISSILE
            paintRegion(g, NukeSlot.FISSILE, circle(cx, cy, R * 0.50), true);
            anchorLabel(NukeSlot.FISSILE,
                    new Point((int) (cx - R * 0.45), (int) (cy + R * 0.30)), false);

            // Boost gas in pit centre
            paintRegion(g, NukeSlot.BOOST, circle(cx, cy, R * 0.25), true);
            anchorLabel(NukeSlot.BOOST,
                    new Point((int) (cx + R * 0.22), (int) (cy + R * 0.10)), true);

            // Initiator
            paintRegion(g, NukeSlot.INITIATOR, circle(cx, cy, R * 0.08), true);
            anchorLabel(NukeSlot.INITIATOR, new Point(cx, (int) (cy - R * 0.05)), true);
        }

        // ─── Two-stage Teller–Ulam (primary + secondary inside radiation case) ───
        private void drawTellerUlam(Graphics2D g, int cx, int devTop, int devBot) {
            // TU needs to fit two stages, so always use the wide body
            // regardless of any delivery hint.
            int[] body = stubbyBody(cx, devTop, devBot);
            int devW = body[0], noseH = body[1], tailH = body[2];
            Rectangle2D bay = paintShell(g, cx, devTop, devBot, devW, noseH, tailH);

            // Inner radiation case — a simple rounded rectangle that
            // hugs the bay. There's no outer envelope to clip against
            // anymore, so just shrink the bay a little.
            Shape radCase = new Rectangle2D.Double(
                    bay.getX() + 1, bay.getY() + 1,
                    bay.getWidth() - 2, bay.getHeight() - 2);
            g.setColor(new Color(50, 55, 65));
            g.fill(radCase);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(120, 135, 150));
            g.draw(radCase);
            Rectangle2D rcB = radCase.getBounds2D();

            // Vertical layout: primary sphere up top, secondary cylinder
            // below, with a gap between them.
            double margin  = 16;
            double caseTop = rcB.getY() + margin;
            double caseBot = rcB.getMaxY() - margin;
            double caseH   = caseBot - caseTop;
            double primR  = Math.min(rcB.getWidth() * 0.30, caseH * 0.22);

            double primCy = caseTop + primR + 4;
            double secTop = primCy + primR + 22;        // breathing gap
            double secBot = caseBot - 4;
            double secH   = Math.max(50, secBot - secTop);
            double secCy  = (secTop + secBot) / 2;
            double secW   = rcB.getWidth() * 0.72;

            // ── Primary (small implosion sphere up top) ──
            paintLensRing(g, cx, primCy, primR);
            paintRegion(g, NukeSlot.IMPLOSION, circle(cx, primCy, primR), true);
            anchorLabel(NukeSlot.IMPLOSION,
                    new Point((int) (cx + primR), (int) (primCy - primR * 0.6)), true);
            paintRegion(g, NukeSlot.TAMPER, circle(cx, primCy, primR * 0.78), true);
            anchorLabel(NukeSlot.TAMPER,
                    new Point((int) (cx - primR * 0.78), (int) (primCy - primR * 0.4)), false);
            paintRegion(g, NukeSlot.BOOST, circle(cx, primCy, primR * 0.55), true);
            anchorLabel(NukeSlot.BOOST,
                    new Point((int) (cx + primR * 0.50), (int) (primCy + primR * 0.10)), true);
            paintRegion(g, NukeSlot.FISSILE, circle(cx, primCy, primR * 0.42), true);
            anchorLabel(NukeSlot.FISSILE,
                    new Point((int) (cx - primR * 0.40), (int) (primCy + primR * 0.30)), false);
            paintRegion(g, NukeSlot.INITIATOR, circle(cx, primCy, primR * 0.13), true);
            anchorLabel(NukeSlot.INITIATOR, new Point(cx, (int) primCy), true);

            // ── Secondary cylinder (Teller-Ulam style: nested layers). ──
            // Clip the cylinder to the radiation case so it can't poke
            // outside the casing on bulbous / conical envelopes.
            Rectangle2D secRect = new Rectangle2D.Double(
                    cx - secW / 2, secCy - secH / 2, secW, secH);
            Shape secondaryShape;
            Rectangle2D secondary;
            Area secArea = new Area(secRect);
            secArea.intersect(new Area(radCase));
            if (secArea.isEmpty()) {
                secondaryShape = secRect;
                secondary = secRect;
            } else {
                secondaryShape = secArea;
                secondary = secArea.getBounds2D();
            }
            // The outer SECONDARY shell registers the click target for the
            // whole secondary stage and is drawn first as the ablator/pusher.
            paintRegion(g, NukeSlot.SECONDARY, secondaryShape, true);
            stageOuterShape[0] = secondaryShape;
            anchorLabel(NukeSlot.SECONDARY,
                    new Point((int) secondary.getMaxX(), (int) secondary.getCenterY()), true);

            // Decorative inner layers (non-interactive) so the secondary
            // reads as a real Teller-Ulam fuel capsule instead of a flat
            // bar. Layer materials are driven by the Fusion Designer.
            double sx = secondary.getX(), sy = secondary.getY();
            double sw = secondary.getWidth(), sh = secondary.getHeight();

            // (Multi-stage devices are rendered by drawMultiStage instead
            // of stacking decorative jackets here.)

            // 1. Channel filler band just inside the case.
            Color fillerCol, fillerEdge;
            switch (fusion.channelFiller[0]) {
                case "Vacuum":
                    fillerCol = new Color(20, 30, 40); fillerEdge = new Color(80, 110, 140); break;
                case "Polystyrene foam":
                default:
                    fillerCol = new Color(70, 110, 140); fillerEdge = new Color(120, 170, 200); break;
            }
            Rectangle2D foam = new Rectangle2D.Double(sx + 4, sy + 4, sw - 8, sh - 8);
            g.setColor(fillerCol);
            g.fill(foam);
            g.setColor(fillerEdge);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(foam);
            stageFoamRect[0] = foam;

            // 2. Pusher / tamper.
            Color pusherCol, pusherEdge;
            switch (fusion.pusher[0]) {
                case "Lead":
                    pusherCol = new Color(80, 80, 95); pusherEdge = new Color(150, 150, 170);
                    break;
                case "Tungsten":
                    pusherCol = new Color(60, 65, 75); pusherEdge = new Color(170, 175, 185);
                    break;
                case "U-238":
                default:
                    pusherCol = new Color(95, 75, 60); pusherEdge = new Color(150, 120, 95);
                    break;
            }
            Rectangle2D pusher = new Rectangle2D.Double(
                    sx + 12, sy + 10, sw - 24, sh - 20);
            g.setColor(pusherCol);
            g.fill(pusher);
            g.setColor(pusherEdge);
            g.draw(pusher);
            stagePusherRect[0] = pusher;

            // 3. LiD fusion fuel.
            Rectangle2D fuel = new Rectangle2D.Double(
                    sx + 22, sy + 18, sw - 44, sh - 36);
            g.setColor(new Color(210, 180, 110));
            g.fill(fuel);
            g.setColor(new Color(245, 220, 150));
            g.draw(fuel);
            stageFuelRect[0] = fuel;
            g.setColor(new Color(245, 240, 220));
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            FontMetrics fmF = g.getFontMetrics();
            String fuelTag = fusion.fuel[0].startsWith("Natural") ? "natLi-D" : "6Li-D";
            int tagW = fmF.stringWidth(fuelTag);
            if (fuel.getWidth() > fmF.getHeight() + 4 && fuel.getHeight() > tagW + 10) {
                // The whole schematic is rotated 90° CW. Anchor the
                // tag near the canvas-left edge of the fuel rect so it
                // appears at the *display top* of the cylinder, then
                // counter-rotate so the text reads horizontally.
                // After the -90° rotation around (ax, ay), glyphs
                // extend in canvas -y, so to centre the text on
                // fuel.getCenterY() we offset ay by +tagW/2.
                double ax = fuel.getX() + fmF.getAscent() + 2;
                double ay = fuel.getCenterY() + tagW / 2.0;
                java.awt.geom.AffineTransform old = g.getTransform();
                g.rotate(-Math.PI / 2.0, ax, ay);
                g.drawString(fuelTag, (int) ax, (int) ay);
                g.setTransform(old);
            }

            // 4. Optional Pu-239 spark-plug rod down the central axis.
            if (fusion.sparkPlug[0]) {
                int plugW = 8;
                Rectangle2D plug = new Rectangle2D.Double(
                        cx - plugW / 2.0, fuel.getY() + 4,
                        plugW, fuel.getHeight() - 8);
                g.setColor(new Color(220, 150, 70));
                g.fill(plug);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(new Color(255, 200, 120));
                g.draw(plug);
                stagePlugRect[0] = plug;
            }
        }

        // ════════════════════════════════════════════════════════════
        // Multi-stage (3-stage / 4-stage) Teller-Ulam variant.
        // Lays out the primary at the top of the radiation case and
        // then stacks (totalStages - 1) secondary capsules vertically,
        // each of which radiation-couples downward into the next. Only
        // the topmost (true secondary) registers a SECONDARY hit shape;
        // the lower stages are decorative tertiary / quaternary fuel
        // packages whose materials follow the same fusion sub-design.
        // ════════════════════════════════════════════════════════════
        private void drawMultiStage(Graphics2D g, int cx, int devTop, int devBot,
                                    int totalStages) {
            int[] body = stubbyBody(cx, devTop, devBot);
            int devW = body[0], noseH = body[1], tailH = body[2];
            Rectangle2D bay = paintShell(g, cx, devTop, devBot, devW, noseH, tailH);

            Shape radCase = new Rectangle2D.Double(
                    bay.getX() + 1, bay.getY() + 1,
                    bay.getWidth() - 2, bay.getHeight() - 2);
            g.setColor(new Color(50, 55, 65));
            g.fill(radCase);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(120, 135, 150));
            g.draw(radCase);
            Rectangle2D rcB = radCase.getBounds2D();

            // Use the *exact* same primary geometry as drawTellerUlam
            // so the fission stage stays in a fixed visual position
            // (and at a fixed size) regardless of stage count.
            double margin  = 16;
            double caseTop = rcB.getY() + margin;
            double caseBot = rcB.getMaxY() - margin;
            double caseH   = caseBot - caseTop;
            double primR   = Math.min(rcB.getWidth() * 0.30, caseH * 0.22);

            double primCy = caseTop + primR + 4;

            // ── Primary (small implosion sphere up top) ──
            paintLensRing(g, cx, primCy, primR);
            paintRegion(g, NukeSlot.IMPLOSION, circle(cx, primCy, primR), true);
            paintRegion(g, NukeSlot.TAMPER,    circle(cx, primCy, primR * 0.78), true);
            paintRegion(g, NukeSlot.BOOST,     circle(cx, primCy, primR * 0.55), true);
            paintRegion(g, NukeSlot.FISSILE,   circle(cx, primCy, primR * 0.42), true);
            paintRegion(g, NukeSlot.INITIATOR, circle(cx, primCy, primR * 0.13), true);

            // ── Stack the secondary capsules below the primary. ──
            // The stack starts at the same Y as the 2-stage TU secondary
            // (primCy + primR + 22) so the fission primary doesn't move
            // when stages are added. The remaining room is then split
            // equally among all (totalStages - 1) capsules so the top
            // secondary shrinks along the long axis to make space for
            // the tertiary / quaternary instead of pushing them off the
            // case. Width still tapers per-stage.
            int    nSecs    = totalStages - 1;
            double stackTop = primCy + primR + 22;
            double stackBot = caseBot - 4;
            double gap      = 10;
            double totalH   = stackBot - stackTop;
            double secH     = (totalH - gap * (nSecs - 1)) / nSecs;
            secH = Math.max(36, secH);

            String[] stageNames = { "SECONDARY", "TERTIARY", "QUATERNARY" };
            for (int i = 0; i < nSecs; i++) {
                double secCy = stackTop + secH / 2.0 + i * (secH + gap);
                // Match the 2-stage TU secondary width (0.72) for the
                // top secondary, then taper inward for each tertiary /
                // quaternary so the cascade is visually distinct.
                double secW  = rcB.getWidth() * (0.72 - 0.10 * i);
                drawSecondaryCapsule(g, cx, secCy, secW, secH, radCase,
                        i == 0,
                        i < stageNames.length ? stageNames[i] : "STAGE " + (i + 2),
                        i);

                // Radiation-coupling arrow into the next stage.
                if (i < nSecs - 1) {
                    int ay = (int) (secCy + secH / 2.0 + 2);
                    int by = (int) (secCy + secH / 2.0 + gap - 2);
                    g.setStroke(new BasicStroke(1.4f));
                    g.setColor(new Color(255, 210, 120, 200));
                    g.drawLine(cx, ay, cx, by);
                    int[] xs = { cx - 4, cx + 4, cx };
                    int[] ys = { by - 4, by - 4, by };
                    g.fillPolygon(xs, ys, 3);
                }
            }
        }

        /** Draws one secondary fuel capsule (filler / pusher / fuel /
         *  optional spark plug). When {@code isPrimarySecondary} is true
         *  the outer shell registers as the SECONDARY hit shape; lower
         *  stages are purely decorative. */
        private void drawSecondaryCapsule(Graphics2D g, int cx, double secCy,
                                          double secW, double secH, Shape radCase,
                                          boolean isPrimarySecondary,
                                          String label,
                                          int stage) {
            // Per-stage material picks. Falls back to the secondary's
            // values for any out-of-range stage so the painter stays
            // robust if it gets called with a bad index.
            int s = (stage >= 0 && stage < FusionDetails.MAX) ? stage : 0;
            String stageFiller   = fusion.channelFiller[s];
            String stagePusher   = fusion.pusher[s];
            String stageFuel     = fusion.fuel[s];
            boolean stageSpark   = fusion.sparkPlug[s];
            Rectangle2D secRect = new Rectangle2D.Double(
                    cx - secW / 2, secCy - secH / 2, secW, secH);
            Shape secondaryShape;
            Rectangle2D secondary;
            Area secArea = new Area(secRect);
            secArea.intersect(new Area(radCase));
            if (secArea.isEmpty()) {
                secondaryShape = secRect;
                secondary = secRect;
            } else {
                secondaryShape = secArea;
                secondary = secArea.getBounds2D();
            }

            if (isPrimarySecondary) {
                // Register the click target for the SECONDARY slot but
                // paint the shell with the same neutral jacket as the
                // tertiary / quaternary stages. Otherwise the violet
                // SECONDARY fill makes the top capsule look permanently
                // highlighted even when the user is editing a *different*
                // stage — the new editing-stage overlay is the single
                // source of truth for "which stage is active".
                hitShapes.put(NukeSlot.SECONDARY, secondaryShape);
                g.setColor(new Color(70, 55, 40));
                g.fill(secondaryShape);
                g.setColor(new Color(140, 110, 80));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(secondaryShape);
            } else {
                // Decorative tertiary/quaternary outer shell — U-238-tinted
                // jacket that visually mimics the SECONDARY ablator but
                // with a cooler hue so the user reads the cascade order.
                g.setColor(new Color(70, 55, 40));
                g.fill(secondaryShape);
                g.setColor(new Color(140, 110, 80));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(secondaryShape);
            }
            if (s >= 0 && s < FusionDetails.MAX) stageOuterShape[s] = secondaryShape;

            double sx = secondary.getX(), sy = secondary.getY();
            double sw = secondary.getWidth(), sh = secondary.getHeight();
            if (sw < 30 || sh < 24) return; // too tiny for inner detail

            // 1. Channel filler band.
            Color fillerCol, fillerEdge;
            switch (stageFiller) {
                case "Vacuum":
                    fillerCol = new Color(20, 30, 40); fillerEdge = new Color(80, 110, 140); break;
                case "Polystyrene foam":
                default:
                    fillerCol = new Color(70, 110, 140); fillerEdge = new Color(120, 170, 200); break;
            }
            Rectangle2D foam = new Rectangle2D.Double(sx + 4, sy + 4, sw - 8, sh - 8);
            g.setColor(fillerCol);
            g.fill(foam);
            g.setColor(fillerEdge);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(foam);
            stageFoamRect[s] = foam;

            // 2. Pusher / tamper.
            Color pusherCol, pusherEdge;
            switch (stagePusher) {
                case "Lead":
                    pusherCol = new Color(80, 80, 95); pusherEdge = new Color(150, 150, 170); break;
                case "Tungsten":
                    pusherCol = new Color(60, 65, 75); pusherEdge = new Color(170, 175, 185); break;
                case "U-238":
                default:
                    pusherCol = new Color(95, 75, 60); pusherEdge = new Color(150, 120, 95); break;
            }
            Rectangle2D pusher = new Rectangle2D.Double(
                    sx + 12, sy + 10, sw - 24, sh - 20);
            if (pusher.getWidth() > 0 && pusher.getHeight() > 0) {
                g.setColor(pusherCol);
                g.fill(pusher);
                g.setColor(pusherEdge);
                g.draw(pusher);
                stagePusherRect[s] = pusher;
            }

            // 3. LiD fusion fuel.
            Rectangle2D fuel = new Rectangle2D.Double(
                    sx + 22, sy + 18, sw - 44, sh - 36);
            if (fuel.getWidth() > 0 && fuel.getHeight() > 0) {
                g.setColor(new Color(210, 180, 110));
                g.fill(fuel);
                g.setColor(new Color(245, 220, 150));
                g.draw(fuel);
                stageFuelRect[s] = fuel;

                g.setColor(new Color(245, 240, 220));
                g.setFont(new Font("Monospaced", Font.BOLD, 9));
                FontMetrics fmF = g.getFontMetrics();
                String fuelTag = stageFuel.startsWith("Natural") ? "natLi-D" : "6Li-D";
                int tagW = fmF.stringWidth(fuelTag);
                if (fuel.getWidth() > fmF.getHeight() + 4 && fuel.getHeight() > tagW + 10) {
                    // Anchor near the canvas-left edge of the fuel
                    // rect (= display top after the 90° CW canvas
                    // rotation), centred along the long axis, then
                    // counter-rotate so the text reads horizontally.
                    // After the -90° rotation around (ax, ay), glyphs
                    // extend in canvas -y, so to centre the text on
                    // fuel.getCenterY() we offset ay by +tagW/2.
                    double ax = fuel.getX() + fmF.getAscent() + 2;
                    double ay = fuel.getCenterY() + tagW / 2.0;
                    java.awt.geom.AffineTransform old = g.getTransform();
                    g.rotate(-Math.PI / 2.0, ax, ay);
                    g.drawString(fuelTag, (int) ax, (int) ay);
                    g.setTransform(old);
                }

                // 4. Optional Pu-239 spark-plug rod. Drawn whenever
                // *this* stage's spark plug is enabled — physically
                // only the secondary uses one, but the per-stage UI
                // lets the user override that for educational purposes.
                if (stageSpark) {
                    int plugW = 8;
                    Rectangle2D plug = new Rectangle2D.Double(
                            cx - plugW / 2.0, fuel.getY() + 4,
                            plugW, fuel.getHeight() - 8);
                    g.setColor(new Color(220, 150, 70));
                    g.fill(plug);
                    g.setStroke(new BasicStroke(1.2f));
                    g.setColor(new Color(255, 200, 120));
                    g.draw(plug);
                    stagePlugRect[s] = plug;
                }
            }

            // Stage label, in normal (horizontal) orientation for the
            // user. The schematic canvas is rotated 90° CW, so:
            //   • "display bottom-left of the stage" maps to canvas
            //     bottom-right (sx+sw, sy+sh)
            //   • a -90° (CCW) text rotation around that anchor cancels
            //     the canvas rotation so the label reads left-to-right.
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            g.setColor(new Color(180, 210, 230, 220));
            double ax = secondary.getMaxX() - 4;
            double ay = secondary.getMaxY() - 4;
            java.awt.geom.AffineTransform oldL = g.getTransform();
            g.rotate(-Math.PI / 2.0, ax, ay);
            g.drawString(label, (int) ax, (int) (ay - 2));
            g.setTransform(oldL);
        }

        private void anchorLabel(NukeSlot slot, Point pivot, boolean rightSide) {
            // Leader-line labels were removed from the schematic at the
            // user's request; this method is intentionally a no-op so
            // existing call sites in the architecture renderers stay
            // valid without further changes.
        }

        private void paintRegion(Graphics2D g, NukeSlot slot, Shape shape, boolean track) {
            if (!slotApplicable(slot)) return;   // hidden when N/A for this config
            if (track) hitShapes.put(slot, shape);
            NukePart p = design.get(slot);
            Color base = SLOT_COLORS.getOrDefault(slot.getId(), ACCENT_DIM);

            if (p == NukePart.NONE) {
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 50));
                g.fill(shape);
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{5f, 4f}, 0f));
                g.setColor(base);
                g.draw(shape);
            } else {
                g.setColor(base);
                g.fill(shape);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(base.brighter());
                g.draw(shape);
            }
        }

        private void paintBanner(Graphics2D g, NukeSlot slot, Rectangle2D box, String text) {
            hitShapes.put(slot, box);
            NukePart p = design.get(slot);
            Color base = SLOT_COLORS.getOrDefault(slot.getId(), ACCENT_DIM);
            boolean empty = (p == NukePart.NONE);

            g.setColor(empty ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 70)
                             : base);
            g.fill(box);
            g.setStroke(new BasicStroke(empty ? 1.0f : 1.4f,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, empty ? new float[]{5f, 4f} : null, 0f));
            g.setColor(activeSlot == slot ? ACCENT : base.brighter());
            g.draw(box);

            g.setFont(new Font("Monospaced", Font.BOLD, 12));
            g.setColor(empty ? new Color(180, 200, 220) : Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int tx = (int) (box.getX() + 12);
            int ty = (int) (box.getY() + (box.getHeight() + fm.getAscent()) / 2 - 3);
            g.drawString(text, tx, ty);
        }

        /**
         * Lays out leader-line labels in the side gutters. Labels are
         * stacked vertically with a minimum spacing so they never overlap.
         */
        private void drawLeaderLabels(Graphics2D g, int w, int gutter) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            int rowH = fm.getHeight() + 4;

            // Split labels by side and sort by anchor Y
            java.util.List<LabelAnchor> rights = new java.util.ArrayList<>();
            java.util.List<LabelAnchor> lefts  = new java.util.ArrayList<>();
            for (LabelAnchor la : labels) (la.rightSide ? rights : lefts).add(la);
            rights.sort((a, b) -> Integer.compare(a.pivot.y, b.pivot.y));
            lefts.sort((a, b) -> Integer.compare(a.pivot.y, b.pivot.y));

            int rightX = w - gutter + 8;
            int leftX  = gutter - 8;

            placeColumn(g, rights, rightX, rowH, true,  fm);
            placeColumn(g, lefts,  leftX,  rowH, false, fm);
        }

        private void placeColumn(Graphics2D g, java.util.List<LabelAnchor> col,
                                 int columnX, int rowH, boolean rightSide,
                                 FontMetrics fm) {
            int lastY = -10000;
            for (LabelAnchor la : col) {
                int y = Math.max(la.pivot.y, lastY + rowH);
                lastY = y;

                NukePart p = design.get(la.slot);
                String text = la.slot.getName() + ": " +
                        (p == NukePart.NONE ? "(empty)" : p.getName());

                Color base = SLOT_COLORS.getOrDefault(la.slot.getId(), ACCENT_DIM);
                boolean active = la.slot == activeSlot;
                Color textCol = active ? ACCENT
                        : (p == NukePart.NONE ? base.brighter() : new Color(220, 235, 240));

                int textW = fm.stringWidth(text);
                int textX = rightSide ? columnX : columnX - textW;
                int textY = y + fm.getAscent() / 2 - 2;

                // Register the label rect as a click target for this slot
                int padH = 3, padW = 6;
                Rectangle2D labelHit = new Rectangle2D.Double(
                        textX - padW, textY - fm.getAscent() - padH + 2,
                        textW + 2 * padW, fm.getHeight() + 2 * padH);
                labelHitShapes.put(la.slot, labelHit);

                // Subtle pill background behind active label so it pops.
                if (active) {
                    g.setColor(new Color(0, 200, 220, 35));
                    g.fill(labelHit);
                }

                // Leader line: from the anchor on the device, kink near label
                g.setStroke(new BasicStroke(active ? 1.8f : 1.0f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(active ? ACCENT : base);
                int kinkX = rightSide ? columnX - 14 : columnX + 14;
                g.drawLine(la.pivot.x, la.pivot.y, kinkX, y);
                g.drawLine(kinkX, y, rightSide ? columnX - 4 : columnX + 4, y);

                // Ringed anchor dot — dark inner core + bright outer ring
                // so it stays visible no matter what colour fill it sits on.
                int dotR = active ? 5 : 4;
                g.setColor(new Color(8, 12, 20));
                g.fillOval(la.pivot.x - dotR, la.pivot.y - dotR, dotR * 2, dotR * 2);
                g.setStroke(new BasicStroke(1.4f));
                g.setColor(active ? ACCENT : base.brighter());
                g.drawOval(la.pivot.x - dotR, la.pivot.y - dotR, dotR * 2, dotR * 2);

                // Text
                g.setColor(textCol);
                g.drawString(text, textX, textY);
                if (active) {
                    g.setStroke(new BasicStroke(1.0f));
                    g.drawLine(textX, textY + 2, textX + textW, textY + 2);
                }
            }
        }
    }
}
