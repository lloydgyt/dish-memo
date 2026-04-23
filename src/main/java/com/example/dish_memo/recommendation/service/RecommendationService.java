package com.example.dish_memo.recommendation.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.common.StructuredLogUtils;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.MealType;
import com.example.dish_memo.dish.service.DishService;
import com.example.dish_memo.recommendation.dto.RecommendationItemResponse;
import com.example.dish_memo.recommendation.dto.TodayMealsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Business service for random meal recommendations.
 */
@Service
public class RecommendationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);

    private final DishService dishService;

    /**
     * Creates the service with the dish query dependency.
     *
     * @param dishService dish business service
     */
    public RecommendationService(DishService dishService) {
        this.dishService = dishService;
    }

    /**
     * Randomly samples non-duplicated dishes for one meal type.
     *
     * @param userId current user ID
     * @param mealType raw meal type
     * @param size requested candidate count
     * @return recommendation response
     */
    public TodayMealsResponse todayMeals(String userId, String mealType, int size) {
        LOGGER.info(StructuredLogUtils.info(userId, "recommend today meals"));
        MealType parsedMealType = MealType.from(mealType);
        if (size <= 0 || size > 10) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "size must be between 1 and 10");
        }
        List<DishRecord> candidates = new ArrayList<>(dishService.listRecommendationCandidates(userId, parsedMealType.name()));
        Collections.shuffle(candidates);
        List<RecommendationItemResponse> selected = candidates.stream()
                .limit(size)
                .map(RecommendationItemResponse::from)
                .toList();
        boolean empty = selected.isEmpty();
        return new TodayMealsResponse(
                parsedMealType.name(),
                size,
                selected.size(),
                empty,
                empty ? "你还没有记录过这类餐食" : null,
                selected
        );
    }
}
