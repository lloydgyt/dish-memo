package com.example.dish_memo.suggestion.client;

import com.example.dish_memo.suggestion.config.SuggestionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Calls Alibaba Bailian through the OpenAI-compatible Java client pattern.
 */
public class BailianNameSuggestionClient implements NameSuggestionClient {
    private static final String DEFAULT_USER_PROMPT = "请根据图片推荐一个适合保存到做菜记录里的中文菜名。";
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是菜品识别和命名助手。根据图片，只返回 JSON：
            {"suggested_name":"不超过20个中文字符的菜名"}。
            不要返回 Markdown、解释或多余字段。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SuggestionProperties.Bailian properties;

    /**
     * Creates a Bailian model adapter.
     *
     * @param restClient configured HTTP client
     * @param objectMapper JSON mapper
     * @param properties Bailian settings
     */
    public BailianNameSuggestionClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            SuggestionProperties.Bailian properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Sends image and text input to qwen3.6-flash and parses the structured dish name.
     *
     * @param imageUrl validated image URL
     * @return structured model result
     */
    @Override
    public ModelNameSuggestion suggest(String imageUrl) {
        requireApiKey();
        Map<String, Object> request = buildRequest(imageUrl);
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (requestSpec, response) -> {
                        throw new NameSuggestionClientException(
                                NameSuggestionClientException.Reason.MODEL_ERROR,
                                "model inference failed"
                        );
                    })
                    .body(String.class);
        } catch (NameSuggestionClientException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.NETWORK,
                    "model inference network error",
                    ex
            );
        }
        return parseResponse(responseBody);
    }

    private Map<String, Object> buildRequest(String imageUrl) {
        return Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", DEFAULT_SYSTEM_PROMPT),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)),
                                        Map.of("type", "text", "text", DEFAULT_USER_PROMPT)
                                )
                        )
                ),
                "temperature", 0.2
        );
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.MODEL_ERROR,
                    "model api key is not configured"
            );
        }
    }

    private ModelNameSuggestion parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            JsonNode modelJson = objectMapper.readTree(stripJsonFence(content));
            String suggestedName = modelJson.path("suggested_name").asText(null);
            if (!StringUtils.hasText(suggestedName)) {
                throw new NameSuggestionClientException(
                        NameSuggestionClientException.Reason.INVALID_RESPONSE,
                        "model response missing suggested_name"
                );
            }
            return new ModelNameSuggestion(suggestedName.trim());
        } catch (NameSuggestionClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.INVALID_RESPONSE,
                    "model response is invalid",
                    ex
            );
        }
    }

    private String stripJsonFence(String content) {
        if (!StringUtils.hasText(content)) {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.INVALID_RESPONSE,
                    "model response content is empty"
            );
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int fenceEnd = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && fenceEnd > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, fenceEnd).trim();
            }
        }
        return trimmed;
    }
}
