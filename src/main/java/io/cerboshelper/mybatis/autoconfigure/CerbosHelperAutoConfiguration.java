package io.cerboshelper.mybatis.autoconfigure;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.check.CerbosAutoCheckAspect;
import io.cerboshelper.mybatis.scope.CerbosMyBatisScopeInterceptor;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.auth.CerbosSdkAuthorizationClient;
import io.cerboshelper.mybatis.auth.SpringSecurityCerbosPrincipalResolver;
import io.cerboshelper.mybatis.check.CerbosResourceCheckExecutor;
import io.cerboshelper.mybatis.check.CerbosResourceResolver;
import io.cerboshelper.mybatis.check.DefaultCerbosResourceResolver;
import io.cerboshelper.mybatis.config.CerbosHelperConfig;
import io.cerboshelper.mybatis.config.CerbosHelperConfigurer;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(CerbosAuthorizationClient.class)
    @ConditionalOnMissingBean
    CerbosResourceCheckExecutor cerbosResourceCheckExecutor(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler, CerbosResourceResolver resourceResolver) {
        return new CerbosResourceCheckExecutor(authorizationClient, beanFactory, principalResolver, accessDeniedHandler, resourceResolver);
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(CerbosResourceCheckExecutor.class)
    @ConditionalOnMissingBean
    CerbosAutoCheckAspect cerbosAutoCheckAspect(CerbosCheckConventionResolver conventionResolver, CerbosResourceCheckExecutor checkExecutor, CerbosHelperConfigurer configurer) {
        return new CerbosAutoCheckAspect(conventionResolver, checkExecutor, configurer.autoCheck());
    }

    @Bean
    @ConditionalOnMissingBean
    CerbosResourceResolver cerbosResourceResolver(BeanFactory beanFactory, CerbosCommonResourceRegistry registry, CerbosHelperConfigurer configurer) {
        return configurer.resourceResolver()
                .orElseGet(() -> new DefaultCerbosResourceResolver(beanFactory, new CerbosMethodExpressionEvaluator(beanFactory), registry));
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
    CerbosMyBatisInterceptorOrderStrategy cerbosMyBatisInterceptorOrderStrategy(CerbosHelperConfigurer configurer) {
        return configurer.interceptorOrderStrategy()
                .orElseGet(DefaultCerbosMyBatisInterceptorOrderStrategy::new);
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
