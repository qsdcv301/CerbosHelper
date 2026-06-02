package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class CerbosMyBatisScopeInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(CerbosMyBatisScopeInterceptor.class);
    private static final String PARAM_PREFIX = "__cerbos_scope_param_";

    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPlanToSqlConverter planToSqlConverter;
    private final CerbosPrincipalResolver principalResolver;

    public CerbosMyBatisScopeInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, CerbosPrincipalResolver principalResolver) {
        this.authorizationClient = authorizationClient;
        this.planToSqlConverter = planToSqlConverter;
        this.principalResolver = principalResolver;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        Object parameter = args[1];
        RowBounds rowBounds = (RowBounds) args[2];
        ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];
        Executor executor = (Executor) invocation.getTarget();

        ScopedMethod scopedMethod = findScopedMethod(statement);
        if (scopedMethod == null) {
            return invocation.proceed();
        }
        CerbosScoped scoped = scopedMethod.annotation();
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            throw new IllegalStateException("@CerbosScoped can only be used on SELECT statements: " + statement.getId());
        }

        CerbosScopeContext.Request request = CerbosScopeContext.current();

        String action = resolveAction(scoped, request);
        Object principal = resolvePrincipal(request, statement);
        BoundSql boundSql = args.length == 4 ? statement.getBoundSql(parameter) : (BoundSql) args[5];
        String resourceKind = resolveResourceKind(scopedMethod);
        BoundSql scopedBoundSql = applyCerbosScope(statement, boundSql, resourceKind, action, principal);
        CacheKey cacheKey = executor.createCacheKey(statement, parameter, rowBounds, scopedBoundSql);

        if (args.length == 4) {
            return executor.query(statement, parameter, rowBounds, resultHandler, cacheKey, scopedBoundSql);
        }
        args[4] = cacheKey;
        args[5] = scopedBoundSql;
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private BoundSql applyCerbosScope(MappedStatement statement, BoundSql boundSql, String resourceKind, String action, Object principal) {
        JsonNode plan = authorizationClient.planResources(principal, resourceKind, action);
        CerbosSqlFilter filter = planToSqlConverter.convertPositional(resourceKind, plan);
        SqlMergeResult mergeResult = mergePredicate(boundSql.getSql(), filter.denied() ? "1 = 0" : filter.whereSql());

        List<ParameterMapping> originalMappings = boundSql.getParameterMappings();
        int insertionPoint = Math.min(mergeResult.parameterInsertionIndex(), originalMappings.size());
        List<ParameterMapping> mappings = new ArrayList<>();
        mappings.addAll(originalMappings.subList(0, insertionPoint));
        for (int index = 0; index < filter.positionalParams().size(); index++) {
            mappings.add(new ParameterMapping.Builder(statement.getConfiguration(), PARAM_PREFIX + index, Object.class).build());
        }
        mappings.addAll(originalMappings.subList(insertionPoint, originalMappings.size()));

        BoundSql scopedBoundSql = new BoundSql(statement.getConfiguration(), mergeResult.sql(), mappings, boundSql.getParameterObject());
        boundSql.getAdditionalParameters().forEach(scopedBoundSql::setAdditionalParameter);
        for (int index = 0; index < filter.positionalParams().size(); index++) {
            scopedBoundSql.setAdditionalParameter(PARAM_PREFIX + index, filter.positionalParams().get(index));
        }

        log.debug(
                "cerboshelper.mybatis-scope statement={} action={} predicate={} params={}",
                statement.getId(),
                action,
                filter.denied() ? "1 = 0" : filter.whereSql(),
                filter.positionalParams()
        );
        return scopedBoundSql;
    }

    private SqlMergeResult mergePredicate(String sql, String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return new SqlMergeResult(sql, countPlaceholders(sql));
        }

        int orderByIndex = findTopLevelOrderBy(sql);
        String selectPart = orderByIndex >= 0 ? sql.substring(0, orderByIndex) : sql;
        String suffix = orderByIndex >= 0 ? sql.substring(orderByIndex) : "";
        String trimmedSelect = selectPart.stripTrailing();
        int insertionIndex = countPlaceholders(trimmedSelect);

        if (hasTopLevelWhere(trimmedSelect)) {
            return new SqlMergeResult(trimmedSelect + " AND (" + predicate + ") " + suffix.stripLeading(), insertionIndex);
        }
        return new SqlMergeResult(trimmedSelect + " WHERE (" + predicate + ") " + suffix.stripLeading(), insertionIndex);
    }

    private int countPlaceholders(String sql) {
        int count = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }

    private boolean hasTopLevelWhere(String sql) {
        return findTopLevelKeyword(sql, "where", 0) >= 0;
    }

    private int findTopLevelOrderBy(String sql) {
        int index = 0;
        while (index >= 0 && index < sql.length()) {
            int orderIndex = findTopLevelKeyword(sql, "order", index);
            if (orderIndex < 0) {
                return -1;
            }
            int afterOrder = orderIndex + "order".length();
            int byIndex = findTopLevelKeyword(sql, "by", afterOrder);
            if (byIndex >= 0 && sql.substring(afterOrder, byIndex).isBlank()) {
                return orderIndex;
            }
            index = afterOrder;
        }
        return -1;
    }

    private int findTopLevelKeyword(String sql, String keyword, int start) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        int depth = 0;
        boolean inSingleQuote = false;
        for (int index = start; index <= sql.length() - keyword.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (inSingleQuote) {
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && lowerSql.startsWith(keyword, index) && isKeywordBoundary(sql, index, keyword.length())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isKeywordBoundary(String sql, int start, int length) {
        int before = start - 1;
        int after = start + length;
        return (before < 0 || !isIdentifierPart(sql.charAt(before))) && (after >= sql.length() || !isIdentifierPart(sql.charAt(after)));
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private String resolveAction(CerbosScoped scoped, CerbosScopeContext.Request request) {
        if (request != null && request.action() != null && !request.action().isBlank()) {
            return request.action();
        }
        if (!scoped.action().isBlank()) {
            return scoped.action();
        }
        throw new IllegalStateException("No Cerbos action for scoped mapper statement");
    }

    private Object resolvePrincipal(CerbosScopeContext.Request request, MappedStatement statement) {
        if (request != null && request.principal() != null) {
            return request.principal();
        }
        return principalResolver.currentPrincipal()
                .orElseThrow(() -> new IllegalStateException("No Cerbos principal for protected statement: " + statement.getId()
                        + ". Register a CerbosPrincipalResolver bean or wrap the call with @CerbosScope(principal = \"...\")."));
    }

    private String resolveResourceKind(ScopedMethod scopedMethod) {
        if (!scopedMethod.annotation().resourceKind().isBlank()) {
            return scopedMethod.annotation().resourceKind();
        }
        return resourceKindFromReturnType(scopedMethod.method())
                .orElseThrow(() -> new IllegalStateException("No Cerbos resourceKind for scoped mapper method: " + scopedMethod.method()
                        + ". Set @CerbosScoped(resourceKind = \"...\") or annotate the mapper return type with @CerbosResource(kind = \"...\")."));
    }

    private java.util.Optional<String> resourceKindFromReturnType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterizedType) {
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                java.util.Optional<String> resourceKind = resourceKindFromType(argument);
                if (resourceKind.isPresent()) {
                    return resourceKind;
                }
            }
        }
        return resourceKindFromType(returnType);
    }

    private java.util.Optional<String> resourceKindFromType(Type type) {
        if (type instanceof Class<?> returnClass && returnClass.isAnnotationPresent(CerbosResource.class)) {
            return java.util.Optional.of(returnClass.getAnnotation(CerbosResource.class).kind());
        }
        return java.util.Optional.empty();
    }

    private ScopedMethod findScopedMethod(MappedStatement statement) {
        int separator = statement.getId().lastIndexOf('.');
        if (separator < 0) {
            return null;
        }
        String className = statement.getId().substring(0, separator);
        String methodName = statement.getId().substring(separator + 1);
        try {
            Class<?> mapperType = Class.forName(className);
            for (Method method : mapperType.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(CerbosScoped.class)) {
                    return new ScopedMethod(method, method.getAnnotation(CerbosScoped.class));
                }
            }
            return null;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot resolve mapper type for statement: " + statement.getId(), exception);
        }
    }

    private record SqlMergeResult(String sql, int parameterInsertionIndex) {
    }

    private record ScopedMethod(Method method, CerbosScoped annotation) {
    }
}
