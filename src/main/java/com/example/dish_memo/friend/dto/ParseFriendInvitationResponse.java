package com.example.dish_memo.friend.dto;

/**
 * Response returned after parsing a valid friend invitation token.
 *
 * @param nickname inviter nickname
 * @param avatarFileId inviter avatar file ID or null
 */
public record ParseFriendInvitationResponse(String nickname, String avatarFileId) {
}
