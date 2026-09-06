package com.codeinsight.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramGeneratorTest {

    @Test
    void mvcProducesALinearFlowchart() {
        String diagram = DiagramGenerator.generate("MVC", List.of("Controllers", "Services", "Repositories"));

        assertThat(diagram).startsWith("flowchart TD");
        assertThat(diagram).contains("Cliente([Cliente])");
        assertThat(diagram).contains("\"Controllers\"");
        assertThat(diagram).contains("\"Services\"");
        assertThat(diagram).contains("BD[(Base de datos)]");
    }

    @Test
    void hexagonalWrapsComponentsInACoreSubgraph() {
        String diagram = DiagramGenerator.generate("Hexagonal", List.of("Dominio", "Puertos"));

        assertThat(diagram).contains("subgraph Nucleo");
        assertThat(diagram).contains("Adaptador de entrada");
        assertThat(diagram).contains("Adaptador de salida");
    }

    @Test
    void hexagonalDoesNotForceAnOrderBetweenCoreComponents() {
        String diagram = DiagramGenerator.generate("Hexagonal", List.of("Dominio", "Puertos", "Adaptadores"));

        // Son componentes pares dentro del núcleo, no un flujo secuencial: no debe
        // haber enlaces (ni visibles ni invisibles) entre ellos, para no sugerir un
        // orden que no existe (eso se leía como una arquitectura en capas).
        assertThat(diagram).doesNotContain("~~~");
        assertThat(diagram).doesNotContain("N0 --> N1");
        assertThat(diagram).doesNotContain("N1 --> N2");
    }

    @Test
    void microservicesFansOutFromAGateway() {
        String diagram = DiagramGenerator.generate("Microservicios", List.of("Cuentas", "Pagos"));

        assertThat(diagram).contains("API Gateway");
        assertThat(diagram).contains("Gateway --> N0");
        assertThat(diagram).contains("Gateway --> N1");
    }

    @Test
    void isCaseInsensitiveOnArchitectureName() {
        String diagram = DiagramGenerator.generate("microservicios", List.of("Cuentas"));

        assertThat(diagram).contains("API Gateway");
    }

    @Test
    void fallsBackToLayeredWhenArchitectureIsUnknownOrNull() {
        assertThat(DiagramGenerator.generate("Patrón inventado", List.of("Algo"))).startsWith("flowchart TD");
        assertThat(DiagramGenerator.generate(null, List.of("Algo"))).contains("Cliente");
    }

    @Test
    void usesAPlaceholderNodeWhenThereAreNoComponents() {
        String diagram = DiagramGenerator.generate("MVC", List.of());

        assertThat(diagram).contains("\"Aplicación\"");
    }

    @Test
    void sanitizesLabelsWithMermaidSpecialCharactersAndTruncatesLongOnes() {
        String longName = "Un componente con un nombre extremadamente largo que debería truncarse en algún punto";
        String diagram = DiagramGenerator.generate("MVC", List.of("Servicio [con] \"corchetes\"", longName));

        assertThat(diagram).doesNotContain("[con]");
        assertThat(diagram).contains("(con)");
        assertThat(diagram).contains("...\"");
    }

    @Test
    void limitsToAtMostEightComponents() {
        List<String> tenComponents = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        String diagram = DiagramGenerator.generate("N-Capas", tenComponents);

        assertThat(diagram).contains("\"H\"");
        assertThat(diagram).doesNotContain("\"I\"");
        assertThat(diagram).doesNotContain("\"J\"");
    }
}
