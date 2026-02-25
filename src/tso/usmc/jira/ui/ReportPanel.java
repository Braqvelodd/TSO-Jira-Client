package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.JsonUtils;
import javax.swing.*;
import java.awt.*;
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

public class ReportPanel extends JPanel {

    private static final List<String> ISPW_PREFIXES = Arrays.asList(
            "COB", "PROC", "JCL", "SYS", "ASM", "COPY", "DMGR", "DCLG", "CMAP"
    );
    
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
    private final JTextArea inputKeysArea = new JTextArea();
    private final JTextArea errorArea = new JTextArea();
    private final JButton allSubtasksBtn = new JButton("Report: All Sub-tasks");
    private final JButton filteredSubtasksBtn = new JButton("Report: ISPW types");
    private final JButton fullJsonBtn = new JButton("Report: Full Pretty JSON");

    public ReportPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        allSubtasksBtn.addActionListener(e -> generateSubtaskDetailReport(false));
        filteredSubtasksBtn.addActionListener(e -> generateSubtaskDetailReport(true));
        fullJsonBtn.addActionListener(e -> generateFullJsonReport());
        btnPanel.add(allSubtasksBtn);
        btnPanel.add(filteredSubtasksBtn);
        btnPanel.add(fullJsonBtn);
        add(btnPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Enter Epic or Parent Issue Keys (one per line):"));
        centerPanel.add(new JScrollPane(inputKeysArea), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(0, 250));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Status / Error Log:"));
        errorArea.setEditable(false);
        errorArea.setForeground(Color.RED);
        bottomPanel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setButtonsEnabled(boolean enabled) {
        allSubtasksBtn.setEnabled(enabled);
        filteredSubtasksBtn.setEnabled(enabled);
        fullJsonBtn.setEnabled(enabled);
    }

    private void generateSubtaskDetailReport(boolean filterIspwTypes) {
        String[] topLevelKeys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
            JOptionPane.showMessageDialog(this, "Please enter at least one issue key.");
            return;
        }

        errorArea.setText("Starting report generation...");
        errorArea.setForeground(Color.BLUE);
        setButtonsEnabled(false);
        final StringBuilder reportContent = new StringBuilder();

        new Thread(() -> {
            boolean reportGeneratedSuccessfully = false;
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                
                reportContent.append("JIRA SUB-TASK DETAIL REPORT GENERATED: ").append(new java.util.Date()).append("\n");
                reportContent.append("====================================================\n\n");

                SwingUtilities.invokeLater(() -> errorArea.setText("Step 1: Fetching summaries for top-level keys..."));
                Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys);

                SwingUtilities.invokeLater(() -> errorArea.append("\nStep 2: Fetching all stories within epics (with pagination)..."));
                List<StoryInfo> storiesInEpics = fetchStoriesInEpics(service, baseUrl, topLevelKeys);
                Map<String, StoryInfo> storyMap = storiesInEpics.stream().collect(Collectors.toMap(s -> s.key, s -> s));

                Set<String> allPotentialParentKeys = new HashSet<>(Arrays.asList(topLevelKeys));
                allPotentialParentKeys.addAll(storyMap.keySet());
                
                SwingUtilities.invokeLater(() -> errorArea.append("\nStep 3: Fetching all sub-tasks (with pagination)..."));
                Map<String, List<SubtaskInfo>> subtasksByParent = fetchSubtasksOf(service, baseUrl, allPotentialParentKeys, filterIspwTypes);

                SwingUtilities.invokeLater(() -> errorArea.append("\nStep 4: Assembling final report..."));
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
                SwingUtilities.invokeLater(() -> {
                    errorArea.setForeground(Color.RED);
                    errorArea.setText("FATAL ERROR: Report generation failed.\n\n" + sw.toString());
                });
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
                if (reportGeneratedSuccessfully) {
                    saveAndOpenFile(reportContent.toString());
                }
            }
        }).start();
    }
    
    // --- THIS METHOD IS NOW COMPLETE ---
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
    
    // This method has the pagination fix
    private List<StoryInfo> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys) throws Exception {
        final String EPIC_LINK_FIELD_ID = "customfield_13056";
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

    // This method also has the pagination and batching fix
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
                    boolean passesFilter = !filter || ISPW_PREFIXES.stream().anyMatch(summary::startsWith);
                    
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
    
    // --- THIS METHOD IS NOW COMPLETE ---
    private void generateFullJsonReport() {
        String[] keys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
            JOptionPane.showMessageDialog(this, "Please enter at least one issue key.");
            return;
        }

        errorArea.setText("Starting JSON report generation...");
        errorArea.setForeground(Color.BLUE);
        setButtonsEnabled(false);
        final StringBuilder reportContent = new StringBuilder();

        new Thread(() -> {
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
                    SwingUtilities.invokeLater(() -> errorArea.setText("Fetching JSON for " + key + " (" + current + " of " + total + ")..."));
                    
                    String endpoint = "/rest/api/2/issue/" + key + "?fields=*all&expand=renderedFields";
                    String response = service.executeRequest(baseUrl + endpoint, "GET", null);
                    
                    reportContent.append("--- FULL JSON FOR ").append(key).append(" ---\n");
                    reportContent.append(JsonUtils.prettyPrintJson(response)).append("\n\n");
                }
                
                reportGeneratedSuccessfully = true;

            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                SwingUtilities.invokeLater(() -> {
                    errorArea.setForeground(Color.RED);
                    errorArea.setText("FATAL ERROR: JSON report failed.\n\n" + sw.toString());
                });
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
                if (reportGeneratedSuccessfully) {
                    saveAndOpenFile(reportContent.toString());
                }
            }
        }).start();
    }

    // --- THIS METHOD IS NOW COMPLETE ---
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
            SwingUtilities.invokeLater(() -> {
                errorArea.setForeground(Color.RED);
                errorArea.setText("ERROR: Could not save or open the report file.\n" + e.getMessage());
            });
        }
    }
}


