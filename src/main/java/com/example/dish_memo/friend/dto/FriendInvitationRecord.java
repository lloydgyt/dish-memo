package com.example.dish_memo.friend.dto;

import java.time.OffsetDateTime;

/**
 * Mock friend invitation record.
 *
 * @param inviterUid inviter user ID
 * @param expireAt expiration time
 * @param createdAt creation time
 */
public record FriendInvitationRecord(String inviterUid, OffsetDateTime expireAt, OffsetDateTime createdAt) {
}
