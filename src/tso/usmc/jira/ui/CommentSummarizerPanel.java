package tso.usmc.jira.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.EmbeddedLlmService;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.ExecutionService;

/**
 * A panel for fetching comments from a Jira issue and summarizing them
 * using a local offline LLM (llama.cpp).
 */
public class CommentSummarizerPanel extends BorderPane {

    private final JiraApiClientGui mainFrame;
    private EmbeddedLlmService llmService;
    private Future<?> activeTask;

    // UI Components
    private final TextField issueKeyField = new TextField();
    private final Button summarizeButton = new Button("Fetch & Summarize");
    private final Button cancelButton = new Button("Cancel");
    private final Button resetButton = new Button("Reset");
    private final WebView summaryPane = new WebView();
    private final TextArea rawCommentsArea = new TextArea();
    private final Label statusLabel = new Label(" Ready");

    // Progress components for extraction
    private final VBox progressPanel = new VBox(5);
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("Initial setup...");

    public CommentSummarizerPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));

        // --- TOP: Input ---
        VBox topPanel = new VBox(10);
        
        HBox inputPanel = new HBox(10);
        inputPanel.getStyleClass().add("card");
        inputPanel.setPadding(new Insets(10));
        
        issueKeyField.setPrefWidth(150);
        cancelButton.setDisable(true);
        cancelButton.setStyle("-fx-text-fill: red;");

        inputPanel.getChildren().addAll(
            new Label("Jira Issue Key:"),
            issueKeyField,
            summarizeButton,
            cancelButton,
            resetButton
        );
        
        // Progress Panel (Hidden by default, shown during extraction)
        progressPanel.getStyleClass().add("card");
        progressPanel.setPadding(new Insets(10));
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressPanel.getChildren().addAll(progressLabel, progressBar);
        progressPanel.setVisible(false);
        progressPanel.setManaged(false); // don't allocate space when invisible

        topPanel.getChildren().addAll(inputPanel, progressPanel);
        setTop(topPanel);

        // --- CENTER: Results (Tabs) ---
        TabPane resultTabs = new TabPane();
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Tab 1: AI Summary
        Tab summaryTab = new Tab("AI Summary", summaryPane);
        
        // Tab 2: Raw Comments
        rawCommentsArea.setEditable(false);
        rawCommentsArea.setStyle("-fx-font-family: monospace;");
        Tab rawTab = new Tab("Raw Comments", rawCommentsArea);

        resultTabs.getTabs().addAll(summaryTab, rawTab);
        setCenter(resultTabs);

        // --- BOTTOM: Status ---
        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);
        setBottom(statusPanel);

        // --- Action Listeners ---
        summarizeButton.setOnAction(e -> startSummarization());
        cancelButton.setOnAction(e -> cancelTask());
        resetButton.setOnAction(e -> resetPanel());

        // Initialize LLM in background
        initializeLlm();
    }

    private void resetPanel() {
        cancelTask();
        issueKeyField.setText("");
        summaryPane.getEngine().loadContent("");
        mainFrame.getThemeManager().applyThemeToWebView(summaryPane);
        rawCommentsArea.setText("");
        statusLabel.setText(" Ready");
        summarizeButton.setDisable(false);
        cancelButton.setDisable(true);
    }

    private void cancelTask() {
        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
            if (llmService != null) {
                llmService.terminate(); // KILL the active llama process
            }
            statusLabel.setText(" Process Cancelled.");
            summarizeButton.setDisable(false);
            cancelButton.setDisable(true);
        }
    }

    private void initializeLlm() {
        statusLabel.setText(" Initializing Offline LLM Engine...");
        summarizeButton.setDisable(true);
        
        ExecutionService.submit(() -> {
            try {
                llmService = new EmbeddedLlmService(mainFrame.getJiraConfig(), (task, percent) -> {
                    Platform.runLater(() -> {
                        if (!progressPanel.isVisible()) {
                            progressPanel.setVisible(true);
                            progressPanel.setManaged(true);
                        }
                        progressLabel.setText(task);
                        progressBar.setProgress(percent / 100.0);
                    });
                });
                Platform.runLater(() -> {
                    statusLabel.setText(" Offline LLM Ready.");
                    summarizeButton.setDisable(false);
                    progressPanel.setVisible(false);
                    progressPanel.setManaged(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText(" LLM Error: " + e.getMessage());
                    progressPanel.setVisible(false);
                    progressPanel.setManaged(false);
                    summaryPane.getEngine().loadContent("<html><body style='color:red;'><h3>LLM Initialization Failed</h3>" +
                            "<p>Error: " + e.getMessage() + "</p></body></html>");
                    mainFrame.getThemeManager().applyThemeToWebView(summaryPane);
                });
            }
        });
    }

    private void startSummarization() {
        String issueKey = issueKeyField.getText().trim().toUpperCase();
        if (issueKey.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText(null);
            alert.setContentText("Please enter an issue key.");
            alert.showAndWait();
            return;
        }

        summarizeButton.setDisable(true);
        cancelButton.setDisable(false);
        summaryPane.getEngine().loadContent("<html><body><h3>Processing " + issueKey + "...</h3><p>Fetching data and running local AI model.</p></body></html>");
        mainFrame.getThemeManager().applyThemeToWebView(summaryPane);
        rawCommentsArea.setText("");
        statusLabel.setText(" Fetching comments from Jira...");

        activeTask = ExecutionService.submit(() -> {
            try {
                // 1. Fetch Comments
                updateStatus("Fetching data from Jira...");
                JiraApiService api = mainFrame.getService();
                String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + issueKey + "/comment";
                String response = api.executeRequest(url, "GET", null);
                
                JSONObject root = new JSONObject(response);
                JSONArray comments = root.getJSONArray("comments");
                
                if (comments.length() == 0) {
                    updateSummary("No comments found for this issue.", false);
                    updateStatus(" Done.");
                    return;
                }

                StringBuilder aiInput = new StringBuilder();
                StringBuilder displayRaw = new StringBuilder();

                for (int i = 0; i < comments.length(); i++) {
                    JSONObject c = comments.getJSONObject(i);
                    String author = c.getJSONObject("author").getString("displayName");
                    String created = c.getString("created");
                    String body = c.getString("body");

                    displayRaw.append("Author: ").append(author).append(" | Date: ").append(created).append("\n").append(body).append("\n\n------------------\n\n");
                    aiInput.append("Comment by ").append(author).append(": ").append(body).append("\n");
                }

                String formattedRaw = displayRaw.toString();
                String rawTextForAI = aiInput.toString();
                
                Platform.runLater(() -> rawCommentsArea.setText(formattedRaw));

                // 2. Run LLM
                updateStatus("Local AI Engine: Analyzing " + comments.length() + " comments...");
                final StringBuilder accumulated = new StringBuilder();
                
                String finalSummary = llmService.summarizeActions(rawTextForAI, new EmbeddedLlmService.ProgressListener() {
                    @Override
                    public void onProgress(String task, int percent) {
                        updateStatus(task);
                    }
                    @Override
                    public void onPartialOutput(String text) {
                        accumulated.append(text);
                        updateSummary(accumulated.toString(), true);
                    }
                });

                updateSummary(finalSummary, false);
                updateStatus(" Summarization complete.");

            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) {
                    updateStatus(" Error: " + ex.getMessage());
                    updateSummary("<span style='color:red;'><b>Error:</b> " + ex.getMessage() + "</span>", false);
                }
            } finally {
                Platform.runLater(() -> {
                    summarizeButton.setDisable(false);
                    cancelButton.setDisable(true);
                });
            }
        });
    }

    private void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(" " + msg));
    }

    private void updateSummary(String text, boolean partial) {
        Platform.runLater(() -> {
            String title = partial ? "AI Summary (Generating...)" : "AI Summary";
            summaryPane.getEngine().loadContent("<html><body><h3>" + title + "</h3><p>" + text.replace("\n", "<br>") + "</p></body></html>");
            mainFrame.getThemeManager().applyThemeToWebView(summaryPane);
        });
    }
}
