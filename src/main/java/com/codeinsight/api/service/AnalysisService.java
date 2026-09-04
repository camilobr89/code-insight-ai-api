package com.codeinsight.api.service;

import com.codeinsight.api.domain.Analysis;
import com.codeinsight.api.repository.AnalysisRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Minimal (heuristic) reverse-engineering engine.
 *
 * <p>For the MVP it infers the stack and architecture from the repository URL instead of
 * cloning the repository. The {@link #infer(String)} method is a pure function so it can be
 * unit-tested without a database.
 */
@Service
public class AnalysisService {

    private final AnalysisRepository repository;

    public AnalysisService(AnalysisRepository repository) {
        this.repository = repository;
    }

    /** Runs the inference and persists the result. */
    public Analysis analyze(String repoUrl) {
        Analysis analysis = infer(repoUrl);
        analysis.setCreatedAt(Instant.now());
        return repository.save(analysis);
    }

    public List<Analysis> history() {
        return repository.findAll();
    }

    /** Pure inference logic — no side effects, safe to unit-test. */
    public Analysis infer(String repoUrl) {
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
        analysis.setArchitecture("Angular".equals(framework) ? "SPA / Componentes" : "MVC / N-Capas");
        analysis.setFileCount(estimateFileCount(repoUrl));
        analysis.setSummary(String.format(
                "Esta aplicación (%s) es un proyecto %s construido con %s. "
                        + "Expone/consume una API REST y sigue una arquitectura de %s.",
                projectName, language, framework,
                "Angular".equals(framework) ? "componentes" : "capas"));
        analysis.setComponents(String.join("\n", detectComponents(framework)));
        analysis.setRecommendations(String.join("\n",
                "Agregar documentación (README) con instrucciones de ejecución.",
                "Verificar el manejo centralizado de errores.",
                "Revisar dependencias desactualizadas y posibles vulnerabilidades."));
        analysis.setRisks(String.join("\n",
                "No se evidencia una capa de pruebas automatizadas suficiente.",
                "Posible acoplamiento entre capas."));
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
