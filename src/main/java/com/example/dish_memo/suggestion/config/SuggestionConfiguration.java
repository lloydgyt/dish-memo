package com.example.dish_memo.suggestion.config;

import com.example.dish_memo.suggestion.client.BailianNameSuggestionClient;
import com.example.dish_memo.suggestion.client.NameSuggestionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires dish name suggestion dependencies.
 */
@Configuration
@EnableConfigurationProperties(SuggestionProperties.class)
public class SuggestionConfiguration {

    /**
     * Creates the Bailian client used by the suggestion service.
     *
     * @param properties suggestion configuration
     * @param objectMapper JSON mapper
     * @return name suggestion client
     */
    @Bean
    public NameSuggestionClient nameSuggestionClient(SuggestionProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getBailian().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getBailian().getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBailian().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new BailianNameSuggestionClient(restClient, objectMapper, properties.getBailian());
    }
}
