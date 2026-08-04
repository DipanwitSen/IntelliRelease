package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.ReleaseNote;
import com.gyansys.intellirelease.model.enums.Audience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, UUID> {

    List<ReleaseNote> findByReleaseIdOrderByAudience(UUID releaseId);

    Optional<ReleaseNote> findByReleaseIdAndAudience(UUID releaseId, Audience audience);

    void deleteByReleaseId(UUID releaseId);
}
