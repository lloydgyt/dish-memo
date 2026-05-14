package com.example.dish_memo.friend.repository;

import com.example.dish_memo.friend.dto.FriendInvitationRecord;
import com.example.dish_memo.friend.dto.FriendRelationRecord;
import com.example.dish_memo.friend.dto.FriendUser;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory friend repository used to keep the new friend APIs independent from physical MySQL or Redis connections.
 */
@Repository
public class MockFriendRepository {
    private final Map<String, FriendUser> users = new ConcurrentHashMap<>();
    private final Map<String, FriendInvitationRecord> invitations = new ConcurrentHashMap<>();
    private final Map<String, FriendRelationRecord> relations = new ConcurrentHashMap<>();

    /**
     * Ensures a user profile exists for the given uid.
     *
     * @param uid user ID
     * @param now current time
     * @return existing or newly created mock user
     */
    public FriendUser ensureUser(String uid, OffsetDateTime now) {
        return users.computeIfAbsent(uid, key -> new FriendUser(key, key, null, now, now));
    }

    /**
     * Saves or replaces the active invitation for one inviter.
     *
     * @param record invitation record
     */
    public void saveInvitation(FriendInvitationRecord record) {
        invitations.put(record.inviterUid(), record);
    }

    /**
     * Finds an invitation by inviter uid.
     *
     * @param inviterUid inviter user ID
     * @return invitation if present
     */
    public Optional<FriendInvitationRecord> findInvitation(String inviterUid) {
        return Optional.ofNullable(invitations.get(inviterUid));
    }

    /**
     * Checks whether two users already have a normalized relation.
     *
     * @param uidOne first user ID
     * @param uidTwo second user ID
     * @return true if the relation exists
     */
    public boolean relationExists(String uidOne, String uidTwo) {
        return relations.containsKey(relationKey(uidOne, uidTwo));
    }

    /**
     * Creates a normalized friend relation when absent.
     *
     * @param uidOne first user ID
     * @param uidTwo second user ID
     * @param now creation time
     * @return created relation or existing relation
     */
    public FriendRelationRecord saveRelation(String uidOne, String uidTwo, OffsetDateTime now) {
        String key = relationKey(uidOne, uidTwo);
        String[] parts = key.split("\\|", 2);
        return relations.computeIfAbsent(key, ignored -> new FriendRelationRecord(parts[0], parts[1], now));
    }

    /**
     * Lists friend relations for one user sorted by newest relation first.
     *
     * @param uid current user ID
     * @return matching relation records
     */
    public List<FriendRelationRecord> listRelations(String uid) {
        return relations.values().stream()
                .filter(relation -> relation.uidA().equals(uid) || relation.uidB().equals(uid))
                .sorted(Comparator.comparing(FriendRelationRecord::createdAt).reversed())
                .toList();
    }

    /**
     * Finds a user profile if it exists.
     *
     * @param uid user ID
     * @return user if present
     */
    public Optional<FriendUser> findUser(String uid) {
        return Optional.ofNullable(users.get(uid));
    }

    /**
     * Clears in-memory state for isolated tests.
     */
    public void clear() {
        users.clear();
        invitations.clear();
        relations.clear();
    }

    private String relationKey(String uidOne, String uidTwo) {
        List<String> normalized = new ArrayList<>(List.of(uidOne, uidTwo));
        normalized.sort(String::compareTo);
        return StringUtils.collectionToDelimitedString(normalized, "|");
    }
}
