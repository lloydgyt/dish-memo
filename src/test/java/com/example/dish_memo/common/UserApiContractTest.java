package com.example.dish_memo.common;

import com.example.dish_memo.friend.dto.FriendUser;
import com.example.dish_memo.user.controller.UserController;
import com.example.dish_memo.user.mapper.UserMapper;
import com.example.dish_memo.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({
        WebConfig.class,
        RequestLoggingFilter.class,
        UserContextInterceptor.class,
        GlobalExceptionHandler.class,
        UserService.class
})
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "dish-memo.logging.slow-request-threshold-ms=-1"
})
class UserApiContractTest {
    private static final String USER_ID = "u_1001";
    private static final String AVATAR_FILE_ID = "production/avatar/u_1001/avatar_01HRXYZ.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @Test
    void createUserReturnsDocumentedProfileFields() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_create_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" 阿青 \",\"avatar_file_id\":\"" + AVATAR_FILE_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("阿青"))
                .andExpect(jsonPath("$.data.avatar_file_id").value(AVATAR_FILE_ID))
                .andExpect(jsonPath("$.data.created_at").isString())
                .andExpect(jsonPath("$.data.uid").doesNotExist());
        verify(userMapper).insert(any(FriendUser.class));
    }

    @Test
    void getCurrentUserReturnsOnlyDisplayProfile() throws Exception {
        when(userMapper.findByUid(USER_ID)).thenReturn(user());

        mockMvc.perform(get("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_get_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("阿青"))
                .andExpect(jsonPath("$.data.avatar_file_id").value(AVATAR_FILE_ID))
                .andExpect(jsonPath("$.data.uid").doesNotExist())
                .andExpect(jsonPath("$.data.created_at").doesNotExist())
                .andExpect(jsonPath("$.data.updated_at").doesNotExist());
    }

    @Test
    void createUserRejectsInvalidProfileAndDuplicateUid() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_bad_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \",\"avatar_file_id\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003001));

        mockMvc.perform(post("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_null_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003001));

        when(userMapper.findByUid(USER_ID)).thenReturn(user());

        mockMvc.perform(post("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_dup_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"阿青\",\"avatar_file_id\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4093001));
    }

    @Test
    void getCurrentUserRejectsMissingUserAndInvalidUid() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_missing_user")
                        .header(ApiHeaders.WX_OPENID, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4043001));

        mockMvc.perform(get("/api/v1/users")
                        .header(ApiHeaders.REQUEST_ID, "req_bad_uid")
                        .header(ApiHeaders.WX_OPENID, "bad uid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002001));
    }

    private FriendUser user() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-18T10:23:11+08:00");
        return new FriendUser(USER_ID, "阿青", AVATAR_FILE_ID, now, now);
    }
}
