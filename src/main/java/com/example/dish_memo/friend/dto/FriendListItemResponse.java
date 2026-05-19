package com.example.dish_memo.friend.dto;

import java.time.OffsetDateTime;

/**
 * One friend entry in the friend list response.
 *
 * @param uid friend user ID
 * @param nickname friend nickname
 * @param avatarFileId friend avatar file ID or null
 * @param createdAt relationship creation time
 */
public record FriendListItemResponse(String uid, String nickname, String avatarFileId, OffsetDateTime createdAt) {
}
