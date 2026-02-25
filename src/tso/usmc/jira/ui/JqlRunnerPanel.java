package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JsonUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class JqlRunnerPanel extends JPanel implements tso.usmc.jira.util.ConfigChangeListener {

    private final JiraApiClientGui mainFrame;
    private final tso.usmc.jira.util.JiraConfig jiraConfig;

    // UI Components
    private final JTextArea jqlArea = new JTextArea("issuetype = Bug AND status = 'To Do' ORDER BY created DESC");
    private final JTextField fieldsField = new JTextField("key, summary, status, assignee, issuelinks");
    private final JButton executeBtn = new JButton("Execute JQL");
    private final JLabel statusLabel = new JLabel("Enter a JQL query and click Execute.");

    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable resultsTable = new JTable(tableModel);
    private String selectedIssueKey;

    public JqlRunnerPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.jiraConfig = mainFrame.getJiraConfig();
        this.jiraConfig.addConfigChangeListener(this);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- TOP: Input Configuration Panel ---
        JPanel configPanel = new JPanel(new BorderLayout(10, 10));
        
        JScrollPane jqlScroll = new JScrollPane(jqlArea);
        jqlArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        jqlScroll.setBorder(BorderFactory.createTitledBorder("JQL Query"));
        jqlScroll.setPreferredSize(new Dimension(0, 100));
        configPanel.add(jqlScroll, BorderLayout.CENTER);

        JPanel fieldsPanel = new JPanel(new BorderLayout(5, 5));
        fieldsPanel.add(new JLabel("Fields to display:"), BorderLayout.WEST);
        fieldsPanel.add(fieldsField, BorderLayout.CENTER);
        fieldsPanel.add(executeBtn, BorderLayout.EAST);
        configPanel.add(fieldsPanel, BorderLayout.SOUTH);
        
        // --- CENTER: Results Table ---
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setAutoCreateRowSorter(true); 
        
        // --- NEW: Enable selection of individual cells ---
        resultsTable.setCellSelectionEnabled(true);
        
        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Results"));

        // --- BOTTOM: Status Bar ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);

        // --- Layout Assembly ---
        add(configPanel, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // --- Action Listener ---
        executeBtn.addActionListener(e -> executeJql());
        setupContextMenu();
    }

    @Override
    public void onConfigChanged() {
        // Context menu is built on-the-fly or needs refresh?
        // Current setupContextMenu attaches a listener that builds it.
    }

    // NEW: Method to set up the right-click context menu on the results table
    private void setupContextMenu() {
        // Add a mouse listener to the table to detect right-clicks
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                JTable source = (JTable) e.getSource();
                int row = source.rowAtPoint(e.getPoint());

                if (row >= 0 && row < source.getRowCount()) {
                    source.setRowSelectionInterval(row, row);
                    int keyColumnIndex = -1;
                    for (int i = 0; i < tableModel.getColumnCount(); i++) {
                        if ("key".equalsIgnoreCase(tableModel.getColumnName(i))) {
                            keyColumnIndex = i;
                            break;
                        }
                    }

                    if (keyColumnIndex != -1) {
                        selectedIssueKey = (String) source.getModel().getValueAt(source.convertRowIndexToModel(row), keyColumnIndex);
                        
                        // BUILD DYNAMIC MENU
                        final JPopupMenu contextMenu = new JPopupMenu();
                        
                        if (jiraConfig.isTabEnabled("TaskBuilder")) {
                            JMenuItem openInTB = new JMenuItem("Create Sub-Task in TaskBuilder");
                            openInTB.addActionListener(al -> {
                                mainFrame.showPanel("Task Builder");
                                if (mainFrame.getTaskBuilderPanel() != null) {
                                    mainFrame.getTaskBuilderPanel().setParentTicket(selectedIssueKey);
                                }
                            });
                            contextMenu.add(openInTB);
                            contextMenu.addSeparator();

                            // Add templates from config
                            String[] templateKeys = jiraConfig.getTemplateKeys();
                            for (String tKey : templateKeys) {
                                String label = jiraConfig.getTemplateLabel(tKey);
                                String text = jiraConfig.getTemplateText(tKey);
                                if (label != null && text != null) {
                                    JMenuItem item = new JMenuItem(label);
                                    item.addActionListener(al -> {
                                        mainFrame.showPanel("Task Builder");
                                        TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                                        if (tbp != null) {
                                            tbp.setInputAreaText("PARENT_TICKET:" + selectedIssueKey + "\n" + text);
                                        }
                                    });
                                    contextMenu.add(item);
                                }
                            }
                        }

                        if (contextMenu.getComponentCount() > 0) {
                            contextMenu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });
    }

    private void executeJql() {
        String jql = jqlArea.getText().trim();
        if (jql.isEmpty()) {
            JOptionPane.showMessageDialog(this, "JQL query cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusLabel.setText("Executing query...");
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("jql", jql);
                
                String fieldsText = fieldsField.getText().trim();
                if (!fieldsText.isEmpty()) {
                    payload.put("fields", fieldsText.split("\\s*,\\s*"));
                }
                
                payload.put("maxResults", 500);

                String rawResponse = mainFrame.getService().executeRequest(
                    mainFrame.getBaseUrl() + "/rest/api/2/search",
                    "POST",
                    payload.toString()
                );

                JSONObject responseJson = new JSONObject(rawResponse);
                JSONArray issues = responseJson.getJSONArray("issues");
                
                if (issues.length() == 0) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Query executed successfully. No issues found."));
                    return;
                }

                String[] columns = fieldsText.isEmpty() ? JSONObject.getNames(issues.getJSONObject(0).getJSONObject("fields")) : fieldsText.split("\\s*,\\s*");
                
                SwingUtilities.invokeLater(() -> {
                    tableModel.setColumnIdentifiers(columns);

                    for (int i = 0; i < issues.length(); i++) {
                        JSONObject issue = issues.getJSONObject(i);
                        Object[] row = new Object[columns.length];

                        for (int j = 0; j < columns.length; j++) {
                            row[j] = getFieldValue(issue, columns[j]);
                        }
                        tableModel.addRow(row);
                    }
                    statusLabel.setText("Success! Found " + issues.length() + " issues. (Max 500 displayed)");
                });

            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                JOptionPane.showMessageDialog(this, "API Error:\n" + ex.getMessage() + "\n\n" + sw.toString(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Error executing JQL."));
            }
        }).start();
    }

    private String getFieldValue(JSONObject issue, String fieldName) {
        if (!issue.has("fields")) return "N/A";
        JSONObject fields = issue.getJSONObject("fields");

        if ("key".equalsIgnoreCase(fieldName)) {
            return issue.optString("key", "N/A");
        }
        
        if ("issuelinks".equalsIgnoreCase(fieldName)) {
            if (!fields.has("issuelinks") || fields.isNull("issuelinks") || fields.getJSONArray("issuelinks").length() == 0) {
                return "---";
            }
            
            JSONArray links = fields.getJSONArray("issuelinks");
            StringBuilder formattedLinks = new StringBuilder();
            
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                JSONObject linkType = link.getJSONObject("type");
                
                String linkKey = "N/A";
                String linkDescription = "";
                
                if (link.has("inwardIssue")) {
                    linkDescription = linkType.getString("inward");
                    linkKey = link.getJSONObject("inwardIssue").getString("key");
                } else if (link.has("outwardIssue")) {
                    linkDescription = linkType.getString("outward");
                    linkKey = link.getJSONObject("outwardIssue").getString("key");
                }
                
                if (i > 0) {
                    formattedLinks.append(", ");
                }
                formattedLinks.append(linkDescription).append(" ").append(linkKey);
            }
            return formattedLinks.toString();
        }

        if (!fields.has(fieldName) || fields.isNull(fieldName)) {
            return "---";
        }

        Object field = fields.get(fieldName);

        if (field instanceof JSONObject) {
            JSONObject nestedObj = (JSONObject) field;
            if (nestedObj.has("name")) {
                return nestedObj.getString("name");
            } else if (nestedObj.has("displayName")) {
                return nestedObj.getString("displayName");
            } else {
                return "[Object]";
            }
        }
        return field.toString();
    }
}