package com.example.dish_memo.friend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Response returned after creating a signed friend invitation token.
 *
 * @param inviteToken signed invitation token
 * @param expireAt token expiration time
 */
public record CreateFriendInvitationResponse(
        @JsonProperty("inviteToken") String inviteToken,
        OffsetDateTime expireAt
) {
}
