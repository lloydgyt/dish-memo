package com.example.dish_memo.user.dto;

/**
 * Current user profile response.
 *
 * @param nickname display nickname
 * @param avatarFileId avatar file ID or null
 */
public record UserProfileResponse(String nickname, String avatarFileId) {
}
