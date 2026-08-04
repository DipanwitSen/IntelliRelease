package com.gyansys.intellirelease.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gyansys.intellirelease.infra.JobHandler;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.Job;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Runs the deterministic pipeline for one captured pull request.
 *
 * <p>Idempotent: {@link PullRequestAnalysisService} writes {@code pr_analysis}
 * keyed on the pull request ID, so a retry after a mid-flight crash overwrites
 * the previous attempt rather than creating a second record.
 */
@Component
public class AnalyzePrJobHandler implements JobHandler {

    private final PullRequestAnalysisService analysisService;
    private final JsonMapper jsonMapper;

    public AnalyzePrJobHandler(PullRequestAnalysisService analysisService, JsonMapper jsonMapper) {
        this.analysisService = analysisService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String kind() {
        return Job.KIND_ANALYZE_PR;
    }

    @Override
    public void handle(Job job) {
        Map<String, String> payload = jsonMapper.fromJson(
                job.getPayload(), new TypeReference<Map<String, String>>() {
                });
        if (payload == null || payload.get("prId") == null) {
            throw new IllegalArgumentException("ANALYZE_PR job " + job.getJobId() + " has no prId");
        }
        analysisService.analyze(UUID.fromString(payload.get("prId")));
    }
}
