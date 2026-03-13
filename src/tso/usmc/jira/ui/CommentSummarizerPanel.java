package tso.usmc.jira.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
public class CommentSummarizerPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private EmbeddedLlmService llmService;
    private Future<?> activeTask;

    // UI Components
    private final JTextField issueKeyField = new JTextField(15);
    private final JButton summarizeButton = new JButton("Fetch & Summarize");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton resetButton = new JButton("Reset");
    private final JEditorPane summaryPane = new JEditorPane();
    private final JTextArea rawCommentsArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" Ready");

    // Progress components for extraction
    private final JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("Initial setup...");

    public CommentSummarizerPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- TOP: Input ---
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Issue Details"));
        inputPanel.add(new JLabel("Jira Issue Key:"));
        inputPanel.add(issueKeyField);
        inputPanel.add(summarizeButton);
        inputPanel.add(cancelButton);
        inputPanel.add(resetButton);
        
        cancelButton.setEnabled(false);
        cancelButton.setForeground(Color.RED);

        // Progress Panel (Hidden by default, shown during extraction)
        progressPanel.setBorder(BorderFactory.createTitledBorder("One-time AI Setup"));
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setVisible(false);

        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(progressPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Results (Tabs) ---
        JTabbedPane resultTabs = new JTabbedPane();
        
        summaryPane.setEditable(false);
        summaryPane.setContentType("text/html");
        resultTabs.addTab("AI Summary", new JScrollPane(summaryPane));

        rawCommentsArea.setEditable(false);
        rawCommentsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultTabs.addTab("Raw Comments", new JScrollPane(rawCommentsArea));

        add(resultTabs, BorderLayout.CENTER);

        // --- BOTTOM: Status ---
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // --- Action Listeners ---
        summarizeButton.addActionListener(e -> startSummarization());
        cancelButton.addActionListener(e -> cancelTask());
        resetButton.addActionListener(e -> resetPanel());

        // Initialize LLM in background
        initializeLlm();
    }

    private void resetPanel() {
        cancelTask();
        issueKeyField.setText("");
        summaryPane.setText("");
        rawCommentsArea.setText("");
        statusLabel.setText(" Ready");
        summarizeButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    private void cancelTask() {
        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
            if (llmService != null) {
                llmService.terminate(); // KILL the active llama process
            }
            statusLabel.setText(" Process Cancelled.");
            summarizeButton.setEnabled(true);
            cancelButton.setEnabled(false);
        }
    }

    private void initializeLlm() {
        statusLabel.setText(" Initializing Offline LLM Engine...");
        summarizeButton.setEnabled(false);
        
        ExecutionService.submit(() -> {
            try {
                llmService = new EmbeddedLlmService(mainFrame.getJiraConfig(), (task, percent) -> {
                    SwingUtilities.invokeLater(() -> {
                        if (!progressPanel.isVisible()) {
                            progressPanel.setVisible(true);
                            revalidate();
                        }
                        progressLabel.setText(task);
                        progressBar.setValue(percent);
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(" Offline LLM Ready.");
                    summarizeButton.setEnabled(true);
                    progressPanel.setVisible(false);
                    revalidate();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(" LLM Error: " + e.getMessage());
                    progressPanel.setVisible(false);
                    summaryPane.setText("<html><body style='color:red;'><h3>LLM Initialization Failed</h3>" +
                            "<p>Error: " + e.getMessage() + "</p></body></html>");
                });
            }
        });
    }

    private void startSummarization() {
        String issueKey = issueKeyField.getText().trim().toUpperCase();
        if (issueKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an issue key.");
            return;
        }

        summarizeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        summaryPane.setText("<html><body><h3>Processing " + issueKey + "...</h3><p>Fetching data and running local AI model.</p></body></html>");
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
                
                SwingUtilities.invokeLater(() -> rawCommentsArea.setText(formattedRaw));

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
                SwingUtilities.invokeLater(() -> {
                    summarizeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                });
            }
        });
    }

    private void updateStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" " + msg));
    }

    private void updateSummary(String text, boolean partial) {
        SwingUtilities.invokeLater(() -> {
            String title = partial ? "AI Summary (Generating...)" : "AI Summary";
            summaryPane.setText("<html><body><h3>" + title + "</h3><p>" + text.replace("\n", "<br>") + "</p></body></html>");
        });
    }
}
