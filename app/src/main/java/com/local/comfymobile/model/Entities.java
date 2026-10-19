package com.local.comfymobile.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Entities {
    private Entities() {}

    public static final class Session {
        public final String token;
        public final String username;
        public final List<ComfyInstance> instances;

        public Session(JSONObject json) {
            token = json.optString("token");
            username = json.optString("username");
            instances = ComfyInstance.list(json.optJSONArray("comfyInstances"));
        }
    }

    public static final class ComfyInstance {
        public final String url;
        public final String label;
        public final boolean online;

        public ComfyInstance(JSONObject json) {
            url = json.optString("url");
            label = json.optString("label", url);
            online = json.optBoolean("online");
        }

        static List<ComfyInstance> list(JSONArray array) {
            List<ComfyInstance> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new ComfyInstance(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class WorkflowSummary {
        public final String id;
        public final String name;
        public final String relativePath;
        public final int nodeCount;
        public final long modifiedAt;

        public WorkflowSummary(JSONObject json) {
            id = json.optString("id");
            name = json.optString("name");
            relativePath = json.optString("relativePath");
            nodeCount = json.optInt("nodeCount");
            modifiedAt = json.optLong("modifiedAt");
        }

        public static List<WorkflowSummary> list(JSONArray array) {
            List<WorkflowSummary> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new WorkflowSummary(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class WorkflowDetail {
        public final WorkflowSummary workflow;
        public final String comfyUrl;
        public final List<WorkflowInput> inputs;

        public WorkflowDetail(JSONObject json) {
            workflow = new WorkflowSummary(json.optJSONObject("workflow"));
            comfyUrl = json.optString("comfyUrl");
            inputs = WorkflowInput.list(json.optJSONArray("inputs"));
        }
    }

    public static final class WorkflowInput {
        public final String key;
        public final String nodeId;
        public final String nodeTitle;
        public final String nodeType;
        public final String name;
        public final String label;
        public final String kind;
        public final Object value;
        public final List<Object> choices;
        public final Double min;
        public final Double max;
        public final Double step;
        public final String mime;

        public WorkflowInput(JSONObject json) {
            key = json.optString("key");
            nodeId = json.optString("nodeId");
            nodeTitle = json.optString("nodeTitle");
            nodeType = json.optString("nodeType");
            name = json.optString("name");
            label = json.optString("label", name);
            kind = json.optString("kind", "TEXT");
            value = json.isNull("value") ? null : json.opt("value");
            choices = new ArrayList<>();
            JSONArray values = json.optJSONArray("choices");
            if (values != null) for (int i = 0; i < values.length(); i++) choices.add(values.opt(i));
            min = json.has("min") ? json.optDouble("min") : null;
            max = json.has("max") ? json.optDouble("max") : null;
            step = json.has("step") ? json.optDouble("step") : null;
            mime = json.optString("mime", "*/*");
        }

        static List<WorkflowInput> list(JSONArray array) {
            List<WorkflowInput> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new WorkflowInput(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class JobInfo {
        public final String id;
        public final String workflowName;
        public final String status;
        public final String error;
        public final long createdAt;
        public final List<Artifact> artifacts;

        public JobInfo(JSONObject json) {
            id = json.optString("id");
            workflowName = json.optString("workflowName");
            status = json.optString("status");
            error = json.optString("error");
            createdAt = json.optLong("createdAt");
            artifacts = Artifact.list(json.optJSONArray("artifacts"));
        }

        public static List<JobInfo> list(JSONArray array) {
            List<JobInfo> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new JobInfo(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class Artifact {
        public final String jobId;
        public final int index;
        public final String filename;
        public final String mimeType;
        public final String url;

        public Artifact(JSONObject json) {
            jobId = json.optString("jobId");
            index = json.optInt("index");
            filename = json.optString("filename");
            mimeType = json.optString("mimeType", "application/octet-stream");
            url = json.optString("url");
        }

        static List<Artifact> list(JSONArray array) {
            List<Artifact> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new Artifact(array.optJSONObject(i)));
            return result;
        }
    }
}
