package com.example.dish_memo.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers API interceptors for versioned business endpoints.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final RequestLoggingInterceptor requestLoggingInterceptor;
    private final UserContextInterceptor userContextInterceptor;

    /**
     * Creates the MVC configuration with request logging and user header interceptors.
     *
     * @param requestLoggingInterceptor interceptor that emits controller request logs
     * @param userContextInterceptor interceptor that validates user identity
     */
    public WebConfig(
            RequestLoggingInterceptor requestLoggingInterceptor,
            UserContextInterceptor userContextInterceptor
    ) {
        this.requestLoggingInterceptor = requestLoggingInterceptor;
        this.userContextInterceptor = userContextInterceptor;
    }

    /**
     * Applies request logging before user identity checks on all versioned API endpoints.
     *
     * @param registry Spring interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(userContextInterceptor).addPathPatterns("/api/v1/**");
    }
}
