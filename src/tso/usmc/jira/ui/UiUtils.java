package tso.usmc.jira.ui;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.Optional;

public class UiUtils {
    /**
     * Configures a Dialog (or Alert) so that its owner is properly set to the
     * application window, and it centers directly over the application window
     * on whichever monitor the app is located on.
     */
    public static void configureWindowOwner(Dialog<?> dialog, Window preferredOwner) {
        Window owner = preferredOwner;
        if (owner == null) {
            try {
                if (tso.usmc.jira.app.JiraApiClientGui.getInstance() != null) {
                    owner = tso.usmc.jira.app.JiraApiClientGui.getInstance().getPrimaryStage();
                }
            } catch (Exception ignored) {}
        }
        if (owner == null) {
            try {
                java.util.Iterator<Window> it = Window.impl_getWindows();
                while (it.hasNext()) {
                    Window w = it.next();
                    if (w.isShowing()) {
                        owner = w;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (owner != null) {
            try {
                dialog.initOwner(owner);
            } catch (Exception ignored) {}
            
            final Window targetOwner = owner;
            dialog.setOnShown(e -> {
                try {
                    if (targetOwner.isShowing()) {
                        double x = targetOwner.getX() + (targetOwner.getWidth() - dialog.getWidth()) / 2.0;
                        double y = targetOwner.getY() + (targetOwner.getHeight() - dialog.getHeight()) / 2.0;
                        dialog.setX(x);
                        dialog.setY(y);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    /**
     * Displays an Alert centered directly over the specified owner window on multi-monitor setups.
     */
    public static Optional<ButtonType> showAlert(Window owner, Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        configureWindowOwner(alert, owner);
        return alert.showAndWait();
    }

    /**
     * Displays an Alert centered directly over the window containing the given node.
     */
    public static Optional<ButtonType> showAlert(Node sourceNode, Alert.AlertType type, String title, String content) {
        Window owner = null;
        if (sourceNode != null && sourceNode.getScene() != null) {
            owner = sourceNode.getScene().getWindow();
        }
        return showAlert(owner, type, title, content);
    }

    /**
     * Displays an Error Alert with a clean user-facing message and an expandable
     * debug stack trace (so debug details do not overwhelm the dialog).
     */
    public static Optional<ButtonType> showExceptionAlert(Window owner, String title, String userMessage, Throwable throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title != null ? title : "Error");
        alert.setHeaderText(null);
        alert.setContentText(userMessage != null ? userMessage : (throwable != null ? throwable.getMessage() : "An unexpected error occurred."));
        configureWindowOwner(alert, owner);

        if (throwable != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            String exceptionText = sw.toString();

            Label label = new Label("Exception Details (Debug Stack Trace):");
            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefRowCount(10);
            textArea.setPrefColumnCount(50);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
        }

        return alert.showAndWait();
    }

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
        
        Window owner = (field != null && field.getScene() != null) ? field.getScene().getWindow() : null;
        configureWindowOwner(dialog, owner);
        
        if (field != null && field.getScene() != null && field.getScene().getStylesheets() != null) {
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
