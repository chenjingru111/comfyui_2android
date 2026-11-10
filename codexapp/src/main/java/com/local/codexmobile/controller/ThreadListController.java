package com.local.codexmobile.controller;

import com.local.codexmobile.model.Entities.ThreadSummary;
import com.local.codexmobile.model.Repository;

import java.util.List;

public final class ThreadListController {
    public interface View { void setLoading(boolean value); void showThreads(List<ThreadSummary> items); void showError(String message); void openThread(ThreadSummary thread); }
    private final Repository repository; private final View view;
    public ThreadListController(Repository repository, View view) { this.repository = repository; this.view = view; }
    public void refresh() {
        view.setLoading(true); repository.threads(new Repository.Callback<>() {
            @Override public void onSuccess(List<ThreadSummary> value) { view.setLoading(false); view.showThreads(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
    public void create(String cwd) {
        view.setLoading(true); repository.createThread(cwd, new Repository.Callback<>() {
            @Override public void onSuccess(ThreadSummary value) { view.setLoading(false); view.openThread(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
