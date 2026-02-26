package tso.usmc.jira.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.EmbeddedLlmService;
import tso.usmc.jira.service.JiraApiService;

/**
 * A panel for fetching comments from a Jira issue and summarizing them
 * using a local offline LLM (llama.cpp).
 */
public class CommentSummarizerPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private EmbeddedLlmService llmService;

    // UI Components
    private final JTextField issueKeyField = new JTextField(15);
    private final JButton summarizeButton = new JButton("Fetch & Summarize");
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

        // Initialize LLM in background
        initializeLlm();
    }

    private void initializeLlm() {
        statusLabel.setText(" Initializing Offline LLM Engine...");
        summarizeButton.setEnabled(false);
        
        new SwingWorker<EmbeddedLlmService, Object[]>() {
            @Override
            protected EmbeddedLlmService doInBackground() throws Exception {
                return new EmbeddedLlmService(mainFrame.getJiraConfig(), (task, percent) -> {
                    publish(new Object[]{task, percent});
                });
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                if (!progressPanel.isVisible()) {
                    progressPanel.setVisible(true);
                    revalidate();
                }
                Object[] latest = chunks.get(chunks.size() - 1);
                progressLabel.setText((String) latest[0]);
                progressBar.setValue((Integer) latest[1]);
            }

            @Override
            protected void done() {
                try {
                    llmService = get();
                    statusLabel.setText(" Offline LLM Ready.");
                    summarizeButton.setEnabled(true);
                    progressPanel.setVisible(false);
                    revalidate();
                } catch (Exception e) {
                    statusLabel.setText(" LLM Error: " + e.getMessage());
                    progressPanel.setVisible(false);
                    summaryPane.setText("<html><body style='color:red;'><h3>LLM Initialization Failed</h3>" +
                            "<p>Check your <b>JiraConfig.ini</b> paths or ensure <code>llama-cli.exe</code> and the model are in the <code>embedding</code> folder.</p>" +
                            "<p>Error: " + e.getMessage() + "</p></body></html>");
                }
            }
        }.execute();
    }

    private void startSummarization() {
        String issueKey = issueKeyField.getText().trim().toUpperCase();
        if (issueKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an issue key.");
            return;
        }

        summarizeButton.setEnabled(false);
        summaryPane.setText("<html><body><h3>Processing " + issueKey + "...</h3><p>Fetching data and running local AI model. This may take a minute.</p></body></html>");
        rawCommentsArea.setText("");
        statusLabel.setText(" Fetching comments from Jira...");

        new SwingWorker<String, String>() {
            private String rawTextForAI;
            private String formattedRawComments;

            @Override
            protected String doInBackground() throws Exception {
                // 1. Fetch Comments
                publish("Fetching data from Jira...");
                JiraApiService api = mainFrame.getService();
                String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + issueKey + "/comment";
                String response = api.executeRequest(url, "GET", null);
                
                JSONObject root = new JSONObject(response);
                JSONArray comments = root.getJSONArray("comments");
                
                if (comments.length() == 0) {
                    return "No comments found for this issue.";
                }

                StringBuilder aiInput = new StringBuilder();
                StringBuilder displayRaw = new StringBuilder();

                for (int i = 0; i < comments.length(); i++) {
                    JSONObject c = comments.getJSONObject(i);
                    String author = c.getJSONObject("author").getString("displayName");
                    String created = c.getString("created");
                    String body = c.getString("body");

                    String header = "Author: " + author + " | Date: " + created + "\n";
                    displayRaw.append(header).append(body).append("\n\n------------------\n\n");
                    aiInput.append("Comment by ").append(author).append(": ").append(body).append("\n");
                }

                this.formattedRawComments = displayRaw.toString();
                this.rawTextForAI = aiInput.toString();

                // 2. Run LLM with progress updates
                publish("Local AI Engine: Starting analysis of " + comments.length() + " comments...");
                return llmService.summarizeActions(rawTextForAI, (task, percent) -> {
                    publish(task);
                });
            }

            @Override
            protected void process(java.util.List<String> statusUpdates) {
                // Update the status label with the latest message from the worker
                String latest = statusUpdates.get(statusUpdates.get(statusUpdates.size() - 1).isEmpty() ? 0 : statusUpdates.size() - 1);
                statusLabel.setText(" " + latest);
            }

            @Override
            protected void done() {
                try {
                    String summary = get();
                    summaryPane.setText("<html><body><h3>AI Summary for " + issueKey + "</h3>" +
                            "<p>" + summary.replace("\n", "<br>") + "</p></body></html>");
                    rawCommentsArea.setText(formattedRawComments);
                    statusLabel.setText(" Summarization complete.");
                } catch (Exception ex) {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    summaryPane.setText("<html><body style='color:red;'><h3>Summarization Failed</h3>" +
                            "<pre>" + ex.getMessage() + "</pre></body></html>");
                    statusLabel.setText(" Error during summarization.");
                } finally {
                    summarizeButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
