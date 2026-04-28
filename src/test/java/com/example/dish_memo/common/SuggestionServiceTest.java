package com.example.dish_memo.common;

import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
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
    void suggestReturnsSuccessForValidFileId(CapturedOutput output) {
        SuggestionService service = new SuggestionService();

        NameSuggestionResponse response = service.suggest("u_1", new NameSuggestionRequest("production/dish/u_1/dish.jpg"));

        assertThat(response.modelStatus()).isEqualTo("success");
        assertThat(response.suggestedName()).isNotBlank();
        assertThat(output).contains("\"userId\":\"u_1\"");
        assertThat(output).contains("\"description\":\"suggest dish name\"");
    }

    @Test
    void suggestReturnsFallbackWhenModelFails() {
        SuggestionService service = new SuggestionService();

        NameSuggestionResponse response = service.suggest("u_1", new NameSuggestionRequest("production/dish/u_1/force_model_failed.jpg"));

        assertThat(response.modelStatus()).isEqualTo("failed");
        assertThat(response.suggestedName()).isNull();
        assertThat(response.reason()).contains("timeout");
    }

    @Test
    void suggestRejectsInvalidFileId() {
        SuggestionService service = new SuggestionService();

        assertThatThrownBy(() -> service.suggest("u_1", new NameSuggestionRequest("not-a-file-id")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }
}
