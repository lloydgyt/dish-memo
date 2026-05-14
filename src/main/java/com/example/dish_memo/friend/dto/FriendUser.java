package com.example.dish_memo.friend.dto;

import java.time.OffsetDateTime;

/**
 * Mock user record used by the friend module while no physical user table is required at runtime.
 *
 * @param uid user ID
 * @param nickname display nickname
 * @param avatarUrl avatar URL or null
 * @param createdAt creation time
 * @param updatedAt update time
 */
public record FriendUser(String uid, String nickname, String avatarUrl, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
