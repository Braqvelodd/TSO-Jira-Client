package tso.usmc.jira.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;

public class StyledBorder extends AbstractBorder {
    private final Color backgroundColor;
    private final Color borderColor;
    private final int borderWidth;
    private final int borderRadius;
    private final Insets insets;

    public StyledBorder(Color backgroundColor, Color borderColor, int borderWidth, int borderRadius, Insets padding) {
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.borderRadius = borderRadius;
        
        int top = (padding != null) ? padding.top : 0;
        int left = (padding != null) ? padding.left : 0;
        int bottom = (padding != null) ? padding.bottom : 0;
        int right = (padding != null) ? padding.right : 0;
        
        // Add safety padding for border radius
        int radiusOffset = borderRadius / 2;
        this.insets = new Insets(top + radiusOffset, left + radiusOffset, bottom + radiusOffset, right + radiusOffset);
    }

    public Color getBackgroundColor() { return backgroundColor; }
    public Color getBorderColor() { return borderColor; }
    public int getBorderWidth() { return borderWidth; }
    public int getBorderRadius() { return borderRadius; }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Paint background if it's set and has some alpha (visible)
        if (backgroundColor != null && backgroundColor.getAlpha() > 0) {
            g2.setColor(backgroundColor);
            if (borderRadius > 0) {
                g2.fillRoundRect(x, y, width - 1, height - 1, borderRadius, borderRadius);
            } else {
                g2.fillRect(x, y, width, height);
            }
        }

        // 2. Paint outline border if visible
        if (borderColor != null && borderWidth > 0 && borderColor.getAlpha() > 0) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderWidth));
            float offset = borderWidth / 2.0f;
            if (borderRadius > 0) {
                g2.draw(new RoundRectangle2D.Float(
                    x + offset, 
                    y + offset, 
                    width - borderWidth, 
                    height - borderWidth, 
                    borderRadius, 
                    borderRadius
                ));
            } else {
                g2.draw(new java.awt.geom.Rectangle2D.Float(
                    x + offset, 
                    y + offset, 
                    width - borderWidth, 
                    height - borderWidth
                ));
            }
        }

        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return (Insets) insets.clone();
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.top = this.insets.top;
        insets.left = this.insets.left;
        insets.bottom = this.insets.bottom;
        insets.right = this.insets.right;
        return insets;
    }
}
