package com.example.dish_memo.common;

import com.example.dish_memo.friend.controller.FriendController;
import com.example.dish_memo.friend.repository.MockFriendRepository;
import com.example.dish_memo.friend.service.FriendService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
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
        FriendService.class,
        MockFriendRepository.class
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

    @Autowired
    private MockFriendRepository repository;

    @BeforeEach
    void clearRepository() {
        repository.clear();
    }

    @Test
    void createInvitationReturnsDocumentedTokenAndExpireAt() throws Exception {
        mockMvc.perform(post("/api/v1/friends/invitations")
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
        mockMvc.perform(get("/api/v1/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4011001));
    }

    @Test
    void parseInvitationReturnsInviterProfileFieldsInSnakeCase() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);

        mockMvc.perform(post("/api/v1/friends/invitations/parse")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(INVITER_UID))
                .andExpect(jsonPath("$.data.avatar_url").doesNotExist());
    }

    @Test
    void parseInvitationRejectsTamperedTokenWithDocumentedError() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(post("/api/v1/friends/invitations/parse")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + tampered + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4222001));
    }

    @Test
    void addFriendCreatesRelationAndListReturnsFriendEntry() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviter_uid").value(INVITER_UID))
                .andExpect(jsonPath("$.data.friend_uid").value(FRIEND_UID));

        mockMvc.perform(get("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("page_no", "1")
                        .param("page_size", "20")
                        .param("nickname_keyword", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].uid").value(INVITER_UID))
                .andExpect(jsonPath("$.data.list[0].avatar_url").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].created_at").isString());
    }

    @Test
    void addFriendRejectsSelfAddAndDuplicateRelation() throws Exception {
        String token = createInvitation(INVITER_UID, 86400);

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, INVITER_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002002));

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4092001));
    }

    @Test
    void addFriendRejectsMissingExpiredAndUnbackedInvitation() throws Exception {
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001001));

        String unbackedToken = createInvitation(INVITER_UID, 86400);
        repository.clear();
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + unbackedToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4042001));

        String expiredToken = createInvitation(INVITER_UID, 1);
        Thread.sleep(1100);
        mockMvc.perform(post("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + expiredToken + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(4102001));
    }

    @Test
    void invalidUidAndPaginationUseDocumentedErrors() throws Exception {
        mockMvc.perform(post("/api/v1/friends/invitations")
                        .header(ApiHeaders.WX_OPENID, "bad uid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002001));

        mockMvc.perform(get("/api/v1/friends")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID)
                        .param("page_no", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001001));
    }

    @Test
    void removedGetInvitationEndpointIsNotRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/friends/invitations/fit_missing")
                        .header(ApiHeaders.WX_OPENID, FRIEND_UID))
                .andExpect(status().isNotFound());
    }

    private String createInvitation(String userId, int expireSeconds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/friends/invitations")
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
}
