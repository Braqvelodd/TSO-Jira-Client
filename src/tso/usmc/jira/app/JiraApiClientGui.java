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
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.ui.BulkActionPanel;
import tso.usmc.jira.ui.CommentSummarizerPanel;
import tso.usmc.jira.ui.JqlRunnerPanel;
import tso.usmc.jira.ui.RawApiPanel;
import tso.usmc.jira.ui.ReconciliationPanel;
import tso.usmc.jira.ui.ReportPanel;
import tso.usmc.jira.ui.TaskBuilderPanel;
import tso.usmc.jira.ui.TemplateExtractorPanel;
import tso.usmc.jira.ui.WorkflowOrchestratorPanel;
import tso.usmc.jira.ui.WorkflowPanel;
import tso.usmc.jira.util.ConfigChangeListener;
import tso.usmc.jira.util.JiraConfig; 
import tso.usmc.jira.util.ThemeManager; 


public class JiraApiClientGui extends JFrame implements ConfigChangeListener {
    private JComboBox<String> certComboBox = new JComboBox<>();
    private JTextField baseUrlField;
    private JiraApiService apiService;
    private MetadataCacheService metadataService;
    private JiraIssueService issueService;
    private final JiraConfig jiraConfig;
    private JTabbedPane tabs;
    private TaskBuilderPanel taskBuilderPanel;
    private WorkflowOrchestratorPanel workflowOrchestratorPanel;
    private final JLabel statusLabel = new JLabel(" Ready");
    private final ThemeManager themeManager;

    public JiraApiClientGui() {
        this.jiraConfig = new JiraConfig();
        this.themeManager = new ThemeManager(jiraConfig);
        
        // Create custom content pane to support gradient backdrop
        JPanel contentPane = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                String theme = jiraConfig.getTheme();
                if (!"default".equals(theme)) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    int w = getWidth();
                    int h = getHeight();
                    Color color1 = new Color(15, 23, 42);   // Slate 900
                    Color color2 = new Color(30, 41, 59);   // Slate 800
                    
                    GradientPaint gp = new GradientPaint(0, 0, color1, w, h, color2);
                    g2.setPaint(gp);
                    g2.fillRect(0, 0, w, h);
                    
                    // Indigo glow
                    g2.setPaint(new RadialGradientPaint(
                        new Point(w * 3 / 4, h / 4),
                        Math.max(w, h) * 0.6f,
                        new float[]{0.0f, 1.0f},
                        new Color[]{new Color(99, 102, 241, 20), new Color(0, 0, 0, 0)}
                    ));
                    g2.fillRect(0, 0, w, h);
                    g2.dispose();
                }
            }
        };
        setContentPane(contentPane);

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
                    File configFile = jiraConfig.getConfigFile();
                    new ProcessBuilder("notepad.exe", configFile.getAbsolutePath()).start();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, "Error opening config file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
        tabs.addTab("Raw API Call", new RawApiPanel(this));
        tabs.addTab("JQL Runner", new JqlRunnerPanel(this));

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
        if (jiraConfig.isTabEnabled("WorkflowOrchestrator")) {
            this.workflowOrchestratorPanel = new WorkflowOrchestratorPanel(this);
            tabs.addTab("Workflow Orchestrator", this.workflowOrchestratorPanel);
        }

        // Add Theme Settings tab
        tabs.addTab("Theme Settings", new tso.usmc.jira.ui.ThemeSettingsPanel(this, this.jiraConfig, this.themeManager));

        // Layout Assembly
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEtchedBorder());
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        loadCertificates();
        themeManager.applyTheme(this);
    }
    public TaskBuilderPanel getTaskBuilderPanel() { return this.taskBuilderPanel; }
    public WorkflowOrchestratorPanel getWorkflowOrchestratorPanel() { return this.workflowOrchestratorPanel; }
    public JiraConfig getJiraConfig() { return this.jiraConfig; }
    public ThemeManager getThemeManager() { return this.themeManager; }
    public void showPanel(String panelName) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equals(panelName)) {
                tabs.setSelectedIndex(i);
                break;
            }
        }
    }
    public JFrame getMainFrame() { return this; }
    
    private void loadCertificates() {
        final String CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2";
        certComboBox.removeAllItems();
        try {
            KeyStore ks = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
            ks.load(null, null);
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    List<String> extendedKeyUsage = x509Cert.getExtendedKeyUsage();
                    if (extendedKeyUsage != null && extendedKeyUsage.contains(CLIENT_AUTH_OID)) {
                        certComboBox.addItem(alias);
                    }
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading CAC certificates: " + e.getMessage());
        }
    }

    public JiraApiService getService() throws Exception {
        String selectedAlias = (String) certComboBox.getSelectedItem();
        if (selectedAlias == null) throw new Exception("No CAC certificate selected.");
        if (apiService == null) apiService = new JiraApiService(selectedAlias);
        String verbose = jiraConfig.getProperty("VERBOSE_API_LOGS");
        apiService.setLoggingEnabled("YES".equalsIgnoreCase(verbose));
        return apiService;
    }

    public MetadataCacheService getMetadataService() throws Exception {
        if (metadataService == null) metadataService = new MetadataCacheService(getService(), getBaseUrl());
        return metadataService;
    }

    public JiraIssueService getIssueService() throws Exception {
        if (issueService == null) issueService = new JiraIssueService(getService(), getBaseUrl(), getMetadataService());
        return issueService;
    }

    public String getBaseUrl() {
        String url = baseUrlField.getText().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void onConfigChanged() {
        SwingUtilities.invokeLater(() -> {
            System.out.println("GUI Detected a configuration change!");
            baseUrlField.setText(jiraConfig.getJiraBaseUrl());
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            statusLabel.setText(" Configuration updated: " + time);
            statusLabel.setForeground(new Color(0, 120, 0)); 
            themeManager.applyTheme(this);
        });
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            try { new JiraApiClientGui().setVisible(true); } catch (Exception e) {
                System.err.println("Failed to start Jira API Client: " + e.getMessage());
            }
        });
    }
}
