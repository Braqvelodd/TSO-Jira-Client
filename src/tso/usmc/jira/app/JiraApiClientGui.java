package tso.usmc.jira.app;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import javax.swing.*;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.ui.BulkActionPanel;
import tso.usmc.jira.ui.CommentSummarizerPanel;
import tso.usmc.jira.ui.JqlRunnerPanel;
import tso.usmc.jira.ui.RawApiPanel;
import tso.usmc.jira.ui.ReconciliationPanel;
import tso.usmc.jira.ui.ReportPanel; // --- NEW: Import Certificate
import tso.usmc.jira.ui.TaskBuilderPanel;
import tso.usmc.jira.ui.TemplateExtractorPanel;
import tso.usmc.jira.ui.WorkflowPanel;
import tso.usmc.jira.util.ConfigChangeListener;
import tso.usmc.jira.util.JiraConfig; 


public class JiraApiClientGui extends JFrame implements ConfigChangeListener {
    private JComboBox<String> certComboBox = new JComboBox<>();
    private JTextField baseUrlField;
    private JiraApiService apiService;
    private final JiraConfig jiraConfig;
    private JTabbedPane tabs;
    private TaskBuilderPanel taskBuilderPanel;

    public JiraApiClientGui() {
        this.jiraConfig = new JiraConfig();
        this.baseUrlField = new JTextField(jiraConfig.getJiraBaseUrl());
        this.jiraConfig.addConfigChangeListener(this);
        setTitle("USMC TSO CCB Jira Client");
        setSize(1200, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- TOP PANEL: Identity and Connection ---
        JPanel headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBorder(BorderFactory.createTitledBorder("Connection Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: CAC Selection
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        headerPanel.add(new JLabel("Select CAC Certificate:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        headerPanel.add(certComboBox, gbc);

        JButton refreshBtn = new JButton("Refresh Certs");
        refreshBtn.addActionListener(e -> loadCertificates());
        gbc.gridx = 2; gbc.weightx = 0;
        headerPanel.add(refreshBtn, gbc);
        JButton editConfigButton = new JButton("Edit Configuration");
        editConfigButton.addActionListener(e -> {
            try {
                // Get the config file path from our JiraConfig instance
                    File configFile = jiraConfig.getConfigFile();
                    
                    // Use ProcessBuilder to safely open the file with the default system editor (Notepad on Windows)
                    // This is more robust than Runtime.getRuntime().exec()
                    new ProcessBuilder("notepad.exe", configFile.getAbsolutePath()).start();
                    
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                        null, 
                        "Error opening config file: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE
                    );
                    ex.printStackTrace(); // For debugging
                }
            });
        gbc.gridx = 3; gbc.weightx = 0;
        headerPanel.add(editConfigButton, gbc);

        JButton editTemplatesButton = new JButton("Edit Templates");
        editTemplatesButton.addActionListener(e -> {
            try {
                File templateFile = jiraConfig.getTemplateFile();
                new ProcessBuilder("notepad.exe", templateFile.getAbsolutePath()).start();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Error opening template file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        gbc.gridx = 4; gbc.weightx = 0;
        headerPanel.add(editTemplatesButton, gbc);

        // Row 1: Base URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        headerPanel.add(new JLabel("Jira Base URL:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        headerPanel.add(baseUrlField, gbc);

        // --- CENTER: TABS ---
        tabs = new JTabbedPane();
        
        // Always Enabled Tabs
        tabs.addTab("Raw API Call", new RawApiPanel(this));
        tabs.addTab("JQL Runner", new JqlRunnerPanel(this));

        // Optional Tabs
        if (jiraConfig.isTabEnabled("Reports")) {
            tabs.addTab("Reports", new ReportPanel(this));
        }
        
        if (jiraConfig.isTabEnabled("TaskBuilder")) {
            this.taskBuilderPanel = new TaskBuilderPanel(this);
            tabs.addTab("Task Builder", this.taskBuilderPanel);
        }

        if (jiraConfig.isTabEnabled("TemplateBuilder")) {
            tabs.addTab("Template Builder", new TemplateExtractorPanel(this));
        }

        if (jiraConfig.isTabEnabled("Reconciliation")) {
            tabs.addTab("Reconciliation", new ReconciliationPanel(this));
        }

        if (jiraConfig.isTabEnabled("BulkActions")) {
            tabs.addTab("Bulk Actions", new BulkActionPanel(this));
        }

        if (jiraConfig.isTabEnabled("CommentSummarizer")) {
            tabs.addTab("Comment Summarizer", new CommentSummarizerPanel(this));
        }

        if (jiraConfig.isTabEnabled("WorkflowAutomation")) {
            tabs.addTab("Workflow Automation", new WorkflowPanel(this, this.jiraConfig));
        }

        // Layout Assembly
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // Initial Cert Load
        loadCertificates();
    }
    public TaskBuilderPanel getTaskBuilderPanel() {
        return this.taskBuilderPanel;
    }
    public JiraConfig getJiraConfig() {
        return this.jiraConfig;
    }
    public void showPanel(String panelName) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equals(panelName)) {
                tabs.setSelectedIndex(i);
                break;
            }
        }
    }
    public JFrame getMainFrame() {
        // IMPORTANT: Replace 'frame' with the actual name of your main JFrame variable
        // if it is different.
        return this;
    }
    /**
     * Reads from the Windows-MY store to populate the CAC dropdown.
     */
    private void loadCertificates() {
        final String CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2";
        certComboBox.removeAllItems();
        try {
            KeyStore ks = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
            ks.load(null, null);
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                
                // --- NEW: Inspect each certificate before adding it ---
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    List<String> extendedKeyUsage = x509Cert.getExtendedKeyUsage();
                    
                    // Only add the alias if its purpose includes Client Authentication
                    if (extendedKeyUsage != null && extendedKeyUsage.contains(CLIENT_AUTH_OID)) {
                        certComboBox.addItem(alias);
                    }
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading CAC certificates: " + e.getMessage());
        }
    }

    /**
     * Returns the service. Initializes it if it doesn't exist.
     */
    public JiraApiService getService() throws Exception {
        String selectedAlias = (String) certComboBox.getSelectedItem();
        if (selectedAlias == null) {
            throw new Exception("No CAC certificate selected.");
        }
        // If service doesn't exist or alias changed, create new service
        if (apiService == null) {
            apiService = new JiraApiService(selectedAlias);
        }
        return apiService;
    }

    public String getBaseUrl() {
        String url = baseUrlField.getText().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    @Override
    public void onConfigChanged() {
        // UI updates must be run on the Event Dispatch Thread (EDT).
        // SwingUtilities.invokeLater ensures this happens safely.
        SwingUtilities.invokeLater(() -> {
            System.out.println("GUI Detected a configuration change!");

            // Update the Base URL field with the new value from config
            baseUrlField.setText(jiraConfig.getJiraBaseUrl());

            // Provide feedback to the user that the reload happened.
            JOptionPane.showMessageDialog(this,
                    "Configuration has been reloaded.",
                    "Config Reloaded",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }
    public static void main(String[] args) {
        // Set Look and Feel to System (Windows) for better UI
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            try {
                // <-- MODIFIED: If config loading fails in the constructor, this will catch it.
                new JiraApiClientGui().setVisible(true);
            } catch (Exception e) {
                // The JiraConfig loader already shows a user-friendly error dialog.
                // This catch block prevents the application from starting in an invalid state.
                System.err.println("Failed to start Jira API Client: " + e.getMessage());
            }
        });
    }
    
}