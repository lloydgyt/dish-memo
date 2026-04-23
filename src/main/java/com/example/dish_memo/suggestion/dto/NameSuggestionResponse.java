package com.example.dish_memo.suggestion.dto;

/**
 * Dish name suggestion response with explicit model status.
 *
 * @param suggestedName generated dish name or null
 * @param modelStatus success or failed
 * @param reason failure reason or null
 */
public record NameSuggestionResponse(String suggestedName, String modelStatus, String reason) {
}