// package tso.usmc.jira.ui;

// import tso.usmc.jira.app.JiraApiClientGui;
// import tso.usmc.jira.service.JiraApiService;
// import tso.usmc.jira.util.JsonUtils;
// import javax.swing.*;
// import java.awt.*;
// import java.io.BufferedWriter;
// import java.io.File;
// import java.io.FileWriter;
// import java.io.IOException;
// import java.io.PrintWriter;
// import java.io.StringWriter;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.stream.Collectors;
// import org.json.JSONArray;
// import org.json.JSONObject;

// public class ReportPanel extends JPanel {

//     private static final List<String> ISPW_PREFIXES = Arrays.asList(
//             "COB", "PROC", "JCL", "SYS", "ASM", "COPY", "DMGR", "DCLG", "CMAP"
//     );
    
//     // Helper classes
//     private static class SubtaskInfo {
//         String key;
//         String summary;
//         String assignee = "Unassigned";
//         String parentKey;
//     }
//     private static class StoryInfo {
//         String key;
//         String summary;
//         String epicKey;
//     }

//     private final JiraApiClientGui mainFrame;
//     private final JTextArea inputKeysArea = new JTextArea();
//     private final JTextArea errorArea = new JTextArea();
//     private final JButton allSubtasksBtn = new JButton("Report: All Sub-tasks");
//     private final JButton filteredSubtasksBtn = new JButton("Report: ISPW types");
//     private final JButton fullJsonBtn = new JButton("Report: Full Pretty JSON");

//     // Constructor and UI Setup (unchanged)
//     public ReportPanel(JiraApiClientGui mainFrame) {
//         this.mainFrame = mainFrame;
//         setLayout(new BorderLayout());

//         JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
//         allSubtasksBtn.addActionListener(e -> generateSubtaskDetailReport(false));
//         filteredSubtasksBtn.addActionListener(e -> generateSubtaskDetailReport(true));
//         fullJsonBtn.addActionListener(e -> generateFullJsonReport());

