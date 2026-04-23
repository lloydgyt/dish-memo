package com.example.dish_memo.suggestion.controller;

import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import com.example.dish_memo.suggestion.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API controller for dish name suggestion.
 */
@RestController
@RequestMapping("/api/v1/dishes")
public class SuggestionController {
    private final SuggestionService suggestionService;

    /**
     * Creates the controller with its suggestion dependency.
     *
     * @param suggestionService dish name suggestion service
     */
    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * Suggests a dish name from an uploaded image URL.
     *
     * @param userId current user ID from gateway header
     * @param request image URL request
     * @return name suggestion result
     */
    @PostMapping("/name-suggestions")
    public ApiResponse<NameSuggestionResponse> suggest(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody NameSuggestionRequest request
    ) {
        return ApiResponse.ok(suggestionService.suggest(userId, request));
    }
}
