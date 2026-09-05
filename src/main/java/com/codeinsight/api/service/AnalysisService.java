package com.codeinsight.api.service;

import com.codeinsight.api.ai.AiInsight;
import com.codeinsight.api.ai.OpenAiClient;
import com.codeinsight.api.domain.Analysis;
import com.codeinsight.api.github.GitHubRepoClient;
import com.codeinsight.api.github.RepoSnapshot;
import com.codeinsight.api.repository.AnalysisRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisRepository repository;
    private final GitHubRepoClient gitHubRepoClient;
    private final OpenAiClient openAiClient;

    public AnalysisService(
            AnalysisRepository repository, GitHubRepoClient gitHubRepoClient, OpenAiClient openAiClient) {
        this.repository = repository;
        this.gitHubRepoClient = gitHubRepoClient;
        this.openAiClient = openAiClient;
    }

    public AnalysisResult analyze(String rawRepoUrl, boolean forceRefresh) {
        String repoUrl = normalize(rawRepoUrl);

        if (!forceRefresh) {
            Optional<Analysis> cached = repository.findFirstByRepoUrlOrderByCreatedAtDesc(repoUrl);
            if (cached.isPresent()) {
                return new AnalysisResult(cached.get(), true);
            }
        }

        Analysis analysis = performFreshAnalysis(repoUrl);
        analysis.setRepoUrl(repoUrl);
        analysis.setCreatedAt(Instant.now());
        return new AnalysisResult(repository.save(analysis), false);
    }

    public List<Analysis> history() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Analysis> findById(UUID id) {
        return repository.findById(id);
    }

    public boolean deleteById(UUID id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    private Analysis performFreshAnalysis(String repoUrl) {
        // Sin OPENAI_API_KEY se evita también la llamada a GitHub (modo 100% offline).
        if (!openAiClient.isEnabled()) {
            return infer(repoUrl, "No hay una API key de IA configurada.");
        }

        boolean isGitHubUrl = GitHubRepoClient.parseOwnerRepo(repoUrl).isPresent();
        Optional<RepoSnapshot> snapshot = gitHubRepoClient.fetch(repoUrl);
        if (snapshot.isEmpty()) {
            String reason = isGitHubUrl
                    ? "No se pudo acceder al repositorio en GitHub (puede ser privado, no existir, o el token "
                            + "configurado no tiene acceso)."
                    : "La URL no corresponde a un repositorio de GitHub soportado.";
            return infer(repoUrl, reason);
        }

        try {
            AiInsight insight = openAiClient.infer(repoUrl, snapshot.get());
            return toAnalysis(repoUrl, snapshot.get(), insight);
        } catch (Exception e) {
            log.warn("Inferencia con IA falló para {}, usando heurística de respaldo: {}", repoUrl, e.getMessage());
            Analysis heuristic = infer(repoUrl, "La consulta a la IA falló (" + e.getMessage() + ").");
            heuristic.setFileCount(snapshot.get().fileCount());
            return heuristic;
        }
    }

    private static Analysis toAnalysis(String repoUrl, RepoSnapshot snapshot, AiInsight insight) {
        Analysis analysis = new Analysis();
        analysis.setRepoUrl(repoUrl);
        analysis.setProjectName(snapshot.repo());
        analysis.setMainLanguage(firstNonBlank(insight.mainLanguage(), snapshot.primaryLanguage()));
        analysis.setFramework(insight.framework());
        analysis.setArchitecture(insight.architecture());
        analysis.setFileCount(snapshot.fileCount());
        analysis.setSummary(insight.summary());
        analysis.setComponents(String.join("\n", insight.components()));
        analysis.setRecommendations(String.join("\n", insight.recommendations()));
        analysis.setRisks(String.join("\n", insight.risks()));
        analysis.setEvidence(String.join("\n", insight.evidence()));
        analysis.setSource("AI");
        return analysis;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    static String normalize(String repoUrl) {
        if (repoUrl == null) {
            return null;
        }
        String cleaned = repoUrl.trim();
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        return cleaned;
    }

    public Analysis infer(String repoUrl) {
        return infer(repoUrl, "no se pudo consultar GitHub/IA para esta URL");
    }

    public Analysis infer(String repoUrl, String reason) {
        String url = repoUrl == null ? "" : repoUrl.toLowerCase();
        String projectName = projectNameFrom(repoUrl);

        String language;
        String framework;
        if (url.contains("spring") || url.contains("java")) {
            language = "Java";
            framework = "Spring Boot";
        } else if (url.contains("nest") || url.contains("node") || url.contains("express")) {
            language = "TypeScript";
            framework = "NestJS";
        } else if (url.contains("angular") || url.contains("front") || url.contains("web")) {
            language = "TypeScript";
            framework = "Angular";
        } else if (url.contains("python") || url.contains("django") || url.contains("flask")) {
            language = "Python";
            framework = "Flask";
        } else {
            language = "Java";
            framework = "Spring Boot";
        }

        Analysis analysis = new Analysis();
        analysis.setRepoUrl(repoUrl);
        analysis.setProjectName(projectName);
        analysis.setMainLanguage(language);
        analysis.setFramework(framework);
        analysis.setArchitecture("Angular".equals(framework) ? "MVC" : "N-Capas");
        analysis.setFileCount(estimateFileCount(repoUrl));
        analysis.setSummary(String.format(
                "Esta aplicación (%s) es un proyecto %s construido con %s. "
                        + "Expone/consume una API REST y sigue una arquitectura de %s. "
                        + "(Análisis heurístico: %s)",
                projectName, language, framework,
                "Angular".equals(framework) ? "componentes" : "capas",
                reason));
        analysis.setComponents(String.join("\n", detectComponents(framework)));
        analysis.setRecommendations(String.join("\n",
                "Agregar documentación (README) con instrucciones de ejecución.",
                "Verificar el manejo centralizado de errores.",
                "Revisar dependencias desactualizadas y posibles vulnerabilidades."));
        analysis.setRisks(String.join("\n",
                "No se evidencia una capa de pruebas automatizadas suficiente.",
                "Posible acoplamiento entre capas."));
        analysis.setEvidence(String.join("\n",
                "Inferido a partir del texto de la URL. " + reason));
        analysis.setSource("HEURISTIC");
        return analysis;
    }

    static String projectNameFrom(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "unknown-project";
        }
        String cleaned = repoUrl.trim();
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        int slash = cleaned.lastIndexOf('/');
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return name.isBlank() ? "unknown-project" : name;
    }

    private static int estimateFileCount(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return 0;
        }
        return 40 + Math.abs(repoUrl.hashCode() % 260);
    }

    private static List<String> detectComponents(String framework) {
        if ("Angular".equals(framework)) {
            return List.of("Components", "Services", "Modules", "APIs consumidas");
        }
        if ("NestJS".equals(framework)) {
            return List.of("Controllers", "Services", "Modules", "Repositories");
        }
        return List.of("Controllers", "Services", "Repositories", "Models");
    }
}
