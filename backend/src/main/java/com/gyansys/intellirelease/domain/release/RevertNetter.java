package com.gyansys.intellirelease.domain.release;

import com.gyansys.intellirelease.adapters.git.CommitRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cancels out commits that were merged and then reverted inside the same
 * release range.
 *
 * <p>This is the difference between a report a release manager can trust and one
 * they cannot. A date-based tool sees the original merge, does not understand
 * the revert, and announces a change to the client that will never reach
 * production.
 *
 * <p>A revert <em>outside</em> the range is deliberately not netted: if the
 * original shipped in an earlier release and the revert ships in this one, then
 * this release genuinely does remove that behaviour and must say so.
 */
@Component
public class RevertNetter {

    private static final Logger log = LoggerFactory.getLogger(RevertNetter.class);

    /** Both the surviving commits and the pairs that cancelled. */
    public record NettingResult(List<CommitRef> surviving, List<ExcludedChange> excluded) {
    }

    public NettingResult net(List<CommitRef> commits) {
        if (commits == null || commits.isEmpty()) {
            return new NettingResult(List.of(), List.of());
        }

        Map<String, CommitRef> bySha = new HashMap<>();
        for (CommitRef commit : commits) {
            if (commit.sha() != null) {
                bySha.put(commit.sha(), commit);
            }
        }

        Set<String> cancelled = new HashSet<>();
        List<ExcludedChange> excluded = new ArrayList<>();

        for (CommitRef commit : commits) {
            if (!commit.isRevert()) {
                continue;
            }
            CommitRef original = resolve(bySha, commit.revertsSha());
            if (original == null) {
                // Reverting something from an earlier release: this release really
                // does remove that behaviour, so both commits stay.
                log.debug("Revert {} targets {} which is outside the release range — not netted",
                        commit.shortSha(), commit.revertsSha());
                continue;
            }

            cancelled.add(original.sha());
            cancelled.add(commit.sha());
            excluded.add(ExcludedChange.reverted(
                    original.pullRequestNumber(),
                    original.sha(),
                    firstLine(original.message()),
                    commit.sha()));
        }

        List<CommitRef> surviving = commits.stream()
                .filter(commit -> !cancelled.contains(commit.sha()))
                .toList();

        if (!excluded.isEmpty()) {
            log.info("Revert netting removed {} change(s) from the release", excluded.size());
        }
        return new NettingResult(surviving, excluded);
    }

    /**
     * Git abbreviates SHAs in revert messages, so an exact map lookup misses.
     * Falls back to prefix matching, which is what a human reading the log does.
     */
    private CommitRef resolve(Map<String, CommitRef> bySha, String reference) {
        if (reference == null) {
            return null;
        }
        CommitRef exact = bySha.get(reference);
        if (exact != null) {
            return exact;
        }
        return bySha.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(reference))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return null;
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
