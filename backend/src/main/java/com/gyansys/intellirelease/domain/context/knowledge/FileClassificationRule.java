package com.gyansys.intellirelease.domain.context.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One glob pattern to artifact-type mapping, as authored in
 * {@code sap_context.json}'s {@code file_classification} array.
 *
 * <p>{@code priority} — not array order — decides which rule wins when a path
 * matches more than one pattern, so the highest-priority (most specific) match
 * always takes precedence regardless of where it sits in the source file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileClassificationRule(
        String pattern,
        @JsonProperty("artifact_type") String artifactType,
        int priority
) {
}
