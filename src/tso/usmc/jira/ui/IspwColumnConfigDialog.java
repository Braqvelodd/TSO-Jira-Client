package tso.usmc.jira.ui;

import tso.usmc.jira.util.JiraConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IspwColumnConfigDialog extends JDialog {
    private final String sampleText;
    private final JiraConfig config;
    private final List<Integer> splitPoints = new ArrayList<>();
    private final JPanel previewPanel;
    
    // UI mapping fields
    private final JTextField typeStart = new JTextField(3);
    private final JTextField typeEnd = new JTextField(3);
    private final JTextField nameStart = new JTextField(3);
    private final JTextField nameEnd = new JTextField(3);
    private final JTextField srStart = new JTextField(3);
    private final JTextField srEnd = new JTextField(3);
    private final JTextField userStart = new JTextField(3);
    private final JTextField userEnd = new JTextField(3);
    private final JTextField actionStart = new JTextField(3);
    private final JTextField actionEnd = new JTextField(3);
    private final JTextField minLen = new JTextField(3);

    public IspwColumnConfigDialog(Frame owner, String text, JiraConfig config) {
        super(owner, "Configure ISPW Columns (Fixed Width)", true);
        this.sampleText = text;
        this.config = config;
        
        setLayout(new BorderLayout());
        setSize(900, 700);
        setLocationRelativeTo(owner);

        // Header Instructions
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        header.add(new JLabel("<html><b>Instructions:</b> Click on the preview to add/remove red split lines. " +
                "Use the numbers to fill the mapping fields below. Values are saved to your JiraConfig.ini.</html>"), BorderLayout.NORTH);
        add(header, BorderLayout.NORTH);

        // Preview Area
        previewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(new Font("Monospaced", Font.PLAIN, 12));
                FontMetrics fm = g.getFontMetrics();
                int charW = fm.charWidth('W');
                int lineHeight = fm.getHeight();
                
                String[] lines = sampleText.split("\n");
                int displayLines = Math.min(lines.length, 25);
                
                // Draw ruler
                g.setColor(Color.LIGHT_GRAY);
                for (int i = 0; i < 150; i += 10) {
                    int x = 10 + (i * charW);
                    g.drawLine(x, 0, x, 5);
                    g.drawString(String.valueOf(i), x - 5, 15);
                }

                // Draw text
                g.setColor(Color.BLACK);
                for (int i = 0; i < displayLines; i++) {
                    g.drawString(lines[i], 10, 45 + (i * lineHeight));
                }

                // Draw vertical split lines
                g.setColor(Color.RED);
                for (int split : splitPoints) {
                    int x = 10 + (split * charW);
                    g.drawLine(x, 20, x, 45 + (displayLines * lineHeight));
                    g.drawString(String.valueOf(split), x - 5, 35);
                }
            }
        };
        previewPanel.setBackground(Color.WHITE);
        previewPanel.setPreferredSize(new Dimension(1500, 500));
        
        previewPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                FontMetrics fm = previewPanel.getFontMetrics(new Font("Monospaced", Font.PLAIN, 12));
                int charW = fm.charWidth('W');
                int col = (e.getX() - 10 + (charW / 2)) / charW;
                if (col < 0) col = 0;

                if (SwingUtilities.isRightMouseButton(e)) {
                    showColumnContextMenu(e.getComponent(), e.getX(), e.getY(), col);
                } else {
                    if (splitPoints.contains(col)) {
                        splitPoints.remove(Integer.valueOf(col));
                    } else {
                        splitPoints.add(col);
                        Collections.sort(splitPoints);
                    }
                    previewPanel.repaint();
                }
            }
        });

        add(new JScrollPane(previewPanel), BorderLayout.CENTER);

        // Mapping Panel
        JPanel mappingPanel = new JPanel(new GridBagLayout());
        mappingPanel.setBorder(BorderFactory.createTitledBorder("Column Mappings (Character Offsets)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        addMappingRow(mappingPanel, gbc, 0, "CI Type Bounds:", typeStart, typeEnd);
        addMappingRow(mappingPanel, gbc, 1, "CI Name Bounds:", nameStart, nameEnd);
        addMappingRow(mappingPanel, gbc, 2, "SR Number Bounds:", srStart, srEnd);
        addMappingRow(mappingPanel, gbc, 3, "User ID Bounds:", userStart, userEnd);
        addMappingRow(mappingPanel, gbc, 4, "Action Bounds:", actionStart, actionEnd);
        
        gbc.gridy = 5; gbc.gridx = 0;
        mappingPanel.add(new JLabel("Min Line Length:"), gbc);
        gbc.gridx = 1; mappingPanel.add(minLen, gbc);

        add(mappingPanel, BorderLayout.SOUTH);
        
        // Initialize with current config
        loadCurrentConfig();

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save & Apply");
        JButton cancelBtn = new JButton("Cancel");
        
        saveBtn.addActionListener(e -> saveConfig());
        cancelBtn.addActionListener(e -> dispose());
        
        footer.add(saveBtn);
        footer.add(cancelBtn);
        
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(mappingPanel, BorderLayout.CENTER);
        bottomContainer.add(footer, BorderLayout.SOUTH);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void showColumnContextMenu(Component invoker, int x, int y, int clickedCol) {
        // Find the range [start, end] based on existing split points
        int start = 0;
        int end = clickedCol;
        
        List<Integer> sorted = new ArrayList<>(splitPoints);
        if (!sorted.contains(0)) sorted.add(0);
        Collections.sort(sorted);
        
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) > clickedCol) {
                start = sorted.get(i - 1);
                end = sorted.get(i);
                break;
            }
            if (i == sorted.size() - 1) {
                start = sorted.get(i);
                end = clickedCol + 5; // Default some width if clicking past last marker
            }
        }

        final int finalStart = start;
        final int finalEnd = end;

        JPopupMenu menu = new JPopupMenu();
        menu.add(createMenuItem("Set as CI Type (" + start + " to " + end + ")", () -> {
            typeStart.setText(String.valueOf(finalStart));
            typeEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        }));
        menu.add(createMenuItem("Set as CI Name (" + start + " to " + end + ")", () -> {
            nameStart.setText(String.valueOf(finalStart));
            nameEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        }));
        menu.add(createMenuItem("Set as SR Number (" + start + " to " + end + ")", () -> {
            srStart.setText(String.valueOf(finalStart));
            srEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        }));
        menu.add(createMenuItem("Set as User ID (" + start + " to " + end + ")", () -> {
            userStart.setText(String.valueOf(finalStart));
            userEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        }));
        menu.add(createMenuItem("Set as Action (" + start + " to " + end + ")", () -> {
            actionStart.setText(String.valueOf(finalStart));
            actionEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        }));

        menu.show(invoker, x, y);
    }

    private void updateMinLineLength() {
        int max = 0;
        try { max = Math.max(max, Integer.parseInt(typeEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(nameEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(srEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(userEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(actionEnd.getText().trim())); } catch (Exception ignored) {}
        if (max > 0) {
            minLen.setText(String.valueOf(max));
        }
    }

    private JMenuItem createMenuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void addMappingRow(JPanel p, GridBagConstraints gbc, int row, String label, JTextField start, JTextField end) {
        gbc.gridy = row;
        gbc.gridx = 0; p.add(new JLabel(label), gbc);
        gbc.gridx = 1; p.add(start, gbc);
        gbc.gridx = 2; p.add(new JLabel("to"), gbc);
        gbc.gridx = 3; p.add(end, gbc);
    }

    private void loadCurrentConfig() {
        int[] type = config.getIspwColumnBounds("ci_type", new int[]{0, 4});
        int[] name = config.getIspwColumnBounds("ci_name", new int[]{5, 13});
        int[] sr = config.getIspwColumnBounds("sr", new int[]{30, 40});
        int[] user = config.getIspwColumnBounds("user", new int[]{41, 47});
        int[] action = config.getIspwActionBounds(new int[]{55, 56});
        
        typeStart.setText(String.valueOf(type[0]));
        typeEnd.setText(String.valueOf(type[1]));
        nameStart.setText(String.valueOf(name[0]));
        nameEnd.setText(String.valueOf(name[1]));
        srStart.setText(String.valueOf(sr[0]));
        srEnd.setText(String.valueOf(sr[1]));
        userStart.setText(String.valueOf(user[0]));
        userEnd.setText(String.valueOf(user[1]));
        actionStart.setText(String.valueOf(action[0]));
        actionEnd.setText(String.valueOf(action[1]));
        
        minLen.setText(String.valueOf(config.getIspwMinLineLength(65)));

        addSplitPoint(type[0]);
        addSplitPoint(type[1]);
        addSplitPoint(name[0]);
        addSplitPoint(name[1]);
        addSplitPoint(sr[0]);
        addSplitPoint(sr[1]);
        addSplitPoint(user[0]);
        addSplitPoint(user[1]);
        addSplitPoint(action[0]);
        addSplitPoint(action[1]);
        
        Collections.sort(splitPoints);
        previewPanel.repaint();
    }
    
    private void addSplitPoint(int p) {
        if (!splitPoints.contains(p)) splitPoints.add(p);
    }

    private void saveConfig() {
        try {
            Map<String, String> props = new HashMap<>();
            props.put("recon.ispw.ci_type.bounds", typeStart.getText().trim() + "," + typeEnd.getText().trim());
            props.put("recon.ispw.ci_name.bounds", nameStart.getText().trim() + "," + nameEnd.getText().trim());
            props.put("recon.ispw.sr.bounds", srStart.getText().trim() + "," + srEnd.getText().trim());
            props.put("recon.ispw.user.bounds", userStart.getText().trim() + "," + userEnd.getText().trim());
            props.put("recon.ispw.action.bounds", actionStart.getText().trim() + "," + actionEnd.getText().trim());
            props.put("recon.ispw.min_line_length", minLen.getText().trim());
            
            config.saveProperties(props);
            
            JOptionPane.showMessageDialog(this, "Settings saved successfully and applied to the Reconciliation panel.");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error saving settings: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
