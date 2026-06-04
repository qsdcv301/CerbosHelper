package io.cerboshelper.mybatis.autoconfigure;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.scope.CerbosMyBatisScopeInterceptor;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.annotation.CerbosResource;
import io.cerboshelper.mybatis.auth.CerbosSdkAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosCheckAspect;
import io.cerboshelper.mybatis.check.CerbosResourceResolver;
import io.cerboshelper.mybatis.check.DefaultCerbosResourceResolver;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptorOrderStrategy;
import io.cerboshelper.mybatis.scope.CerbosScopeAspect;
import io.cerboshelper.mybatis.scope.DefaultCerbosMyBatisInterceptorOrderStrategy;
import io.cerboshelper.mybatis.sql.CerbosSqlPredicateInjector;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import io.cerboshelper.mybatis.sql.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.aspectj.lang.annotation.Aspect;
import dev.cerbos.sdk.CerbosBlockingClient;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(CerbosHelperProperties.class)
public class CerbosHelperAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CerbosPayloadMapper cerbosPayloadMapper(CerbosHelperProperties properties) {
        return new CerbosPayloadMapper(properties);
    }

    @Bean
    @ConditionalOnClass(CerbosBlockingClient.class)
    @ConditionalOnMissingBean(CerbosAuthorizationClient.class)
    CerbosAuthorizationClient cerbosAuthorizationClient(CerbosHelperProperties properties, CerbosPayloadMapper payloadMapper) {
        return new CerbosSdkAuthorizationClient(properties, payloadMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosPrincipalResolver cerbosPrincipalResolver() {
        return Optional::empty;
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosAccessDeniedHandler cerbosAccessDeniedHandler() {
        return CerbosAccessDeniedHandler.securityException();
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(CerbosAuthorizationClient.class)
    @ConditionalOnMissingBean
    CerbosCheckAspect cerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler, CerbosResourceResolver resourceResolver) {
        return new CerbosCheckAspect(authorizationClient, beanFactory, principalResolver, accessDeniedHandler, resourceResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceResolver cerbosResourceResolver(BeanFactory beanFactory) {
        return new DefaultCerbosResourceResolver(beanFactory, new CerbosMethodExpressionEvaluator(beanFactory));
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnMissingBean
    CerbosScopeAspect cerbosScopeAspect(BeanFactory beanFactory, CerbosPrincipalResolver principalResolver) {
        return new CerbosScopeAspect(beanFactory, principalResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceColumnRegistry cerbosResourceColumnRegistry(BeanFactory beanFactory) {
        CerbosResourceColumns.Builder builder = CerbosResourceColumnRegistry.builder();
        if (!AutoConfigurationPackages.has(beanFactory)) {
            return builder.build();
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CerbosResource.class));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String basePackage : AutoConfigurationPackages.get(beanFactory)) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> {
                try {
                    builder.resource(Class.forName(candidate.getBeanClassName(), false, classLoader));
                } catch (ClassNotFoundException exception) {
                    throw new IllegalStateException("Cannot load Cerbos resource type: " + candidate.getBeanClassName(), exception);
                }
            });
        }
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
    CerbosSqlPredicateInjector cerbosSqlPredicateInjector() {
        return new DefaultCerbosSqlPredicateInjector();
    }

    @Bean
    @ConditionalOnBean({CerbosAuthorizationClient.class, CerbosPlanToSqlConverter.class})
    @ConditionalOnMissingBean
    CerbosMyBatisScopeInterceptor cerbosMyBatisScopeInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter converter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver) {
        return new CerbosMyBatisScopeInterceptor(authorizationClient, converter, sqlPredicateInjector, principalResolver);
    }

    @Bean
    @ConditionalOnBean(CerbosMyBatisScopeInterceptor.class)
    SmartInitializingSingleton cerbosHelperInterceptorOrderVerifier(List<SqlSessionFactory> sqlSessionFactories, CerbosMyBatisInterceptorOrderStrategy interceptorOrderStrategy) {
        return () -> interceptorOrderStrategy.apply(sqlSessionFactories);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosMyBatisInterceptorOrderStrategy cerbosMyBatisInterceptorOrderStrategy() {
        return new DefaultCerbosMyBatisInterceptorOrderStrategy();
    }
}
