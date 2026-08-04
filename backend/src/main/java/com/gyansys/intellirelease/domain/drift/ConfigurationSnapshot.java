package com.gyansys.intellirelease.domain.drift;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flattened view of one environment's configuration at a point in time.
 *
 * <p>Flattened deliberately: {@code spring.datasource.url} rather than a nested
 * tree, so two snapshots from different formats (properties and YAML) can be
 * compared key by key.
 *
 * <p>In the POC the production snapshot is seeded from
 * {@code sample-data/prod_config.yml}. In a pilot it is read from the live
 * configuration source — the engine does not change either way.
 */
public record ConfigurationSnapshot(String environment, Map<String, String> values) {

    public ConfigurationSnapshot {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ConfigurationSnapshot empty(String environment) {
        return new ConfigurationSnapshot(environment, Map.of());
    }

    public static ConfigurationSnapshot of(String environment, Map<String, String> values) {
        return new ConfigurationSnapshot(environment, new LinkedHashMap<>(values));
    }

    public String get(String key) {
        return values.get(key);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
