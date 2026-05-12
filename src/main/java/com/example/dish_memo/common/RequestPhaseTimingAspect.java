package com.example.dish_memo.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Records Controller and Service method durations for detailed request phase logs.
 */
@Aspect
@Component
public class RequestPhaseTimingAspect {

    /**
     * Measures controller method duration for the current request.
     *
     * @param joinPoint intercepted controller method
     * @return original controller method result
     * @throws Throwable when the intercepted method fails
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object recordControllerDuration(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            RequestLogContext.recordController(System.nanoTime() - start);
        }
    }

    /**
     * Measures service method duration for the current request.
     *
     * @param joinPoint intercepted service method
     * @return original service method result
     * @throws Throwable when the intercepted method fails
     */
    @Around("@within(org.springframework.stereotype.Service)")
    public Object recordServiceDuration(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            RequestLogContext.recordService(System.nanoTime() - start);
        }
    }
}
