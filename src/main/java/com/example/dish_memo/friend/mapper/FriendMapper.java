package com.example.dish_memo.friend.mapper;

import com.example.dish_memo.friend.dto.FriendInvitationRecord;
import com.example.dish_memo.friend.dto.FriendListItemResponse;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * MyBatis mapper for friend invitation and relation persistence.
 */
@Mapper
public interface FriendMapper {

    /**
     * Saves the active invitation for an inviter.
     *
     * @param record invitation record to upsert
     * @return affected row count
     */
    @Insert("""
            INSERT INTO friend_invitation (inviter_uid, expire_at, created_at)
            VALUES (#{inviterUid}, #{expireAt}, #{createdAt})
            ON DUPLICATE KEY UPDATE expire_at = VALUES(expire_at), created_at = VALUES(created_at)
            """)
    int saveInvitation(FriendInvitationRecord record);

    /**
     * Finds the active invitation row for an inviter.
     *
     * @param inviterUid inviter user ID
     * @return invitation record or null
     */
    @ConstructorArgs({
            @Arg(column = "inviter_uid", javaType = String.class),
            @Arg(column = "expire_at", javaType = OffsetDateTime.class),
            @Arg(column = "created_at", javaType = OffsetDateTime.class)
    })
    @Select("""
            SELECT inviter_uid, expire_at, created_at
            FROM friend_invitation
            WHERE inviter_uid = #{inviterUid}
            """)
    FriendInvitationRecord findInvitation(@Param("inviterUid") String inviterUid);

    /**
     * Checks whether a normalized friend relation exists.
     *
     * @param uidA lexicographically smaller user ID
     * @param uidB lexicographically larger user ID
     * @return true when relation exists
     */
    @Select("""
            SELECT COUNT(*) > 0
            FROM friend_relation
            WHERE uid_a = #{uidA} AND uid_b = #{uidB}
            """)
    boolean relationExists(@Param("uidA") String uidA, @Param("uidB") String uidB);

    /**
     * Inserts a normalized friend relation.
     *
     * @param uidA lexicographically smaller user ID
     * @param uidB lexicographically larger user ID
     * @param createdAt creation time
     * @return affected row count
     */
    @Insert("""
            INSERT INTO friend_relation (uid_a, uid_b, created_at)
            VALUES (#{uidA}, #{uidB}, #{createdAt})
            """)
    int insertRelation(
            @Param("uidA") String uidA,
            @Param("uidB") String uidB,
            @Param("createdAt") OffsetDateTime createdAt
    );

    /**
     * Counts friends for one user with an optional nickname filter.
     *
     * @param uid current user ID
     * @param keyword optional nickname keyword
     * @return matching friend count
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM friend_relation fr
            JOIN `user` u ON u.uid = CASE WHEN fr.uid_a = #{uid} THEN fr.uid_b ELSE fr.uid_a END
            WHERE (fr.uid_a = #{uid} OR fr.uid_b = #{uid})
            <if test="keyword != null and keyword != ''">AND u.nickname LIKE CONCAT('%', #{keyword}, '%')</if>
            </script>
            """)
    long countFriends(@Param("uid") String uid, @Param("keyword") String keyword);

    /**
     * Lists friends for one user with database pagination.
     *
     * @param uid current user ID
     * @param keyword optional nickname keyword
     * @param limit page size
     * @param offset page offset
     * @return current page friend entries
     */
    @ConstructorArgs({
            @Arg(column = "uid", javaType = String.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar_file_id", javaType = String.class),
            @Arg(column = "created_at", javaType = OffsetDateTime.class)
    })
    @Select("""
            <script>
            SELECT u.uid, u.nickname, u.avatar_file_id, fr.created_at
            FROM friend_relation fr
            JOIN `user` u ON u.uid = CASE WHEN fr.uid_a = #{uid} THEN fr.uid_b ELSE fr.uid_a END
            WHERE (fr.uid_a = #{uid} OR fr.uid_b = #{uid})
            <if test="keyword != null and keyword != ''">AND u.nickname LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY fr.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FriendListItemResponse> listFriends(
            @Param("uid") String uid,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Lists all friend user IDs for the current user.
     *
     * @param uid current user ID
     * @return friend user IDs
     */
    @Select("""
            SELECT CASE WHEN uid_a = #{uid} THEN uid_b ELSE uid_a END
            FROM friend_relation
            WHERE uid_a = #{uid} OR uid_b = #{uid}
            ORDER BY created_at DESC
            """)
    List<String> listFriendUids(@Param("uid") String uid);
}
