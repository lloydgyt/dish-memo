package com.example.dish_memo.dish.dto;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;

/**
 * Supported meal categories for dish records and recommendations.
 */
public enum MealType {
    breakfast,
    lunch,
    dinner;

    /**
     * Parses and validates a meal type value from an API request.
     *
     * @param value raw request value
     * @return matching meal type
     */
    public static MealType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "meal_type is required");
        }
        try {
            return MealType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "meal_type is invalid");
        }
    }
}
