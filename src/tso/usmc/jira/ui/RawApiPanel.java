package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JsonUtils;
import tso.usmc.jira.util.ExecutionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;

public class RawApiPanel extends GridPane {

    private final JiraApiClientGui mainFrame;
    private final TextField endpointField = new TextField("/rest/api/2/issue/TSO-123");
    private final TextArea requestArea = new TextArea();
    private final TextArea responseArea = new TextArea();
    private final ComboBox<ApiTemplate> templateCombo = new ComboBox<>();
    
    private final Map<String, Button> actionButtons = new HashMap<>();

    private static class ApiTemplate {
        String label, method, endpoint, body;
        ApiTemplate(String label, String method, String endpoint, String body) {
            this.label = label; this.method = method; this.endpoint = endpoint; this.body = body;
        }
        @Override public String toString() { return label; }
    }

    public RawApiPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));
        setHgap(10);
        setVgap(10);

        // Column Constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(80);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(col1, col2);

        // Row Constraints (to distribute height)
        RowConstraints rTemplate = new RowConstraints();
        RowConstraints rEndpoint = new RowConstraints();
        RowConstraints rReqLabel = new RowConstraints();
        RowConstraints rReqArea = new RowConstraints();
        rReqArea.setVgrow(Priority.ALWAYS);
        RowConstraints rButtons = new RowConstraints();
        RowConstraints rResLabel = new RowConstraints();
        RowConstraints rResArea = new RowConstraints();
        rResArea.setVgrow(Priority.ALWAYS);
        getRowConstraints().addAll(rTemplate, rEndpoint, rReqLabel, rReqArea, rButtons, rResLabel, rResArea);

        // 0. Template Row
        add(new Label("Template:"), 0, 0);
        templateCombo.setMaxWidth(Double.MAX_VALUE);
        loadTemplates();
        add(templateCombo, 1, 0);
        templateCombo.setOnAction(e -> applyTemplate());

        // 1. Endpoint Row
        add(new Label("Endpoint:"), 0, 1);
        add(endpointField, 1, 1);

        // 2. Request JSON Area (Label)
        Label reqLabel = new Label("Request Body (JSON for POST/PUT):");
        add(reqLabel, 0, 2, 2, 1);

        // 3. Request JSON ScrollPane
        requestArea.setStyle("-fx-font-family: monospace;");
        add(requestArea, 0, 3, 2, 1);

        // 4. Button Row
        HBox btnPanel = new HBox(10);
        btnPanel.setPadding(new Insets(5, 0, 5, 0));
        
        Button getBtn = new Button("Execute GET");
        Button postBtn = new Button("Execute POST");
        Button putBtn = new Button("Execute PUT");
        Button delBtn = new Button("Execute DELETE");
        
        getBtn.setOnAction(e -> callApi("GET"));
        postBtn.setOnAction(e -> callApi("POST"));
        putBtn.setOnAction(e -> callApi("PUT"));
        delBtn.setOnAction(e -> callApi("DELETE"));
        
        actionButtons.put("GET", getBtn);
        actionButtons.put("POST", postBtn);
        actionButtons.put("PUT", putBtn);
        actionButtons.put("DELETE", delBtn);

        btnPanel.getChildren().addAll(getBtn, putBtn, postBtn, delBtn);
        add(btnPanel, 0, 4, 2, 1);

        // 5. Response Area (Label)
        add(new Label("Response:"), 0, 5, 2, 1);

        // 6. Response ScrollPane
        responseArea.setEditable(false);
        responseArea.setStyle("-fx-font-family: monospace;");
        add(responseArea, 0, 6, 2, 1);
    }

    private void loadTemplates() {
        templateCombo.getItems().add(new ApiTemplate("--- Select Template ---", "", "", ""));
        String[] keys = mainFrame.getJiraConfig().getRawApiTemplateKeys();
        for (String key : keys) {
            String val = mainFrame.getJiraConfig().getRawApiTemplate(key);
            if (val != null) {
                String[] parts = val.split("\\|", -1);
                if (parts.length >= 4) {
                    String body = parts[3].replace("\\n", "\n");
                    templateCombo.getItems().add(new ApiTemplate(parts[0], parts[1], parts[2], body));
                }
            }
        }
        templateCombo.getSelectionModel().select(0);
    }

    private void applyTemplate() {
        ApiTemplate t = templateCombo.getSelectionModel().getSelectedItem();
        
        if (t != null && !t.method.isEmpty()) {
            endpointField.setText(t.endpoint);
            String body = t.body;
            if (body != null && !body.trim().isEmpty()) {
                requestArea.setText(JsonUtils.prettyPrintJson(body));
            } else {
                requestArea.setText("");
            }
            
            // Filter buttons: Show only the matching method
            for (String method : actionButtons.keySet()) {
                actionButtons.get(method).setVisible(method.equalsIgnoreCase(t.method));
            }
        } else {
            // Reset to default endpoint instead of empty
            endpointField.setText("/rest/api/2/issue/TSO-123");
            requestArea.setText("");
            
            // Reset: Show all buttons
            for (Button btn : actionButtons.values()) {
                btn.setVisible(true);
            }
        }
    }

    private void callApi(String method) {
        String fullUrl = mainFrame.getBaseUrl() + endpointField.getText().trim();
        String body = ("POST".equals(method) || "PUT".equals(method)) ? requestArea.getText() : null;
        
        responseArea.setStyle("-fx-font-family: monospace; -fx-text-fill: -fx-text-base-color;");
        responseArea.setText("Sending " + method + " request to: " + fullUrl + "...");
        
        ExecutionService.submit(() -> {
            try {
                String rawResponse = mainFrame.getService().executeRequest(fullUrl, method, body);
                final String formatted = (rawResponse == null || rawResponse.trim().isEmpty())
                    ? "Request successful (204 No Content)"
                    : JsonUtils.prettyPrintJson(rawResponse);
                
                Platform.runLater(() -> responseArea.setText(formatted));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    responseArea.setStyle("-fx-font-family: monospace; -fx-text-fill: red;");
                    responseArea.setText("ERROR: " + ex.getMessage() + "\n\nStack Trace:\n" + getStackTrace(ex));
                });
            }
        });
    }

    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
