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
    INTERNAL_ERROR(5001001, HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_UPLOAD_FAILED(5001002, HttpStatus.INTERNAL_SERVER_ERROR);

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
