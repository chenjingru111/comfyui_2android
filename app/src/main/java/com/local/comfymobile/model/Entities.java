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

    /** ComfyUI 画布上的一个分区（用来在手机上选择“跑哪条流”） */
    public static final class WorkflowGroup {
        public final int id;
        public final String title;
        public final int nodes;
        public final boolean pipeline;
        /** 是否是 ComfyUI 面板里那条主流程（标题以“编辑”结尾） */
        public final boolean flow;
        public final boolean enabled;
        /** 这个分区里嵌套的子分区，打开外层时要一起打开 */
        public final List<Integer> children;

        public WorkflowGroup(JSONObject json) {
            id = json.optInt("id", -1);
            title = json.optString("title");
            nodes = json.optInt("nodes");
            pipeline = json.optBoolean("pipeline");
            flow = json.optBoolean("flow");
            enabled = json.optBoolean("enabled");
            children = new ArrayList<>();
            JSONArray array = json.optJSONArray("children");
            if (array != null) for (int i = 0; i < array.length(); i++) children.add(array.optInt(i));
        }

        static List<WorkflowGroup> list(JSONArray array) {
            List<WorkflowGroup> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new WorkflowGroup(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class WorkflowDetail {
        public final WorkflowSummary workflow;
        public final String comfyUrl;
        public final List<WorkflowInput> inputs;
        public final List<WorkflowGroup> groups;

        public WorkflowDetail(JSONObject json) {
            workflow = new WorkflowSummary(json.optJSONObject("workflow"));
            comfyUrl = json.optString("comfyUrl");
            inputs = WorkflowInput.list(json.optJSONArray("inputs"));
            groups = WorkflowGroup.list(json.optJSONArray("groups"));
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
        /** ComfyUI 画布上的分区标题（可为空） */
        public final String group;
        /** 分区下标，-1 表示不在任何分区里 */
        public final int groupId;
        /** false 表示该节点在画布上被旁路（不会执行） */
        public final boolean enabled;

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
            group = json.optString("group", "");
            groupId = json.optInt("groupId", -1);
            enabled = json.optBoolean("enabled", true);
        }

        static List<WorkflowInput> list(JSONArray array) {
            List<WorkflowInput> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new WorkflowInput(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class OutputEntry {
        public final String name;
        public final String path;
        public final String mime;
        public final boolean dir;
        public final long size;
        public final long modifiedAt;

        public OutputEntry(JSONObject json) {
            name = json.optString("name");
            path = json.optString("path");
            mime = json.optString("mime", "");
            dir = json.optBoolean("dir");
            size = json.optLong("size");
            modifiedAt = json.optLong("mtime");
        }

        static List<OutputEntry> list(JSONArray array) {
            List<OutputEntry> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new OutputEntry(array.optJSONObject(i)));
            return result;
        }
    }

    /** ComfyUI output 目录的一次列举结果 */
    public static final class OutputListing {
        public final String path;
        public final String parent;
        public final List<OutputEntry> entries;

        public OutputListing(JSONObject json) {
            path = json.optString("path");
            parent = json.optString("parent");
            entries = OutputEntry.list(json.optJSONArray("entries"));
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
