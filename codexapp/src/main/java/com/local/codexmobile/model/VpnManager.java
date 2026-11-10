package com.local.codexmobile.model;

import android.content.Context;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class VpnManager {
    private final Context context;
    private final GoBackend backend;
    private final Tunnel tunnel = new PersonTunnel();
    public VpnManager(Context context) { this.context = context.getApplicationContext(); backend = new GoBackend(this.context); }
    public void connect() throws Exception {
        if (backend.getState(tunnel) == Tunnel.State.UP) return;
        JSONObject profile = new JSONObject(readProfile());
        Config config = Config.parse(new ByteArrayInputStream(profile.getString("config").getBytes(StandardCharsets.UTF_8)));
        backend.setState(tunnel, Tunnel.State.UP, config);
    }
    private String readProfile() throws Exception {
        int id = context.getResources().getIdentifier("person_key", "raw", context.getPackageName());
        if (id == 0) throw new IllegalStateException("缺少 res/raw/person_key.json");
        try (InputStream input = context.getResources().openRawResource(id); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static final class PersonTunnel implements Tunnel {
        @Override public String getName() { return "codex_person_key"; }
        @Override public void onStateChange(State newState) {}
    }
}
