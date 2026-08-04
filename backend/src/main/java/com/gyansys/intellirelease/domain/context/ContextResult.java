package com.gyansys.intellirelease.domain.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Context Engine's verdict on a whole pull request.
 *
 * <p>This is the input every other deterministic engine reads. Impact,
 * Regression, Risk and Drift all run off {@link #capabilities()} — which is why
 * an inaccurate library here degrades everything downstream, and why nothing
 * here is left to inference.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextResult(
        List<FileContext> files,
        Set<SapCapability> capabilities,
        int fileCount,
        int unclassifiedCount,
        boolean testsIncluded,
        String libraryVersion,
        ProvenanceClass provenanceClass
) {

    public ContextResult {
        files = files == null ? List.of() : List.copyOf(files);
        capabilities = capabilities == null ? Set.of() : new LinkedHashSet<>(capabilities);
    }

    public boolean hasCapability(SapCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean hasAnyCapability(SapCapability... candidates) {
        for (SapCapability candidate : candidates) {
            if (capabilities.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Files matching a given capability — used to build evidence strings. */
    public List<String> pathsFor(SapCapability capability) {
        return files.stream()
                .filter(file -> file.allCapabilities().contains(capability))
                .map(FileContext::filePath)
                .toList();
    }
}
