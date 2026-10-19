package com.local.comfymobile.controller;

import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.Session;

public final class LoginController {
    public interface View {
        void setLoading(boolean loading);
        void showError(String message);
        void requestVpnPermission();
        void openWorkflows();
    }

    private final AppRepository repository;
    private final View view;
    private String pendingServer;
    private String pendingUsername;
    private String pendingPassword;

    public LoginController(AppRepository repository, View view) {
        this.repository = repository;
        this.view = view;
    }

    public void login(String server, String username, String password, boolean useVpn) {
        if (server.trim().isEmpty() || username.trim().isEmpty() || password.isEmpty()) {
            view.showError("请输入本地域名、账号和密码");
            return;
        }
        pendingServer = server;
        pendingUsername = username;
        pendingPassword = password;
        view.setLoading(true);
        if (useVpn) view.requestVpnPermission();
        else loginNow();
    }

    public void onVpnPermissionResult(boolean granted) {
        if (!granted) {
            view.setLoading(false);
            view.showError("未授予 VPN 权限");
            return;
        }
        repository.connectVpn(new AppRepository.Callback<Void>() {
            @Override public void onSuccess(Void ignored) { loginNow(); }
            @Override public void onError(String message) { view.setLoading(false); view.showError("VPN 连接失败：" + message); }
        });
    }

    private void loginNow() {
        repository.login(pendingServer, pendingUsername, pendingPassword, new AppRepository.Callback<Session>() {
            @Override public void onSuccess(Session value) { view.setLoading(false); view.openWorkflows(); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
