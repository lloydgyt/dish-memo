package com.example.dish_memo.friend.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.MealType;
import com.example.dish_memo.dish.mapper.DishMapper;
import com.example.dish_memo.friend.dto.AddFriendResponse;
import com.example.dish_memo.friend.dto.CreateFriendInvitationRequest;
import com.example.dish_memo.friend.dto.CreateFriendInvitationResponse;
import com.example.dish_memo.friend.dto.FriendInvitationRecord;
import com.example.dish_memo.friend.dto.FriendListItemResponse;
import com.example.dish_memo.friend.dto.FriendPageResponse;
import com.example.dish_memo.friend.dto.FriendTodayDishItemResponse;
import com.example.dish_memo.friend.dto.FriendTodayDishesResponse;
import com.example.dish_memo.friend.dto.FriendUser;
import com.example.dish_memo.friend.dto.ParseFriendInvitationResponse;
import com.example.dish_memo.friend.mapper.FriendMapper;
import com.example.dish_memo.user.mapper.UserMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Business service for friend invitations, signed token validation, and friend relations.
 */
@Service
public class FriendService {
    private static final Pattern UID_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-:.]{1,128}$");
    private static final String TOKEN_PREFIX = "fit_";
    private static final long DEFAULT_EXPIRE_SECONDS = 86_400L;
    private static final long MAX_EXPIRE_SECONDS = 604_800L;
    private static final ZoneOffset API_ZONE = ZoneOffset.ofHours(8);

    private final FriendMapper friendMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final DishMapper dishMapper;
    private final String tokenSecret;

    /**
     * Creates the friend service with database persistence and token signing dependencies.
     *
     * @param friendMapper friend persistence mapper
     * @param userMapper user profile mapper
     * @param objectMapper JSON mapper used for token payloads
     * @param dishMapper dish persistence mapper
     * @param tokenSecret HMAC signing secret
     */
    public FriendService(
            FriendMapper friendMapper,
            UserMapper userMapper,
            ObjectMapper objectMapper,
            DishMapper dishMapper,
            @Value("${dish-memo.friend.invite-token-secret:dish-memo-local-friend-secret}") String tokenSecret
    ) {
        this.friendMapper = friendMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.dishMapper = dishMapper;
        this.tokenSecret = tokenSecret;
    }

    /**
     * Creates a signed invitation token and stores a matching active invitation record.
     *
     * @param currentUid current user ID
     * @param request optional expiration request
     * @return invitation token and expiration
     */
    public CreateFriendInvitationResponse createInvitation(String currentUid, CreateFriendInvitationRequest request) {
        validateUid(currentUid);
        long expireSeconds = resolveExpireSeconds(request);
        OffsetDateTime now = now();
        OffsetDateTime expireAt = now.plusSeconds(expireSeconds);
        FriendUser user = requireCurrentUser(currentUid);
        friendMapper.saveInvitation(new FriendInvitationRecord(currentUid, expireAt, now));

        String token = sign(new TokenPayload(user.uid(), user.nickname(), user.avatarFileId(), expireAt.toEpochSecond()));
        return new CreateFriendInvitationResponse(token, expireAt);
    }

    /**
     * Parses a signed invitation token without creating a friend relation.
     *
     * @param currentUid current user ID
     * @param inviteToken signed invite token
     * @return inviter profile fields embedded in the token
     */
    public ParseFriendInvitationResponse parseInvitation(String currentUid, String inviteToken) {
        validateUid(currentUid);
        requireCurrentUser(currentUid);
        TokenPayload payload = parseAndValidateToken(inviteToken);
        return new ParseFriendInvitationResponse(payload.nickname(), payload.avatarFileId());
    }

    /**
     * Confirms an invitation and creates a normalized friend relation in database storage.
     *
     * @param currentUid accepting user ID
     * @param inviteToken signed invite token
     * @return inviter and accepter IDs
     */
    @Transactional
    public AddFriendResponse addFriend(String currentUid, String inviteToken) {
        validateUid(currentUid);
        requireCurrentUser(currentUid);
        TokenPayload payload = parseAndValidateToken(inviteToken);
        String inviterUid = payload.uid();
        if (currentUid.equals(inviterUid)) {
            throw new BusinessException(ErrorCode.FRIEND_SELF_ADD, "can not add yourself as friend");
        }
        FriendInvitationRecord invitation = friendMapper.findInvitation(inviterUid);
        if (invitation == null) {
            throw new BusinessException(ErrorCode.FRIEND_INVITATION_NOT_FOUND, "friend invitation not found");
        }
        if (invitation.expireAt().isBefore(now())) {
            throw new BusinessException(ErrorCode.FRIEND_INVITATION_EXPIRED, "inviteToken is expired");
        }
        String[] normalized = normalizePair(inviterUid, currentUid);
        if (friendMapper.relationExists(normalized[0], normalized[1])) {
            throw new BusinessException(ErrorCode.FRIEND_RELATION_EXISTS, "friend relation already exists");
        }

        friendMapper.insertRelation(normalized[0], normalized[1], now());
        return new AddFriendResponse(inviterUid, currentUid);
    }

    /**
     * Lists current user's friends with pagination and optional nickname filtering.
     *
     * @param currentUid current user ID
     * @param pageNo requested page number
     * @param pageSize requested page size
     * @param nicknameKeyword optional nickname keyword
     * @return paginated friend list
     */
    public FriendPageResponse listFriends(String currentUid, int pageNo, int pageSize, String nicknameKeyword) {
        validateUid(currentUid);
        validatePage(pageNo, pageSize);
        String keyword = StringUtils.hasText(nicknameKeyword) ? nicknameKeyword.trim() : null;
        if (keyword != null && keyword.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "nickname_keyword length must be <= 50");
        }

        requireCurrentUser(currentUid);
        int offset = (pageNo - 1) * pageSize;
        long total = friendMapper.countFriends(currentUid, keyword);
        List<FriendListItemResponse> list = friendMapper.listFriends(currentUid, keyword, pageSize, offset);
        return new FriendPageResponse(pageNo, pageSize, total, list);
    }

    /**
     * Lists dishes recorded today by the current user's friends.
     *
     * @param currentUid current user ID
     * @param mealType requested meal type
     * @param pageNo requested page number
     * @param pageSize requested page size
     * @return paginated friend today dishes
     */
    public FriendTodayDishesResponse listTodayDishes(String currentUid, String mealType, int pageNo, int pageSize) {
        validateUid(currentUid);
        requireCurrentUser(currentUid);
        validatePage(pageNo, pageSize);
        MealType parsedMealType = MealType.from(mealType);

        List<String> friendUids = friendMapper.listFriendUids(currentUid);
        if (friendUids.isEmpty()) {
            return new FriendTodayDishesResponse(pageNo, pageSize, 0, true, List.of());
        }

        LocalDate today = LocalDate.now(API_ZONE);
        int offset = (pageNo - 1) * pageSize;
        long total = dishMapper.countTodayDishesByUserIds(friendUids, parsedMealType.name(), today);
        Map<String, FriendUser> usersByUid = friendUids.stream()
                .map(userMapper::findByUid)
                .filter(user -> user != null)
                .collect(java.util.stream.Collectors.toMap(FriendUser::uid, user -> user, (left, right) -> left));
        List<FriendTodayDishItemResponse> list = dishMapper.listTodayDishesByUserIds(
                        friendUids, parsedMealType.name(), today, pageSize, offset
                )
                .stream()
                .map(record -> toTodayDishItem(record, usersByUid))
                .toList();
        return new FriendTodayDishesResponse(pageNo, pageSize, total, list.isEmpty(), list);
    }

    private FriendTodayDishItemResponse toTodayDishItem(DishRecord record, Map<String, FriendUser> usersByUid) {
        FriendUser friend = usersByUid.get(record.getUserId());
        return new FriendTodayDishItemResponse(
                record.getUserId(),
                friend == null ? null : friend.avatarFileId(),
                friend == null ? record.getUserId() : friend.nickname(),
                record.getId(),
                record.getName(),
                record.getFileId(),
                record.getMealType(),
                record.getDate()
        );
    }

    private long resolveExpireSeconds(CreateFriendInvitationRequest request) {
        Integer requested = request == null ? null : request.expireInSeconds();
        long seconds = requested == null ? DEFAULT_EXPIRE_SECONDS : requested;
        if (seconds <= 0 || seconds > MAX_EXPIRE_SECONDS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "expire_in_seconds must be between 1 and 604800");
        }
        return seconds;
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_no must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_size must be between 1 and 100");
        }
    }

    private void validateUid(String uid) {
        if (!StringUtils.hasText(uid) || !UID_PATTERN.matcher(uid.trim()).matches()) {
            throw new BusinessException(ErrorCode.FRIEND_UID_INVALID, "uid is invalid");
        }
    }

    private FriendUser requireCurrentUser(String uid) {
        FriendUser user = userMapper.findByUid(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.FRIEND_CURRENT_USER_NOT_FOUND, "current user not found");
        }
        return user;
    }

    private String[] normalizePair(String uidOne, String uidTwo) {
        return uidOne.compareTo(uidTwo) <= 0
                ? new String[]{uidOne, uidTwo}
                : new String[]{uidTwo, uidOne};
    }

    private TokenPayload parseAndValidateToken(String inviteToken) {
        if (!StringUtils.hasText(inviteToken) || !inviteToken.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "inviteToken is invalid");
        }
        String tokenBody = inviteToken.substring(TOKEN_PREFIX.length());
        String[] parts = tokenBody.split("\\.", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "inviteToken is invalid");
        }
        if (!MessageDigest.isEqual(signPart(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FRIEND_INVITE_TOKEN_INVALID, "inviteToken signature is invalid");
        }
        TokenPayload payload;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            payload = objectMapper.readValue(payloadBytes, TokenPayload.class);
        } catch (IllegalArgumentException | IOException ex) {
            throw new BusinessException(ErrorCode.FRIEND_INVITE_TOKEN_INVALID, "inviteToken content is invalid");
        }
        validateUid(payload.uid());
        if (payload.exp() <= now().toEpochSecond()) {
            throw new BusinessException(ErrorCode.FRIEND_INVITATION_EXPIRED, "inviteToken is expired");
        }
        return payload;
    }

    private String sign(TokenPayload payload) {
        try {
            String payloadPart = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            return TOKEN_PREFIX + payloadPart + "." + signPart(payloadPart);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to generate inviteToken");
        }
    }

    private String signPart(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to sign inviteToken");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(API_ZONE);
    }

    private record TokenPayload(
            String uid,
            String nickname,
            @JsonProperty("avatar_file_id") String avatarFileId,
            long exp
    ) {
    }
}
