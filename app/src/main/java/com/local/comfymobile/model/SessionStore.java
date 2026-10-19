package com.local.comfymobile.model;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {
    private final SharedPreferences preferences;

    public SessionStore(Context context) {
        preferences = context.getSharedPreferences("comfy_mobile_session", Context.MODE_PRIVATE);
    }

    public void save(String baseUrl, String token, String comfyUrl) {
        preferences.edit().putString("baseUrl", baseUrl).putString("token", token).putString("comfyUrl", comfyUrl).apply();
    }

    public String baseUrl() { return preferences.getString("baseUrl", ""); }
    public String token() { return preferences.getString("token", ""); }
    public String comfyUrl() { return preferences.getString("comfyUrl", ""); }
    public boolean hasSession() { return !token().isEmpty(); }
    public void clear() { preferences.edit().clear().apply(); }
}
