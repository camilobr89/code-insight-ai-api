package com.codeinsight.api.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubRepoClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepoClient.class);

    private static final Pattern GITHUB_URL =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$", Pattern.CASE_INSENSITIVE);

    private static final int MAX_README_CHARS = 3000;

    private final RestClient restClient;

    public GitHubRepoClient(@Qualifier("gitHubRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public static Optional<String[]> parseOwnerRepo(String repoUrl) {
        if (repoUrl == null) {
            return Optional.empty();
        }
        Matcher m = GITHUB_URL.matcher(repoUrl.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {m.group(1), m.group(2)});
    }

    public Optional<RepoSnapshot> fetch(String repoUrl) {
        Optional<String[]> ownerRepo = parseOwnerRepo(repoUrl);
        if (ownerRepo.isEmpty()) {
            return Optional.empty();
        }
        String owner = ownerRepo.get()[0];
        String repo = ownerRepo.get()[1];
        try {
            RepoMeta meta = get("/repos/" + owner + "/" + repo, RepoMeta.class);
            Map<String, Long> languages = getLanguages(owner, repo);
            TreeResponse tree = get(
                    "/repos/" + owner + "/" + repo + "/git/trees/" + meta.defaultBranch() + "?recursive=1",
                    TreeResponse.class);
            List<String> paths = tree.tree() == null
                    ? List.of()
                    : tree.tree().stream()
                            .filter(e -> "blob".equals(e.type()))
                            .map(TreeEntry::path)
                            .toList();
            String readme = fetchReadmeSafely(owner, repo);

            return Optional.of(new RepoSnapshot(
                    owner,
                    repo,
                    meta.description(),
                    meta.defaultBranch(),
                    meta.language(),
                    languages,
                    paths,
                    tree.truncated(),
                    readme));
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener datos de GitHub para {}/{}: {}", owner, repo, e.getMessage());
            return Optional.empty();
        }
    }

    private String fetchReadmeSafely(String owner, String repo) {
        try {
            String readme = restClient
                    .get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repo)
                    .header("Accept", "application/vnd.github.raw")
                    .retrieve()
                    .body(String.class);
            if (readme == null) {
                return "";
            }
            return readme.length() > MAX_README_CHARS ? readme.substring(0, MAX_README_CHARS) : readme;
        } catch (RestClientException e) {
            return "";
        }
    }

    private <T> T get(String path, Class<T> type) {
        return restClient.get().uri(path).retrieve().body(type);
    }

    private Map<String, Long> getLanguages(String owner, String repo) {
        Map<String, Long> languages = restClient
                .get()
                .uri("/repos/{owner}/{repo}/languages", owner, repo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Long>>() {});
        return languages == null ? Map.of() : languages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RepoMeta(String description, @JsonProperty("default_branch") String defaultBranch, String language) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeResponse(List<TreeEntry> tree, boolean truncated) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeEntry(String path, String type) {}
}
