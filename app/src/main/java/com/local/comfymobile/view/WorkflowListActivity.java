package com.local.comfymobile.view;

import android.app.Activity;
import android.app.AlertDialog;
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
        findViewById(R.id.accountButton).setOnClickListener(view -> openAccountMenu());
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
    @Override public void showError(String message) { handleError(message); }
    @Override public void showComfyStatus(String message) { status.setText(message); }

    /** 登录态失效时自动清掉并回登录页，其它错误只弹提示。 */
    private void handleError(String message) {
        AppRepository repository = AppRepository.get(this);
        if (repository.consumeUnauthorized()) {
            repository.session().clear();
            Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class).putExtra(LoginActivity.EXTRA_FORCE, true));
            finish();
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** 入口：改地址 / 换账号 / 退出登录。 */
    private void openAccountMenu() {
        final String[] items = { "重新登录 / 修改服务器地址", "退出登录", "取消" };
        new AlertDialog.Builder(this)
                .setTitle("账号")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, LoginActivity.class)
                                .putExtra(LoginActivity.EXTRA_FORCE, true));
                    } else if (which == 1) {
                        AppRepository.get(this).session().clear();
                        startActivity(new Intent(this, LoginActivity.class)
                                .putExtra(LoginActivity.EXTRA_FORCE, true));
                        finish();
                    }
                })
                .show();
    }
}
