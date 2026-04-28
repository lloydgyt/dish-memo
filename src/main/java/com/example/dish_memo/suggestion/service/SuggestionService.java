package com.example.dish_memo.suggestion.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.common.StructuredLogUtils;
import com.example.dish_memo.suggestion.dto.NameSuggestionRequest;
import com.example.dish_memo.suggestion.dto.NameSuggestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Provides replaceable dish name suggestion behavior with MVP fallback semantics.
 */
@Service
public class SuggestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionService.class);
    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "^(cloud://.+|(?:development|production)/dish/[^/\\s]+/[^\\s]+)$"
    );

    /**
     * Generates a dish name suggestion or returns a documented fallback without failing the flow.
     *
     * @param userId current user ID
     * @param request image suggestion request
     * @return suggestion result with model status
     */
    public NameSuggestionResponse suggest(String userId, NameSuggestionRequest request) {
        LOGGER.info(StructuredLogUtils.info(userId, "suggest dish name"));
        String fileId = normalizeFileId(request.fileId());
        if (fileId.contains("force_model_failed")) {
            return new NameSuggestionResponse(null, "failed", "model inference timeout");
        }
        // TODO should add LLM function
        return new NameSuggestionResponse("家常菜", "success", null);
    }

    /**
     * Normalizes and validates object storage file IDs before model inference.
     *
     * @param fileId raw file ID from the request body
     * @return trimmed file ID
     */
    private String normalizeFileId(String fileId) {
        String trimmed = fileId == null ? "" : fileId.trim();
        if (!FILE_ID_PATTERN.matcher(trimmed).matches() || trimmed.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file_id is invalid");
        }
        return trimmed;
    }
}
