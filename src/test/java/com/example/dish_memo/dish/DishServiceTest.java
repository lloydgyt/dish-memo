package com.example.dish_memo.dish;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.DishRequest;
import com.example.dish_memo.dish.dto.CreateDishResponse;
import com.example.dish_memo.dish.mapper.DishMapper;
import com.example.dish_memo.dish.service.DishService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishServiceTest {

    @Test
    void createStoresDishForCurrentUser() {
        DishMapper mapper = mock(DishMapper.class);
        DishService service = new DishService(mapper);

        CreateDishResponse response = service.create("u_1", new DishRequest(
                "番茄炒蛋",
                "production/dish/u_1/dish.jpg",
                "少糖",
                LocalDate.parse("2026-04-18"),
                "dinner"
        ));

        assertThat(response.id()).startsWith("dish_");
        assertThat(response.mealType()).isEqualTo("dinner");
        verify(mapper).insert(any(DishRecord.class));
    }

    @Test
    void getRejectsOtherUsersRecordAsForbidden() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.findByIdAndUserId("dish_1", "u_other")).thenReturn(null);
        DishService service = new DishService(mapper);

        assertThatThrownBy(() -> service.get("u_other", "dish_1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(mapper).findByIdAndUserId("dish_1", "u_other");
    }

    @Test
    void listValidatesDateRangeAndPagination() {
        DishMapper mapper = mock(DishMapper.class);
        DishService service = new DishService(mapper);

        assertThatThrownBy(() -> service.list("u_1", 0, 20, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page_no");

        assertThatThrownBy(() -> service.list(
                "u_1",
                1,
                20,
                null,
                LocalDate.parse("2026-04-20"),
                LocalDate.parse("2026-04-18"),
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("date_from");
    }

    @Test
    void listReturnsOnlyMapperFilteredRecords() {
        DishMapper mapper = mock(DishMapper.class);
        DishRecord record = record("dish_1", "u_1", "breakfast");
        when(mapper.countByFilters(eq("u_1"), eq("breakfast"), any(), any(), eq("蛋"))).thenReturn(1L);
        when(mapper.listByFilters(eq("u_1"), eq("breakfast"), any(), any(), eq("蛋"), eq(10), eq(0)))
                .thenReturn(List.of(record));
        DishService service = new DishService(mapper);

        DishPageResponse response = service.list(
                "u_1",
                1,
                10,
                "breakfast",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                "蛋"
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.list()).hasSize(1);
        assertThat(response.list().get(0).id()).isEqualTo("dish_1");
    }

    private DishRecord record(String id, String userId, String mealType) {
        DishRecord record = new DishRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setName("菜");
        record.setFileId("production/dish/u_1/dish.jpg");
        record.setDate(LocalDate.parse("2026-04-18"));
        record.setMealType(mealType);
        record.setCreatedAt(LocalDateTime.parse("2026-04-18T10:00:00"));
        record.setUpdatedAt(LocalDateTime.parse("2026-04-18T10:00:00"));
        return record;
    }
}
