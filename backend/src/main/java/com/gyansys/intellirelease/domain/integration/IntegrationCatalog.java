package com.gyansys.intellirelease.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.domain.context.knowledge.PathGlob;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.ApiDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.Catalogue;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.DiscoveryRule;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.FlowDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingSetDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.SamplePayload;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.TopologyProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads {@code integration_catalog.json} once at startup and answers questions
 * about the landscape.
 *
 * <p>Same discipline as {@code SapCommerceKnowledgeBase}: hand-curated domain
 * knowledge read from a versioned data file, never inferred by a model. An SAP
 * integration consultant can revise this catalogue without touching Java, and
 * every answer still traces back to one named rule at one priority in one file.
 *
 * <p>Rules are pre-compiled and sorted by priority once, in the constructor.
 * Classification runs on every changed file of every analysed pull request, so
 * recompiling a regex per call would be the difference between microseconds and
 * milliseconds across a large release.
 */
@Component
public class IntegrationCatalog {

    private static final Logger log = LoggerFactory.getLogger(IntegrationCatalog.class);
    private static final String RESOURCE_PATH = "/integration/integration_catalog.json";

    private final Catalogue catalogue;
    private final List<CompiledDiscoveryRule> discoveryRules;

    private final Map<String, InterfaceDefinition> interfacesById;
    private final Map<String, FlowDefinition> flowsById;
    private final Map<String, MappingSetDefinition> mappingSetsById;
    private final Map<String, ApiDefinition> apisById;
    private final Map<String, SamplePayload> payloadsById;
    private final Map<String, TopologyProfile> topologiesById;

    /** API contract globs, compiled once — used to flag "changed in this window". */
    private final Map<String, List<Pattern>> apiContractPatterns;

    public IntegrationCatalog(ObjectMapper objectMapper) {
        this.catalogue = load(objectMapper);

        this.discoveryRules = catalogue.discoveryRules().stream()
                .sorted(Comparator.comparingInt(DiscoveryRule::priority).reversed())
                .map(rule -> new CompiledDiscoveryRule(PathGlob.compile(rule.pattern()), rule))
                .toList();

        this.interfacesById = index(catalogue.interfaces(), InterfaceDefinition::id);
        this.flowsById = index(catalogue.flows(), FlowDefinition::id);
        this.mappingSetsById = index(catalogue.mappingSets(), MappingSetDefinition::id);
        this.apisById = index(catalogue.apis(), ApiDefinition::id);
        this.payloadsById = index(catalogue.samplePayloads(), SamplePayload::id);
        this.topologiesById = index(catalogue.topologies(), TopologyProfile::id);

        this.apiContractPatterns = catalogue.apis().stream().collect(Collectors.toMap(
                ApiDefinition::id,
                api -> api.contractPaths().stream().map(PathGlob::compile).toList()));

        log.info("Integration catalogue {} loaded: {} interfaces, {} flows, {} mapping sets, {} APIs, {} discovery rules",
                version(), interfacesById.size(), flowsById.size(), mappingSetsById.size(),
                apisById.size(), discoveryRules.size());
    }

    /* ------------------------------------------------------------ lookups */

    public List<InterfaceDefinition> interfaces() {
        return catalogue.interfaces();
    }

    public List<FlowDefinition> flows() {
        return catalogue.flows();
    }

    public List<MappingSetDefinition> mappingSets() {
        return catalogue.mappingSets();
    }

    public List<ApiDefinition> apis() {
        return catalogue.apis();
    }

    public List<SamplePayload> samplePayloads() {
        return catalogue.samplePayloads();
    }

    public List<TopologyProfile> topologies() {
        return catalogue.topologies();
    }

    public Optional<InterfaceDefinition> findInterface(String id) {
        return Optional.ofNullable(interfacesById.get(id));
    }

    public Optional<FlowDefinition> findFlow(String id) {
        return Optional.ofNullable(flowsById.get(id));
    }

    public Optional<MappingSetDefinition> findMappingSet(String id) {
        return Optional.ofNullable(mappingSetsById.get(id));
    }

    public Optional<ApiDefinition> findApi(String id) {
        return Optional.ofNullable(apisById.get(id));
    }

    public Optional<SamplePayload> findPayload(String id) {
        return Optional.ofNullable(payloadsById.get(id));
    }

    public Optional<TopologyProfile> findTopology(String id) {
        return Optional.ofNullable(topologiesById.get(id));
    }

    public String version() {
        return catalogue.meta() == null ? "unknown" : catalogue.meta().version();
    }

    /**
     * Only the topologies this landscape actually uses, in catalogue order.
     *
     * <p>Derived from the interfaces present rather than from a static list, so
     * a customer running nothing but nightly CSV gets exactly one topology and
     * never sees a middleware diagram they do not have.
     */
    public List<TopologyProfile> activeTopologies() {
        var used = catalogue.interfaces().stream()
                .map(InterfaceDefinition::topology)
                .collect(Collectors.toSet());
        return catalogue.topologies().stream()
                .filter(profile -> used.contains(profile.id()))
                .toList();
    }

    /* -------------------------------------------------------- classification */

    /**
     * All discovery rules that match a path, highest priority first.
     *
     * <p>Returns every match rather than only the winner: a single file can
     * legitimately affect an interface, an API contract and a mapping set at
     * once, and collapsing that to one answer would hide real impact.
     */
    public List<DiscoveryRule> rulesFor(String path) {
        String candidate = PathGlob.normalise(path);
        return discoveryRules.stream()
                .filter(compiled -> compiled.pattern.matcher(candidate).matches())
                .map(CompiledDiscoveryRule::rule)
                .toList();
    }

    /** True when this path is part of the named API's published contract. */
    public boolean touchesApiContract(String apiId, String path) {
        String candidate = PathGlob.normalise(path);
        return apiContractPatterns.getOrDefault(apiId, List.of()).stream()
                .anyMatch(pattern -> pattern.matcher(candidate).matches());
    }

    /** Every API whose contract one of these paths touches. */
    public List<String> apisTouchedBy(List<String> paths) {
        return catalogue.apis().stream()
                .map(ApiDefinition::id)
                .filter(apiId -> paths.stream().anyMatch(path -> touchesApiContract(apiId, path)))
                .toList();
    }

    /* ---------------------------------------------------------- internals */

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            result.put(key.apply(value), value);
        }
        return Map.copyOf(result);
    }

    private static Catalogue load(ObjectMapper objectMapper) {
        try (InputStream in = IntegrationCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Integration catalogue not found on classpath: " + RESOURCE_PATH);
            }
            return objectMapper.readValue(in, Catalogue.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load the integration catalogue", exception);
        }
    }

    private record CompiledDiscoveryRule(Pattern pattern, DiscoveryRule rule) {
    }
}
