package com.codeinsight.api.service;

import com.codeinsight.api.domain.Analysis;

public record AnalysisResult(Analysis analysis, boolean cached) {}
