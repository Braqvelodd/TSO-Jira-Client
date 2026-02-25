package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JsonUtils;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class RawApiPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private final JTextField endpointField = new JTextField("/rest/api/2/issue/TSO-123");
    private final JTextArea requestArea = new JTextArea();
    private final JTextArea responseArea = new JTextArea();
    private final JComboBox<ApiTemplate> templateCombo = new JComboBox<>();
    
    // Map to keep track of buttons for dynamic visibility
    private final Map<String, JButton> actionButtons = new HashMap<>();

    private static class ApiTemplate {
        String label, method, endpoint, body;
        ApiTemplate(String label, String method, String endpoint, String body) {
            this.label = label; this.method = method; this.endpoint = endpoint; this.body = body;
        }
        @Override public String toString() { return label; }
    }

    public RawApiPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;

        // 0. Template Row
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        add(new JLabel("Template:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        loadTemplates();
        add(templateCombo, gbc);
        templateCombo.addActionListener(e -> applyTemplate());

        // 1. Endpoint Row
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        add(new JLabel("Endpoint:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(endpointField, gbc);

        // 2. Request JSON Area (Label)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weighty = 0;
        add(new JLabel("Request Body (JSON for POST/PUT):"), gbc);

        // 3. Request JSON ScrollPane
        gbc.gridy = 3; gbc.weighty = 0.4;
        requestArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(requestArea), gbc);

        // 4. Button Row
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10)); 
        
        JButton getBtn = new JButton("Execute GET");
        JButton postBtn = new JButton("Execute POST");
        JButton putBtn = new JButton("Execute PUT");
        JButton delBtn = new JButton("Execute DELETE");
        
        getBtn.addActionListener(e -> callApi("GET"));
        postBtn.addActionListener(e -> callApi("POST"));
        putBtn.addActionListener(e -> callApi("PUT"));
        delBtn.addActionListener(e -> callApi("DELETE"));
        
        // Add to map for easy management
        actionButtons.put("GET", getBtn);
        actionButtons.put("POST", postBtn);
        actionButtons.put("PUT", putBtn);
        actionButtons.put("DELETE", delBtn);

        btnPanel.add(getBtn);
        btnPanel.add(putBtn);
        btnPanel.add(postBtn);
        btnPanel.add(delBtn);
        
        gbc.gridy = 4; gbc.weighty = 0;
        add(btnPanel, gbc);

        // 5. Response Area (Label)
        gbc.gridy = 5;
        add(new JLabel("Response:"), gbc);

        // 6. Response ScrollPane
        gbc.gridy = 6; gbc.weighty = 0.5;
        responseArea.setEditable(false);
        responseArea.setBackground(new Color(245, 245, 245));
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(responseArea), gbc);
    }

    private void loadTemplates() {
        templateCombo.addItem(new ApiTemplate("--- Select Template ---", "", "", ""));
        String[] keys = mainFrame.getJiraConfig().getRawApiTemplateKeys();
        for (String key : keys) {
            String val = mainFrame.getJiraConfig().getRawApiTemplate(key);
            if (val != null) {
                String[] parts = val.split("\\|", -1);
                if (parts.length >= 4) {
                    String body = parts[3].replace("\\n", "\n");
                    templateCombo.addItem(new ApiTemplate(parts[0], parts[1], parts[2], body));
                }
            }
        }
    }

    private void applyTemplate() {
        ApiTemplate t = (ApiTemplate) templateCombo.getSelectedItem();
        
        if (t != null && !t.method.isEmpty()) {
            endpointField.setText(t.endpoint);
            String body = t.body;
            if (body != null && !body.trim().isEmpty()) {
                requestArea.setText(JsonUtils.prettyPrintJson(body));
            } else {
                requestArea.setText("");
            }
            
            // Filter buttons: Show only the matching method
            for (String method : actionButtons.keySet()) {
                actionButtons.get(method).setVisible(method.equalsIgnoreCase(t.method));
            }
        } else {
            // Reset to default endpoint instead of empty
            endpointField.setText("/rest/api/2/issue/TSO-123");
            requestArea.setText("");
            
            // Reset: Show all buttons
            for (JButton btn : actionButtons.values()) {
                btn.setVisible(true);
            }
        }
        
        this.revalidate();
        this.repaint();
    }

    private void callApi(String method) {
        String fullUrl = mainFrame.getBaseUrl() + endpointField.getText().trim();
        String body = ("POST".equals(method) || "PUT".equals(method)) ? requestArea.getText() : null;
        
        responseArea.setForeground(Color.BLACK);
        responseArea.setText("Sending " + method + " request to: " + fullUrl + "...");
        
        new Thread(() -> {
            try {
                String rawResponse = mainFrame.getService().executeRequest(fullUrl, method, body);
                final String formatted = (rawResponse == null || rawResponse.trim().isEmpty())
                    ? "Request successful (204 No Content)"
                    : JsonUtils.prettyPrintJson(rawResponse);
                
                SwingUtilities.invokeLater(() -> responseArea.setText(formatted));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    responseArea.setForeground(Color.RED);
                    responseArea.setText("ERROR: " + ex.getMessage() + "\n\nStack Trace:\n" + getStackTrace(ex));
                });
            }
        }).start();
    }

    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
