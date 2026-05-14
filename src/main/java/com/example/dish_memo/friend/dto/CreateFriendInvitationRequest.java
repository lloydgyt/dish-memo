package com.example.dish_memo.friend.dto;

/**
 * Request body for creating a friend invitation token.
 *
 * @param expireInSeconds optional invitation lifetime in seconds
 */
public record CreateFriendInvitationRequest(Integer expireInSeconds) {
}
