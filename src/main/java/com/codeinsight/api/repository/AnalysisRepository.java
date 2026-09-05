package com.codeinsight.api.repository;

import com.codeinsight.api.domain.Analysis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    Optional<Analysis> findFirstByRepoUrlOrderByCreatedAtDesc(String repoUrl);

    List<Analysis> findAllByOrderByCreatedAtDesc();
}
