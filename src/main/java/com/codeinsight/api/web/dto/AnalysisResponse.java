package com.codeinsight.api.web.dto;

import com.codeinsight.api.domain.Analysis;
import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
        Long id,
        String repoUrl,
        String projectName,
        String mainLanguage,
        String framework,
        String architecture,
        int fileCount,
        String summary,
        List<String> components,
        List<String> recommendations,
        List<String> risks,
        Instant createdAt) {

    public static AnalysisResponse from(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getRepoUrl(),
                analysis.getProjectName(),
                analysis.getMainLanguage(),
                analysis.getFramework(),
                analysis.getArchitecture(),
                analysis.getFileCount(),
                analysis.getSummary(),
                splitLines(analysis.getComponents()),
                splitLines(analysis.getRecommendations()),
                splitLines(analysis.getRisks()),
                analysis.getCreatedAt());
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\n"));
    }
}
