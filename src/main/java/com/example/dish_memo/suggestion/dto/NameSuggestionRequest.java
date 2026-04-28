package com.example.dish_memo.suggestion.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for dish name suggestion.
 *
 * @param fileId object storage file ID
 */
public record NameSuggestionRequest(@NotBlank(message = "file_id is required") String fileId) {
}
