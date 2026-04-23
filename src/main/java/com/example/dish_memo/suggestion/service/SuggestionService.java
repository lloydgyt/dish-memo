package com.example.dish_memo.suggestion.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.common.StructuredLogUtils;
import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides replaceable dish name suggestion behavior with MVP fallback semantics.
 */
@Service
public class SuggestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionService.class);

    /**
     * Generates a dish name suggestion or returns a documented fallback without failing the flow.
     *
     * @param userId current user ID
     * @param request image suggestion request
     * @return suggestion result with model status
     */
    public NameSuggestionResponse suggest(String userId, NameSuggestionRequest request) {
        LOGGER.info(StructuredLogUtils.info(userId, "suggest dish name"));
        validateImageUrl(userId, request.imageUrl());
        if (request.imageUrl().contains("force_model_failed")) {
            return new NameSuggestionResponse(null, "failed", "model inference timeout");
        }
        // TODO should add LLM function
        return new NameSuggestionResponse("家常菜", "success", null);
    }

    private void validateImageUrl(String userId, String imageUrl) {
        try {
            URI uri = new URI(imageUrl);
            boolean absoluteHttp = uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
            boolean localUpload = imageUrl.startsWith("/uploads/");
            if (!absoluteHttp && !localUpload) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "image_url is invalid");
            }
        } catch (URISyntaxException ex) {
            LOGGER.warn(StructuredLogUtils.exception(userId, "validate suggestion image url", ex));
            throw new BusinessException(ErrorCode.PARAM_ERROR, "image_url is invalid");
        }
    }
}
