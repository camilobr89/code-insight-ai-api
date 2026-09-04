package com.codeinsight.api.repository;

import com.codeinsight.api.domain.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
}
