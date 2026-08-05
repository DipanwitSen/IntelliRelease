package com.gyansys.intellirelease.domain.context.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One entry from {@code sap_context.json}'s {@code artifact_types} map — the
 * curated SAP Commerce/Hybris domain knowledge for a single artifact kind
 * (e.g. {@code items_xml}, {@code occ_controller}, {@code frontend_guard}).
 *
 * <p>This record only carries the fields the engine consumes today. The
 * source file also defines {@code sub_changes}, {@code high_risk_patterns},
 * {@code special_rules}, {@code risk_modifier}, {@code sub_types} and
 * {@code contract_pair} on some entries — deliberately not modelled yet.
 * Those drive risk amplification, contract-pair drift detection and
 * completeness checks, which are later phases of this same knowledge base,
 * not this one. {@code @JsonIgnoreProperties} lets the loader ignore them
 * without failing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactTypeDefinition(
        @JsonProperty("display_name") String displayName,
        String layer,
        @JsonProperty("general_role") String generalRole,
        @JsonProperty("client_implementation") String clientImplementation,
        @JsonProperty("generalized_meaning") String generalizedMeaning,
        @JsonProperty("business_capability") List<String> businessCapability,
        @JsonProperty("upstream_dependencies") List<String> upstreamDependencies,
        @JsonProperty("downstream_dependencies") List<String> downstreamDependencies,
        @JsonProperty("potential_impact") List<String> potentialImpact,
        @JsonProperty("regression_areas") List<String> regressionAreas,
        @JsonProperty("deployment_risk") String deploymentRisk,
        @JsonProperty("required_actions") List<String> requiredActions,
        @JsonProperty("related_configuration") List<String> relatedConfiguration,
        @JsonProperty("related_integrations") List<String> relatedIntegrations,
        @JsonProperty("alias_of") String aliasOf
) {
    public ArtifactTypeDefinition {
        businessCapability = businessCapability == null ? List.of() : List.copyOf(businessCapability);
        upstreamDependencies = upstreamDependencies == null ? List.of() : List.copyOf(upstreamDependencies);
        downstreamDependencies = downstreamDependencies == null ? List.of() : List.copyOf(downstreamDependencies);
        potentialImpact = potentialImpact == null ? List.of() : List.copyOf(potentialImpact);
        regressionAreas = regressionAreas == null ? List.of() : List.copyOf(regressionAreas);
        requiredActions = requiredActions == null ? List.of() : List.copyOf(requiredActions);
        relatedConfiguration = relatedConfiguration == null ? List.of() : List.copyOf(relatedConfiguration);
        relatedIntegrations = relatedIntegrations == null ? List.of() : List.copyOf(relatedIntegrations);
    }
}
