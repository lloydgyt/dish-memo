package com.example.dish_memo.suggestion.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for dish name suggestion.
 *
 * @param imageUrl temporary object storage image URL
 */
public record NameSuggestionRequest(
        @NotBlank(message = "image_url is required") String imageUrl
) {
}
