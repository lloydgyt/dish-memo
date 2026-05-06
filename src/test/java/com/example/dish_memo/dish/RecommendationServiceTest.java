package com.example.dish_memo.dish;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.service.DishService;
import com.example.dish_memo.recommendation.dto.TodayMealsResponse;
import com.example.dish_memo.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void todayMealsReturnsEmptyStateWhenNoCandidatesExist() {
        DishService dishService = mock(DishService.class);
        when(dishService.listRecommendationCandidates("u_1", "breakfast")).thenReturn(List.of());
        RecommendationService service = new RecommendationService(dishService);

        TodayMealsResponse response = service.todayMeals("u_1", "breakfast", 3);

        assertThat(response.isEmpty()).isTrue();
        assertThat(response.actualSize()).isZero();
        assertThat(response.emptyTip()).isEqualTo("你还没有记录过这类餐食");
        verify(dishService).listRecommendationCandidates("u_1", "breakfast");
    }

    @Test
    void todayMealsLimitsSizeWithoutDuplicates() {
        DishService dishService = mock(DishService.class);
        when(dishService.listRecommendationCandidates("u_1", "dinner")).thenReturn(List.of(
                record("dish_1"),
                record("dish_2"),
                record("dish_3"),
                record("dish_4")
        ));
        RecommendationService service = new RecommendationService(dishService);

        TodayMealsResponse response = service.todayMeals("u_1", "dinner", 3);

        assertThat(response.actualSize()).isEqualTo(3);
        assertThat(response.list()).extracting("id").doesNotHaveDuplicates();
    }

    @Test
    void todayMealsValidatesSize() {
        RecommendationService service = new RecommendationService(mock(DishService.class));

        assertThatThrownBy(() -> service.todayMeals("u_1", "lunch", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size");
    }

    private DishRecord record(String id) {
        DishRecord record = new DishRecord();
        record.setId(id);
        record.setUserId("u_1");
        record.setName("菜" + id);
        record.setFileId("production/dish/u_1/" + id + ".jpg");
        record.setDate(LocalDate.parse("2026-04-18"));
        record.setMealType("dinner");
        record.setCreatedAt(LocalDateTime.parse("2026-04-18T10:00:00"));
        record.setUpdatedAt(LocalDateTime.parse("2026-04-18T10:00:00"));
        return record;
    }
}
