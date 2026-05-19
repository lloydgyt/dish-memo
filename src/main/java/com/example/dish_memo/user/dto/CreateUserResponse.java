package com.example.dish_memo.user.dto;

import java.time.OffsetDateTime;

/**
 * Response returned after creating a user profile.
 *
 * @param nickname display nickname
 * @param avatarFileId avatar file ID or null
 * @param createdAt creation time
 */
public record CreateUserResponse(String nickname, String avatarFileId, OffsetDateTime createdAt) {
}
