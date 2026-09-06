package com.codeinsight.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Genera un diagrama de arquitectura (sintaxis Mermaid) a partir del patrón inferido
 * y la lista de componentes — de forma determinista, para que nunca produzca un
 * diagrama inválido (a diferencia de pedirle sintaxis Mermaid directamente a la IA).
 */
final class DiagramGenerator {

    private static final int MAX_COMPONENTS = 8;
    private static final Set<String> LAYERED_PATTERNS = Set.of("MONOLITO", "MVC", "N-CAPAS");
    private static final Set<String> CORE_CENTERED_PATTERNS = Set.of("HEXAGONAL", "CLEAN ARCHITECTURE");

    private DiagramGenerator() {
    }

    static String generate(String architecture, List<String> components) {
        List<String> nodes = trim(components);
        if (nodes.isEmpty()) {
            nodes = List.of("Aplicación");
        }
        String pattern = architecture == null ? "" : architecture.toUpperCase();

        if ("MICROSERVICIOS".equals(pattern)) {
            return microservices(nodes);
        }
        if (CORE_CENTERED_PATTERNS.contains(pattern)) {
            return coreCentered(nodes);
        }
        return layered(nodes);
    }

    private static String layered(List<String> nodes) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        sb.append("  Cliente([Cliente])\n");
        List<String> ids = ids(nodes);
        sb.append("  Cliente --> ").append(ids.get(0)).append('\n');
        for (int i = 0; i < ids.size(); i++) {
            sb.append("  ").append(ids.get(i)).append('[').append(label(nodes.get(i))).append("]\n");
            if (i > 0) {
                sb.append("  ").append(ids.get(i - 1)).append(" --> ").append(ids.get(i)).append('\n');
            }
        }
        sb.append("  ").append(ids.get(ids.size() - 1)).append(" --> BD[(Base de datos)]\n");
        return sb.toString();
    }

    private static String coreCentered(List<String> nodes) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        sb.append("  Cliente([Cliente]) --> Adaptador[Adaptador de entrada]\n");
        sb.append("  subgraph Nucleo [Núcleo de dominio]\n");
        List<String> ids = ids(nodes);
        for (int i = 0; i < ids.size(); i++) {
            sb.append("    ").append(ids.get(i)).append('[').append(label(nodes.get(i))).append("]\n");
        }
        sb.append("  end\n");
        sb.append("  Adaptador --> ").append(ids.get(0)).append('\n');
        sb.append("  ").append(ids.get(ids.size() - 1)).append(" --> Salida[Adaptador de salida]\n");
        sb.append("  Salida --> BD[(Base de datos)]\n");
        return sb.toString();
    }

    private static String microservices(List<String> nodes) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        sb.append("  Cliente([Cliente]) --> Gateway[API Gateway]\n");
        List<String> ids = ids(nodes);
        for (int i = 0; i < ids.size(); i++) {
            sb.append("  ").append(ids.get(i)).append('[').append(label(nodes.get(i))).append("]\n");
            sb.append("  Gateway --> ").append(ids.get(i)).append('\n');
            sb.append("  ").append(ids.get(i)).append(" --> BD").append(i).append("[(BD)]\n");
        }
        return sb.toString();
    }

    private static List<String> trim(List<String> components) {
        List<String> result = new ArrayList<>();
        for (String c : components) {
            if (c != null && !c.isBlank() && result.size() < MAX_COMPONENTS) {
                result.add(c.trim());
            }
        }
        return result;
    }

    private static List<String> ids(List<String> nodes) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            ids.add("N" + i);
        }
        return ids;
    }

    /** Mermaid rompe con corchetes/comillas sin escapar dentro de una etiqueta. */
    private static String label(String raw) {
        String safe = raw.replace("\"", "'").replace("[", "(").replace("]", ")");
        if (safe.length() > 40) {
            safe = safe.substring(0, 37) + "...";
        }
        return "\"" + safe + "\"";
    }
}
