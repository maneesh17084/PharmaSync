package com.pharmasync.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Rounded white (or dark) surface with a hairline border, used for dashboard tiles, charts and forms. */
public class Card extends JPanel {

    private final int arc;

    public Card(LayoutManager layout) {
        this(layout, 14);
    }

    public Card(LayoutManager layout, int arc) {
        super(layout);
        this.arc = arc;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.card());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(Theme.cardBorder());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
