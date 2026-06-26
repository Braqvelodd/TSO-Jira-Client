package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.util.JiraUtils;
import java.util.*;

/**
 * Headless engine for executing Workflow Recipes.
 * Decoupled from Swing components.
 */
public class WorkflowEngine {
    
    /**
     * Data class representing the result of a workflow run on a single issue.
     */
    public static class ExecutionResult {
        public final String issueKey;
        public final String status; // SUCCESS, FAILED, SKIPPED (Dry Run)
        public final List<String> logEntries = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public final long durationMs;

        public ExecutionResult(String key, String status, long duration) {
            this.issueKey = key; this.status = status; this.durationMs = duration;
        }
    }

    private final JiraApiService apiService;
    private final JiraIssueService issueService;
    private final MetadataCacheService metadataService;
    private final String baseUrl;
    private final WorkflowProgressListener listener;
    
    private final Map<String, String> executionVars = new HashMap<>();
    private final Map<String, JSONObject> jsonContexts = new HashMap<>();
    private boolean verboseLogging = false;
    private boolean dryRun = false;
    private boolean suppressSummaryLogging = false;

    public void setSuppressSummaryLogging(boolean suppress) {
        this.suppressSummaryLogging = suppress;
    }

    public WorkflowEngine(JiraApiService apiService, JiraIssueService issueService, MetadataCacheService metadataService, String baseUrl, WorkflowProgressListener listener) {
        this.apiService = apiService;
        this.issueService = issueService;
        this.metadataService = metadataService;
        this.baseUrl = baseUrl;
        this.listener = listener;
    }

    public void setVerboseLogging(boolean enabled) {
        this.verboseLogging = enabled;
    }

    public void setDryRun(boolean enabled) {
        this.dryRun = enabled;
    }

    public List<ExecutionResult> execute(WorkflowRecipe recipe, List<JSONObject> issues, Map<String, String> promptValues) {
        List<ExecutionResult> results = new ArrayList<>();
        try {
            String mode = dryRun ? "[DRY RUN - VALIDATE ONLY]" : "[LIVE EXECUTION]";
            if (!suppressSummaryLogging) {
                listener.onLog(mode + " Starting workflow: " + recipe.getRecipeName() + " on " + issues.size() + " issues.");
            }

            for (JSONObject issue : issues) {
                long start = System.currentTimeMillis();
                String key = issue.getString("key");
                listener.onLog("--- Processing " + key + " ---");
                
                ExecutionResult result = new ExecutionResult(key, dryRun ? "SKIPPED" : "SUCCESS", 0);
                boolean issueFailed = false;

                executionVars.clear();
                jsonContexts.clear();
                executionVars.put("issue.key", key);
                executionVars.put("key", key);
                jsonContexts.put("issue", issue);

                // Load prompt values into context
                for (String pLabel : promptValues.keySet()) {
                    String cleanLabel = pLabel.replaceAll("\\[.*?\\]", "").trim();
                    executionVars.put(cleanLabel + ".value", promptValues.get(pLabel));
                    if (cleanLabel.startsWith("team.")) {
                        executionVars.put(cleanLabel, promptValues.get(pLabel));
                    }
                }

                for (WorkflowStep step : recipe.getSteps()) {
                    listener.onLog("Step: " + step.getLabel());
                    try {
                        // Evaluate Condition
                        if (!evaluateCondition(step, issue)) {
                            String msg = "Condition not met (" + step.getConditionToken() + " " + step.getConditionOperator() + " " + step.getConditionValue() + "). Skipping step.";
                            listener.onLog("  > " + msg);
                            result.logEntries.add("SKIPPED: " + step.getLabel() + " (" + msg + ")");
                            continue;
                        }

                        step.validate(); // Client-side structure validation
                        executeStep(step, issue, promptValues);
                        result.logEntries.add("COMPLETED: " + step.getLabel());
                    } catch (Exception ex) {
                        String errorMsg = "Error in step '" + step.getLabel() + "': " + ex.getMessage();
                        listener.onLog("  > " + errorMsg);
                        result.errors.add(errorMsg);
                        if (verboseLogging) ex.printStackTrace();
                        
                        if (!dryRun) {
                            issueFailed = true;
                            break;
                        }
                    }
                }
                
                ExecutionResult finalRes = new ExecutionResult(key, issueFailed ? "FAILED" : (dryRun ? "SKIPPED" : "SUCCESS"), System.currentTimeMillis() - start);
                finalRes.logEntries.addAll(result.logEntries);
                finalRes.errors.addAll(result.errors);
                results.add(finalRes);
            }
            if (!suppressSummaryLogging) {
                listener.onLog(mode + " Workflow Execution Complete.");
                listener.onComplete();
            }
        } catch (Exception e) {
            listener.onError("Fatal error during workflow execution", e);
        }
        return results;
    }

