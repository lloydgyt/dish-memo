package com.example.dish_memo.testing.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.dish.dto.DishListItemResponse;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.MealType;
import com.example.dish_memo.testing.mapper.TestingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for the in-memory baseline endpoint.
 */
@Service
public class TestingService {
    private final TestingMapper testingMapper;

    public TestingService(TestingMapper testingMapper) {
        this.testingMapper = testingMapper;
    }

    /**
     * Returns a paginated response with the same service-level validation and DTO mapping as dish list.
     *
     * @param userId current user ID
     * @param pageNo requested page number
     * @param pageSize requested page size
     * @param mealType raw optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional dish name keyword
     * @return paginated response
     */
    public DishPageResponse list(
            String userId,
            int pageNo,
            int pageSize,
            String mealType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword
    ) {
        validatePage(pageNo, pageSize);
        String parsedMealType = StringUtils.hasText(mealType) ? MealType.from(mealType).name() : null;
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "date_from must be before or equal to date_to");
        }
        if (keyword != null && keyword.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "keyword length must be <= 50");
        }

        int offset = (pageNo - 1) * pageSize;
        long total = testingMapper.countByFilters(userId, parsedMealType, dateFrom, dateTo, keyword);
        List<DishRecord> records = testingMapper.listByFilters(
                userId,
                parsedMealType,
                dateFrom,
                dateTo,
                keyword,
                pageSize,
                offset
        );
        List<DishListItemResponse> list = records.stream()
                .map(DishListItemResponse::from)
                .toList();
        return new DishPageResponse(list, total, pageNo, pageSize);
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_no must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_size must be between 1 and 100");
        }
    }
}
