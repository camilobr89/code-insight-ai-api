package com.codeinsight.api.web.dto;

import com.codeinsight.api.domain.Analysis;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
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
        List<String> evidence,
        Instant createdAt,
        boolean cached,
        /** "AI" si el análisis se generó con OpenAI, "HEURISTIC" si fue por fallback. */
        String source) {

    public static AnalysisResponse from(Analysis analysis, boolean cached) {
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
                splitLines(analysis.getEvidence()),
                analysis.getCreatedAt(),
                cached,
                analysis.getSource() != null ? analysis.getSource() : "HEURISTIC");
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\n"));
    }
}
