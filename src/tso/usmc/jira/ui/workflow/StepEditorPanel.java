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
import org.json.JSONObject;
import tso.usmc.jira.ui.UiUtils;

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

    private TextField targetIssueField;
    private TextField projField;
    private TextField typeField;
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

    public StepEditorPanel(WorkflowStep step, Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata, Runnable onRemove, StepActionListener stepListener, StepMetadataListener metadataListener) {
        this.step = step;
        this.fieldOptions = fieldOptions;
        this.fullMetadata = fullMetadata;
        this.metadataListener = metadataListener;
        this.contentPanel = new VBox(5);
        this.header = new HBox(10);

        setStyle("-fx-border-color: gray; -fx-border-width: 1px; -fx-padding: 5px;");
        setMinWidth(Region.USE_PREF_SIZE);

        // Header
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5));
        header.getStyleClass().add("step-header");

        Button collapseBtn = new Button("▼");
        collapseBtn.getStyleClass().add("list-action-btn");
        collapseBtn.setMinSize(22, 22); collapseBtn.setMaxSize(22, 22);
        collapseBtn.setOnAction(e -> {
            boolean visible = !contentPanel.isVisible();
            contentPanel.setVisible(visible);
            contentPanel.setManaged(visible);
            collapseBtn.setText(visible ? "▼" : "▶");
        });
        header.getChildren().add(collapseBtn);

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
            
            TextField targetField = new TextField(ts.getTargetStatus());
            targetField.setPrefColumnCount(15);
            UiUtils.setupExpandedView(targetField);
            targetField.textProperty().addListener((obs, oldVal, newVal) -> ts.setTargetStatus(newVal));
            header.getChildren().add(createPair("Target Status:", targetField));
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            projField = new TextField(cs.getProjectKey());
            projField.setPrefColumnCount(5);
            UiUtils.setupExpandedView(projField);
            header.getChildren().add(createPair("Project:", projField));
            
            typeField = new TextField(cs.getIssueType());
            typeField.setPrefColumnCount(10);
            UiUtils.setupExpandedView(typeField);
            header.getChildren().add(createPair("Type:", typeField));
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
        }

        // Step Rearrangement Buttons
        HBox movePanel = new HBox(2);
        movePanel.setAlignment(Pos.CENTER_LEFT);
        Button stepUpBtn = new Button("^");
        Button stepDownBtn = new Button("v");
        stepUpBtn.setMinSize(22, 22); stepUpBtn.setMaxSize(22, 22);
        stepDownBtn.setMinSize(22, 22); stepDownBtn.setMaxSize(22, 22);
        stepUpBtn.getStyleClass().addAll("list-action-btn", "action-btn-up");
        stepDownBtn.getStyleClass().addAll("list-action-btn", "action-btn-down");
        
        stepUpBtn.setOnAction(e -> stepListener.onMoveUp(this));
        stepDownBtn.setOnAction(e -> stepListener.onMoveDown(this));
        movePanel.getChildren().addAll(stepUpBtn, stepDownBtn);
        header.getChildren().add(movePanel);

        Button removeBtn = new Button("X");
        removeBtn.getStyleClass().addAll("list-action-btn", "action-btn-delete");
        removeBtn.setMinSize(22, 22); removeBtn.setMaxSize(22, 22);
        removeBtn.setOnAction(e -> onRemove.run());
        header.getChildren().add(removeBtn);
        
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
        contentPanel.getChildren().add(fieldsContainer);

        // Footer (Add Field/Link)
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(5, 0, 5, 0));
        
        if (step instanceof LinkStep) {
            Button addLinkBtn = new Button("+ Add Link");
            addLinkBtn.setOnAction(e -> addLinkAction(new LinkAction()));
            footer.getChildren().add(addLinkBtn);
        } else if (step.getType() != WorkflowStep.StepType.ASSET && step.getType() != WorkflowStep.StepType.WORKLOG) {
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

    public void addField(FieldAction action) {
        FieldActionPanel panel = new FieldActionPanel(action, fieldOptions, fullMetadata, new FieldActionPanel.FieldActionListener() {
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
