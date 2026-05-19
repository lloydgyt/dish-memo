package com.example.dish_memo.common;

import org.springframework.http.HttpStatus;

/**
 * API business error codes and their matching HTTP statuses.
 */
public enum ErrorCode {
    PARAM_ERROR(4001001, HttpStatus.BAD_REQUEST),
    AUTH_FAILED(4011001, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(4031001, HttpStatus.FORBIDDEN),
    DISH_NOT_FOUND(4041001, HttpStatus.NOT_FOUND),
    CONFLICT(4091001, HttpStatus.CONFLICT),
    LLM_FAILED(4221001, HttpStatus.UNPROCESSABLE_ENTITY),
    FRIEND_UID_INVALID(4002001, HttpStatus.BAD_REQUEST),
    FRIEND_SELF_ADD(4002002, HttpStatus.BAD_REQUEST),
    FRIEND_INVITATION_NOT_FOUND(4042001, HttpStatus.NOT_FOUND),
    FRIEND_CURRENT_USER_NOT_FOUND(4042002, HttpStatus.NOT_FOUND),
    FRIEND_RELATION_EXISTS(4092001, HttpStatus.CONFLICT),
    FRIEND_INVITATION_CONFLICT(4092002, HttpStatus.CONFLICT),
    FRIEND_INVITATION_EXPIRED(4102001, HttpStatus.GONE),
    FRIEND_INVITE_TOKEN_INVALID(4222001, HttpStatus.UNPROCESSABLE_ENTITY),
    USER_PARAM_ERROR(4003001, HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(4043001, HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(4093001, HttpStatus.CONFLICT),
    INTERNAL_ERROR(5001001, HttpStatus.INTERNAL_SERVER_ERROR),
    OBJECT_STORAGE_ACCESS_FAILED(5001002, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus status;

    ErrorCode(int code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    /**
     * Returns the documented business code.
     *
     * @return integer business code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the documented HTTP status for this business code.
     *
     * @return HTTP status
     */
    public HttpStatus status() {
        return status;
    }
}
