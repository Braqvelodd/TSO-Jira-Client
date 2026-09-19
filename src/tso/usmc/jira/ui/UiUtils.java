package tso.usmc.jira.ui;

import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class UiUtils {
    /**
     * Sets up a double-click listener and context menu on a TextInputControl (TextField or TextArea)
     * to show an expanded multi-line editor in a popup dialog.
     */
    public static void setupExpandedView(TextInputControl field) {
        // Use addEventFilter during capturing phase so TextAreaSkin does not consume the double-click event
        field.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                e.consume();
                openExpandedDialog(field);
            }
        });
        
        // Also provide a right-click context menu option with standard editing operations
        MenuItem expandItem = new MenuItem("Expand Editor (Double-Click)");
        expandItem.setOnAction(e -> openExpandedDialog(field));
        
        ContextMenu cm = field.getContextMenu();
        if (cm == null) {
            MenuItem cutItem = new MenuItem("Cut");
            cutItem.setOnAction(e -> field.cut());
            MenuItem copyItem = new MenuItem("Copy");
            copyItem.setOnAction(e -> field.copy());
            MenuItem pasteItem = new MenuItem("Paste");
            pasteItem.setOnAction(e -> field.paste());
            MenuItem selectAllItem = new MenuItem("Select All");
            selectAllItem.setOnAction(e -> field.selectAll());
            
            cm = new ContextMenu(expandItem, new SeparatorMenuItem(), cutItem, copyItem, pasteItem, selectAllItem);
            field.setContextMenu(cm);
        } else {
            cm.getItems().add(0, expandItem);
            cm.getItems().add(1, new SeparatorMenuItem());
        }
        
        Tooltip existing = field.getTooltip();
        if (existing != null && existing.getText() != null) {
            if (!existing.getText().contains("Double-click to expand")) {
                existing.setText(existing.getText() + "\n(Double-click to expand)");
            }
        } else {
            field.setTooltip(new Tooltip("Double-click to expand"));
        }
    }

    public static void openExpandedDialog(TextInputControl field) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Expanded Input");
        dialog.setHeaderText(null);
        
        if (field.getScene() != null && field.getScene().getStylesheets() != null) {
            dialog.getDialogPane().getStylesheets().addAll(field.getScene().getStylesheets());
        }
        
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
        
        String initialText = field.getText();
        if (initialText != null) {
            initialText = initialText.replace("\\n", "\n").replace("\\r", "\r");
        }
        
        TextArea textArea = new TextArea(initialText != null ? initialText : "");
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(50);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        
        GridPane content = new GridPane();
        content.add(textArea, 0, 0);
        dialog.getDialogPane().setContent(content);
        
        boolean isMultiLine = (field instanceof TextArea);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                String resultText = textArea.getText();
                if (resultText != null && !isMultiLine) {
                    resultText = resultText.replace("\n", "\\n").replace("\r", "\\r");
                }
                return resultText;
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(result -> field.setText(result));
    }
}
