package com.example.dish_memo.dish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body used for creating and updating dish records.
 *
 * @param name dish name confirmed by the user
 * @param fileId object storage file ID for the dish image
 * @param note optional user note
 * @param date cooking or eating date
 * @param mealType requested meal category
 */
public record DishRequest(
        @NotBlank(message = "name is required") @Size(max = 50, message = "name length must be <= 50") String name,
        @NotBlank(message = "file_id is required") String fileId,
        @Size(max = 500, message = "note length must be <= 500") String note,
        @NotNull(message = "date is required") LocalDate date,
        @NotBlank(message = "meal_type is required") String mealType
) {
}
