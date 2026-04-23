package com.example.dish_memo.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds safe JSON log payloads for user-facing API and service events.
 */
public final class StructuredLogUtils {
    public static final String UNKNOWN_USER_ID = "UNKNOWN";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password\\s*[=:]\\s*)[^\\s,;&}]+"
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)((?:authorization|token|access_token|refresh_token)\\s*[=:]\\s*)(?:Bearer\\s+)?[^\\s,;&}]+"
    );

    private StructuredLogUtils() {
    }

    /**
     * Creates a JSON payload for service entry logs.
     *
     * @param userId current request user ID
     * @param description business operation description
     * @return JSON log payload containing mandatory userId and description fields
     */
    public static String info(String userId, String description) {
        Map<String, String> fields = baseFields(userId, description);
        return toJson(fields);
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

    private static Map<String, String> baseFields(String userId, String description) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("userId", sanitize(hasText(userId) ? userId : UNKNOWN_USER_ID));
        fields.put("description", sanitize(description));
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String toJson(Map<String, String> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            return "{\"userId\":\"" + escape(fields.get("userId"))
                    + "\",\"description\":\"" + escape(fields.get("description")) + "\"}";
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
