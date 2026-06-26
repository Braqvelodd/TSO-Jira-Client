package tso.usmc.jira.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.service.JqlAutocompleteService;
import tso.usmc.jira.util.ExecutionService;
import tso.usmc.jira.util.JiraUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A beautiful, full-featured Jira issue viewer panel.
 * Provides a SplitPane layout resembling a native Jira issue view,
 * including HTML description rendering, transitions, attachments, 
 * issue links, comments, and assignee update with autocomplete.
 */
public class IssueViewPanel extends BorderPane {

    private final JiraApiClientGui mainFrame;
    private String currentIssueKey = null;
    private List<JSONObject> currentTransitions = new ArrayList<>();

    // Top Search Bar Components
    private final TextField issueKeySearchField = new TextField();
    private final Button loadButton = new Button("Load Issue");
    private final Button browseButton = new Button("Open in Browser");
    private final Button copyButton = new Button("Copy Key & Summary");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    // Main Layout Splits
    private final SplitPane splitPane = new SplitPane();

    // Left Panel Components (Main Content)
    private final VBox leftPaneContent = new VBox(15);
    private final Label projectHeaderLabel = new Label("No issue loaded");
    private final Label summaryLabel = new Label("Enter an issue key above and click Load.");
    
    private final VBox descriptionCard = new VBox(5);
    private final WebView descriptionWebView = new WebView();
    
    private final VBox subtasksCard = new VBox(5);
    private final VBox subtasksVBox = new VBox(5);
    
    private final VBox attachmentsCard = new VBox(10);
    private final FlowPane attachmentsFlowPane = new FlowPane(10, 10);
    
    private final VBox linksCard = new VBox(5);
    private final VBox linksVBox = new VBox(5);
    
    private final VBox commentsCard = new VBox(10);
    private final WebView commentsWebView = new WebView();
    private final TextArea commentInputField = new TextArea();
    private final Button addCommentButton = new Button("Add Comment");

    // Right Panel Components (Sidebar Details)
    private final VBox rightPaneContent = new VBox(15);
    
    private final VBox statusCard = new VBox(10);
    private final Label statusBadgeLabel = new Label("UNKNOWN");
    private final ComboBox<String> transitionCombo = new ComboBox<>();
    private final Button transitionButton = new Button("Transition");

    private final VBox detailsCard = new VBox(10);
    private final JiraUserAutocompleteTextField assigneeField = new JiraUserAutocompleteTextField(20);
    private final Button assignButton = new Button("Assign");
    private final Hyperlink assignToMeLink = new Hyperlink("Assign to me");
    private final Label reporterLabel = new Label("-");
    private final Label creatorLabel = new Label("-");
    private final Label priorityLabel = new Label("-");
    private final FlowPane labelsFlowPane = new FlowPane(5, 5);
    private final Label componentsLabel = new Label("-");
    private final Label fixVersionsLabel = new Label("-");
    private final Label affectsVersionsLabel = new Label("-");
    private final Label createdLabel = new Label("-");
    private final Label updatedLabel = new Label("-");
    private final Label resolvedLabel = new Label("-");

    // Bottom Status Bar
    private final Label statusText = new Label(" Ready");