    private boolean evaluateCondition(WorkflowStep step, JSONObject issue) {
        String token = step.getConditionToken();
        String op = step.getConditionOperator();
        String expected = step.getConditionValue();

        if (token == null || token.trim().isEmpty() || op == null || op.equals("ALWAYS")) {
            return true;
        }

        String actual = resolveTokens(token, issue);
        if (actual == null) actual = "";
        if (expected == null) expected = "";

        switch (op.toUpperCase()) {
            case "EQUALS": return actual.equalsIgnoreCase(expected);
            case "NOT_EQUALS": return !actual.equalsIgnoreCase(expected);
            case "CONTAINS": return actual.toLowerCase().contains(expected.toLowerCase());
            case "NOT_CONTAINS": return !actual.toLowerCase().contains(expected.toLowerCase());
            case "EMPTY": return actual.trim().isEmpty();
            case "NOT_EMPTY": return !actual.trim().isEmpty();
            default: return true;
        }
    }

    private void executeStep(WorkflowStep step, JSONObject issue, Map<String, String> prompts) throws Exception {
        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            String proj = resolveStepProperty(cs.getProjectKey(), "Project (" + step.getLabel() + ")", prompts, issue);
            String type = resolveStepProperty(cs.getIssueType(), "Issue Type (" + step.getLabel() + ")", prompts, issue);
            
            JSONObject fields = buildFields(step, issue, prompts);
            fields.put("project", new JSONObject().put("key", proj));
            fields.put("issuetype", new JSONObject().put("name", type));

            String parentLog = "";
            if (cs.getParentIssueKey() != null && !cs.getParentIssueKey().trim().isEmpty()) {
                String parentKey = resolveStepProperty(cs.getParentIssueKey(), "Parent Issue (" + step.getLabel() + ")", prompts, issue);
                if (parentKey != null && !parentKey.trim().isEmpty()) {
                    fields.put("parent", new JSONObject().put("key", parentKey));
                    parentLog = " under parent " + parentKey;
                }
            }

            if (dryRun) {
                listener.onLog("  > [DRY RUN] Would create issue in " + proj + " (" + type + ")" + parentLog);
                listener.onLog("  > [DRY RUN] Fields: " + fields.keySet());
                // Mock last_key for subsequent steps in dry run
                String mockKey = proj + "-MOCK";
                executionVars.put("last_key", mockKey);
                executionVars.put("last.key", mockKey);
                executionVars.put("last.id", "10000");
            } else {
                if (verboseLogging) listener.onLog("  > Creating issue in " + proj + " (" + type + ")" + parentLog + "...");
                JSONObject respJson = issueService.createIssue(fields);
                String newKey = respJson.getString("key");
                executionVars.put("last_key", newKey);
                executionVars.put("last.key", newKey);
                executionVars.put("last.id", respJson.getString("id"));
                jsonContexts.put("last", respJson);
                listener.onLog("  > Created " + newKey);
            }
        } else if (step instanceof UpdateStep) {
            UpdateStep us = (UpdateStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(us.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) return;
            
            JSONObject fields = buildFields(step, issue, prompts);
            
            if (dryRun) {
                listener.onLog("  > [DRY RUN] Would update " + targetKey);
                listener.onLog("  > [DRY RUN] Fields to update: " + fields.keySet());
                executionVars.put("last_key", targetKey);
                executionVars.put("last.key", targetKey);
            } else {
                if (verboseLogging) listener.onLog("  > Updating " + targetKey + "...");
                issueService.updateIssue(targetKey, fields);
                executionVars.put("last_key", targetKey);
                executionVars.put("last.key", targetKey);
                listener.onLog("  > Updated " + targetKey);
            }
        } else if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(ts.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) return;

            String targetStatus = ts.getTargetStatus();
            String promptLabel = "Transition (" + step.getLabel() + ")";
            if (prompts != null && prompts.containsKey(promptLabel)) {
                targetStatus = prompts.get(promptLabel);
            }

            String transUrl = baseUrl + "/rest/api/2/issue/" + targetKey + "/transitions";
            String transMeta = apiService.executeRequest(transUrl, "GET", null);
            String tid = JiraUtils.findTransitionIdByName(transMeta, targetStatus);
            
            if (tid != null) {
                JSONObject fields = buildFields(step, issue, prompts);
                if (dryRun) {
                    listener.onLog("  > [DRY RUN] Transition '" + targetStatus + "' (ID: " + tid + ") IS AVAILABLE for " + targetKey);
                    if (fields.length() > 0) listener.onLog("  > [DRY RUN] Would set fields: " + fields.keySet());
                } else {
                    if (verboseLogging) listener.onLog("  > Transitioning " + targetKey + " to " + targetStatus + "...");
                    issueService.transitionIssue(targetKey, targetStatus, fields);
                    listener.onLog("  > Transitioned " + targetKey + " to " + targetStatus);
                }
                executionVars.put("last_key", targetKey);
                executionVars.put("last.key", targetKey);
            } else {
                throw new Exception("Transition '" + targetStatus + "' not found on " + targetKey);
            }
        } else if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            for (LinkAction la : ls.getLinkActions()) {
                String inward = JiraUtils.cleanIssueKey(resolveTokens(la.getInwardIssueToken(), issue));
                if (inward == null || inward.trim().isEmpty()) continue;

                if (la.isRemote()) {
                    String resolvedUrl = resolveTokens(la.getUrl(), issue);
                    if (dryRun) {
                        listener.onLog("  > [DRY RUN] Would remote link " + inward + " to " + resolvedUrl);
                    } else {
                        String resolvedTitle = resolveTokens(la.getTitle(), issue);
                        String resolvedSummary = resolveTokens(la.getSummary(), issue);
                        String resolvedRel = resolveTokens(la.getRelationship(), issue);

                        JSONObject remoteObj = new JSONObject();
                        remoteObj.put("url", resolvedUrl);
                        remoteObj.put("title", resolvedTitle);
                        if (resolvedSummary != null && !resolvedSummary.isEmpty()) remoteObj.put("summary", resolvedSummary);

                        JSONObject body = new JSONObject();
                        body.put("object", remoteObj);
                        body.put("relationship", resolvedRel);

                        apiService.executeRequest(baseUrl + "/rest/api/2/issue/" + inward + "/remotelink", "POST", body.toString(4));
                        listener.onLog("  > Remote Linked " + inward + " to " + resolvedUrl);
                    }
                } else {
                    String outward = JiraUtils.cleanIssueKey(resolveTokens(la.getOutwardIssueToken(), issue));
                    if (outward == null || outward.trim().isEmpty()) continue;
                    
                    if (dryRun) {
                        listener.onLog("  > [DRY RUN] Would link " + inward + " to " + outward + " (" + la.getLinkType() + ")");
                    } else {
                        issueService.linkIssues(inward, outward, la.getLinkType());
                        listener.onLog("  > Linked " + inward + " to " + outward + " (" + la.getLinkType() + ")");
                    }
                }
            }
        } else if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            String srcKey = JiraUtils.cleanIssueKey(resolveTokens(as.getSourceIssueToken(), issue));
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(as.getTargetIssueToken(), issue));

            if (srcKey == null || srcKey.trim().isEmpty() || targetKey == null || targetKey.trim().isEmpty()) return;

            if (dryRun) {
                listener.onLog("  > [DRY RUN] Would copy assets from " + srcKey + " to " + targetKey);
                return;
            }

            JSONObject sourceData = issue;
            if (!srcKey.equals(issue.getString("key"))) {
                String url = baseUrl + "/rest/api/2/issue/" + srcKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                String srcResp = apiService.executeRequest(url, "GET", null);
                sourceData = new JSONObject(srcResp);
            }

            boolean doAtt = as.isCopyAttachments(), doLinks = as.isCopyLinks(), doSub = as.isCopySubTasks();
            String csvFields = as.getSubTaskFields();
            if (as.isPromptOptions()) {
                String p = prompts.get("Asset Options (" + step.getLabel() + ")");
                if (p != null && p.contains(",")) {
                    String[] parts = p.split(",");
                    if (parts.length >= 3) {
                        doAtt = Boolean.parseBoolean(parts[0]);
                        doLinks = Boolean.parseBoolean(parts[1]);
                        doSub = Boolean.parseBoolean(parts[2]);
                        if (parts.length >= 4) {
                            csvFields = parts[3];
                        }
                    }
                }
            }

            if (doAtt) copyAttachments(sourceData, targetKey);
            if (doLinks) copyLinks(sourceData, targetKey);
            if (doSub) copySubTasks(sourceData, targetKey, csvFields);
        } else if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(ws.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) return;

            String timeSpentPromptKey = "Time Spent (" + step.getLabel() + ")";
            String commentPromptKey = "Comment (" + step.getLabel() + ")";
            String startedPromptKey = "Started (" + step.getLabel() + ")";

            if (ws.isPromptAtRuntime() && ws.isPromptPerIssue()) {
                timeSpentPromptKey = "Time Spent (" + step.getLabel() + ") for " + targetKey;
                commentPromptKey = "Comment (" + step.getLabel() + ") for " + targetKey;
                startedPromptKey = "Started (" + step.getLabel() + ") for " + targetKey;
            }

            String timeSpent = resolveStepProperty(ws.getTimeSpent(), timeSpentPromptKey, prompts, issue);
            String comment = resolveStepProperty(ws.getComment(), commentPromptKey, prompts, issue);
            String started = resolveStepProperty(ws.getStarted(), startedPromptKey, prompts, issue);
            
            if (dryRun) {
                listener.onLog("  > [DRY RUN] Would add " + timeSpent + " worklog to " + targetKey + (comment != null && !comment.trim().isEmpty() ? " with comment: \"" + comment + "\"" : ""));
            } else {
                started = resolveTokens(started, issue);
                started = JiraUtils.formatJiraDateTime(started);

                JSONObject body = new JSONObject();
                if (timeSpent != null && !timeSpent.trim().isEmpty()) body.put("timeSpent", timeSpent);
                if (comment != null && !comment.trim().isEmpty()) body.put("comment", comment);
                if (started != null && !started.trim().isEmpty()) body.put("started", started);

                apiService.executeRequest(baseUrl + "/rest/api/2/issue/" + targetKey + "/worklog", "POST", body.toString(4));
                listener.onLog("  > Added Worklog to " + targetKey + " (" + timeSpent + ")");
            }
        } else if (step instanceof AttachmentStep) {
            AttachmentStep as = (AttachmentStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(as.getTargetIssueToken(), issue));
            if (targetKey != null && !targetKey.trim().isEmpty()) {
                String filePath = "";
                if (as.isPromptAtRuntime()) {
                    String promptLabel = "Attachment File (" + step.getLabel() + ")";
                    if (prompts != null && prompts.containsKey(promptLabel)) {
                        filePath = prompts.get(promptLabel);
                    }
                } else {
                    filePath = resolveTokens(as.getFilePath(), issue);
                }

                if (filePath == null || filePath.trim().isEmpty()) {
                    throw new Exception("Attachment Step: No file selected or file path is empty.");
                }

                java.io.File file = new java.io.File(filePath);
                if (!dryRun && !file.exists()) {
                    throw new Exception("Attachment Step: File not found: " + file.getAbsolutePath());
                }

                if (dryRun) {
                    listener.onLog("  > [DRY RUN] Would upload attachment '" + file.getName() + "' to " + targetKey);
                } else {
                    if (verboseLogging) listener.onLog("  > Uploading attachment '" + file.getName() + "' to " + targetKey + "...");
                    apiService.uploadAttachment(baseUrl + "/rest/api/2/issue/" + targetKey + "/attachments", file, file.getName());
                    listener.onLog("  > Uploaded attachment '" + file.getName() + "' to " + targetKey);
                }
            }
        } else if (step instanceof CommentStep) {
            CommentStep cs = (CommentStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(cs.getTargetIssueToken(), issue));
            if (targetKey != null && !targetKey.trim().isEmpty()) {
                String commentText = "";
                if (cs.isPromptAtRuntime()) {
                    if (cs.isPromptPerIssue()) {
                        String perIssueLabel = "Comment (" + step.getLabel() + ") for " + targetKey;
                        if (prompts != null && prompts.containsKey(perIssueLabel)) {
                            commentText = prompts.get(perIssueLabel);
                        } else {
                            commentText = resolveTokens(cs.getCommentBody(), issue);
                        }
                    } else {
                        String promptLabel = "Comment (" + step.getLabel() + ")";
                        if (prompts != null && prompts.containsKey(promptLabel)) {
                            commentText = prompts.get(promptLabel);
                            commentText = resolveTokens(commentText, issue);
                        } else {
                            commentText = resolveTokens(cs.getCommentBody(), issue);
                        }
                    }
                } else {
                    commentText = resolveTokens(cs.getCommentBody(), issue);
                }

                if (commentText == null || commentText.trim().isEmpty()) {
                    throw new Exception("Comment Step: Comment body is empty.");
                }

                if (dryRun) {
                    listener.onLog("  > [DRY RUN] Would add comment to " + targetKey + ": \"" + commentText + "\"");
                } else {
                    if (verboseLogging) listener.onLog("  > Adding comment to " + targetKey + "...");
                    issueService.addComment(targetKey, commentText);
                    listener.onLog("  > Added comment to " + targetKey);
                }
            }
        } else if (step instanceof NotifyStep) {
            NotifyStep ns = (NotifyStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(ns.getTargetIssueToken(), issue));
            if (targetKey != null && !targetKey.trim().isEmpty()) {
                String sub = "";
                String bodyText = "";
                String usersStr = "";
                String groupsStr = "";

                if (ns.isPromptAtRuntime()) {
                    if (ns.isPromptPerIssue()) {
                        String subLabel = "Subject (" + step.getLabel() + ") for " + targetKey;
                        String bodyLabel = "Body (" + step.getLabel() + ") for " + targetKey;
                        String usersLabel = "To Users (" + step.getLabel() + ") for " + targetKey;
                        String groupsLabel = "To Groups (" + step.getLabel() + ") for " + targetKey;

                        sub = prompts.containsKey(subLabel) ? prompts.get(subLabel) : resolveTokens(ns.getSubject(), issue);
                        bodyText = prompts.containsKey(bodyLabel) ? prompts.get(bodyLabel) : resolveTokens(ns.getTextBody(), issue);
                        usersStr = prompts.containsKey(usersLabel) ? prompts.get(usersLabel) : resolveTokens(ns.getToUsers(), issue);
                        groupsStr = prompts.containsKey(groupsLabel) ? prompts.get(groupsLabel) : resolveTokens(ns.getToGroups(), issue);
                    } else {
                        String subLabel = "Subject (" + step.getLabel() + ")";
                        String bodyLabel = "Body (" + step.getLabel() + ")";
                        String usersLabel = "To Users (" + step.getLabel() + ")";
                        String groupsLabel = "To Groups (" + step.getLabel() + ")";

                        sub = prompts.containsKey(subLabel) ? prompts.get(subLabel) : resolveTokens(ns.getSubject(), issue);
                        bodyText = prompts.containsKey(bodyLabel) ? prompts.get(bodyLabel) : resolveTokens(ns.getTextBody(), issue);
                        usersStr = prompts.containsKey(usersLabel) ? prompts.get(usersLabel) : resolveTokens(ns.getToUsers(), issue);
                        groupsStr = prompts.containsKey(groupsLabel) ? prompts.get(groupsLabel) : resolveTokens(ns.getToGroups(), issue);

                        // Resolve tokens since it was prompted once for the batch but can contain issue-specific tokens
                        sub = resolveTokens(sub, issue);
                        bodyText = resolveTokens(bodyText, issue);
                        usersStr = resolveTokens(usersStr, issue);
                        groupsStr = resolveTokens(groupsStr, issue);
                    }
                } else {
                    sub = resolveTokens(ns.getSubject(), issue);
                    bodyText = resolveTokens(ns.getTextBody(), issue);
                    usersStr = resolveTokens(ns.getToUsers(), issue);
                    groupsStr = resolveTokens(ns.getToGroups(), issue);
                }

                if (sub == null) sub = "";
                if (bodyText == null) bodyText = "";

                if (dryRun) {
                    listener.onLog("  > [DRY RUN] Would send Jira notification for " + targetKey + " with Subject: \"" + sub + "\"");
                } else {
                    if (verboseLogging) listener.onLog("  > Formulating notification for " + targetKey + "...");

                    JSONObject notifyPayload = new JSONObject();
                    notifyPayload.put("subject", sub);
                    notifyPayload.put("textBody", bodyText);

                    JSONObject toObj = new JSONObject();
                    toObj.put("assignee", ns.isToAssignee());
                    toObj.put("reporter", ns.isToReporter());
                    toObj.put("watchers", ns.isToWatchers());
                    toObj.put("voters", ns.isToVoters());

                    // Custom Users (with custom team support)
                    JSONArray usersArr = new JSONArray();
                    if (usersStr != null && !usersStr.trim().isEmpty()) {
                        for (String user : usersStr.split("\\s*,\\s*")) {
                            String u = user.trim();
                            if (u.isEmpty()) continue;

                            String teamKey = u;
                            if (teamKey.startsWith("@")) teamKey = teamKey.substring(1);
                            if (teamKey.startsWith("team.")) teamKey = teamKey.substring(5);

                            String members = (issueService != null && issueService.getJiraConfig() != null)
                                    ? issueService.getJiraConfig().getTeamProperty(teamKey, "members")
                                    : null;
                            if (members != null && !members.trim().isEmpty()) {
                                for (String m : members.split(",")) {
                                    String memberTrimmed = m.trim();
                                    if (!memberTrimmed.isEmpty()) {
                                        usersArr.put(new JSONObject().put("name", memberTrimmed));
                                    }
                                }
                            } else {
                                usersArr.put(new JSONObject().put("name", u));
                            }
                        }
                    }
                    if (usersArr.length() > 0) {
                        toObj.put("users", usersArr);
                    }

                    // Custom Groups
                    JSONArray groupsArr = new JSONArray();
                    if (groupsStr != null && !groupsStr.trim().isEmpty()) {
                        for (String grp : groupsStr.split("\\s*,\\s*")) {
                            String g = grp.trim();
                            if (!g.isEmpty()) {
                                groupsArr.put(new JSONObject().put("name", g));
                            }
                        }
                    }
                    if (groupsArr.length() > 0) {
                        toObj.put("groups", groupsArr);
                    }

                    notifyPayload.put("to", toObj);

                    String notifyUrl = baseUrl + "/rest/api/2/issue/" + targetKey + "/notify";
                    try {
                        apiService.executeRequest(notifyUrl, "POST", notifyPayload.toString());
                        listener.onLog("  > Sent Notification for " + targetKey + " (Subject: " + sub + ")");
                    } catch (Exception ex) {
                        listener.onLog("  > [WARNING] Notification failed for " + targetKey + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    private String resolveStepProperty(String value, String promptLabel, Map<String, String> prompts, JSONObject issue) {
        if (value == null) return "";
        if (prompts != null && prompts.containsKey(promptLabel)) {
            return prompts.get(promptLabel);
        }
        if (value.contains("{{")) return resolveTokens(value, issue);
        return value;
    }

    private void copySubTasks(JSONObject sourceIssue, String targetParentKey, String csvFields) throws Exception {
        String sourceKey = sourceIssue.getString("key");
        String subtaskUrl = baseUrl + "/rest/api/2/issue/" + sourceKey + "/subtask";
        String subtaskResp = apiService.executeRequest(subtaskUrl, "GET", null);
        JSONArray subtasks;
        
        if (subtaskResp.trim().startsWith("[")) subtasks = new JSONArray(subtaskResp);
        else {
            JSONObject respObj = new JSONObject(subtaskResp);
            subtasks = respObj.optJSONArray("subtasks");
            if (subtasks == null) return;
        }

        for (int i = 0; i < subtasks.length(); i++) {
            JSONObject subData = subtasks.getJSONObject(i);
            String subKey = subData.getString("key");
            JSONObject subFields = subData.getJSONObject("fields");
            
            JSONObject newSubFields = new JSONObject();
            newSubFields.put("project", new JSONObject().put("key", targetParentKey.split("-")[0]));
            newSubFields.put("parent", new JSONObject().put("key", targetParentKey));
            newSubFields.put("issuetype", subFields.getJSONObject("issuetype"));
            
            String csv = csvFields;
            if (csv != null && !csv.trim().isEmpty()) copySubTaskFields(subFields, newSubFields, csv.split(","));

            try {
                issueService.createIssue(newSubFields);
                listener.onLog("    > Created sub-task copy: " + subKey);
            } catch (Exception e) {
                listener.onLog("    > Error copying sub-task " + subKey + ": " + e.getMessage());
            }
        }
    }

    private void copySubTaskFields(JSONObject src, JSONObject dest, String[] fields) {
        for (String f : fields) {
            String fieldId = f.trim();
            if (src.has(fieldId) && !src.isNull(fieldId)) {
                Object val = src.get(fieldId);
                if (val instanceof JSONObject) {
                    JSONObject obj = (JSONObject) val;
                    if (fieldId.equals("reporter") || fieldId.equals("assignee")) {
                        if (obj.has("name")) dest.put(fieldId, new JSONObject().put("name", obj.getString("name")));
                    } else if (obj.has("id")) dest.put(fieldId, new JSONObject().put("id", obj.getString("id")));
                    else if (obj.has("value")) dest.put(fieldId, new JSONObject().put("value", obj.getString("value")));
                    else dest.put(fieldId, val);
                } else dest.put(fieldId, val);
            }
        }
        for (String key : src.keySet()) {
            if (key.startsWith("customfield_") && !src.isNull(key)) {
                boolean skip = false;
                for(String f : fields) if(f.trim().equals(key)) skip = true;
                if(!skip) dest.put(key, src.get(key));
            }
        }
    }

    private void copyAttachments(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("attachment")) return;
        JSONArray attachments = sourceIssue.getJSONObject("fields").getJSONArray("attachment");
        for (int i = 0; i < attachments.length(); i++) {
            JSONObject att = attachments.getJSONObject(i);
            String filename = att.getString("filename");
            java.io.File tempFile = apiService.downloadAttachmentToTempFile(att.getString("content"), filename);
            try {
                apiService.uploadAttachment(baseUrl + "/rest/api/2/issue/" + targetKey + "/attachments", tempFile, filename);
                listener.onLog("  > Copied attachment: " + filename);
            } finally { if (tempFile != null) tempFile.delete(); }
        }
    }

    private void copyLinks(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("issuelinks")) return;
        JSONArray links = sourceIssue.getJSONObject("fields").getJSONArray("issuelinks");
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            String typeName = link.getJSONObject("type").getString("name");
            String otherKey = link.has("inwardIssue") ? link.getJSONObject("inwardIssue").getString("key") : (link.has("outwardIssue") ? link.getJSONObject("outwardIssue").getString("key") : null);
            if (otherKey == null) continue;

            try { 
                issueService.linkIssues(targetKey, otherKey, typeName);
                listener.onLog("  > Copied link: " + typeName + " to " + otherKey); 
            }
            catch (Exception e) { listener.onLog("  > Warning: Could not copy link to " + otherKey); }
        }
    }

    private JSONObject buildFields(WorkflowStep step, JSONObject issue, Map<String, String> prompts) {
        JSONObject fields = new JSONObject();
        for (FieldAction fa : step.getFieldActions().values()) {
            if ("teams_selection".equalsIgnoreCase(fa.getFieldId())) continue;
            String val = resolveValue(fa, issue, prompts);
            String fieldId = fa.getFieldId();
            if (fieldId != null && fieldId.contains("{{")) {
                fieldId = resolveTokens(fieldId, issue);
            }
            if (val == null || val.equalsIgnoreCase("null")) { fields.put(fieldId, JSONObject.NULL); continue; }

            String trimmed = val.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try { if (trimmed.startsWith("{")) fields.put(fieldId, new JSONObject(trimmed)); else fields.put(fieldId, new JSONArray(trimmed)); continue; }
                catch (Exception ignored) {}
            }

            JSONObject fieldMeta = metadataService != null ? metadataService.getFieldMetadata(fieldId) : null;
            boolean isArray = (fieldMeta != null && fieldMeta.has("schema") && "array".equals(fieldMeta.getJSONObject("schema").optString("type"))) || (fieldId.equals("labels") || fieldId.equals("components") || fieldId.equals("fixVersions") || fieldId.equals("versions"));

            if (isArray) {
                JSONArray arr = new JSONArray();
                for (String p : val.split(",")) arr.put(wrapSingleValue(fieldId, p.trim(), fieldMeta));
                fields.put(fieldId, arr);
            } else fields.put(fieldId, wrapSingleValue(fieldId, val, fieldMeta));
        }
        return fields;
    }

    private Object wrapSingleValue(String fieldId, String val, JSONObject fieldMeta) {
        if (val == null || val.equalsIgnoreCase("null") || val.trim().isEmpty()) {
            return JSONObject.NULL;
        }

        // 1. Determine the field's "semantic type" primarily from metadata.
        String semanticType = null;
        String source = "METADATA";
        if (fieldMeta != null && fieldMeta.has("schema")) {
            JSONObject schema = fieldMeta.getJSONObject("schema");
            semanticType = "array".equals(schema.optString("type"))
                    ? schema.optString("items")
                    : schema.optString("type");
        }

        // 2. If metadata is missing, fall back to guessing based on the field ID (safety net).
        if (semanticType == null || semanticType.trim().isEmpty()) {
            source = "FALLBACK";
            if (fieldId.equals("assignee") || fieldId.equals("reporter") || fieldId.contains("user") || fieldId.contains("owner")) semanticType = "user";
            else if (fieldId.equals("priority") || fieldId.equals("resolution") || fieldId.startsWith("customfield_")) semanticType = "option";
            else if (fieldId.equals("labels")) semanticType = "string";
            else if (fieldId.equals("fixVersions") || fieldId.equals("versions") || fieldId.contains("Version")) semanticType = "version";
            else if (fieldId.equals("components")) semanticType = "component";
        }

        if (verboseLogging && listener != null) {
            listener.onLog("    > Field [" + fieldId + "] wrapping value [" + val + "] using " + source + " (" + (semanticType != null ? semanticType : "UNKNOWN") + ")");
        }

        // 3. Wrap the value based on the determined semantic type.
        if (semanticType != null) {
            switch (semanticType.toLowerCase()) {
                case "user":
                    return new JSONObject().put("name", val);
                case "priority":
                case "resolution":
                case "status":
                    return new JSONObject().put("name", val);
                case "option":
                    return new JSONObject().put("value", val);
                case "component":
                case "version":
                case "project":
                case "issuetype":
                    return new JSONObject().put("name", val);
                case "number":
                    try {
                        if (val.contains(".")) return Double.parseDouble(val);
                        return Long.parseLong(val);
                    } catch (NumberFormatException e) {
                        return val;
                    }
                case "string":
                case "date":
                case "datetime":
                case "sd-request-type":
                case "service-desk-request-type":
                    return val;
            }
        }

        // Final fallback: if no type could be determined, check for number, otherwise return raw value.
        if (val.matches("-?\\d+(\\.\\d+)?")) {
            try {
                if (val.contains(".")) return Double.parseDouble(val);
                return Long.parseLong(val);
            } catch (Exception ignored) {}
        }
        return val;
    }

    private String resolveTokens(String input, JSONObject issue) {
        jsonContexts.put("issue", issue);
        return TokenEngine.replaceTokens(input, jsonContexts, executionVars);
    }

    private String resolveValue(FieldAction fa, JSONObject issue, Map<String, String> prompts) {
        String val = "";
        if (fa.getMode() == FieldAction.MappingMode.SET) {
            val = resolveTokens(fa.getValue().toString(), issue);
        } else if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
            String label = fa.getPromptLabel(), clean = label.replaceAll("\\[.*?\\]", "").trim();
            if (prompts.containsKey(clean)) val = prompts.get(clean);
        }
        if (val != null) {
            val = val.replace("\\n", "\n").replace("\\r", "\r");
        }
        return val;
    }
}
