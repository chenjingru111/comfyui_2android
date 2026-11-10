package com.local.codexmobile.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Entities {
    private Entities() {}

    public static final class ThreadSummary {
        public final String id, name, preview, cwd, status;
        public final long updatedAt;
        public ThreadSummary(JSONObject json) {
            id = json.optString("id");
            preview = json.optString("preview");
            String rawName = json.isNull("name") ? "" : json.optString("name");
            name = rawName.isEmpty() ? firstLine(preview, "未命名对话") : rawName;
            cwd = json.optString("cwd");
            JSONObject statusObject = json.optJSONObject("status");
            status = statusObject == null ? json.optString("status") : statusObject.optString("type");
            updatedAt = json.optLong("updatedAt");
        }
        public static List<ThreadSummary> list(JSONArray array) {
            List<ThreadSummary> result = new ArrayList<>();
            if (array != null) for (int i = 0; i < array.length(); i++) result.add(new ThreadSummary(array.optJSONObject(i)));
            return result;
        }
    }

    public static final class Message {
        public final String role, text;
        public Message(String role, String text) { this.role = role; this.text = text; }
    }

    public static final class ThreadDetail {
        public final String id, name, status;
        public final List<Message> messages = new ArrayList<>();
        public String activeTurnId = "";
        public ThreadDetail(JSONObject response) {
            JSONObject thread = response.optJSONObject("thread");
            id = thread == null ? "" : thread.optString("id");
            name = thread == null ? "Codex" : thread.optString("name", "Codex");
            status = thread == null ? "" : thread.optString("status");
            JSONArray turns = thread == null ? null : thread.optJSONArray("turns");
            if (turns == null) return;
            for (int i = 0; i < turns.length(); i++) {
                JSONObject turn = turns.optJSONObject(i);
                if (turn == null) continue;
                if (!"completed".equals(turn.optString("status")) && !"failed".equals(turn.optString("status")) && !"interrupted".equals(turn.optString("status"))) activeTurnId = turn.optString("id");
                JSONArray items = turn.optJSONArray("items");
                if (items == null) continue;
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.optJSONObject(j);
                    if (item == null) continue;
                    String type = item.optString("type");
                    if ("userMessage".equals(type)) add("user", contentText(item.optJSONArray("content")));
                    else if ("agentMessage".equals(type)) add("agent", item.optString("text"));
                    else if ("commandExecution".equals(type)) {
                        String command = item.optString("command");
                        if (!command.isEmpty()) add("tool", shorten("$ " + command + "\n" + item.optString("aggregatedOutput"), 1600));
                    }
                }
            }
        }
        private void add(String role, String text) { if (text != null && !text.trim().isEmpty()) messages.add(new Message(role, text.trim())); }
        private static String contentText(JSONArray content) {
            if (content == null) return "";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                Object value = content.opt(i);
                if (value instanceof JSONObject) {
                    String part = ((JSONObject) value).optString("text");
                    if (!part.isEmpty()) text.append(part);
                } else if (value != null) text.append(value);
            }
            return text.toString();
        }
        private static String shorten(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit) + "\n…（工具输出已截断）"; }
    }

    private static String firstLine(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String line = value.trim().split("\\R", 2)[0];
        return line.length() <= 40 ? line : line.substring(0, 40) + "…";
    }
}
