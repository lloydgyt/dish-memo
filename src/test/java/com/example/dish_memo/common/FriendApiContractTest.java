package com.example.dish_memo.common;

import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.mapper.DishMapper;
import com.example.dish_memo.friend.controller.FriendController;
import com.example.dish_memo.friend.dto.FriendInvitationRecord;
import com.example.dish_memo.friend.dto.FriendListItemResponse;
import com.example.dish_memo.friend.dto.FriendUser;
import com.example.dish_memo.friend.mapper.FriendMapper;
import com.example.dish_memo.friend.service.FriendService;
import com.example.dish_memo.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FriendController.class)
@Import({
        WebConfig.class,
        RequestLoggingFilter.class,
        UserContextInterceptor.class,
        GlobalExceptionHandler.class,
        FriendService.class
})
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "dish-memo.logging.slow-request-threshold-ms=-1",
        "dish-memo.friend.invite-token-secret=test-secret"
})
class FriendApiContractTest {
    private static final String INVITER_UID = "u_1001";
    private static final String FRIEND_UID = "u_2002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendMapper friendMapper;

    @MockBean
    private DishMapper dishMapper;

    @MockBean
    private UserMapper userMapper;

    @Test
    void createInvitationReturnsDocumentedTokenAndExpireAt() throws Exception {
        when(userMapper.findByUid(INVITER_UID)).thenReturn(user(INVITER_UID));

        mockMvc.perform(post("/api/v1/friends/invitations")
                        .header(ApiHeaders.REQUEST_ID, "req_create_invitation")
                        .header(ApiHeaders.WX_OPENID, INVITER_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expire_in_seconds\":86400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.inviteToken").isString())
                .andExpect(jsonPath("$.data.expire_at").isString());
    }

    @Test
    void friendEndpointsRequireLoginState() throws Exception {
        mockMvc.perform(get("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_no_login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4011001));
    }

    @Test
    void parseInvitationReturnsInviterProfileFieldsInSnakeCase() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));

