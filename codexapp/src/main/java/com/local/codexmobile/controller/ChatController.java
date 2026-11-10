package com.local.codexmobile.controller;

import com.local.codexmobile.model.Entities.ThreadDetail;
import com.local.codexmobile.model.Repository;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChatController {
    public interface View {
        void setLoading(boolean value); void showThread(ThreadDetail thread); void showError(String message);
        void appendDelta(String delta); void clearStreaming(); void setStatus(String status);
        void requestApproval(String requestId, String title, String detail);
    }
    private final Repository repository; private final View view; private final String threadId;
    private boolean polling; private long lastSeq; private String activeTurn = "";
    public ChatController(Repository repository, View view, String threadId) { this.repository = repository; this.view = view; this.threadId = threadId; }
    public void start() { polling = true; load(); poll(); }
    public void stop() { polling = false; }
    public void load() {
        view.setLoading(true); repository.thread(threadId, new Repository.Callback<>() {
            @Override public void onSuccess(ThreadDetail value) { activeTurn = value.activeTurnId; view.setLoading(false); view.showThread(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
    public void send(String text) {
        if (text.trim().isEmpty()) return;
        view.setStatus("正在提交…"); view.clearStreaming();
        repository.send(threadId, text, new Repository.Callback<>() {
            @Override public void onSuccess(String value) { activeTurn = value; view.setStatus("Codex 正在工作"); load(); }
            @Override public void onError(String message) { view.showError(message); }
        });
    }
    public void interrupt() {
        if (activeTurn.isEmpty()) { view.showError("当前没有运行中的任务"); return; }
        repository.interrupt(threadId, activeTurn, new Repository.Callback<>() {
            @Override public void onSuccess(Void value) { view.setStatus("已停止"); load(); }
            @Override public void onError(String message) { view.showError(message); }
        });
    }
    public void answerApproval(String requestId, boolean accept) {
        repository.approve(requestId, accept, new Repository.Callback<>() {
            @Override public void onSuccess(Void value) { view.setStatus(accept ? "已批准" : "已拒绝"); }
            @Override public void onError(String message) { view.showError(message); }
        });
    }
    private void poll() {
        if (!polling) return;
        repository.events(lastSeq, new Repository.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                JSONArray events = value.optJSONArray("events");
                if (events != null) for (int i = 0; i < events.length(); i++) handle(events.optJSONObject(i));
                lastSeq = value.optLong("lastSeq", lastSeq); poll();
            }
            @Override public void onError(String message) { if (polling) { view.setStatus("事件连接重试中"); poll(); } }
        });
    }
    private void handle(JSONObject event) {
        if (event == null) return;
        String method = event.optString("method"); JSONObject params = event.optJSONObject("params");
        if (params == null) return;
        if ("approval/requested".equals(method)) {
            JSONObject original = params.optJSONObject("params");
            if (original == null || !threadId.equals(original.optString("threadId"))) return;
            String detail = original.optString("command", original.optString("reason", original.toString()));
            view.requestApproval(params.optString("requestId"), params.optString("method"), detail); return;
        }
        if (!threadId.equals(params.optString("threadId"))) return;
        if ("item/agentMessage/delta".equals(method)) view.appendDelta(params.optString("delta"));
        else if ("turn/started".equals(method)) { JSONObject turn = params.optJSONObject("turn"); if (turn != null) activeTurn = turn.optString("id"); view.setStatus("Codex 正在工作"); }
        else if ("turn/completed".equals(method)) { activeTurn = ""; view.setStatus("已完成"); view.clearStreaming(); load(); }
    }
}
