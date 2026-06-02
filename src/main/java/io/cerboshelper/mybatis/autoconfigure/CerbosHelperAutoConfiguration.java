package io.cerboshelper.mybatis.autoconfigure;

import io.cerboshelper.mybatis.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.CerbosCheckAspect;
import io.cerboshelper.mybatis.CerbosHelperProperties;
import io.cerboshelper.mybatis.CerbosHttpAuthorizationClient;
import io.cerboshelper.mybatis.CerbosMyBatisScopeInterceptor;
import io.cerboshelper.mybatis.CerbosPayloadMapper;
import io.cerboshelper.mybatis.CerbosPlanProvider;
import io.cerboshelper.mybatis.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.CerbosResource;
import io.cerboshelper.mybatis.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.CerbosResourceColumns;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.client.RestClient;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(CerbosHelperProperties.class)
public class CerbosHelperAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CerbosPayloadMapper cerbosPayloadMapper(CerbosHelperProperties properties) {
        return new CerbosPayloadMapper(properties);
    }

    @Bean
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnMissingBean(CerbosPlanProvider.class)
    CerbosAuthorizationClient cerbosAuthorizationClient(CerbosHelperProperties properties, CerbosPayloadMapper payloadMapper) {
        return new CerbosHttpAuthorizationClient(properties, payloadMapper);
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(CerbosAuthorizationClient.class)
    @ConditionalOnMissingBean
    CerbosCheckAspect cerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory) {
        return new CerbosCheckAspect(authorizationClient, beanFactory);
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
    @ConditionalOnBean({CerbosPlanProvider.class, CerbosPlanToSqlConverter.class})
    @ConditionalOnMissingBean
    CerbosMyBatisScopeInterceptor cerbosMyBatisScopeInterceptor(CerbosPlanProvider planProvider, CerbosPlanToSqlConverter converter) {
        return new CerbosMyBatisScopeInterceptor(planProvider, converter);
    }

    @Bean
    @ConditionalOnBean(CerbosMyBatisScopeInterceptor.class)
    SmartInitializingSingleton cerbosHelperInterceptorOrderVerifier(List<SqlSessionFactory> sqlSessionFactories) {
        return new InterceptorOrderVerifier(sqlSessionFactories);
    }

    private static final class InterceptorOrderVerifier implements SmartInitializingSingleton {
        private static final Logger log = LoggerFactory.getLogger(InterceptorOrderVerifier.class);

        private final List<SqlSessionFactory> sqlSessionFactories;

        private InterceptorOrderVerifier(List<SqlSessionFactory> sqlSessionFactories) {
            this.sqlSessionFactories = sqlSessionFactories;
        }

        @Override
        public void afterSingletonsInstantiated() {
            sqlSessionFactories.forEach(this::moveCerbosInterceptorLast);
        }

        private void moveCerbosInterceptorLast(SqlSessionFactory sqlSessionFactory) {
            List<Interceptor> interceptors = mutableInterceptors(sqlSessionFactory);
            List<Interceptor> cerbosInterceptors = interceptors.stream()
                    .filter(CerbosMyBatisScopeInterceptor.class::isInstance)
                    .toList();
            if (cerbosInterceptors.isEmpty()) {
                return;
            }

            interceptors.removeIf(CerbosMyBatisScopeInterceptor.class::isInstance);
            interceptors.addAll(cerbosInterceptors);
            log.info("cerboshelper.mybatis.interceptor-order {}", interceptorNames(interceptors));
        }

        @SuppressWarnings("unchecked")
        private List<Interceptor> mutableInterceptors(SqlSessionFactory sqlSessionFactory) {
            try {
                Field chainField = sqlSessionFactory.getConfiguration().getClass().getDeclaredField("interceptorChain");
                chainField.setAccessible(true);
                InterceptorChain chain = (InterceptorChain) chainField.get(sqlSessionFactory.getConfiguration());

                Field interceptorsField = InterceptorChain.class.getDeclaredField("interceptors");
                interceptorsField.setAccessible(true);
                return (List<Interceptor>) interceptorsField.get(chain);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot inspect MyBatis interceptor order", exception);
            }
        }

        private List<String> interceptorNames(List<Interceptor> interceptors) {
            List<String> names = new ArrayList<>();
            for (Interceptor interceptor : interceptors) {
                names.add(interceptor.getClass().getSimpleName());
            }
            return names;
        }
    }
}
