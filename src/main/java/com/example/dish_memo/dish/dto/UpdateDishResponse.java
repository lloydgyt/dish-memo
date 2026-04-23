package com.example.dish_memo.dish.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Response returned after updating a dish record.
 *
 * @param id dish ID
 * @param name latest dish name
 * @param imageUrl latest image URL
 * @param note optional latest note
 * @param date latest dish date
 * @param mealType latest meal category
 * @param updatedAt RFC3339 update timestamp
 */
public record UpdateDishResponse(
        String id,
        String name,
        String imageUrl,
        String note,
        LocalDate date,
        String mealType,
        OffsetDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Hong_Kong");

    /**
     * Creates an update response DTO from the persistence model.
     *
     * @param record dish record
     * @return update response DTO
     */
    public static UpdateDishResponse from(DishRecord record) {
        return new UpdateDishResponse(
                record.getId(),
                record.getName(),
                record.getImageUrl(),
                record.getNote(),
                record.getDate(),
                record.getMealType(),
                record.getUpdatedAt().atZone(API_ZONE).toOffsetDateTime()
        );
    }
}
