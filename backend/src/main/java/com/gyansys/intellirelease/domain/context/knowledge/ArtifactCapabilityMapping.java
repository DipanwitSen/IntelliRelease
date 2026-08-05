package com.gyansys.intellirelease.domain.context.knowledge;

import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.Map;

/**
 * Bridges the new, ~65-artifact-type knowledge base onto the existing
 * ~26-value {@link SapCapability} enum that Risk, Impact and Regression
 * already key their logic on.
 *
 * <p>This is scaffolding for a transition, not the destination. Phase 1 of
 * adopting {@code sap_context.json} enriches <em>classification</em> —
 * {@link com.gyansys.intellirelease.domain.context.FileContext} now carries
 * the full curated artifact type, business capability tags, deployment risk
 * band, potential impact and regression areas straight from the knowledge
 * base. But {@link com.gyansys.intellirelease.domain.risk.RiskEngine},
 * {@link com.gyansys.intellirelease.domain.impact.ImpactAnalyzer} and
 * {@link com.gyansys.intellirelease.domain.regression.RegressionRecommender}
 * still reason in terms of {@link SapCapability} this phase, so every
 * classified file needs a best-fit legacy value or those engines would treat
 * roughly half of the newly-recognised artifact types (interceptors,
 * strategies, hooks, business processes, patches, every frontend type — none
 * of which existed in the old vocabulary) as producing no capability at all.
 *
 * <p>The mapping is deliberately approximate — e.g. every frontend artifact
 * type folds into a small number of backend-shaped buckets, because no
 * frontend-aware capability existed before this knowledge base. Rewiring
 * Risk/Impact/Regression to reason natively over artifact types and their
 * own {@code deployment_risk} bands is later-phase work; until then this map
 * exists so nothing regresses to zero signal.
 */
public final class ArtifactCapabilityMapping {

    private static final Map<String, SapCapability> MAPPING = Map.ofEntries(
            Map.entry("items_xml", SapCapability.TYPE_SYSTEM),
            Map.entry("beans_xml", SapCapability.DATA_TRANSFORMATION),
            Map.entry("spring_xml", SapCapability.SPRING_CONFIGURATION),
            Map.entry("occ_web_spring", SapCapability.API_LAYER),
            Map.entry("occ_controller", SapCapability.API_LAYER),
            Map.entry("occ_filter", SapCapability.API_LAYER),
            Map.entry("validator", SapCapability.API_LAYER),
            Map.entry("facade", SapCapability.BUSINESS_LOGIC),
            Map.entry("service", SapCapability.BUSINESS_LOGIC),
            Map.entry("dao", SapCapability.DATA_ACCESS),
            Map.entry("populator", SapCapability.DATA_TRANSFORMATION),
            Map.entry("converter", SapCapability.DATA_TRANSFORMATION),
            Map.entry("interceptor", SapCapability.DATA_ACCESS),
            Map.entry("strategy", SapCapability.BUSINESS_LOGIC),
            Map.entry("hook", SapCapability.BUSINESS_LOGIC),
            Map.entry("event_or_listener", SapCapability.BUSINESS_LOGIC),
            Map.entry("business_process", SapCapability.BUSINESS_LOGIC),
            Map.entry("process_action", SapCapability.BUSINESS_LOGIC),
            Map.entry("cronjob", SapCapability.BACKGROUND_PROCESSING),
            Map.entry("key_generator", SapCapability.BUSINESS_LOGIC),
            Map.entry("solr_config", SapCapability.SEARCH_CONFIGURATION),
            Map.entry("solr_value_provider", SapCapability.SEARCH_CONFIGURATION),
            Map.entry("cms_component_type", SapCapability.CMS_CAPABILITY),
            Map.entry("cms_content", SapCapability.CMS_CAPABILITY),
            Map.entry("impex_general", SapCapability.DATA_IMPORT),
            Map.entry("impex_security", SapCapability.SECURITY),
            Map.entry("integration_object", SapCapability.INTEGRATION),
            Map.entry("hot_folder_config", SapCapability.DATA_IMPORT),
            Map.entry("patch", SapCapability.DATA_IMPORT),
            Map.entry("system_setup", SapCapability.DATA_IMPORT),
            Map.entry("backoffice_config", SapCapability.CONFIGURATION),
            Map.entry("backoffice_widget", SapCapability.CONFIGURATION),
            Map.entry("backoffice_spring", SapCapability.SECURITY),
            Map.entry("smartedit_config", SapCapability.CMS_CAPABILITY),
            Map.entry("email_template", SapCapability.CONFIGURATION),
            Map.entry("properties_extension", SapCapability.CONFIGURATION),
            Map.entry("properties_environment", SapCapability.CONFIGURATION),
            Map.entry("cloud_manifest", SapCapability.BUILD_CONFIGURATION),
            Map.entry("extension_set", SapCapability.BUILD_CONFIGURATION),
            Map.entry("extension_dependency", SapCapability.BUILD_CONFIGURATION),
            Map.entry("frontend_occ_config", SapCapability.API_LAYER),
            Map.entry("frontend_feature_registry", SapCapability.CMS_CAPABILITY),
            Map.entry("frontend_module", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_adapter", SapCapability.API_LAYER),
            Map.entry("frontend_connector", SapCapability.API_LAYER),
            Map.entry("frontend_service", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_component", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_guard", SapCapability.SECURITY),
            Map.entry("frontend_interceptor", SapCapability.API_LAYER),
            Map.entry("frontend_root_module", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_i18n", SapCapability.CONFIGURATION),
            Map.entry("frontend_dependencies", SapCapability.BUILD_CONFIGURATION),
            Map.entry("test", SapCapability.TEST),
            Map.entry("impex_store", SapCapability.DATA_IMPORT),
            Map.entry("impex_catalog", SapCapability.DATA_IMPORT),
            Map.entry("impex_cronjob", SapCapability.BACKGROUND_PROCESSING),
            Map.entry("impex_integration", SapCapability.INTEGRATION),
            Map.entry("patch_data", SapCapability.DATA_IMPORT),
            Map.entry("properties_local", SapCapability.CONFIGURATION),
            Map.entry("project_descriptor", SapCapability.BUILD_CONFIGURATION),
            Map.entry("backend_i18n", SapCapability.CONFIGURATION),
            Map.entry("template", SapCapability.CONFIGURATION),
            Map.entry("build_config", SapCapability.BUILD_CONFIGURATION),
            Map.entry("ci_pipeline", SapCapability.BUILD_CONFIGURATION),
            Map.entry("frontend_model", SapCapability.API_LAYER),
            Map.entry("frontend_component_template", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_pipe", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_style", SapCapability.BUSINESS_LOGIC),
            Map.entry("frontend_environment", SapCapability.CONFIGURATION),
            Map.entry("frontend_build_config", SapCapability.BUILD_CONFIGURATION)
    );

    private ArtifactCapabilityMapping() {
    }

    /** Falls back to UNCLASSIFIED only for an artifact type this map has never heard of. */
    public static SapCapability toLegacyCapability(String artifactType) {
        return MAPPING.getOrDefault(artifactType, SapCapability.UNCLASSIFIED);
    }
}
