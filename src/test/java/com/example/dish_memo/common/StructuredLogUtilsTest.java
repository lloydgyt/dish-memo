package com.example.dish_memo.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLogUtilsTest {

    @Test
    void requestPayloadContainsMandatoryFields() {
        String payload = StructuredLogUtils.request(
                "req_1",
                "u_1",
                Map.of("page_no", new String[]{"1"}),
                "GET /api/v1/dishes",
                200,
                12,
                7
        );

        assertThat(payload).contains("\"request_id\":\"req_1\"");
        assertThat(payload).contains("\"user_id\":\"u_1\"");
        assertThat(payload).contains("\"request_params\":{\"page_no\":\"1\"}");
        assertThat(payload).contains("\"route\":\"GET /api/v1/dishes\"");
        assertThat(payload).contains("\"status\":200");
        assertThat(payload).contains("\"duration_ms\":12");
        assertThat(payload).contains("\"db_duration_ms\":7");
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
        String payload = StructuredLogUtils.request("req_1", " ", Map.of(), "GET /api/v1/dishes", 200, 1, 0);

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
                "GET /api/v1/dishes",
                200,
                1,
                0
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

    @Test
    void phasePayloadContainsDocumentedMapperArray() {
        String payload = StructuredLogUtils.requestPhase(
                "req_1",
                20,
                0,
                List.of(
                        new RequestLogContext.MapperLog(
                                "com.example.dish_memo.dish.mapper.DishMapper.countByFilters",
                                "dish_memo:dish_record",
                                1,
                                "SELECT COUNT(*) FROM dish_record WHERE user_id = ?",
                                4
                        ),
                        new RequestLogContext.MapperLog(
                                "com.example.dish_memo.dish.mapper.DishMapper.listByFilters",
                                "dish_memo:dish_record",
                                2,
                                "SELECT * FROM dish_record WHERE user_id = ?",
                                5
                        )
                )
        );

        assertThat(payload).contains("\"request_id\":\"req_1\"");
        assertThat(payload).contains("\"controller_ms\":20");
        assertThat(payload).contains("\"service_ms\":0");
        assertThat(payload).contains("\"mapper\":[");
        assertThat(payload).contains("\"duration_ms\":4");
        assertThat(payload).contains("\"statement_id\":\"com.example.dish_memo.dish.mapper.DishMapper.countByFilters\"");
        assertThat(payload).contains("\"result_size\":1");
        assertThat(payload).contains("\"sql_fingerprint\":\"SELECT COUNT(*) FROM dish_record WHERE user_id = ?\"");
        assertThat(payload).contains("\"duration_ms\":5");
        assertThat(payload).contains("\"statement_id\":\"com.example.dish_memo.dish.mapper.DishMapper.listByFilters\"");
        assertThat(payload).contains("\"db_table\":\"dish_memo:dish_record\"");
        assertThat(payload).contains("\"result_size\":2");
        assertThat(payload).contains("\"sql_fingerprint\":\"SELECT * FROM dish_record WHERE user_id = ?\"");
    }

    @Test
    void requestContextAccumulatesMapperDurationAndKeepsEveryMapperDetail() {
        RequestLogContext.start("req_1", "u_1");
        try {
            RequestLogContext.recordMapper("mapper.count", "dish_memo:dish_record", 1, "SELECT COUNT(*)", 4_000_000);
            RequestLogContext.recordMapper("mapper.list", "dish_memo:dish_record", 2, "SELECT *", 5_000_000);

            assertThat(RequestLogContext.dbDurationMs()).isEqualTo(9);
            assertThat(RequestLogContext.mapperLogs())
                    .extracting(RequestLogContext.MapperLog::statementId)
                    .containsExactly("mapper.count", "mapper.list");
        } finally {
            RequestLogContext.clear();
        }
    }
}
