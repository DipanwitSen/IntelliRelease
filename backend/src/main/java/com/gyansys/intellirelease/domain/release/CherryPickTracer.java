package com.gyansys.intellirelease.domain.release;

import com.gyansys.intellirelease.adapters.git.CommitRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves cherry-picked commits back to the change they came from.
 *
 * <p>A hotfix cherry-picked onto a release branch has a different SHA from the
 * commit on main. Counted naively it appears twice — once as itself and once as
 * its origin — and the release announces the same fix to the client twice.
 *
 * <p>Relies on the {@code (cherry picked from commit ...)} trailer that
 * {@code git cherry-pick -x} writes. When the trailer is absent the commit is
 * treated as an ordinary change: inferring a cherry-pick from a matching diff
 * would be a guess, and this engine does not guess.
 */
@Component
public class CherryPickTracer {

    private static final Logger log = LoggerFactory.getLogger(CherryPickTracer.class);

    /** Commits after de-duplication, plus the trailers that were resolved. */
    public record TraceResult(List<CommitRef> deduplicated, List<String> notes) {
    }

    public TraceResult trace(List<CommitRef> commits) {
        if (commits == null || commits.isEmpty()) {
            return new TraceResult(List.of(), List.of());
        }

        Set<String> presentShas = new HashSet<>();
        for (CommitRef commit : commits) {
            if (commit.sha() != null) {
                presentShas.add(commit.sha());
            }
        }

        List<CommitRef> deduplicated = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> seenOrigins = new HashSet<>();

        for (CommitRef commit : commits) {
            if (!commit.isCherryPick()) {
                deduplicated.add(commit);
                continue;
            }

            String origin = commit.cherryPickedFrom();
            boolean originAlsoInRange = presentShas.stream().anyMatch(sha -> sha.startsWith(origin));

            if (originAlsoInRange || !seenOrigins.add(origin)) {
                // The same logical change is already represented in this release.
                notes.add("Commit " + commit.shortSha() + " is a cherry-pick of " + origin
                        + ", which is already included in this release — counted once");
                log.debug("Collapsed cherry-pick {} onto origin {}", commit.shortSha(), origin);
                continue;
            }

            notes.add("Commit " + commit.shortSha() + " is a cherry-pick of " + origin
                    + " from outside this range — counted as a change in this release");
            deduplicated.add(commit);
        }

        return new TraceResult(deduplicated, notes);
    }
}
