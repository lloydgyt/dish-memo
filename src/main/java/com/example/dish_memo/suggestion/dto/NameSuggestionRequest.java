package com.example.dish_memo.suggestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for dish name suggestion.
 *
 * @param imageUrl temporary object storage image URL
 * @param prompt optional user prompt for dish naming preference
 */
public record NameSuggestionRequest(
        @NotBlank(message = "image_url is required") String imageUrl,
        @Size(max = 500, message = "prompt is too long") String prompt
) {
}
