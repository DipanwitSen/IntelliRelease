package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.CleanupRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CleanupRecommendationRepository extends JpaRepository<CleanupRecommendation, UUID> {

    List<CleanupRecommendation> findByTenantIdOrderByAnalyzedAtDesc(String tenantId);

    void deleteByTenantId(String tenantId);
}
