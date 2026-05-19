package com.example.dish_memo.user.dto;

/**
 * Request body for creating the current user's profile.
 *
 * @param nickname display nickname
 * @param avatarFileId avatar file ID or null
 */
public record CreateUserRequest(String nickname, String avatarFileId) {
}
