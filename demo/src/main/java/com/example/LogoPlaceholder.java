package com.example;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class LogoPlaceholder extends JComponent {
    private static final String DEFAULT_RESOURCE = "/logo.png";

    private final BufferedImage image; // null -> draw the placeholder

    public LogoPlaceholder(int width, int height) {
        this(DEFAULT_RESOURCE, width, height);
    }

    public LogoPlaceholder(String resource, int width, int height) {
        this.image = load(resource);
        Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setOpaque(false);
    }

    private static BufferedImage load(String resource) {
        try (InputStream in = LogoPlaceholder.class.getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            if (image != null) {
                double scale = Math.min((double) w / image.getWidth(), (double) h / image.getHeight());
                int iw = (int) Math.round(image.getWidth() * scale);
                int ih = (int) Math.round(image.getHeight() * scale);
                g2.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
                return;
            }
            g2.setColor(new Color(0xE4E4E4));
            g2.fillRect(0, 0, w - 1, h - 1);
            g2.setColor(new Color(0x9A9A9A));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] { 6f, 4f }, 0f));
            g2.drawRect(1, 1, w - 3, h - 3);
            g2.setFont(getFont().deriveFont(java.awt.Font.BOLD, Math.max(10f, h / 4f)));
            FontMetrics fm = g2.getFontMetrics();
            String text = "LOGO";
            g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
