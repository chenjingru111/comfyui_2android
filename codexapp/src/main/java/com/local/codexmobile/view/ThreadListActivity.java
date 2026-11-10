package com.local.codexmobile.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.local.codexmobile.R;
import com.local.codexmobile.adapter.ThreadAdapter;
import com.local.codexmobile.controller.ThreadListController;
import com.local.codexmobile.model.Entities.ThreadSummary;
import com.local.codexmobile.model.Repository;

import java.util.List;

public final class ThreadListActivity extends Activity implements ThreadListController.View {
    private ThreadAdapter adapter; private ProgressBar progress; private TextView error; private ThreadListController controller;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_threads);
        progress = findViewById(R.id.threadProgress); error = findViewById(R.id.threadError); ListView list = findViewById(R.id.threadList);
        adapter = new ThreadAdapter(this); list.setAdapter(adapter); controller = new ThreadListController(Repository.get(this), this);
        list.setOnItemClickListener((p, v, position, id) -> openThread(adapter.getItem(position)));
        findViewById(R.id.refreshButton).setOnClickListener(v -> controller.refresh());
        findViewById(R.id.newButton).setOnClickListener(v -> newThreadDialog()); controller.refresh();
    }
    private void newThreadDialog() {
        EditText input = new EditText(this); input.setHint("服务器工作目录"); input.setText("/gpu_move"); input.setSingleLine(true);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density); input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this).setTitle("新建 Codex 对话").setView(input)
            .setPositiveButton("创建", (d, w) -> controller.create(input.getText().toString())).setNegativeButton("取消", null).show();
    }
    @Override public void setLoading(boolean value) { progress.setVisibility(value ? View.VISIBLE : View.GONE); }
    @Override public void showThreads(List<ThreadSummary> items) { error.setText(""); adapter.replace(items); }
    @Override public void showError(String message) { error.setText(message); }
    @Override public void openThread(ThreadSummary thread) {
        Intent intent = new Intent(this, ChatActivity.class).putExtra("threadId", thread.id).putExtra("threadName", thread.name); startActivity(intent);
    }
}
