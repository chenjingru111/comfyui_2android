package com.local.comfymobile.model;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ApiClient {
    private String baseUrl;
    private String token;

    public ApiClient(String baseUrl, String token) {
        configure(baseUrl, token);
    }

    public void configure(String baseUrl, String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    public JSONObject get(String path) throws Exception { return request("GET", path, null); }
    public JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body); }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return parseJsonResponse(connection);
    }

    public JSONObject upload(ContentResolver resolver, Uri uri, String kind) throws Exception {
        String boundary = "ComfyMobile-" + UUID.randomUUID();
        HttpURLConnection connection = open("/api/uploads");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(1024 * 1024);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        String filename = displayName(resolver, uri);
        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";
        try (OutputStream output = connection.getOutputStream(); InputStream input = resolver.openInputStream(uri)) {
            writePart(output, boundary, "kind", kind.getBytes(StandardCharsets.UTF_8), null, "text/plain");
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename.replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            if (input == null) throw new IOException("Cannot open selected file");
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return parseJsonResponse(connection);
    }

    private static void writePart(OutputStream output, String boundary, String name, byte[] value, String filename, String mime) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) disposition += "; filename=\"" + filename + "\"";
        output.write((disposition + "\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public File download(String path, File destination) throws Exception {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IOException(errorMessage(connection));
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return destination;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(120000);
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private static JSONObject parseJsonResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IOException(errorMessage(connection));
        try (InputStream input = connection.getInputStream()) {
            return new JSONObject(new String(readAll(input), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static String errorMessage(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getErrorStream();
        if (stream == null) return "HTTP " + connection.getResponseCode();
        String text = new String(readAll(stream), StandardCharsets.UTF_8);
        try { return new JSONObject(text).optString("detail", text); } catch (Exception ignored) { return text; }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "upload.bin" : segment;
    }
}
