package tso.usmc.jira.ui;

import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class UiUtils {
    /**
     * Sets up a double-click listener on a TextField to show an expanded
     * multi-line editor in a popup dialog.
     */
    public static void setupExpandedView(TextField field) {
        field.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                Dialog<String> dialog = new Dialog<>();
                dialog.setTitle("Expanded Input");
                dialog.setHeaderText(null);
                
                ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
                
                TextArea textArea = new TextArea(field.getText());
                textArea.setWrapText(true);
                textArea.setPrefRowCount(15);
                textArea.setPrefColumnCount(50);
                GridPane.setHgrow(textArea, Priority.ALWAYS);
                GridPane.setVgrow(textArea, Priority.ALWAYS);
                
                GridPane content = new GridPane();
                content.add(textArea, 0, 0);
                dialog.getDialogPane().setContent(content);
                
                dialog.setResultConverter(dialogButton -> {
                    if (dialogButton == okButtonType) {
                        return textArea.getText();
                    }
                    return null;
                });
                
                dialog.showAndWait().ifPresent(result -> field.setText(result));
            }
        });
        field.setTooltip(new Tooltip("Double-click to expand"));
    }
}
