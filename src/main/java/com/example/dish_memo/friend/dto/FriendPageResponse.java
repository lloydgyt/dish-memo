package com.example.dish_memo.friend.dto;

import java.util.List;

/**
 * Paginated friend list response.
 *
 * @param pageNo current page number
 * @param pageSize current page size
 * @param total total matching friends
 * @param list current page entries
 */
public record FriendPageResponse(int pageNo, int pageSize, long total, List<FriendListItemResponse> list) {
}
