package com.local.codexmobile.controller;

import com.local.codexmobile.model.Repository;

public final class LoginController {
    public interface View { void setLoading(boolean value); void showError(String message); void requestVpnPermission(); void openThreads(); }
    private final Repository repository; private final View view;
    private String server, username, password;
    public LoginController(Repository repository, View view) { this.repository = repository; this.view = view; }
    public void login(String server, String username, String password, boolean vpn) {
        if (server.trim().isEmpty() || username.trim().isEmpty() || password.isEmpty()) { view.showError("请输入服务器、账号和密码"); return; }
        this.server = server; this.username = username; this.password = password; view.setLoading(true);
        if (vpn) view.requestVpnPermission(); else loginNow();
    }
    public void vpnPermission(boolean granted) {
        if (!granted) { view.setLoading(false); view.showError("未授予 VPN 权限"); return; }
        repository.connectVpn(new Repository.Callback<>() {
            @Override public void onSuccess(Void value) { loginNow(); }
            @Override public void onError(String message) { view.setLoading(false); view.showError("VPN 连接失败：" + message); }
        });
    }
    private void loginNow() {
        repository.login(server, username, password, new Repository.Callback<>() {
            @Override public void onSuccess(Void value) { view.setLoading(false); view.openThreads(); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
