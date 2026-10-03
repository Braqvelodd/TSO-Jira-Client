package tso.usmc.jira.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tso.usmc.jira.util.JiraConfig;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for configuring and launching the Topaz Mainframe companion tool.
 */
public class CompanionConfigDialog extends Dialog<Boolean> {

    private final JiraConfig config;
    private final TextField jarPathField = new TextField();
    private final ComboBox<String> authModeCombo = new ComboBox<>();
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();

    private final ComboBox<String> fetchModeCombo = new ComboBox<>();
    private final TextField datasetField = new TextField();
    private final TextField jobIdField = new TextField();
    private final TextField jobDdField = new TextField();
    private final TextField jclSourceField = new TextField();
    private final TextField timeoutField = new TextField();

    private final VBox datasetBox = new VBox(5);
    private final VBox spoolBox = new VBox(5);
    private final VBox submitBox = new VBox(5);
    private final HBox userPassRow = new HBox(10);

    public CompanionConfigDialog(Window owner, JiraConfig config) {
        this.config = config;

        setTitle("Mainframe Companion Settings (Topaz)");
        UiUtils.configureWindowOwner(this, owner);

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("card");
        pane.setPrefWidth(600);

        ButtonType fetchNowType = new ButtonType("Fetch Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType saveType = new ButtonType("Save Settings", ButtonBar.ButtonData.APPLY);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(fetchNowType, saveType, cancelType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));

        // 1. Topaz JAR Path
        Label jarLabel = new Label("Topaz JAR Path:");
        String configuredPath = config.getCompanionTopazPath();
        if (configuredPath != null && (configuredPath.toLowerCase().endsWith(".bat") || configuredPath.toLowerCase().endsWith(".cmd"))) {
            File batFile = new File(configuredPath.trim());
            if (batFile.getParentFile() != null) {
                File distJar = new File(batFile.getParentFile(), "dist" + File.separator + "topaz-pds-reader.jar");
                if (distJar.exists()) {
                    try {
                        configuredPath = distJar.getCanonicalPath();
                    } catch (Exception ignored) {
                        configuredPath = distJar.getAbsolutePath();
                    }
                }
            }
        }
        if (configuredPath == null || configuredPath.trim().isEmpty() || !new File(configuredPath.trim()).exists()) {
            File fallback1 = new File("../Topaz-file-read/dist/topaz-pds-reader.jar");
            File fallback2 = new File(System.getProperty("user.home"), "Documents/projects/Topaz-file-read/dist/topaz-pds-reader.jar");
            if (fallback1.exists()) {
                try {
                    configuredPath = fallback1.getCanonicalPath();
                } catch (Exception ignored) {
                    configuredPath = fallback1.getAbsolutePath();
                }
            } else if (fallback2.exists()) {
                configuredPath = fallback2.getAbsolutePath();
            }
        }
        jarPathField.setText(configuredPath != null ? configuredPath : "");
        jarPathField.setPromptText("Path to topaz-pds-reader.jar");
        Button browseJarBtn = new Button("Browse...");
        browseJarBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Topaz Extractor JAR");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable JAR Files (*.jar)", "*.jar"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
            if (!jarPathField.getText().trim().isEmpty()) {
                File cur = new File(jarPathField.getText().trim());
                if (cur.getParentFile() != null && cur.getParentFile().exists()) {
                    chooser.setInitialDirectory(cur.getParentFile());
                }
            }
            File selected = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                jarPathField.setText(selected.getAbsolutePath());
            }
        });
        HBox jarBox = new HBox(8, jarPathField, browseJarBtn);
        HBox.setHgrow(jarPathField, Priority.ALWAYS);
        grid.add(jarLabel, 0, 0);
        grid.add(jarBox, 1, 0);

        // 2. Authentication Mode
        Label authLabel = new Label("Authentication Mode:");
        authModeCombo.getItems().addAll("CAC X.509 Certificate", "User / Password", "mTLS Certificate");
        String currentAuth = config.getCompanionTopazAuthMode();
        if ("USER_PASSWORD".equalsIgnoreCase(currentAuth) || "PASSWORD".equalsIgnoreCase(currentAuth)) {
            authModeCombo.setValue("User / Password");
        } else if ("MTLS".equalsIgnoreCase(currentAuth)) {
            authModeCombo.setValue("mTLS Certificate");
        } else {
            authModeCombo.setValue("CAC X.509 Certificate");
        }
        authModeCombo.setMaxWidth(Double.MAX_VALUE);
        grid.add(authLabel, 0, 1);
        grid.add(authModeCombo, 1, 1);

        // 3. User / Password Fields (only visible if User/Password mode is chosen)
        userField.setPromptText("RACF User ID");
        userField.setText(config.getCompanionTopazUser());
        passField.setPromptText("RACF Password");
        passField.setText(config.getCompanionTopazPassword());
        userPassRow.getChildren().addAll(new Label("User:"), userField, new Label("Pass:"), passField);
        userPassRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(userField, Priority.ALWAYS);
        HBox.setHgrow(passField, Priority.ALWAYS);
        grid.add(new Label("Credentials:"), 0, 2);
        grid.add(userPassRow, 1, 2);

        authModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isPass = "User / Password".equals(newV);
            userPassRow.setDisable(!isPass);
        });
        userPassRow.setDisable(!"User / Password".equals(authModeCombo.getValue()));

        // 4. Fetch Mode
        Label fetchModeLabel = new Label("Mainframe Fetch Mode:");
        fetchModeCombo.getItems().addAll("Sequential Dataset", "JES Job Spool", "Submit JCL & Wait");
        String currentFetch = config.getCompanionTopazFetchMode();
        if ("JOB_SPOOL".equalsIgnoreCase(currentFetch)) {
            fetchModeCombo.setValue("JES Job Spool");
        } else if ("SUBMIT".equalsIgnoreCase(currentFetch)) {
            fetchModeCombo.setValue("Submit JCL & Wait");
        } else {
            fetchModeCombo.setValue("Sequential Dataset");
        }
        fetchModeCombo.setMaxWidth(Double.MAX_VALUE);
        grid.add(fetchModeLabel, 0, 3);
        grid.add(fetchModeCombo, 1, 3);

        // Mode Parameters Panels
        datasetField.setPromptText("e.g. MTFSP.REPORT.DATA");
        datasetField.setText(config.getCompanionTopazDataset());
        datasetBox.getChildren().addAll(new Label("Dataset Name:"), datasetField);

        jobIdField.setPromptText("e.g. JOB24742");
        jobIdField.setText(config.getCompanionTopazJobId());
        jobDdField.setPromptText("e.g. SORTOUT or SYSPRINT");
        jobDdField.setText(config.getCompanionTopazJobDd());
        HBox spoolRow = new HBox(8, new Label("Job ID:"), jobIdField, new Label("DD:"), jobDdField);
        spoolRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(jobIdField, Priority.ALWAYS);
        HBox.setHgrow(jobDdField, Priority.ALWAYS);
        spoolBox.getChildren().add(spoolRow);

        jclSourceField.setPromptText("Local .jcl file path or PDS(MEM) e.g. MTFSP.JCL(REPORT)");
        jclSourceField.setText(config.getCompanionTopazJclSource());
        Button browseJclBtn = new Button("Browse...");
        browseJclBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Local JCL File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JCL Files (*.jcl, *.txt)", "*.jcl", "*.txt"));
            File selected = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                jclSourceField.setText(selected.getAbsolutePath());
            }
        });
        HBox jclRow = new HBox(8, jclSourceField, browseJclBtn);
        HBox.setHgrow(jclSourceField, Priority.ALWAYS);
        submitBox.getChildren().addAll(new Label("JCL Source:"), jclRow);

        VBox modeParamsContainer = new VBox(5);
        grid.add(new Label("Operation Target:"), 0, 4);
        grid.add(modeParamsContainer, 1, 4);

        fetchModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            modeParamsContainer.getChildren().clear();
            if ("JES Job Spool".equals(newV)) {
                modeParamsContainer.getChildren().add(spoolBox);
            } else if ("Submit JCL & Wait".equals(newV)) {
                modeParamsContainer.getChildren().add(submitBox);
            } else {
                modeParamsContainer.getChildren().add(datasetBox);
            }
        });

        // Initialize mode parameters container
        if ("JES Job Spool".equals(fetchModeCombo.getValue())) {
            modeParamsContainer.getChildren().add(spoolBox);
        } else if ("Submit JCL & Wait".equals(fetchModeCombo.getValue())) {
            modeParamsContainer.getChildren().add(submitBox);
        } else {
            modeParamsContainer.getChildren().add(datasetBox);
        }

        // 5. Timeout
        Label timeoutLabel = new Label("Timeout (sec, 0=infinite):");
        timeoutField.setText(String.valueOf(config.getCompanionTopazTimeoutSec()));
        timeoutField.setPromptText("0 = infinite");
        timeoutField.setPrefWidth(80);
        grid.add(timeoutLabel, 0, 5);
        grid.add(timeoutField, 1, 5);

        pane.setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == fetchNowType || dialogButton == saveType) {
                saveSettingsToConfig();
                return dialogButton == fetchNowType; // true = fetch now, false = save only
            }
            return null;
        });
    }

    private void saveSettingsToConfig() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("companion.topaz.path", jarPathField.getText().trim());

        String auth;
        if ("User / Password".equals(authModeCombo.getValue())) {
            auth = "USER_PASSWORD";
        } else if ("mTLS Certificate".equals(authModeCombo.getValue())) {
            auth = "MTLS";
        } else {
            auth = "CERTIFICATE";
        }
        props.put("companion.topaz.auth_mode", auth);
        props.put("companion.topaz.user", userField.getText().trim());
        props.put("companion.topaz.password", passField.getText());

        String fetch;
        if ("JES Job Spool".equals(fetchModeCombo.getValue())) {
            fetch = "JOB_SPOOL";
        } else if ("Submit JCL & Wait".equals(fetchModeCombo.getValue())) {
            fetch = "SUBMIT";
        } else {
            fetch = "DATASET";
        }
        props.put("companion.topaz.fetch_mode", fetch);
        props.put("companion.topaz.dataset", datasetField.getText().trim());
        props.put("companion.topaz.job_id", jobIdField.getText().trim());
        props.put("companion.topaz.job_dd", jobDdField.getText().trim().isEmpty() ? "SORTOUT" : jobDdField.getText().trim());
        props.put("companion.topaz.jcl_source", jclSourceField.getText().trim());
        props.put("companion.topaz.timeout_sec", timeoutField.getText().trim());

        config.saveProperties(props);
    }
}
