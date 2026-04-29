package com.example.dish_memo.common;

import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import com.example.dish_memo.suggestion.client.ModelNameSuggestion;
import com.example.dish_memo.suggestion.client.NameSuggestionClient;
import com.example.dish_memo.suggestion.client.NameSuggestionClientException;
import com.example.dish_memo.suggestion.config.SuggestionProperties;
import com.example.dish_memo.suggestion.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class SuggestionServiceTest {

    @Test
    void suggestReturnsSuccessForAllowedHttpsImageUrl(CapturedOutput output) {
        SuggestionService service = serviceWithClient(imageUrl -> new ModelNameSuggestion("番茄炒蛋"));

        NameSuggestionResponse response = service.suggest(
                "u_1",
                new NameSuggestionRequest("https://img.example.com/dish.jpg")
        );

        assertThat(response.modelStatus()).isEqualTo("success");
        assertThat(response.suggestedName()).isEqualTo("番茄炒蛋");
        assertThat(output).contains("\"userId\":\"u_1\"");
        assertThat(output).contains("\"description\":\"suggest dish name\"");
    }

    @Test
    void suggestRejectsNonHttpsImageUrl() {
        SuggestionService service = serviceWithClient(imageUrl -> new ModelNameSuggestion("不会调用"));

        assertThatThrownBy(() -> service.suggest("u_1", new NameSuggestionRequest("http://img.example.com/dish.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    void suggestRejectsUnlistedImageUrlHost() {
        SuggestionService service = serviceWithClient(imageUrl -> new ModelNameSuggestion("不会调用"));

        assertThatThrownBy(() -> service.suggest("u_1", new NameSuggestionRequest("https://evil.example.net/dish.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    void suggestDowngradesModelGenerationFailure() {
        SuggestionService service = serviceWithClient(imageUrl -> {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.MODEL_ERROR,
                    "model inference timeout"
            );
        });

        NameSuggestionResponse response = service.suggest(
                "u_1",
                new NameSuggestionRequest("https://img.example.com/dish.jpg")
        );

        assertThat(response.suggestedName()).isNull();
        assertThat(response.modelStatus()).isEqualTo("failed");
        assertThat(response.reason()).isEqualTo("model inference timeout");
    }

    @Test
    void suggestMapsNetworkErrorToObjectStorageAccessFailure() {
        SuggestionService service = serviceWithClient(imageUrl -> {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.NETWORK,
                    "model inference network error"
            );
        });

        assertThatThrownBy(() -> service.suggest("u_1", new NameSuggestionRequest("https://img.example.com/dish.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OBJECT_STORAGE_ACCESS_FAILED);
    }

    @Test
    void suggestMapsInvalidModelResponseToLlmFailure() {
        SuggestionService service = serviceWithClient(imageUrl -> {
            throw new NameSuggestionClientException(
                    NameSuggestionClientException.Reason.INVALID_RESPONSE,
                    "model response is invalid"
            );
        });

        assertThatThrownBy(() -> service.suggest("u_1", new NameSuggestionRequest("https://img.example.com/dish.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_FAILED);
    }

    private SuggestionService serviceWithClient(NameSuggestionClient client) {
        SuggestionProperties properties = new SuggestionProperties();
        properties.setImageUrlAllowedHosts(java.util.List.of("img.example.com"));
        return new SuggestionService(client, properties);
    }
}
