package com.example.dish_memo.recommendation.dto;

import com.example.dish_memo.dish.dto.DishRecord;

import java.time.LocalDate;

/**
 * Compact recommendation candidate response.
 *
 * @param id dish ID
 * @param name dish name
 * @param imageUrl image URL
 * @param date historical dish date
 * @param mealType meal category
 */
public record RecommendationItemResponse(
        String id,
        String name,
        String imageUrl,
        LocalDate date,
        String mealType
) {
    /**
     * Creates a recommendation item from a dish record.
     *
     * @param record dish record
     * @return recommendation item
     */
    public static RecommendationItemResponse from(DishRecord record) {
        return new RecommendationItemResponse(
                record.getId(),
                record.getName(),
                record.getImageUrl(),
                record.getDate(),
                record.getMealType()
        );
    }
}
