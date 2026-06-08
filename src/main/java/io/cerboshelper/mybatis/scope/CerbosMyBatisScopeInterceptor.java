package io.cerboshelper.mybatis.scope;

import com.fasterxml.jackson.databind.JsonNode;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.convention.CerbosScopeConventionResolver;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.sql.CerbosSqlInjectionResult;
import io.cerboshelper.mybatis.sql.CerbosSqlFilter;
import io.cerboshelper.mybatis.sql.CerbosSqlPredicateInjector;
import io.cerboshelper.mybatis.sql.CerbosSqlResourceAliasResolver;
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
import java.util.Properties;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class CerbosMyBatisScopeInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(CerbosMyBatisScopeInterceptor.class);
    private static final String PARAM_PREFIX = "__cerbos_scope_param_";
    private static final CerbosSqlResourceAliasResolver SQL_ALIAS_RESOLVER = new CerbosSqlResourceAliasResolver();

    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPlanToSqlConverter planToSqlConverter;
    private final CerbosSqlPredicateInjector sqlPredicateInjector;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosScopeConventionResolver conventionResolver;

    public CerbosMyBatisScopeInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver) {
        this(authorizationClient, planToSqlConverter, sqlPredicateInjector, principalResolver, null);
    }

    public CerbosMyBatisScopeInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver, CerbosScopeConventionResolver conventionResolver) {
        this.authorizationClient = authorizationClient;
        this.planToSqlConverter = planToSqlConverter;
        this.sqlPredicateInjector = sqlPredicateInjector;
        this.principalResolver = principalResolver;
        this.conventionResolver = conventionResolver;
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
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            throw new IllegalStateException("Cerbos scoped rules can only be used on SELECT statements: " + statement.getId());
        }

        String action = resolveAction(scopedMethod);
        Object principal = resolvePrincipal(statement);
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
        String sqlAlias = sqlPredicateInjector.predicateAlias(boundSql.getSql(), resourceKind)
                .or(() -> SQL_ALIAS_RESOLVER.resolve(boundSql.getSql(), resourceKind))
                .orElse("");
        CerbosSqlFilter filter = planToSqlConverter.convertPositional(resourceKind, plan, sqlAlias);
        CerbosSqlInjectionResult injectionResult = sqlPredicateInjector.inject(boundSql.getSql(), filter.denied() ? "1 = 0" : filter.whereSql());

        List<ParameterMapping> originalMappings = boundSql.getParameterMappings();
        int insertionPoint = Math.min(injectionResult.parameterInsertionIndex(), originalMappings.size());
        List<ParameterMapping> mappings = new ArrayList<>();
        mappings.addAll(originalMappings.subList(0, insertionPoint));
        for (int index = 0; index < filter.positionalParams().size(); index++) {
            mappings.add(new ParameterMapping.Builder(statement.getConfiguration(), PARAM_PREFIX + index, Object.class).build());
        }
        mappings.addAll(originalMappings.subList(insertionPoint, originalMappings.size()));

        BoundSql scopedBoundSql = new BoundSql(statement.getConfiguration(), injectionResult.sql(), mappings, boundSql.getParameterObject());
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

    private String resolveAction(ScopedMethod scopedMethod) {
        if (scopedMethod.convention() != null && !scopedMethod.convention().action().isBlank()) {
            return scopedMethod.convention().action();
        }
        if (scopedMethod.convention() == null) {
            throw new IllegalStateException("No Cerbos action for scoped mapper statement");
        }
        throw new IllegalStateException("No Cerbos action for scoped mapper statement");
    }

    private Object resolvePrincipal(MappedStatement statement) {
        return principalResolver.currentPrincipal()
                .orElseThrow(() -> new IllegalStateException("No Cerbos principal for protected statement: " + statement.getId()
                        + ". Register a CerbosPrincipalResolver bean."));
    }

    private String resolveResourceKind(ScopedMethod scopedMethod) {
        if (scopedMethod.convention() != null && !scopedMethod.convention().resourceKind().isBlank()) {
            return scopedMethod.convention().resourceKind();
        }
        return resourceKindFromReturnType(scopedMethod.method())
                .orElseThrow(() -> new IllegalStateException("No Cerbos resourceKind for scoped mapper method: " + scopedMethod.method()
                        + ". Return a CerbosCommonDto type or use a mapper method name that includes a CerbosCommonDto resource token."));
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
        if (type instanceof Class<?> returnClass) {
            return conventionResolver == null ? java.util.Optional.empty() : conventionResolver.resourceKindForType(returnClass);
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
            for (String candidateMethodName : candidateMethodNames(methodName)) {
                for (Method method : mapperType.getMethods()) {
                    if (method.getName().equals(candidateMethodName)) {
                        CerbosScopeConventionResolver.CerbosScopeConvention convention = conventionResolver == null
                                ? null
                                : conventionResolver.resolve(method).orElse(null);
                        if (convention != null) {
                            return new ScopedMethod(method, convention);
                        }
                    }
                }
            }
            return null;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot resolve mapper type for statement: " + statement.getId(), exception);
        }
    }

    private List<String> candidateMethodNames(String methodName) {
        if (methodName.endsWith("_COUNT")) {
            return List.of(methodName.substring(0, methodName.length() - "_COUNT".length()), methodName);
        }
        return List.of(methodName);
    }

    private record ScopedMethod(Method method, CerbosScopeConventionResolver.CerbosScopeConvention convention) {
    }
}
