package com.example.dish_memo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLogUtilsTest {

    @Test
    void infoPayloadContainsMandatoryFields() {
        String payload = StructuredLogUtils.info("u_1", "create dish record");

        assertThat(payload).contains("\"userId\":\"u_1\"");
        assertThat(payload).contains("\"description\":\"create dish record\"");
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
        String payload = StructuredLogUtils.info(" ", "list dish records");

        assertThat(payload).contains("\"userId\":\"UNKNOWN\"");
    }
}
