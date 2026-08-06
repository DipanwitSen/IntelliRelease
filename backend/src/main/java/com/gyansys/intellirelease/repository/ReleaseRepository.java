package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.Release;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, UUID> {

    List<Release> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /** Real total count via Spring Data's Page, for the {@code PageResponse} envelope. */
    Page<Release> findByTenantId(String tenantId, Pageable pageable);

    Optional<Release> findByTenantIdAndRepoNameAndVersion(String tenantId, String repoName, String version);

    /** Fetches the resolved PR set alongside the release in one query. */
    @EntityGraph(attributePaths = "pullRequests")
    Optional<Release> findWithPullRequestsByReleaseId(UUID releaseId);

    /** Deployed releases, most recent first — backs the Deployments screen. */
    List<Release> findByTenantIdAndDeployedAtIsNotNullOrderByDeployedAtDesc(String tenantId);

    long countByTenantIdAndDeployedAtIsNotNullAndDeployedAtAfter(String tenantId, OffsetDateTime after);
}
