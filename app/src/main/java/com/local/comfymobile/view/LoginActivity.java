package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import android.net.VpnService;

import com.local.comfymobile.R;
import com.local.comfymobile.controller.LoginController;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.SessionStore;

public final class LoginActivity extends Activity implements LoginController.View {
    private static final int VPN_PERMISSION = 3001;
    /** 传 true 时强制显示登录表单（用来改服务器地址/换账号） */
    public static final String EXTRA_FORCE = "forceLogin";
    private EditText server;
    private EditText username;
    private EditText password;
    private Button login;
    private ProgressBar progress;
    private TextView error;
    private Switch vpn;
    private LoginController controller;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppRepository repository = AppRepository.get(this);
        SessionStore session = repository.session();
        boolean force = getIntent().getBooleanExtra(EXTRA_FORCE, false);
        // 已经有登录态就直接进列表，不用每次重新登录
        if (!force && session.hasSession()) {
            startActivity(new Intent(this, WorkflowListActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);
        server = findViewById(R.id.serverInput);
        username = findViewById(R.id.usernameInput);
        password = findViewById(R.id.passwordInput);
        login = findViewById(R.id.loginButton);
        progress = findViewById(R.id.loginProgress);
        error = findViewById(R.id.loginError);
        vpn = findViewById(R.id.vpnSwitch);
        server.setText(session.baseUrl());
        username.setText(session.username());
        controller = new LoginController(repository, this);
        login.setOnClickListener(view -> {
            String serverText = server.getText().toString().trim();
            String usernameText = username.getText().toString().trim();
            // 先落盘，哪怕登录失败，下次打开也不用重新输入
            session.saveDraft(serverText, usernameText);
            controller.login(serverText, usernameText, password.getText().toString(), vpn.isChecked());
        });
    }

    @Override
    public void requestVpnPermission() {
        Intent intent = VpnService.prepare(this);
        if (intent == null) controller.onVpnPermissionResult(true);
        else startActivityForResult(intent, VPN_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_PERMISSION) controller.onVpnPermissionResult(resultCode == RESULT_OK);
    }

    @Override public void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); login.setEnabled(!loading); }
    @Override public void showError(String message) { error.setText(message); }
    @Override public void openWorkflows() { startActivity(new Intent(this, WorkflowListActivity.class)); finish(); }
}
