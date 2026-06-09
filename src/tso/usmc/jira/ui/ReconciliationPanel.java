package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
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
        String srNumber;
        String userId;
        String action;
    }

    public static class IspwRow {
        public final SimpleStringProperty type;
        public final SimpleStringProperty name;
        public final SimpleStringProperty action;
        public final SimpleStringProperty srNumber;
        public final SimpleStringProperty userId;

        public IspwRow(String type, String name, String action, String sr, String user) {
            this.type = new SimpleStringProperty(type);
            this.name = new SimpleStringProperty(name);
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

    public static class MatchRow {
        public final SimpleStringProperty type;
        public final SimpleStringProperty name;
        public final SimpleStringProperty jiraKey;
        public final SimpleStringProperty status;
        public final SimpleStringProperty assignee;
        public final SimpleStringProperty ispwAction;
        public final SimpleStringProperty srNumber;
        public final SimpleStringProperty ispwUser;
        public final SimpleStringProperty link;

        public MatchRow(String type, String name, String key, String status, String assignee, String action, String sr, String user, String link) {
            this.type = new SimpleStringProperty(type);
            this.name = new SimpleStringProperty(name);
            this.jiraKey = new SimpleStringProperty(key);
            this.status = new SimpleStringProperty(status);
            this.assignee = new SimpleStringProperty(assignee);
            this.ispwAction = new SimpleStringProperty(action);
            this.srNumber = new SimpleStringProperty(sr);
            this.ispwUser = new SimpleStringProperty(user);
            this.link = new SimpleStringProperty(link);
        }
    }

    private final JiraApiClientGui mainFrame;

    // UI Components
    private final TextArea jiraParentKeysArea;
    private final Button fetchJiraBtn = new Button("Fetch Jira Sub-tasks");
    private final TextArea ispwReportArea = new TextArea();
    private final Button configureColumnsBtn = new Button("Configure Columns...");
    private final Button compareBtn = new Button("Compare Jira vs. ISPW");
    private final Label statusLabel = new Label("Ready. Fetch Jira tasks and paste ISPW report.");

    private final TableView<IspwRow> onlyInIspwTable = new TableView<>();
    private final TableView<JiraRow> onlyInJiraTable = new TableView<>();
    private final TableView<MatchRow> matchesTable = new TableView<>();

    // Data holders
    private Map<String, JiraReconInfo> jiraTaskMap = new HashMap<>();
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

        VBox jiraPanel = new VBox(5);
        jiraPanel.getStyleClass().add("card");
        jiraPanel.setPadding(new Insets(10));
        Label jiraTitle = new Label("1. Jira Input");
        jiraTitle.getStyleClass().add("card-title");
        jiraParentKeysArea.setPrefHeight(150);
        fetchJiraBtn.setMaxWidth(Double.MAX_VALUE);
        jiraPanel.getChildren().addAll(jiraTitle, jiraParentKeysArea, fetchJiraBtn);
        topPanel.add(jiraPanel, 0, 0);

        VBox ispwPanel = new VBox(5);
        ispwPanel.getStyleClass().add("card");
        ispwPanel.setPadding(new Insets(10));
        Label ispwTitle = new Label("2. Paste ISPW Report");
        ispwTitle.getStyleClass().add("card-title");
        ispwReportArea.setPrefHeight(150);
        configureColumnsBtn.setMaxWidth(Double.MAX_VALUE);
        ispwPanel.getChildren().addAll(ispwTitle, ispwReportArea, configureColumnsBtn);
        topPanel.add(ispwPanel, 1, 0);

        setTop(topPanel);

        // Center section: Compare action + TabPane results
        BorderPane centerContainer = new BorderPane();
        BorderPane.setMargin(centerContainer, new Insets(10, 0, 0, 0));

        HBox comparePanel = new HBox();
        comparePanel.setAlignment(Pos.CENTER);
        comparePanel.setPadding(new Insets(5, 0, 10, 0));
        comparePanel.getChildren().add(compareBtn);
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

        fetchJiraBtn.setOnAction(e -> fetchJiraTasks());
        compareBtn.setOnAction(e -> performComparison());
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

        TableColumn<IspwRow, String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(cellData -> cellData.getValue().action);
        colAction.setPrefWidth(100);

        TableColumn<IspwRow, String> colSr = new TableColumn<>("SR Number");
        colSr.setCellValueFactory(cellData -> cellData.getValue().srNumber);
        colSr.setPrefWidth(100);

        TableColumn<IspwRow, String> colUser = new TableColumn<>("User ID");
        colUser.setCellValueFactory(cellData -> cellData.getValue().userId);
        colUser.setPrefWidth(100);

        onlyInIspwTable.getColumns().addAll(colType, colName, colAction, colSr, colUser);
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

        matchesTable.getColumns().addAll(colType, colName, colJiraKey, colStatus, colAssignee, colAction, colSr, colUser, colLink);
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
        statusLabel.setText("Parsing ISPW report and performing comparison...");
        this.ispwTaskMap = new HashMap<>();
        String ispwText = ispwReportArea.getText();
        
        tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
        int minLenVal = config.getIspwMinLineLength(65);
        int[] typeBounds = config.getIspwColumnBounds("ci_type", new int[]{0, 4});
        int[] nameBounds = config.getIspwColumnBounds("ci_name", new int[]{5, 13});
        int[] srBounds = config.getIspwColumnBounds("sr", new int[]{30, 40});
        int[] userBounds = config.getIspwColumnBounds("user", new int[]{41, 47});
        int[] actionBounds = config.getIspwActionBounds(new int[]{55, 56});

        for (String line : ispwText.split("\n")) {
            try {
                if (line.length() < minLenVal) continue;
                String typePart = line.substring(typeBounds[0], typeBounds[1]).trim();
                String namePart = line.substring(nameBounds[0], nameBounds[1]).trim();
                
                if (!typePart.isEmpty() && !namePart.isEmpty()) {
                    String rawTaskName = typePart + " " + namePart;
                    String normalizedName = rawTaskName.trim().replaceAll("\\s+", " ");
                    IspwReconInfo info = new IspwReconInfo();
                    info.fullTaskName = normalizedName;
                    info.srNumber = line.substring(srBounds[0], srBounds[1]).trim();
                    info.userId = line.substring(userBounds[0], userBounds[1]).trim();
                    info.action = line.substring(actionBounds[0], actionBounds[1]).trim();
                    this.ispwTaskMap.put(normalizedName, info);
                }
            } catch (Exception e) { 
                System.err.println("Could not parse line: " + line); 
            }
        }

        if (this.jiraTaskMap.isEmpty()) {
            statusLabel.setText("Jira data has not been fetched. Please click 'Fetch Jira Sub-tasks' first.");
            return;
        }
        if (this.ispwTaskMap.isEmpty()) {
            statusLabel.setText("No valid task names could be parsed from the ISPW report.");
            return;
        }

        Set<String> ispwKeys = ispwTaskMap.keySet();
        Set<String> jiraKeys = jiraTaskMap.keySet();
        Set<String> onlyInIspw = new HashSet<>(ispwKeys);
        onlyInIspw.removeAll(jiraKeys);
        Set<String> onlyInJira = new HashSet<>(jiraKeys);
        onlyInJira.removeAll(ispwKeys);
        Set<String> matches = new HashSet<>(ispwKeys);
        matches.retainAll(jiraKeys);
        
        Platform.runLater(() -> {
            onlyInIspwTable.getItems().clear();
            for (String key : onlyInIspw) {
                IspwReconInfo info = ispwTaskMap.get(key);
                String[] parts = info.fullTaskName.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : info.fullTaskName;
                String name = (parts.length > 1) ? parts[1] : "";
                onlyInIspwTable.getItems().add(new IspwRow(type, name, formatIspwAction(info.action), info.srNumber, info.userId));
            }

            onlyInJiraTable.getItems().clear();
            for (String key : onlyInJira) {
                JiraReconInfo info = jiraTaskMap.get(key);
                String[] parts = info.subtaskSummary.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : info.subtaskSummary;
                String name = (parts.length > 1) ? parts[1] : "";
                String link = mainFrame.getBaseUrl() + "/browse/" + info.subtaskKey;
                onlyInJiraTable.getItems().add(new JiraRow(type, name, info.parentSummary, info.assignee, info.status, link));
            }

            matchesTable.getItems().clear();
            for (String key : matches) {
                IspwReconInfo ispw = ispwTaskMap.get(key);
                JiraReconInfo jira = jiraTaskMap.get(key);
                String[] parts = ispw.fullTaskName.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : ispw.fullTaskName;
                String name = (parts.length > 1) ? parts[1] : "";
                String link = mainFrame.getBaseUrl() + "/browse/" + jira.subtaskKey;
                matchesTable.getItems().add(new MatchRow(
                    type, name, jira.subtaskKey, jira.status, jira.assignee, 
                    formatIspwAction(ispw.action), ispw.srNumber, ispw.userId, link
                ));
            }
            
            statusLabel.setText("Comparison Complete: " + matches.size() + " matches, " + 
                onlyInIspw.size() + " only in ISPW, " + onlyInJira.size() + " only in Jira.");
        });
    }

    private String formatIspwAction(String action) {
        if (action == null) return "";
        String upper = action.toUpperCase();
        if (upper.equals("C")) return "Compile only";
        if (upper.equals("D")) return "Delete";
        return action;
    }
    
    private void fetchJiraTasks() {
        String[] topLevelKeys = jiraParentKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one Jira Parent/Epic key.");
            return;
        }
        fetchJiraBtn.setDisable(true);
        statusLabel.setText("Fetching Jira data...");
        ExecutionService.submit(() -> {
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                Platform.runLater(() -> statusLabel.setText("Step 1/3: Fetching top-level summaries..."));
                Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys);
                Platform.runLater(() -> statusLabel.setText("Step 2/3: Fetching stories..."));
                Map<String, String> storySummaries = fetchStoriesInEpics(service, baseUrl, topLevelKeys);
                Map<String, String> allParentSummaries = new HashMap<>(topLevelSummaries);
                allParentSummaries.putAll(storySummaries);
                Set<String> allPotentialParentKeys = new HashSet<>(allParentSummaries.keySet());
                Platform.runLater(() -> statusLabel.setText("Step 3/3: Fetching all sub-tasks..."));
                List<JiraReconInfo> fetchedTasks = fetchAllSubtaskInfo(service, baseUrl, allPotentialParentKeys);
                this.jiraTaskMap = new HashMap<>();
                for (JiraReconInfo task : fetchedTasks) {
                    task.parentSummary = allParentSummaries.getOrDefault(task.parentKey, "N/A");
                    this.jiraTaskMap.put(task.subtaskSummary, task);
                }
                Platform.runLater(() -> {
                    statusLabel.setText("Success! Fetched " + this.jiraTaskMap.size() + " unique, ISPW-related Jira sub-tasks.");
                    fetchJiraBtn.setDisable(false);
                });
            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Execution Error", "Jira API Error:\n" + ex.getMessage());
                    statusLabel.setText("Error fetching Jira data.");
                    fetchJiraBtn.setDisable(false);
                });
            }
        });
    }
    
    private Map<String, String> fetchIssueSummaries(JiraApiService service, String baseUrl, String[] keys) throws Exception {
         Map<String, String> summaries = new HashMap<>();
         if (keys.length == 0) return summaries;
        String jql = "key in (" + String.join(",", keys) + ")";
        JSONObject payload = new JSONObject().put("jql", jql).put("fields", new JSONArray().put("summary"));
        String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
        JSONArray issues = new JSONObject(response).getJSONArray("issues");
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            summaries.put(issue.getString("key"), issue.getJSONObject("fields").getString("summary"));
        }
        return summaries;
    }
    
    private Map<String, String> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys) throws Exception {
        Map<String, String> storySummaries = new HashMap<>();
        if (epicKeys.length == 0) return storySummaries;
        String jql = String.format("\"Epic Link\" in (%s)", String.join(",", epicKeys));
        int startAt = 0;
        int total;
        do {
            JSONObject payload = new JSONObject()
                .put("jql", jql)
                .put("fields", new JSONArray().put("key").put("summary"))
                .put("startAt", startAt)
                .put("maxResults", 500); 
            String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
            JSONObject responseJson = new JSONObject(response);
            total = responseJson.getInt("total");
            JSONArray issues = responseJson.getJSONArray("issues");
            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                storySummaries.put(issue.getString("key"), issue.getJSONObject("fields").getString("summary"));
            }
            startAt += issues.length();
        } while (startAt < total);
        return storySummaries;
    }

    private List<JiraReconInfo> fetchAllSubtaskInfo(JiraApiService service, String baseUrl, Set<String> parentKeys) throws Exception {
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
                    .put("fields", new JSONArray().put("summary").put("parent").put("assignee").put("status"))
                    .put("startAt", startAt)
                    .put("maxResults", 500);
                String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
                JSONObject responseJson = new JSONObject(response);
                total = responseJson.getInt("total");
                JSONArray issues = responseJson.getJSONArray("issues");
                for (int j = 0; j < issues.length(); j++) {
                    JSONObject issue = issues.getJSONObject(j);
                    JSONObject fields = issue.getJSONObject("fields");
                    String rawSummary = fields.getString("summary");
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
                        info.parentKey = fields.getJSONObject("parent").getString("key");
                        if (fields.has("assignee") && !fields.isNull("assignee")) {
                            info.assignee = fields.getJSONObject("assignee").getString("displayName");
                        }
                        if (fields.has("status") && !fields.isNull("status")) {
                            info.status = fields.getJSONObject("status").getString("name");
                        }
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
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
