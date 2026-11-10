package com.local.codexmobile.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.local.codexmobile.R;
import com.local.codexmobile.adapter.MessageAdapter;
import com.local.codexmobile.controller.ChatController;
import com.local.codexmobile.model.Entities.ThreadDetail;
import com.local.codexmobile.model.Repository;

public final class ChatActivity extends Activity implements ChatController.View {
    private ChatController controller; private MessageAdapter adapter; private ListView list; private EditText input;
    private TextView title, status, streaming; private ProgressBar progress;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_chat);
        String id = getIntent().getStringExtra("threadId"); title = findViewById(R.id.chatTitle); status = findViewById(R.id.chatStatus);
        title.setText(getIntent().getStringExtra("threadName")); progress = findViewById(R.id.chatProgress); streaming = findViewById(R.id.streamingText);
        input = findViewById(R.id.messageInput); list = findViewById(R.id.messageList); adapter = new MessageAdapter(this); list.setAdapter(adapter);
        controller = new ChatController(Repository.get(this), this, id);
        findViewById(R.id.sendButton).setOnClickListener(v -> { String text = input.getText().toString(); if (!text.trim().isEmpty()) { input.setText(""); controller.send(text); } });
        findViewById(R.id.stopButton).setOnClickListener(v -> controller.interrupt());
    }
    @Override protected void onStart() { super.onStart(); controller.start(); }
    @Override protected void onStop() { controller.stop(); super.onStop(); }
    @Override public void setLoading(boolean value) { progress.setVisibility(value ? View.VISIBLE : View.GONE); }
    @Override public void showThread(ThreadDetail thread) { title.setText(thread.name); adapter.replace(thread.messages); if (adapter.getCount() > 0) list.setSelection(adapter.getCount() - 1); }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void appendDelta(String delta) { streaming.setVisibility(View.VISIBLE); streaming.append(delta); }
    @Override public void clearStreaming() { streaming.setText(""); streaming.setVisibility(View.GONE); }
    @Override public void setStatus(String value) { status.setText(value); }
    @Override public void requestApproval(String requestId, String approvalTitle, String detail) {
        new AlertDialog.Builder(this).setTitle("Codex 请求审批").setMessage(approvalTitle + "\n\n" + detail)
            .setPositiveButton("批准", (d, w) -> controller.answerApproval(requestId, true))
            .setNegativeButton("拒绝", (d, w) -> controller.answerApproval(requestId, false)).setCancelable(false).show();
    }
}
