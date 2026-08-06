package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Integration Center. Honestly empty: no customer has imported an
 * interface, flow or topology catalogue for this tenant yet — that ingestion
 * path ({@code IntegrationModel}) is data-modelled but not wired to a source.
 * Every response here says so via {@code unavailableReason} rather than
 * inventing a landscape.
 */
@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Integration Center", description = "Interface, topology and health catalogue — not yet populated")
public class IntegrationController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CountEntry(String key, String label, int count, String tone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrendPoint(String label, double value, String timestamp) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HealthSnapshot(String status, Double successRate, Double avgResponseMs, Double p95ResponseMs,
                                 int failedInterfaces, int totalInterfaces, Integer retryCount,
                                 Integer queueBacklog, Double avgProcessingMs, Integer dailyVolume,
                                 Integer peakVolume, String peakAt, List<CountEntry> topFailures,
                                 List<CountEntry> topInterfaces, List<TrendPoint> volumeTrend,
                                 List<TrendPoint> successTrend, String unavailableReason, String provenance) {
        static HealthSnapshot empty() {
            return new HealthSnapshot("NOT_CONFIGURED", null, null, null, 0, 0, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of(),
                    "No integration catalogue has been imported for this tenant yet", "UNKNOWN");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Overview(List<Object> topologies, int totalInterfaces, int inboundCount, int outboundCount,
                           int syncCount, int asyncCount, int batchCount, List<CountEntry> protocolBreakdown,
                           List<CountEntry> formatBreakdown, List<CountEntry> domainBreakdown, HealthSnapshot health) {
    }

    @GetMapping("/overview")
    @Operation(summary = "Landscape summary — empty until an interface catalogue is imported")
    public Overview overview() {
        return new Overview(List.of(), 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), HealthSnapshot.empty());
    }

    @GetMapping("/interfaces")
    @Operation(summary = "Integration interfaces — empty until an interface catalogue is imported")
    public PageResponse<Object> interfaces(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/interfaces/{id}")
    @Operation(summary = "One integration interface — not found until an interface catalogue is imported")
    public ResponseEntity<Object> getInterface(@PathVariable String id) {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Integration landscape health — unavailable until a catalogue and telemetry source exist")
    public HealthSnapshot health() {
        return HealthSnapshot.empty();
    }
}
