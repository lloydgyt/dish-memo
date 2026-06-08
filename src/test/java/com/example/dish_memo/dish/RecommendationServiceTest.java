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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void todayMealsReturnsEmptyStateWhenNoCandidatesExist() {
        DishService dishService = mock(DishService.class);
        when(dishService.countRecommendationCandidates("u_1", "breakfast")).thenReturn(0L);
        RecommendationService service = new RecommendationService(dishService);

        TodayMealsResponse response = service.todayMeals("u_1", "breakfast", 3);

        assertThat(response.isEmpty()).isTrue();
        assertThat(response.actualSize()).isZero();
        assertThat(response.emptyTip()).isEqualTo("你还没有记录过这类餐食");
        verify(dishService).countRecommendationCandidates("u_1", "breakfast");
    }

    @Test
    void todayMealsLimitsSizeWithoutDuplicates() {
        DishService dishService = mock(DishService.class);
        when(dishService.countRecommendationCandidates("u_1", "dinner")).thenReturn(4L);
        when(dishService.listRecommendationCandidatePage("u_1", "dinner", 1)).thenReturn(List.of(
                record("dish_1"),
                record("dish_2"),
                record("dish_3"),
                record("dish_4")
        ));
        RecommendationService service = new RecommendationService(dishService);

        TodayMealsResponse response = service.todayMeals("u_1", "dinner", 3);

        assertThat(response.actualSize()).isEqualTo(3);
        assertThat(response.list()).extracting("id").doesNotHaveDuplicates();
        verify(dishService).listRecommendationCandidatePage("u_1", "dinner", 1);
    }

    @Test
    void todayMealsReadsOnlyOneRecommendationPage() {
        DishService dishService = mock(DishService.class);
        when(dishService.countRecommendationCandidates("u_1", "dinner")).thenReturn(250L);
        when(dishService.listRecommendationCandidatePage(eq("u_1"), eq("dinner"), anyInt()))
                .thenReturn(List.of(
                        record("dish_1"),
                        record("dish_2"),
                        record("dish_3"),
                        record("dish_4")
                ));
        RecommendationService service = new RecommendationService(dishService);

        TodayMealsResponse response = service.todayMeals("u_1", "dinner", 2);

        assertThat(response.actualSize()).isEqualTo(2);
        verify(dishService).countRecommendationCandidates("u_1", "dinner");
        verify(dishService).listRecommendationCandidatePage(eq("u_1"), eq("dinner"), anyInt());
    }

    @Test
    void todayMealsValidatesSize() {
        DishService dishService = mock(DishService.class);
        RecommendationService service = new RecommendationService(dishService);

        assertThatThrownBy(() -> service.todayMeals("u_1", "lunch", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size");
        verifyNoInteractions(dishService);
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
