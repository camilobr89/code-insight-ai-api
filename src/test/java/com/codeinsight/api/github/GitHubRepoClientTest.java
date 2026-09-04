package com.codeinsight.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubRepoClientTest {

    private RestClient.Builder newBuilder() {
        return RestClient.builder().baseUrl("https://api.github.com");
    }

    @Test
    void parseOwnerRepoExtractsFromVariousUrlForms() {
        assertThat(GitHubRepoClient.parseOwnerRepo("https://github.com/acme/demo").get())
                .containsExactly("acme", "demo");
        assertThat(GitHubRepoClient.parseOwnerRepo("https://github.com/acme/demo.git").get())
                .containsExactly("acme", "demo");
        assertThat(GitHubRepoClient.parseOwnerRepo("https://github.com/acme/demo/").get())
                .containsExactly("acme", "demo");
        assertThat(GitHubRepoClient.parseOwnerRepo("https://gitlab.com/acme/demo")).isEmpty();
        assertThat(GitHubRepoClient.parseOwnerRepo(null)).isEmpty();
    }

    @Test
    void fetchReturnsEmptyForNonGithubUrl() {
        RestClient.Builder builder = newBuilder();
        GitHubRepoClient client = new GitHubRepoClient(builder.build());

        assertThat(client.fetch("https://gitlab.com/acme/demo")).isEmpty();
    }

    @Test
    void fetchBuildsSnapshotFromGithubApi() {
        RestClient.Builder builder = newBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubRepoClient client = new GitHubRepoClient(builder.build());

        server.expect(requestTo("https://api.github.com/repos/acme/demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"description\":\"Demo repo\",\"default_branch\":\"main\",\"language\":\"Java\"}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/acme/demo/languages"))
                .andRespond(withSuccess("{\"Java\":1000,\"HTML\":200}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/acme/demo/git/trees/main?recursive=1"))
                .andRespond(withSuccess(
                        "{\"truncated\":false,\"tree\":["
                                + "{\"path\":\"src/Main.java\",\"type\":\"blob\"},"
                                + "{\"path\":\"src\",\"type\":\"tree\"}]}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/acme/demo/readme"))
                .andRespond(withSuccess("# Demo", MediaType.TEXT_PLAIN));

        Optional<RepoSnapshot> result = client.fetch("https://github.com/acme/demo");

        assertThat(result).isPresent();
        RepoSnapshot snapshot = result.get();
        assertThat(snapshot.owner()).isEqualTo("acme");
        assertThat(snapshot.repo()).isEqualTo("demo");
        assertThat(snapshot.fileCount()).isEqualTo(1);
        assertThat(snapshot.filePaths()).containsExactly("src/Main.java");
        assertThat(snapshot.languageBytes()).containsEntry("Java", 1000L);
        assertThat(snapshot.readmeExcerpt()).isEqualTo("# Demo");
        server.verify();
    }

    @Test
    void fetchReturnsEmptyWhenGithubApiFails() {
        RestClient.Builder builder = newBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubRepoClient client = new GitHubRepoClient(builder.build());

        server.expect(requestTo("https://api.github.com/repos/acme/demo")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetch("https://github.com/acme/demo")).isEmpty();
    }

    @Test
    void fetchToleratesMissingReadme() {
        RestClient.Builder builder = newBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubRepoClient client = new GitHubRepoClient(builder.build());

        server.expect(requestTo("https://api.github.com/repos/acme/demo"))
                .andRespond(withSuccess(
                        "{\"description\":null,\"default_branch\":\"main\",\"language\":\"Java\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/acme/demo/languages"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/acme/demo/git/trees/main?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/acme/demo/readme"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<RepoSnapshot> result = client.fetch("https://github.com/acme/demo");

        assertThat(result).isPresent();
        assertThat(result.get().readmeExcerpt()).isEmpty();
        assertThat(result.get().fileCount()).isZero();
    }
}
