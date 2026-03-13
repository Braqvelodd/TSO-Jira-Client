package tso.usmc.jira.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * UI-specific utilities for Swing components and layouts.
 */
public class SwingUtils {

    /**
     * Sets up a double-click listener on a text field to show an expanded
     * multi-line editor in a popup dialog.
     */
    public static void setupExpandedView(JTextField field) {
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTextArea area = new JTextArea(15, 50);
                    area.setText(field.getText());
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    int result = JOptionPane.showConfirmDialog(field, new JScrollPane(area), 
                        "Expanded Input", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (result == JOptionPane.OK_OPTION) {
                        field.setText(area.getText());
                    }
                }
            }
        });
        field.setToolTipText("Double-click to expand");
    }

    /**
     * FlowLayout subclass that correctly calculates its preferred size when its
     * components wrap.
     */
    public static class WrapLayout extends FlowLayout {
        public WrapLayout() { super(FlowLayout.LEFT); }
        public WrapLayout(int align) { super(align); }
        public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension size = layoutSize(target, false);
            size.width -= getHgap() + 1;
            return size;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        if (rowWidth > 0) rowWidth += hgap;
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
                if (scrollPane != null && target.isValid()) {
                    dim.width -= (hgap + 1);
                }

                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }
}
