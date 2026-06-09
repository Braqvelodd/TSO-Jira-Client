package tso.usmc.jira.ui;

import tso.usmc.jira.util.JiraConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IspwColumnConfigDialog extends Dialog<Void> {
    private final String sampleText;
    private final JiraConfig config;
    private final List<Integer> splitPoints = new ArrayList<>();
    private final Canvas canvas;
    
    private final TextField typeStart = new TextField();
    private final TextField typeEnd = new TextField();
    private final TextField nameStart = new TextField();
    private final TextField nameEnd = new TextField();
    private final TextField srStart = new TextField();
    private final TextField srEnd = new TextField();
    private final TextField userStart = new TextField();
    private final TextField userEnd = new TextField();
    private final TextField actionStart = new TextField();
    private final TextField actionEnd = new TextField();
    private final TextField minLen = new TextField();

    public IspwColumnConfigDialog(Window owner, String text, JiraConfig config) {
        this.sampleText = text;
        this.config = config;
        
        setTitle("Configure ISPW Columns (Fixed Width)");
        initOwner(owner);

        // Header Instructions
        Label headerLabel = new Label("Instructions: Left-click on the preview to add/remove red split lines. " +
                "Right-click to quickly map fields. Use the numbers to fill the mapping fields below. Values are saved to your JiraConfig.ini.");
        headerLabel.setWrapText(true);
        headerLabel.setPadding(new Insets(10));

        // Preview Area Canvas
        canvas = new Canvas(1500, 500);
        canvas.setStyle("-fx-background-color: white;");
        
        // Mouse click handler
        canvas.setOnMousePressed(e -> {
            double charW = getCharWidth();
            int col = (int) ((e.getX() - 10 + (charW / 2)) / charW);
            if (col < 0) col = 0;

            if (e.getButton() == MouseButton.SECONDARY) {
                showColumnContextMenu(canvas, e.getScreenX(), e.getScreenY(), col);
            } else {
                if (splitPoints.contains(col)) {
                    splitPoints.remove(Integer.valueOf(col));
                } else {
                    splitPoints.add(col);
                    Collections.sort(splitPoints);
                }
                draw();
            }
        });

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setPrefViewportHeight(350);
        scrollPane.setPrefViewportWidth(850);

        // Mapping Panel
        GridPane mappingPanel = new GridPane();
        mappingPanel.getStyleClass().add("card");
        mappingPanel.setHgap(10);
        mappingPanel.setVgap(5);
        mappingPanel.setPadding(new Insets(10));

        addMappingRow(mappingPanel, 0, "CI Type Bounds:", typeStart, typeEnd);
        addMappingRow(mappingPanel, 1, "CI Name Bounds:", nameStart, nameEnd);
        addMappingRow(mappingPanel, 2, "SR Number Bounds:", srStart, srEnd);
        addMappingRow(mappingPanel, 3, "User ID Bounds:", userStart, userEnd);
        addMappingRow(mappingPanel, 4, "Action Bounds:", actionStart, actionEnd);
        
        mappingPanel.add(new Label("Min Line Length:"), 0, 5);
        minLen.setPrefWidth(50);
        mappingPanel.add(minLen, 1, 5);

        // Layout Assembly
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(headerLabel, scrollPane, mappingPanel);

        // Save & Cancel buttons
        ButtonType saveButtonType = new ButtonType("Save & Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);
        getDialogPane().setContent(root);

        // Custom action for Save button
        final Button saveBtn = (Button) getDialogPane().lookupButton(saveButtonType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!saveConfig()) {
                e.consume(); // prevent dialog closure if save failed
            }
        });

        // Initialize and draw
        loadCurrentConfig();
        draw();
    }

    private double getCharWidth() {
        Text textNode = new Text("W");
        textNode.setFont(Font.font("Courier New", 12));
        return textNode.getLayoutBounds().getWidth();
    }

    private double getLineHeight() {
        Text textNode = new Text("W");
        textNode.setFont(Font.font("Courier New", 12));
        return textNode.getLayoutBounds().getHeight();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        // Fill white background
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        gc.setFont(Font.font("Courier New", 12));
        double charW = getCharWidth();
        double lineHeight = getLineHeight();
        
        String[] lines = sampleText.split("\n");
        int displayLines = Math.min(lines.length, 25);
        
        // Draw ruler
        gc.setStroke(Color.LIGHTGRAY);
        gc.setFill(Color.GRAY);
        for (int i = 0; i < 150; i += 10) {
            double x = 10 + (i * charW);
            gc.strokeLine(x, 0, x, 5);
            gc.fillText(String.valueOf(i), x - 5, 15);
        }

        // Draw text
        gc.setFill(Color.BLACK);
        for (int i = 0; i < displayLines; i++) {
            gc.fillText(lines[i], 10, 45 + (i * lineHeight));
        }

        // Draw vertical split lines
        gc.setStroke(Color.RED);
        gc.setFill(Color.RED);
        for (int split : splitPoints) {
            double x = 10 + (split * charW);
            gc.strokeLine(x, 20, x, 45 + (displayLines * lineHeight));
            gc.fillText(String.valueOf(split), x - 5, 35);
        }
    }

    private void showColumnContextMenu(Canvas invoker, double screenX, double screenY, int clickedCol) {
        int start = 0;
        int end = clickedCol;
        
        List<Integer> sorted = new ArrayList<>(splitPoints);
        if (!sorted.contains(0)) sorted.add(0);
        Collections.sort(sorted);
        
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) > clickedCol) {
                start = sorted.get(i - 1);
                end = sorted.get(i);
                break;
            }
            if (i == sorted.size() - 1) {
                start = sorted.get(i);
                end = clickedCol + 5;
            }
        }

        final int finalStart = start;
        final int finalEnd = end;

        ContextMenu menu = new ContextMenu();
        
        MenuItem typeItem = new MenuItem("Set as CI Type (" + start + " to " + end + ")");
        typeItem.setOnAction(e -> {
            typeStart.setText(String.valueOf(finalStart));
            typeEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        });

        MenuItem nameItem = new MenuItem("Set as CI Name (" + start + " to " + end + ")");
        nameItem.setOnAction(e -> {
            nameStart.setText(String.valueOf(finalStart));
            nameEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        });

        MenuItem srItem = new MenuItem("Set as SR Number (" + start + " to " + end + ")");
        srItem.setOnAction(e -> {
            srStart.setText(String.valueOf(finalStart));
            srEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        });

        MenuItem userItem = new MenuItem("Set as User ID (" + start + " to " + end + ")");
        userItem.setOnAction(e -> {
            userStart.setText(String.valueOf(finalStart));
            userEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        });

        MenuItem actionItem = new MenuItem("Set as Action (" + start + " to " + end + ")");
        actionItem.setOnAction(e -> {
            actionStart.setText(String.valueOf(finalStart));
            actionEnd.setText(String.valueOf(finalEnd));
            updateMinLineLength();
        });

        menu.getItems().addAll(typeItem, nameItem, srItem, userItem, actionItem);
        menu.show(invoker, screenX, screenY);
    }

    private void updateMinLineLength() {
        int max = 0;
        try { max = Math.max(max, Integer.parseInt(typeEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(nameEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(srEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(userEnd.getText().trim())); } catch (Exception ignored) {}
        try { max = Math.max(max, Integer.parseInt(actionEnd.getText().trim())); } catch (Exception ignored) {}
        if (max > 0) {
            minLen.setText(String.valueOf(max));
        }
    }

    private void addMappingRow(GridPane p, int row, String label, TextField start, TextField end) {
        p.add(new Label(label), 0, row);
        start.setPrefWidth(50);
        p.add(start, 1, row);
        p.add(new Label("to"), 2, row);
        end.setPrefWidth(50);
        p.add(end, 3, row);
    }

    private void loadCurrentConfig() {
        int[] type = config.getIspwColumnBounds("ci_type", new int[]{0, 4});
        int[] name = config.getIspwColumnBounds("ci_name", new int[]{5, 13});
        int[] sr = config.getIspwColumnBounds("sr", new int[]{30, 40});
        int[] user = config.getIspwColumnBounds("user", new int[]{41, 47});
        int[] action = config.getIspwActionBounds(new int[]{55, 56});
        
        typeStart.setText(String.valueOf(type[0]));
        typeEnd.setText(String.valueOf(type[1]));
        nameStart.setText(String.valueOf(name[0]));
        nameEnd.setText(String.valueOf(name[1]));
        srStart.setText(String.valueOf(sr[0]));
        srEnd.setText(String.valueOf(sr[1]));
        userStart.setText(String.valueOf(user[0]));
        userEnd.setText(String.valueOf(user[1]));
        actionStart.setText(String.valueOf(action[0]));
        actionEnd.setText(String.valueOf(action[1]));
        
        minLen.setText(String.valueOf(config.getIspwMinLineLength(65)));

        addSplitPoint(type[0]);
        addSplitPoint(type[1]);
        addSplitPoint(name[0]);
        addSplitPoint(name[1]);
        addSplitPoint(sr[0]);
        addSplitPoint(sr[1]);
        addSplitPoint(user[0]);
        addSplitPoint(user[1]);
        addSplitPoint(action[0]);
        addSplitPoint(action[1]);
        
        Collections.sort(splitPoints);
    }
    
    private void addSplitPoint(int p) {
        if (!splitPoints.contains(p)) splitPoints.add(p);
    }

    private boolean saveConfig() {
        try {
            Map<String, String> props = new HashMap<>();
            props.put("recon.ispw.ci_type.bounds", typeStart.getText().trim() + "," + typeEnd.getText().trim());
            props.put("recon.ispw.ci_name.bounds", nameStart.getText().trim() + "," + nameEnd.getText().trim());
            props.put("recon.ispw.sr.bounds", srStart.getText().trim() + "," + srEnd.getText().trim());
            props.put("recon.ispw.user.bounds", userStart.getText().trim() + "," + userEnd.getText().trim());
            props.put("recon.ispw.action.bounds", actionStart.getText().trim() + "," + actionEnd.getText().trim());
            props.put("recon.ispw.min_line_length", minLen.getText().trim());
            
            config.saveProperties(props);
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("Settings saved successfully and applied to the Reconciliation panel.");
            alert.showAndWait();
            return true;
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Error saving settings: " + ex.getMessage());
            alert.showAndWait();
            return false;
        }
    }
}
