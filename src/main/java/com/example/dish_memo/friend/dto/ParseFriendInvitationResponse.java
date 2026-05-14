package com.example.dish_memo.friend.dto;

/**
 * Response returned after parsing a valid friend invitation token.
 *
 * @param nickname inviter nickname
 * @param avatarUrl inviter avatar URL or null
 */
public record ParseFriendInvitationResponse(String nickname, String avatarUrl) {
}
