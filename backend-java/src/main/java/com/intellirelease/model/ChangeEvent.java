package com.intellirelease.model;

import java.time.Instant;
import java.util.List;

public record ChangeEvent(
        String repository,
        String branch,
        String mergeSha,
        String prNumber,
        String author,
        String title,
        List<ChangeFile> changedFiles,
        Instant receivedAt
) {
    public ChangeEvent(String repository,
                       String branch,
                       String mergeSha,
                       String prNumber,
                       String author,
                       String title,
                       List<ChangeFile> changedFiles) {
        this(repository, branch, mergeSha, prNumber, author, title, changedFiles, Instant.now());
    }
}