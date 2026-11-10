package com.local.codexmobile.model;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {
    private final SharedPreferences preferences;
    public SessionStore(Context context) { preferences = context.getSharedPreferences("codex_mobile_session", Context.MODE_PRIVATE); }
    public String baseUrl() { return preferences.getString("baseUrl", "http://192.168.5.160:8770"); }
    public String token() { return preferences.getString("token", ""); }
    public void save(String baseUrl, String token) { preferences.edit().putString("baseUrl", baseUrl).putString("token", token).apply(); }
}
