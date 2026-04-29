package com.example.dish_memo.common;

import com.example.dish_memo.dish.controller.DishController;
import com.example.dish_memo.dish.dto.CreateDishResponse;
import com.example.dish_memo.dish.dto.DeleteDishResponse;
import com.example.dish_memo.dish.dto.DishListItemResponse;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishResponse;
import com.example.dish_memo.dish.dto.UpdateDishResponse;
import com.example.dish_memo.dish.service.DishService;
import com.example.dish_memo.recommendation.controller.RecommendationController;
import com.example.dish_memo.recommendation.dto.RecommendationItemResponse;
import com.example.dish_memo.recommendation.dto.TodayMealsResponse;
import com.example.dish_memo.recommendation.service.RecommendationService;
import com.example.dish_memo.suggestion.controller.SuggestionController;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import com.example.dish_memo.suggestion.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DishController.class,
        SuggestionController.class,
        RecommendationController.class
})
@Import({WebConfig.class, RequestLoggingInterceptor.class, UserContextInterceptor.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.jackson.property-naming-strategy=SNAKE_CASE")
@ExtendWith(OutputCaptureExtension.class)
class ApiContractTest {
    private static final String USER_ID = "u_1";
    private static final String FILE_ID = "production/dish/u_1/img_01HRXYZ.jpg";
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-04-18T10:23:11+08:00");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DishService dishService;

    @MockBean
    private SuggestionService suggestionService;

    @MockBean
    private RecommendationService recommendationService;

    @Test
    void nameSuggestionUsesImageUrlAndSnakeCaseResponse() throws Exception {
        when(suggestionService.suggest(eq(USER_ID), any()))
                .thenReturn(new NameSuggestionResponse("番茄炒蛋", "success", null));

        mockMvc.perform(post("/api/v1/dishes/name-suggestions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"image_url\":\"https://img.example.com/dish.jpg\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggested_name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data.model_status").value("success"));
    }

    @Test
    void nameSuggestionRejectsMissingImageUrlWithDocumentedError() throws Exception {
        mockMvc.perform(post("/api/v1/dishes/name-suggestions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001001))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void createDishUsesDocumentedRequestAndResponseFields() throws Exception {
        when(dishService.create(eq(USER_ID), any())).thenReturn(new CreateDishResponse(
                "dish_1", "番茄炒蛋", FILE_ID, "少糖", LocalDate.parse("2026-04-18"), "dinner", TIME
        ));

        mockMvc.perform(post("/api/v1/dishes")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"name\":\"番茄炒蛋\","
                                + "\"file_id\":\"production/dish/u_1/img_01HRXYZ.jpg\","
                                + "\"note\":\"少糖\","
                                + "\"date\":\"2026-04-18\","
                                + "\"meal_type\":\"dinner\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("dish_1"))
                .andExpect(jsonPath("$.data.file_id").value(FILE_ID))
                .andExpect(jsonPath("$.data.meal_type").value("dinner"))
                .andExpect(jsonPath("$.data.created_at").exists());
    }

    @Test
    void listDishUsesDocumentedPaginationAndItemFields() throws Exception {
        when(dishService.list(eq(USER_ID), eq(1), eq(20), eq("dinner"), any(), any(), eq("番茄")))
                .thenReturn(new DishPageResponse(
                        List.of(new DishListItemResponse("dish_1", "番茄炒蛋", FILE_ID,
                                LocalDate.parse("2026-04-18"), "dinner", TIME)),
                        1,
                        1,
                        20
                ));

        mockMvc.perform(get("/api/v1/dishes")
                        .header("X-User-Id", USER_ID)
                        .param("page_no", "1")
                        .param("page_size", "20")
                        .param("meal_type", "dinner")
                        .param("date_from", "2026-04-01")
                        .param("date_to", "2026-04-30")
                        .param("keyword", "番茄"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.list[0].file_id").value(FILE_ID))
                .andExpect(jsonPath("$.data.list[0].updated_at").exists());
    }

    @Test
    void getDishUsesDocumentedDetailFields() throws Exception {
        when(dishService.get(USER_ID, "dish_1")).thenReturn(new DishResponse(
                "dish_1", "番茄炒蛋", FILE_ID, null, LocalDate.parse("2026-04-18"), "dinner", TIME, TIME
        ));

        mockMvc.perform(get("/api/v1/dishes/dish_1").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_id").value(FILE_ID))
                .andExpect(jsonPath("$.data.created_at").exists())
                .andExpect(jsonPath("$.data.updated_at").exists());
    }

    @Test
    void updateDishUsesDocumentedFields() throws Exception {
        when(dishService.update(eq(USER_ID), eq("dish_1"), any())).thenReturn(new UpdateDishResponse(
                "dish_1", "家常番茄炒蛋", FILE_ID, null, LocalDate.parse("2026-04-18"), "lunch", TIME
        ));

        mockMvc.perform(put("/api/v1/dishes/dish_1")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"name\":\"家常番茄炒蛋\","
                                + "\"file_id\":\"production/dish/u_1/img_01HRXYZ.jpg\","
                                + "\"date\":\"2026-04-18\","
                                + "\"meal_type\":\"lunch\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_id").value(FILE_ID))
                .andExpect(jsonPath("$.data.meal_type").value("lunch"))
                .andExpect(jsonPath("$.data.updated_at").exists());
    }

    @Test
    void deleteDishUsesDocumentedSuccessField() throws Exception {
        when(dishService.delete(USER_ID, "dish_1")).thenReturn(new DeleteDishResponse(true));

        mockMvc.perform(delete("/api/v1/dishes/dish_1").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void todayMealsUsesDocumentedRecommendationFields(CapturedOutput output) throws Exception {
        when(recommendationService.todayMeals(USER_ID, "breakfast", 3)).thenReturn(new TodayMealsResponse(
                "breakfast",
                3,
                1,
                false,
                null,
                List.of(new RecommendationItemResponse("dish_1", "三明治", FILE_ID,
                        LocalDate.parse("2026-04-02"), "breakfast"))
        ));

        mockMvc.perform(get("/api/v1/recommendations/today-meals")
                        .header("X-User-Id", USER_ID)
                        .param("meal_type", "breakfast")
                        .param("size", "3")
                        .param("refresh_token", "r_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meal_type").value("breakfast"))
                .andExpect(jsonPath("$.data.requested_size").value(3))
                .andExpect(jsonPath("$.data.actual_size").value(1))
                .andExpect(jsonPath("$.data.is_empty").value(false))
                .andExpect(jsonPath("$.data.list[0].file_id").value(FILE_ID));

        assertThat(output).contains("\"request_id\"");
        assertThat(output).contains("\"user_id\":\"u_1\"");
        assertThat(output).contains("\"request_params\"");
        assertThat(output).contains("\"meal_type\":\"breakfast\"");
        assertThat(output).contains("\"refresh_token\":\"[REDACTED]\"");
        assertThat(output).contains("\"method\":\"GET\"");
        assertThat(output).contains("\"path\":\"/api/v1/recommendations/today-meals\"");
        assertThat(output).contains("\"status\":200");
        assertThat(output).contains("\"duration_ms\"");
        assertThat(output).doesNotContain("r_1");
    }

    @Test
    void removedImageUploadEndpointIsNotAvailable() throws Exception {
        mockMvc.perform(post("/api/v1/files/images").header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestLogContainsStatusAndDurationForErrorResponse(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/dishes")
                        .header("X-Request-Id", "req_error")
                        .param("page_size", "20"))
                .andExpect(status().isUnauthorized());

        assertThat(output).contains("\"request_id\":\"req_error\"");
        assertThat(output).contains("\"user_id\":\"UNKNOWN\"");
        assertThat(output).contains("\"method\":\"GET\"");
        assertThat(output).contains("\"path\":\"/api/v1/dishes\"");
        assertThat(output).contains("\"status\":401");
        assertThat(output).contains("\"duration_ms\"");
    }
}
