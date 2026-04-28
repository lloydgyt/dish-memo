package com.example.dish_memo.dish.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Response returned after creating a dish record.
 *
 * @param id dish ID
 * @param name dish name
 * @param fileId object storage file ID
 * @param note optional note
 * @param date dish date
 * @param mealType meal category
 * @param createdAt RFC3339 creation timestamp
 */
public record CreateDishResponse(
        String id,
        String name,
        String fileId,
        String note,
        LocalDate date,
        String mealType,
        OffsetDateTime createdAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Hong_Kong");

    /**
     * Creates a create response DTO from the persistence model.
     *
     * @param record dish record
     * @return create response DTO
     */
    public static CreateDishResponse from(DishRecord record) {
        return new CreateDishResponse(
                record.getId(),
                record.getName(),
                record.getFileId(),
                record.getNote(),
                record.getDate(),
                record.getMealType(),
                record.getCreatedAt().atZone(API_ZONE).toOffsetDateTime()
        );
    }
}
