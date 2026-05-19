package com.example.dish_memo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Emits sanitized JSON access logs for versioned API requests from the servlet filter boundary.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String API_PATH_PREFIX = "/api/v1/";

    private final long slowRequestThresholdMs;

    /**
     * Creates the request logging filter with the configured slow request threshold.
     *
     * @param slowRequestThresholdMs request duration threshold that enables phase detail logging
     */
    public RequestLoggingFilter(
            @Value("${dish-memo.logging.slow-request-threshold-ms:500}") long slowRequestThresholdMs
    ) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    /**
     * Initializes request log context at filter entry and writes final logs before filter exit.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain downstream servlet filter chain
     * @throws ServletException when downstream servlet processing fails
     * @throws IOException when downstream IO fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTimeNanos = System.nanoTime();
        String requestId = requestId(request);
        if (!StringUtils.hasText(requestId)) {
            writeMissingRequestIdResponse(response);
            return;
        }
        RequestLogContext.start(requestId, request.getHeader(ApiHeaders.WX_OPENID));
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, requestId, durationMs(startTimeNanos));
            RequestLogContext.clear();
        }
    }

    /**
     * Restricts structured request logs to documented versioned API paths.
     *
     * @param request current HTTP request
     * @return true when this request should bypass access logging
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(API_PATH_PREFIX);
    }

    private void logRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            long durationMs
    ) {
        long dbDurationMs = RequestLogContext.dbDurationMs();
        LOGGER.info(StructuredLogUtils.request(
                requestId,
                request.getHeader(ApiHeaders.WX_OPENID),
                request.getParameterMap(),
                request.getMethod() + " " + request.getRequestURI(),
                response.getStatus(),
                durationMs,
                dbDurationMs
        ));
        if (durationMs > slowRequestThresholdMs) {
            LOGGER.info(StructuredLogUtils.requestPhase(
                    requestId,
                    RequestLogContext.controllerDurationMs(),
                    RequestLogContext.serviceDurationMs(),
                    RequestLogContext.mapperLogs()
            ));
        }
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader(ApiHeaders.REQUEST_ID);
    }

    private void writeMissingRequestIdResponse(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.PARAM_ERROR.status().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":4001001,\"message\":\"X-Request-Id is required\",\"data\":null}");
    }

    private long durationMs(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos) / 1_000_000;
    }
}
