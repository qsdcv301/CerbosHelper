package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosMethodExpressionEvaluatorTest {
    private final CerbosMethodExpressionEvaluator evaluator = new CerbosMethodExpressionEvaluator(new DefaultListableBeanFactory());

    @Test
    void usesNamedPrincipalParameterFirst() throws Exception {
        Method method = SampleService.class.getMethod("withPrincipal", Object.class, Object.class);
        CerbosMethodExpressionEvaluator.Context context = evaluator.context(method, new Object[]{"methodPrincipal", "resource"});

        Object principal = evaluator.principal("", context, () -> Optional.of("resolverPrincipal"));

        assertEquals("methodPrincipal", principal);
    }

    @Test
    void usesResolverBeforeFirstArgumentFallback() throws Exception {
        Method method = SampleService.class.getMethod("withoutPrincipal", Object.class);
        CerbosMethodExpressionEvaluator.Context context = evaluator.context(method, new Object[]{"resource"});

        Object principal = evaluator.principal("", context, () -> Optional.of("resolverPrincipal"));

        assertEquals("resolverPrincipal", principal);
    }

    @Test
    void fallsBackToFirstArgumentWhenNoResolverValueExists() throws Exception {
        Method method = SampleService.class.getMethod("withoutPrincipal", Object.class);
        CerbosMethodExpressionEvaluator.Context context = evaluator.context(method, new Object[]{"firstArgument"});

        Object principal = evaluator.principal("", context, Optional::empty);

        assertEquals("firstArgument", principal);
    }

    public static class SampleService {
        public void withPrincipal(Object principal, Object resource) {
        }

        public void withoutPrincipal(Object resource) {
        }
    }
}
