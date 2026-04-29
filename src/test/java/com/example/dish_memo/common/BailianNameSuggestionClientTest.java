package com.example.dish_memo.common;

import com.example.dish_memo.suggestion.client.BailianNameSuggestionClient;
import com.example.dish_memo.suggestion.client.ModelNameSuggestion;
import com.example.dish_memo.suggestion.client.NameSuggestionClientException;
import com.example.dish_memo.suggestion.config.SuggestionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BailianNameSuggestionClientTest {

    @Test
    void suggestParsesMockBailianResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dashscope.test/compatible-mode/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BailianNameSuggestionClient client = new BailianNameSuggestionClient(
                builder.build(),
                new ObjectMapper(),
                properties()
        );
        server.expect(requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3.6-flash"))
                .andExpect(jsonPath("$.messages[1].content[0].image_url.url").value("https://img.example.com/dish.jpg"))
                .andExpect(jsonPath("$.messages[1].content[1].text").value("请根据图片推荐一个适合保存到做菜记录里的中文菜名。"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"suggested_name\\":\\"番茄炒蛋\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ModelNameSuggestion result = client.suggest("https://img.example.com/dish.jpg");

        assertThat(result.suggestedName()).isEqualTo("番茄炒蛋");
        server.verify();
    }

    @Test
    void suggestConvertsServerErrorToClientException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dashscope.test/compatible-mode/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BailianNameSuggestionClient client = new BailianNameSuggestionClient(
                builder.build(),
                new ObjectMapper(),
                properties()
        );
        server.expect(requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.suggest("https://img.example.com/dish.jpg"))
                .isInstanceOf(NameSuggestionClientException.class)
                .extracting("reason")
                .isEqualTo(NameSuggestionClientException.Reason.MODEL_ERROR);
        server.verify();
    }

    private SuggestionProperties.Bailian properties() {
        SuggestionProperties.Bailian properties = new SuggestionProperties.Bailian();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://dashscope.test/compatible-mode/v1");
        properties.setModel("qwen3.6-flash");
        return properties;
    }
}
