package com.example.dish_memo.friend.controller;

import com.example.dish_memo.common.ApiHeaders;
import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.friend.dto.AddFriendResponse;
import com.example.dish_memo.friend.dto.CreateFriendInvitationRequest;
import com.example.dish_memo.friend.dto.CreateFriendInvitationResponse;
import com.example.dish_memo.friend.dto.FriendInviteTokenRequest;
import com.example.dish_memo.friend.dto.FriendPageResponse;
import com.example.dish_memo.friend.dto.ParseFriendInvitationResponse;
import com.example.dish_memo.friend.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API controller for friend invitation and friend relation endpoints.
 */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {
    private final FriendService friendService;

    /**
     * Creates the controller with its friend service dependency.
     *
     * @param friendService friend business service
     */
    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /**
     * Creates a signed friend invitation for the current user.
     *
     * @param userId current OpenID from gateway header
     * @param request optional expiration request
     * @return signed invitation token
     */
    @PostMapping("/invitations")
    public ApiResponse<CreateFriendInvitationResponse> createInvitation(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @RequestBody(required = false) CreateFriendInvitationRequest request
    ) {
        return ApiResponse.ok(friendService.createInvitation(userId, request));
    }

    /**
     * Parses and validates a signed friend invitation token.
     *
     * @param userId current OpenID from gateway header
     * @param request token request
     * @return inviter profile fields embedded in the token
     */
    @PostMapping("/invitations/parse")
    public ApiResponse<ParseFriendInvitationResponse> parseInvitation(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @Valid @RequestBody FriendInviteTokenRequest request
    ) {
        return ApiResponse.ok(friendService.parseInvitation(userId, request.inviteToken()));
    }

    /**
     * Confirms a friend invitation and creates the relation.
     *
     * @param userId current OpenID from gateway header
     * @param request token request
     * @return inviter and accepting friend IDs
     */
    @PostMapping
    public ApiResponse<AddFriendResponse> addFriend(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @Valid @RequestBody FriendInviteTokenRequest request
    ) {
        return ApiResponse.ok(friendService.addFriend(userId, request.inviteToken()));
    }

    /**
     * Lists current user's friends.
     *
     * @param userId current OpenID from gateway header
     * @param pageNo page number
     * @param pageSize page size
     * @param nicknameKeyword optional nickname filter
     * @return paginated friend list
     */
    @GetMapping
    public ApiResponse<FriendPageResponse> listFriends(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "nickname_keyword", required = false) String nicknameKeyword
    ) {
        return ApiResponse.ok(friendService.listFriends(userId, pageNo, pageSize, nicknameKeyword));
    }
}
