package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.PrAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrAnalysisRepository extends JpaRepository<PrAnalysis, UUID> {

    List<PrAnalysis> findByPrIdIn(List<UUID> prIds);

    List<PrAnalysis> findByTenantId(String tenantId);
}
