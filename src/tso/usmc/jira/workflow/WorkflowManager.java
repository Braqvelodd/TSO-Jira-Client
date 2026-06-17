package tso.usmc.jira.workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class WorkflowManager {
    private final File workflowDir;

    public WorkflowManager() {
        String userHome = System.getProperty("user.home");
        workflowDir = new File(userHome, ".JiraApiClient/workflows");
        if (!workflowDir.exists()) {
            workflowDir.mkdirs();
        }
    }

    public List<String> listWorkflows() {
        List<String> names = new ArrayList<>();
        File[] files = workflowDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().replace(".json", ""));
            }
        }
        return names;
    }

    public void saveWorkflow(WorkflowRecipe recipe) throws IOException {
        File file = new File(workflowDir, recipe.getRecipeName() + ".json");
        Files.write(file.toPath(), recipe.toJson().toString(4).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public WorkflowRecipe loadWorkflow(String name) throws IOException {
        File file = new File(workflowDir, name + ".json");
        if (!file.exists()) return null;
        String content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        return WorkflowRecipe.fromJson(content);
    }
    
    public void deleteWorkflow(String name) {
        File file = new File(workflowDir, name + ".json");
        if (file.exists()) file.delete();
    }
}
