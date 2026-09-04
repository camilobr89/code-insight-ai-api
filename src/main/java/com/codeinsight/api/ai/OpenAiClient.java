package com.codeinsight.api.ai;

import com.codeinsight.api.github.RepoSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final int MAX_FILE_PATHS = 200;

    private static final List<String> ALLOWED_ARCHITECTURES =
            List.of("Monolito", "MVC", "Clean Architecture", "Hexagonal", "Microservicios", "N-Capas");

    private static final String SYSTEM_PROMPT = """
            Eres un ingeniero de software senior especializado en ingeniería inversa de \
            repositorios. Recibes metadata real de un repositorio (lenguajes, árbol de \
            archivos, README) y debes inferir su propósito, arquitectura y componentes.

            Responde ÚNICAMENTE con un objeto JSON (sin markdown, sin explicación fuera del \
            JSON) con EXACTAMENTE estas claves:
            {
              "summary": string (2-4 frases, en español, describiendo qué hace la aplicación),
              "mainLanguage": string,
              "framework": string,
              "architecture": string (EXACTAMENTE uno de: %s),
              "components": string[] (componentes reales identificados: Controllers, Services, \
              Repositories, Models, Components Angular, Modules, APIs consumidas, etc., según \
              lo que evidencien las rutas de archivo),
              "recommendations": string[] (2-4 recomendaciones concretas y accionables),
              "risks": string[] (1-3 riesgos concretos detectados, ej: falta de tests, \
              dependencias desactualizadas, falta de manejo de errores),
              "evidence": string[] (2-5 rutas de archivo o hechos concretos que sustentan tu \
              inferencia, ej: "src/main/java/.../Controller.java presente")
            }

            No inventes información que no esté sustentada por los datos entregados.
            """
            .formatted(String.join(", ", ALLOWED_ARCHITECTURES));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.openai.api-key:}") String apiKey,
            @Value("${app.openai.model:gpt-5.6-luna}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(45).toMillis());
        this.restClient = builder
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(factory)
                .build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiInsight infer(String repoUrl, RepoSnapshot snapshot) {
        if (!isEnabled()) {
            throw new IllegalStateException("OPENAI_API_KEY no configurada");
        }
        String userPrompt = buildUserPrompt(repoUrl, snapshot);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)));
        body.put("response_format", Map.of("type", "json_object"));

        String raw = restClient
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseInsight(raw);
    }

    private AiInsight parseInsight(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.at("/choices/0/message/content").asText("");
            JsonNode insight = objectMapper.readTree(content);
            return new AiInsight(
                    textOrDefault(insight, "summary", ""),
                    textOrDefault(insight, "mainLanguage", "Desconocido"),
                    textOrDefault(insight, "framework", "Desconocido"),
                    normalizeArchitecture(textOrDefault(insight, "architecture", "N-Capas")),
                    arrayOrEmpty(insight, "components"),
                    arrayOrEmpty(insight, "recommendations"),
                    arrayOrEmpty(insight, "risks"),
                    arrayOrEmpty(insight, "evidence"));
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta de OpenAI: {}", e.getMessage());
            throw new IllegalStateException("Respuesta inválida de OpenAI", e);
        }
    }

    private static String normalizeArchitecture(String value) {
        return ALLOWED_ARCHITECTURES.stream()
                .filter(a -> a.equalsIgnoreCase(value))
                .findFirst()
                .orElse("N-Capas");
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? fallback : v.asText(fallback);
    }

    private static List<String> arrayOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        List<String> result = new ArrayList<>();
        if (v != null && v.isArray()) {
            v.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private static String buildUserPrompt(String repoUrl, RepoSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repositorio: ").append(snapshot.owner()).append('/').append(snapshot.repo()).append('\n');
        sb.append("URL: ").append(repoUrl).append('\n');
        sb.append("Descripción: ").append(nullToDash(snapshot.description())).append('\n');
        sb.append("Rama por defecto: ").append(nullToDash(snapshot.defaultBranch())).append('\n');
        sb.append("Lenguaje principal (GitHub): ").append(nullToDash(snapshot.primaryLanguage())).append('\n');
        sb.append("Distribución de lenguajes (bytes): ");
        snapshot.languageBytes().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        sb.append('\n');
        sb.append("Cantidad total de archivos: ").append(snapshot.fileCount()).append('\n');
        sb.append("Rutas de archivo (muestra");
        if (snapshot.filePaths().size() > MAX_FILE_PATHS) {
            sb.append(", primeras ").append(MAX_FILE_PATHS).append(" de ").append(snapshot.fileCount());
        }
        sb.append("):\n");
        snapshot.filePaths().stream()
                .sorted(Comparator.naturalOrder())
                .limit(MAX_FILE_PATHS)
                .forEach(p -> sb.append("  ").append(p).append('\n'));
        sb.append("\nREADME (extracto):\n\"\"\"\n")
                .append(nullToDash(snapshot.readmeExcerpt()))
                .append("\n\"\"\"\n");
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
