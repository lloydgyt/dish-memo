package com.example.dish_memo.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerLoggingTest {

    @Test
    void businessExceptionHandlerLogsStructuredWarn(CapturedOutput output) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u_1");

        handler.handleBusinessException(
                new BusinessException(ErrorCode.PARAM_ERROR, "parameter is invalid"),
                request
        );

        assertThat(output).contains("WARN");
        assertThat(output).contains("\"userId\":\"u_1\"");
        assertThat(output).contains("\"description\":\"handle business exception\"");
        assertThat(output).contains("\"exceptionType\":\"com.example.dish_memo.common.BusinessException\"");
        assertThat(output).contains("\"exceptionMessage\":\"parameter is invalid\"");
    }

    @Test
    void exceptionHandlerUsesUnknownUserWhenHeaderIsMissing(CapturedOutput output) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        handler.handleException(new RuntimeException("token=secret-token"), new MockHttpServletRequest());

        assertThat(output).contains("\"userId\":\"UNKNOWN\"");
        assertThat(output).contains("\"description\":\"handle unexpected exception\"");
        assertThat(output).contains("token=[REDACTED]");
        assertThat(output).doesNotContain("secret-token");
    }
}
