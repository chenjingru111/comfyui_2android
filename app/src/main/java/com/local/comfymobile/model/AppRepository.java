package com.local.comfymobile.model;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.local.comfymobile.model.Entities.Artifact;
import com.local.comfymobile.model.Entities.JobInfo;
import com.local.comfymobile.model.Entities.Session;
import com.local.comfymobile.model.Entities.WorkflowDetail;
import com.local.comfymobile.model.Entities.WorkflowSummary;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppRepository {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    private static AppRepository instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionStore sessionStore;
    private final ContentResolver contentResolver;
    private final File cacheDir;
    private final ApiClient api;
    private final VpnManager vpn;

    private AppRepository(Context context) {
        Context app = context.getApplicationContext();
        sessionStore = new SessionStore(app);
        contentResolver = app.getContentResolver();
        cacheDir = app.getCacheDir();
        api = new ApiClient(sessionStore.baseUrl(), sessionStore.token());
        vpn = new VpnManager(app);
    }

    public static synchronized AppRepository get(Context context) {
        if (instance == null) instance = new AppRepository(context);
        return instance;
    }

    public SessionStore session() { return sessionStore; }

    public void connectVpn(Callback<Void> callback) {
        run(callback, () -> { vpn.connect(); return null; });
    }

    public void login(String baseUrl, String username, String password, Callback<Session> callback) {
        executor.execute(() -> {
            try {
                api.configure(normalize(baseUrl), "");
                JSONObject request = new JSONObject().put("username", username).put("password", password);
                Session session = new Session(api.post("/api/login", request));
                String comfyUrl = session.instances.stream().filter(item -> item.online).map(item -> item.url).findFirst().orElse("");
                sessionStore.save(normalize(baseUrl), session.token, comfyUrl);
                api.configure(normalize(baseUrl), session.token);
                success(callback, session);
            } catch (Exception error) { failure(callback, error); }
        });
    }

    public void workflows(Callback<List<WorkflowSummary>> callback) {
        run(callback, () -> WorkflowSummary.list(api.get("/api/workflows").optJSONArray("workflows")));
    }

    public void workflow(String id, Callback<WorkflowDetail> callback) {
        run(callback, () -> new WorkflowDetail(api.get("/api/workflows/" + id + "?comfyUrl=" + encode(sessionStore.comfyUrl()))));
    }

    public void runWorkflow(String id, String comfyUrl, Map<String, Object> values, Map<String, Uri> media, Callback<JobInfo> callback) {
        executor.execute(() -> {
            try {
                JSONObject overrides = new JSONObject();
                for (Map.Entry<String, Object> entry : values.entrySet()) overrides.put(entry.getKey(), entry.getValue());
                for (Map.Entry<String, Uri> entry : media.entrySet()) {
                    JSONObject upload = api.upload(contentResolver, entry.getValue(), mediaKind(entry.getKey()));
                    overrides.put(entry.getKey(), upload.getString("storedName"));
                }
                JSONObject body = new JSONObject().put("comfyUrl", comfyUrl).put("overrides", overrides);
                JobInfo job = new JobInfo(api.post("/api/workflows/" + id + "/run", body).getJSONObject("job"));
                success(callback, job);
            } catch (Exception error) { failure(callback, error); }
        });
    }

    public void jobs(Callback<List<JobInfo>> callback) {
        run(callback, () -> JobInfo.list(api.get("/api/jobs").optJSONArray("jobs")));
    }

    public void downloadArtifact(Artifact artifact, Callback<File> callback) {
        String safeName = artifact.filename.replaceAll("[^A-Za-z0-9._-]", "_");
        run(callback, () -> api.download(artifact.url, new File(cacheDir, artifact.jobId + "_" + artifact.index + "_" + safeName)));
    }

    private <T> void run(Callback<T> callback, ThrowingSupplier<T> supplier) {
        executor.execute(() -> {
            try { success(callback, supplier.get()); }
            catch (Exception error) { failure(callback, error); }
        });
    }

    private <T> void success(Callback<T> callback, T value) { main.post(() -> callback.onSuccess(value)); }
    private void failure(Callback<?> callback, Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        main.post(() -> callback.onError(message));
    }

    private static String normalize(String url) {
        String value = url.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        return value.replaceAll("/+$", "");
    }

    private static String encode(String value) {
        return Uri.encode(value == null ? "" : value);
    }

    private static String mediaKind(String key) {
        String lower = key.toLowerCase();
        if (lower.contains("video")) return "video";
        if (lower.contains("audio")) return "audio";
        return "image";
    }

    private interface ThrowingSupplier<T> { T get() throws Exception; }
}
