package io.cerboshelper.mybatis.autoconfigure;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptor;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.auth.CerbosSdkAuthorizationClient;
import io.cerboshelper.mybatis.auth.SpringSecurityCerbosPrincipalResolver;
import io.cerboshelper.mybatis.check.CerbosResourceCheckExecutor;
import io.cerboshelper.mybatis.config.CerbosHelperConfig;
import io.cerboshelper.mybatis.config.CerbosHelperConfigurer;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.rule.CerbosCheckRuleResolver;
import io.cerboshelper.mybatis.rule.CerbosScopeRuleResolver;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptorOrderStrategy;
import io.cerboshelper.mybatis.scope.DefaultCerbosMyBatisInterceptorOrderStrategy;
import io.cerboshelper.mybatis.sql.CerbosSqlPredicateInjector;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import io.cerboshelper.mybatis.sql.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import dev.cerbos.sdk.CerbosBlockingClient;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
public class CerbosHelperAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CerbosHelperConfigurer cerbosHelperConfigurer(ObjectProvider<CerbosHelperConfig> configs) {
        CerbosHelperConfigurer configurer = new CerbosHelperConfigurer();
        configs.orderedStream().forEach(config -> config.configure(configurer));
        return configurer;
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosCommonResourceRegistry cerbosCommonResourceRegistry(CerbosHelperConfigurer configurer) {
        return CerbosCommonResourceRegistry.configured(configurer.resources().resources());
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosCheckRuleResolver cerbosCheckRuleResolver(CerbosCommonResourceRegistry registry, CerbosHelperConfigurer configurer) {
        return new CerbosCheckRuleResolver(registry, configurer.methodRules());
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosScopeRuleResolver cerbosScopeRuleResolver(CerbosCommonResourceRegistry registry, CerbosHelperConfigurer configurer) {
        return new CerbosScopeRuleResolver(registry, configurer.methodRules());
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosPayloadMapper cerbosPayloadMapper(CerbosHelperConfigurer configurer, CerbosCommonResourceRegistry registry) {
        return new CerbosPayloadMapper(configurer.client(), registry);
    }

    @Bean
    @ConditionalOnClass(CerbosBlockingClient.class)
    @ConditionalOnMissingBean(CerbosAuthorizationClient.class)
    CerbosAuthorizationClient cerbosAuthorizationClient(CerbosPayloadMapper payloadMapper, CerbosHelperConfigurer configurer) {
        return configurer.authorizationClient()
                .orElseGet(() -> new CerbosSdkAuthorizationClient(configurer.client(), payloadMapper));
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosPrincipalResolver cerbosPrincipalResolver(CerbosHelperConfigurer configurer) {
        return configurer.principalResolver()
                .orElseGet(SpringSecurityCerbosPrincipalResolver::new);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosAccessDeniedHandler cerbosAccessDeniedHandler(CerbosHelperConfigurer configurer) {
        return configurer.accessDeniedHandler()
                .orElseGet(CerbosAccessDeniedHandler::securityException);
    }

    @Bean
    @ConditionalOnBean(CerbosAuthorizationClient.class)
    @ConditionalOnMissingBean
    CerbosResourceCheckExecutor cerbosResourceCheckExecutor(CerbosAuthorizationClient authorizationClient, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler) {
        return new CerbosResourceCheckExecutor(authorizationClient, principalResolver, accessDeniedHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceColumnRegistry cerbosResourceColumnRegistry(CerbosCommonResourceRegistry registry, CerbosHelperConfigurer configurer) {
        Optional<CerbosResourceColumnRegistry> configuredRegistry = configurer.resourceColumnRegistry();
        if (configuredRegistry.isPresent()) {
            return configuredRegistry.get();
        }
        CerbosResourceColumns.Builder builder = CerbosResourceColumnRegistry.builder();
        registry.resources().forEach(builder::resource);
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(CerbosResourceColumnRegistry.class)
    @ConditionalOnMissingBean
    CerbosPlanToSqlConverter cerbosPlanToSqlConverter(CerbosResourceColumnRegistry columnRegistry) {
        return new CerbosPlanToSqlConverter(columnRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosSqlPredicateInjector cerbosSqlPredicateInjector(CerbosHelperConfigurer configurer) {
        return configurer.sqlPredicateInjector()
                .orElseGet(DefaultCerbosSqlPredicateInjector::new);
    }

    @Bean
    @ConditionalOnBean({CerbosAuthorizationClient.class, CerbosPlanToSqlConverter.class})
    @ConditionalOnMissingBean
    CerbosMyBatisInterceptor cerbosMyBatisInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter converter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver, CerbosScopeRuleResolver scopeRuleResolver, CerbosCheckRuleResolver checkRuleResolver, CerbosResourceCheckExecutor checkExecutor) {
        return new CerbosMyBatisInterceptor(authorizationClient, converter, sqlPredicateInjector, principalResolver, scopeRuleResolver, checkRuleResolver, checkExecutor);
    }

    @Bean
    @ConditionalOnBean(CerbosMyBatisInterceptor.class)
    SmartInitializingSingleton cerbosHelperInterceptorOrderVerifier(List<SqlSessionFactory> sqlSessionFactories, CerbosMyBatisInterceptorOrderStrategy interceptorOrderStrategy) {
        return () -> interceptorOrderStrategy.apply(sqlSessionFactories);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosMyBatisInterceptorOrderStrategy cerbosMyBatisInterceptorOrderStrategy(CerbosHelperConfigurer configurer) {
        return configurer.interceptorOrderStrategy()
                .orElseGet(DefaultCerbosMyBatisInterceptorOrderStrategy::new);
    }

}
