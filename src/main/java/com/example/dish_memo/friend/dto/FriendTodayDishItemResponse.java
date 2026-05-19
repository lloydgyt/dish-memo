package com.example.dish_memo.friend.dto;

import java.time.LocalDate;

/**
 * One dish record in the friend today dishes response.
 *
 * @param friendUid friend user ID
 * @param friendAvatarFileId friend avatar file ID or null
 * @param friendNickname friend display nickname
 * @param dishId dish record ID
 * @param dishName dish name
 * @param dishFileId dish image file ID
 * @param mealType meal type
 * @param date server local date
 */
public record FriendTodayDishItemResponse(
        String friendUid,
        String friendAvatarFileId,
        String friendNickname,
        String dishId,
        String dishName,
        String dishFileId,
        String mealType,
        LocalDate date
) {
}
