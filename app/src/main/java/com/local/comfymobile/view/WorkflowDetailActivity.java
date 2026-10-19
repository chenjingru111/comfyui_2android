package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.local.comfymobile.R;
import com.local.comfymobile.adapter.WorkflowInputAdapter;
import com.local.comfymobile.controller.WorkflowDetailController;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.JobInfo;
import com.local.comfymobile.model.Entities.WorkflowDetail;
import com.local.comfymobile.model.Entities.WorkflowInput;

public final class WorkflowDetailActivity extends Activity implements WorkflowDetailController.View {
    private static final int PICK_MEDIA = 1201;
    private WorkflowInputAdapter adapter;
    private WorkflowInput pendingInput;
    private ProgressBar progress;
    private Button run;
    private WorkflowDetailController controller;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_workflow_detail);
        String workflowId = getIntent().getStringExtra("workflowId");
        ((TextView) findViewById(R.id.detailTitle)).setText(getIntent().getStringExtra("workflowName"));
        progress = findViewById(R.id.detailProgress);
        run = findViewById(R.id.runButton);
        ListView list = findViewById(R.id.inputList);
        adapter = new WorkflowInputAdapter(this, this::pickMedia);
        list.setAdapter(adapter);
        controller = new WorkflowDetailController(AppRepository.get(this), this, workflowId);
        run.setOnClickListener(view -> controller.run(adapter.values(), adapter.media()));
        controller.load();
    }

    private void pickMedia(WorkflowInput input) {
        pendingInput = input;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType(input.mime);
        startActivityForResult(intent, PICK_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA && resultCode == RESULT_OK && data != null && pendingInput != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                adapter.setMedia(pendingInput.key, uri);
            }
        }
    }

    @Override public void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); run.setEnabled(!loading); }
    @Override public void showWorkflow(WorkflowDetail workflow) { adapter.replace(workflow.inputs); }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void showQueued(JobInfo job) {
        Toast.makeText(this, "已加入 ComfyUI 队列", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, JobsActivity.class));
    }
}
