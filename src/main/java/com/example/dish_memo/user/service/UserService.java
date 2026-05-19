package com.example.dish_memo.user.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.friend.dto.FriendUser;
import com.example.dish_memo.user.dto.CreateUserRequest;
import com.example.dish_memo.user.dto.CreateUserResponse;
import com.example.dish_memo.user.dto.UserProfileResponse;
import com.example.dish_memo.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/**
 * Business service for current-user profile creation and lookup.
 */
@Service
public class UserService {
    private static final Pattern UID_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-:.]{1,128}$");
    private static final Pattern AVATAR_FILE_ID_PATTERN = Pattern.compile(
            "^(cloud://.+|(?:development|production)/avatar/[^/\\s]+/[^\\s]+)$"
    );
    private static final ZoneOffset API_ZONE = ZoneOffset.ofHours(8);

    private final UserMapper userMapper;

    /**
     * Creates the user service with its profile repository.
     *
     * @param userMapper user profile mapper
     */
    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * Creates the profile for the current login uid.
     *
     * @param currentUid current OpenID from gateway header
     * @param request profile request body
     * @return created profile fields
     */
    public CreateUserResponse create(String currentUid, CreateUserRequest request) {
        validateUid(currentUid);
        String nickname = normalizeNickname(request == null ? null : request.nickname());
        String avatarFileId = normalizeAvatarFileId(request == null ? null : request.avatarFileId());
        OffsetDateTime now = OffsetDateTime.now(API_ZONE);
        FriendUser user = new FriendUser(currentUid, nickname, avatarFileId, now, now);
        if (userMapper.findByUid(currentUid) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "user already exists");
        }
        userMapper.insert(user);
        return new CreateUserResponse(user.nickname(), user.avatarFileId(), user.createdAt());
    }

    /**
     * Gets the current user's profile fields allowed by the API contract.
     *
     * @param currentUid current OpenID from gateway header
     * @return current user profile
     */
    public UserProfileResponse getCurrent(String currentUid) {
        validateUid(currentUid);
        FriendUser user = userMapper.findByUid(currentUid);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "user not found");
        }
        return new UserProfileResponse(user.nickname(), user.avatarFileId());
    }

    private void validateUid(String uid) {
        if (!StringUtils.hasText(uid) || !UID_PATTERN.matcher(uid.trim()).matches()) {
            throw new BusinessException(ErrorCode.FRIEND_UID_INVALID, "uid is invalid");
        }
    }

    private String normalizeNickname(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (!StringUtils.hasText(trimmed) || trimmed.length() > 50) {
            throw new BusinessException(ErrorCode.USER_PARAM_ERROR, "nickname is invalid");
        }
        return trimmed;
    }

    private String normalizeAvatarFileId(String avatarFileId) {
        if (!StringUtils.hasText(avatarFileId)) {
            return null;
        }
        String trimmed = avatarFileId.trim();
        if (!AVATAR_FILE_ID_PATTERN.matcher(trimmed).matches() || trimmed.contains("..")) {
            throw new BusinessException(ErrorCode.USER_PARAM_ERROR, "avatar_file_id is invalid");
        }
        return trimmed;
    }
}
