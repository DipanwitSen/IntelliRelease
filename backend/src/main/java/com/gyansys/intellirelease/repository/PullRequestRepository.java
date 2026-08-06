package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.PullRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Real total count via Spring Data's Page, for the {@code PageResponse} envelope. */
    Page<PullRequest> findByTenantId(String tenantId, Pageable pageable);

    List<PullRequest> findByTenantIdAndRepoName(String tenantId, String repoName);

    /**
     * Distinct repos this tenant has captured PRs for — backs the Repositories screen.
     * Explicit JPQL rather than a derived query: {@code findDistinctRepoNameByTenantId}
     * parses ambiguously and Hibernate resolves the projection to the whole entity.
     */
    @Query("select distinct pr.repoName from PullRequest pr where pr.tenantId = :tenantId")
    List<String> findDistinctRepoNameByTenantId(@Param("tenantId") String tenantId);
}
