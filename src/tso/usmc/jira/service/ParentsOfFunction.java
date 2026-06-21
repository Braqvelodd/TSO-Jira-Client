package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Concrete strategy that resolves standard parents of issues matching an inner JQL query.
 * For example, "parentsOf('status = Done')" queries all Done issues and extracts their parent issue keys.
 */
public class ParentsOfFunction implements CustomJqlFunction {

    @Override
    public String getFunctionName() {
        return "parentsOf";
    }

    @Override
    public String extractInnerJql(String fullQuery) {
        return parseInnerJql(fullQuery);
    }

    @Override
    public List<String> getFirstPassFields() {
        return Collections.singletonList("parent");
    }

    @Override
    public String buildFinalJql(JSONObject firstPassResponse) {
        List<String> parentKeys = new ArrayList<>();
        if (firstPassResponse != null && firstPassResponse.has("issues")) {
            JSONArray issues = firstPassResponse.getJSONArray("issues");
            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                if (issue.has("fields")) {
                    JSONObject fields = issue.getJSONObject("fields");
                    if (fields.has("parent") && !fields.isNull("parent")) {
                        JSONObject parent = fields.getJSONObject("parent");
                        if (parent.has("key")) {
                            parentKeys.add(parent.getString("key"));
                        }
                    }
                }
            }
        }
        return CustomJqlFunction.buildChunkedKeyInQuery(parentKeys);
    }
}
