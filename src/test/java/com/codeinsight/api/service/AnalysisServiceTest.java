package com.codeinsight.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeinsight.api.domain.Analysis;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    private final AnalysisService service = new AnalysisService(null);

    @Test
    void inferDetectsSpringBootFromUrl() {
        Analysis result = service.infer("https://github.com/spring-projects/spring-petclinic");

        assertThat(result.getProjectName()).isEqualTo("spring-petclinic");
        assertThat(result.getMainLanguage()).isEqualTo("Java");
        assertThat(result.getFramework()).isEqualTo("Spring Boot");
        assertThat(result.getFileCount()).isPositive();
        assertThat(result.getComponents()).contains("Controllers");
        assertThat(result.getSummary()).contains("spring-petclinic");
    }

    @Test
    void inferDetectsAngularFrontend() {
        Analysis result = service.infer("https://github.com/acme/my-angular-web");

        assertThat(result.getFramework()).isEqualTo("Angular");
        assertThat(result.getArchitecture()).isEqualTo("SPA / Componentes");
    }

    @Test
    void projectNameStripsGitSuffixAndTrailingSlash() {
        assertThat(AnalysisService.projectNameFrom("https://github.com/acme/demo.git")).isEqualTo("demo");
        assertThat(AnalysisService.projectNameFrom("https://github.com/acme/demo/")).isEqualTo("demo");
        assertThat(AnalysisService.projectNameFrom("")).isEqualTo("unknown-project");
    }
}
