package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Step 17 — companion window showing the full strategic command
 * guide. Display only; never mutates match state.
 *
 * <p><b>Offline-only.</b>
 */
public class MabCommandGuideWindow extends JFrame {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId playerId;
    private final MabCommandGuideModel model = new MabCommandGuideModel();
    private final JTextArea area = new JTextArea(28, 64);

    public MabCommandGuideWindow(MutuallyAssuredBlocksMatch match, ParticipantId playerId) {
        super("Mutually Assured Blocks — Command Guide");
        if (match == null) throw new IllegalArgumentException("match");
        this.match = match;
        this.playerId = (playerId == null) ? ParticipantId.PLAYER_A : playerId;

        area.setEditable(false);
        area.setFont(MONO);
        area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        south.add(refresh);
        south.add(close);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        pack();
        setLocationByPlatform(true);
        refresh();
    }

    /** Re-reads the model and updates the text. */
    public void refresh() {
        try {
            String text = MabCommandGuideFormatter.formatFullGuide(
                    model.buildEntries(match, playerId));
            area.setText(text);
            area.setCaretPosition(0);
        } catch (RuntimeException ex) {
            area.setText("(unable to build command guide: " + ex.getMessage() + ")");
        }
    }
}
