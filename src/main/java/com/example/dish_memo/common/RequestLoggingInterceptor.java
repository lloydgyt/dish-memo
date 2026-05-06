package com.example.dish_memo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Emits one sanitized JSON access log for every versioned controller request.
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".startTime";
    private static final String REQUEST_ID_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Captures request metadata before controller execution and downstream validation.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler selected Spring handler
     * @return true so request processing continues
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId(request));
        return true;
    }

    /**
     * Writes the final request log after Spring has resolved normal or exceptional responses.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler selected Spring handler
     * @param ex exception raised during handler execution, if any
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        long durationMs = durationMs(request.getAttribute(START_TIME_ATTRIBUTE));
        LOGGER.info(StructuredLogUtils.request(
                String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE)),
                request.getHeader(ApiHeaders.WX_OPENID),
                request.getParameterMap(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs
        ));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }

    private long durationMs(Object startTime) {
        if (startTime instanceof Long startTimeNanos) {
            return (System.nanoTime() - startTimeNanos) / 1_000_000;
        }
        return 0;
    }
}
