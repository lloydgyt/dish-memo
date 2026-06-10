package com.example.dish_memo.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {
    private static final String SUMMARY_LOGGER_NAME = "com.example.dish_memo.access.summary";
    private static final String PHASE_LOGGER_NAME = "com.example.dish_memo.access.slow";

    @AfterEach
    void clearRequestLogContext() {
        RequestLogContext.clear();
    }

    @Test
    void successfulSummaryLogUsesInfoWhenSampledIn() throws Exception {
        ListAppender<ILoggingEvent> summaryAppender = attachListAppender(SUMMARY_LOGGER_NAME);

        perform(new RequestLoggingFilter(500, () -> true), HttpServletResponse.SC_OK);

        assertThat(summaryAppender.list).hasSize(1);
        assertThat(summaryAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(summaryAppender.list.get(0).getFormattedMessage()).contains("\"status\":200");
    }

    @Test
    void successfulSummaryLogIsSkippedWhenSampledOut() throws Exception {
        ListAppender<ILoggingEvent> summaryAppender = attachListAppender(SUMMARY_LOGGER_NAME);

        perform(new RequestLoggingFilter(500, () -> false), HttpServletResponse.SC_OK);

        assertThat(summaryAppender.list).isEmpty();
    }

    @Test
    void clientErrorSummaryLogUsesWarnWithoutSampling() throws Exception {
        ListAppender<ILoggingEvent> summaryAppender = attachListAppender(SUMMARY_LOGGER_NAME);

        perform(new RequestLoggingFilter(500, () -> false), HttpServletResponse.SC_BAD_REQUEST);

        assertThat(summaryAppender.list).hasSize(1);
        assertThat(summaryAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(summaryAppender.list.get(0).getFormattedMessage()).contains("\"status\":400");
    }

    @Test
    void serverErrorSummaryLogUsesErrorWithoutSampling() throws Exception {
        ListAppender<ILoggingEvent> summaryAppender = attachListAppender(SUMMARY_LOGGER_NAME);

        perform(new RequestLoggingFilter(500, () -> false), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        assertThat(summaryAppender.list).hasSize(1);
        assertThat(summaryAppender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(summaryAppender.list.get(0).getFormattedMessage()).contains("\"status\":500");
    }

    @Test
    void slowSuccessLogsWarnSummaryAndWarnPhaseWithoutSampling() throws Exception {
        ListAppender<ILoggingEvent> summaryAppender = attachListAppender(SUMMARY_LOGGER_NAME);
        ListAppender<ILoggingEvent> phaseAppender = attachListAppender(PHASE_LOGGER_NAME);

        perform(new RequestLoggingFilter(-1, () -> false), HttpServletResponse.SC_OK);

        assertThat(summaryAppender.list).hasSize(1);
        assertThat(summaryAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(summaryAppender.list.get(0).getFormattedMessage()).contains("\"status\":200");
        assertThat(phaseAppender.list).hasSize(1);
        assertThat(phaseAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(phaseAppender.list.get(0).getFormattedMessage()).contains("\"request_id\":\"req_1\"");
    }

    private void perform(RequestLoggingFilter filter, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dishes");
        request.setRequestURI("/api/v1/dishes");
        request.addHeader(ApiHeaders.REQUEST_ID, "req_1");
        request.addHeader(ApiHeaders.WX_OPENID, "u_1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(status);

        filter.doFilter(request, response, chain);
    }

    private ListAppender<ILoggingEvent> attachListAppender(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
