package com.example.dish_memo.friend.dto;

/**
 * Response returned after confirming a friend invitation.
 *
 * @param inviterUid inviter user ID
 * @param friendUid accepting user ID
 */
public record AddFriendResponse(String inviterUid, String friendUid) {
}