//         btnPanel.add(allSubtasksBtn);
//         btnPanel.add(filteredSubtasksBtn);
//         btnPanel.add(fullJsonBtn);
//         add(btnPanel, BorderLayout.NORTH);

//         JPanel centerPanel = new JPanel(new BorderLayout());
//         centerPanel.setBorder(BorderFactory.createTitledBorder("Enter Epic or Parent Issue Keys (one per line):"));
//         centerPanel.add(new JScrollPane(inputKeysArea), BorderLayout.CENTER);
//         add(centerPanel, BorderLayout.CENTER);

//         JPanel bottomPanel = new JPanel(new BorderLayout());
//         bottomPanel.setPreferredSize(new Dimension(0, 250));
//         bottomPanel.setBorder(BorderFactory.createTitledBorder("Status / Error Log:"));
//         errorArea.setEditable(false);
//         errorArea.setForeground(Color.RED);
//         bottomPanel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
//         add(bottomPanel, BorderLayout.SOUTH);
//     }

//     private void setButtonsEnabled(boolean enabled) {
//         allSubtasksBtn.setEnabled(enabled);
//         filteredSubtasksBtn.setEnabled(enabled);
//         fullJsonBtn.setEnabled(enabled);
//     }

//     private void generateSubtaskDetailReport(boolean filterIspwTypes) {
//         // --- CHANGE: Convert input text to uppercase before splitting ---
//         String[] topLevelKeys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        
//         if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
//             JOptionPane.showMessageDialog(this, "Please enter at least one issue key.");
//             return;
//         }

//         errorArea.setText("Starting hierarchical report generation...");
//         errorArea.setForeground(Color.BLUE);
//         setButtonsEnabled(false);
//         final StringBuilder reportContent = new StringBuilder();

//         new Thread(() -> {
//             boolean reportGeneratedSuccessfully = false;
//             try {
//                 JiraApiService service = mainFrame.getService();
//                 String baseUrl = mainFrame.getBaseUrl();
                
//                 reportContent.append("JIRA SUB-TASK DETAIL REPORT GENERATED: ").append(new java.util.Date()).append("\n");
//                 reportContent.append("====================================================\n\n");

//                 Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys);
//                 List<StoryInfo> storiesInEpics = fetchStoriesInEpics(service, baseUrl, topLevelKeys);
//                 Map<String, StoryInfo> storyMap = storiesInEpics.stream().collect(Collectors.toMap(s -> s.key, s -> s));

//                 Set<String> allPotentialParentKeys = new HashSet<>(Arrays.asList(topLevelKeys));
//                 allPotentialParentKeys.addAll(storyMap.keySet());
                
//                 Map<String, List<SubtaskInfo>> subtasksByParent = fetchSubtasksOf(service, baseUrl, allPotentialParentKeys, filterIspwTypes);

//                 for (String topKey : topLevelKeys) {
//                     reportContent.append("PARENT/EPIC: ").append(topKey)
//                                  .append(" (").append(topLevelSummaries.getOrDefault(topKey, "Unknown Summary")).append(")\n");
                    
//                     List<String> formattedLines = new ArrayList<>();

//                     if (subtasksByParent.containsKey(topKey)) {
//                         String parentSummary = topLevelSummaries.get(topKey);
//                         for (SubtaskInfo subtask : subtasksByParent.get(topKey)) {
//                             String line = String.format("  - %s [%s] [%s] [%s] [%s]",
//                                 subtask.summary, subtask.key, subtask.assignee, parentSummary, topKey);
//                             formattedLines.add(line);
//                         }
//                     }
                    
//                     for (StoryInfo story : storiesInEpics) {
//                         if (topKey.equals(story.epicKey) && subtasksByParent.containsKey(story.key)) {
//                             for (SubtaskInfo subtask : subtasksByParent.get(story.key)) {
//                                 String line = String.format("  - %s [%s] [%s] [%s] [%s]",
//                                     subtask.summary, subtask.key, subtask.assignee, story.summary, story.key);
//                                 formattedLines.add(line);
//                             }
//                         }
//                     }

