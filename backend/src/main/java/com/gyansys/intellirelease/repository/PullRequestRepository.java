package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.PullRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {

    /** The idempotency lookup: one merge SHA is one pull request, forever. */
    Optional<PullRequest> findByTenantIdAndRepoNameAndMergeSha(String tenantId, String repoName, String mergeSha);

    Optional<PullRequest> findByTenantIdAndRepoNameAndPrNumber(String tenantId, String repoName, Integer prNumber);

    List<PullRequest> findByTenantIdAndRepoNameOrderByMergedAtDesc(String tenantId, String repoName, Pageable pageable);

    List<PullRequest> findByTenantIdOrderByMergedAtDesc(String tenantId, Pageable pageable);

    List<PullRequest> findByTenantIdAndMergeShaIn(String tenantId, List<String> mergeShas);
}
