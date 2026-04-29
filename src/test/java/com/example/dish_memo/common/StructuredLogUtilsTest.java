package com.example.dish_memo.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLogUtilsTest {

    @Test
    void requestPayloadContainsMandatoryFields() {
        String payload = StructuredLogUtils.request(
                "req_1",
                "u_1",
                Map.of("page_no", new String[]{"1"}),
                "GET",
                "/api/v1/dishes",
                200,
                12
        );

        assertThat(payload).contains("\"request_id\":\"req_1\"");
        assertThat(payload).contains("\"user_id\":\"u_1\"");
        assertThat(payload).contains("\"request_params\":{\"page_no\":\"1\"}");
        assertThat(payload).contains("\"method\":\"GET\"");
        assertThat(payload).contains("\"path\":\"/api/v1/dishes\"");
        assertThat(payload).contains("\"status\":200");
        assertThat(payload).contains("\"duration_ms\":12");
    }

    @Test
    void exceptionPayloadContainsExceptionFieldsAndRedactsSecrets() {
        String payload = StructuredLogUtils.exception(
                "u_1",
                "handle unexpected exception",
                new IllegalArgumentException("password=abc123 token=full-token-value")
        );

        assertThat(payload).contains("\"userId\":\"u_1\"");
        assertThat(payload).contains("\"description\":\"handle unexpected exception\"");
        assertThat(payload).contains("\"exceptionType\":\"java.lang.IllegalArgumentException\"");
        assertThat(payload).contains("password=[REDACTED]");
        assertThat(payload).contains("token=[REDACTED]");
        assertThat(payload).doesNotContain("abc123");
        assertThat(payload).doesNotContain("full-token-value");
    }

    @Test
    void missingUserIdFallsBackToUnknown() {
        String payload = StructuredLogUtils.request("req_1", " ", Map.of(), "GET", "/api/v1/dishes", 200, 1);

        assertThat(payload).contains("\"user_id\":\"UNKNOWN\"");
    }

    @Test
    void requestParametersRedactSensitiveValuesByName() {
        String payload = StructuredLogUtils.request(
                "req_1",
                "u_1",
                Map.of(
                        "password", new String[]{"plain-password"},
                        "refresh_token", new String[]{"refresh-secret"},
                        "Authorization", new String[]{"Bearer auth-secret"},
                        "keyword", new String[]{"token=inline-secret"}
                ),
                "GET",
                "/api/v1/dishes",
                200,
                1
        );

        assertThat(payload).contains("\"password\":\"[REDACTED]\"");
        assertThat(payload).contains("\"refresh_token\":\"[REDACTED]\"");
        assertThat(payload).contains("\"Authorization\":\"[REDACTED]\"");
        assertThat(payload).contains("token=[REDACTED]");
        assertThat(payload).doesNotContain("plain-password");
        assertThat(payload).doesNotContain("refresh-secret");
        assertThat(payload).doesNotContain("auth-secret");
        assertThat(payload).doesNotContain("inline-secret");
    }
}
