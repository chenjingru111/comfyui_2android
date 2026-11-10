package com.local.codexmobile.model;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private String baseUrl;
    private String token;

    public ApiClient(String baseUrl, String token) { configure(baseUrl, token); }

    public void configure(String baseUrl, String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    public JSONObject get(String path) throws Exception { return request("GET", path, null, path.startsWith("/api/events") ? 40000 : 70000); }
    public JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body, 70000); }

    private JSONObject request(String method, String path, JSONObject body, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        JSONObject json = text.length() == 0 ? new JSONObject() : new JSONObject(text.toString());
        if (status >= 400) throw new IllegalStateException(json.optString("detail", "HTTP " + status));
        return json;
    }
}
