package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.Release;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, UUID> {

    List<Release> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<Release> findByTenantIdAndRepoNameAndVersion(String tenantId, String repoName, String version);

    /** Fetches the resolved PR set alongside the release in one query. */
    @EntityGraph(attributePaths = "pullRequests")
    Optional<Release> findWithPullRequestsByReleaseId(UUID releaseId);
}
