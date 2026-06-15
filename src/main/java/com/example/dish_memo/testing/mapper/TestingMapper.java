package com.example.dish_memo.testing.mapper;

import com.example.dish_memo.dish.dto.DishRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * In-memory mapper for baseline testing; intentionally avoids MyBatis dynamic proxies and database IO.
 */
@Repository
public class TestingMapper {
    private static final int FIXED_TOTAL = 20;
    private static final List<DishRecord> FIXED_RECORDS = IntStream.rangeClosed(1, FIXED_TOTAL)
            .mapToObj(TestingMapper::record)
            .toList();

    /**
     * Returns a fixed total with the same signature shape as dish list counting.
     *
     * @param userId current user ID
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional name keyword
     * @return fixed total
     */
    public long countByFilters(
            String userId,
            String mealType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword
    ) {
        return FIXED_TOTAL;
    }

    /**
     * Returns fixed in-memory records with the same signature shape as dish list querying.
     *
     * @param userId current user ID
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional name keyword
     * @param limit page size
     * @param offset page offset
     * @return fixed record page
     */
    public List<DishRecord> listByFilters(
            String userId,
            String mealType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword,
            int limit,
            int offset
    ) {
        if (offset >= FIXED_RECORDS.size()) {
            return List.of();
        }
        int endExclusive = Math.min(offset + limit, FIXED_RECORDS.size());
        return FIXED_RECORDS.subList(offset, endExclusive);
    }

    private static DishRecord record(int index) {
        DishRecord record = new DishRecord();
        record.setId("testing_dish_" + index);
        record.setUserId("testing_user");
        record.setName("testing dish " + index);
        record.setFileId("production/dish/testing_user/testing_" + index + ".jpg");
        record.setNote("testing baseline record " + index);
        record.setDate(LocalDate.of(2026, 1, 1).plusDays(index - 1L));
        record.setMealType(mealType(index));
        record.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0).plusMinutes(index));
        record.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 9, 0).plusMinutes(index));
        return record;
    }

    private static String mealType(int index) {
        return switch (index % 3) {
            case 1 -> "breakfast";
            case 2 -> "lunch";
            default -> "dinner";
        };
    }
}
