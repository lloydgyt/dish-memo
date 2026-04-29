package com.example.dish_memo.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds safe JSON log payloads for user-facing API request and exception events.
 */
public final class StructuredLogUtils {
    public static final String UNKNOWN_USER_ID = "UNKNOWN";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i).*(password|token|authorization|access[_-]?token|refresh[_-]?token).*"
    );
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password\\s*[=:]\\s*)[^\\s,;&}]+"
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)((?:authorization|token|access_token|refresh_token)\\s*[=:]\\s*)(?:Bearer\\s+)?[^\\s,;&}]+"
    );

    private StructuredLogUtils() {
    }

    /**
     * Creates a JSON payload for warning logs produced from caught exceptions.
     *
     * @param userId current request user ID
     * @param description exception context description
     * @param ex caught exception
     * @return JSON log payload containing mandatory userId, description, exceptionType and exceptionMessage fields
     */
    public static String exception(String userId, String description, Exception ex) {
        Map<String, String> fields = baseFields(userId, description);
        fields.put("exceptionType", ex.getClass().getName());
        fields.put("exceptionMessage", sanitize(ex.getMessage()));
        return toJson(fields);
    }

    /**
     * Creates a JSON payload for controller request completion logs.
     *
     * @param requestId stable request ID from the inbound header or generated UUID
     * @param userId current request user ID
     * @param requestParams sanitized query parameter map
     * @param method HTTP method
     * @param path request path
     * @param status final HTTP status
     * @param durationMs request duration in milliseconds
     * @return JSON log payload containing all request audit fields
     */
    public static String request(
            String requestId,
            String userId,
            Map<String, String[]> requestParams,
            String method,
            String path,
            int status,
            long durationMs
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("request_id", sanitize(requestId));
        fields.put("user_id", sanitize(hasText(userId) ? userId : UNKNOWN_USER_ID));
        fields.put("request_params", sanitizeParameters(requestParams));
        fields.put("method", sanitize(method));
        fields.put("path", sanitize(path));
        fields.put("status", status);
        fields.put("duration_ms", Math.max(durationMs, 0));
        return toJson(fields);
    }

    /**
     * Redacts sensitive parameter values based on parameter names before JSON serialization.
     *
     * @param parameters raw request parameter map
     * @return sanitized map safe for logging
     */
    public static Map<String, Object> sanitizeParameters(Map<String, String[]> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        parameters.forEach((name, values) -> sanitized.put(sanitize(name), sanitizeParameterValue(name, values)));
        return sanitized;
    }

    /**
     * Redacts common secret-bearing values before they can be written to logs.
     *
     * @param value raw field value
     * @return sanitized field value
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = PASSWORD_PATTERN.matcher(value).replaceAll("$1[REDACTED]");
        return TOKEN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
    }

    private static Object sanitizeParameterValue(String name, String[] values) {
        if (isSensitiveKey(name)) {
            return REDACTED;
        }
        if (values == null) {
            return "";
        }
        if (values.length == 1) {
            return sanitize(values[0]);
        }
        return Arrays.stream(values).map(StructuredLogUtils::sanitize).toList();
    }

    private static boolean isSensitiveKey(String name) {
        return name != null && SENSITIVE_KEY_PATTERN.matcher(name).matches();
    }

    private static Map<String, String> baseFields(String userId, String description) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("userId", sanitize(hasText(userId) ? userId : UNKNOWN_USER_ID));
        fields.put("description", sanitize(description));
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String toJson(Map<String, ?> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            return "{\"serialization_error\":\"" + escape(ex.getMessage()) + "\"}";
        }
    }

    private static String escape(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
