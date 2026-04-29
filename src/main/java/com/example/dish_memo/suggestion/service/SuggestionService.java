package com.example.dish_memo.suggestion.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.common.StructuredLogUtils;
import com.example.dish_memo.suggestion.client.ModelNameSuggestion;
import com.example.dish_memo.suggestion.client.NameSuggestionClient;
import com.example.dish_memo.suggestion.client.NameSuggestionClientException;
import com.example.dish_memo.suggestion.config.SuggestionProperties;
import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Provides dish name suggestions from validated image URL input.
 */
@Service
public class SuggestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionService.class);
    private final NameSuggestionClient nameSuggestionClient;
    private final SuggestionProperties properties;

    /**
     * Creates the suggestion service with model client and validation settings.
     *
     * @param nameSuggestionClient multimodal model client
     * @param properties suggestion configuration
     */
    public SuggestionService(NameSuggestionClient nameSuggestionClient, SuggestionProperties properties) {
        this.nameSuggestionClient = nameSuggestionClient;
        this.properties = properties;
    }

    /**
     * Generates a dish name suggestion from an image URL.
     *
     * @param userId current user ID
     * @param request image suggestion request
     * @return suggestion result with model status
     */
    public NameSuggestionResponse suggest(String userId, NameSuggestionRequest request) {
        LOGGER.info(StructuredLogUtils.info(userId, "suggest dish name"));
        String imageUrl = normalizeImageUrl(request.imageUrl());
        try {
            ModelNameSuggestion result = nameSuggestionClient.suggest(imageUrl);
            return new NameSuggestionResponse(result.suggestedName(), "success", null);
        } catch (NameSuggestionClientException ex) {
            if (ex.reason() == NameSuggestionClientException.Reason.MODEL_ERROR) {
                return new NameSuggestionResponse(null, "failed", ex.getMessage());
            }
            throw toBusinessException(ex);
        }
    }

    /**
     * Normalizes and validates temporary object storage URLs before model inference.
     *
     * @param imageUrl raw image URL from the request body
     * @return trimmed image URL
     */
    private String normalizeImageUrl(String imageUrl) {
        String trimmed = imageUrl == null ? "" : imageUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "image_url is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "image_url is invalid");
        }
        if (!isAllowedHost(uri.getHost())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "image_url host is not allowed");
        }
        return trimmed;
    }

    private boolean isAllowedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        List<String> allowedHosts = properties.getImageUrlAllowedHosts();
        return allowedHosts.stream()
                .filter(StringUtils::hasText)
                .map(allowedHost -> allowedHost.trim().toLowerCase(Locale.ROOT))
                .anyMatch(allowedHost -> normalizedHost.equals(allowedHost)
                        || normalizedHost.endsWith("." + allowedHost));
    }

    private BusinessException toBusinessException(NameSuggestionClientException ex) {
        if (ex.reason() == NameSuggestionClientException.Reason.INVALID_RESPONSE) {
            return new BusinessException(ErrorCode.LLM_FAILED, "model response is invalid");
        }
        if (ex.reason() == NameSuggestionClientException.Reason.NETWORK) {
            return new BusinessException(ErrorCode.OBJECT_STORAGE_ACCESS_FAILED, "image_url is not accessible");
        }
        return new BusinessException(ErrorCode.LLM_FAILED, "model inference failed");
    }
}
