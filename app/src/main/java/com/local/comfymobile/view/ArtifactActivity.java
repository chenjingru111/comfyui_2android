package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.local.comfymobile.R;
import com.local.comfymobile.controller.ArtifactController;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.Artifact;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public final class ArtifactActivity extends Activity implements ArtifactController.View {
    private static final int SAVE_FILE = 2001;
    private Artifact artifact;
    private File localFile;
    private ProgressBar progress;
    private ImageView image;
    private VideoView video;
    private Button audioButton;
    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_artifact);
        artifact = new Artifact(new JSONObjectBuilder()
                .put("jobId", getIntent().getStringExtra("jobId"))
                .put("index", getIntent().getIntExtra("index", 0))
                .put("filename", getIntent().getStringExtra("filename"))
                .put("mimeType", getIntent().getStringExtra("mimeType"))
                .put("url", getIntent().getStringExtra("url")).build());
        ((TextView) findViewById(R.id.artifactTitle)).setText(artifact.filename);
        progress = findViewById(R.id.artifactProgress);
        image = findViewById(R.id.artifactImage);
        video = findViewById(R.id.artifactVideo);
        audioButton = findViewById(R.id.audioPlayButton);
        findViewById(R.id.saveArtifactButton).setOnClickListener(view -> save());
        new ArtifactController(AppRepository.get(this), this, artifact).load();
    }

    @Override public void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); }

    @Override
    public void showContent(File content) {
        try {
            localFile = content;
            Uri uri = Uri.fromFile(localFile);
            if (artifact.mimeType.startsWith("image/")) {
                image.setVisibility(View.VISIBLE);
                image.setImageBitmap(BitmapFactory.decodeFile(localFile.getAbsolutePath()));
            } else if (artifact.mimeType.startsWith("video/")) {
                video.setVisibility(View.VISIBLE);
                video.setMediaController(new MediaController(this));
                video.setVideoURI(uri);
                video.start();
            } else if (artifact.mimeType.startsWith("audio/")) {
                audioButton.setVisibility(View.VISIBLE);
                player = MediaPlayer.create(this, uri);
                audioButton.setOnClickListener(view -> { if (player.isPlaying()) player.pause(); else player.start(); });
            }
        } catch (Exception error) { showError(error.getMessage()); }
    }

    private void save() {
        if (localFile == null) { showError("产物尚未下载完成"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(artifact.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, artifact.filename);
        startActivityForResult(intent, SAVE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_FILE || resultCode != RESULT_OK || data == null || data.getData() == null || localFile == null) return;
        try (FileInputStream input = new FileInputStream(localFile); OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception error) { showError(error.getMessage()); }
    }

    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { if (player != null) player.release(); super.onDestroy(); }

    private static final class JSONObjectBuilder {
        private final JSONObject json = new JSONObject();
        JSONObjectBuilder put(String key, Object value) { try { json.put(key, value); } catch (Exception ignored) {} return this; }
        JSONObject build() { return json; }
    }
}
