package com.example.dish_memo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Validates public dish image URLs before Spring's static resource handler reads the filesystem.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DishUploadStaticResourceFilter extends OncePerRequestFilter {
    private static final String PUBLIC_PATH_PREFIX = "/uploads/dish/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /**
     * Skips validation for requests outside the dish image public path.
     *
     * @param request current HTTP request
     * @return true when this filter should not run
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requestPath(request).startsWith(PUBLIC_PATH_PREFIX);
    }

    /**
     * Rejects traversal attempts, nested paths and non-image extensions with 404.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain remaining servlet filter chain
     * @throws ServletException when the downstream chain fails
     * @throws IOException when the response or downstream chain fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isAllowedDishImagePath(requestPath(request))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the application-relative request URI so deployments with a context path still validate correctly.
     *
     * @param request current HTTP request
     * @return path relative to the servlet context
     */
    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    /**
     * Confines public access to one image filename directly under /uploads/dish/.
     *
     * @param rawPath raw request path
     * @return true when the path is a safe dish image URL
     */
    private boolean isAllowedDishImagePath(String rawPath) {
        String decodedPath = decodeRepeatedly(rawPath);
        if (decodedPath == null || containsTraversal(decodedPath)) {
            return false;
        }
        String filename = decodedPath.substring(PUBLIC_PATH_PREFIX.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        int extensionStart = filename.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == filename.length() - 1) {
            return false;
        }
        String extension = filename.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * Decodes percent-encoded traversal markers a few times to catch double-encoded attacks.
     *
     * @param value raw path value
     * @return decoded path, or null when the path contains invalid percent encoding
     */
    private String decodeRepeatedly(String value) {
        String decoded = value;
        for (int i = 0; i < 3; i++) {
            try {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (next.equals(decoded)) {
                    return next;
                }
                decoded = next;
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return decoded;
    }

    /**
     * Checks normalized slash variants for parent-directory traversal.
     *
     * @param path decoded request path
     * @return true when the path tries to navigate outside the upload directory
     */
    private boolean containsTraversal(String path) {
        return path.contains("../") || path.contains("..\\") || path.endsWith("/..") || path.endsWith("\\..");
    }
}
