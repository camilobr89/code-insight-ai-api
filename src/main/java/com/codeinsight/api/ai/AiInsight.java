package com.codeinsight.api.ai;

import java.util.List;

public record AiInsight(
        String summary,
        String mainLanguage,
        String framework,
        String architecture,
        List<String> components,
        List<String> recommendations,
        List<String> risks,
        List<String> evidence) {}
