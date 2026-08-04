package com.intellirelease.service;

import com.intellirelease.model.ChangeEvent;
import com.intellirelease.model.ReleaseSummary;

import java.util.List;

public interface ReleaseService {
    ReleaseSummary ingestGithubWebhook(ChangeEvent event);

    ReleaseSummary getRelease(String releaseId);

    List<ReleaseSummary> listReleases();
}