package com.example.dish_memo.user.controller;

import com.example.dish_memo.common.ApiHeaders;
import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.user.dto.CreateUserRequest;
import com.example.dish_memo.user.dto.CreateUserResponse;
import com.example.dish_memo.user.dto.UserProfileResponse;
import com.example.dish_memo.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API controller for current-user profile endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    /**
     * Creates the controller with its user service dependency.
     *
     * @param userService user profile business service
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Creates the current user's profile from the login uid.
     *
     * @param userId current OpenID from gateway header
     * @param request profile request body
     * @return created user profile
     */
    @PostMapping
    public ApiResponse<CreateUserResponse> createUser(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @RequestBody(required = false) CreateUserRequest request
    ) {
        return ApiResponse.ok(userService.create(userId, request));
    }

    /**
     * Gets the current user's public profile fields.
     *
     * @param userId current OpenID from gateway header
     * @return current user profile
     */
    @GetMapping
    public ApiResponse<UserProfileResponse> getCurrentUser(@RequestHeader(ApiHeaders.WX_OPENID) String userId) {
        return ApiResponse.ok(userService.getCurrent(userId));
    }
}
