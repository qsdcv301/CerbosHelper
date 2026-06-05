package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.scope.CerbosMyBatisScopeInterceptor;
import io.cerboshelper.mybatis.scope.DefaultCerbosMyBatisInterceptorOrderStrategy;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosMyBatisInterceptorOrderStrategyTest {
    @Test
    void movesCerbosInterceptorBeforeOtherInterceptors() {
        Configuration configuration = new Configuration();
        Interceptor pageHelperLikeInterceptor = new NoopInterceptor();
        CerbosMyBatisScopeInterceptor cerbosInterceptor = new CerbosMyBatisScopeInterceptor(null, null, null, null);
        configuration.addInterceptor(pageHelperLikeInterceptor);
        configuration.addInterceptor(cerbosInterceptor);

        new DefaultCerbosMyBatisInterceptorOrderStrategy()
                .apply(List.of(new DefaultSqlSessionFactory(configuration)));

        List<Class<?>> interceptorTypes = configuration.getInterceptors().stream()
                .map(Object::getClass)
                .toList();
        assertEquals(List.of(CerbosMyBatisScopeInterceptor.class, NoopInterceptor.class), interceptorTypes);
    }

    static class NoopInterceptor implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return target;
        }

        @Override
        public void setProperties(Properties properties) {
        }
    }
}
