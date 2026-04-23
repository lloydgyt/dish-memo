package com.example.dish_memo.common;

/**
 * Standard API response envelope shared by every endpoint.
 *
 * @param code business response code
 * @param message human-readable response message
 * @param data response payload or null
 * @param <T> payload type
 */
public record ApiResponse<T>(int code, String message, T data) {

    /**
     * Builds a successful response with the documented code and message.
     *
     * @param data response payload
     * @param <T> payload type
     * @return standard success envelope
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /**
     * Builds an error response from an application error code.
     *
     * @param errorCode documented business error code
     * @param message response message
     * @return standard error envelope
     */
    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null);
    }
}
