package com.example.dish_memo.common;

/**
 * Exception type for expected API failures with documented business codes.
 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    /**
     * Creates an exception mapped to a documented error code.
     *
     * @param errorCode business error code
     * @param message response message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the response error code.
     *
     * @return business error code
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
