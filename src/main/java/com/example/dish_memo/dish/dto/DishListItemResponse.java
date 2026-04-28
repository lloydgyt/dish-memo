package com.example.dish_memo.dish.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Compact dish response for list and recommendation views.
 *
 * @param id dish ID
 * @param name dish name
 * @param fileId object storage file ID
 * @param date dish date
 * @param mealType meal category
 * @param updatedAt RFC3339 latest update timestamp
 */
public record DishListItemResponse(
        String id,
        String name,
        String fileId,
        LocalDate date,
        String mealType,
        OffsetDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Hong_Kong");

    /**
     * Creates a compact response DTO from the persistence model.
     *
     * @param record dish record
     * @return compact response DTO
     */
    public static DishListItemResponse from(DishRecord record) {
        return new DishListItemResponse(
                record.getId(),
                record.getName(),
                record.getFileId(),
                record.getDate(),
                record.getMealType(),
                record.getUpdatedAt().atZone(API_ZONE).toOffsetDateTime()
        );
    }
}
