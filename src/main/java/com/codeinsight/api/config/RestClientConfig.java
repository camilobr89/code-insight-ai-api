package com.codeinsight.api.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Qualifier("gitHubRestClient")
    RestClient gitHubRestClient(RestClient.Builder builder, @Value("${app.github.token:}") String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());

        RestClient.Builder configured = builder
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + token);
        }
        return configured.build();
    }

    @Bean
    @Qualifier("openAiRestClient")
    RestClient openAiRestClient(RestClient.Builder builder, @Value("${app.openai.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(45).toMillis());

        RestClient.Builder configured = builder.baseUrl("https://api.openai.com/v1").requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        return configured.build();
    }
}
