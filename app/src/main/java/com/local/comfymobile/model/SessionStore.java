package com.local.comfymobile.model;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {
    private final SharedPreferences preferences;

    public SessionStore(Context context) {
        preferences = context.getSharedPreferences("comfy_mobile_session", Context.MODE_PRIVATE);
    }

    public void save(String baseUrl, String token, String comfyUrl) {
        save(baseUrl, token, comfyUrl, username());
    }

    /** 登录成功后保存会话，同时记住这次用的账号，下次打开自动回填。 */
    public void save(String baseUrl, String token, String comfyUrl, String username) {
        preferences.edit()
                .putString("baseUrl", baseUrl)
                .putString("token", token)
                .putString("comfyUrl", comfyUrl)
                .putString("username", username == null ? "" : username.trim())
                .apply();
    }

    /** 只记住地址和账号（不涉及登录状态），点“连接并登录”时就写入，失败也不丢。 */
    public void saveDraft(String baseUrl, String username) {
        preferences.edit()
                .putString("baseUrl", baseUrl == null ? "" : baseUrl.trim())
                .putString("username", username == null ? "" : username.trim())
                .apply();
    }

    public String baseUrl() { return preferences.getString("baseUrl", ""); }
    public String username() { return preferences.getString("username", ""); }
    public String token() { return preferences.getString("token", ""); }
    public String comfyUrl() { return preferences.getString("comfyUrl", ""); }
    public boolean hasSession() { return !token().isEmpty(); }
    public void clear() { preferences.edit().clear().apply(); }
}
