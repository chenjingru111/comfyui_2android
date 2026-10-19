package com.local.comfymobile.controller;

import com.local.comfymobile.model.AppRepository;
import com.local.comfymobile.model.Entities.Artifact;

import java.io.File;

public final class ArtifactController {
    public interface View {
        void setLoading(boolean loading);
        void showContent(File content);
        void showError(String message);
    }

    private final AppRepository repository;
    private final View view;
    private final Artifact artifact;

    public ArtifactController(AppRepository repository, View view, Artifact artifact) {
        this.repository = repository;
        this.view = view;
        this.artifact = artifact;
    }

    public void load() {
        view.setLoading(true);
        repository.downloadArtifact(artifact, new AppRepository.Callback<File>() {
            @Override public void onSuccess(File value) { view.setLoading(false); view.showContent(value); }
            @Override public void onError(String message) { view.setLoading(false); view.showError(message); }
        });
    }
}
