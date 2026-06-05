package io.cerboshelper.mybatis.scope;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class DefaultCerbosMyBatisInterceptorOrderStrategy implements CerbosMyBatisInterceptorOrderStrategy {
    private static final Logger log = LoggerFactory.getLogger(DefaultCerbosMyBatisInterceptorOrderStrategy.class);

    @Override
    public void apply(List<SqlSessionFactory> sqlSessionFactories) {
        sqlSessionFactories.forEach(this::moveCerbosInterceptorFirst);
    }

    private void moveCerbosInterceptorFirst(SqlSessionFactory sqlSessionFactory) {
        List<Interceptor> interceptors = mutableInterceptors(sqlSessionFactory);
        List<Interceptor> cerbosInterceptors = interceptors.stream()
                .filter(CerbosMyBatisScopeInterceptor.class::isInstance)
                .toList();
        if (cerbosInterceptors.isEmpty()) {
            return;
        }

        interceptors.removeIf(CerbosMyBatisScopeInterceptor.class::isInstance);
        // MyBatis wraps plugins in registration order, so the last interceptor runs first.
        // Keep PageHelper outside Cerbos so it can build count/page SQL before scope injection.
        interceptors.addAll(0, cerbosInterceptors);
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
            throw new IllegalStateException("Cannot inspect MyBatis interceptor order. "
                    + "Register a custom CerbosMyBatisInterceptorOrderStrategy bean if this MyBatis version does not expose the default interceptor chain fields.", exception);
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
