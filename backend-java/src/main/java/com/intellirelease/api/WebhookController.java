package com.intellirelease.api;

import com.intellirelease.model.ChangeEvent;
import com.intellirelease.model.ChangeFile;
import com.intellirelease.model.ReleaseSummary;
import com.intellirelease.service.ReleaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WebhookController {

    private final ReleaseService releaseService;

    public WebhookController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now().toString());
    }

    @PostMapping("/webhooks/github")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReleaseSummary ingestGithubWebhook(@Valid @RequestBody GithubWebhookRequest request) {
        return releaseService.ingestGithubWebhook(request.toEvent());
    }

    @GetMapping("/releases/{releaseId}")
    public ReleaseSummary getRelease(@PathVariable String releaseId) {
        return releaseService.getRelease(releaseId);
    }

    @GetMapping("/releases")
    public List<ReleaseSummary> listReleases() {
        return releaseService.listReleases();
    }

    public record HealthResponse(String status, String timestamp) {
    }

    public record GithubWebhookRequest(
            @NotBlank String repository,
            @NotBlank String branch,
            @NotBlank String mergeSha,
            @NotBlank String prNumber,
            @NotBlank String author,
            @NotBlank String title,
            @NotEmpty List<@Valid FileRequest> changedFiles
    ) {
        ChangeEvent toEvent() {
            return new ChangeEvent(
                    repository,
                    branch,
                    mergeSha,
                    prNumber,
                    author,
                    title,
                    changedFiles.stream().map(fileRequest -> fileRequest.toModel()).toList()
            );
        }
    }

    public record FileRequest(@NotBlank String path, @NotBlank String changeType) {
        ChangeFile toModel() {
            return new ChangeFile(path, changeType);
        }
    }
}