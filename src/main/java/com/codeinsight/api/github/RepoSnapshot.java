package com.codeinsight.api.github;

import java.util.List;
import java.util.Map;

public record RepoSnapshot(
        String owner,
        String repo,
        String description,
        String defaultBranch,
        String primaryLanguage,
        Map<String, Long> languageBytes,
        List<String> filePaths,
        boolean truncated,
        String readmeExcerpt) {

    public int fileCount() {
        return filePaths.size();
    }
}
