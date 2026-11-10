package com.local.codexmobile.view;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.local.codexmobile.R;
import com.local.codexmobile.controller.LoginController;
import com.local.codexmobile.model.Repository;

public final class LoginActivity extends Activity implements LoginController.View {
    private static final int VPN_PERMISSION = 4001;
    private EditText server, username, password; private Switch vpn; private Button login; private ProgressBar progress; private TextView error;
    private LoginController controller;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_login);
        server = findViewById(R.id.serverInput); username = findViewById(R.id.usernameInput); password = findViewById(R.id.passwordInput);
        vpn = findViewById(R.id.vpnSwitch); login = findViewById(R.id.loginButton); progress = findViewById(R.id.loginProgress); error = findViewById(R.id.loginError);
        Repository repository = Repository.get(this); server.setText(repository.session().baseUrl()); controller = new LoginController(repository, this);
        login.setOnClickListener(v -> controller.login(server.getText().toString(), username.getText().toString(), password.getText().toString(), vpn.isChecked()));
    }
    @Override public void requestVpnPermission() { Intent intent = VpnService.prepare(this); if (intent == null) controller.vpnPermission(true); else startActivityForResult(intent, VPN_PERMISSION); }
    @Override protected void onActivityResult(int code, int result, Intent data) { super.onActivityResult(code, result, data); if (code == VPN_PERMISSION) controller.vpnPermission(result == RESULT_OK); }
    @Override public void setLoading(boolean value) { progress.setVisibility(value ? View.VISIBLE : View.GONE); login.setEnabled(!value); }
    @Override public void showError(String message) { error.setText(message); }
    @Override public void openThreads() { startActivity(new Intent(this, ThreadListActivity.class)); finish(); }
}
