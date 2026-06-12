package io.cerboshelper.mybatis.scope;

import com.fasterxml.jackson.databind.JsonNode;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.check.CerbosResourceCheckExecutor;
import io.cerboshelper.mybatis.rule.CerbosCheckRuleResolver;
import io.cerboshelper.mybatis.rule.CerbosScopeRuleResolver;
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
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class CerbosMyBatisInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(CerbosMyBatisInterceptor.class);
    private static final String PARAM_PREFIX = "__cerbos_scope_param_";
    private static final CerbosSqlResourceAliasResolver SQL_ALIAS_RESOLVER = new CerbosSqlResourceAliasResolver();

    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPlanToSqlConverter planToSqlConverter;
    private final CerbosSqlPredicateInjector sqlPredicateInjector;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosScopeRuleResolver scopeRuleResolver;
    private final CerbosCheckRuleResolver checkRuleResolver;
    private final CerbosResourceCheckExecutor checkExecutor;

    public CerbosMyBatisInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver) {
        this(authorizationClient, planToSqlConverter, sqlPredicateInjector, principalResolver, null, null, null);
    }

    public CerbosMyBatisInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver, CerbosScopeRuleResolver scopeRuleResolver) {
        this(authorizationClient, planToSqlConverter, sqlPredicateInjector, principalResolver, scopeRuleResolver, null, null);
    }

    public CerbosMyBatisInterceptor(
            CerbosAuthorizationClient authorizationClient,
            CerbosPlanToSqlConverter planToSqlConverter,
            CerbosSqlPredicateInjector sqlPredicateInjector,
            CerbosPrincipalResolver principalResolver,
            CerbosScopeRuleResolver scopeRuleResolver,
            CerbosCheckRuleResolver checkRuleResolver,
            CerbosResourceCheckExecutor checkExecutor
    ) {
        this.authorizationClient = authorizationClient;
        this.planToSqlConverter = planToSqlConverter;
        this.sqlPredicateInjector = sqlPredicateInjector;
        this.principalResolver = principalResolver;
        this.scopeRuleResolver = scopeRuleResolver;
        this.checkRuleResolver = checkRuleResolver;
        this.checkExecutor = checkExecutor;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        Object parameter = args[1];
        if (args.length == 2) {
            applyCerbosCommandCheck(statement, parameter);
            return invocation.proceed();
        }
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

    private void applyCerbosCommandCheck(MappedStatement statement, Object parameter) {
        if (checkRuleResolver == null || checkExecutor == null) {
            return;
        }
        Method mapperMethod = findMapperMethod(statement);
        if (mapperMethod == null) {
            return;
        }
        checkRuleResolver.resolveCommand(mapperMethod, parameter)
                .ifPresent(check -> checkExecutor.authorizeMappedResource(statement.getId(), check.action(), check.resource()));
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
        if (scopedMethod.rule() != null && !scopedMethod.rule().action().isBlank()) {
            return scopedMethod.rule().action();
        }
        if (scopedMethod.rule() == null) {
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
        if (scopedMethod.rule() != null && !scopedMethod.rule().resourceKind().isBlank()) {
            return scopedMethod.rule().resourceKind();
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
            return scopeRuleResolver == null ? java.util.Optional.empty() : scopeRuleResolver.resourceKindForType(returnClass);
        }
        return java.util.Optional.empty();
    }

    private ScopedMethod findScopedMethod(MappedStatement statement) {
        Method mapperMethod = findMapperMethod(statement);
        if (mapperMethod == null) {
            return null;
        }
        CerbosScopeRuleResolver.CerbosScopeRule rule = scopeRuleResolver == null
                ? null
                : scopeRuleResolver.resolve(mapperMethod).orElse(null);
        if (rule != null) {
            return new ScopedMethod(mapperMethod, rule);
        }
        return null;
    }

    private Method findMapperMethod(MappedStatement statement) {
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
                        return method;
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

    private record ScopedMethod(Method method, CerbosScopeRuleResolver.CerbosScopeRule rule) {
    }
}
