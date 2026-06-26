package tso.usmc.jira.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;

import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.ui.*;
import tso.usmc.jira.util.ConfigChangeListener;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.ThemeManager;

public class JiraApiClientGui extends Application implements ConfigChangeListener {
    private ComboBox<String> certComboBox = new ComboBox<>();
    private TextField baseUrlField;
    private JiraApiService apiService;
    private MetadataCacheService metadataService;
    private JiraIssueService issueService;
    private JiraConfig jiraConfig;
    private TabPane tabs;
    private TaskBuilderPanel taskBuilderPanel;
    private WorkflowOrchestratorPanel workflowOrchestratorPanel;
    private BulkActionPanel bulkActionPanel;
    private IssueViewPanel issueViewPanel;
    private final Label statusLabel = new Label(" Ready");
    private ThemeManager themeManager;
    private Stage primaryStage;
    private Scene mainScene;
    
    private static JiraApiClientGui instance;
    
    public static JiraApiClientGui getInstance() {
        return instance;
    }

    public JiraApiClientGui() {
        instance = this;
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.jiraConfig = new JiraConfig();
        this.themeManager = new ThemeManager(jiraConfig);

        stage.setTitle("USMC TSO CCB Jira Client");

        java.net.URL iconUrl = JiraApiClientGui.class.getResource("/app_icon.png");
        if (iconUrl != null) {
            stage.getIcons().add(new javafx.scene.image.Image(iconUrl.toExternalForm()));
        }

        // Layout Assembly
        BorderPane root = new BorderPane();
        mainScene = new Scene(root, 1200, 900);

        // --- TOP PANEL: Connection Settings ---
        GridPane headerPanel = new GridPane();
        headerPanel.getStyleClass().add("connection-panel");
        headerPanel.setHgap(10);
        headerPanel.setVgap(10);
        headerPanel.setPadding(new Insets(10));

        // Row 0: CAC selection
        Label certLabel = new Label("Select CAC Certificate:");
        headerPanel.add(certLabel, 0, 0);

        certComboBox.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(certComboBox, Priority.ALWAYS);
        headerPanel.add(certComboBox, 1, 0);

        Button refreshBtn = new Button("Refresh Certs");
        refreshBtn.setOnAction(e -> loadCertificates());
        headerPanel.add(refreshBtn, 2, 0);

        Button editConfigButton = new Button("Edit Configuration");
        editConfigButton.setOnAction(e -> {
            try {
                File configFile = jiraConfig.getConfigFile();
                new ProcessBuilder("notepad.exe", configFile.getAbsolutePath()).start();
            } catch (IOException ex) {
                showError("Error", "Error opening config file: " + ex.getMessage());
            }
        });
        headerPanel.add(editConfigButton, 3, 0);

        Button editTemplatesButton = new Button("Edit Templates");
        editTemplatesButton.setOnAction(e -> {
            try {
                File templateFile = jiraConfig.getTemplateFile();
                new ProcessBuilder("notepad.exe", templateFile.getAbsolutePath()).start();
            } catch (IOException ex) {
                showError("Error", "Error opening template file: " + ex.getMessage());
            }
        });
        headerPanel.add(editTemplatesButton, 4, 0);

        // Row 1: Base URL
        Label urlLabel = new Label("Jira Base URL:");
        headerPanel.add(urlLabel, 0, 1);

        baseUrlField = new TextField(jiraConfig.getJiraBaseUrl());
        headerPanel.add(baseUrlField, 1, 1, 4, 1); // span 4 columns
        GridPane.setHgrow(baseUrlField, Priority.ALWAYS);

        // --- CENTER: TABS ---
        tabs = new TabPane();

        // Standard tabs
        tabs.getTabs().add(new Tab("Raw API Call", new RawApiPanel(this)));
        tabs.getTabs().add(new Tab("JQL Runner", new JqlRunnerPanel(this)));
        if (jiraConfig.isTabEnabled("IssueViewer")) {
            this.issueViewPanel = new IssueViewPanel(this);
            tabs.getTabs().add(new Tab("Issue Viewer", this.issueViewPanel));
        }

        if (jiraConfig.isTabEnabled("Reports")) {
            tabs.getTabs().add(new Tab("Reports", new ReportPanel(this)));
        }
        if (jiraConfig.isTabEnabled("TaskBuilder")) {
            this.taskBuilderPanel = new TaskBuilderPanel(this);
            tabs.getTabs().add(new Tab("Task Builder", this.taskBuilderPanel));
        }
        if (jiraConfig.isTabEnabled("TemplateBuilder")) {
            tabs.getTabs().add(new Tab("Template Builder", new TemplateExtractorPanel(this)));
        }
        if (jiraConfig.isTabEnabled("Reconciliation")) {
            tabs.getTabs().add(new Tab("Reconciliation", new ReconciliationPanel(this)));
        }
        if (jiraConfig.isTabEnabled("BulkActions")) {
            this.bulkActionPanel = new BulkActionPanel(this);
            tabs.getTabs().add(new Tab("Bulk Actions", this.bulkActionPanel));
        }
        if (jiraConfig.isTabEnabled("CommentSummarizer")) {
            tabs.getTabs().add(new Tab("Comment Summarizer", new CommentSummarizerPanel(this)));
        }
        if (jiraConfig.isTabEnabled("WorkflowAutomation")) {
            tabs.getTabs().add(new Tab("Workflow Automation", new WorkflowPanel(this, this.jiraConfig)));
        }
        if (jiraConfig.isTabEnabled("WorkflowOrchestrator")) {
            this.workflowOrchestratorPanel = new WorkflowOrchestratorPanel(this);
            tabs.getTabs().add(new Tab("Workflow Orchestrator", this.workflowOrchestratorPanel));
        }

        // Theme Settings
        tabs.getTabs().add(new Tab("Theme Settings", new ThemeSettingsPanel(this, this.jiraConfig, this.themeManager)));

        // Disable closing tabs
        for (Tab tab : tabs.getTabs()) {
            tab.setClosable(false);
        }

        // --- BOTTOM: STATUS BAR ---
        HBox bottomPanel = new HBox();
        bottomPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        bottomPanel.getChildren().add(statusLabel);

        root.setTop(headerPanel);
        root.setCenter(tabs);
        root.setBottom(bottomPanel);

        loadCertificates();
        this.jiraConfig.addConfigChangeListener(this);
        
        themeManager.applyTheme(mainScene);

        stage.setScene(mainScene);
        stage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    public TaskBuilderPanel getTaskBuilderPanel() { return this.taskBuilderPanel; }
    public WorkflowOrchestratorPanel getWorkflowOrchestratorPanel() { return this.workflowOrchestratorPanel; }
    public BulkActionPanel getBulkActionPanel() { return this.bulkActionPanel; }
    public IssueViewPanel getIssueViewPanel() { return this.issueViewPanel; }
    
    public void loadAndShowIssue(String issueKey) {
        if (issueViewPanel != null) {
            issueViewPanel.loadIssue(issueKey);
            showPanel("Issue Viewer");
        } else {
            tso.usmc.jira.util.JiraUtils.browseIssue(getBaseUrl(), issueKey);
        }
    }
    
    public JiraConfig getJiraConfig() { return this.jiraConfig; }
    public ThemeManager getThemeManager() { return this.themeManager; }
    public Stage getPrimaryStage() { return this.primaryStage; }
    public Scene getMainScene() { return this.mainScene; }

    public void showPanel(String panelName) {
        for (Tab tab : tabs.getTabs()) {
            if (tab.getText().equals(panelName)) {
                tabs.getSelectionModel().select(tab);
                break;
            }
        }
    }

    private void loadCertificates() {
        final String CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2";
        certComboBox.getItems().clear();
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
                        certComboBox.getItems().add(alias);
                    }
                }
            }
            if (!certComboBox.getItems().isEmpty()) {
                certComboBox.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            showError("CAC Certificate Error", "Error loading CAC certificates: " + e.getMessage());
        }
    }

    public JiraApiService getService() throws Exception {
        String selectedAlias = certComboBox.getSelectionModel().getSelectedItem();
        if (selectedAlias == null) throw new Exception("No CAC certificate selected.");
        if (apiService == null) apiService = new JiraApiService(selectedAlias);
        String verbose = jiraConfig.getProperty("VERBOSE_API_LOGS");
        apiService.setLoggingEnabled("YES".equalsIgnoreCase(verbose));
        return apiService;
    }

    public MetadataCacheService getMetadataService() throws Exception {
        if (metadataService == null) {
            JiraApiService service = null;
            try {
                service = getService();
            } catch (Exception e) {
                // Ignore for initial startup cache loading
            }
            metadataService = new MetadataCacheService(service, getBaseUrl());
        } else if (metadataService.getApiService() == null) {
            try {
                metadataService.setApiService(getService());
            } catch (Exception e) {
                // Ignore
            }
        }
        return metadataService;
    }

    public JiraIssueService getIssueService() throws Exception {
        if (issueService == null) issueService = new JiraIssueService(getService(), getBaseUrl(), getMetadataService(), jiraConfig);
        return issueService;
    }

    public String getBaseUrl() {
        String url = baseUrlField.getText().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void onConfigChanged() {
        Platform.runLater(() -> {
            System.out.println("GUI Detected a configuration change!");
            baseUrlField.setText(jiraConfig.getJiraBaseUrl());
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            statusLabel.setText(" Configuration updated: " + time);
            themeManager.applyTheme(mainScene);
        });
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
