package com.example.dish_memo.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers API interceptors for versioned business endpoints.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UserContextInterceptor userContextInterceptor;

    /**
     * Creates the MVC configuration with user header interceptors.
     *
     * @param userContextInterceptor interceptor that validates user identity
     */
    public WebConfig(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }

    /**
     * Applies user identity checks on all versioned API endpoints.
     *
     * @param registry Spring interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor).addPathPatterns("/api/v1/**");
    }
}
