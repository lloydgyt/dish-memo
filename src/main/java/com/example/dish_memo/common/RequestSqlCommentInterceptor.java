package com.example.dish_memo.common;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds request trace comments to MyBatis CRUD SQL and records Mapper execution timing.
 */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
        })
})
public class RequestSqlCommentInterceptor implements Interceptor {
    private static final Pattern TRACE_COMMENT = Pattern.compile("/\\*\\s*request_id:\\s*.*?\\s*\\*/", Pattern.DOTALL);
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?is)\\b(?:from|into|update|delete\\s+from)\\s+([`\\w.]+)"
    );
    private static final String DATABASE_NAME = "dish_memo";

    /**
     * Intercepts SQL preparation for comment injection and Executor calls for Mapper duration recording.
     *
     * @param invocation MyBatis invocation context
     * @return original invocation result
     * @throws Throwable when the underlying MyBatis operation fails
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof StatementHandler) {
            appendRequestComment(invocation.getTarget());
            return invocation.proceed();
        }
        if (invocation.getTarget() instanceof Executor) {
            return recordMapperDuration(invocation);
        }
        return invocation.proceed();
    }

    /**
     * Wraps supported MyBatis targets with this interceptor.
     *
     * @param target MyBatis target object
     * @return proxied target when supported
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * Accepts MyBatis plugin properties for framework compatibility.
     *
     * @param properties configured plugin properties
     */
    @Override
    public void setProperties(Properties properties) {
        // No configurable MyBatis properties are required.
    }

    /**
     * Appends the documented request_id SQL comment to core CRUD statements.
     *
     * @param sql original SQL
     * @param requestId current request ID
     * @return SQL with a trailing request_id comment when the statement is INSERT/UPDATE/DELETE/SELECT
     */
    static String appendRequestIdComment(String sql, String requestId) {
        if (sql == null || !isCrudSql(sql) || TRACE_COMMENT.matcher(sql).find()) {
            return sql;
        }
        String trimmed = sql.trim();
        String suffix = " /* request_id: " + sanitizeRequestId(requestId) + " */";
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1) + suffix + ";";
        }
        return trimmed + suffix;
    }

    /**
     * Removes the trace comment and normalizes whitespace for phase log SQL fingerprints.
     *
     * @param sql SQL text
     * @return SQL fingerprint without request_id comments
     */
    static String fingerprint(String sql) {
        if (sql == null) {
            return "";
        }
        return TRACE_COMMENT.matcher(sql).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /**
     * Extracts the primary table label for the documented phase log.
     *
     * @param sql SQL text
     * @return database:table label when a table is found
     */
    static String dbTable(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(fingerprint(sql));
        if (!matcher.find()) {
            return "";
        }
        String table = matcher.group(1).replace("`", "");
        int dotIndex = table.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < table.length() - 1) {
            return table.substring(0, dotIndex) + ":" + table.substring(dotIndex + 1);
        }
        return DATABASE_NAME + ":" + table;
    }

    private void appendRequestComment(Object statementHandler) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        String path = metaObject.hasGetter("delegate.boundSql.sql") ? "delegate.boundSql.sql" : "boundSql.sql";
        if (!metaObject.hasGetter(path)) {
            return;
        }
        String sql = (String) metaObject.getValue(path);
        metaObject.setValue(path, appendRequestIdComment(sql, RequestLogContext.requestId()));
    }

    private Object recordMapperDuration(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        BoundSql boundSql = boundSql(mappedStatement, args);
        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            RequestLogContext.recordMapper(
                    mappedStatement.getId(),
                    dbTable(boundSql.getSql()),
                    resultSize(result),
                    fingerprint(boundSql.getSql()),
                    System.nanoTime() - start
            );
            return result;
        } catch (Throwable ex) {
            RequestLogContext.recordMapper(
                    mappedStatement.getId(),
                    dbTable(boundSql.getSql()),
                    0,
                    fingerprint(boundSql.getSql()),
                    System.nanoTime() - start
            );
            throw ex;
        }
    }

    private BoundSql boundSql(MappedStatement mappedStatement, Object[] args) {
        if (args.length == 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        Object parameter = args.length > 1 ? args[1] : null;
        return mappedStatement.getBoundSql(parameter);
    }

    private static int resultSize(Object result) {
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        return result == null ? 0 : 1;
    }

    private static boolean isCrudSql(String sql) {
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.startsWith("select")
                || normalized.startsWith("insert")
                || normalized.startsWith("update")
                || normalized.startsWith("delete");
    }

    private static String sanitizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return StructuredLogUtils.UNKNOWN_USER_ID;
        }
        return requestId.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }
}
