package com.example.dish_memo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Registers API interceptors and local upload static resource handling.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UserContextInterceptor userContextInterceptor;
    private final Path uploadBaseDir;

    /**
     * Creates the MVC configuration with the user header interceptor.
     *
     * @param userContextInterceptor interceptor that validates user identity
     * @param uploadBaseDir configured local upload root directory
     */
    public WebConfig(
            UserContextInterceptor userContextInterceptor,
            @Value("${app.upload.base-dir}") String uploadBaseDir
    ) {
        this.userContextInterceptor = userContextInterceptor;
        this.uploadBaseDir = Path.of(uploadBaseDir);
    }

    /**
     * Applies user identity checks to all versioned API endpoints.
     *
     * @param registry Spring interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor).addPathPatterns("/api/v1/**");
    }

    /**
     * Serves locally stored dish images from the dedicated upload subdirectory only.
     *
     * @param registry Spring resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/dish/**")
                .addResourceLocations(dishUploadResourceLocation());
    }

    /**
     * Builds the file URI for the configured dish image upload directory.
     *
     * @return normalized resource location ending with a slash
     */
    private String dishUploadResourceLocation() {
        String location = uploadBaseDir.resolve("dish").toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
