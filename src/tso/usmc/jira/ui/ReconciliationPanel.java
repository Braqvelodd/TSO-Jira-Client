package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.service.CompanionRunner;
import tso.usmc.jira.util.ExecutionService;
import tso.usmc.jira.util.ExcelExportUtil;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReconciliationPanel extends BorderPane {



    // Helper classes
    private static class JiraReconInfo {
        String subtaskKey;
        String subtaskSummary;
        String parentKey;
        String parentSummary;
        String assignee = "Unassigned";
        String status = "N/A";
    }

    private static class IspwReconInfo {
        String fullTaskName;
        String envLvl = "";
        String srNumber;
        String userId;
        String action;
    }

    public static class IspwRow {
        public final SimpleStringProperty type;
        public final SimpleStringProperty name;
        public final SimpleStringProperty envLvl;
        public final SimpleStringProperty action;
        public final SimpleStringProperty srNumber;
        public final SimpleStringProperty userId;

        public IspwRow(String type, String name, String envLvl, String action, String sr, String user) {
            this.type = new SimpleStringProperty(type);
            this.name = new SimpleStringProperty(name);
            this.envLvl = new SimpleStringProperty(envLvl);
            this.action = new SimpleStringProperty(action);
            this.srNumber = new SimpleStringProperty(sr);
            this.userId = new SimpleStringProperty(user);
        }
    }

    public static class JiraRow {
        public final SimpleStringProperty type;
        public final SimpleStringProperty name;
        public final SimpleStringProperty parent;
        public final SimpleStringProperty assignee;
        public final SimpleStringProperty status;
        public final SimpleStringProperty link;

        public JiraRow(String type, String name, String parent, String assignee, String status, String link) {
            this.type = new SimpleStringProperty(type);
            this.name = new SimpleStringProperty(name);
            this.parent = new SimpleStringProperty(parent);
            this.assignee = new SimpleStringProperty(assignee);
            this.status = new SimpleStringProperty(status);
            this.link = new SimpleStringProperty(link);
        }
    }

    private static class JiraIssueInfo {
        String key;
        String summary = "";
        String description = "";
        String status = "N/A";
        String assignee = "Unassigned";
        boolean isReleaseManagement = false;
    }

    public static class MatchRow {
        public final SimpleStringProperty type;
        public final SimpleStringProperty name;
        public final SimpleStringProperty jiraKey;
        public final SimpleStringProperty status;
        public final SimpleStringProperty assignee;
        public final SimpleStringProperty envLvl;
        public final SimpleStringProperty ispwAction;
        public final SimpleStringProperty srNumber;
        public final SimpleStringProperty ispwUser;
        public final SimpleStringProperty link;
        public final SimpleStringProperty notes;

        public MatchRow(String type, String name, String key, String status, String assignee, String envLvl, String action, String sr, String user, String link, String notes) {
            this.type = new SimpleStringProperty(type);
            this.name = new SimpleStringProperty(name);
            this.jiraKey = new SimpleStringProperty(key);
            this.status = new SimpleStringProperty(status);
            this.assignee = new SimpleStringProperty(assignee);
            this.envLvl = new SimpleStringProperty(envLvl);
            this.ispwAction = new SimpleStringProperty(action);
            this.srNumber = new SimpleStringProperty(sr);
            this.ispwUser = new SimpleStringProperty(user);
            this.link = new SimpleStringProperty(link);
            this.notes = new SimpleStringProperty(notes != null ? notes : "");
        }
    }

    private final JiraApiClientGui mainFrame;

    // UI Components
    private final TextArea jiraParentKeysArea;
    private final TextArea ispwReportArea = new TextArea();
    private final Button fetchMainframeBtn = new Button("Fetch from Mainframe");
    private final Button configureColumnsBtn = new Button("Configure Columns...");
    private final Button compareBtn = new Button("Compare Jira & ISPW");
    private final Button exportExcelBtn = new Button("Export to Excel (.xlsx)");
    private final Label statusLabel = new Label("Ready. Enter Jira keys, paste ISPW report, and click Compare.");

    private final TableView<IspwRow> onlyInIspwTable = new TableView<>();
    private final TableView<JiraRow> onlyInJiraTable = new TableView<>();
    private final TableView<MatchRow> matchesTable = new TableView<>();

    // Data holders
    private Map<String, JiraReconInfo> jiraTaskMap = new HashMap<>();
    private List<JiraIssueInfo> jiraAllIssuesList = new ArrayList<>();
    private Map<String, IspwReconInfo> ispwTaskMap = new HashMap<>();

    public ReconciliationPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.jiraParentKeysArea = new TextArea(mainFrame.getJiraConfig().getReconciliationParentKeys());
        setPadding(new Insets(10));

        // --- UI Setup ---
        GridPane topPanel = new GridPane();
        topPanel.setHgap(10);
        topPanel.setVgap(10);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        topPanel.getColumnConstraints().addAll(col1, col2);

        // Card 1: Jira Input
        VBox jiraPanel = new VBox(5);
        jiraPanel.getStyleClass().add("card");
        jiraPanel.setPadding(new Insets(10));

        HBox jiraHeader = new HBox(10);
        jiraHeader.setAlignment(Pos.CENTER_LEFT);
        jiraHeader.setMinHeight(28);
        Label jiraTitle = new Label("1. Jira Input");
        jiraTitle.getStyleClass().add("card-title");
        jiraHeader.getChildren().add(jiraTitle);

        jiraParentKeysArea.setPrefHeight(150);
        VBox.setVgrow(jiraParentKeysArea, Priority.ALWAYS);
        jiraPanel.getChildren().addAll(jiraHeader, jiraParentKeysArea);
        topPanel.add(jiraPanel, 0, 0);

        // Card 2: ISPW Report with Option C Header Configuration Action
        VBox ispwPanel = new VBox(5);
        ispwPanel.getStyleClass().add("card");
        ispwPanel.setPadding(new Insets(10));

        HBox ispwHeader = new HBox(8);
        ispwHeader.setAlignment(Pos.CENTER_LEFT);
        ispwHeader.setMinHeight(28);
        Label ispwTitle = new Label("2. Paste ISPW Report");
        ispwTitle.getStyleClass().add("card-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        ispwHeader.getChildren().addAll(ispwTitle, headerSpacer, fetchMainframeBtn, configureColumnsBtn);

        ispwReportArea.setPrefHeight(150);
        VBox.setVgrow(ispwReportArea, Priority.ALWAYS);
        ispwPanel.getChildren().addAll(ispwHeader, ispwReportArea);
        topPanel.add(ispwPanel, 1, 0);

        setTop(topPanel);

        // Center section: Compare action + TabPane results
        BorderPane centerContainer = new BorderPane();
        BorderPane.setMargin(centerContainer, new Insets(10, 0, 0, 0));

        HBox comparePanel = new HBox(12);
        comparePanel.setAlignment(Pos.CENTER);
        comparePanel.setPadding(new Insets(5, 0, 10, 0));
        comparePanel.getChildren().addAll(compareBtn, exportExcelBtn);
        centerContainer.setTop(comparePanel);

        TabPane resultsTabs = new TabPane();
        resultsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // 1. Setup Table 1: onlyInIspwTable
        setupIspwTable();
        Tab tabIspw = new Tab("Only in ISPW (Not in Jira)", onlyInIspwTable);
        
        // 2. Setup Table 2: onlyInJiraTable
        setupJiraTable();
        Tab tabJira = new Tab("Only in Jira (Not in ISPW)", onlyInJiraTable);
        
        // 3. Setup Table 3: matchesTable
        setupMatchesTable();
        Tab tabMatches = new Tab("Matches (Both)", matchesTable);

        setupTableKeys(onlyInIspwTable);
        setupTableKeys(onlyInJiraTable);
        setupTableKeys(matchesTable);

        resultsTabs.getTabs().addAll(tabIspw, tabJira, tabMatches);
        centerContainer.setCenter(resultsTabs);
        setCenter(centerContainer);

        // --- Bottom: Status ---
        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);
        setBottom(statusPanel);

        fetchMainframeBtn.setOnAction(e -> handleFetchFromMainframe());
        ContextMenu companionMenu = new ContextMenu();
        MenuItem configCompanionItem = new MenuItem("Configure Mainframe Settings...");
        configCompanionItem.setOnAction(e -> openCompanionConfigDialog());
        companionMenu.getItems().add(configCompanionItem);
        fetchMainframeBtn.setContextMenu(companionMenu);

        compareBtn.setOnAction(e -> performComparison());
        exportExcelBtn.setOnAction(e -> exportToExcel());
        configureColumnsBtn.setOnAction(e -> {
            String text = ispwReportArea.getText();
            if (text.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Warning", "Please paste an ISPW report first to use as a template.");
                return;
            }
            new IspwColumnConfigDialog(mainFrame.getPrimaryStage(), text, mainFrame.getJiraConfig()).showAndWait();
        });

        setupContextMenu();
    }
    
    private void setupIspwTable() {
        onlyInIspwTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        TableColumn<IspwRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cellData -> cellData.getValue().type);
        colType.setPrefWidth(80);

        TableColumn<IspwRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cellData -> cellData.getValue().name);
        colName.setPrefWidth(120);

        TableColumn<IspwRow, String> colEnv = new TableColumn<>("ENVlvl");
        colEnv.setCellValueFactory(cellData -> cellData.getValue().envLvl);
        colEnv.setPrefWidth(90);

        TableColumn<IspwRow, String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(cellData -> cellData.getValue().action);
        colAction.setPrefWidth(100);

        TableColumn<IspwRow, String> colSr = new TableColumn<>("SR Number");
        colSr.setCellValueFactory(cellData -> cellData.getValue().srNumber);
        colSr.setPrefWidth(100);

        TableColumn<IspwRow, String> colUser = new TableColumn<>("User ID");
        colUser.setCellValueFactory(cellData -> cellData.getValue().userId);
        colUser.setPrefWidth(100);

        onlyInIspwTable.getColumns().addAll(colType, colName, colEnv, colAction, colSr, colUser);
    }

    private void setupJiraTable() {
        TableColumn<JiraRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cellData -> cellData.getValue().type);
        colType.setPrefWidth(80);

        TableColumn<JiraRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cellData -> cellData.getValue().name);
        colName.setPrefWidth(120);

        TableColumn<JiraRow, String> colParent = new TableColumn<>("Parent Issue");
        colParent.setCellValueFactory(cellData -> cellData.getValue().parent);
        colParent.setPrefWidth(120);

        TableColumn<JiraRow, String> colAssignee = new TableColumn<>("Assignee");
        colAssignee.setCellValueFactory(cellData -> cellData.getValue().assignee);
        colAssignee.setPrefWidth(120);

        TableColumn<JiraRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cellData -> cellData.getValue().status);
        colStatus.setPrefWidth(100);

        TableColumn<JiraRow, String> colLink = new TableColumn<>("Link");
        colLink.setCellValueFactory(cellData -> cellData.getValue().link);
        colLink.setPrefWidth(250);
        setupHyperlinkCell(colLink);

        onlyInJiraTable.getColumns().addAll(colType, colName, colParent, colAssignee, colStatus, colLink);
    }

    private void setupMatchesTable() {
        TableColumn<MatchRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cellData -> cellData.getValue().type);
        colType.setPrefWidth(80);

        TableColumn<MatchRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cellData -> cellData.getValue().name);
        colName.setPrefWidth(120);

        TableColumn<MatchRow, String> colJiraKey = new TableColumn<>("Jira Key");
        colJiraKey.setCellValueFactory(cellData -> cellData.getValue().jiraKey);
        colJiraKey.setPrefWidth(100);

        TableColumn<MatchRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cellData -> cellData.getValue().status);
        colStatus.setPrefWidth(100);

        TableColumn<MatchRow, String> colAssignee = new TableColumn<>("Assignee");
        colAssignee.setCellValueFactory(cellData -> cellData.getValue().assignee);
        colAssignee.setPrefWidth(120);

        TableColumn<MatchRow, String> colEnv = new TableColumn<>("ENVlvl");
        colEnv.setCellValueFactory(cellData -> cellData.getValue().envLvl);
        colEnv.setPrefWidth(90);

        TableColumn<MatchRow, String> colAction = new TableColumn<>("ISPW Action");
        colAction.setCellValueFactory(cellData -> cellData.getValue().ispwAction);
        colAction.setPrefWidth(100);

        TableColumn<MatchRow, String> colSr = new TableColumn<>("SR Number");
        colSr.setCellValueFactory(cellData -> cellData.getValue().srNumber);
        colSr.setPrefWidth(100);

        TableColumn<MatchRow, String> colUser = new TableColumn<>("ISPW User");
        colUser.setCellValueFactory(cellData -> cellData.getValue().ispwUser);
        colUser.setPrefWidth(100);

        TableColumn<MatchRow, String> colLink = new TableColumn<>("Link");
        colLink.setCellValueFactory(cellData -> cellData.getValue().link);
        colLink.setPrefWidth(250);
        setupHyperlinkCell(colLink);

        TableColumn<MatchRow, String> colNotes = new TableColumn<>("Notes");
        colNotes.setCellValueFactory(cellData -> cellData.getValue().notes);
        colNotes.setPrefWidth(120);

        matchesTable.getColumns().addAll(colType, colName, colJiraKey, colStatus, colAssignee, colEnv, colAction, colSr, colUser, colLink, colNotes);
    }

    private <T> void setupHyperlinkCell(TableColumn<T, String> colLink) {
        colLink.setCellFactory(col -> new TableCell<T, String>() {
            private final Hyperlink hyperlink = new Hyperlink();
            {
                hyperlink.setOnAction(e -> {
                    String url = hyperlink.getText();
                    if (url != null && url.startsWith("http")) {
                        try {
                            Desktop.getDesktop().browse(new java.net.URI(url));
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || !item.startsWith("http")) {
                    setGraphic(null);
                    setText(item);
                } else {
                    hyperlink.setText(item);
                    setGraphic(hyperlink);
                    setText(null);
                }
            }
        });
    }

    private void performComparison() {
        String rawKeys = jiraParentKeysArea.getText().trim();
        if (rawKeys.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one Jira Parent/Epic key.");
            return;
        }

        String ispwText = ispwReportArea.getText();
        if (ispwText.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please paste an ISPW report first.");
            return;
        }

        String[] topLevelKeys = rawKeys.toUpperCase().split("\\s+");
        if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one Jira Parent/Epic key.");
            return;
        }

        compareBtn.setDisable(true);
        exportExcelBtn.setDisable(true);
        statusLabel.setText("Starting reconciliation...");

        ExecutionService.submit(() -> {
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                List<JiraIssueInfo> collectedIssues = new ArrayList<>();

                Platform.runLater(() -> statusLabel.setText("Step 1/4: Fetching top-level issues..."));
                Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys, collectedIssues);

                Platform.runLater(() -> statusLabel.setText("Step 2/4: Fetching stories..."));
                Map<String, String> storySummaries = fetchStoriesInEpics(service, baseUrl, topLevelKeys, collectedIssues);

                Map<String, String> allParentSummaries = new HashMap<>(topLevelSummaries);
                allParentSummaries.putAll(storySummaries);
                Set<String> allPotentialParentKeys = new HashSet<>(allParentSummaries.keySet());

                Platform.runLater(() -> statusLabel.setText("Step 3/4: Fetching all sub-tasks..."));
                List<JiraReconInfo> fetchedTasks = fetchAllSubtaskInfo(service, baseUrl, allPotentialParentKeys, collectedIssues);

                Map<String, JiraReconInfo> tempJiraTaskMap = new HashMap<>();
                for (JiraReconInfo task : fetchedTasks) {
                    task.parentSummary = allParentSummaries.getOrDefault(task.parentKey, "N/A");
                    tempJiraTaskMap.put(task.subtaskSummary, task);
                }
                this.jiraTaskMap = tempJiraTaskMap;
                this.jiraAllIssuesList = collectedIssues;

                Platform.runLater(() -> statusLabel.setText("Step 4/4: Parsing ISPW report and comparing..."));

                // Parse ISPW Report
                Map<String, IspwReconInfo> tempIspwTaskMap = new HashMap<>();
                tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
                int minLenVal = config.getIspwMinLineLength(65);
                int[] typeBounds = config.getIspwColumnBounds("ci_type", new int[]{0, 4});
                int[] nameBounds = config.getIspwColumnBounds("ci_name", new int[]{5, 13});
                int[] envLvlBounds = config.getIspwEnvLvlBounds(new int[]{14, 22});
                int[] srBounds = config.getIspwColumnBounds("sr", new int[]{30, 40});
                int[] userBounds = config.getIspwColumnBounds("user", new int[]{41, 47});
                int[] actionBounds = config.getIspwActionBounds(new int[]{55, 56});

                for (String line : ispwText.split("\n")) {
                    try {
                        if (line.length() < minLenVal) continue;
                        String typePart = (line.length() >= typeBounds[1]) ? line.substring(typeBounds[0], typeBounds[1]).trim() :
                                          (line.length() > typeBounds[0] ? line.substring(typeBounds[0]).trim() : "");
                        String namePart = (line.length() >= nameBounds[1]) ? line.substring(nameBounds[0], nameBounds[1]).trim() :
                                          (line.length() > nameBounds[0] ? line.substring(nameBounds[0]).trim() : "");
                        
                        if (!typePart.isEmpty() && !namePart.isEmpty()) {
                            String rawTaskName = typePart + " " + namePart;
                            String normalizedName = rawTaskName.trim().replaceAll("\\s+", " ");
                            IspwReconInfo info = new IspwReconInfo();
                            info.fullTaskName = normalizedName;
                            info.envLvl = (line.length() >= envLvlBounds[1]) ? line.substring(envLvlBounds[0], envLvlBounds[1]).trim() :
                                          (line.length() > envLvlBounds[0] ? line.substring(envLvlBounds[0]).trim() : "");
                            info.srNumber = (line.length() >= srBounds[1]) ? line.substring(srBounds[0], srBounds[1]).trim() :
                                            (line.length() > srBounds[0] ? line.substring(srBounds[0]).trim() : "");
                            info.userId = (line.length() >= userBounds[1]) ? line.substring(userBounds[0], userBounds[1]).trim() :
                                          (line.length() > userBounds[0] ? line.substring(userBounds[0]).trim() : "");
                            info.action = (line.length() >= actionBounds[1]) ? line.substring(actionBounds[0], actionBounds[1]).trim() :
                                          (line.length() > actionBounds[0] ? line.substring(actionBounds[0]).trim() : "");
                            tempIspwTaskMap.put(normalizedName, info);
                        }
                    } catch (Exception e) { 
                        System.err.println("Could not parse line: " + line); 
                    }
                }
                this.ispwTaskMap = tempIspwTaskMap;

                if (this.ispwTaskMap.isEmpty()) {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.WARNING, "Warning", "No valid task names could be parsed from the ISPW report. Please check column configurations.");
                        statusLabel.setText("No valid task names found in ISPW report.");
                        compareBtn.setDisable(false);
                        exportExcelBtn.setDisable(false);
                    });
                    return;
                }

                Set<String> ispwKeys = ispwTaskMap.keySet();
                Set<String> matchedJiraCiKeys = new HashSet<>();
                List<MatchRow> matchRows = new ArrayList<>();
                List<IspwRow> onlyIspwRows = new ArrayList<>();

                for (String ispwKey : ispwKeys) {
                    IspwReconInfo ispw = ispwTaskMap.get(ispwKey);
                    String[] parts = ispw.fullTaskName.split(" ", 2);
                    String type = (parts.length > 0) ? parts[0] : ispw.fullTaskName;
                    String name = (parts.length > 1) ? parts[1] : "";
                    String formattedAction = formatIspwAction(ispw.action);

                    // Step 1: Direct summary match against Jira CI subtasks
                    if (jiraTaskMap.containsKey(ispwKey)) {
                        JiraReconInfo jira = jiraTaskMap.get(ispwKey);
                        matchedJiraCiKeys.add(ispwKey);
                        String link = baseUrl + "/browse/" + jira.subtaskKey;
                        matchRows.add(new MatchRow(
                            type, name, jira.subtaskKey, jira.status, jira.assignee, ispw.envLvl,
                            formattedAction, ispw.srNumber, ispw.userId, link, ""
                        ));
                    } else {
                        // Step 2: Check if action is Compile only or Delete, and search Jira descriptions
                        boolean isCompileOrDelete = isCompileOrDeleteAction(ispw.action, formattedAction);
                        boolean foundInDesc = false;

                        if (isCompileOrDelete) {
                            for (JiraIssueInfo issue : jiraAllIssuesList) {
                                // If it is in a Release Management subtask, it is NOT a valid location to find an item
                                if (issue.isReleaseManagement) {
                                    continue;
                                }
                                if (isCiInDescription(issue.description, type, name) || isNameInDescription(issue.description, name)) {
                                    foundInDesc = true;
                                    String link = baseUrl + "/browse/" + issue.key;
                                    matchRows.add(new MatchRow(
                                        type, name, issue.key, issue.status, issue.assignee, ispw.envLvl,
                                        formattedAction, ispw.srNumber, ispw.userId, link, "In description"
                                    ));
                                    break;
                                }
                            }
                        }

                        if (!foundInDesc) {
                            onlyIspwRows.add(new IspwRow(type, name, ispw.envLvl, formattedAction, ispw.srNumber, ispw.userId));
                        }
                    }
                }

                // Subtasks only in Jira: Jira CI subtasks that were not matched by summary to an ISPW item
                List<JiraRow> onlyJiraRows = new ArrayList<>();
                for (Map.Entry<String, JiraReconInfo> entry : jiraTaskMap.entrySet()) {
                    if (!matchedJiraCiKeys.contains(entry.getKey())) {
                        JiraReconInfo info = entry.getValue();
                        String[] parts = info.subtaskSummary.split(" ", 2);
                        String type = (parts.length > 0) ? parts[0] : info.subtaskSummary;
                        String name = (parts.length > 1) ? parts[1] : "";
                        String link = baseUrl + "/browse/" + info.subtaskKey;
                        onlyJiraRows.add(new JiraRow(type, name, info.parentSummary, info.assignee, info.status, link));
                    }
                }

                Platform.runLater(() -> {
                    onlyInIspwTable.getItems().setAll(onlyIspwRows);
                    onlyInJiraTable.getItems().setAll(onlyJiraRows);
                    matchesTable.getItems().setAll(matchRows);
                    
                    statusLabel.setText("Comparison Complete: " + matchRows.size() + " matches, " + 
                        onlyIspwRows.size() + " only in ISPW, " + onlyJiraRows.size() + " only in Jira.");
                    compareBtn.setDisable(false);
                    exportExcelBtn.setDisable(false);
                });
            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Execution Error", "Reconciliation Error:\n" + ex.getMessage());
                    statusLabel.setText("Error during reconciliation.");
                    compareBtn.setDisable(false);
                    exportExcelBtn.setDisable(false);
                });
            }
        });
    }

    private void handleFetchFromMainframe() {
        tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
        boolean hasPath = config.getCompanionTopazPath() != null && !config.getCompanionTopazPath().isEmpty();
        String mode = config.getCompanionTopazFetchMode();
        boolean hasTarget = false;
        if ("SUBMIT".equalsIgnoreCase(mode)) {
            hasTarget = config.getCompanionTopazJclSource() != null && !config.getCompanionTopazJclSource().isEmpty();
        } else if ("JOB_SPOOL".equalsIgnoreCase(mode)) {
            hasTarget = config.getCompanionTopazJobId() != null && !config.getCompanionTopazJobId().isEmpty();
        } else {
            hasTarget = config.getCompanionTopazDataset() != null && !config.getCompanionTopazDataset().isEmpty();
        }

        // If not fully configured, open settings dialog first
        if (!hasPath || !hasTarget) {
            openCompanionConfigDialog();
            return;
        }

        executeMainframeFetch();
    }

    private void openCompanionConfigDialog() {
        CompanionConfigDialog dialog = new CompanionConfigDialog(mainFrame.getPrimaryStage(), mainFrame.getJiraConfig());
        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && Boolean.TRUE.equals(result.get())) {
            executeMainframeFetch();
        }
    }

    private void executeMainframeFetch() {
        tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
        String selectedCert = mainFrame.getSelectedCertificate();

        fetchMainframeBtn.setDisable(true);
        compareBtn.setDisable(true);
        exportExcelBtn.setDisable(true);
        statusLabel.setText("Connecting to mainframe via Topaz companion...");

        CompanionRunner.executeTopazFetchAsync(
            config,
            selectedCert,
            msg -> Platform.runLater(() -> statusLabel.setText(msg)),
            res -> Platform.runLater(() -> {
                fetchMainframeBtn.setDisable(false);
                compareBtn.setDisable(false);
                exportExcelBtn.setDisable(false);

                if (res.success && res.outputContent != null && !res.outputContent.trim().isEmpty()) {
                    ispwReportArea.setText(res.outputContent);
                    int lines = res.outputContent.split("\n").length;
                    statusLabel.setText("Successfully retrieved ISPW report from mainframe (" + lines + " lines). Ready to compare.");
                } else {
                    statusLabel.setText("Mainframe companion extraction failed.");
                    showAlert(Alert.AlertType.ERROR, "Mainframe Fetch Error",
                            (res.errorMessage != null ? res.errorMessage : "Unknown error occurred while running Topaz companion."));
                }
            })
        );
    }

    private void exportToExcel() {
        if (onlyInIspwTable.getItems().isEmpty() && onlyInJiraTable.getItems().isEmpty() && matchesTable.getItems().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No reconciliation data to export. Please perform a comparison first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Reconciliation to Excel");
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fileChooser.setInitialFileName("Reconciliation_Export_" + timestamp + ".xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        File file = fileChooser.showSaveDialog(mainFrame != null ? mainFrame.getPrimaryStage() : null);
        if (file == null) {
            return;
        }

        statusLabel.setText("Exporting data to Excel...");
        ExecutionService.submit(() -> {
            try {
                List<ExcelExportUtil.SheetData> sheets = new ArrayList<>();

                // Sheet 1: Only in ISPW (with Notes column and yellow highlight if SR is blank)
                List<String> ispwHeaders = Arrays.asList("Type", "Name", "ENVlvl", "Action", "SR Number", "User ID", "Notes");
                ExcelExportUtil.SheetData ispwSheet = new ExcelExportUtil.SheetData("Only in ISPW", ispwHeaders);
                for (IspwRow row : onlyInIspwTable.getItems()) {
                    String type = row.type.get() != null ? row.type.get() : "";
                    String name = row.name.get() != null ? row.name.get() : "";
                    String envLvl = row.envLvl.get() != null ? row.envLvl.get() : "";
                    String action = row.action.get() != null ? row.action.get() : "";
                    String sr = row.srNumber.get() != null ? row.srNumber.get() : "";
                    String user = row.userId.get() != null ? row.userId.get() : "";
                    String notes = "";

                    List<String> rowValues = Arrays.asList(type, name, envLvl, action, sr, user, notes);
                    List<Integer> styles = new ArrayList<>();
                    for (int c = 0; c < rowValues.size(); c++) {
                        // SR Number is column index 4: if blank, highlight yellow!
                        if (c == 4 && sr.trim().isEmpty()) {
                            styles.add(ExcelExportUtil.STYLE_YELLOW);
                        } else {
                            styles.add(ExcelExportUtil.STYLE_NORMAL);
                        }
                    }
                    ispwSheet.addRow(rowValues, styles);
                }
                sheets.add(ispwSheet);

                // Sheet 2: Only in Jira (with Notes column)
                List<String> jiraHeaders = Arrays.asList("Type", "Name", "Parent Issue", "Assignee", "Status", "Link", "Notes");
                ExcelExportUtil.SheetData jiraSheet = new ExcelExportUtil.SheetData("Only in Jira", jiraHeaders);
                for (JiraRow row : onlyInJiraTable.getItems()) {
                    String type = row.type.get() != null ? row.type.get() : "";
                    String name = row.name.get() != null ? row.name.get() : "";
                    String parent = row.parent.get() != null ? row.parent.get() : "";
                    String assignee = row.assignee.get() != null ? row.assignee.get() : "";
                    String status = row.status.get() != null ? row.status.get() : "";
                    String link = row.link.get() != null ? row.link.get() : "";
                    String notes = "";

                    jiraSheet.addRow(Arrays.asList(type, name, parent, assignee, status, link, notes));
                }
                sheets.add(jiraSheet);

                // Sheet 3: Matches (with ENVlvl and Notes)
                List<String> matchHeaders = Arrays.asList("Type", "Name", "Jira Key", "Status", "Assignee", "ENVlvl", "ISPW Action", "SR Number", "ISPW User", "Link", "Notes");
                ExcelExportUtil.SheetData matchSheet = new ExcelExportUtil.SheetData("Matches", matchHeaders);
                for (MatchRow row : matchesTable.getItems()) {
                    String type = row.type.get() != null ? row.type.get() : "";
                    String name = row.name.get() != null ? row.name.get() : "";
                    String jiraKey = row.jiraKey.get() != null ? row.jiraKey.get() : "";
                    String status = row.status.get() != null ? row.status.get() : "";
                    String assignee = row.assignee.get() != null ? row.assignee.get() : "";
                    String envLvl = row.envLvl.get() != null ? row.envLvl.get() : "";
                    String action = row.ispwAction.get() != null ? row.ispwAction.get() : "";
                    String sr = row.srNumber.get() != null ? row.srNumber.get() : "";
                    String user = row.ispwUser.get() != null ? row.ispwUser.get() : "";
                    String link = row.link.get() != null ? row.link.get() : "";
                    String notes = row.notes.get() != null ? row.notes.get() : "";

                    matchSheet.addRow(Arrays.asList(type, name, jiraKey, status, assignee, envLvl, action, sr, user, link, notes));
                }
                sheets.add(matchSheet);

                ExcelExportUtil.exportToFile(file, sheets);

                Platform.runLater(() -> {
                    statusLabel.setText("Excel export complete: " + file.getName());
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Export Success");
                    alert.setHeaderText("Reconciliation report exported successfully!");
                    alert.setContentText("File saved to:\n" + file.getAbsolutePath() + "\n\nWould you like to open it now?");
                    ButtonType btnOpen = new ButtonType("Open File", ButtonBar.ButtonData.YES);
                    ButtonType btnClose = new ButtonType("Close", ButtonBar.ButtonData.NO);
                    alert.getButtonTypes().setAll(btnOpen, btnClose);
                    UiUtils.configureWindowOwner(alert, mainFrame != null ? mainFrame.getPrimaryStage() : null);
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (choice.isPresent() && choice.get() == btnOpen) {
                        try {
                            Desktop.getDesktop().open(file);
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.ERROR, "Open Error", "Could not open file: " + ex.getMessage());
                        }
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Export failed: " + ex.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Export Error", "Failed to export Excel file:\n" + ex.getMessage());
                });
            }
        });
    }

    private String formatIspwAction(String action) {
        if (action == null) return "";
        String upper = action.toUpperCase();
        if (upper.equals("C")) return "Compile only";
        if (upper.equals("D")) return "Delete";
        return action;
    }

    private static boolean isCompileOrDeleteAction(String rawAction, String formattedAction) {
        if (rawAction != null) {
            String u = rawAction.trim().toUpperCase();
            if (u.equals("C") || u.equals("D")) return true;
        }
        if (formattedAction != null) {
            String u = formattedAction.trim().toLowerCase();
            if (u.contains("compile") || u.contains("delete")) return true;
        }
        return false;
    }

    private static boolean isReleaseManagementSummary(String summary) {
        if (summary == null || summary.isEmpty()) return false;
        String norm = summary.toLowerCase().replaceAll("[\\s_-]+", " ");
        return norm.contains("release management") || norm.contains("rel mgt") || norm.contains("relmgmt");
    }

    private static boolean isCiInDescription(String description, String type, String name) {
        if (description == null || description.isEmpty() || type == null || name == null) return false;

        String descLower = description.toLowerCase();
        String typeNorm = type.trim().toLowerCase();
        String nameNorm = name.trim().toLowerCase();
        if (typeNorm.isEmpty() || nameNorm.isEmpty()) return false;
        String searchString = typeNorm + " " + nameNorm;

        if (descLower.contains(searchString)) return true;

        String normalizedDesc = descLower.replaceAll("\\s+", " ");
        if (normalizedDesc.contains(searchString)) return true;

        String[] lines = descLower.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains(typeNorm) && line.contains(nameNorm)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNameInDescription(String description, String name) {
        if (description == null || description.isEmpty() || name == null || name.trim().isEmpty()) return false;

        String descLower = description.toLowerCase();
        String nameNorm = name.trim().toLowerCase();

        int nameLen = nameNorm.length();
        int idx = 0;
        while ((idx = descLower.indexOf(nameNorm, idx)) != -1) {
            boolean beforeOk = (idx == 0) || !Character.isLetterOrDigit(descLower.charAt(idx - 1));
            boolean afterOk = (idx + nameLen == descLower.length()) || !Character.isLetterOrDigit(descLower.charAt(idx + nameLen));
            if (beforeOk && afterOk) {
                return true;
            }
            idx += 1;
        }

        return false;
    }

    private static JiraIssueInfo parseIssueInfo(JSONObject issue) {
        JiraIssueInfo info = new JiraIssueInfo();
        info.key = issue.getString("key");
        JSONObject fields = issue.optJSONObject("fields");
        if (fields != null) {
            info.summary = fields.optString("summary", "");
            info.description = (fields.has("description") && !fields.isNull("description")) ? fields.optString("description", "") : "";
            if (fields.has("status") && !fields.isNull("status")) {
                info.status = fields.getJSONObject("status").optString("name", "N/A");
            }
            if (fields.has("assignee") && !fields.isNull("assignee")) {
                info.assignee = fields.getJSONObject("assignee").optString("displayName", "Unassigned");
            }
        }
        info.isReleaseManagement = isReleaseManagementSummary(info.summary);
        return info;
    }
    
    private Map<String, String> fetchIssueSummaries(JiraApiService service, String baseUrl, String[] keys, List<JiraIssueInfo> collectedIssues) throws Exception {
        Map<String, String> summaries = new HashMap<>();
        if (keys.length == 0) return summaries;
        String jql = "key in (" + String.join(",", keys) + ")";
        JSONObject payload = new JSONObject()
            .put("jql", jql)
            .put("fields", new JSONArray().put("summary").put("description").put("status").put("assignee"));
        String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
        JSONArray issues = new JSONObject(response).getJSONArray("issues");
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            JiraIssueInfo issueInfo = parseIssueInfo(issue);
            collectedIssues.add(issueInfo);
            summaries.put(issueInfo.key, issueInfo.summary);
        }
        return summaries;
    }
    
    private Map<String, String> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys, List<JiraIssueInfo> collectedIssues) throws Exception {
        Map<String, String> storySummaries = new HashMap<>();
        if (epicKeys.length == 0) return storySummaries;
        String jql = String.format("\"Epic Link\" in (%s)", String.join(",", epicKeys));
        int startAt = 0;
        int total;
        do {
            JSONObject payload = new JSONObject()
                .put("jql", jql)
                .put("fields", new JSONArray().put("key").put("summary").put("description").put("status").put("assignee"))
                .put("startAt", startAt)
                .put("maxResults", 500); 
            String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
            JSONObject responseJson = new JSONObject(response);
            total = responseJson.getInt("total");
            JSONArray issues = responseJson.getJSONArray("issues");
            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                JiraIssueInfo issueInfo = parseIssueInfo(issue);
                collectedIssues.add(issueInfo);
                storySummaries.put(issueInfo.key, issueInfo.summary);
            }
            startAt += issues.length();
        } while (startAt < total);
        return storySummaries;
    }

    private List<JiraReconInfo> fetchAllSubtaskInfo(JiraApiService service, String baseUrl, Set<String> parentKeys, List<JiraIssueInfo> collectedIssues) throws Exception {
        List<JiraReconInfo> tasks = new ArrayList<>();
        if (parentKeys.isEmpty()) return tasks;
        List<String> parentKeyList = new ArrayList<>(parentKeys);
        int batchSize = 200; 
        for (int i = 0; i < parentKeyList.size(); i += batchSize) {
            List<String> batch = parentKeyList.subList(i, Math.min(i + batchSize, parentKeyList.size()));
            String jql = "parent in (" + String.join(",", batch) + ") AND status != Canceled";
            int startAt = 0;
            int total;
            do {
                JSONObject payload = new JSONObject()
                    .put("jql", jql)
                    .put("fields", new JSONArray().put("summary").put("description").put("parent").put("assignee").put("status"))
                    .put("startAt", startAt)
                    .put("maxResults", 500);
                String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
                JSONObject responseJson = new JSONObject(response);
                total = responseJson.getInt("total");
                JSONArray issues = responseJson.getJSONArray("issues");
                for (int j = 0; j < issues.length(); j++) {
                    JSONObject issue = issues.getJSONObject(j);
                    JiraIssueInfo issueInfo = parseIssueInfo(issue);
                    collectedIssues.add(issueInfo);

                    JSONObject fields = issue.getJSONObject("fields");
                    String rawSummary = fields.optString("summary", "");
                    String tempSummary = rawSummary.trim().replaceAll("\\s+", " ");
                    String[] parts = tempSummary.split(" ");
                    String normalizedSummary;
                    if (parts.length >= 2) {
                        normalizedSummary = parts[0] + " " + parts[1];
                    } else {
                        normalizedSummary = tempSummary;
                    }
                    if (mainFrame.getJiraConfig().getCiTypes().stream().anyMatch(prefix -> normalizedSummary.startsWith(prefix))) {
                        JiraReconInfo info = new JiraReconInfo();
                        info.subtaskKey = issue.getString("key");
                        info.subtaskSummary = normalizedSummary;
                        info.parentKey = fields.has("parent") ? fields.getJSONObject("parent").getString("key") : "";
                        info.assignee = issueInfo.assignee;
                        info.status = issueInfo.status;
                        tasks.add(info);
                    }
                }
                startAt += issues.length();
            } while (startAt < total);
        }
        return tasks;
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        onlyInIspwTable.setContextMenu(contextMenu);

        onlyInIspwTable.setOnContextMenuRequested(event -> {
            contextMenu.getItems().clear();
            ObservableList<IspwRow> selectedItems = onlyInIspwTable.getSelectionModel().getSelectedItems();
            if (selectedItems.isEmpty() || selectedItems.get(0) == null) return;

            MenuItem buildItem = new MenuItem("Send selected to Task Builder");
            buildItem.setOnAction(al -> buildTaskBuilderEntries(selectedItems));
            contextMenu.getItems().add(buildItem);
        });
    }

    private void buildTaskBuilderEntries(List<IspwRow> selectedItems) {
        String[] rawKeys = jiraParentKeysArea.getText().trim().toUpperCase().split("\\s+");
        List<String> keyList = new ArrayList<>();
        keyList.add("-- None / Use Defaults --");
        for (String k : rawKeys) {
            String clean = k.trim();
            if (!clean.isEmpty()) {
                keyList.add(clean);
            }
        }
        
        String selectedParent = null;
        if (keyList.size() > 1) {
            ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(keyList.get(1), keyList);
            choiceDialog.setTitle("Assign Parent Key");
            choiceDialog.setHeaderText("Select Parent Key for the selected ISPW tasks:");
            choiceDialog.setContentText("Parent:");
            
            Optional<String> result = choiceDialog.showAndWait();
            if (!result.isPresent()) {
                return; // Canceled
            }
            selectedParent = result.get();
            if (selectedParent.startsWith("--")) {
                selectedParent = null;
            }
        }
        
        StringBuilder sb = new StringBuilder();
        String sep = "******************************************************************";
        
        for (int i = 0; i < selectedItems.size(); i++) {
            IspwRow row = selectedItems.get(i);
            if (row == null) continue;
            
            sb.append(row.type.get()).append(" ").append(row.name.get()).append("\n");
            sb.append("Action: ").append(row.action.get()).append("\n");
            sb.append("SR Number: ").append(row.srNumber.get()).append("\n");
            sb.append("User ID: ").append(row.userId.get()).append("\n");
            
            if (selectedParent != null) {
                sb.append("parent: ").append(selectedParent).append("\n");
            }
            
            if (i < selectedItems.size() - 1) {
                sb.append(sep).append("\n\n");
            } else {
                sb.append(sep);
            }
        }
        
        final String tasksText = sb.toString();
        
        Platform.runLater(() -> {
            mainFrame.showPanel("Task Builder");
            TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
            if (tbp != null) {
                String currentText = tbp.getInputAreaText();
                if (currentText != null && !currentText.trim().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Append or Overwrite");
                    alert.setHeaderText("Task Builder already has content.");
                    alert.setContentText("Do you want to append the new tasks?\n(Selecting 'No' will overwrite the existing content)");
                    UiUtils.configureWindowOwner(alert, mainFrame != null ? mainFrame.getPrimaryStage() : null);
                    
                    ButtonType btnAppend = new ButtonType("Append", ButtonBar.ButtonData.YES);
                    ButtonType btnOverwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.NO);
                    ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    
                    alert.getButtonTypes().setAll(btnAppend, btnOverwrite, btnCancel);
                    
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (choice.isPresent() && choice.get() == btnAppend) {
                        tbp.appendInputAreaText(tasksText);
                    } else if (choice.isPresent() && choice.get() == btnOverwrite) {
                        tbp.setInputAreaText(tasksText);
                    }
                } else {
                    tbp.setInputAreaText(tasksText);
                }
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        UiUtils.showAlert(mainFrame != null ? mainFrame.getPrimaryStage() : null, type, title, content);
    }

    private <T> void setupTableKeys(TableView<T> table) {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        table.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == javafx.scene.input.KeyCode.C) {
                    copyTableSelectionToClipboard(table);
                    event.consume();
                } else if (event.getCode() == javafx.scene.input.KeyCode.A) {
                    table.getSelectionModel().selectAll();
                    event.consume();
                }
            }
        });
    }

    private <T> void copyTableSelectionToClipboard(TableView<T> table) {
        ObservableList<T> selectedItems = table.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        ObservableList<TableColumn<T, ?>> columns = table.getColumns();

        for (int i = 0; i < selectedItems.size(); i++) {
            T item = selectedItems.get(i);
            if (item == null) continue;

            if (i > 0) {
                sb.append("\n");
            }

            for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                TableColumn<T, ?> column = columns.get(colIndex);
                Object cellValue = null;
                if (column.getCellValueFactory() != null) {
                    cellValue = column.getCellData(item);
                }

                if (colIndex > 0) {
                    sb.append("\t");
                }

                sb.append(cellValue != null ? cellValue.toString() : "");
            }
        }

        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }
}
