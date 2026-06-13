package com.example.dish_memo.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Holds request-scoped logging values that must be shared by MVC and MyBatis interceptors.
 */
public final class RequestLogContext {
    private static final ThreadLocal<Context> CURRENT = ThreadLocal.withInitial(Context::new);

    private RequestLogContext() {
    }

    /**
     * Initializes the current thread context at the start of request processing.
     *
     * @param requestId trace ID used by API logs and SQL comments
     * @param userId current request user ID
     */
    public static void start(String requestId, String userId) {
        Context context = new Context();
        context.requestId = requestId;
        context.userId = userId;
        CURRENT.set(context);
    }

    /**
     * Clears the request context when request processing is complete.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Returns the current request ID, or an empty string when no web request is active.
     *
     * @return current request ID
     */
    public static String requestId() {
        return CURRENT.get().requestId;
    }

    /**
     * Adds one Mapper execution duration to the request summary and phase detail list.
     *
     * @param statementId MyBatis mapped statement ID
     * @param dbTable database and table label
     * @param resultSize mapped result size, or zero for write statements
     * @param sqlFingerprint SQL text with trace comments removed
     * @param durationNanos Mapper execution duration in nanoseconds
     */
    public static void recordMapper(
            String statementId,
            String dbTable,
            int resultSize,
            String sqlFingerprint,
            long durationNanos
    ) {
        Context context = CURRENT.get();
        long durationMs = Math.max(TimeUnit.NANOSECONDS.toMillis(durationNanos), 0);
        context.dbDurationMs += durationMs;
        context.mapperLogs.add(new MapperLog(
                statementId,
                dbTable,
                resultSize,
                sqlFingerprint,
                durationMs
        ));
    }

    /**
     * Adds Mapper duration to the request summary without capturing slow-log details.
     *
     * @param durationNanos Mapper execution duration in nanoseconds
     */
    public static void recordDbDuration(long durationNanos) {
        CURRENT.get().dbDurationMs += Math.max(TimeUnit.NANOSECONDS.toMillis(durationNanos), 0);
    }

    /**
     * Adds Controller method duration to the current request phase metrics.
     *
     * @param durationNanos Controller method duration in nanoseconds
     */
    public static void recordController(long durationNanos) {
        CURRENT.get().controllerDurationMs += Math.max(TimeUnit.NANOSECONDS.toMillis(durationNanos), 0);
    }

    /**
     * Adds Service method duration to the current request phase metrics.
     *
     * @param durationNanos Service method duration in nanoseconds
     */
    public static void recordService(long durationNanos) {
        CURRENT.get().serviceDurationMs += Math.max(TimeUnit.NANOSECONDS.toMillis(durationNanos), 0);
    }

    /**
     * Returns accumulated Mapper duration for the current request.
     *
     * @return database duration in milliseconds
     */
    public static long dbDurationMs() {
        return CURRENT.get().dbDurationMs;
    }

    /**
     * Returns accumulated Controller method duration for the current request.
     *
     * @return controller duration in milliseconds
     */
    public static long controllerDurationMs() {
        return CURRENT.get().controllerDurationMs;
    }

    /**
     * Returns accumulated Service method duration for the current request.
     *
     * @return service duration in milliseconds
     */
    public static long serviceDurationMs() {
        return CURRENT.get().serviceDurationMs;
    }

    /**
     * Returns every Mapper detail captured during the current request.
     *
     * @return immutable snapshot of Mapper details in execution order
     */
    public static List<MapperLog> mapperLogs() {
        return List.copyOf(CURRENT.get().mapperLogs);
    }

    /**
     * Immutable detail for the Mapper phase log required by the API document.
     */
    public record MapperLog(
            String statementId,
            String dbTable,
            int resultSize,
            String sqlFingerprint,
            long durationMs
    ) {
    }

    private static final class Context {
        private String requestId = "";
        private String userId = StructuredLogUtils.UNKNOWN_USER_ID;
        private long controllerDurationMs;
        private long serviceDurationMs;
        private long dbDurationMs;
        private final List<MapperLog> mapperLogs = new ArrayList<>();
    }
}
