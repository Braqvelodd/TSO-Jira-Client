package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JqlAutocompleteService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.JsonUtils;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Bounds;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public class TaskBuilderPanel extends BorderPane {

    private static final boolean MOCK_MODE = false;

    private final JiraApiClientGui mainFrame;
    private JqlAutocompleteService jqlAutocompleteService;

    private boolean isUpdating = false;

    // Configurable Keyboard Shortcuts
    private KeyCombination keyDuplicateDown;
    private KeyCombination keyDuplicateUp;
    private KeyCombination keyMoveDown;
    private KeyCombination keyMoveUp;
    private KeyCombination keyComment;
    private KeyCombination keyDelete;
    private KeyCombination keyBold;
    private KeyCombination keyItalic;

    // UI Components
    private final TextField parentField = new TextField();
    private final ComboBox<String> defTypeField = new ComboBox<>();
    private final JiraUserAutocompleteTextField defAssigneeField = new JiraUserAutocompleteTextField(20);
    private final TextField defCompField = new TextField();
    private final TextField defTransField = new TextField();
    private final ComboBox<String> templateSelector = new ComboBox<>();
    private final JiraUserAutocompleteTextArea inputArea = new JiraUserAutocompleteTextArea(null);
    private final Pane overlayPane = new Pane();
    private final ObservableList<JiraTask> taskListModel = FXCollections.observableArrayList();
    private final ListView<JiraTask> taskList = new ListView<>(taskListModel);
    private final List<JiraTask> parsedTasks = new ArrayList<>();

    private final TableView<ExecutionResultRow> resultsTable = new TableView<>();
    private final Label statusBar = new Label(" Ready");

    private final Button selectAllBtn = new Button("Select All");
    private final Button unselectAllBtn = new Button("Unselect All");

    public static class ExecutionResultRow {
        public final SimpleStringProperty summary;
        public final SimpleStringProperty status;
        public final SimpleStringProperty link;

        public ExecutionResultRow(String summary, String status, String link) {
            this.summary = new SimpleStringProperty(summary);
            this.status = new SimpleStringProperty(status);
            this.link = new SimpleStringProperty(link);
        }
    }

    public TaskBuilderPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));

        // --- Defaults Panel (GridPane) ---
        GridPane configPanel = new GridPane();
        configPanel.getStyleClass().add("card");
        configPanel.setPadding(new Insets(10));
        configPanel.setHgap(10);
        configPanel.setVgap(5);

        // Row 0: Parent and Template
        configPanel.add(new Label("Parent:"), 0, 0);
        configPanel.add(parentField, 1, 0);
        configPanel.add(new Label(" Template:"), 2, 0);
        configPanel.add(templateSelector, 3, 0);
        GridPane.setHgrow(parentField, Priority.ALWAYS);
        GridPane.setHgrow(templateSelector, Priority.ALWAYS);

        // Row 1: Type
        configPanel.add(new Label("Type:"), 0, 1);
        defTypeField.getItems().addAll(mainFrame.getJiraConfig().getSubtaskTypes());
        if (!defTypeField.getItems().isEmpty()) {
            defTypeField.getSelectionModel().select(0);
        }
        defTypeField.setMaxWidth(Double.MAX_VALUE);
        configPanel.add(defTypeField, 1, 1, 3, 1);

        // Row 2: Assignee
        configPanel.add(new Label("Assignee:"), 0, 2);
        configPanel.add(defAssigneeField, 1, 2, 3, 1);

        // Row 3: Component
        configPanel.add(new Label("Component:"), 0, 3);
        configPanel.add(defCompField, 1, 3, 3, 1);

        // Row 4: Transition
        configPanel.add(new Label("Transition:"), 0, 4);
        configPanel.add(defTransField, 1, 4, 3, 1);

        // Sync inputs to textarea default prefixes
        addSyncListener(parentField, "PARENT_TICKET");
        defTypeField.setOnAction(e -> syncToText("DEFAULT_TYPE", defTypeField.getSelectionModel().getSelectedItem()));
        addSyncListener(defAssigneeField, "DEFAULT_ASSIGNEE");
        addSyncListener(defCompField, "DEFAULT_COMPONENT");
        addSyncListener(defTransField, "DEFAULT_TRANSITION");

        loadTemplatesFromDisk();
        templateSelector.setOnAction(e -> loadSelectedTemplate());

        // Autocomplete init
        inputArea.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) ensureAutocompleteServiceInitialized();
        });

        inputArea.setStyle("-fx-font-family: monospace;");
        setupDragAndDrop();

        // Listen for textarea text changes to parse tasks dynamically
        inputArea.textProperty().addListener((obs, oldV, newV) -> parseInput());

        // Dynamic visual highlighting listeners
        inputArea.textProperty().addListener((obs, oldV, newV) -> updateHighlights());
        inputArea.widthProperty().addListener((obs, oldV, newV) -> updateHighlights());
        inputArea.heightProperty().addListener((obs, oldV, newV) -> updateHighlights());

        inputArea.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> {
                    ScrollBar vScrollBar = (ScrollBar) inputArea.lookup(".scroll-bar:vertical");
                    ScrollBar hScrollBar = (ScrollBar) inputArea.lookup(".scroll-bar:horizontal");
                    if (vScrollBar != null) {
                        vScrollBar.valueProperty().addListener((o, ov, nv) -> updateHighlights());
                    }
                    if (hScrollBar != null) {
                        hScrollBar.valueProperty().addListener((o, ov, nv) -> updateHighlights());
                    }
                    updateHighlights();
                });
            }
        });

        setupInputAreaKeyBindings();
        setupContextMenu();

        // --- SPLIT PANE ---
        SplitPane splitPane = new SplitPane();
        BorderPane leftPanel = new BorderPane();
        leftPanel.setTop(configPanel);
        StackPane inputContainer = new StackPane();
        overlayPane.setMouseTransparent(true);
        inputContainer.getChildren().addAll(inputArea, overlayPane);
        leftPanel.setCenter(inputContainer);
        BorderPane.setMargin(inputContainer, new Insets(10, 0, 0, 0));

        BorderPane rightPanel = new BorderPane();
        taskList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Custom cell renderer in ListView to display HTML-like content and toggle selection on mouse press
        taskList.setCellFactory(lv -> {
            ListCell<JiraTask> cell = new ListCell<JiraTask>() {
                @Override
                protected void updateItem(JiraTask task, boolean empty) {
                    super.updateItem(task, empty);
                    if (empty || task == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        HBox hbox = new HBox(5);
                        Label summaryLabel = new Label(task.summary);
                        hbox.getChildren().add(summaryLabel);
                        if (!task.transition.isEmpty()) {
                            Label transLabel = new Label(task.transition);
                            transLabel.getStyleClass().addAll("badge", "badge-transition");
                            hbox.getChildren().add(transLabel);
                        }
                        setGraphic(hbox);
                        setText(null);
                    }
                }
            };
            
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!cell.isEmpty() && cell.getItem() != null) {
                    if (event.getClickCount() == 2) {
                        JiraTask task = cell.getItem();
                        if (task != null) {
                            inputArea.positionCaret(task.startIndex);
                            inputArea.requestFocus();
                        }
                        event.consume();
                        return;
                    }
                    
                    if (event.isShiftDown() || event.isControlDown()) {
                        return;
                    }
                    
                    int index = cell.getIndex();
                    MultipleSelectionModel<JiraTask> selModel = taskList.getSelectionModel();
                    if (selModel.isSelected(index)) {
                        selModel.clearSelection(index);
                    } else {
                        selModel.select(index);
                    }
                    taskList.requestFocus();
                }
                event.consume();
            });

            cell.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                if (!cell.isEmpty() && cell.getItem() != null) {
                    if (event.isShiftDown() || event.isControlDown()) {
                        return;
                    }
                    event.consume();
                }
            });

            cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (!cell.isEmpty() && cell.getItem() != null) {
                    if (event.isShiftDown() || event.isControlDown()) {
                        return;
                    }
                    event.consume();
                }
            });
            
            return cell;
        });

        taskList.getSelectionModel().getSelectedItems().addListener((ListChangeListener<JiraTask>) c -> {
            updateHighlights();
        });

        rightPanel.setCenter(taskList);

        HBox actionButtonsPanel = new HBox(5);
        actionButtonsPanel.setPadding(new Insets(10, 0, 0, 0));
        Button executeBtn = new Button(MOCK_MODE ? "Run Mock Execution" : "Execute Selected Tasks");
        actionButtonsPanel.getChildren().addAll(selectAllBtn, unselectAllBtn, executeBtn);
        HBox.setHgrow(executeBtn, Priority.ALWAYS);
        executeBtn.setMaxWidth(Double.MAX_VALUE);
        rightPanel.setBottom(actionButtonsPanel);

        splitPane.getItems().addAll(leftPanel, rightPanel);
        setCenter(splitPane);

        // --- BOTTOM PANEL: Results Table & Status ---
        VBox bottomContainer = new VBox(5);
        bottomContainer.getStyleClass().add("card");
        bottomContainer.setPadding(new Insets(10));
        
        Label bottomTitle = new Label("Execution Results");
        bottomTitle.getStyleClass().add("card-title");
        
        resultsTable.setPrefHeight(150);
        TableColumn<ExecutionResultRow, String> colSummary = new TableColumn<>("Summary");
        colSummary.setCellValueFactory(cellData -> cellData.getValue().summary);
        colSummary.setPrefWidth(300);

        TableColumn<ExecutionResultRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cellData -> cellData.getValue().status);
        colStatus.setPrefWidth(200);

        TableColumn<ExecutionResultRow, String> colLink = new TableColumn<>("Jira Link");
        colLink.setCellValueFactory(cellData -> cellData.getValue().link);
        colLink.setPrefWidth(350);
        colLink.setCellFactory(col -> new TableCell<ExecutionResultRow, String>() {
            private final Hyperlink hyperlink = new Hyperlink();
            {
                hyperlink.setOnAction(e -> {
                    String url = hyperlink.getText();
                    if (url != null && url.startsWith("http")) {
                        try {
                            Desktop.getDesktop().browse(new java.net.URI(url));
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || !item.startsWith("http")) {
                    setGraphic(null);
                    setText(item);
                } else {
                    hyperlink.setText(item);
                    setGraphic(hyperlink);
                    setText(null);
                }
            }
        });

        resultsTable.getColumns().addAll(colSummary, colStatus, colLink);

        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusBar.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusBar);

        bottomContainer.getChildren().addAll(bottomTitle, resultsTable);
        
        BorderPane footer = new BorderPane();
        footer.setCenter(bottomContainer);
        footer.setBottom(statusPanel);
        BorderPane.setMargin(bottomContainer, new Insets(10, 0, 0, 0));
        setBottom(footer);

        selectAllBtn.setOnAction(e -> setAllTasksSelected(true));
        unselectAllBtn.setOnAction(e -> setAllTasksSelected(false));
        executeBtn.setOnAction(e -> executeTasks());
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem addAssignee = new MenuItem("Set Assignee...");
        addAssignee.setOnAction(e -> applyTaskOverride("assignee:"));
        menu.getItems().add(addAssignee);

        MenuItem addParent = new MenuItem("Set Parent...");
        addParent.setOnAction(e -> applyTaskOverride("parent:"));
        menu.getItems().add(addParent);
        
        Menu componentMenu = new Menu("Set Component");
        String[] teamKeys = mainFrame.getJiraConfig().getWorkflowTeamKeys();
        for (String key : teamKeys) {
            String details = mainFrame.getJiraConfig().getTeamDetails(key);
            if (details != null && details.contains("|")) {
                String label = details.split("\\|")[0];
                String compName = label;
                
                CheckMenuItem compItem = new CheckMenuItem(label);
                compItem.setOnAction(e -> toggleComponentOverride(compName));
                componentMenu.getItems().add(compItem);
            }
        }
        
        MenuItem otherComp = new MenuItem("Other...");
        otherComp.setOnAction(e -> applyTaskOverride("component:"));
        componentMenu.getItems().add(otherComp);
        menu.getItems().add(componentMenu);

        Menu transitionMenu = new Menu("Set Transition");
        String[] transOptions = {"HOLD", "CANCELED", "IN PROGRESS", "DONE"};
        for (String trans : transOptions) {
            MenuItem transItem = new MenuItem(trans);
            transItem.setOnAction(e -> applyTaskOverride("transition:", trans));
            transitionMenu.getItems().add(transItem);
        }
        MenuItem otherTrans = new MenuItem("Other...");
        otherTrans.setOnAction(e -> applyTaskOverride("transition:"));
        transitionMenu.getItems().add(otherTrans);
        menu.getItems().add(transitionMenu);
        
        Menu issueTypeMenu = new Menu("Set Issue-Type");
        java.util.List<String> types = mainFrame.getJiraConfig().getSubtaskTypes();
        for (String type : types) {
            MenuItem typeItem = new MenuItem(type);
            typeItem.setOnAction(e -> applyTaskOverride("issue-type:", type));
            issueTypeMenu.getItems().add(typeItem);
        }
        MenuItem otherType = new MenuItem("Other...");
        otherType.setOnAction(e -> applyTaskOverride("issue-type:"));
        issueTypeMenu.getItems().add(otherType);
        menu.getItems().add(issueTypeMenu);

        MenuItem addDueDate = new MenuItem("Set Due Date...");
        String dateExample = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        addDueDate.setOnAction(e -> applyTaskOverride("duedate:", dateExample));
        menu.getItems().add(addDueDate);

        MenuItem addNotify = new MenuItem("Set Notify...");
        addNotify.setOnAction(e -> applyTaskOverride("notify:"));
        menu.getItems().add(addNotify);
        
        menu.getItems().add(new SeparatorMenuItem());
        
        MenuItem clearAssignee = new MenuItem("No Assignee");
        clearAssignee.setOnAction(e -> applyTaskOverride("noassignee:"));
        menu.getItems().add(clearAssignee);

        MenuItem clearComp = new MenuItem("No Component");
        clearComp.setOnAction(e -> applyTaskOverride("nocomponent:"));
        menu.getItems().add(clearComp);

        MenuItem clearTrans = new MenuItem("No Transition");
        clearTrans.setOnAction(e -> applyTaskOverride("notransition:"));
        menu.getItems().add(clearTrans);

        inputArea.setContextMenu(menu);
        
        menu.setOnShowing(e -> {
            String currentText = inputArea.getText();
            int caretPos = inputArea.getCaretPosition();
            int taskStart = 0;
            int lastPos = 0;
            while (true) {
                int nextMatch = currentText.indexOf("******", lastPos);
                if (nextMatch == -1 || nextMatch >= caretPos) break;
                taskStart = nextMatch + 6;
                lastPos = nextMatch + 6;
            }
            int taskEnd = currentText.indexOf("******", taskStart);
            if (taskEnd == -1) taskEnd = currentText.length();
            String block = currentText.substring(taskStart, taskEnd);
            
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^component:(.*)$");
            java.util.regex.Matcher m = p.matcher(block);
            Set<String> activeComps = new HashSet<>();
            if (m.find()) {
                for (String s : m.group(1).split(",")) {
                    activeComps.add(s.trim());
                }
            }
            
            for (MenuItem item : componentMenu.getItems()) {
                if (item instanceof CheckMenuItem) {
                    CheckMenuItem checkItem = (CheckMenuItem) item;
                    checkItem.setSelected(activeComps.contains(checkItem.getText()));
                }
            }
        });
    }

    private void toggleComponentOverride(String compName) {
        String text = inputArea.getText();
        int caretPos = inputArea.getCaretPosition();
        
        int taskStart = 0;
        int lastPos = 0;
        while (true) {
            int nextMatch = text.indexOf("******", lastPos);
            if (nextMatch == -1 || nextMatch >= caretPos) break;
            taskStart = nextMatch + 6;
            lastPos = nextMatch + 6;
        }
        int taskEnd = text.indexOf("******", taskStart);
        if (taskEnd == -1) taskEnd = text.length();
        
        String block = text.substring(taskStart, taskEnd);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^component:(.*)$");
        java.util.regex.Matcher m = p.matcher(block);
        
        if (m.find()) {
            String currentVals = m.group(1).trim();
            Set<String> comps = new LinkedHashSet<>();
            for (String s : currentVals.split(",")) {
                if (!s.trim().isEmpty()) comps.add(s.trim());
            }
            
            if (comps.contains(compName)) {
                comps.remove(compName);
            } else {
                comps.add(compName);
            }
            
            String newLine = "component: " + String.join(", ", comps);
            int lineStart = taskStart + m.start();
            int lineEnd = taskStart + m.end();
            inputArea.replaceText(lineStart, lineEnd, newLine);
            inputArea.positionCaret(lineStart + newLine.length());
        } else {
            String before = (taskEnd == 0 || text.charAt(taskEnd - 1) == '\n') ? "" : "\n";
            String newLine = "component: " + compName + "\n";
            inputArea.insertText(taskEnd, before + newLine);
            inputArea.positionCaret(taskEnd + before.length() + newLine.length() - 1);
        }
        inputArea.requestFocus();
        parseInput();
    }

    private void applyTaskOverride(String prefix) {
        applyTaskOverride(prefix, "");
    }

    private void applyTaskOverride(String prefix, String value) {
        String text = inputArea.getText();
        int caretPos = inputArea.getCaretPosition();
        
        int taskStart = 0;
        int lastPos = 0;
        while (true) {
            int nextMatch = text.indexOf("******", lastPos);
            if (nextMatch == -1 || nextMatch >= caretPos) break;
            taskStart = nextMatch + 6;
            lastPos = nextMatch + 6;
        }
        int taskEnd = text.indexOf("******", taskStart);
        if (taskEnd == -1) taskEnd = text.length();
        
        String block = text.substring(taskStart, taskEnd);
        String prefixMatch = prefix.contains(":") ? prefix.split(":")[0] : prefix;
        String linePrefix = prefixMatch + ":";
        
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^" + linePrefix + "(.*)$");
        java.util.regex.Matcher m = p.matcher(block);
        
        if (m.find()) {
            int lineStartInDoc = taskStart + m.start();
            int lineEndInDoc = taskStart + m.end();
            
            if (value.isEmpty()) {
                inputArea.replaceText(lineStartInDoc, lineEndInDoc, linePrefix);
                inputArea.positionCaret(lineStartInDoc + linePrefix.length());
            } else {
                String newLine = linePrefix + value;
                inputArea.replaceText(lineStartInDoc, lineEndInDoc, newLine);
                inputArea.selectRange(lineStartInDoc + linePrefix.length(), lineStartInDoc + newLine.length());
            }
        } else {
            String before = (taskEnd == 0 || text.charAt(taskEnd - 1) == '\n') ? "" : "\n";
            String after = "\n";
            String insertText = before + linePrefix + value + after;
            
            inputArea.insertText(taskEnd, insertText);
            
            int insertPoint = taskEnd + before.length() + linePrefix.length();
            if (value.isEmpty()) {
                inputArea.positionCaret(insertPoint);
            } else {
                inputArea.selectRange(insertPoint, insertPoint + value.length());
            }
        }
        inputArea.requestFocus();
        parseInput();
    }

    private Rectangle2D getCharacterBounds(Skin<?> skin, int index) {
        try {
            java.lang.reflect.Method m = skin.getClass().getMethod("getCharacterBounds", int.class);
            m.setAccessible(true);
            return (Rectangle2D) m.invoke(skin, index);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateHighlights() {
        Platform.runLater(() -> {
            overlayPane.getChildren().clear();
            
            List<JiraTask> selectedTasks = taskList.getSelectionModel().getSelectedItems();
            if (selectedTasks == null || selectedTasks.isEmpty()) {
                return;
            }
            
            Skin<?> skin = inputArea.getSkin();
            if (skin == null) {
                return;
            }
            
            Node viewport = inputArea.lookup(".viewport");
            if (viewport != null) {
                Bounds viewportBounds = viewport.localToScene(viewport.getBoundsInLocal());
                Bounds localBounds = overlayPane.sceneToLocal(viewportBounds);
                if (localBounds != null) {
                    Rectangle clip = new Rectangle(
                        localBounds.getMinX(),
                        localBounds.getMinY(),
                        localBounds.getWidth(),
                        localBounds.getHeight()
                    );
                    overlayPane.setClip(clip);
                }
            }
            
            for (JiraTask task : selectedTasks) {
                if (task == null) continue;
                
                int start = Math.max(0, Math.min(task.startIndex, inputArea.getText().length()));
                int end = Math.max(0, Math.min(task.endIndex, inputArea.getText().length()));
                if (start >= end) continue;
                
                double currentX = -1;
                double currentY = -1;
                double startX = -1;
                double height = -1;
                
                List<Rectangle> rects = new ArrayList<>();
                
                for (int i = start; i < end; i++) {
                    Rectangle2D charBounds = getCharacterBounds(skin, i);
                    if (charBounds == null) continue;
                    
                    javafx.geometry.Point2D scenePos = inputArea.localToScene(charBounds.getMinX(), charBounds.getMinY());
                    javafx.geometry.Point2D localPos = overlayPane.sceneToLocal(scenePos);
                    if (localPos == null) continue;
                    
                    double x = localPos.getX();
                    double y = localPos.getY();
                    double w = charBounds.getWidth();
                    double h = charBounds.getHeight();
                    
                    if (currentY == -1) {
                        startX = x;
                        currentY = y;
                        currentX = x + w;
                        height = h;
                    } else if (Math.abs(y - currentY) > 2) {
                        rects.add(new Rectangle(startX, currentY, currentX - startX, height));
                        startX = x;
                        currentY = y;
                        currentX = x + w;
                        height = h;
                    } else {
                        currentX = x + w;
                        height = Math.max(height, h);
                    }
                }
                if (startX != -1) {
                    rects.add(new Rectangle(startX, currentY, currentX - startX, height));
                }
                
                for (Rectangle r : rects) {
                    r.setStyle("-fx-fill: -fx-accent; -fx-opacity: 0.25;");
                    overlayPane.getChildren().add(r);
                }
            }
        });
    }

    private void updateStatus(String msg) { Platform.runLater(() -> statusBar.setText(" " + msg)); }

    private void addSyncListener(TextField field, String prefix) {
        field.textProperty().addListener((obs, oldV, newV) -> syncToText(prefix, newV));
    }

    private void syncToText(String prefix, String newValue) {
        if (isUpdating) return;
        isUpdating = true;
        String content = inputArea.getText();
        String lineStart = prefix + ":";
        if (content.contains(lineStart)) {
            content = content.replaceAll("(?m)^" + lineStart + ".*$", lineStart + newValue);
        } else {
            content = lineStart + newValue + "\n" + content;
        }
        inputArea.setText(content);
        isUpdating = false;
    }

    private void parseInput() {
        if (isUpdating) return;
        isUpdating = true;

        List<JiraTask> selectedTasks = new ArrayList<>(taskList.getSelectionModel().getSelectedItems());
        Set<String> selectedSummaries = new HashSet<>();
        for (JiraTask selectedTask : selectedTasks) {
            if (selectedTask != null) {
                selectedSummaries.add(selectedTask.summary);
            }
        }

        parsedTasks.clear();
        taskListModel.clear();

        String text = inputArea.getText();
        int currentOffset = 0;
        for (String block : text.split("\\*{6,}")) {
            int blockStart = text.indexOf(block, currentOffset);
            if (block.trim().isEmpty()) {
                if (blockStart != -1) currentOffset = blockStart + block.length();
                continue;
            }
            JiraTask task = new JiraTask();
            task.startIndex = blockStart;
            StringBuilder desc = new StringBuilder();
            boolean summaryFound = false;

            for (String line : block.split("\n")) {
                String t = line.trim();
                if (t.startsWith("--")) continue;
                if (t.startsWith("DEFAULT_TYPE:")) { Platform.runLater(() -> defTypeField.getSelectionModel().select(val(t))); continue; }
                if (t.startsWith("DEFAULT_ASSIGNEE:")) { Platform.runLater(() -> defAssigneeField.setText(val(t))); continue; }
                if (t.startsWith("DEFAULT_COMPONENT:")) { Platform.runLater(() -> defCompField.setText(val(t))); continue; }
                if (t.startsWith("DEFAULT_TRANSITION:")) { Platform.runLater(() -> defTransField.setText(val(t))); continue; }
                if (t.startsWith("PARENT_TICKET:")) { Platform.runLater(() -> parentField.setText(val(t).toUpperCase())); continue; }
                if (t.equalsIgnoreCase("noassignee:")) { task.assignee = ""; task.overAssignee = true; continue; }
                if (t.equalsIgnoreCase("nocomponent:")) { task.component = ""; task.overComp = true; continue; }
                if (t.equalsIgnoreCase("notransition:")) { task.transition = ""; task.overTrans = true; continue; }
                if (t.startsWith("assignee:")) { task.assignee = val(t); task.overAssignee = true; continue; }
                if (t.startsWith("component:")) { task.component = val(t); task.overComp = true; continue; }
                if (t.startsWith("issue-type:")) { task.type = val(t); continue; }
                if (t.startsWith("transition:")) { task.transition = val(t); task.overTrans = true; continue; }
                if (t.startsWith("parent:")) { task.parent = val(t).toUpperCase(); continue; }
                if (t.startsWith("duedate:")) { task.duedate = val(t); continue; }
                if (t.startsWith("notify:")) { task.notify = val(t); continue; }
                if (!summaryFound && !t.isEmpty()) { task.summary = t; summaryFound = true; }
                else if (summaryFound) { desc.append(line).append("\n"); }
            }

            if (summaryFound) {
                applyDefaults(task);
                task.description = desc.toString().trim();
                task.endIndex = blockStart + block.length();
                parsedTasks.add(task);
                taskListModel.add(task);
            }
            if (blockStart != -1) currentOffset = blockStart + block.length();
        }
        
        for (JiraTask currentTask : taskListModel) {
            if (selectedSummaries.contains(currentTask.summary)) {
                taskList.getSelectionModel().select(currentTask);
            }
        }
        updateHighlights();
        
        isUpdating = false;
    }
    
    private void loadTemplatesFromDisk() {
        templateSelector.getItems().clear();
        templateSelector.getItems().add("--- Select Template ---");
        
        try {
            File templateDir = new File(mainFrame.getJiraConfig().getConfigFile().getParentFile(), "template");
            if (!templateDir.exists()) {
                templateDir.mkdirs();
            }
            
            File[] files = templateDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                for (File f : files) {
                    templateSelector.getItems().add(f.getName());
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        templateSelector.getSelectionModel().select(0);
    }

    private void loadSelectedTemplate() {
        if (isUpdating) return;
        String selected = templateSelector.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals("--- Select Template ---")) return;
        
        try {
            File templateDir = new File(mainFrame.getJiraConfig().getConfigFile().getParentFile(), "template");
            File templateFile = new File(templateDir, selected);
            String content = new String(Files.readAllBytes(templateFile.toPath()));
            
            inputArea.setText(content);
            parseInput();
            setAllTasksSelected(true);
            
            Platform.runLater(() -> templateSelector.getSelectionModel().select(0));
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Error reading template: " + ex.getMessage());
        }
    }
    
    private void applyDefaults(JiraTask t) {
        if (t.type == null) t.type = defTypeField.getSelectionModel().getSelectedItem();
        if (!t.overAssignee) t.assignee = defAssigneeField.getText();
        if (!t.overComp) t.component = defCompField.getText();
        if (!t.overTrans) t.transition = defTransField.getText();
    }

    private String val(String s) { return s.contains(":") ? s.substring(s.indexOf(":") + 1).trim() : ""; }

    private void executeTasks() {
        resultsTable.getItems().clear();
        parseInput();
        
        List<JiraTask> selected = new ArrayList<>(taskList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty() || (selected.size() == 1 && selected.get(0) == null)) {
            updateStatus("No tasks selected.");
            return;
        }

        final String defaultParent = parentField.getText().trim().toUpperCase();
        final int total = selected.size();
        final List<String> createdKeys = Collections.synchronizedList(new ArrayList<>());

        ExecutionService.submit(() -> {
            try {
                if (total > 1 && !MOCK_MODE) {
                    updateStatus("Creating " + total + " tasks in bulk...");
                    List<String> taskJsons = new ArrayList<>();
                    for (JiraTask t : selected) {
                        if (t == null) continue;
                        String parent = (t.parent != null && !t.parent.isEmpty()) ? t.parent : defaultParent;
                        String proj = parent.contains("-") ? parent.split("-")[0] : "PROJ";
                        String assignee = t.assignee;
                        List<String> noAssigneeTypes = Arrays.asList("ST-PCU", "ST-Database", "ST-Interface");
                        if (t.type != null && noAssigneeTypes.contains(t.type)) assignee = null;
                        taskJsons.add(JsonUtils.buildManualJson(proj, parent, t.summary, t.description, t.type, assignee, t.component, t.duedate));
                    }
                    String bulkJson = JsonUtils.buildBulkJson(taskJsons);
                    String resp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/bulk", "POST", bulkJson);
                    
                    JSONObject bulkResp = new JSONObject(resp);
                    JSONArray issues = bulkResp.getJSONArray("issues");
                    for (int i = 0; i < issues.length(); i++) createdKeys.add(issues.getJSONObject(i).getString("key"));
                } else {
                    for (int i = 0; i < total; i++) {
                        JiraTask t = selected.get(i);
                        if (t == null) continue;
                        String parent = (t.parent != null && !t.parent.isEmpty()) ? t.parent : defaultParent;
                        String proj = parent.contains("-") ? parent.split("-")[0] : "PROJ";
                        if (MOCK_MODE) {
                            Thread.sleep(200);
                            createdKeys.add(proj + "-" + (100 + new Random().nextInt(900)));
                        } else {
                            String assignee = t.assignee;
                            List<String> noAssigneeTypes = Arrays.asList("ST-PCU", "ST-Database", "ST-Interface");
                            if (t.type != null && noAssigneeTypes.contains(t.type)) assignee = null;
                            String createJson = JsonUtils.buildManualJson(proj, parent, t.summary, t.description, t.type, assignee, t.component, t.duedate);
                            String resp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue", "POST", createJson);
                            createdKeys.add(new JSONObject(resp).getString("key"));
                        }
                    }
                }

                updateStatus("Created " + total + " tasks. Processing transitions and notifications in parallel...");
                int threadCount = mainFrame.getJiraConfig().getParallelThreads();
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                AtomicInteger completedCount = new AtomicInteger(0);

                for (int i = 0; i < total; i++) {
                    final int idx = i;
                    final String key = createdKeys.get(idx);
                    final JiraTask t = selected.get(idx);
                    if (t == null) continue;
                    
                    executor.submit(() -> {
                        String link = mainFrame.getBaseUrl() + "/browse/" + key;
                        String status = "CREATED";
                        try {
                            if (!t.transition.isEmpty()) {
                                String transitionId = null;
                                if (MOCK_MODE) {
                                    Thread.sleep(300);
                                    transitionId = "711";
                                } else {
                                    String transResp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "GET", null);
                                    transitionId = JiraUtils.findTransitionIdByName(transResp, t.transition);
                                }
                                
                                if (transitionId != null) {
                                    if (!MOCK_MODE) {
                                        JSONObject payload = new JSONObject();
                                        payload.put("transition", new JSONObject().put("id", transitionId));
                                        mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "POST", payload.toString());
                                    }
                                    status = "CREATED & MOVED TO: " + t.transition.toUpperCase();
                                } else {
                                    status = "CREATED (Trans. '" + t.transition + "' not found)";
                                }
                            }

                            if (t.notify != null && !t.notify.trim().isEmpty()) {
                                if (MOCK_MODE) {
                                    Thread.sleep(200);
                                } else {
                                    JSONObject notifyPayload = new JSONObject();
                                    notifyPayload.put("subject", "Task Created: " + t.summary);
                                    notifyPayload.put("textBody", "A new issue has been created that you were listed to be notified about.\n\nSummary: " + t.summary + "\nLink: " + link);
                                    JSONArray usersToNotify = new JSONArray();
                                    for (String user : t.notify.split("\\s*,\\s*")) {
                                        String u = user.trim();
                                        if (u.isEmpty()) continue;
                                        
                                        String teamKey = u;
                                        if (teamKey.startsWith("@")) teamKey = teamKey.substring(1);
                                        if (teamKey.startsWith("team.")) teamKey = teamKey.substring(5);
                                        
                                        String members = mainFrame.getJiraConfig().getTeamProperty(teamKey, "members");
                                        if (members != null && !members.trim().isEmpty()) {
                                            for (String m : members.split(",")) {
                                                String memberTrimmed = m.trim();
                                                if (!memberTrimmed.isEmpty()) {
                                                    usersToNotify.put(new JSONObject().put("name", memberTrimmed));
                                                }
                                            }
                                        } else {
                                            usersToNotify.put(new JSONObject().put("name", u));
                                        }
                                    }
                                    notifyPayload.put("to", new JSONObject().put("users", usersToNotify));
                                     mainFrame.getIssueService().notifyIssue(key, notifyPayload.toString());
                                }
                                status += " & NOTIFIED";
                            }
                            addRow(t.summary, status, link);
                        } catch (Exception ex) {
                            addRow(t.summary, "CREATED (Err: " + ex.getMessage() + ")", link);
                        } finally {
                            int count = completedCount.incrementAndGet();
                            updateStatus("Parallel Progress: " + count + " of " + total + " actions complete...");
                        }
                    });
                }

                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.HOURS);
                updateStatus("Execution Complete. " + total + " tasks and actions processed.");

            } catch (Exception e) {
                updateStatus("Execution Failed: " + e.getMessage());
                addRow("SYSTEM ERROR", e.getMessage(), "N/A");
            }
        });
    }

    private void addRow(String s, String st, String l) { 
        Platform.runLater(() -> resultsTable.getItems().add(new ExecutionResultRow(s, st, l))); 
    }
    
    private KeyCombination parseKeyCombination(String text, String defaultCombo) {
        if (text == null || text.trim().isEmpty()) {
            return KeyCombination.valueOf(defaultCombo);
        }
        String cleaned = text.trim().replaceAll("\\s+", "");
        String[] parts = cleaned.split("\\+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i > 0) sb.append("+");
            
            if (part.equalsIgnoreCase("ctrl") || part.equalsIgnoreCase("control") || part.equalsIgnoreCase("cntrl")) {
                sb.append("Ctrl");
            } else if (part.equalsIgnoreCase("alt")) {
                sb.append("Alt");
            } else if (part.equalsIgnoreCase("shift")) {
                sb.append("Shift");
            } else if (part.equalsIgnoreCase("meta") || part.equalsIgnoreCase("shortcut")) {
                sb.append("Meta");
            } else {
                String key = part;
                if (key.equals("/")) key = "Slash";
                else if (key.equals("\\")) key = "Back_Slash";
                else if (key.equals(";")) key = "Semicolon";
                else if (key.equals(",")) key = "Comma";
                else if (key.equals(".")) key = "Period";
                else if (key.equals("-")) key = "Minus";
                else if (key.equals("=")) key = "Equals";
                else if (key.equals("[")) key = "Open_Bracket";
                else if (key.equals("]")) key = "Close_Bracket";
                else if (key.equals("`")) key = "Back_Quote";
                else if (key.equals("'")) key = "Quote";
                else if (key.equals("+")) key = "Plus";
                else if (key.equals("*")) key = "Asterisk";
                
                if (key.length() > 1) {
                    key = key.substring(0, 1).toUpperCase() + key.substring(1).toLowerCase();
                } else {
                    key = key.toUpperCase();
                }
                sb.append(key);
            }
        }
        try {
            return KeyCombination.valueOf(sb.toString());
        } catch (Exception e) {
            System.err.println("Invalid key combination: " + text + " (parsed as: " + sb + "). Using default: " + defaultCombo);
            try {
                return KeyCombination.valueOf(defaultCombo);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private void loadKeybinds() {
        tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
        keyDuplicateDown = parseKeyCombination(config.getProperty("keybind.taskbuilder.duplicate.down"), "Ctrl+Alt+Down");
        keyDuplicateUp = parseKeyCombination(config.getProperty("keybind.taskbuilder.duplicate.up"), "Ctrl+Alt+Up");
        keyMoveDown = parseKeyCombination(config.getProperty("keybind.taskbuilder.move.down"), "Alt+Down");
        keyMoveUp = parseKeyCombination(config.getProperty("keybind.taskbuilder.move.up"), "Alt+Up");
        keyComment = parseKeyCombination(config.getProperty("keybind.taskbuilder.comment"), "Ctrl+Slash");
        keyDelete = parseKeyCombination(config.getProperty("keybind.taskbuilder.delete"), "Ctrl+D");
        keyBold = parseKeyCombination(config.getProperty("keybind.taskbuilder.bold"), "Ctrl+B");
        keyItalic = parseKeyCombination(config.getProperty("keybind.taskbuilder.italic"), "Ctrl+I");
    }

    private void setupInputAreaKeyBindings() {
        loadKeybinds();
        mainFrame.getJiraConfig().addConfigChangeListener(this::loadKeybinds);

        inputArea.setOnKeyPressed(event -> {
            if (keyDuplicateDown != null && keyDuplicateDown.match(event)) {
                duplicateLines(true);
                event.consume();
            } else if (keyDuplicateUp != null && keyDuplicateUp.match(event)) {
                duplicateLines(false);
                event.consume();
            } else if (keyMoveDown != null && keyMoveDown.match(event)) {
                moveLines(true);
                event.consume();
            } else if (keyMoveUp != null && keyMoveUp.match(event)) {
                moveLines(false);
                event.consume();
            } else if (keyComment != null && keyComment.match(event)) {
                toggleComments();
                event.consume();
            } else if (keyDelete != null && keyDelete.match(event)) {
                deleteLines();
                event.consume();
            } else if (keyBold != null && keyBold.match(event)) {
                toggleFormat("*");
                event.consume();
            } else if (keyItalic != null && keyItalic.match(event)) {
                toggleFormat("{_}");
                event.consume();
            }
        });
    }

    private void toggleFormat(String symbol) {
        try {
            IndexRange range = inputArea.getSelection();
            if (range.getLength() == 0) return;

            String selectedText = inputArea.getSelectedText();
            if (selectedText == null) return;

            if (selectedText.startsWith(symbol) && selectedText.endsWith(symbol)) {
                String unformatted = selectedText.substring(symbol.length(), selectedText.length() - symbol.length());
                inputArea.replaceText(range.getStart(), range.getEnd(), unformatted);
                inputArea.selectRange(range.getStart(), range.getStart() + unformatted.length());
            } else {
                String formatted = symbol + selectedText + symbol;
                inputArea.replaceText(range.getStart(), range.getEnd(), formatted);
                inputArea.selectRange(range.getStart(), range.getStart() + formatted.length());
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private int getRowStart(String text, int offset) {
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        return start;
    }

    private int getRowEnd(String text, int offset) {
        int end = offset;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }

    private void duplicateLines(boolean down) {
        try {
            String text = inputArea.getText();
            IndexRange range = inputArea.getSelection();
            int lineStart = getRowStart(text, range.getStart());
            int lineEnd = getRowEnd(text, range.getEnd());
            
            String textToDuplicate = text.substring(lineStart, lineEnd);
            if (textToDuplicate.isEmpty()) return;

            if (down) {
                inputArea.insertText(lineEnd, "\n" + textToDuplicate);
            } else {
                inputArea.insertText(lineStart, textToDuplicate + "\n");
                inputArea.positionCaret(lineStart);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void moveLines(boolean down) {
        try {
            String text = inputArea.getText();
            IndexRange range = inputArea.getSelection();
            int lineStart = getRowStart(text, range.getStart());
            int lineEnd = getRowEnd(text, range.getEnd());
            
            int docLen = text.length();
            int selectionEnd = (lineEnd < docLen) ? lineEnd + 1 : lineEnd;
            String textToMove = text.substring(lineStart, selectionEnd);
            
            if (!textToMove.endsWith("\n") && selectionEnd < docLen) {
                textToMove += "\n";
            }

            int caretPos = inputArea.getCaretPosition();
            int anchorPos = inputArea.getAnchor();
            int caretOffset = caretPos - lineStart;
            int anchorOffset = anchorPos - lineStart;

            if (down) {
                if (selectionEnd >= docLen) return;
                int nextLineEnd = getRowEnd(text, selectionEnd);
                int nextSelectionEnd = (nextLineEnd < docLen) ? nextLineEnd + 1 : nextLineEnd;
                String nextLine = text.substring(selectionEnd, nextSelectionEnd);
                
                if (!nextLine.endsWith("\n") && nextSelectionEnd < docLen) {
                    nextLine += "\n";
                }

                inputArea.replaceText(lineStart, nextSelectionEnd, nextLine + textToMove);
                int newStart = lineStart + nextLine.length();
                inputArea.selectRange(newStart + anchorOffset, newStart + caretOffset);
            } else {
                if (lineStart <= 0) return;
                int prevLineStart = getRowStart(text, lineStart - 1);
                String prevLine = text.substring(prevLineStart, lineStart);
                
                if (!prevLine.endsWith("\n")) {
                    prevLine += "\n";
                }

                inputArea.replaceText(prevLineStart, selectionEnd, textToMove + prevLine);
                int newStart = prevLineStart;
                inputArea.selectRange(newStart + anchorOffset, newStart + caretOffset);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void toggleComments() {
        try {
            String text = inputArea.getText();
            IndexRange range = inputArea.getSelection();
            int lineStart = getRowStart(text, range.getStart());
            int lineEnd = getRowEnd(text, range.getEnd());
            String selectedText = text.substring(lineStart, lineEnd);
            
            String[] lines = selectedText.split("\n", -1);
            boolean allCommented = true;
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.trim().startsWith("--")) {
                    allCommented = false;
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (allCommented) {
                    if (line.trim().startsWith("--")) {
                        sb.append(line.replaceFirst("--\\s?", ""));
                    } else {
                        sb.append(line);
                    }
                } else {
                    sb.append("-- ").append(line);
                }
                if (i < lines.length - 1) sb.append("\n");
            }
            
            inputArea.replaceText(lineStart, lineEnd, sb.toString());
            inputArea.selectRange(lineStart, lineStart + sb.length());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void deleteLines() {
        try {
            String text = inputArea.getText();
            IndexRange range = inputArea.getSelection();
            int lineStart = getRowStart(text, range.getStart());
            int lineEnd = getRowEnd(text, range.getEnd());
            
            int docLen = text.length();
            int deletionEnd = (lineEnd < docLen) ? lineEnd + 1 : lineEnd;
            
            inputArea.replaceText(lineStart, deletionEnd, "");
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void setupDragAndDrop() {
        inputArea.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        inputArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    try {
                        String fileContent = new String(Files.readAllBytes(file.toPath()));
                        if (inputArea.getText().length() > 0) {
                            inputArea.appendText("\n******\n");
                        }
                        inputArea.appendText(fileContent);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }
    
    private void setAllTasksSelected(boolean selected) {
        if (selected) {
            taskList.getSelectionModel().selectAll();
        } else {
            taskList.getSelectionModel().clearSelection();
        }
    }

    private void ensureAutocompleteServiceInitialized() {
        if (jqlAutocompleteService != null) return;
        try {
            this.jqlAutocompleteService = new JqlAutocompleteService(mainFrame.getService(), mainFrame.getBaseUrl(), mainFrame.getJiraConfig());
            boolean enabled = mainFrame.getJiraConfig().isAutocompleteEnabled();
            this.inputArea.setService(jqlAutocompleteService);
            this.inputArea.setAutocompleteEnabled(enabled);
            this.defAssigneeField.setService(jqlAutocompleteService);
            this.defAssigneeField.setAutocompleteEnabled(enabled);
        } catch (Exception e) {
            // ignore
        }
    }
    
    private class JiraTask {
        String summary = "", description = "", type = null, assignee = "", component = "", transition = "", duedate = null, notify = null, parent = null;
        boolean overAssignee = false, overComp = false, overTrans = false;
        int startIndex = 0, endIndex = 0;
    }

    public void setParentTicket(String issueKey) {
        if (issueKey != null) {
            parentField.setText(issueKey);
        }
    }

    public void setInputAreaText(String text) {
        if (text != null) {
            inputArea.setText(text);
        }
    }

    public String getInputAreaText() {
        return inputArea.getText();
    }

    public void appendInputAreaText(String text) {
        if (text != null) {
            String current = inputArea.getText();
            if (!current.isEmpty()) {
                if (!current.endsWith("\n")) {
                    inputArea.appendText("\n");
                }
                inputArea.appendText("******\n");
            }
            inputArea.appendText(text);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
