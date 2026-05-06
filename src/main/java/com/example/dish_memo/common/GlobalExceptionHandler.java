package com.example.dish_memo.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts framework and domain exceptions to the documented response envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles expected business failures.
     *
     * @param ex application exception
     * @param request current servlet request
     * @return API error response
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        warn(request, "handle business exception", ex);
        return ResponseEntity.status(ex.errorCode().status())
                .body(ApiResponse.error(ex.errorCode(), ex.getMessage()));
    }

    /**
     * Handles bean validation failures from JSON request bodies.
     *
     * @param ex validation exception
     * @param request current servlet request
     * @return parameter error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        warn(request, "handle request body validation exception", ex);
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "request body is invalid" : fieldError.getDefaultMessage();
        return parameterError(message);
    }

    /**
     * Handles query and path validation failures.
     *
     * @param ex validation exception
     * @param request current servlet request
     * @return parameter error response
     */
    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleParameterException(Exception ex, HttpServletRequest request) {
        warn(request, "handle request parameter exception", ex);
        return parameterError("parameter is invalid");
    }

    /**
     * Handles missing user identity headers.
     *
     * @param ex missing header exception
     * @param request current servlet request
     * @return authentication error response
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeaderException(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        warn(request, "handle missing request header exception", ex);
        if (ApiHeaders.WX_OPENID.equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.status(ErrorCode.AUTH_FAILED.status())
                    .body(ApiResponse.error(ErrorCode.AUTH_FAILED, ApiHeaders.WX_OPENID + " is required"));
        }
        return parameterError("required header is missing");
    }

    /**
     * Handles requests that do not match any documented endpoint or static resource.
     *
     * @param ex missing resource exception
     * @param request current servlet request
     * @return not found response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        warn(request, "handle missing resource exception", ex);
        return ResponseEntity.status(ErrorCode.DISH_NOT_FOUND.status())
                .body(ApiResponse.error(ErrorCode.DISH_NOT_FOUND, "resource not found"));
    }

    /**
     * Handles unexpected server failures without leaking stack details.
     *
     * @param ex unexpected exception
     * @param request current servlet request
     * @return internal error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        warn(request, "handle unexpected exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "internal server error"));
    }

    private void warn(HttpServletRequest request, String description, Exception ex) {
        LOGGER.warn(StructuredLogUtils.exception(userId(request), description, ex));
    }

    private String userId(HttpServletRequest request) {
        return request == null ? StructuredLogUtils.UNKNOWN_USER_ID : request.getHeader(ApiHeaders.WX_OPENID);
    }

    private ResponseEntity<ApiResponse<Void>> parameterError(String message) {
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.status())
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, message));
    }
}
