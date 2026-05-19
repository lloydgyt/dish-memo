package com.example.dish_memo.friend.dto;

import java.util.List;

/**
 * Paginated response for friends' dishes recorded today.
 *
 * @param pageNo current page number
 * @param pageSize current page size
 * @param total total matching dishes
 * @param isEmpty whether the result is empty
 * @param list current page entries
 */
public record FriendTodayDishesResponse(
        int pageNo,
        int pageSize,
        long total,
        boolean isEmpty,
        List<FriendTodayDishItemResponse> list
) {
}
