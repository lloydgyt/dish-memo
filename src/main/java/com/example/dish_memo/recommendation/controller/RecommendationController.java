package com.example.dish_memo.recommendation.controller;

import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.common.ApiHeaders;
import com.example.dish_memo.recommendation.dto.TodayMealsResponse;
import com.example.dish_memo.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API controller for today meal recommendations.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    /**
     * Creates the controller with its recommendation dependency.
     *
     * @param recommendationService recommendation service
     */
    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Returns random non-duplicated dishes for the requested meal type.
     *
     * @param userId current OpenID from gateway header
     * @param mealType requested meal type
     * @param size requested candidate count
     * @param refreshToken optional refresh token for API compatibility
     * @return today meal recommendations
     */
    @GetMapping("/today-meals")
    public ApiResponse<TodayMealsResponse> todayMeals(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @RequestParam(name = "meal_type") String mealType,
            @RequestParam(name = "size", defaultValue = "3") int size,
            @RequestParam(name = "refresh_token", required = false) String refreshToken
    ) {
        return ApiResponse.ok(recommendationService.todayMeals(userId, mealType, size));
    }
}
