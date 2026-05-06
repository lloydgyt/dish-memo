package com.example.dish_memo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the gateway-provided user header for all versioned business APIs.
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    /**
     * Rejects requests that do not contain a usable X-WX-OPENID header.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler selected Spring handler
     * @return true when request processing may continue
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String userId = request.getHeader(ApiHeaders.WX_OPENID);
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, ApiHeaders.WX_OPENID + " is required");
        }
        return true;
    }
}
