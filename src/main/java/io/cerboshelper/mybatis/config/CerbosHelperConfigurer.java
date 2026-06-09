package io.cerboshelper.mybatis.config;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.check.CerbosResourceResolver;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptorOrderStrategy;
import io.cerboshelper.mybatis.sql.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.sql.CerbosSqlPredicateInjector;

import java.util.Optional;
import java.util.function.Consumer;

public final class CerbosHelperConfigurer {
    private CerbosAuthorizationClient authorizationClient;
    private CerbosPrincipalResolver principalResolver;
    private CerbosAccessDeniedHandler accessDeniedHandler;
    private CerbosResourceResolver resourceResolver;
    private CerbosResourceColumnRegistry resourceColumnRegistry;
    private CerbosSqlPredicateInjector sqlPredicateInjector;
    private CerbosMyBatisInterceptorOrderStrategy interceptorOrderStrategy;
    private final CerbosAutoCheckOptions autoCheck = new CerbosAutoCheckOptions();

    public CerbosHelperConfigurer authorizationClient(CerbosAuthorizationClient authorizationClient) {
        this.authorizationClient = authorizationClient;
        return this;
    }

    public CerbosHelperConfigurer principalResolver(CerbosPrincipalResolver principalResolver) {
        this.principalResolver = principalResolver;
        return this;
    }

    public CerbosHelperConfigurer accessDeniedHandler(CerbosAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
        return this;
    }

    public CerbosHelperConfigurer resourceResolver(CerbosResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        return this;
    }

    public CerbosHelperConfigurer resourceColumnRegistry(CerbosResourceColumnRegistry resourceColumnRegistry) {
        this.resourceColumnRegistry = resourceColumnRegistry;
        return this;
    }

    public CerbosHelperConfigurer sqlPredicateInjector(CerbosSqlPredicateInjector sqlPredicateInjector) {
        this.sqlPredicateInjector = sqlPredicateInjector;
        return this;
    }

    public CerbosHelperConfigurer interceptorOrderStrategy(CerbosMyBatisInterceptorOrderStrategy interceptorOrderStrategy) {
        this.interceptorOrderStrategy = interceptorOrderStrategy;
        return this;
    }

    public CerbosHelperConfigurer autoCheck(Consumer<CerbosAutoCheckOptions> customizer) {
        if (customizer != null) {
            customizer.accept(autoCheck);
        }
        return this;
    }

    public Optional<CerbosAuthorizationClient> authorizationClient() {
        return Optional.ofNullable(authorizationClient);
    }

    public Optional<CerbosPrincipalResolver> principalResolver() {
        return Optional.ofNullable(principalResolver);
    }

    public Optional<CerbosAccessDeniedHandler> accessDeniedHandler() {
        return Optional.ofNullable(accessDeniedHandler);
    }

    public Optional<CerbosResourceResolver> resourceResolver() {
        return Optional.ofNullable(resourceResolver);
    }

    public Optional<CerbosResourceColumnRegistry> resourceColumnRegistry() {
        return Optional.ofNullable(resourceColumnRegistry);
    }

    public Optional<CerbosSqlPredicateInjector> sqlPredicateInjector() {
        return Optional.ofNullable(sqlPredicateInjector);
    }

    public Optional<CerbosMyBatisInterceptorOrderStrategy> interceptorOrderStrategy() {
        return Optional.ofNullable(interceptorOrderStrategy);
    }

    public CerbosAutoCheckOptions autoCheck() {
        return autoCheck;
    }
}