        mockMvc.perform(post("/api/v1/friends/invitations/parse")
                        .header(ApiHeaders.REQUEST_ID, "req_parse_invitation")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(INVITER_UID))
                .andExpect(jsonPath("$.data.avatar_file_id").doesNotExist())
                .andExpect(jsonPath("$.data.avatar_url").doesNotExist());
    }

    @Test
    void parseInvitationRejectsTamperedTokenWithDocumentedError() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(post("/api/v1/friends/invitations/parse")
                        .header(ApiHeaders.REQUEST_ID, "req_tampered_token")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + tampered + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4222001));
    }

    @Test
    void addFriendCreatesRelationAndListReturnsFriendEntry() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));
        when(friendMapper.findInvitation(INVITER_UID)).thenReturn(invitation(INVITER_UID, 86400));
        when(friendMapper.relationExists(INVITER_UID, FRIEND_UID)).thenReturn(false);

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_add_friend")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviter_uid").value(INVITER_UID))
                .andExpect(jsonPath("$.data.friend_uid").value(FRIEND_UID));

        when(friendMapper.countFriends(FRIEND_UID, "1001")).thenReturn(1L);
        when(friendMapper.listFriends(FRIEND_UID, "1001", 20, 0)).thenReturn(List.of(
                new FriendListItemResponse(INVITER_UID, INVITER_UID, null, now())
        ));
        mockMvc.perform(get("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_list_friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("page_no", "1")
                        .param("page_size", "20")
                        .param("nickname_keyword", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].uid").value(INVITER_UID))
                .andExpect(jsonPath("$.data.list[0].avatar_file_id").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].avatar_url").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].created_at").isString());
    }

    @Test
    void listFriendTodayDishesReturnsDocumentedFields() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));
        when(friendMapper.findInvitation(INVITER_UID)).thenReturn(invitation(INVITER_UID, 86400));
        when(friendMapper.relationExists(INVITER_UID, FRIEND_UID)).thenReturn(false);
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_add_friend_today")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        DishRecord record = new DishRecord();
        record.setId("dish_today_1");
        record.setUserId(INVITER_UID);
        record.setName("番茄炒蛋");
        record.setFileId("production/dish/u_1001/img_01HRXYZ.jpg");
        record.setDate(LocalDate.now());
        record.setMealType("lunch");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        when(friendMapper.listFriendUids(FRIEND_UID)).thenReturn(List.of(INVITER_UID));
        when(dishMapper.countTodayDishesByUserIds(any(), eq("lunch"), any())).thenReturn(1L);
        when(dishMapper.listTodayDishesByUserIds(any(), eq("lunch"), any(), eq(20), eq(0)))
                .thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/friends/today-dishes")
                        .header(ApiHeaders.REQUEST_ID, "req_today_dishes")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("meal_type", "lunch")
                        .param("page_no", "1")
                        .param("page_size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.is_empty").value(false))
                .andExpect(jsonPath("$.data.list[0].friend_uid").value(INVITER_UID))
                .andExpect(jsonPath("$.data.list[0].friend_nickname").value(INVITER_UID))
                .andExpect(jsonPath("$.data.list[0].friend_avatar_file_id").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].dish_id").value("dish_today_1"))
                .andExpect(jsonPath("$.data.list[0].dish_file_id").value("production/dish/u_1001/img_01HRXYZ.jpg"))
                .andExpect(jsonPath("$.data.list[0].meal_type").value("lunch"));
        verify(dishMapper).listTodayDishesByUserIds(any(), eq("lunch"), any(), eq(20), eq(0));
    }

    @Test
    void listFriendTodayDishesRejectsUnknownCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/friends/today-dishes")
                        .header(ApiHeaders.REQUEST_ID, "req_missing_current")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("meal_type", "lunch"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4042002));
    }

    @Test
    void addFriendRejectsSelfAddAndDuplicateRelation() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_self_add")
                        .header(ApiHeaders.WX_OPENID, INVITER_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002002));

        when(friendMapper.findInvitation(INVITER_UID)).thenReturn(invitation(INVITER_UID, 86400));
        when(friendMapper.relationExists(INVITER_UID, FRIEND_UID)).thenReturn(false, true);
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_first_add")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_duplicate_add")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4092001));
    }

    @Test
    void addFriendRejectsMissingExpiredAndUnbackedInvitation() throws Exception {
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_missing_token")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001001));

        String unbackedToken = createInvitation(INVITER_UID, 86400);
        when(userMapper.findByUid(FRIEND_UID)).thenReturn(user(FRIEND_UID));
        when(friendMapper.findInvitation(INVITER_UID)).thenReturn(null);
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_unbacked_token")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + unbackedToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4042001));

        String expiredToken = createInvitation(INVITER_UID, 1);
        Thread.sleep(1100);
        when(friendMapper.findInvitation(INVITER_UID)).thenReturn(invitation(INVITER_UID, -1));
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_expired_token")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + expiredToken + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(4102001));
    }

    @Test
    void invalidUidAndPaginationUseDocumentedErrors() throws Exception {
        mockMvc.perform(post("/api/v1/friends/invitations")
                        .header(ApiHeaders.REQUEST_ID, "req_invalid_uid")
                        .header(ApiHeaders.WX_OPENID, "bad uid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002001));

        mockMvc.perform(get("/api/v1/friends")
                        .header(ApiHeaders.REQUEST_ID, "req_bad_page")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("page_no", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001001));
    }

    @Test
    void removedGetInvitationEndpointIsNotRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/friends/invitations/fit_missing")
                        .header(ApiHeaders.REQUEST_ID, "req_removed_invitation")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID))
                .andExpect(status().isNotFound());
    }

    private String createInvitation(String userId, int expireSeconds) throws Exception {
        when(userMapper.findByUid(userId)).thenReturn(user(userId));
        MvcResult result = mockMvc.perform(post("/api/v1/friends/invitations")
                        .header(ApiHeaders.REQUEST_ID, "req_helper_" + userId + "_" + expireSeconds)
                        .header(ApiHeaders.WX_OPENID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expire_in_seconds\":" + expireSeconds + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("inviteToken").asText();
        assertThat(token).startsWith("fit_");
        return token;
    }

    private FriendUser user(String uid) {
        return new FriendUser(uid, uid, null, now(), now());
    }

    private FriendInvitationRecord invitation(String inviterUid, long expireInSeconds) {
        OffsetDateTime createdAt = now();
        return new FriendInvitationRecord(inviterUid, createdAt.plusSeconds(expireInSeconds), createdAt);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
