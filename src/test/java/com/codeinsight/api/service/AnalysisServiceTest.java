package com.codeinsight.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeinsight.api.ai.AiInsight;
import com.codeinsight.api.ai.OpenAiClient;
import com.codeinsight.api.domain.Analysis;
import com.codeinsight.api.github.GitHubRepoClient;
import com.codeinsight.api.github.RepoSnapshot;
import com.codeinsight.api.repository.AnalysisRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    private final AnalysisService heuristicOnlyService = new AnalysisService(null, null, null);

    @Test
    void inferDetectsSpringBootFromUrl() {
        Analysis result = heuristicOnlyService.infer("https://github.com/spring-projects/spring-petclinic");

        assertThat(result.getProjectName()).isEqualTo("spring-petclinic");
        assertThat(result.getMainLanguage()).isEqualTo("Java");
        assertThat(result.getFramework()).isEqualTo("Spring Boot");
        assertThat(result.getFileCount()).isPositive();
        assertThat(result.getComponents()).contains("Controllers");
        assertThat(result.getSummary()).contains("spring-petclinic");
        assertThat(result.getSource()).isEqualTo("HEURISTIC");
    }

    @Test
    void inferDetectsAngularFrontend() {
        Analysis result = heuristicOnlyService.infer("https://github.com/acme/my-angular-web");

        assertThat(result.getFramework()).isEqualTo("Angular");
        assertThat(result.getArchitecture()).isEqualTo("MVC");
    }

    @Test
    void projectNameStripsGitSuffixAndTrailingSlash() {
        assertThat(AnalysisService.projectNameFrom("https://github.com/acme/demo.git")).isEqualTo("demo");
        assertThat(AnalysisService.projectNameFrom("https://github.com/acme/demo/")).isEqualTo("demo");
        assertThat(AnalysisService.projectNameFrom("")).isEqualTo("unknown-project");
    }

    @Test
    void normalizeStripsTrailingSlashAndGitSuffix() {
        assertThat(AnalysisService.normalize("https://github.com/acme/demo.git")).isEqualTo("https://github.com/acme/demo");
        assertThat(AnalysisService.normalize("https://github.com/acme/demo/")).isEqualTo("https://github.com/acme/demo");
        assertThat(AnalysisService.normalize(" https://github.com/acme/demo ")).isEqualTo("https://github.com/acme/demo");
    }

    @Test
    void analyzeReturnsCachedResultWithoutCallingGitHubOrOpenAi() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        GitHubRepoClient gitHubRepoClient = mock(GitHubRepoClient.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AnalysisService service = new AnalysisService(repository, gitHubRepoClient, openAiClient);

        Analysis stored = new Analysis();
        stored.setRepoUrl("https://github.com/acme/demo");
        when(repository.findFirstByRepoUrlOrderByCreatedAtDesc("https://github.com/acme/demo"))
                .thenReturn(Optional.of(stored));

        AnalysisResult result = service.analyze("https://github.com/acme/demo", false);

        assertThat(result.cached()).isTrue();
        assertThat(result.analysis()).isSameAs(stored);
        verify(gitHubRepoClient, never()).fetch(anyString());
        verify(openAiClient, never()).infer(anyString(), any());
    }

    @Test
    void analyzeUsesGitHubAndOpenAiWhenNoCache() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        GitHubRepoClient gitHubRepoClient = mock(GitHubRepoClient.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AnalysisService service = new AnalysisService(repository, gitHubRepoClient, openAiClient);

        when(repository.findFirstByRepoUrlOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RepoSnapshot snapshot = new RepoSnapshot(
                "acme", "demo", "desc", "main", "Java", Map.of("Java", 100L),
                List.of("src/main/java/Controller.java"), false, "readme");
        when(gitHubRepoClient.fetch("https://github.com/acme/demo")).thenReturn(Optional.of(snapshot));
        when(openAiClient.isEnabled()).thenReturn(true);
        when(openAiClient.infer(anyString(), any())).thenReturn(new AiInsight(
                "Resumen", "Java", "Spring Boot", "MVC",
                List.of("Controllers"), List.of("Agregar tests"), List.of("Sin tests"),
                List.of("src/main/java/Controller.java presente")));

        AnalysisResult result = service.analyze("https://github.com/acme/demo", false);

        assertThat(result.cached()).isFalse();
        assertThat(result.analysis().getArchitecture()).isEqualTo("MVC");
        assertThat(result.analysis().getFileCount()).isEqualTo(1);
        assertThat(result.analysis().getEvidence()).contains("Controller.java presente");
        assertThat(result.analysis().getSource()).isEqualTo("AI");
    }

    @Test
    void analyzeFallsBackToHeuristicWhenOpenAiFails() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        GitHubRepoClient gitHubRepoClient = mock(GitHubRepoClient.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AnalysisService service = new AnalysisService(repository, gitHubRepoClient, openAiClient);

        when(repository.findFirstByRepoUrlOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RepoSnapshot snapshot = new RepoSnapshot(
                "acme", "spring-demo", "desc", "main", "Java", Map.of("Java", 100L),
                List.of("a.java", "b.java", "c.java"), false, "readme");
        when(gitHubRepoClient.fetch(anyString())).thenReturn(Optional.of(snapshot));
        when(openAiClient.isEnabled()).thenReturn(true);
        when(openAiClient.infer(anyString(), any())).thenThrow(new RuntimeException("boom"));

        AnalysisResult result = service.analyze("https://github.com/acme/spring-demo", false);

        assertThat(result.cached()).isFalse();
        assertThat(result.analysis().getFileCount()).isEqualTo(3);
        assertThat(result.analysis().getFramework()).isEqualTo("Spring Boot");
        assertThat(result.analysis().getSource()).isEqualTo("HEURISTIC");
    }

    @Test
    void analyzeExplainsWhenAGithubRepoIsInaccessible() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        GitHubRepoClient gitHubRepoClient = mock(GitHubRepoClient.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AnalysisService service = new AnalysisService(repository, gitHubRepoClient, openAiClient);

        when(repository.findFirstByRepoUrlOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openAiClient.isEnabled()).thenReturn(true);
        // GitHub-shaped URL, but the client couldn't reach it (e.g. private repo, 404 unauthenticated).
        when(gitHubRepoClient.fetch("https://github.com/acme/private-repo")).thenReturn(Optional.empty());

        AnalysisResult result = service.analyze("https://github.com/acme/private-repo", false);

        assertThat(result.analysis().getSource()).isEqualTo("HEURISTIC");
        assertThat(result.analysis().getSummary()).contains("privado");
        assertThat(result.analysis().getEvidence()).contains("privado");
    }

    @Test
    void analyzeForceRefreshIgnoresCache() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        GitHubRepoClient gitHubRepoClient = mock(GitHubRepoClient.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AnalysisService service = new AnalysisService(repository, gitHubRepoClient, openAiClient);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gitHubRepoClient.fetch(anyString())).thenReturn(Optional.empty());
        when(openAiClient.isEnabled()).thenReturn(false);

        AnalysisResult result = service.analyze("https://github.com/acme/demo", true);

        assertThat(result.cached()).isFalse();
        verify(repository, never()).findFirstByRepoUrlOrderByCreatedAtDesc(anyString());
    }

    @Test
    void deleteByIdReturnsTrueAndDeletesWhenPresent() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        AnalysisService service = new AnalysisService(repository, null, null);
        when(repository.existsById(1L)).thenReturn(true);

        boolean deleted = service.deleteById(1L);

        assertThat(deleted).isTrue();
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteByIdReturnsFalseWhenMissing() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        AnalysisService service = new AnalysisService(repository, null, null);
        when(repository.existsById(99L)).thenReturn(false);

        boolean deleted = service.deleteById(99L);

        assertThat(deleted).isFalse();
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void deleteAllDelegatesToRepository() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        AnalysisService service = new AnalysisService(repository, null, null);

        service.deleteAll();

        verify(repository).deleteAll();
    }
}
