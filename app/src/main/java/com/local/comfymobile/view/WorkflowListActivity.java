package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.local.comfymobile.R;
import com.local.comfymobile.adapter.WorkflowListAdapter;
import com.local.comfymobile.controller.WorkflowListController;
import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.WorkflowSummary;

import java.util.List;

public final class WorkflowListActivity extends Activity implements WorkflowListController.View {
    private WorkflowListAdapter adapter;
    private ProgressBar progress;
    private TextView status;
    private WorkflowListController controller;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_workflow_list);
        progress = findViewById(R.id.workflowProgress);
        status = findViewById(R.id.comfyStatus);
        ListView list = findViewById(R.id.workflowList);
        adapter = new WorkflowListAdapter(this);
        list.setAdapter(adapter);
        controller = new WorkflowListController(AppRepository.get(this), this);
        findViewById(R.id.refreshButton).setOnClickListener(view -> controller.refresh());
        findViewById(R.id.jobsButton).setOnClickListener(view -> startActivity(new Intent(this, JobsActivity.class)));
        list.setOnItemClickListener((parent, view, position, id) -> {
            WorkflowSummary workflow = adapter.getItem(position);
            Intent intent = new Intent(this, WorkflowDetailActivity.class);
            intent.putExtra("workflowId", workflow.id);
            intent.putExtra("workflowName", workflow.name);
            startActivity(intent);
        });
        controller.refresh();
    }

    @Override public void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); }
    @Override public void showWorkflows(List<WorkflowSummary> workflows) { adapter.replace(workflows); }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void showComfyStatus(String message) { status.setText(message); }
}
