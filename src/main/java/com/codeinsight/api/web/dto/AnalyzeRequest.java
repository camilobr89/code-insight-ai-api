package com.codeinsight.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AnalyzeRequest(
        @NotBlank(message = "repoUrl es obligatorio")
        @Pattern(regexp = "^https?://.+", message = "repoUrl debe ser una URL http(s) válida")
        String repoUrl,
        boolean forceRefresh) {

    public AnalyzeRequest(String repoUrl) {
        this(repoUrl, false);
    }
}
