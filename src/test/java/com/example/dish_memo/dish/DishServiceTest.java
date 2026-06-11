package com.example.dish_memo.dish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.DishRequest;
import com.example.dish_memo.dish.dto.CreateDishResponse;
import com.example.dish_memo.dish.mapper.DishMapper;
import com.example.dish_memo.dish.service.DishService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishServiceTest {

    @Test
    void createStoresDishForCurrentUser() {
        DishMapper mapper = mock(DishMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DishService service = newService(mapper, redisTemplate);

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
        verify(redisTemplate).delete(any(String.class));
    }

    @Test
    void getRejectsOtherUsersRecordAsForbidden() {
        DishMapper mapper = mock(DishMapper.class);
        when(mapper.findByIdAndUserId("dish_1", "u_other")).thenReturn(null);
        DishService service = newService(mapper);

        assertThatThrownBy(() -> service.get("u_other", "dish_1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(mapper).findByIdAndUserId("dish_1", "u_other");
    }

    @Test
    void listValidatesDateRangeAndPagination() {
        DishMapper mapper = mock(DishMapper.class);
        DishService service = newService(mapper);

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
        DishService service = newService(mapper);

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

    @Test
    void listUsesRedisForUnfilteredCountAndPage() {
        DishMapper mapper = mock(DishMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(any(String.class)))
                .thenReturn("1")
                .thenReturn("""
                        [{
                          "id":"dish_1",
                          "userId":"u_1",
                          "name":"菜",
                          "fileId":"production/dish/u_1/dish.jpg",
                          "date":"2026-04-18",
                          "mealType":"dinner",
                          "createdAt":"2026-04-18T10:00:00",
                          "updatedAt":"2026-04-18T10:00:00"
                        }]
                        """);
        DishService service = newService(mapper, redisTemplate);

        DishPageResponse response = service.list("u_1", 1, 20, null, null, null, null);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.list()).hasSize(1);
        assertThat(response.list().get(0).id()).isEqualTo("dish_1");
        verify(mapper, never()).countByFilters(any(), any(), any(), any(), any());
        verify(mapper, never()).listByFilters(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void deleteEvictsCurrentUsersUnfilteredListCache() {
        DishMapper mapper = mock(DishMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(mapper.findByIdAndUserId("dish_1", "u_1")).thenReturn(record("dish_1", "u_1", "dinner"));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Set.of("dish:list:page:v1:user:20:0"));
        DishService service = newService(mapper, redisTemplate);

        service.delete("u_1", "dish_1");

        verify(mapper).deleteByIdAndUserId("dish_1", "u_1");
        verify(redisTemplate).delete(any(String.class));
        verify(redisTemplate).execute(any(RedisCallback.class));
        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate).delete(Set.of("dish:list:page:v1:user:20:0"));
    }

    private DishService newService(DishMapper mapper) {
        return newService(mapper, mock(StringRedisTemplate.class));
    }

    private DishService newService(DishMapper mapper, StringRedisTemplate redisTemplate) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new DishService(mapper, redisTemplate, objectMapper);
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
