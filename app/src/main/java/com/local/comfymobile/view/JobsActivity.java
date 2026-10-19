package com.local.comfymobile.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.local.comfymobile.R;
import com.local.comfymobile.adapter.JobsAdapter;
import com.local.comfymobile.controller.JobsController;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.Artifact;
import com.local.comfymobile.model.Entities.JobInfo;

import java.util.List;

public final class JobsActivity extends Activity implements JobsController.View {
    private JobsAdapter adapter;
    private ProgressBar progress;
    private JobsController controller;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_jobs);
        progress = findViewById(R.id.jobsProgress);
        ListView list = findViewById(R.id.jobsList);
        adapter = new JobsAdapter(this);
        list.setAdapter(adapter);
        controller = new JobsController(AppRepository.get(this), this);
        findViewById(R.id.jobsRefreshButton).setOnClickListener(view -> controller.refresh());
        list.setOnItemClickListener((parent, view, position, id) -> chooseArtifact(adapter.getItem(position)));
        controller.refresh();
    }

    @Override protected void onResume() { super.onResume(); if (controller != null) controller.refresh(); }

    private void chooseArtifact(JobInfo job) {
        if (job.artifacts.isEmpty()) { Toast.makeText(this, "任务尚无可查看产物", Toast.LENGTH_SHORT).show(); return; }
        if (job.artifacts.size() == 1) { openArtifact(job.artifacts.get(0)); return; }
        String[] names = job.artifacts.stream().map(item -> item.filename).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("选择产物").setItems(names, (dialog, which) -> openArtifact(job.artifacts.get(which))).show();
    }

    private void openArtifact(Artifact artifact) {
        Intent intent = new Intent(this, ArtifactActivity.class);
        intent.putExtra("jobId", artifact.jobId);
        intent.putExtra("index", artifact.index);
        intent.putExtra("filename", artifact.filename);
        intent.putExtra("mimeType", artifact.mimeType);
        intent.putExtra("url", artifact.url);
        startActivity(intent);
    }

    @Override public void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); }
    @Override public void showJobs(List<JobInfo> jobs) { adapter.replace(jobs); }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
