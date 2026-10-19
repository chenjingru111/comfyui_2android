package com.local.comfymobile.controller;

import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.WorkflowSummary;

import java.util.List;

public final class WorkflowListController {
    public interface View {
        void setLoading(boolean loading);
        void showWorkflows(List<WorkflowSummary> workflows);
        void showError(String message);
        void showComfyStatus(String message);
    }

    private final AppRepository repository;
    private final View view;

    public WorkflowListController(AppRepository repository, View view) {
        this.repository = repository;
        this.view = view;
    }

    public void refresh() {
        String comfy = repository.session().comfyUrl();
        view.showComfyStatus(comfy.isEmpty() ? "未发现在线 ComfyUI" : "已连接 ComfyUI：" + comfy);
        view.setLoading(true);
        repository.workflows(new AppRepository.Callback<List<WorkflowSummary>>() {
            @Override public void onSuccess(List<WorkflowSummary> value) { view.setLoading(false); view.showWorkflows(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
