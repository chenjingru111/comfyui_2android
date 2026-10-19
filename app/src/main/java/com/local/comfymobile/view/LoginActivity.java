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

public final class LoginActivity extends Activity implements LoginController.View {
    private static final int VPN_PERMISSION = 3001;
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
        setContentView(R.layout.activity_login);
        server = findViewById(R.id.serverInput);
        username = findViewById(R.id.usernameInput);
        password = findViewById(R.id.passwordInput);
        login = findViewById(R.id.loginButton);
        progress = findViewById(R.id.loginProgress);
        error = findViewById(R.id.loginError);
        vpn = findViewById(R.id.vpnSwitch);
        AppRepository repository = AppRepository.get(this);
        server.setText(repository.session().baseUrl());
        controller = new LoginController(repository, this);
        login.setOnClickListener(view -> controller.login(server.getText().toString(), username.getText().toString(), password.getText().toString(), vpn.isChecked()));
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
