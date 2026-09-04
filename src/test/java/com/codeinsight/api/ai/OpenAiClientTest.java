package com.codeinsight.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codeinsight.api.github.RepoSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RepoSnapshot sampleSnapshot() {
        return new RepoSnapshot(
                "acme", "demo", "desc", "main", "Java", Map.of("Java", 100L), List.of("a.java"), false, "readme");
    }

    private String chatCompletionResponse(Map<String, Object> insightJson) throws Exception {
        String content = objectMapper.writeValueAsString(insightJson);
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content))));
        return objectMapper.writeValueAsString(response);
    }

    @Test
    void isEnabledReflectsApiKeyPresence() {
        RestClient client = RestClient.builder().build();
        assertThat(new OpenAiClient(client, objectMapper, "sk-test", "gpt-test").isEnabled()).isTrue();
        assertThat(new OpenAiClient(client, objectMapper, "", "gpt-test").isEnabled()).isFalse();
        assertThat(new OpenAiClient(client, objectMapper, null, "gpt-test").isEnabled()).isFalse();
    }

    @Test
    void inferThrowsWhenDisabled() {
        OpenAiClient client = new OpenAiClient(RestClient.builder().build(), objectMapper, "", "gpt-test");

        assertThatThrownBy(() -> client.infer("https://github.com/acme/demo", sampleSnapshot()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inferParsesStructuredResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient client = new OpenAiClient(builder.build(), objectMapper, "sk-test", "gpt-test");

        Map<String, Object> insightJson = new LinkedHashMap<>();
        insightJson.put("summary", "Resumen");
        insightJson.put("mainLanguage", "Java");
        insightJson.put("framework", "Spring Boot");
        insightJson.put("architecture", "mvc");
        insightJson.put("components", List.of("Controllers"));
        insightJson.put("recommendations", List.of("Agregar tests"));
        insightJson.put("risks", List.of("Sin tests"));
        insightJson.put("evidence", List.of("a.java"));

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(chatCompletionResponse(insightJson), MediaType.APPLICATION_JSON));

        AiInsight insight = client.infer("https://github.com/acme/demo", sampleSnapshot());

        assertThat(insight.summary()).isEqualTo("Resumen");
        assertThat(insight.architecture()).isEqualTo("MVC");
        assertThat(insight.components()).containsExactly("Controllers");
        assertThat(insight.evidence()).containsExactly("a.java");
        server.verify();
    }

    @Test
    void inferNormalizesUnknownArchitectureToNCapas() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient client = new OpenAiClient(builder.build(), objectMapper, "sk-test", "gpt-test");

        Map<String, Object> insightJson = new LinkedHashMap<>();
        insightJson.put("summary", "Resumen");
        insightJson.put("architecture", "algo-inventado");
        insightJson.put("components", List.of());
        insightJson.put("recommendations", List.of());
        insightJson.put("risks", List.of());
        insightJson.put("evidence", List.of());

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(chatCompletionResponse(insightJson), MediaType.APPLICATION_JSON));

        AiInsight insight = client.infer("https://github.com/acme/demo", sampleSnapshot());

        assertThat(insight.architecture()).isEqualTo("N-Capas");
    }

    @Test
    void inferThrowsWhenResponseIsMalformed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient client = new OpenAiClient(builder.build(), objectMapper, "sk-test", "gpt-test");

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.infer("https://github.com/acme/demo", sampleSnapshot()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inferPropagatesHttpErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient client = new OpenAiClient(builder.build(), objectMapper, "sk-test", "gpt-test");

        server.expect(requestTo("https://api.openai.com/v1/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.infer("https://github.com/acme/demo", sampleSnapshot()))
                .isInstanceOf(RuntimeException.class);
    }
}