//                     if (formattedLines.isEmpty()) {
//                         reportContent.append(filterIspwTypes ? "  (No matching sub-tasks found)\n" : "  (No sub-tasks found)\n");
//                     } else {
//                         Collections.sort(formattedLines);
//                         for (String line : formattedLines) {
//                             reportContent.append(line).append("\n");
//                         }
//                     }
//                     reportContent.append("\n");
//                 }
                
//                 reportGeneratedSuccessfully = true;

//             } catch (Exception ex) {
//                 StringWriter sw = new StringWriter();
//                 ex.printStackTrace(new PrintWriter(sw));
//                 SwingUtilities.invokeLater(() -> {
//                     errorArea.setForeground(Color.RED);
//                     errorArea.setText("FATAL ERROR: Report generation failed.\n\n" + sw.toString());
//                 });
//             } finally {
//                 SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
//                 if (reportGeneratedSuccessfully) {
//                     saveAndOpenFile(reportContent.toString());
//                 }
//             }
//         }).start();
//     }
    
//     private void generateFullJsonReport() {
//         // --- CHANGE: Convert input text to uppercase before splitting ---
//         String[] keys = inputKeysArea.getText().trim().toUpperCase().split("\\s+");
        
//         if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
//             JOptionPane.showMessageDialog(this, "Please enter at least one issue key.");
//             return;
//         }

//         errorArea.setText("Starting JSON report generation...");
//         errorArea.setForeground(Color.BLUE);
//         setButtonsEnabled(false);
//         final StringBuilder reportContent = new StringBuilder();

//         new Thread(() -> {
//             boolean reportGeneratedSuccessfully = false;
//             try {
//                 JiraApiService service = mainFrame.getService();
//                 String baseUrl = mainFrame.getBaseUrl();
                
//                 reportContent.append("JIRA FULL JSON REPORT GENERATED: ").append(new java.util.Date()).append("\n");
//                 reportContent.append("====================================================\n\n");

//                 for (int i = 0; i < keys.length; i++) {
//                     String key = keys[i];
//                     final int current = i + 1;
//                     final int total = keys.length;
//                     SwingUtilities.invokeLater(() -> errorArea.setText("Fetching JSON for " + key + " (" + current + " of " + total + ")..."));
                    
//                     String endpoint = "/rest/api/2/issue/" + key + "?fields=*all&expand=renderedFields";
//                     String response = service.executeRequest(baseUrl + endpoint, "GET", null);
                    
//                     reportContent.append("--- FULL JSON FOR ").append(key).append(" ---\n");
//                     reportContent.append(JsonUtils.prettyPrintJson(response)).append("\n\n");
//                 }
                
//                 SwingUtilities.invokeLater(() -> {
//                     errorArea.setForeground(new Color(0, 128, 0));
//                     errorArea.setText("JSON Report generation complete. Opening file...");
//                 });
//                 reportGeneratedSuccessfully = true;

//             } catch (Exception ex) {
//                 StringWriter sw = new StringWriter();
//                 ex.printStackTrace(new PrintWriter(sw));
//                 SwingUtilities.invokeLater(() -> {
//                     errorArea.setForeground(Color.RED);
//                     errorArea.setText("FATAL ERROR: JSON report failed.\n\n" + sw.toString());
//                 });
//             } finally {
//                 SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
//                 if (reportGeneratedSuccessfully) {
//                     saveAndOpenFile(reportContent.toString());
//                 }
//             }
//         }).start();
//     }
    
