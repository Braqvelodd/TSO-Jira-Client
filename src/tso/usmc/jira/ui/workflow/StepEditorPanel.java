package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.WorkflowStep;
import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.workflow.TransitionStep;
import tso.usmc.jira.workflow.UpdateStep;
import tso.usmc.jira.workflow.AssetStep;
import tso.usmc.jira.workflow.CreateStep;
import tso.usmc.jira.workflow.LinkAction;
import tso.usmc.jira.workflow.LinkStep;
import tso.usmc.jira.workflow.WorklogStep;
import tso.usmc.jira.workflow.AttachmentStep;
import tso.usmc.jira.workflow.CommentStep;
import tso.usmc.jira.workflow.NotifyStep;
import org.json.JSONObject;
import tso.usmc.jira.ui.UiUtils;
import tso.usmc.jira.ui.AutocompleteTextField;
import tso.usmc.jira.service.JqlAutocompleteService;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StepEditorPanel extends BorderPane {
    public interface StepActionListener {
        void onMoveUp(StepEditorPanel panel);
        void onMoveDown(StepEditorPanel panel);
    }

    public interface StepMetadataListener {
        void onFetchTransitionFields(TransitionStep step);
        void onFetchCreateFields(CreateStep step);
    }

    private final WorkflowStep step;
    private final TextField labelField;
    private final VBox fieldsContainer;
    private final VBox contentPanel;
    private final HBox header;
    private final List<FieldActionPanel> actionPanels = new ArrayList<>();
    private final List<LinkActionPanel> linkActionPanels = new ArrayList<>();
    private final Map<String, String> fieldOptions; // Label -> ID mapping
    private final Map<String, JSONObject> fullMetadata;
    private final StepMetadataListener metadataListener;
    private Button collapseBtn;

    private TextField targetIssueField;
    private AutocompleteTextField projField;
    private AutocompleteTextField typeField;
    private TextField parentField;
    private TextField attachmentPathField;
    private CheckBox attachmentPromptCheck;
    private TextArea commentBodyArea;
    private CheckBox commentPromptCheck;
    private CheckBox commentPerIssueCheck;
    private TextField notifySubjectField;
    private TextArea notifyBodyArea;
    private TextField notifyToUsersField;
    private TextField notifyToGroupsField;
    private CheckBox notifyToAssigneeCheck;
    private CheckBox notifyToReporterCheck;
    private CheckBox notifyToWatchersCheck;
    private CheckBox notifyToVotersCheck;
    private CheckBox notifyPromptSubjectCheck;
    private CheckBox notifyPromptBodyCheck;
    private CheckBox notifyPromptUsersCheck;
    private CheckBox notifyPromptGroupsCheck;
    private CheckBox notifyPerIssueCheck;
    private CheckBox worklogPromptCheck;
    private CheckBox worklogPerIssueCheck;
    private TextField inwardField;
    private TextField sourceTokenField;
    private TextField targetTokenField;
    private TextField timeSpentField;
    private TextField commentField;
    private TextField startedField;
    private TextField subTaskFieldsComp;
    private List<String> cachedLinkTypes = new ArrayList<>();

    // Condition UI
    private final TextField condTokenField;
    private final ComboBox<String> condOpCombo;
    private final TextField condValueField;

    private final JqlAutocompleteService autocompleteService;

    public StepEditorPanel(WorkflowStep step, Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata, JqlAutocompleteService autocompleteService, Runnable onRemove, StepActionListener stepListener, StepMetadataListener metadataListener) {
        this.step = step;
        this.fieldOptions = fieldOptions;
        this.fullMetadata = fullMetadata;
        this.autocompleteService = autocompleteService;
        this.metadataListener = metadataListener;
        this.contentPanel = new VBox(5);
        this.header = new HBox(10);

        setStyle("-fx-border-color: gray; -fx-border-width: 1px; -fx-padding: 5px;");
        setMinWidth(Region.USE_PREF_SIZE);

        // Header
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5));
        header.getStyleClass().add("step-header");

        collapseBtn = new Button("▼");
        collapseBtn.getStyleClass().add("list-action-btn");
        collapseBtn.setMinSize(22, 22); collapseBtn.setMaxSize(22, 22);
        collapseBtn.setOnAction(e -> {
            boolean visible = !contentPanel.isVisible();
            contentPanel.setVisible(visible);
            contentPanel.setManaged(visible);
            collapseBtn.setText(visible ? "▼" : "▶");
        });
        header.getChildren().add(collapseBtn);

        Button removeBtn = new Button("X");
        removeBtn.getStyleClass().addAll("list-action-btn", "action-btn-delete");
        removeBtn.setMinSize(22, 22); removeBtn.setMaxSize(22, 22);
        removeBtn.setOnAction(e -> onRemove.run());
        header.getChildren().add(removeBtn);

        labelField = new TextField(step.getLabel());
        labelField.setPrefColumnCount(20);
        UiUtils.setupExpandedView(labelField);
        
        HBox pair = new HBox(5);
        pair.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label(step.getType().toString());
        typeBadge.getStyleClass().addAll("badge", "badge-" + step.getType().toString().toLowerCase());
        Label labelText = new Label(" Label:");
        pair.getChildren().addAll(typeBadge, labelText, labelField);
        header.getChildren().add(pair);
        
        if (step instanceof UpdateStep) {
            targetIssueField = new TextField(((UpdateStep)step).getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
        }

        if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            targetIssueField = new TextField(ts.getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
            
            AutocompleteTextField targetField = new AutocompleteTextField();
            targetField.getTextField().setText(ts.getTargetStatus() != null ? ts.getTargetStatus() : "");
            targetField.getTextField().setPrefColumnCount(15);
            UiUtils.setupExpandedView(targetField.getTextField());
            targetField.setUserAutocompleteService(autocompleteService);
            targetField.setJqlFieldName("status");
            targetField.setAutocompleteEnabled(true);
            targetField.getTextField().textProperty().addListener((obs, oldVal, newVal) -> ts.setTargetStatus(newVal));
            header.getChildren().add(createPair("Target Status:", targetField));
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            projField = new AutocompleteTextField();
            projField.getTextField().setText(cs.getProjectKey() != null ? cs.getProjectKey() : "");
            projField.getTextField().setPrefColumnCount(5);
            UiUtils.setupExpandedView(projField.getTextField());
            projField.setUserAutocompleteService(autocompleteService);
            projField.setJqlFieldName("project");
            projField.setAutocompleteEnabled(true);
            header.getChildren().add(createPair("Project:", projField));
            
            typeField = new AutocompleteTextField();
            typeField.getTextField().setText(cs.getIssueType() != null ? cs.getIssueType() : "");
            typeField.getTextField().setPrefColumnCount(10);
            UiUtils.setupExpandedView(typeField.getTextField());
            typeField.setUserAutocompleteService(autocompleteService);
            typeField.setJqlFieldName("issuetype");
            typeField.setAutocompleteEnabled(true);
            header.getChildren().add(createPair("Type:", typeField));

            parentField = new TextField(cs.getParentIssueKey());
            parentField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(parentField);
            header.getChildren().add(createPair("Parent:", parentField));
        }

        if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            sourceTokenField = new TextField(as.getSourceIssueToken());
            sourceTokenField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(sourceTokenField);
            header.getChildren().add(createPair("From:", sourceTokenField));
            
            targetTokenField = new TextField(as.getTargetIssueToken());
            targetTokenField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetTokenField);
            header.getChildren().add(createPair("To:", targetTokenField));
            
            CheckBox pOpt = new CheckBox("Prompt?");
            pOpt.setSelected(as.isPromptOptions());
            pOpt.setOnAction(e -> as.setPromptOptions(pOpt.isSelected()));
            header.getChildren().add(pOpt);

            CheckBox att = new CheckBox("Attachments");
            att.setSelected(as.isCopyAttachments());
            att.setOnAction(e -> as.setCopyAttachments(att.isSelected()));
            header.getChildren().add(att);

            CheckBox links = new CheckBox("Links");
            links.setSelected(as.isCopyLinks());
            links.setOnAction(e -> as.setCopyLinks(links.isSelected()));
            header.getChildren().add(links);

            CheckBox subtasks = new CheckBox("Sub-tasks");
            subtasks.setSelected(as.isCopySubTasks());
            subtasks.setOnAction(e -> as.setCopySubTasks(subtasks.isSelected()));
            header.getChildren().add(subtasks);

            subTaskFieldsComp = new TextField(as.getSubTaskFields());
            subTaskFieldsComp.setPrefColumnCount(20);
            UiUtils.setupExpandedView(subTaskFieldsComp);
            header.getChildren().add(createPair("Fields to Asset (CSV):", subTaskFieldsComp));
        }

        if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            targetIssueField = new TextField(ws.getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
            
            timeSpentField = new TextField(ws.getTimeSpent());
            timeSpentField.setPrefColumnCount(8);
            UiUtils.setupExpandedView(timeSpentField);
            header.getChildren().add(createPair("Time Spent:", timeSpentField));
            
            commentField = new TextField(ws.getComment());
            commentField.setPrefColumnCount(15);
            UiUtils.setupExpandedView(commentField);
            header.getChildren().add(createPair("Comment:", commentField));
            
            startedField = new TextField(ws.getStarted());
            startedField.setPrefColumnCount(12);
            UiUtils.setupExpandedView(startedField);
            header.getChildren().add(createPair("Started:", startedField));

            worklogPromptCheck = new CheckBox("Prompt?");
            worklogPromptCheck.setSelected(ws.isPromptAtRuntime());
            worklogPromptCheck.setOnAction(e -> {
                ws.setPromptAtRuntime(worklogPromptCheck.isSelected());
            });
            header.getChildren().add(worklogPromptCheck);

            worklogPerIssueCheck = new CheckBox("Per-Issue?");
            worklogPerIssueCheck.setSelected(ws.isPromptPerIssue());
            worklogPerIssueCheck.setOnAction(e -> {
                ws.setPromptPerIssue(worklogPerIssueCheck.isSelected());
            });
            header.getChildren().add(worklogPerIssueCheck);
        }

        if (step instanceof AttachmentStep) {
            AttachmentStep as = (AttachmentStep) step;
            targetIssueField = new TextField(as.getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
            
            attachmentPathField = new TextField(as.getFilePath());
            attachmentPathField.setPrefColumnCount(25);
            UiUtils.setupExpandedView(attachmentPathField);
            header.getChildren().add(createPair("File Path:", attachmentPathField));

            // Drag-and-drop file support in designer
            attachmentPathField.setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
                e.consume();
            });
            attachmentPathField.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean success = false;
                if (db.hasFiles()) {
                    java.util.List<java.io.File> files = db.getFiles();
                    if (!files.isEmpty()) {
                        attachmentPathField.setText(files.get(0).getAbsolutePath());
                        success = true;
                    }
                }
                e.setDropCompleted(success);
                e.consume();
            });
            
            attachmentPromptCheck = new CheckBox("Prompt?");
            attachmentPromptCheck.setSelected(as.isPromptAtRuntime());
            attachmentPromptCheck.setOnAction(e -> {
                as.setPromptAtRuntime(attachmentPromptCheck.isSelected());
            });
            header.getChildren().add(attachmentPromptCheck);
        }

        if (step instanceof CommentStep) {
            CommentStep cs = (CommentStep) step;
            targetIssueField = new TextField(cs.getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
            
            commentPromptCheck = new CheckBox("Prompt?");
            commentPromptCheck.setSelected(cs.isPromptAtRuntime());
            
            commentPerIssueCheck = new CheckBox("Per-Issue?");
            commentPerIssueCheck.setSelected(cs.isPromptPerIssue());
            commentPerIssueCheck.setDisable(!cs.isPromptAtRuntime());
            
            commentPromptCheck.setOnAction(e -> {
                cs.setPromptAtRuntime(commentPromptCheck.isSelected());
                commentPerIssueCheck.setDisable(!commentPromptCheck.isSelected());
            });
            commentPerIssueCheck.setOnAction(e -> {
                cs.setPromptPerIssue(commentPerIssueCheck.isSelected());
            });
            
            header.getChildren().addAll(commentPromptCheck, commentPerIssueCheck);
        }
        
        if (step instanceof NotifyStep) {
            NotifyStep ns = (NotifyStep) step;
            targetIssueField = new TextField(ns.getTargetIssueToken());
            targetIssueField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(targetIssueField);
            header.getChildren().add(createPair("Target Issue:", targetIssueField));
            
            notifyPromptSubjectCheck = new CheckBox("Prompt Subj?");
            notifyPromptSubjectCheck.setSelected(ns.isPromptSubject());
            
            notifyPromptBodyCheck = new CheckBox("Prompt Body?");
            notifyPromptBodyCheck.setSelected(ns.isPromptBody());
            
            notifyPromptUsersCheck = new CheckBox("Prompt Users?");
            notifyPromptUsersCheck.setSelected(ns.isPromptUsers());
            
            notifyPromptGroupsCheck = new CheckBox("Prompt Grps?");
            notifyPromptGroupsCheck.setSelected(ns.isPromptGroups());
            
            boolean anyPrompt = ns.isPromptSubject() || ns.isPromptBody() || ns.isPromptUsers() || ns.isPromptGroups() || ns.isPromptAtRuntime();
            // Fallback for backwards compatibility: if promptAtRuntime is true but all sub-flags are false, check all of them
            if (ns.isPromptAtRuntime() && !ns.isPromptSubject() && !ns.isPromptBody() && !ns.isPromptUsers() && !ns.isPromptGroups()) {
                notifyPromptSubjectCheck.setSelected(true);
                notifyPromptBodyCheck.setSelected(true);
                notifyPromptUsersCheck.setSelected(true);
                notifyPromptGroupsCheck.setSelected(true);
                ns.setPromptSubject(true);
                ns.setPromptBody(true);
                ns.setPromptUsers(true);
                ns.setPromptGroups(true);
                anyPrompt = true;
            }
            
            notifyPerIssueCheck = new CheckBox("Per-Issue?");
            notifyPerIssueCheck.setSelected(ns.isPromptPerIssue());
            notifyPerIssueCheck.setDisable(!anyPrompt);
            
            Runnable updatePromptState = () -> {
                boolean active = notifyPromptSubjectCheck.isSelected() || notifyPromptBodyCheck.isSelected() || 
                                 notifyPromptUsersCheck.isSelected() || notifyPromptGroupsCheck.isSelected();
                ns.setPromptAtRuntime(active);
                notifyPerIssueCheck.setDisable(!active);
            };
            
            notifyPromptSubjectCheck.setOnAction(e -> {
                ns.setPromptSubject(notifyPromptSubjectCheck.isSelected());
                updatePromptState.run();
            });
            notifyPromptBodyCheck.setOnAction(e -> {
                ns.setPromptBody(notifyPromptBodyCheck.isSelected());
                updatePromptState.run();
            });
            notifyPromptUsersCheck.setOnAction(e -> {
                ns.setPromptUsers(notifyPromptUsersCheck.isSelected());
                updatePromptState.run();
            });
            notifyPromptGroupsCheck.setOnAction(e -> {
                ns.setPromptGroups(notifyPromptGroupsCheck.isSelected());
                updatePromptState.run();
            });
            
            notifyPerIssueCheck.setOnAction(e -> {
                ns.setPromptPerIssue(notifyPerIssueCheck.isSelected());
            });
            
            header.getChildren().addAll(
                notifyPromptSubjectCheck, 
                notifyPromptBodyCheck, 
                notifyPromptUsersCheck, 
                notifyPromptGroupsCheck, 
                notifyPerIssueCheck
            );
        }

        setTop(header);

        // Content Wrapper
        contentPanel.setPadding(new Insets(5, 0, 5, 0));

        // --- CONDITION SECTION ---
        condTokenField = new TextField(step.getConditionToken() != null ? step.getConditionToken() : "");
        condTokenField.setPrefColumnCount(15);
        condOpCombo = new ComboBox<>();
        condOpCombo.getItems().addAll("ALWAYS", "EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY");
        condOpCombo.getSelectionModel().select(step.getConditionOperator() != null ? step.getConditionOperator() : "ALWAYS");
        condValueField = new TextField(step.getConditionValue() != null ? step.getConditionValue() : "");
        condValueField.setPrefColumnCount(15);
        
        HBox conditionInnerPanel = new HBox(5);
        conditionInnerPanel.setAlignment(Pos.CENTER_LEFT);
        conditionInnerPanel.getChildren().addAll(
            new Label("If:"), condTokenField, condOpCombo, condValueField, new Label("then execute.")
        );
        
        TitledPane conditionOuterPanel = new TitledPane("Step Execution Condition (Optional)", conditionInnerPanel);
        conditionOuterPanel.setCollapsible(false);
        contentPanel.getChildren().add(conditionOuterPanel);

        // Fields Container
        fieldsContainer = new VBox(5);
        fieldsContainer.setPadding(new Insets(5, 0, 5, 0));
        
        if (step instanceof LinkStep) {
            for (LinkAction la : ((LinkStep) step).getLinkActions()) {
                addLinkAction(la);
            }
        } else {
            for (FieldAction action : step.getFieldActions().values()) {
                addField(action);
            }
        }
        
        if (step instanceof CommentStep) {
            CommentStep cs = (CommentStep) step;
            commentBodyArea = new TextArea(cs.getCommentBody());
            commentBodyArea.setPrefRowCount(4);
            commentBodyArea.setWrapText(true);
            
            VBox commentBodyBox = new VBox(5);
            commentBodyBox.getChildren().addAll(new Label("Default Comment Body (supports tokens):"), commentBodyArea);
            fieldsContainer.getChildren().add(commentBodyBox);
        }
        
        if (step instanceof NotifyStep) {
            NotifyStep ns = (NotifyStep) step;
            
            VBox notifyContainer = new VBox(8);
            notifyContainer.setPadding(new Insets(5));
            
            notifySubjectField = new TextField(ns.getSubject());
            UiUtils.setupExpandedView(notifySubjectField);
            notifyContainer.getChildren().addAll(new Label("Subject (supports tokens):"), notifySubjectField);
            
            notifyBodyArea = new TextArea(ns.getTextBody());
            notifyBodyArea.setPrefRowCount(4);
            notifyBodyArea.setWrapText(true);
            notifyContainer.getChildren().addAll(new Label("Body (supports tokens):"), notifyBodyArea);
            
            notifyToUsersField = new TextField(ns.getToUsers());
            UiUtils.setupExpandedView(notifyToUsersField);
            notifyContainer.getChildren().addAll(new Label("To Users / Teams (comma-separated, e.g. user1, @team.alpha, {{fields.assignee.name}}):"), notifyToUsersField);
            
            notifyToGroupsField = new TextField(ns.getToGroups());
            UiUtils.setupExpandedView(notifyToGroupsField);
            notifyContainer.getChildren().addAll(new Label("To Groups (comma-separated):"), notifyToGroupsField);
            
            HBox standardRecipients = new HBox(15);
            standardRecipients.setAlignment(Pos.CENTER_LEFT);
            notifyToAssigneeCheck = new CheckBox("Assignee");
            notifyToAssigneeCheck.setSelected(ns.isToAssignee());
            notifyToReporterCheck = new CheckBox("Reporter");
            notifyToReporterCheck.setSelected(ns.isToReporter());
            notifyToWatchersCheck = new CheckBox("Watchers");
            notifyToWatchersCheck.setSelected(ns.isToWatchers());
            notifyToVotersCheck = new CheckBox("Voters");
            notifyToVotersCheck.setSelected(ns.isToVoters());
            standardRecipients.getChildren().addAll(new Label("Standard Recipients:"), notifyToAssigneeCheck, notifyToReporterCheck, notifyToWatchersCheck, notifyToVotersCheck);
            notifyContainer.getChildren().add(standardRecipients);
            
            fieldsContainer.getChildren().add(notifyContainer);
        }
        
        contentPanel.getChildren().add(fieldsContainer);

        // Footer (Add Field/Link)
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(5, 0, 5, 0));
        
        if (step instanceof LinkStep) {
            Button addLinkBtn = new Button("+ Add Link");
            addLinkBtn.setOnAction(e -> addLinkAction(new LinkAction()));
            footer.getChildren().add(addLinkBtn);
        } else if (step.getType() != WorkflowStep.StepType.ASSET && step.getType() != WorkflowStep.StepType.WORKLOG && step.getType() != WorkflowStep.StepType.ATTACHMENT && step.getType() != WorkflowStep.StepType.COMMENT && step.getType() != WorkflowStep.StepType.NOTIFY) {
            Button addFieldBtn = new Button("+ Add Field");
            addFieldBtn.setOnAction(e -> addField(new FieldAction("", FieldAction.MappingMode.SET, "", "")));
            footer.getChildren().add(addFieldBtn);
            
            if (step instanceof TransitionStep) {
                Button fetchBtn = new Button("Fetch Transition Fields");
                fetchBtn.setOnAction(e -> {
                    if (metadataListener != null) {
                        saveToStep(); // Save latest status/key from UI
                        metadataListener.onFetchTransitionFields((TransitionStep) step);
                    }
                });
                footer.getChildren().add(fetchBtn);
            }
        }
        contentPanel.getChildren().add(footer);

        setCenter(contentPanel);
    }

    public void setCollapsed(boolean collapsed) {
        boolean visible = !collapsed;
        contentPanel.setVisible(visible);
        contentPanel.setManaged(visible);
        collapseBtn.setText(visible ? "▼" : "▶");
    }

    public boolean isCollapsed() {
        return !contentPanel.isVisible();
    }

    public void addField(FieldAction action) {
        FieldActionPanel panel = new FieldActionPanel(action, fieldOptions, fullMetadata, autocompleteService, new FieldActionPanel.FieldActionListener() {
            @Override
            public void onMoveUp(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx > 0) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx - 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onMoveDown(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx >= 0 && idx < actionPanels.size() - 1) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx + 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onRemove(FieldActionPanel p) {
                actionPanels.remove(p);
                fieldsContainer.getChildren().remove(p);
            }
        });
        actionPanels.add(panel);
        fieldsContainer.getChildren().add(panel);
    }

    public void addLinkAction(LinkAction action) {
        LinkActionPanel panel = new LinkActionPanel(action, cachedLinkTypes, new LinkActionPanel.LinkActionListener() {
            @Override
            public void onMoveUp(LinkActionPanel p) {
                int idx = linkActionPanels.indexOf(p);
                if (idx > 0) {
                    linkActionPanels.remove(idx);
                    linkActionPanels.add(idx - 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onMoveDown(LinkActionPanel p) {
                int idx = linkActionPanels.indexOf(p);
                if (idx >= 0 && idx < linkActionPanels.size() - 1) {
                    linkActionPanels.remove(idx);
                    linkActionPanels.add(idx + 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onRemove(LinkActionPanel p) {
                linkActionPanels.remove(p);
                fieldsContainer.getChildren().remove(p);
            }
        });
        linkActionPanels.add(panel);
        fieldsContainer.getChildren().add(panel);
    }

    private HBox createPair(String labelText, javafx.scene.Node comp) {
        HBox p = new HBox(5);
        p.setAlignment(Pos.CENTER_LEFT);
        if (labelText != null && !labelText.isEmpty()) {
            p.getChildren().add(new Label(labelText));
        }
        p.getChildren().add(comp);
        return p;
    }

    private void refreshActionLayout() {
        fieldsContainer.getChildren().clear();
        if (step instanceof LinkStep) {
            for (LinkActionPanel p : linkActionPanels) fieldsContainer.getChildren().add(p);
        } else {
            for (FieldActionPanel p : actionPanels) fieldsContainer.getChildren().add(p);
        }
    }

    public void saveToStep() {
        step.setLabel(labelField.getText());
        
        // Save Condition
        step.setConditionToken(condTokenField.getText());
        step.setConditionOperator(condOpCombo.getSelectionModel().getSelectedItem());
        step.setConditionValue(condValueField.getText());

        if (step instanceof UpdateStep) {
            ((UpdateStep)step).setTargetIssueToken(targetIssueField.getText());
        }
        if (step instanceof TransitionStep) {
            ((TransitionStep)step).setTargetIssueToken(targetIssueField.getText());
        }
        if (step instanceof CreateStep) {
            ((CreateStep)step).setProjectKey(projField.getText());
            ((CreateStep)step).setIssueType(typeField.getText());
            if (parentField != null) {
                ((CreateStep)step).setParentIssueKey(parentField.getText());
            }
        }
        if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            ls.getLinkActions().clear();
            for (LinkActionPanel lap : linkActionPanels) {
                ls.addLinkAction(lap.getLinkAction());
            }
        }
        if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            as.setSourceIssueToken(sourceTokenField.getText());
            as.setTargetIssueToken(targetTokenField.getText());
            if (subTaskFieldsComp != null) {
                as.setSubTaskFields(subTaskFieldsComp.getText());
            }
        }
        if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            ws.setTargetIssueToken(targetIssueField.getText());
            ws.setTimeSpent(timeSpentField.getText());
            ws.setComment(commentField.getText());
            ws.setStarted(startedField.getText());
            if (worklogPromptCheck != null) ws.setPromptAtRuntime(worklogPromptCheck.isSelected());
            if (worklogPerIssueCheck != null) ws.setPromptPerIssue(worklogPerIssueCheck.isSelected());
        }
        if (step instanceof AttachmentStep) {
            AttachmentStep as = (AttachmentStep) step;
            as.setTargetIssueToken(targetIssueField.getText());
            if (attachmentPathField != null) as.setFilePath(attachmentPathField.getText());
            if (attachmentPromptCheck != null) as.setPromptAtRuntime(attachmentPromptCheck.isSelected());
        }
        if (step instanceof CommentStep) {
            CommentStep cs = (CommentStep) step;
            cs.setTargetIssueToken(targetIssueField.getText());
            if (commentBodyArea != null) cs.setCommentBody(commentBodyArea.getText());
            if (commentPromptCheck != null) cs.setPromptAtRuntime(commentPromptCheck.isSelected());
            if (commentPerIssueCheck != null) cs.setPromptPerIssue(commentPerIssueCheck.isSelected());
        }
        if (step instanceof NotifyStep) {
            NotifyStep ns = (NotifyStep) step;
            ns.setTargetIssueToken(targetIssueField.getText());
            if (notifySubjectField != null) ns.setSubject(notifySubjectField.getText());
            if (notifyBodyArea != null) ns.setTextBody(notifyBodyArea.getText());
            if (notifyToUsersField != null) ns.setToUsers(notifyToUsersField.getText());
            if (notifyToGroupsField != null) ns.setToGroups(notifyToGroupsField.getText());
            if (notifyToAssigneeCheck != null) ns.setToAssignee(notifyToAssigneeCheck.isSelected());
            if (notifyToReporterCheck != null) ns.setToReporter(notifyToReporterCheck.isSelected());
            if (notifyToWatchersCheck != null) ns.setToWatchers(notifyToWatchersCheck.isSelected());
            if (notifyToVotersCheck != null) ns.setToVoters(notifyToVotersCheck.isSelected());
            if (notifyPromptSubjectCheck != null) ns.setPromptSubject(notifyPromptSubjectCheck.isSelected());
            if (notifyPromptBodyCheck != null) ns.setPromptBody(notifyPromptBodyCheck.isSelected());
            if (notifyPromptUsersCheck != null) ns.setPromptUsers(notifyPromptUsersCheck.isSelected());
            if (notifyPromptGroupsCheck != null) ns.setPromptGroups(notifyPromptGroupsCheck.isSelected());
            if (notifyPerIssueCheck != null) ns.setPromptPerIssue(notifyPerIssueCheck.isSelected());
            boolean active = (notifyPromptSubjectCheck != null && notifyPromptSubjectCheck.isSelected()) ||
                             (notifyPromptBodyCheck != null && notifyPromptBodyCheck.isSelected()) ||
                             (notifyPromptUsersCheck != null && notifyPromptUsersCheck.isSelected()) ||
                             (notifyPromptGroupsCheck != null && notifyPromptGroupsCheck.isSelected());
            ns.setPromptAtRuntime(active);
        }
        
        step.getFieldActions().clear();
        for (FieldActionPanel panel : actionPanels) {
            step.addFieldAction(panel.getFieldAction());
        }
    }

    public WorkflowStep getStep() { return step; }

    public HBox getHeader() { return header; }

    public void refreshMetadata(Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata) {
        for (FieldActionPanel panel : actionPanels) {
            panel.refreshMetadata(fieldOptions, fullMetadata);
        }
    }

    public void updateLinkTypes(List<String> linkTypes) {
        this.cachedLinkTypes = linkTypes;
        for (LinkActionPanel panel : linkActionPanels) {
            panel.updateLinkTypes(linkTypes);
        }
    }
}
