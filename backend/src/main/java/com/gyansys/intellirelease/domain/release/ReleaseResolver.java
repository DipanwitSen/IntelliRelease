package com.gyansys.intellirelease.domain.release;

import com.gyansys.intellirelease.adapters.git.CommitRef;
import com.gyansys.intellirelease.adapters.git.GitProvider;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Assembles the true contents of a release using Git as the source of truth.
 *
 * <p>Git graph diff between two refs, then cherry-pick resolution, then revert
 * netting. Not a date range: SAP Commerce releases in a mature programme have
 * hotfixes, cherry-picks and rollbacks, and a calendar window reports all three
 * wrong.
 *
 * <p>Provenance: FACT. What shipped is observed from the commit graph, and the
 * AI is never consulted about it.
 */
@Service
public class ReleaseResolver {

    private static final Logger log = LoggerFactory.getLogger(ReleaseResolver.class);

    /**
     * @param prNumbers    pull requests genuinely shipping in this release
     * @param mergeShas    the commits behind them
     * @param excluded     changes deliberately left out, each with evidence
     * @param resolverNotes cherry-pick resolutions worth showing a reader
     */
    public record ResolvedRelease(
            List<Integer> prNumbers,
            List<String> mergeShas,
            List<ExcludedChange> excluded,
            List<String> resolverNotes,
            int commitsExamined,
            boolean gitAvailable,
            ProvenanceClass provenanceClass
    ) {
        public ResolvedRelease {
            prNumbers = prNumbers == null ? List.of() : List.copyOf(prNumbers);
            mergeShas = mergeShas == null ? List.of() : List.copyOf(mergeShas);
            excluded = excluded == null ? List.of() : List.copyOf(excluded);
            resolverNotes = resolverNotes == null ? List.of() : List.copyOf(resolverNotes);
        }

        static ResolvedRelease unavailable() {
            return new ResolvedRelease(List.of(), List.of(), List.of(),
                    List.of("Git provider is not configured — release contents could not be resolved "
                            + "from the commit graph"),
                    0, false, ProvenanceClass.UNKNOWN);
        }
    }

    private final GitProvider gitProvider;
    private final CherryPickTracer cherryPickTracer;
    private final RevertNetter revertNetter;

    public ReleaseResolver(GitProvider gitProvider,
                           CherryPickTracer cherryPickTracer,
                           RevertNetter revertNetter) {
        this.gitProvider = gitProvider;
        this.cherryPickTracer = cherryPickTracer;
        this.revertNetter = revertNetter;
    }

    public ResolvedRelease resolve(String repoFullName, String fromRef, String toRef) {
        List<CommitRef> commits = gitProvider.listCommitsBetween(repoFullName, fromRef, toRef);

        if (commits.isEmpty()) {
            if (!gitProvider.isConfigured()) {
                return ResolvedRelease.unavailable();
            }
            log.info("No commits between {} and {} on {}", fromRef, toRef, repoFullName);
            return new ResolvedRelease(List.of(), List.of(), List.of(),
                    List.of("No commits found between " + fromRef + " and " + toRef),
                    0, true, ProvenanceClass.FACT);
        }

        // Order matters. Collapse duplicates first, then cancel out reverts —
        // netting a revert against a cherry-picked copy of its target would
        // leave the original standing and under-report the exclusion.
        CherryPickTracer.TraceResult traced = cherryPickTracer.trace(commits);
        RevertNetter.NettingResult netted = revertNetter.net(traced.deduplicated());

        Set<Integer> prNumbers = new LinkedHashSet<>();
        List<String> mergeShas = new ArrayList<>();

        for (CommitRef commit : netted.surviving()) {
            if (commit.pullRequestNumber() != null) {
                prNumbers.add(commit.pullRequestNumber());
            }
            mergeShas.add(commit.sha());
        }

        log.info("Resolved release {}..{} on {}: {} commits examined, {} PRs included, {} excluded",
                fromRef, toRef, repoFullName, commits.size(), prNumbers.size(), netted.excluded().size());

        return new ResolvedRelease(
                new ArrayList<>(prNumbers),
                mergeShas,
                netted.excluded(),
                traced.notes(),
                commits.size(),
                true,
                ProvenanceClass.FACT
        );
    }
}
