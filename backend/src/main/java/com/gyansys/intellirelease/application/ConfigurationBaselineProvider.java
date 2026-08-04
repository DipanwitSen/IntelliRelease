package com.gyansys.intellirelease.application;

import com.gyansys.intellirelease.domain.drift.ConfigurationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supplies the production configuration baseline the Drift Engine compares
 * against.
 *
 * <p>In the POC this is a seeded snapshot ({@code sample-data/prod_config.yml});
 * in a pilot it is read from the customer's live configuration source. The
 * engine is unchanged either way — only this provider is swapped, which is why
 * drift detection is not demo scaffolding.
 *
 * <p>When no baseline is available the provider returns empty and the Drift
 * Engine reports "could not verify" rather than "no drift found". Those are
 * different statements and only one of them is honest.
 */
@Component
public class ConfigurationBaselineProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationBaselineProvider.class);

    private final ResourceLoader resourceLoader;
    private final String baselineLocation;

    public ConfigurationBaselineProvider(
            ResourceLoader resourceLoader,
            @Value("${intellirelease.sample-data.prod-config:classpath:sample-data/prod_config.yml}")
            String baselineLocation) {
        this.resourceLoader = resourceLoader;
        this.baselineLocation = baselineLocation;
    }

    public ConfigurationSnapshot productionBaseline() {
        return load("production", baselineLocation);
    }

    /**
     * The configuration shipping in a release. The POC reads the same seeded
     * file's {@code release} section; a pilot reads the release branch's config.
     */
    public ConfigurationSnapshot releaseSnapshot(String version) {
        return load("release:" + version, baselineLocation, "release");
    }

    private ConfigurationSnapshot load(String environment, String location) {
        return load(environment, location, "production");
    }

    private ConfigurationSnapshot load(String environment, String location, String section) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("No configuration baseline at {} — drift will be reported as unverifiable", location);
            return ConfigurationSnapshot.empty(environment);
        }

        try (InputStream input = resource.getInputStream()) {
            Object parsed = new Yaml().load(input);
            if (!(parsed instanceof Map<?, ?> root)) {
                return ConfigurationSnapshot.empty(environment);
            }
            Object sectionNode = root.get(section);
            Map<String, String> flattened = new LinkedHashMap<>();
            flatten("", sectionNode instanceof Map<?, ?> ? sectionNode : root, flattened);
            return ConfigurationSnapshot.of(environment, flattened);
        } catch (Exception exception) {
            log.warn("Failed to read configuration baseline {}: {}", location, exception.getMessage());
            return ConfigurationSnapshot.empty(environment);
        }
    }

    /** Nested YAML becomes {@code a.b.c} keys so two formats compare cleanly. */
    private void flatten(String prefix, Object node, Map<String, String> target) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
                flatten(path, value, target);
            });
        } else if (node != null) {
            target.put(prefix, String.valueOf(node));
        }
    }
}
