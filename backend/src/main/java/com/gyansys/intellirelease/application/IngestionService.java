package com.gyansys.intellirelease.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyansys.intellirelease.adapters.git.GitHubProvider;
import com.gyansys.intellirelease.adapters.git.PullRequestDetail;
import com.gyansys.intellirelease.adapters.git.WebhookSignatureVerifier;
import com.gyansys.intellirelease.infra.AuditWriter;
import com.gyansys.intellirelease.infra.JobQueue;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.Job;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.WebhookEvent;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import com.gyansys.intellirelease.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Captures every merged change, exactly once.
 *
 * <p>Order matters here and is deliberate: verify, deduplicate, <em>persist</em>,
 * acknowledge, then analyse asynchronously. Persist-then-acknowledge means a
 * crash after the 202 never loses a production-bound change — the raw event is
 * already durable and the queued job will be retried.
 *
 * <p>The alternative — analyse inline and acknowledge at the end — makes GitHub
 * wait on six engines and an LLM call, times out, and produces exactly the
 * duplicate deliveries that make idempotency load-bearing.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public static final String PROVIDER = "github";

    private final WebhookEventRepository webhookEventRepository;
    private final PullRequestRepository pullRequestRepository;
    private final WebhookSignatureVerifier signatureVerifier;
    private final GitHubProvider gitHubProvider;
    private final JobQueue jobQueue;
    private final JsonMapper jsonMapper;
    private final TenantContext tenantContext;
    private final AuditWriter auditWriter;

    public IngestionService(WebhookEventRepository webhookEventRepository,
                            PullRequestRepository pullRequestRepository,
                            WebhookSignatureVerifier signatureVerifier,
                            GitHubProvider gitHubProvider,
                            JobQueue jobQueue,
                            JsonMapper jsonMapper,
                            TenantContext tenantContext,
                            AuditWriter auditWriter) {
        this.webhookEventRepository = webhookEventRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.signatureVerifier = signatureVerifier;
        this.gitHubProvider = gitHubProvider;
        this.jobQueue = jobQueue;
        this.jsonMapper = jsonMapper;
        this.tenantContext = tenantContext;
        this.auditWriter = auditWriter;
    }

    /** What happened to an inbound delivery. Returned to the caller verbatim. */
    public record IngestResult(
            String outcome,
            UUID eventId,
            UUID prId,
            Integer prNumber,
            boolean signatureValid,
            boolean duplicate,
            String message
    ) {
        static IngestResult duplicate(UUID eventId, String message) {
            return new IngestResult("DUPLICATE_IGNORED", eventId, null, null, true, true, message);
        }

        static IngestResult ignored(UUID eventId, boolean signatureValid, String message) {
            return new IngestResult("IGNORED", eventId, null, null, signatureValid, false, message);
        }

        static IngestResult accepted(UUID eventId, UUID prId, int prNumber, boolean signatureValid) {
            return new IngestResult("ACCEPTED", eventId, prId, prNumber, signatureValid, false,
                    "Change captured and queued for analysis");
        }
    }

    /**
     * @param rawBody         request body exactly as received — re-serialising breaks HMAC
     * @param deliveryId      X-GitHub-Delivery: the idempotency key
     * @param eventType       X-GitHub-Event
     * @param signatureHeader X-Hub-Signature-256
     */
    @Transactional
    public IngestResult ingest(String rawBody, String deliveryId, String eventType, String signatureHeader) {
        boolean signatureValid = signatureVerifier.isValid(rawBody, signatureHeader);

        // Idempotency before persistence. GitHub retries; we must not re-analyse.
        Optional<WebhookEvent> existing =
                webhookEventRepository.findByProviderAndDeliveryId(PROVIDER, deliveryId);
        if (existing.isPresent()) {
            log.info("Duplicate webhook delivery {} ignored", deliveryId);
            auditWriter.recordAs(AuditWriter.SYSTEM_WEBHOOK, "WEBHOOK_DUPLICATE_IGNORED",
                    "WebhookEvent", existing.get().getEventId().toString(),
                    "Delivery " + deliveryId + " had already been received");
            return IngestResult.duplicate(existing.get().getEventId(),
                    "Delivery already processed; no second analysis was performed");
        }

        WebhookEvent event = WebhookEvent.create(
                tenantContext.currentTenantId(), PROVIDER, deliveryId, eventType, rawBody, signatureValid);
        webhookEventRepository.save(event);

        auditWriter.recordAs(AuditWriter.SYSTEM_WEBHOOK, "WEBHOOK_RECEIVED",
                "WebhookEvent", event.getEventId().toString(),
                "event=" + eventType + " signatureValid=" + signatureValid);

        if (!isMergedPullRequest(rawBody)) {
            event.setProcessed(true);
            webhookEventRepository.save(event);
            return IngestResult.ignored(event.getEventId(), signatureValid,
                    "Event is not a merged pull request; recorded but not analysed");
        }

        Optional<PullRequestDetail> parsed = gitHubProvider.parseWebhookPayload(rawBody);
        if (parsed.isEmpty()) {
            event.setProcessed(true);
            webhookEventRepository.save(event);
            return IngestResult.ignored(event.getEventId(), signatureValid,
                    "Payload did not contain a recognisable pull request");
        }

        PullRequest pullRequest = upsert(enrich(parsed.get()));

        event.setProcessed(true);
        webhookEventRepository.save(event);

        jobQueue.enqueue(Job.KIND_ANALYZE_PR, Map.of("prId", pullRequest.getPrId().toString()));

        auditWriter.recordAs(AuditWriter.SYSTEM_WEBHOOK, "PR_CAPTURED",
                "PullRequest", pullRequest.getPrId().toString(),
                "PR #" + pullRequest.getPrNumber() + " on " + pullRequest.getRepoName()
                        + " queued for analysis");

        return IngestResult.accepted(event.getEventId(), pullRequest.getPrId(),
                pullRequest.getPrNumber(), signatureValid);
    }

    /**
     * The webhook body carries PR metadata but not the changed-file manifest.
     * When the API is reachable we ask it; when it is not, we proceed with what
     * the payload gave us and the analysis records fewer files rather than none.
     */
    private PullRequestDetail enrich(PullRequestDetail fromPayload) {
        if (!fromPayload.changedFiles().isEmpty() || !gitHubProvider.isConfigured()) {
            return fromPayload;
        }
        return gitHubProvider
                .fetchPullRequest(fromPayload.repoFullName(), fromPayload.prNumber())
                .filter(detail -> !detail.changedFiles().isEmpty())
                .orElse(fromPayload);
    }

    /** Upsert on (tenant, repo, mergeSha) so redelivery updates rather than duplicates. */
    private PullRequest upsert(PullRequestDetail detail) {
        String tenantId = tenantContext.currentTenantId();

        PullRequest pullRequest = pullRequestRepository
                .findByTenantIdAndRepoNameAndMergeSha(tenantId, detail.repoFullName(), detail.mergeSha())
                .orElseGet(() -> PullRequest.create(
                        tenantId, detail.repoFullName(), detail.prNumber(), detail.mergeSha()));

        pullRequest.setPrNumber(detail.prNumber());
        pullRequest.setTitle(detail.title());
        pullRequest.setDescription(detail.description());
        pullRequest.setAuthor(detail.author());
        pullRequest.setBranch(detail.branch());
        pullRequest.setTicketKey(detail.ticketKey());
        pullRequest.setMergedAt(detail.mergedAt());
        pullRequest.setChangedFiles(jsonMapper.toJson(detail.changedFiles()));

        return pullRequestRepository.save(pullRequest);
    }

    /**
     * Only merged pull requests are production-bound. An opened or synchronised
     * PR is not yet a change to Lilly's platform, and analysing it would fill the
     * Knowledge Repository with changes that never shipped.
     */
    private boolean isMergedPullRequest(String rawBody) {
        JsonNode root = jsonMapper.readTree(rawBody);
        JsonNode pr = root.path("pull_request");
        if (pr.isMissingNode()) {
            return false;
        }
        // GitHub signals a merge as action=closed with merged=true. Saved demo
        // payloads sometimes carry only merged_at, so either is accepted.
        boolean merged = pr.path("merged").asBoolean(false)
                || !"null".equals(pr.path("merged_at").asText("null"));
        return merged;
    }
}
