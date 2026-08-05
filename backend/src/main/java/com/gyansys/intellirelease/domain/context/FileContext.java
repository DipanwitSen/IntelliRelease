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
 * <p>{@code sapCapability}/{@code businessDomain} are the legacy vocabulary
 * that {@link com.gyansys.intellirelease.domain.risk.RiskEngine},
 * {@link com.gyansys.intellirelease.domain.impact.ImpactAnalyzer} and
 * {@link com.gyansys.intellirelease.domain.regression.RegressionRecommender}
 * still key their logic on. {@code artifactType} onward is the richer
 * classification sourced directly from {@code sap_context.json} — the full
 * curated SAP Commerce/Hybris artifact taxonomy, not yet wired into those
 * three engines' scoring (that is later-phase work), but already available
 * for AI narration and for a human reading the raw analysis.
 *
 * @param filePath              the path as reported by Git
 * @param sapCapability         primary Hybris meaning; {@link SapCapability#UNCLASSIFIED} when nothing matched
 * @param businessDomain        business capability detected in the name, when one is present; may be null
 * @param consequences          downstream considerations a Hybris engineer would infer
 * @param confidence            how firmly the rule identifies this file
 * @param evidence              the human-readable reason the rule fired
 * @param matchedRule           the rule name, for traceability back to the library
 * @param artifactType          the knowledge base's artifact type key (e.g. {@code "items_xml"}); null when unclassified
 * @param artifactDisplayName   human-readable name of the artifact type
 * @param generalRole           what this artifact type does in SAP Commerce, in general
 * @param businessCapabilityTags business capability tags the knowledge base attaches to this artifact type
 * @param deploymentRiskBand    the knowledge base's own risk band for this artifact type (critical/high/medium/low)
 * @param knowledgeBaseImpact   potential downstream impact, as catalogued in the knowledge base
 * @param regressionAreas       suggested regression scope, as catalogued in the knowledge base
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
        String matchedRule,
        String artifactType,
        String artifactDisplayName,
        String generalRole,
        List<String> businessCapabilityTags,
        String deploymentRiskBand,
        List<String> knowledgeBaseImpact,
        List<String> regressionAreas
) {

    public FileContext {
        consequences = consequences == null ? List.of() : List.copyOf(consequences);
        businessCapabilityTags = businessCapabilityTags == null ? List.of() : List.copyOf(businessCapabilityTags);
        knowledgeBaseImpact = knowledgeBaseImpact == null ? List.of() : List.copyOf(knowledgeBaseImpact);
        regressionAreas = regressionAreas == null ? List.of() : List.copyOf(regressionAreas);
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
                "none",
                null, null, null, List.of(), null, List.of(), List.of()
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
