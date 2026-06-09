package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.LinkAction;
import tso.usmc.jira.ui.UiUtils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public class LinkActionPanel extends HBox {
    public interface LinkActionListener {
        void onMoveUp(LinkActionPanel panel);
        void onMoveDown(LinkActionPanel panel);
        void onRemove(LinkActionPanel panel);
    }

    private final LinkAction action;
    private final CheckBox remoteToggle;
    private final StackPane modePanel;
    private final TextField inwardField;

    // Jira Link fields
    private final ComboBox<String> linkTypeCombo;
    private final TextField outwardField;

    // Remote Link fields
    private final TextField remoteUrlField;
    private final TextField remoteTitleField;
    private final TextField remoteRelField;
    private final TextField remoteSummaryField;

    public LinkActionPanel(LinkAction action, List<String> linkTypes, LinkActionListener listener) {
        this.action = action;
        
        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 5, 2, 5));
        setMinWidth(Region.USE_PREF_SIZE);
        setStyle("-fx-border-color: lightgray; -fx-border-width: 0 0 1px 0;");

        // Controls
        HBox controls = new HBox(2);
        controls.setAlignment(Pos.CENTER_LEFT);
        Button upBtn = new Button("^");
        Button downBtn = new Button("v");
        Button delBtn = new Button("X");
        
        upBtn.setMinSize(22, 22); upBtn.setMaxSize(22, 22);
        downBtn.setMinSize(22, 22); downBtn.setMaxSize(22, 22);
        delBtn.setMinSize(22, 22); delBtn.setMaxSize(22, 22);
        
        upBtn.getStyleClass().addAll("list-action-btn", "action-btn-up");
        downBtn.getStyleClass().addAll("list-action-btn", "action-btn-down");
        delBtn.getStyleClass().addAll("list-action-btn", "action-btn-delete");
        upBtn.setOnAction(e -> listener.onMoveUp(this));
        downBtn.setOnAction(e -> listener.onMoveDown(this));
        delBtn.setOnAction(e -> listener.onRemove(this));
        
        controls.getChildren().addAll(upBtn, downBtn, delBtn);
        getChildren().add(controls);

        remoteToggle = new CheckBox("Remote?");
        remoteToggle.setSelected(action.isRemote());
        getChildren().add(remoteToggle);

        inwardField = new TextField(action.getInwardIssueToken());
        inwardField.setPrefWidth(100);
        getChildren().addAll(new Label("Inward:"), inwardField);

        modePanel = new StackPane();

        // Jira Panel
        HBox jiraPanel = new HBox(5);
        jiraPanel.setAlignment(Pos.CENTER_LEFT);
        
        linkTypeCombo = new ComboBox<>();
        linkTypeCombo.getItems().addAll(linkTypes);
        linkTypeCombo.setEditable(true);
        linkTypeCombo.setPrefWidth(150);
        if (action.getLinkType() != null) {
            linkTypeCombo.getSelectionModel().select(action.getLinkType());
        }

        outwardField = new TextField(action.getOutwardIssueToken());
        outwardField.setPrefWidth(100);
        jiraPanel.getChildren().addAll(new Label("Type:"), linkTypeCombo, new Label("Outward:"), outwardField);
        
        // Remote Panel
        HBox remotePanel = new HBox(5);
        remotePanel.setAlignment(Pos.CENTER_LEFT);
        remoteUrlField = new TextField(action.getUrl());
        remoteUrlField.setPrefWidth(150);
        remoteTitleField = new TextField(action.getTitle());
        remoteTitleField.setPrefWidth(100);
        remoteRelField = new TextField(action.getRelationship());
        remoteRelField.setPrefWidth(80);
        remoteSummaryField = new TextField(action.getSummary());
        remoteSummaryField.setPrefWidth(100);
        
        remotePanel.getChildren().addAll(
            new Label("URL:"), remoteUrlField,
            new Label("Title:"), remoteTitleField,
            new Label("Rel:"), remoteRelField,
            new Label("Summ:"), remoteSummaryField
        );

        modePanel.getChildren().addAll(jiraPanel, remotePanel);
        getChildren().add(modePanel);

        remoteToggle.setOnAction(e -> updateModeUI(jiraPanel, remotePanel));
        updateModeUI(jiraPanel, remotePanel);

        UiUtils.setupExpandedView(inwardField);
        UiUtils.setupExpandedView(outwardField);
        UiUtils.setupExpandedView(remoteUrlField);
        UiUtils.setupExpandedView(remoteTitleField);
        UiUtils.setupExpandedView(remoteRelField);
        UiUtils.setupExpandedView(remoteSummaryField);
    }

    private void updateModeUI(HBox jiraPanel, HBox remotePanel) {
        boolean remote = remoteToggle.isSelected();
        jiraPanel.setVisible(!remote);
        jiraPanel.setManaged(!remote);
        remotePanel.setVisible(remote);
        remotePanel.setManaged(remote);
    }

    public void saveToLinkAction() {
        action.setRemote(remoteToggle.isSelected());
        action.setInwardIssueToken(inwardField.getText());
        if (action.isRemote()) {
            action.setUrl(remoteUrlField.getText());
            action.setTitle(remoteTitleField.getText());
            action.setRelationship(remoteRelField.getText());
            action.setSummary(remoteSummaryField.getText());
        } else {
            String selected = linkTypeCombo.getSelectionModel().getSelectedItem();
            if (selected == null || selected.trim().isEmpty()) {
                selected = linkTypeCombo.getEditor().getText();
            }
            action.setLinkType(selected != null ? selected.trim() : "");
            action.setOutwardIssueToken(outwardField.getText());
        }
    }

    public LinkAction getLinkAction() {
        saveToLinkAction();
        return action;
    }

    public void updateLinkTypes(List<String> linkTypes) {
        String current = linkTypeCombo.getSelectionModel().getSelectedItem();
        if (current == null || current.trim().isEmpty()) {
            current = linkTypeCombo.getEditor().getText();
        }
        linkTypeCombo.getItems().clear();
        linkTypeCombo.getItems().add("");
        linkTypeCombo.getItems().addAll(linkTypes);
        if (current != null) {
            linkTypeCombo.getSelectionModel().select(current);
        }
    }
}
