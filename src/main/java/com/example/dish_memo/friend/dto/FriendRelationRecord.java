package com.example.dish_memo.friend.dto;

import java.time.OffsetDateTime;

/**
 * Mock normalized friend relation record.
 *
 * @param uidA lexicographically smaller user ID
 * @param uidB lexicographically larger user ID
 * @param createdAt relationship creation time
 */
public record FriendRelationRecord(String uidA, String uidB, OffsetDateTime createdAt) {
}
