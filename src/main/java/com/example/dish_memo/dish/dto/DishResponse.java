package com.example.dish_memo.dish.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Full dish record response.
 *
 * @param id dish ID
 * @param name dish name
 * @param imageUrl image URL
 * @param note optional note
 * @param date dish date
 * @param mealType meal category
 * @param createdAt RFC3339 creation timestamp
 * @param updatedAt RFC3339 update timestamp
 */
public record DishResponse(
        String id,
        String name,
        String imageUrl,
        String note,
        LocalDate date,
        String mealType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Hong_Kong");

    /**
     * Creates a response DTO from the persistence model.
     *
     * @param record dish record
     * @return API response DTO
     */
    public static DishResponse from(DishRecord record) {
        return new DishResponse(
                record.getId(),
                record.getName(),
                record.getImageUrl(),
                record.getNote(),
                record.getDate(),
                record.getMealType(),
                record.getCreatedAt().atZone(API_ZONE).toOffsetDateTime(),
                record.getUpdatedAt().atZone(API_ZONE).toOffsetDateTime()
        );
    }
}
