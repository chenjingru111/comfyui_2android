package com.local.comfymobile.controller;

import android.net.Uri;

import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.JobInfo;
import com.local.comfymobile.model.Entities.WorkflowDetail;

import java.util.Map;

public final class WorkflowDetailController {
    public interface View {
        void setLoading(boolean loading);
        void showWorkflow(WorkflowDetail workflow);
        void showError(String message);
        void showQueued(JobInfo job);
    }

    private final AppRepository repository;
    private final View view;
    private final String workflowId;
    private WorkflowDetail workflow;

    public WorkflowDetailController(AppRepository repository, View view, String workflowId) {
        this.repository = repository;
        this.view = view;
        this.workflowId = workflowId;
    }

    public void load() {
        view.setLoading(true);
        repository.workflow(workflowId, new AppRepository.Callback<WorkflowDetail>() {
            @Override public void onSuccess(WorkflowDetail value) { workflow = value; view.setLoading(false); view.showWorkflow(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }

    public void run(Map<String, Object> values, Map<String, Uri> media,
                    Map<String, Boolean> nodeModes, Map<String, Boolean> groupModes) {
        if (workflow == null) { view.showError("工作流尚未加载"); return; }
        view.setLoading(true);
        repository.runWorkflow(workflowId, workflow.comfyUrl, values, media, nodeModes, groupModes,
                new AppRepository.Callback<JobInfo>() {
            @Override public void onSuccess(JobInfo value) { view.setLoading(false); view.showQueued(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
