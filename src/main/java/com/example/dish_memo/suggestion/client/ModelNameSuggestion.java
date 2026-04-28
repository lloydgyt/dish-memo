package com.example.dish_memo.suggestion.client;

/**
 * Structured result returned by the multimodal model adapter.
 *
 * @param suggestedName generated dish name
 */
public record ModelNameSuggestion(String suggestedName) {
}
