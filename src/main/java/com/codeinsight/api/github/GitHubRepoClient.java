package com.codeinsight.api.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final String token;

    public GitHubRepoClient(RestClient.Builder builder, @Value("${app.github.token:}") String token) {
        this.token = token;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = builder
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
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
                    .headers(h -> {
                        h.set("Accept", "application/vnd.github.raw");
                        applyAuth(h);
                    })
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
        return restClient
                .get()
                .uri(path)
                .headers(this::applyAuth)
                .retrieve()
                .body(type);
    }

    private Map<String, Long> getLanguages(String owner, String repo) {
        Map<String, Long> languages = restClient
                .get()
                .uri("/repos/{owner}/{repo}/languages", owner, repo)
                .headers(this::applyAuth)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Long>>() {});
        return languages == null ? Map.of() : languages;
    }

    private void applyAuth(HttpHeaders headers) {
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", "Bearer " + token);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RepoMeta(String description, @JsonProperty("default_branch") String defaultBranch, String language) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeResponse(List<TreeEntry> tree, boolean truncated) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeEntry(String path, String type) {}
}
