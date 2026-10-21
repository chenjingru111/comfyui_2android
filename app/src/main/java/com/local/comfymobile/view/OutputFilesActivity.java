package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.local.comfymobile.R;
import com.local.comfymobile.adapter.OutputFilesAdapter;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.OutputEntry;
import com.local.comfymobile.model.Entities.OutputListing;

/** 浏览本机 ComfyUI 的 output 目录：进文件夹、看图片/视频/音频。 */
public final class OutputFilesActivity extends Activity {
    private final AppRepository repository = AppRepository.get(this);
    private OutputFilesAdapter adapter;
    private ProgressBar progress;
    private TextView pathView;
    private TextView emptyView;
    private String currentPath = "";
    private String parentPath = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_outputs);
        progress = findViewById(R.id.outputProgress);
        pathView = findViewById(R.id.outputPath);
        emptyView = findViewById(R.id.outputEmpty);
        ListView list = findViewById(R.id.outputList);
        adapter = new OutputFilesAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(adapter.getItem(position)));
        findViewById(R.id.outputRefreshButton).setOnClickListener(view -> load(currentPath));
        findViewById(R.id.outputUpButton).setOnClickListener(view -> goUp());
        load("");
    }

    private void load(String path) {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        repository.outputs(path, new AppRepository.Callback<OutputListing>() {
            @Override public void onSuccess(OutputListing value) {
                progress.setVisibility(View.GONE);
                currentPath = value.path;
                parentPath = value.parent;
                pathView.setText(currentPath.isEmpty() ? "output /" : "output / " + currentPath);
                adapter.replace(value.entries);
                emptyView.setVisibility(value.entries.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onError(String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(OutputFilesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goUp() {
        if (currentPath.isEmpty()) {
            finish();
            return;
        }
        load(parentPath);
    }

    private void open(OutputEntry entry) {
        if (entry.dir) {
            load(entry.path);
            return;
        }
        Intent intent = new Intent(this, ArtifactActivity.class);
        intent.putExtra("jobId", "");
        intent.putExtra("index", 0);
        intent.putExtra("filename", entry.name);
        intent.putExtra("mimeType", entry.mime);
        intent.putExtra("url", "/api/outputs/file?path=" + Uri.encode(entry.path));
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (!currentPath.isEmpty()) {
            load(parentPath);
        } else {
            super.onBackPressed();
        }
    }
}
