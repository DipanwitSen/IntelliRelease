package com.gyansys.intellirelease.domain.readiness;

import com.gyansys.intellirelease.domain.cleanup.CleanupAdvice;
import com.gyansys.intellirelease.domain.drift.DriftItem;
import com.gyansys.intellirelease.domain.drift.DriftResult;
import com.gyansys.intellirelease.domain.impact.ImpactItem;
import com.gyansys.intellirelease.domain.risk.RiskResult;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes one explainable readiness score from every other engine.
 *
 * <p>Weighted composition over six factors plus attention flags:
 * <pre>
 *   Aggregate risk        /25
 *   Regression coverage   /20
 *   Configuration drift   /10
 *   Cleanup health        /10
 *   QA readiness          /15
 *   Human approval        /15   (blocker)
 *   Attention flags       -1 to -5 each
 * </pre>
 *
 * <p>Human approval is a blocker, not merely a heavy weight: without it the
 * ceiling is {@link #MAX_SCORE_WITHOUT_APPROVAL} and the status can never be
 * READY. That asymmetry is deliberate — it is the difference between a gate and
 * a strong suggestion.
 *
 * <p>The AI never invents this number. It receives the factors below and
 * narrates them.
 */
@Service
public class DeploymentReadinessEngine {

    public static final String ENGINE_VERSION = "readiness-engine-2026.1";

    static final int RISK_MAX = 25;
    static final int REGRESSION_MAX = 20;
    static final int DRIFT_MAX = 10;
    static final int CLEANUP_MAX = 10;
    static final int QA_MAX = 15;
    static final int APPROVAL_MAX = 15;

    /** Ceiling while approval is pending: 100 - APPROVAL_MAX. */
    public static final int MAX_SCORE_WITHOUT_APPROVAL = 85;

    /** At or above this, and with nothing outstanding, a release is READY. */
    static final int READY_THRESHOLD = 85;

    /** Below this the release is NOT_READY regardless of approval. */
    static final int NOT_READY_THRESHOLD = 60;

    public ReadinessResult evaluate(ReadinessInput input) {
        List<ReadinessFactor> factors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int score = 0;
        score += scoreRisk(input, factors);
        score += scoreRegression(input, factors, warnings);
        score += scoreDrift(input, factors, warnings);
        score += scoreCleanup(input, factors, warnings);
        score += scoreQa(input, factors, warnings);
        score += scoreApproval(input, factors, warnings);
        score += applyAttentionFlags(input, factors, warnings);

        score = Math.max(0, Math.min(100, score));

        return new ReadinessResult(
                score,
                determineStatus(score, input, warnings),
                factors,
                warnings,
                ENGINE_VERSION,
                ProvenanceClass.RULE_OUTPUT
        );
    }

    // ------------------------------------------------------------------

    /** Risk contributes inversely: a score of 0 earns the full 25 points. */
    private int scoreRisk(ReadinessInput input, List<ReadinessFactor> factors) {
        RiskResult risk = input.risk();
        if (risk == null) {
            factors.add(ReadinessFactor.of("RISK", "Aggregate risk", 0, RISK_MAX,
                    "No risk analysis available for this release"));
            return 0;
        }
        int awarded = Math.max(0, RISK_MAX - (risk.score() * RISK_MAX / 100));
        factors.add(ReadinessFactor.of("RISK", "Aggregate risk", awarded, RISK_MAX,
                "Aggregate risk scored " + risk.score() + " (" + risk.level() + "): " + risk.summarise()));
        return awarded;
    }

    private int scoreRegression(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        int suggested = input.regressionSuggested();
        if (suggested == 0) {
            factors.add(ReadinessFactor.of("REGRESSION", "Regression coverage", REGRESSION_MAX, REGRESSION_MAX,
                    "No regression suites were suggested for this release"));
            return REGRESSION_MAX;
        }
        int executed = Math.min(input.regressionExecuted(), suggested);
        int awarded = executed * REGRESSION_MAX / suggested;
        int pct = executed * 100 / suggested;

        if (pct < 100) {
            warnings.add("Suggested regression is " + pct + "% executed (" + executed + " of "
                    + suggested + "); the remaining suites should complete before deploy");
        }
        factors.add(ReadinessFactor.of("REGRESSION", "Regression coverage", awarded, REGRESSION_MAX,
                suggested + " suites suggested, " + executed + " executed (" + pct + "%)"));
        return awarded;
    }

    private int scoreDrift(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        DriftResult drift = input.drift();

        if (drift == null || !drift.baselineAvailable()) {
            // Cannot check is not the same as nothing found. Half credit, stated plainly.
            factors.add(ReadinessFactor.of("DRIFT", "Configuration drift", DRIFT_MAX / 2, DRIFT_MAX,
                    "No production configuration baseline was available — drift could not be verified"));
            warnings.add("Configuration drift could not be verified: no production baseline configured");
            return DRIFT_MAX / 2;
        }

        int material = drift.materialCount();
        if (material == 0) {
            factors.add(ReadinessFactor.of("DRIFT", "Configuration drift", DRIFT_MAX, DRIFT_MAX,
                    "No material configuration drift against the " + drift.baselineEnvironment() + " baseline"));
            return DRIFT_MAX;
        }

        int awarded = Math.max(0, DRIFT_MAX - material * 5);
        for (DriftItem item : drift.materialDrifts()) {
            warnings.add("Material configuration drift on " + item.settingKey() + " ("
                    + item.baselineValue() + " -> " + item.currentValue() + "): " + item.potentialImpact());
        }
        factors.add(ReadinessFactor.of("DRIFT", "Configuration drift", awarded, DRIFT_MAX,
                material + " material drift(s) against the " + drift.baselineEnvironment() + " baseline"));
        return awarded;
    }

    private int scoreCleanup(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        List<CleanupAdvice> advice = input.cleanupAdvice();
        if (advice.isEmpty()) {
            factors.add(ReadinessFactor.of("CLEANUP", "Cleanup health", CLEANUP_MAX, CLEANUP_MAX,
                    "No scheduled cleanup analysis available; no unhealthy jobs reported"));
            return CLEANUP_MAX;
        }

        long unhealthy = advice.stream().filter(item -> !item.healthy()).count();
        if (unhealthy == 0) {
            factors.add(ReadinessFactor.of("CLEANUP", "Cleanup health", CLEANUP_MAX, CLEANUP_MAX,
                    "All " + advice.size() + " analysed cleanup cronjobs are executing successfully"));
            return CLEANUP_MAX;
        }

        int awarded = Math.max(0, CLEANUP_MAX - (int) unhealthy * 5);
        warnings.add(unhealthy + " cleanup cronjob(s) are failing; database growth is unmanaged for those tables");
        factors.add(ReadinessFactor.of("CLEANUP", "Cleanup health", awarded, CLEANUP_MAX,
                unhealthy + " of " + advice.size() + " cleanup cronjobs are failing"));
        return awarded;
    }

    private int scoreQa(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        if (input.qaSignedOff()) {
            factors.add(ReadinessFactor.of("QA", "QA readiness", QA_MAX, QA_MAX, "QA has signed off"));
            return QA_MAX;
        }
        warnings.add("QA has not signed off on this release");
        factors.add(ReadinessFactor.of("QA", "QA readiness", 0, QA_MAX, "QA sign-off is outstanding"));
        return 0;
    }

    private int scoreApproval(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        if (input.humanApproved()) {
            factors.add(ReadinessFactor.blocker("APPROVAL", "Human approval", APPROVAL_MAX, APPROVAL_MAX,
                    "Client-facing communication has been approved by a named human"));
            return APPROVAL_MAX;
        }
        warnings.add("Human approval is pending — this is a blocker for external communication");
        factors.add(ReadinessFactor.blocker("APPROVAL", "Human approval", 0, APPROVAL_MAX,
                "Approval pending. External communication is structurally blocked until a "
                        + "designated approver signs off. Maximum achievable score is "
                        + MAX_SCORE_WITHOUT_APPROVAL + " until then."));
        return 0;
    }

    /**
     * Small penalties for capabilities that warrant a second look regardless of
     * how the rest of the release scored.
     */
    private int applyAttentionFlags(ReadinessInput input, List<ReadinessFactor> factors, List<String> warnings) {
        if (input.impact() == null) {
            return 0;
        }
        int penalty = 0;
        for (ImpactItem item : input.impact().confirmedImpact()) {
            SapCapability capability = item.capability();
            if (capability == SapCapability.CHECKOUT_CAPABILITY || capability == SapCapability.PAYMENT) {
                factors.add(ReadinessFactor.attention("ATTENTION_CHECKOUT", "Checkout or payment modified", 3,
                        "A revenue-critical path changed: " + item.evidence()));
                penalty -= 3;
            } else if (capability == SapCapability.OCC_API) {
                factors.add(ReadinessFactor.attention("ATTENTION_API", "API contract changed", 2,
                        "Downstream consumers bind to this contract: " + item.evidence()));
                penalty -= 2;
            } else if (capability == SapCapability.TYPE_SYSTEM) {
                factors.add(ReadinessFactor.attention("ATTENTION_TYPE_SYSTEM", "Type system changed", 3,
                        "A system update is required and a Solr reindex may be needed: " + item.evidence()));
                penalty -= 3;
            } else if (capability == SapCapability.SECURITY) {
                factors.add(ReadinessFactor.attention("ATTENTION_SECURITY", "Security changed", 2,
                        "Authentication or session behaviour changed: " + item.evidence()));
                penalty -= 2;
            }
        }
        if (penalty < 0) {
            warnings.add("Attention areas flagged: " + Math.abs(penalty) + " point(s) deducted for "
                    + "business-critical capabilities in this release");
        }
        return penalty;
    }

    private ReadinessStatus determineStatus(int score, ReadinessInput input, List<String> warnings) {
        // Approval is a gate, not a weight. Without it the release is never READY,
        // however well every other factor scored.
        if (!input.humanApproved()) {
            return ReadinessStatus.NOT_READY;
        }
        if (score >= READY_THRESHOLD && warnings.isEmpty()) {
            return ReadinessStatus.READY;
        }
        if (score >= NOT_READY_THRESHOLD) {
            return ReadinessStatus.READY_WITH_WARNINGS;
        }
        return ReadinessStatus.NOT_READY;
    }
}
