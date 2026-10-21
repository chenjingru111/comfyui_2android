package com.local.comfymobile.view;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.local.comfymobile.R;
import com.local.comfymobile.adapter.SimpleItemSelected;
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
    private android.widget.LinearLayout flowGroup;
    private android.widget.TextView flowSummary;
    private android.widget.TextView flowHint;
    private boolean flowReady = false;
    private final java.util.Map<Integer, android.widget.Switch> flowSwitchViews = new java.util.LinkedHashMap<>();
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
        ((EditText) findViewById(R.id.inputFilter)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { adapter.filter(s.toString()); }
        });
        ((android.widget.Switch) findViewById(R.id.showBypassedSwitch))
                .setOnCheckedChangeListener((button, checked) -> adapter.setShowDisabled(checked));
        flowGroup = findViewById(R.id.flowGroup);
        flowSummary = findViewById(R.id.flowSummary);
        flowHint = findViewById(R.id.flowHint);
        findViewById(R.id.flowHeader).setOnClickListener(view -> {
            boolean show = flowGroup.getVisibility() != View.VISIBLE;
            flowGroup.setVisibility(show ? View.VISIBLE : View.GONE);
            flowHint.setText(show ? "收起 ▴" : "展开 ▾");
        });
        controller = new WorkflowDetailController(AppRepository.get(this), this, workflowId);
        run.setOnClickListener(view -> controller.run(adapter.values(), adapter.media(),
                adapter.nodeModes(), adapter.groupModes()));
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
    @Override public void showWorkflow(WorkflowDetail workflow) {
        adapter.replace(workflow.inputs);
        setupFlows(workflow.groups);
    }

    /** 和 ComfyUI 的 Fast Groups Bypasser 面板一致：标题以“编辑”结尾的几条主流程，各一个开关。 */
    private void setupFlows(java.util.List<com.local.comfymobile.model.Entities.WorkflowGroup> groups) {
        java.util.List<com.local.comfymobile.model.Entities.WorkflowGroup> flows = new java.util.ArrayList<>();
        for (com.local.comfymobile.model.Entities.WorkflowGroup group : groups) {
            if (group.id >= 0 && group.flow) flows.add(group);
        }
        if (flows.isEmpty()) {
            for (com.local.comfymobile.model.Entities.WorkflowGroup group : groups) {
                if (group.id >= 0 && group.pipeline) flows.add(group);
            }
        }

        java.util.Map<Integer, java.util.List<Integer>> members = new java.util.LinkedHashMap<>();
        for (com.local.comfymobile.model.Entities.WorkflowGroup group : flows) {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            ids.add(group.id);
            ids.addAll(group.children);
            members.put(group.id, ids);
        }
        adapter.setFlowGroups(members);

        flowReady = false;
        flowGroup.removeAllViews();
        flowSwitchViews.clear();
        for (com.local.comfymobile.model.Entities.WorkflowGroup group : flows) {
            final int groupId = group.id;
            android.widget.Switch toggle = new android.widget.Switch(this);
            toggle.setText(group.title);
            toggle.setTextSize(14f);
            toggle.setChecked(group.enabled);
            toggle.setPadding(0, 10, 0, 10);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!flowReady) return;
                adapter.toggleFlow(groupId, checked);
                // 打开一条时把其它流也拨到“关”，界面上就能看出只跑一条
                flowReady = false;
                if (checked) {
                    for (java.util.Map.Entry<Integer, android.widget.Switch> entry : flowSwitchViews.entrySet()) {
                        if (entry.getKey() != groupId) entry.getValue().setChecked(false);
                    }
                }
                flowReady = true;
                updateFlowSummary();
            });
            flowSwitchViews.put(groupId, toggle);
            flowGroup.addView(toggle);
        }
        flowReady = true;
        updateFlowSummary();
    }

    /** 折叠状态下显示当前选的是哪条流。 */
    private void updateFlowSummary() {
        String name = null;
        for (java.util.Map.Entry<Integer, android.widget.Switch> entry : flowSwitchViews.entrySet()) {
            if (entry.getValue().isChecked()) {
                name = entry.getValue().getText().toString();
                break;
            }
        }
        flowSummary.setText(name == null ? "流程：未选择（保持原样）" : "流程：" + name);
    }

    private android.widget.RadioButton radio(String text, int id) {
        android.widget.RadioButton button = new android.widget.RadioButton(this);
        button.setText(text);
        button.setId(id);
        button.setTextSize(14f);
        button.setPadding(0, 8, 0, 8);
        return button;
    }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void showQueued(JobInfo job) {
        Toast.makeText(this, "已加入 ComfyUI 队列", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, JobsActivity.class));
    }
}
