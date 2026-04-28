package com.example.dish_memo.suggestion.client;

/**
 * Exception raised when the model client cannot complete or parse a request.
 */
public class NameSuggestionClientException extends RuntimeException {
    private final Reason reason;

    /**
     * Creates a client exception with a stable reason category.
     *
     * @param reason reason category
     * @param message safe diagnostic message
     */
    public NameSuggestionClientException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates a client exception with a stable reason category and cause.
     *
     * @param reason reason category
     * @param message safe diagnostic message
     * @param cause root cause
     */
    public NameSuggestionClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the reason category.
     *
     * @return reason category
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Stable client failure categories for HTTP error mapping.
     */
    public enum Reason {
        NETWORK,
        MODEL_ERROR,
        INVALID_RESPONSE
    }
}
