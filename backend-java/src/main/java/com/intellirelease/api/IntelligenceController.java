package com.intellirelease.api;

import com.intellirelease.model.ReleaseNarrative;
import com.intellirelease.service.AiIntelligenceClient;
import com.intellirelease.service.ReleaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/releases")
public class IntelligenceController {

    private final ReleaseService releaseService;
    private final AiIntelligenceClient aiIntelligenceClient;

    public IntelligenceController(ReleaseService releaseService, AiIntelligenceClient aiIntelligenceClient) {
        this.releaseService = releaseService;
        this.aiIntelligenceClient = aiIntelligenceClient;
    }

    @GetMapping("/{releaseId}/narrative")
    public ReleaseNarrative narrative(@PathVariable String releaseId) {
        return aiIntelligenceClient.explain(releaseService.getRelease(releaseId));
    }
}