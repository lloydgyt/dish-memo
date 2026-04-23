package com.example.dish_memo.recommendation.dto;

import java.util.List;

/**
 * Response for the "what to eat today" recommendation endpoint.
 *
 * @param mealType requested meal category
 * @param requestedSize requested candidate count
 * @param actualSize returned candidate count
 * @param isEmpty true when the user has no records for the meal type
 * @param emptyTip empty state text or null
 * @param list recommendation candidates
 */
public record TodayMealsResponse(
        String mealType,
        int requestedSize,
        int actualSize,
        boolean isEmpty,
        String emptyTip,
        List<RecommendationItemResponse> list
) {
}
