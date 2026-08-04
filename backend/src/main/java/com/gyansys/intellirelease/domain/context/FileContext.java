package com.gyansys.intellirelease.domain.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.List;

/**
 * What one changed file <em>means inside SAP Commerce</em>.
 *
 * <p>Provenance: DERIVED_FACT. Every instance is the output of a named,
 * versioned rule in {@link CapabilityLibrary} — never a model's opinion. The
 * {@code evidence} field carries the reason in words, so "how do you know?"
 * is answerable per file rather than per release.
 *
 * @param filePath        the path as reported by Git
 * @param sapCapability   primary Hybris meaning; {@link SapCapability#UNCLASSIFIED} when nothing matched
 * @param businessDomain  business capability detected in the name, when one is present; may be null
 * @param consequences    downstream considerations a Hybris engineer would infer
 * @param confidence      how firmly the rule identifies this file
 * @param evidence        the human-readable reason the rule fired
 * @param matchedRule     the rule name, for traceability back to the library
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileContext(
        String filePath,
        SapCapability sapCapability,
        SapCapability businessDomain,
        ProvenanceClass provenanceClass,
        List<String> consequences,
        ConfidenceLevel confidence,
        String evidence,
        String matchedRule
) {

    public FileContext {
        consequences = consequences == null ? List.of() : List.copyOf(consequences);
    }

    /**
     * An unmatched file. Recorded explicitly so downstream engines treat it
     * conservatively rather than assuming it is harmless.
     */
    public static FileContext unclassified(String filePath) {
        return new FileContext(
                filePath,
                SapCapability.UNCLASSIFIED,
                null,
                ProvenanceClass.UNKNOWN,
                List.of("Meaning could not be determined from the path — review manually"),
                ConfidenceLevel.LOW,
                "No curated SAP Commerce pattern matched this path. The engine does not guess.",
                "none"
        );
    }

    public boolean isClassified() {
        return sapCapability != SapCapability.UNCLASSIFIED;
    }

    /** Primary capability plus business domain, de-duplicated. */
    public List<SapCapability> allCapabilities() {
        if (businessDomain == null || businessDomain == sapCapability) {
            return List.of(sapCapability);
        }
        return List.of(sapCapability, businessDomain);
    }
}