//     // Helper Methods (unchanged)
//     private Map<String, String> fetchIssueSummaries(JiraApiService service, String baseUrl, String[] keys) throws Exception {
//          Map<String, String> summaries = new HashMap<>();
//          if (keys.length == 0) return summaries;
//         String jql = "key in (" + String.join(",", keys) + ")";
//         JSONObject payload = new JSONObject().put("jql", jql).put("fields", new JSONArray().put("summary"));
//         String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
//         JSONArray issues = new JSONObject(response).getJSONArray("issues");
//         for (int i = 0; i < issues.length(); i++) {
//             JSONObject issue = issues.getJSONObject(i);
//             summaries.put(issue.getString("key"), issue.getJSONObject("fields").getString("summary"));
//         }
//         return summaries;
//     }

//     private List<StoryInfo> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys) throws Exception {
//         final String EPIC_LINK_FIELD_ID = "customfield_13056";
//         List<StoryInfo> stories = new ArrayList<>();
//         if (epicKeys.length == 0) return stories;
//         String jql = String.format("\"Epic Link\" in (%s)", String.join(",", epicKeys));
//         JSONObject payload = new JSONObject()
//             .put("jql", jql)
//             .put("fields", new JSONArray().put("summary").put(EPIC_LINK_FIELD_ID))
//             .put("maxResults", 1000);
//         String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
//         JSONArray issues = new JSONObject(response).getJSONArray("issues");
//         for (int i = 0; i < issues.length(); i++) {
//             JSONObject issue = issues.getJSONObject(i);
//             JSONObject fields = issue.getJSONObject("fields");
//             if (fields.has(EPIC_LINK_FIELD_ID) && !fields.isNull(EPIC_LINK_FIELD_ID)) {
//                 StoryInfo story = new StoryInfo();
//                 story.key = issue.getString("key");
//                 story.summary = fields.getString("summary");
//                 story.epicKey = fields.getString(EPIC_LINK_FIELD_ID);
//                 stories.add(story);
//             }
//         }
//         return stories;
//     }

//     private Map<String, List<SubtaskInfo>> fetchSubtasksOf(JiraApiService service, String baseUrl, Set<String> parentKeys, boolean filter) throws Exception {
//         Map<String, List<SubtaskInfo>> subtasksByParent = new HashMap<>();
//         if (parentKeys.isEmpty()) return subtasksByParent;
//         String jql = "parent in (" + String.join(",", parentKeys) + ")";
//         JSONObject payload = new JSONObject()
//             .put("jql", jql)
//             .put("fields", new JSONArray().put("summary").put("parent").put("assignee"))
//             .put("maxResults", 1000);
//         String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
//         JSONArray issues = new JSONObject(response).getJSONArray("issues");
//         for (int i = 0; i < issues.length(); i++) {
//             JSONObject issue = issues.getJSONObject(i);
//             JSONObject fields = issue.getJSONObject("fields");
//             String summary = fields.getString("summary").trim().replace('\t', ' ');
//             boolean passesFilter = !filter || ISPW_PREFIXES.stream().anyMatch(summary::startsWith);
//             if (passesFilter) {
//                 SubtaskInfo subtask = new SubtaskInfo();
//                 subtask.key = issue.getString("key");
//                 subtask.summary = summary;
//                 subtask.parentKey = fields.getJSONObject("parent").getString("key");
//                 if (fields.has("assignee") && !fields.isNull("assignee")) {
//                     subtask.assignee = fields.getJSONObject("assignee").getString("displayName");
//                 }
//                 subtasksByParent.computeIfAbsent(subtask.parentKey, k -> new ArrayList<>()).add(subtask);
//             }
//         }
//         return subtasksByParent;
//     }

//     private void saveAndOpenFile(String content) {
//         try {
//             File tempFile = File.createTempFile("Jira_Report_", ".txt");
//             try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
//                 writer.write(content);
//             }
//             if (Desktop.isDesktopSupported()) {
//                 Desktop.getDesktop().open(tempFile);
//             }
//         } catch (IOException e) {
//             SwingUtilities.invokeLater(() -> {
//                 errorArea.setForeground(Color.RED);
//                 errorArea.setText("ERROR: Could not save or open the report file.\n" + e.getMessage());
//             });
//         }
//     }
// }