package io.cerboshelper.mybatis.autoconfigure;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.check.CerbosAutoCheckAspect;
import io.cerboshelper.mybatis.scope.CerbosMyBatisScopeInterceptor;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.auth.CerbosSdkAuthorizationClient;
import io.cerboshelper.mybatis.auth.SpringSecurityCerbosPrincipalResolver;
import io.cerboshelper.mybatis.check.CerbosCheckAspect;
import io.cerboshelper.mybatis.check.CerbosResourceResolver;
import io.cerboshelper.mybatis.check.DefaultCerbosResourceResolver;
import io.cerboshelper.mybatis.convention.CerbosCheckConventionResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.convention.CerbosScopeConventionResolver;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptorOrderStrategy;
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
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.aspectj.lang.annotation.Aspect;
import dev.cerbos.sdk.CerbosBlockingClient;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(CerbosHelperProperties.class)
public class CerbosHelperAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CerbosCommonResourceRegistry cerbosCommonResourceRegistry(BeanFactory beanFactory) {
        return new CerbosCommonResourceRegistry(scanCommonResourceTypes(beanFactory));
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosCheckConventionResolver cerbosCheckConventionResolver(CerbosCommonResourceRegistry registry) {
        return new CerbosCheckConventionResolver(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosScopeConventionResolver cerbosScopeConventionResolver(CerbosCommonResourceRegistry registry) {
        return new CerbosScopeConventionResolver(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosPayloadMapper cerbosPayloadMapper(CerbosHelperProperties properties, CerbosCommonResourceRegistry registry) {
        return new CerbosPayloadMapper(properties, registry);
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
        return new SpringSecurityCerbosPrincipalResolver();
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
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(CerbosCheckAspect.class)
    @ConditionalOnMissingBean
    CerbosAutoCheckAspect cerbosAutoCheckAspect(CerbosCheckConventionResolver conventionResolver, CerbosCheckAspect checkAspect, CerbosHelperProperties properties) {
        return new CerbosAutoCheckAspect(conventionResolver, checkAspect, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceResolver cerbosResourceResolver(BeanFactory beanFactory) {
        return new DefaultCerbosResourceResolver(beanFactory, new CerbosMethodExpressionEvaluator(beanFactory));
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceColumnRegistry cerbosResourceColumnRegistry(CerbosCommonResourceRegistry registry) {
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
    CerbosSqlPredicateInjector cerbosSqlPredicateInjector() {
        return new DefaultCerbosSqlPredicateInjector();
    }

    @Bean
    @ConditionalOnBean({CerbosAuthorizationClient.class, CerbosPlanToSqlConverter.class})
    @ConditionalOnMissingBean
    CerbosMyBatisScopeInterceptor cerbosMyBatisScopeInterceptor(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter converter, CerbosSqlPredicateInjector sqlPredicateInjector, CerbosPrincipalResolver principalResolver, CerbosScopeConventionResolver conventionResolver) {
        return new CerbosMyBatisScopeInterceptor(authorizationClient, converter, sqlPredicateInjector, principalResolver, conventionResolver);
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

    private List<Class<?>> scanCommonResourceTypes(BeanFactory beanFactory) {
        List<Class<?>> resourceTypes = new ArrayList<>();
        if (!AutoConfigurationPackages.has(beanFactory)) {
            return resourceTypes;
        }
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CerbosCommonDto.class));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String basePackage : AutoConfigurationPackages.get(beanFactory)) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> {
                try {
                    Class<?> resourceType = Class.forName(candidate.getBeanClassName(), false, classLoader);
                    if (!resourceType.isInterface() && !Modifier.isAbstract(resourceType.getModifiers())) {
                        resourceTypes.add(resourceType);
                    }
                } catch (ClassNotFoundException exception) {
                    throw new IllegalStateException("Cannot load Cerbos common resource type: " + candidate.getBeanClassName(), exception);
                }
            });
        }
        return resourceTypes;
    }
}