    public IssueViewPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));

        // --- TOP: Search Panel ---
        HBox searchPanel = new HBox(10);
        searchPanel.getStyleClass().add("connection-panel");
        searchPanel.setAlignment(Pos.CENTER_LEFT);
        searchPanel.setPadding(new Insets(10));
        
        issueKeySearchField.setPromptText("e.g., TSO-123");
        issueKeySearchField.setPrefWidth(150);
        issueKeySearchField.setOnAction(e -> triggerLoad());
        
        loadButton.getStyleClass().add("primary-button");
        loadButton.setOnAction(e -> triggerLoad());
        
        browseButton.setDisable(true);
        browseButton.setOnAction(e -> {
            if (currentIssueKey != null) {
                JiraUtils.browseIssue(mainFrame.getBaseUrl(), currentIssueKey);
            }
        });

        copyButton.setDisable(true);
        copyButton.setOnAction(e -> copyKeyAndSummaryToClipboard());

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        searchPanel.getChildren().addAll(
            new Label("Jira Issue Key:"),
            issueKeySearchField,
            loadButton,
            browseButton,
            copyButton,
            progressIndicator
        );
        setTop(searchPanel);

        // --- CENTER: Split Pane Setup ---
        splitPane.setDividerPositions(0.65); // 65% left, 35% right

        // --- Left Panel Scroll Setup ---
        ScrollPane leftScrollPane = new ScrollPane(leftPaneContent);
        leftScrollPane.setFitToWidth(true);
        leftScrollPane.setPadding(new Insets(10));
        leftPaneContent.getStyleClass().add("pane");

        // 1. Header block
        VBox headerBlock = new VBox(5);
        projectHeaderLabel.setStyle("-fx-text-fill: -fx-accent; -fx-font-size: 12px;");
        summaryLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        summaryLabel.setWrapText(true);
        headerBlock.getChildren().addAll(projectHeaderLabel, summaryLabel);
        leftPaneContent.getChildren().add(headerBlock);

        // 2. Description Card
        descriptionCard.getStyleClass().add("card");
        Label descTitle = new Label("Description");
        descTitle.getStyleClass().add("card-title");
        descriptionWebView.setPrefHeight(250);
        descriptionWebView.setMinHeight(100);
        descriptionCard.getChildren().addAll(descTitle, descriptionWebView);
        leftPaneContent.getChildren().add(descriptionCard);

        // 2b. Sub-tasks Card
        subtasksCard.getStyleClass().add("card");
        Label subtasksTitle = new Label("Sub-tasks");
        subtasksTitle.getStyleClass().add("card-title");
        subtasksVBox.setPadding(new Insets(5, 0, 5, 0));
        subtasksCard.getChildren().addAll(subtasksTitle, subtasksVBox);
        subtasksCard.setVisible(false);
        subtasksCard.setManaged(false);
        leftPaneContent.getChildren().add(subtasksCard);

        // 3. Attachments Card
        attachmentsCard.getStyleClass().add("card");
        Label attachTitle = new Label("Attachments");
        attachTitle.getStyleClass().add("card-title");
        attachmentsFlowPane.setPadding(new Insets(5, 0, 5, 0));
        attachmentsCard.getChildren().addAll(attachTitle, attachmentsFlowPane);
        attachmentsCard.setVisible(false);
        attachmentsCard.setManaged(false);
        leftPaneContent.getChildren().add(attachmentsCard);

        // 4. Issue Links Card
        linksCard.getStyleClass().add("card");
        Label linksTitle = new Label("Issue Links");
        linksTitle.getStyleClass().add("card-title");
        linksVBox.setPadding(new Insets(5, 0, 5, 0));
        linksCard.getChildren().addAll(linksTitle, linksVBox);
        linksCard.setVisible(false);
        linksCard.setManaged(false);
        leftPaneContent.getChildren().add(linksCard);

        // 5. Comments Card
        commentsCard.getStyleClass().add("card");
        Label commentsTitle = new Label("Comments");
        commentsTitle.getStyleClass().add("card-title");
        commentsWebView.setPrefHeight(250);
        commentsWebView.setMinHeight(150);
        
        commentInputField.setPromptText("Add a comment...");
        commentInputField.setPrefHeight(80);
        commentInputField.setWrapText(true);
        
        addCommentButton.setOnAction(e -> postComment());
        
        HBox commentActionBox = new HBox(addCommentButton);
        commentActionBox.setAlignment(Pos.CENTER_RIGHT);

        commentsCard.getChildren().addAll(commentsTitle, commentsWebView, commentInputField, commentActionBox);
        leftPaneContent.getChildren().add(commentsCard);

        // --- Right Panel Scroll Setup ---
        ScrollPane rightScrollPane = new ScrollPane(rightPaneContent);
        rightScrollPane.setFitToWidth(true);
        rightScrollPane.setPadding(new Insets(10));
        rightPaneContent.getStyleClass().add("pane");

        // 1. Status Card
        statusCard.getStyleClass().add("card");
        Label statusCardTitle = new Label("Status");
        statusCardTitle.getStyleClass().add("card-title");
        statusBadgeLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5px 10px; -fx-background-radius: 4px; -fx-text-fill: white; -fx-background-color: #6b7280;");
        statusBadgeLabel.setAlignment(Pos.CENTER);
        statusBadgeLabel.setMaxWidth(Double.MAX_VALUE);

        HBox transitionBox = new HBox(10);
        transitionCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(transitionCombo, Priority.ALWAYS);
        transitionCombo.setPromptText("Choose Transition");
        transitionButton.setOnAction(e -> executeTransition());
        transitionBox.getChildren().addAll(transitionCombo, transitionButton);

        statusCard.getChildren().addAll(statusCardTitle, statusBadgeLabel, transitionBox);
        rightPaneContent.getChildren().add(statusCard);

        // 2. Details Card
        detailsCard.getStyleClass().add("card");
        Label detailsTitle = new Label("Details");
        detailsTitle.getStyleClass().add("card-title");

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(10);
        detailsGrid.setVgap(10);

        // Column widths
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(110);
        col1.setPrefWidth(110);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        detailsGrid.getColumnConstraints().addAll(col1, col2);

        // Row 0: Assignee
        detailsGrid.add(new Label("Assignee:"), 0, 0);
        HBox assignBox = new HBox(5);
        assignBox.setAlignment(Pos.CENTER_LEFT);
        assignButton.setOnAction(e -> updateAssignee());
        HBox.setHgrow(assigneeField, Priority.ALWAYS);
        assignBox.getChildren().addAll(assigneeField, assignButton);
        
        VBox assigneeVBox = new VBox(3);
        assignToMeLink.setOnAction(e -> assignToMe());
        assignToMeLink.setStyle("-fx-font-size: 11px;");
        assigneeVBox.getChildren().addAll(assignBox, assignToMeLink);
        detailsGrid.add(assigneeVBox, 1, 0);

        // Row 1: Reporter
        detailsGrid.add(new Label("Reporter:"), 0, 1);
        reporterLabel.setWrapText(true);
        detailsGrid.add(reporterLabel, 1, 1);

        // Row 2: Creator
        detailsGrid.add(new Label("Creator:"), 0, 2);
        creatorLabel.setWrapText(true);
        detailsGrid.add(creatorLabel, 1, 2);

        // Row 3: Priority
        detailsGrid.add(new Label("Priority:"), 0, 3);
        detailsGrid.add(priorityLabel, 1, 3);

        // Row 4: Components
        detailsGrid.add(new Label("Component/s:"), 0, 4);
        componentsLabel.setWrapText(true);
        detailsGrid.add(componentsLabel, 1, 4);

        // Row 5: Fix Version/s
        detailsGrid.add(new Label("Fix Version/s:"), 0, 5);
        fixVersionsLabel.setWrapText(true);
        detailsGrid.add(fixVersionsLabel, 1, 5);

        // Row 6: Affects Version/s
        detailsGrid.add(new Label("Affects Version/s:"), 0, 6);
        affectsVersionsLabel.setWrapText(true);
        detailsGrid.add(affectsVersionsLabel, 1, 6);

        // Row 7: Labels
        detailsGrid.add(new Label("Labels:"), 0, 7);
        detailsGrid.add(labelsFlowPane, 1, 7);

        // Separator
        Separator detailsSeparator = new Separator();
        detailsSeparator.setPadding(new Insets(5, 0, 5, 0));
        
        GridPane datesGrid = new GridPane();
        datesGrid.setHgap(10);
        datesGrid.setVgap(8);
        datesGrid.getColumnConstraints().addAll(col1, col2);

        // Dates Rows
        datesGrid.add(new Label("Created:"), 0, 0);
        datesGrid.add(createdLabel, 1, 0);
        datesGrid.add(new Label("Updated:"), 0, 1);
        datesGrid.add(updatedLabel, 1, 1);
        datesGrid.add(new Label("Resolved:"), 0, 2);
        datesGrid.add(resolvedLabel, 1, 2);

        detailsCard.getChildren().addAll(detailsTitle, detailsGrid, detailsSeparator, datesGrid);
        rightPaneContent.getChildren().add(detailsCard);

        splitPane.getItems().addAll(leftScrollPane, rightScrollPane);
        setCenter(splitPane);

        // --- BOTTOM: Status Bar ---
        HBox bottomPanel = new HBox();
        bottomPanel.getStyleClass().add("status-bar");
        statusText.getStyleClass().add("status-text");
        bottomPanel.getChildren().add(statusText);
        setBottom(bottomPanel);

        // Initialize Autocomplete lazily
        initializeAutocomplete();
        resetView();
    }

    private void resetView() {
        currentIssueKey = null;
        projectHeaderLabel.setText("No issue loaded");
        summaryLabel.setText("Enter an issue key above and click Load.");
        
        descriptionWebView.getEngine().loadContent("");
        
        subtasksVBox.getChildren().clear();
        subtasksCard.setVisible(false);
        subtasksCard.setManaged(false);

        attachmentsFlowPane.getChildren().clear();
        attachmentsCard.setVisible(false);
        attachmentsCard.setManaged(false);
        
        linksVBox.getChildren().clear();
        linksCard.setVisible(false);
        linksCard.setManaged(false);
        
        commentsWebView.getEngine().loadContent("");
        commentInputField.setText("");
        commentInputField.setDisable(true);
        addCommentButton.setDisable(true);

        statusBadgeLabel.setText("UNKNOWN");
        statusBadgeLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5px 10px; -fx-background-radius: 4px; -fx-text-fill: white; -fx-background-color: #6b7280;");
        
        transitionCombo.getItems().clear();
        transitionCombo.setDisable(true);
        transitionButton.setDisable(true);

        assigneeField.setText("");
        assigneeField.setDisable(true);
        assignButton.setDisable(true);
        assignToMeLink.setDisable(true);

        reporterLabel.setText("-");
        creatorLabel.setText("-");
        priorityLabel.setText("-");
        componentsLabel.setText("-");
        fixVersionsLabel.setText("-");
        affectsVersionsLabel.setText("-");
        labelsFlowPane.getChildren().clear();
        
        createdLabel.setText("-");
        updatedLabel.setText("-");
        resolvedLabel.setText("-");

        browseButton.setDisable(true);
        copyButton.setDisable(true);
    }

    private void initializeAutocomplete() {
        ExecutionService.submit(() -> {
            try {
                JqlAutocompleteService autocompleteService = new JqlAutocompleteService(mainFrame.getService(), mainFrame.getBaseUrl(), mainFrame.getJiraConfig());
                boolean enabled = mainFrame.getJiraConfig().isAutocompleteEnabled();
                Platform.runLater(() -> {
                    assigneeField.setService(autocompleteService);
                    assigneeField.setAutocompleteEnabled(enabled);
                });
            } catch (Exception e) {
                // Silent fallback: user will have to enter exact username
            }
        });
    }

    private void triggerLoad() {
        String keyInput = issueKeySearchField.getText().trim();
        if (keyInput.isEmpty()) {
            showError("Input Required", "Please enter a valid Jira Issue Key.");
            return;
        }
        
        String key = JiraUtils.cleanIssueKey(keyInput).toUpperCase();
        issueKeySearchField.setText(key);
        loadIssue(key);
    }

    public void loadIssue(String issueKey) {
        setLoading(true);
        statusText.setText(" Loading issue " + issueKey + "...");

        ExecutionService.submit(() -> {
            try {
                // 1. Fetch Issue Data with renderedFields expanded
                JiraApiService service = mainFrame.getService();
                String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + issueKey 
                        + "?expand=renderedFields,names&fields=*all,attachment,issuelinks,comment,subtasks";
                String response = service.executeRequest(url, "GET", null);
                JSONObject root = new JSONObject(response);
                
                // 2. Fetch Transitions in Parallel/Sequence
                MetadataCacheService cacheService = mainFrame.getMetadataService();
                List<JSONObject> transitions = cacheService.getTransitions(issueKey);

                Platform.runLater(() -> {
                    try {
                        populateIssue(root, transitions);
                        statusText.setText(" Loaded " + issueKey + " successfully.");
                    } catch (Exception ex) {
                        showError("Display Error", "Error displaying issue fields: " + ex.getMessage());
                        resetView();
                    } finally {
                        setLoading(false);
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Load Error", "Failed to load issue " + issueKey + ": " + ex.getMessage());
                    resetView();
                    setLoading(false);
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        loadButton.setDisable(loading);
        issueKeySearchField.setDisable(loading);
    }

    private void populateIssue(JSONObject root, List<JSONObject> transitions) {
        this.currentIssueKey = root.getString("key");
        this.currentTransitions = transitions;

        JSONObject fields = root.getJSONObject("fields");
        JSONObject renderedFields = root.optJSONObject("renderedFields");

        // --- Enable Controls ---
        browseButton.setDisable(false);
        copyButton.setDisable(false);
        commentInputField.setDisable(false);
        addCommentButton.setDisable(false);
        assigneeField.setDisable(false);
        assignButton.setDisable(false);
        assignToMeLink.setDisable(false);

        // --- Header Block ---
        JSONObject project = fields.optJSONObject("project");
        String projText = project != null ? project.optString("name") + " (" + project.optString("key") + ")" : "Unknown Project";
        projectHeaderLabel.setText(projText + " / " + currentIssueKey);
        
        String summary = fields.optString("summary");
        summaryLabel.setText(summary);

        // --- Description ---
        String descriptionHtml = null;
        if (renderedFields != null) {
            descriptionHtml = renderedFields.optString("description");
        }
        if (descriptionHtml == null || descriptionHtml.trim().isEmpty()) {
            String rawDesc = fields.optString("description", "");
            descriptionHtml = rawDesc.trim().isEmpty() 
                ? "<div style='color:gray; font-style:italic;'>No description provided.</div>" 
                : rawDesc.replace("\n", "<br>");
        }
        
        // Wrap description html in HTML/Body tag to apply dark/light styles from theme stylesheet
        String docStyle = "html, body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.5; padding: 2px; }";
        descriptionWebView.getEngine().loadContent("<html><head><style>" + docStyle + "</style></head><body>" + descriptionHtml + "</body></html>");
        mainFrame.getThemeManager().applyThemeToWebView(descriptionWebView);

        // --- Sub-tasks ---
        subtasksVBox.getChildren().clear();
        JSONArray subtasksArray = fields.optJSONArray("subtasks");
        if (subtasksArray != null && subtasksArray.length() > 0) {
            subtasksCard.setVisible(true);
            subtasksCard.setManaged(true);
            
            for (int i = 0; i < subtasksArray.length(); i++) {
                JSONObject sub = subtasksArray.getJSONObject(i);
                String subKey = sub.getString("key");
                JSONObject subFields = sub.getJSONObject("fields");
                String subSummary = subFields.optString("summary", "");
                
                JSONObject subStatus = subFields.optJSONObject("status");
                String subStatusName = subStatus != null ? subStatus.optString("name", "Unknown") : "Unknown";
                JSONObject subStatusCat = subStatus != null ? subStatus.optJSONObject("statusCategory") : null;
                String subColorName = subStatusCat != null ? subStatusCat.optString("colorName", "gray") : "gray";

                HBox subRow = new HBox(10);
                subRow.setAlignment(Pos.CENTER_LEFT);

                Hyperlink subLink = new Hyperlink(subKey);
                subLink.setOnAction(e -> {
                    issueKeySearchField.setText(subKey);
                    loadIssue(subKey);
                });

                Label subStatusBadge = new Label(subStatusName);
                String subBadgeStyle = "-fx-font-size: 10px; -fx-padding: 2px 6px; -fx-background-radius: 3px; -fx-text-fill: white;";
                if ("green".equalsIgnoreCase(subColorName)) {
                    subBadgeStyle += "-fx-background-color: #10b981;";
                } else if ("yellow".equalsIgnoreCase(subColorName) || "blue".equalsIgnoreCase(subColorName)) {
                    subBadgeStyle += "-fx-background-color: #f59e0b;";
                } else {
                    subBadgeStyle += "-fx-background-color: #64748b;";
                }
                subStatusBadge.setStyle(subBadgeStyle);

                Label subSumLabel = new Label(" - " + subSummary);
                subSumLabel.setStyle("-fx-font-size: 11px;");
                subSumLabel.setWrapText(true);

                subRow.getChildren().addAll(subLink, subStatusBadge, subSumLabel);
                subtasksVBox.getChildren().add(subRow);
            }
        } else {
            subtasksCard.setVisible(false);
            subtasksCard.setManaged(false);
        }

        // --- Attachments ---
        attachmentsFlowPane.getChildren().clear();
        JSONArray attachmentsArray = fields.optJSONArray("attachment");
        if (attachmentsArray != null && attachmentsArray.length() > 0) {
            attachmentsCard.setVisible(true);
            attachmentsCard.setManaged(true);
            
            for (int i = 0; i < attachmentsArray.length(); i++) {
                JSONObject att = attachmentsArray.getJSONObject(i);
                String filename = att.getString("filename");
                String contentUrl = att.getString("content");
                int bytes = att.optInt("size", 0);
                
                String sizeStr = bytes > 1024 * 1024 
                    ? String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                    : String.format("%d KB", bytes / 1024);

                HBox attCapsule = new HBox(8);
                attCapsule.setAlignment(Pos.CENTER_LEFT);
                attCapsule.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-border-color; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 4px 8px;");

                Label nameLabel = new Label(filename + " (" + sizeStr + ")");
                nameLabel.setStyle("-fx-font-size: 11px;");
                
                Button dlButton = new Button("Open");
                dlButton.setStyle("-fx-font-size: 10px; -fx-padding: 2px 6px;");
                dlButton.setOnAction(e -> openAttachment(contentUrl, filename));

                attCapsule.getChildren().addAll(nameLabel, dlButton);
                attachmentsFlowPane.getChildren().add(attCapsule);
            }
        } else {
            attachmentsCard.setVisible(false);
            attachmentsCard.setManaged(false);
        }

        // --- Issue Links ---
        linksVBox.getChildren().clear();
        JSONArray linksArray = fields.optJSONArray("issuelinks");
        if (linksArray != null && linksArray.length() > 0) {
            linksCard.setVisible(true);
            linksCard.setManaged(true);

            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject link = linksArray.getJSONObject(i);
                JSONObject type = link.getJSONObject("type");
                
                JSONObject linkedIssue = link.optJSONObject("inwardIssue");
                String direction = type.optString("inward");
                
                if (linkedIssue == null) {
                    linkedIssue = link.optJSONObject("outwardIssue");
                    direction = type.optString("outward");
                }
                
                if (linkedIssue != null) {
                    String linkKey = linkedIssue.getString("key");
                    JSONObject liFields = linkedIssue.optJSONObject("fields");
                    String linkSummary = liFields != null ? liFields.optString("summary", "") : "";
                    
                    JSONObject liStatus = liFields != null ? liFields.optJSONObject("status") : null;
                    String linkStatusName = liStatus != null ? liStatus.optString("name", "Unknown") : "Unknown";

                    HBox linkRow = new HBox(10);
                    linkRow.setAlignment(Pos.CENTER_LEFT);
                    
                    Label relLabel = new Label(direction + ": ");
                    relLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                    relLabel.setMinWidth(110);

                    Hyperlink linkBtn = new Hyperlink(linkKey);
                    linkBtn.setOnAction(e -> {
                        issueKeySearchField.setText(linkKey);
                        loadIssue(linkKey);
                    });
                    
                    Label sumLabel = new Label(" - " + linkSummary);
                    sumLabel.setStyle("-fx-font-size: 11px;");
                    sumLabel.setWrapText(false);
                    
                    Label linkStatusBadge = new Label(linkStatusName);
                    linkStatusBadge.setStyle("-fx-font-size: 10px; -fx-padding: 1px 4px; -fx-background-radius: 3px; -fx-text-fill: white; -fx-background-color: #6b7280;");
                    
                    linkRow.getChildren().addAll(relLabel, linkBtn, linkStatusBadge, sumLabel);
                    linksVBox.getChildren().add(linkRow);
                }
            }
        } else {
            linksCard.setVisible(false);
            linksCard.setManaged(false);
        }

        // --- Comments Block ---
        JSONObject commentRoot = fields.optJSONObject("comment");
        JSONArray commentsArray = commentRoot != null ? commentRoot.optJSONArray("comments") : null;
        
        JSONArray renderedCommentsArray = null;
        if (renderedFields != null && renderedFields.optJSONObject("comment") != null) {
            renderedCommentsArray = renderedFields.getJSONObject("comment").optJSONArray("comments");
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>body { font-family: 'Segoe UI', Arial, sans-serif; padding: 5px; margin: 0; }</style></head><body>");

        if (commentsArray == null || commentsArray.length() == 0) {
            html.append("<div style='color: gray; font-style: italic; font-size:12px; padding:10px;'>No comments on this issue.</div>");
        } else {
            for (int i = 0; i < commentsArray.length(); i++) {
                JSONObject c = commentsArray.getJSONObject(i);
                String cId = c.optString("id");
                
                JSONObject authorObj = c.optJSONObject("author");
                String author = authorObj != null ? authorObj.optString("displayName") : "Unknown";
                String created = c.optString("created");
                
                String bodyHtml = null;
                if (renderedCommentsArray != null) {
                    for (int j = 0; j < renderedCommentsArray.length(); j++) {
                        JSONObject rc = renderedCommentsArray.getJSONObject(j);
                        if (cId.equals(rc.optString("id"))) {
                            bodyHtml = rc.optString("body");
                            break;
                        }
                    }
                }
                
                if (bodyHtml == null || bodyHtml.trim().isEmpty()) {
                    bodyHtml = c.optString("body").replace("\n", "<br>");
                }

                String formattedDate = created;
                try {
                    formattedDate = created.substring(0, 16).replace("T", " ");
                } catch (Exception ignored) {}

                html.append("<div class='comment-card'>");
                html.append("<div class='comment-header'>");
                html.append("<span>").append(author).append("</span>");
                html.append("<span class='comment-date'>").append(formattedDate).append("</span>");
                html.append("</div>");
                html.append("<div class='comment-body'>").append(bodyHtml).append("</div>");
                html.append("</div>");
            }
        }
        html.append("</body></html>");
        commentsWebView.getEngine().loadContent(html.toString());
        mainFrame.getThemeManager().applyThemeToWebView(commentsWebView);

        // --- Status Badge & Colors ---
        JSONObject statusObj = fields.optJSONObject("status");
        String statusName = statusObj != null ? statusObj.optString("name", "UNKNOWN") : "UNKNOWN";
        statusBadgeLabel.setText(statusName.toUpperCase());

        JSONObject statusCat = statusObj != null ? statusObj.optJSONObject("statusCategory") : null;
        String colorName = statusCat != null ? statusCat.optString("colorName", "gray") : "gray";
        
        String badgeColorStyle = "-fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-text-fill: white;";
        if ("green".equalsIgnoreCase(colorName)) {
            badgeColorStyle += "-fx-background-color: #10b981;"; // Emerald 500
        } else if ("yellow".equalsIgnoreCase(colorName) || "blue".equalsIgnoreCase(colorName)) {
            badgeColorStyle += "-fx-background-color: #f59e0b;"; // Amber 500
        } else {
            badgeColorStyle += "-fx-background-color: #64748b;"; // Slate 500
        }
        statusBadgeLabel.setStyle(badgeColorStyle);

        // --- Transitions Combo ---
        transitionCombo.getItems().clear();
        if (transitions != null && !transitions.isEmpty()) {
            transitionCombo.setDisable(false);
            transitionButton.setDisable(false);
            for (JSONObject t : transitions) {
                transitionCombo.getItems().add(t.getString("name"));
            }
            transitionCombo.getSelectionModel().select(0);
        } else {
            transitionCombo.setDisable(true);
            transitionButton.setDisable(true);
        }

        // --- Details Sidebar Populating ---
        JSONObject assigneeObj = fields.optJSONObject("assignee");
        if (assigneeObj != null) {
            assigneeField.setText(assigneeObj.optString("name", ""));
        } else {
            assigneeField.setText("");
        }

        JSONObject reporterObj = fields.optJSONObject("reporter");
        reporterLabel.setText(reporterObj != null ? reporterObj.optString("displayName", "-") : "-");

        JSONObject creatorObj = fields.optJSONObject("creator");
        creatorLabel.setText(creatorObj != null ? creatorObj.optString("displayName", "-") : "-");

        JSONObject priorityObj = fields.optJSONObject("priority");
        priorityLabel.setText(priorityObj != null ? priorityObj.optString("name", "-") : "-");

        // Components
        JSONArray componentsArray = fields.optJSONArray("components");
        if (componentsArray != null && componentsArray.length() > 0) {
            List<String> compNames = new ArrayList<>();
            for (int i = 0; i < componentsArray.length(); i++) {
                compNames.add(componentsArray.getJSONObject(i).getString("name"));
            }
            componentsLabel.setText(String.join(", ", compNames));
        } else {
            componentsLabel.setText("None");
        }

        // Fix Versions
        JSONArray fixVersionsArray = fields.optJSONArray("fixVersions");
        if (fixVersionsArray != null && fixVersionsArray.length() > 0) {
            List<String> fvNames = new ArrayList<>();
            for (int i = 0; i < fixVersionsArray.length(); i++) {
                fvNames.add(fixVersionsArray.getJSONObject(i).getString("name"));
            }
            fixVersionsLabel.setText(String.join(", ", fvNames));
        } else {
            fixVersionsLabel.setText("None");
        }

        // Affects Versions
        JSONArray versionsArray = fields.optJSONArray("versions");
        if (versionsArray != null && versionsArray.length() > 0) {
            List<String> avNames = new ArrayList<>();
            for (int i = 0; i < versionsArray.length(); i++) {
                avNames.add(versionsArray.getJSONObject(i).getString("name"));
            }
            affectsVersionsLabel.setText(String.join(", ", avNames));
        } else {
            affectsVersionsLabel.setText("None");
        }

        // Labels Tag FlowPane
        labelsFlowPane.getChildren().clear();
        JSONArray labelsArray = fields.optJSONArray("labels");
        if (labelsArray != null && labelsArray.length() > 0) {
            for (int i = 0; i < labelsArray.length(); i++) {
                String labelStr = labelsArray.getString(i);
                Label labelTag = new Label(labelStr);
                labelTag.setStyle("-fx-font-size: 10px; -fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-border-color; -fx-border-radius: 3px; -fx-background-radius: 3px; -fx-padding: 2px 6px;");
                labelsFlowPane.getChildren().add(labelTag);
            }
        } else {
            labelsFlowPane.getChildren().add(new Label("None"));
        }

        // --- Dates ---
        createdLabel.setText(formatDateString(fields.optString("created")));
        updatedLabel.setText(formatDateString(fields.optString("updated")));
        resolvedLabel.setText(formatDateString(fields.optString("resolutiondate")));

        // Force layout pass to ensure newly managed cards (attachments, subtasks, links) are drawn correctly in the ScrollPane
        Platform.runLater(() -> {
            leftPaneContent.requestLayout();
            splitPane.requestLayout();
        });
    }

    private String formatDateString(String jiraDateStr) {
        if (jiraDateStr == null || jiraDateStr.trim().isEmpty() || "null".equalsIgnoreCase(jiraDateStr)) {
            return "Unresolved";
        }
        try {
            // Jira format is usually like: 2026-06-22T10:00:00.000-0400
            // We can simplify it to yyyy-MM-dd HH:mm
            return jiraDateStr.substring(0, 16).replace("T", " ");
        } catch (Exception e) {
            return jiraDateStr;
        }
    }

    private void openAttachment(String fileUrl, String originalFilename) {
        statusText.setText(" Downloading " + originalFilename + "...");
        progressIndicator.setVisible(true);

        ExecutionService.submit(() -> {
            try {
                File tempFile = mainFrame.getService().downloadAttachmentToTempFile(fileUrl, originalFilename);
                Platform.runLater(() -> {
                    try {
                        statusText.setText(" Opening " + originalFilename + "...");
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().open(tempFile);
                        } else {
                            showError("Desktop Not Supported", "The system default editor cannot be opened on this platform.");
                        }
                    } catch (Exception ex) {
                        showError("Attachment Error", "Failed to open download file: " + ex.getMessage());
                    } finally {
                        progressIndicator.setVisible(false);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Download Error", "Failed to download attachment: " + ex.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        });
    }

    private void executeTransition() {
        String transitionName = transitionCombo.getSelectionModel().getSelectedItem();
        if (transitionName == null || currentIssueKey == null) return;

        setLoading(true);
        statusText.setText(" Transitioning " + currentIssueKey + " to " + transitionName + "...");

        ExecutionService.submit(() -> {
            try {
                JiraIssueService issueService = mainFrame.getIssueService();
                issueService.transitionIssue(currentIssueKey, transitionName, null);
                
                // Clear cache key to force reload the transitions next fetch
                mainFrame.getMetadataService().clearCache();

                Platform.runLater(() -> {
                    loadIssue(currentIssueKey);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Transition Error", "Failed to transition issue: " + ex.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void updateAssignee() {
        if (currentIssueKey == null) return;
        String username = assigneeField.getText().trim();
        
        setLoading(true);
        statusText.setText(" Assigning " + currentIssueKey + " to " + (username.isEmpty() ? "Unassigned" : username) + "...");

        ExecutionService.submit(() -> {
            try {
                JiraIssueService issueService = mainFrame.getIssueService();
                issueService.assignIssue(currentIssueKey, username.isEmpty() ? null : username);

                Platform.runLater(() -> {
                    loadIssue(currentIssueKey);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Assignment Error", "Failed to update assignee: " + ex.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void assignToMe() {
        statusText.setText(" Querying current session profile...");
        progressIndicator.setVisible(true);

        ExecutionService.submit(() -> {
            try {
                String myselfUrl = mainFrame.getBaseUrl() + "/rest/api/2/myself";
                String response = mainFrame.getService().executeRequest(myselfUrl, "GET", null);
                JSONObject root = new JSONObject(response);
                String myUsername = root.getString("name");

                Platform.runLater(() -> {
                    assigneeField.setText(myUsername);
                    updateAssignee();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Myself Query Error", "Failed to determine current username from session: " + ex.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        });
    }

    private void postComment() {
        if (currentIssueKey == null) return;
        String comment = commentInputField.getText().trim();
        if (comment.isEmpty()) return;

        commentInputField.setDisable(true);
        addCommentButton.setDisable(true);
        statusText.setText(" Posting comment to " + currentIssueKey + "...");

        ExecutionService.submit(() -> {
            try {
                JiraIssueService issueService = mainFrame.getIssueService();
                issueService.addComment(currentIssueKey, comment);

                Platform.runLater(() -> {
                    commentInputField.clear();
                    commentInputField.setDisable(false);
                    addCommentButton.setDisable(false);
                    loadIssue(currentIssueKey);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showError("Comment Error", "Failed to add comment: " + ex.getMessage());
                    commentInputField.setDisable(false);
                    addCommentButton.setDisable(false);
                });
            }
        });
    }

    private void copyKeyAndSummaryToClipboard() {
        if (currentIssueKey == null || summaryLabel.getText().isEmpty()) return;
        String text = currentIssueKey + ": " + summaryLabel.getText();
        
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        
        statusText.setText(" Copied \"" + text + "\" to clipboard.");
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
