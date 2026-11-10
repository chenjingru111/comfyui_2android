package com.local.codexmobile.model;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.local.codexmobile.model.Entities.ThreadDetail;
import com.local.codexmobile.model.Entities.ThreadSummary;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Repository {
    public interface Callback<T> { void onSuccess(T value); void onError(String message); }
    private interface Supplier<T> { T get() throws Exception; }
    private static Repository instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionStore session;
    private final ApiClient api;
    private final VpnManager vpn;

    private Repository(Context context) {
        Context app = context.getApplicationContext();
        session = new SessionStore(app);
        api = new ApiClient(session.baseUrl(), session.token());
        vpn = new VpnManager(app);
    }
    public static synchronized Repository get(Context context) { if (instance == null) instance = new Repository(context); return instance; }
    public SessionStore session() { return session; }
    public void connectVpn(Callback<Void> callback) { run(callback, () -> { vpn.connect(); return null; }); }
    public void login(String baseUrl, String username, String password, Callback<Void> callback) {
        run(callback, () -> {
            String normalized = normalize(baseUrl);
            api.configure(normalized, "");
            JSONObject response = api.post("/api/login", new JSONObject().put("username", username).put("password", password));
            String token = response.getString("token");
            session.save(normalized, token); api.configure(normalized, token); return null;
        });
    }
    public void threads(Callback<List<ThreadSummary>> callback) { run(callback, () -> ThreadSummary.list(api.get("/api/threads").optJSONArray("data"))); }
    public void thread(String id, Callback<ThreadDetail> callback) { run(callback, () -> new ThreadDetail(api.get("/api/threads/" + id))); }
    public void createThread(String cwd, Callback<ThreadSummary> callback) {
        run(callback, () -> {
            JSONObject response = api.post("/api/threads", new JSONObject().put("cwd", cwd));
            return new ThreadSummary(response.getJSONObject("thread"));
        });
    }
    public void send(String threadId, String text, Callback<String> callback) {
        run(callback, () -> api.post("/api/threads/" + threadId + "/turns", new JSONObject().put("text", text)).getJSONObject("turn").optString("id"));
    }
    public void interrupt(String threadId, String turnId, Callback<Void> callback) {
        run(callback, () -> { api.post("/api/threads/" + threadId + "/turns/" + turnId + "/interrupt", new JSONObject()); return null; });
    }
    public void events(long after, Callback<JSONObject> callback) {
        run(callback, () -> api.get("/api/events?after=" + after + "&timeout=25"));
    }
    public void approve(String requestId, boolean accept, Callback<Void> callback) {
        String encoded = URLEncoder.encode(requestId, StandardCharsets.UTF_8);
        run(callback, () -> { api.post("/api/approvals/" + encoded, new JSONObject().put("decision", accept ? "accept" : "decline")); return null; });
    }
    private <T> void run(Callback<T> callback, Supplier<T> supplier) {
        executor.execute(() -> { try { T value = supplier.get(); main.post(() -> callback.onSuccess(value)); }
            catch (Exception error) { String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); main.post(() -> callback.onError(message)); } });
    }
    private static String normalize(String value) {
        String url = value.trim(); if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        return url.replaceAll("/+$", "");
    }
}
