package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.JsonUtils;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReportPanel extends BorderPane {


    
    // Helper classes
    private static class SubtaskInfo {
        String key;
        String summary;
        String assignee = "Unassigned";
        String parentKey;
    }
    private static class StoryInfo {
        String key;
        String summary;
        String epicKey;
    }

    private final JiraApiClientGui mainFrame;
    private final TextArea inputKeysArea = new TextArea();
    private final TextArea errorArea = new TextArea();
    private final Button allSubtasksBtn = new Button("Report: All Sub-tasks");
    private final Button filteredSubtasksBtn = new Button("Report: ISPW types");
    private final Button fullJsonBtn = new Button("Report: Full Pretty JSON");

    public ReportPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));

        HBox btnPanel = new HBox(10);
        btnPanel.setPadding(new Insets(5, 0, 10, 0));
        allSubtasksBtn.setOnAction(e -> generateSubtaskDetailReport(false));
        filteredSubtasksBtn.setOnAction(e -> generateSubtaskDetailReport(true));
        fullJsonBtn.setOnAction(e -> generateFullJsonReport());
        btnPanel.getChildren().addAll(allSubtasksBtn, filteredSubtasksBtn, fullJsonBtn);
        setTop(btnPanel);

        VBox centerPanel = new VBox(5);
        centerPanel.getStyleClass().add("card");
        centerPanel.setPadding(new Insets(10));
        Label inputTitle = new Label("Enter Epic or Parent Issue Keys (one per line):");
        inputTitle.getStyleClass().add("card-title");
        inputKeysArea.setPrefRowCount(15);
        inputKeysArea.setMinHeight(250);
        VBox.setVgrow(inputKeysArea, Priority.ALWAYS);
        centerPanel.getChildren().addAll(inputTitle, inputKeysArea);
        setCenter(centerPanel);

        VBox bottomPanel = new VBox(5);
        bottomPanel.getStyleClass().add("card");
        bottomPanel.setPadding(new Insets(10));
        bottomPanel.setPrefHeight(250);
        Label statusTitle = new Label("Status / Error Log:");
        statusTitle.getStyleClass().add("card-title");
        errorArea.setEditable(false);
        errorArea.setStyle("-fx-text-fill: red; -fx-font-family: monospace;");
        errorArea.setPrefHeight(Double.MAX_VALUE);
        VBox.setVgrow(errorArea, Priority.ALWAYS);
        bottomPanel.getChildren().addAll(statusTitle, errorArea);
        BorderPane.setMargin(bottomPanel, new Insets(10, 0, 0, 0));
        setBottom(bottomPanel);
    }

    private void setButtonsEnabled(boolean enabled) {
        allSubtasksBtn.setDisable(!enabled);
        filteredSubtasksBtn.setDisable(!enabled);
        fullJsonBtn.setDisable(!enabled);
    }

    private void generateSubtaskDetailReport(boolean filterIspwTypes) {
        String[] topLevelKeys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one issue key.");
            return;
        }

        errorArea.setText("Starting report generation...");
        errorArea.setStyle("-fx-text-fill: -fx-accent; -fx-font-family: monospace;");
        setButtonsEnabled(false);
        final StringBuilder reportContent = new StringBuilder();

        ExecutionService.submit(() -> {
            boolean reportGeneratedSuccessfully = false;
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                
                reportContent.append("JIRA SUB-TASK DETAIL REPORT GENERATED: ").append(new java.util.Date()).append("\n");
                reportContent.append("====================================================\n\n");

                Platform.runLater(() -> errorArea.setText("Step 1: Fetching summaries for top-level keys..."));
                Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys);

                Platform.runLater(() -> errorArea.appendText("\nStep 2: Fetching all stories within epics (with pagination)..."));
                List<StoryInfo> storiesInEpics = fetchStoriesInEpics(service, baseUrl, topLevelKeys);
                Map<String, StoryInfo> storyMap = storiesInEpics.stream().collect(Collectors.toMap(s -> s.key, s -> s));

                Set<String> allPotentialParentKeys = new HashSet<>(Arrays.asList(topLevelKeys));
                allPotentialParentKeys.addAll(storyMap.keySet());
                
                Platform.runLater(() -> errorArea.appendText("\nStep 3: Fetching all sub-tasks (with pagination)..."));
                Map<String, List<SubtaskInfo>> subtasksByParent = fetchSubtasksOf(service, baseUrl, allPotentialParentKeys, filterIspwTypes);

                Platform.runLater(() -> errorArea.appendText("\nStep 4: Assembling final report..."));
                for (String topKey : topLevelKeys) {
                    reportContent.append("PARENT/EPIC: ").append(topKey)
                                 .append(" (").append(topLevelSummaries.getOrDefault(topKey, "Unknown Summary")).append(")\n");
                    
                    List<String> formattedLines = new ArrayList<>();
                    if (subtasksByParent.containsKey(topKey)) {
                        String parentSummary = topLevelSummaries.get(topKey);
                        for (SubtaskInfo subtask : subtasksByParent.get(topKey)) {
                            String line = String.format("  - %s [%s] [%s] [%s] [%s]",
                                subtask.summary, subtask.key, subtask.assignee, parentSummary, topKey);
                            formattedLines.add(line);
                        }
                    }
                    for (StoryInfo story : storiesInEpics) {
                        if (topKey.equals(story.epicKey) && subtasksByParent.containsKey(story.key)) {
                            for (SubtaskInfo subtask : subtasksByParent.get(story.key)) {
                                String line = String.format("  - %s [%s] [%s] [%s] [%s]",
                                    subtask.summary, subtask.key, subtask.assignee, story.summary, story.key);
                                formattedLines.add(line);
                            }
                        }
                    }
                    if (formattedLines.isEmpty()) {
                        reportContent.append(filterIspwTypes ? "  (No matching sub-tasks found)\n" : "  (No sub-tasks found)\n");
                    } else {
                        Collections.sort(formattedLines);
                        for (String line : formattedLines) {
                            reportContent.append(line).append("\n");
                        }
                    }
                    reportContent.append("\n");
                }
                
                reportGeneratedSuccessfully = true;

            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Platform.runLater(() -> {
                    errorArea.setStyle("-fx-text-fill: red; -fx-font-family: monospace;");
                    errorArea.setText("FATAL ERROR: Report generation failed.\n\n" + sw.toString());
                });
            } finally {
                Platform.runLater(() -> setButtonsEnabled(true));
                if (reportGeneratedSuccessfully) {
                    saveAndOpenFile(reportContent.toString());
                }
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
    
    private List<StoryInfo> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys) throws Exception {
        final String EPIC_LINK_FIELD_ID = mainFrame.getJiraConfig().getCustomFieldId("epic_link", "customfield_13056");
        List<StoryInfo> stories = new ArrayList<>();
        if (epicKeys.length == 0) return stories;
        
        String jql = String.format("\"Epic Link\" in (%s)", String.join(",", epicKeys));
        int startAt = 0;
        int total;
        
        do {
            JSONObject payload = new JSONObject()
                .put("jql", jql)
                .put("fields", new JSONArray().put("summary").put(EPIC_LINK_FIELD_ID))
                .put("startAt", startAt)
                .put("maxResults", 100); 

            String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
            JSONObject responseJson = new JSONObject(response);

            total = responseJson.getInt("total");
            JSONArray issues = responseJson.getJSONArray("issues");

            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                JSONObject fields = issue.getJSONObject("fields");
                if (fields.has(EPIC_LINK_FIELD_ID) && !fields.isNull(EPIC_LINK_FIELD_ID)) {
                    StoryInfo story = new StoryInfo();
                    story.key = issue.getString("key");
                    story.summary = fields.getString("summary");
                    story.epicKey = fields.getString(EPIC_LINK_FIELD_ID);
                    stories.add(story);
                }
            }
            startAt += issues.length();
        } while (startAt < total);
        
        return stories;
    }

    private Map<String, List<SubtaskInfo>> fetchSubtasksOf(JiraApiService service, String baseUrl, Set<String> parentKeys, boolean filter) throws Exception {
        Map<String, List<SubtaskInfo>> subtasksByParent = new HashMap<>();
        if (parentKeys.isEmpty()) return subtasksByParent;
        
        List<String> parentKeyList = new ArrayList<>(parentKeys);
        int batchSize = 200; 

        for (int i = 0; i < parentKeyList.size(); i += batchSize) {
            List<String> batch = parentKeyList.subList(i, Math.min(i + batchSize, parentKeyList.size()));
            String jql = "parent in (" + String.join(",", batch) + ")";
            int startAt = 0;
            int total;
            
            do {
                JSONObject payload = new JSONObject()
                    .put("jql", jql)
                    .put("fields", new JSONArray().put("summary").put("parent").put("assignee"))
                    .put("startAt", startAt)
                    .put("maxResults", 100);

                String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
                JSONObject responseJson = new JSONObject(response);

                total = responseJson.getInt("total");
                JSONArray issues = responseJson.getJSONArray("issues");

                for (int j = 0; j < issues.length(); j++) {
                    JSONObject issue = issues.getJSONObject(j);
                    JSONObject fields = issue.getJSONObject("fields");
                    String summary = fields.getString("summary").trim().replace('\t', ' ');
                    boolean passesFilter = !filter || mainFrame.getJiraConfig().getCiTypes().stream().anyMatch(summary::startsWith);
                    
                    if (passesFilter) {
                        SubtaskInfo subtask = new SubtaskInfo();
                        subtask.key = issue.getString("key");
                        subtask.summary = summary;
                        subtask.parentKey = fields.getJSONObject("parent").getString("key");
                        if (fields.has("assignee") && !fields.isNull("assignee")) {
                            subtask.assignee = fields.getJSONObject("assignee").getString("displayName");
                        }
                        subtasksByParent.computeIfAbsent(subtask.parentKey, k -> new ArrayList<>()).add(subtask);
                    }
                }
                startAt += issues.length();
            } while (startAt < total);
        }
        return subtasksByParent;
    }
    
    private void generateFullJsonReport() {
        String[] keys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one issue key.");
            return;
        }

        errorArea.setText("Starting JSON report generation...");
        errorArea.setStyle("-fx-text-fill: -fx-accent; -fx-font-family: monospace;");
        setButtonsEnabled(false);
        final StringBuilder reportContent = new StringBuilder();

        ExecutionService.submit(() -> {
            boolean reportGeneratedSuccessfully = false;
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                
                reportContent.append("JIRA FULL JSON REPORT GENERATED: ").append(new java.util.Date()).append("\n");
                reportContent.append("====================================================\n\n");

                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i];
                    final int current = i + 1;
                    final int total = keys.length;
                    Platform.runLater(() -> errorArea.setText("Fetching JSON for " + key + " (" + current + " of " + total + ")..."));
                    
                    String endpoint = "/rest/api/2/issue/" + key + "?fields=*all&expand=renderedFields";
                    String response = service.executeRequest(baseUrl + endpoint, "GET", null);
                    
                    reportContent.append("--- FULL JSON FOR ").append(key).append(" ---\n");
                    reportContent.append(JsonUtils.prettyPrintJson(response)).append("\n\n");
                }
                
                reportGeneratedSuccessfully = true;

            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Platform.runLater(() -> {
                    errorArea.setStyle("-fx-text-fill: red; -fx-font-family: monospace;");
                    errorArea.setText("FATAL ERROR: JSON report failed.\n\n" + sw.toString());
                });
            } finally {
                Platform.runLater(() -> setButtonsEnabled(true));
                if (reportGeneratedSuccessfully) {
                    saveAndOpenFile(reportContent.toString());
                }
            }
        });
    }

    private void saveAndOpenFile(String content) {
        try {
            File tempFile = File.createTempFile("Jira_Report_", ".txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                writer.write(content);
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tempFile);
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                errorArea.setStyle("-fx-text-fill: red; -fx-font-family: monospace;");
                errorArea.setText("ERROR: Could not save or open the report file.\n" + e.getMessage());
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
