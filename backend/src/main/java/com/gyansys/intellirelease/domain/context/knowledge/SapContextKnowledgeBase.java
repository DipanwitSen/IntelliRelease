package com.gyansys.intellirelease.domain.context.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Root shape of {@code sap_context.json} — only the sections this phase of
 * the engine consumes. See {@link ArtifactTypeDefinition} for why the rest of
 * the file (propagation rules, critical paths, contract pairs, completeness
 * rules, the risk model, the regression map) is deliberately not modelled
 * here yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SapContextKnowledgeBase(
        Meta meta,
        @JsonProperty("file_classification") List<FileClassificationRule> fileClassification,
        @JsonProperty("artifact_types") Map<String, ArtifactTypeDefinition> artifactTypes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, String purpose) {
    }
}
