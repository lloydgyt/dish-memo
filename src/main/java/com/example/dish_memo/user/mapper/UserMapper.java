package com.example.dish_memo.user.mapper;

import com.example.dish_memo.friend.dto.FriendUser;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

/**
 * MyBatis mapper for user profile persistence.
 */
@Mapper
public interface UserMapper {

    /**
     * Inserts a new user profile.
     *
     * @param user user profile to persist
     * @return affected row count
     */
    @Insert("""
            INSERT INTO `user` (uid, nickname, avatar_file_id, created_at, updated_at)
            VALUES (#{uid}, #{nickname}, #{avatarFileId}, #{createdAt}, #{updatedAt})
            """)
    int insert(FriendUser user);

    /**
     * Finds a user profile by uid.
     *
     * @param uid user ID
     * @return matching user profile or null
     */
    @ConstructorArgs({
            @Arg(column = "uid", javaType = String.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar_file_id", javaType = String.class),
            @Arg(column = "created_at", javaType = OffsetDateTime.class),
            @Arg(column = "updated_at", javaType = OffsetDateTime.class)
    })
    @Select("SELECT uid, nickname, avatar_file_id, created_at, updated_at FROM `user` WHERE uid = #{uid}")
    FriendUser findByUid(@Param("uid") String uid);
}
