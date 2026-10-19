package com.local.comfymobile.controller;

import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.JobInfo;

import java.util.List;

public final class JobsController {
    public interface View {
        void setLoading(boolean loading);
        void showJobs(List<JobInfo> jobs);
        void showError(String message);
    }

    private final AppRepository repository;
    private final View view;

    public JobsController(AppRepository repository, View view) { this.repository = repository; this.view = view; }

    public void refresh() {
        view.setLoading(true);
        repository.jobs(new AppRepository.Callback<List<JobInfo>>() {
            @Override public void onSuccess(List<JobInfo> value) { view.setLoading(false); view.showJobs(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
