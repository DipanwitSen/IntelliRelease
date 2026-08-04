package com.gyansys.intellirelease.domain.drift;

import com.gyansys.intellirelease.model.enums.DriftClassification;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects meaningful configuration differences across environments.
 *
 * <p>Structural, not textual. The engine knows that {@code payment.timeout: 8s}
 * versus {@code 8000ms} is the same value expressed twice, and that a reordered
 * comment block is not a change at all. "Works in QA, breaks in production" is a
 * Hybris classic; this surfaces the cause before the deploy rather than during
 * the incident.
 */
@Service
public class ConfigurationDriftEngine {

    public static final String ENGINE_VERSION = "drift-engine-2026.1";

    /** Keys whose difference is presentation only, whatever the value. */
    private static final Set<String> COSMETIC_KEY_FRAGMENTS = Set.of(
            "logging.pattern", "log.format", "logging.level.root.format",
            "banner", "comment", "description", "display-name"
    );

    /**
     * What a difference in a known key could actually do. Curated: an impact
     * line nobody wrote is an impact line nobody can defend.
     */
    private static final Map<String, String> KNOWN_IMPACTS = buildKnownImpacts();

    /**
     * Compares a candidate release's configuration against a baseline.
     *
     * @param baseline  typically the seeded production snapshot
     * @param candidate the configuration shipping in this release
     */
    public DriftResult compare(ConfigurationSnapshot baseline, ConfigurationSnapshot candidate) {
        if (baseline == null || baseline.isEmpty()) {
            return DriftResult.unavailable("No production configuration baseline is configured");
        }
        if (candidate == null) {
            candidate = ConfigurationSnapshot.empty("release");
        }

        Set<String> allKeys = new LinkedHashSet<>(baseline.values().keySet());
        allKeys.addAll(candidate.values().keySet());

        List<DriftItem> drifts = new ArrayList<>();
        int material = 0;
        int cosmetic = 0;

        for (String key : allKeys) {
            String baselineValue = baseline.get(key);
            String candidateValue = candidate.get(key);

            if (!differs(baselineValue, candidateValue)) {
                continue;
            }

            DriftClassification classification = classify(key, baselineValue, candidateValue);
            if (classification == DriftClassification.MATERIAL) {
                material++;
            } else {
                cosmetic++;
            }

            drifts.add(new DriftItem(
                    key,
                    baselineValue,
                    candidateValue,
                    classification,
                    describeImpact(key, classification, baselineValue, candidateValue),
                    describeEvidence(key, baselineValue, candidateValue, classification)
            ));
        }

        return new DriftResult(
                baseline.environment(),
                candidate.environment(),
                drifts,
                material,
                cosmetic,
                true,
                ProvenanceClass.RULE_OUTPUT
        );
    }

    // ------------------------------------------------------------------

    /**
     * True when the two values are semantically different. Whitespace, casing
     * of booleans, and equivalent time units are all the same value.
     */
    private boolean differs(String left, String right) {
        if (left == null && right == null) {
            return false;
        }
        if (left == null || right == null) {
            return true;
        }
        return !normalise(left).equals(normalise(right));
    }

    private String normalise(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        // Strip surrounding quotes: "8s" and 8s are the same setting.
        if (value.length() > 1
                && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        String asDuration = normaliseDuration(value);
        return asDuration != null ? asDuration : value;
    }

    /** Converts a duration literal to milliseconds so units cannot fake a drift. */
    private String normaliseDuration(String value) {
        try {
            if (value.endsWith("ms")) {
                return Long.parseLong(value.substring(0, value.length() - 2).trim()) + "ms";
            }
            if (value.endsWith("s") && !value.endsWith("ms")) {
                return Long.parseLong(value.substring(0, value.length() - 1).trim()) * 1000L + "ms";
            }
            if (value.endsWith("m")) {
                return Long.parseLong(value.substring(0, value.length() - 1).trim()) * 60_000L + "ms";
            }
            if (value.endsWith("h")) {
                return Long.parseLong(value.substring(0, value.length() - 1).trim()) * 3_600_000L + "ms";
            }
        } catch (NumberFormatException ignored) {
            // Not a duration. Fall through to plain string comparison.
        }
        return null;
    }

    private DriftClassification classify(String key, String baselineValue, String candidateValue) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String fragment : COSMETIC_KEY_FRAGMENTS) {
            if (lowerKey.contains(fragment)) {
                return DriftClassification.COSMETIC;
            }
        }
        // Everything that survives is behaviour-affecting until proven otherwise.
        // Conservative on purpose: a missed material drift is an incident, a
        // mislabelled cosmetic one is a sentence a reviewer skims past.
        return DriftClassification.MATERIAL;
    }

    private String describeImpact(String key, DriftClassification classification,
                                  String baselineValue, String candidateValue) {
        if (classification == DriftClassification.COSMETIC) {
            return "None — presentation only";
        }
        String known = KNOWN_IMPACTS.get(key.toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        if (baselineValue == null) {
            return "New setting not present in the baseline environment — behaviour in production "
                    + "is currently undefined for this key";
        }
        if (candidateValue == null) {
            return "Setting present in the baseline but absent from this release — the platform "
                    + "default will apply";
        }
        return "Behaviour governed by " + key + " changes between environments";
    }

    private String describeEvidence(String key, String baselineValue, String candidateValue,
                                    DriftClassification classification) {
        String from = baselineValue == null ? "(absent)" : baselineValue;
        String to = candidateValue == null ? "(absent)" : candidateValue;
        return key + ": " + from + " -> " + to
                + (classification == DriftClassification.COSMETIC
                   ? " (key matches a presentation-only pattern)"
                   : " (structural value difference)");
    }

    private static Map<String, String> buildKnownImpacts() {
        Map<String, String> impacts = new LinkedHashMap<>();
        impacts.put("session.timeout",
                "User sessions expire sooner or later than production — shorter timeouts risk cart "
                        + "abandonment mid-checkout");
        impacts.put("payment.timeout",
                "Potential checkout timeouts under payment gateway latency variance");
        impacts.put("solr.synonyms.version",
                "Search relevance may change for the affected terms");
        impacts.put("cache.ttl",
                "Cache staleness and backend load both shift");
        impacts.put("cronjob.batch.size",
                "Cronjob run duration and database contention change");
        impacts.put("media.max.upload.size",
                "Uploads above the new limit will be rejected");
        impacts.put("solr.commit.interval",
                "Index freshness changes: search may lag catalog updates");
        return Map.copyOf(impacts);
    }
}
