package io.cerboshelper.mybatis.check;

import com.example.cerboshelpertest.ApplicationService;
import com.example.cerboshelpertest.BeanFactoryMethod;
import com.example.cerboshelpertest.DemoConfiguration;
import io.cerboshelper.mybatis.config.CerbosAutoCheckOptions;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosAutoCheckAspectTest {
    private final AspectJExpressionPointcut pointcut = autoCheckPointcut();

    @Test
    void pointcutMatchesApplicationServices() throws NoSuchMethodException {
        Method method = ApplicationService.class.getDeclaredMethod("save");

        assertTrue(pointcut.matches(method, ApplicationService.class));
    }

    @Test
    void pointcutDoesNotMatchConfigurationClasses() throws NoSuchMethodException {
        Method method = DemoConfiguration.class.getDeclaredMethod("sampleBean");

        assertFalse(pointcut.matches(method, DemoConfiguration.class));
    }

    @Test
    void pointcutDoesNotMatchBeanMethods() throws NoSuchMethodException {
        Method method = BeanFactoryMethod.class.getDeclaredMethod("sampleBean");

        assertFalse(pointcut.matches(method, BeanFactoryMethod.class));
    }

    @Test
    void pointcutDoesNotMatchSpringInfrastructure() throws NoSuchMethodException {
        Method method = ApplicationContext.class.getDeclaredMethod("getId");

        assertFalse(pointcut.matches(method, ApplicationContext.class));
    }

    @Test
    void autoCheckFilterIncludesConfiguredClassPattern() throws NoSuchMethodException {
        CerbosAutoCheckOptions autoCheck = new CerbosAutoCheckOptions();
        autoCheck.setIncludeClassNamePatterns(List.of("ApplicationService"));
        CerbosAutoCheckAspect aspect = new CerbosAutoCheckAspect(null, null, autoCheck);
        Method method = ApplicationService.class.getDeclaredMethod("save");

        assertTrue(aspect.shouldAutoCheck(ApplicationService.class, method));
    }

    @Test
    void autoCheckFilterExcludesConfiguredClassPattern() throws NoSuchMethodException {
        CerbosAutoCheckOptions autoCheck = new CerbosAutoCheckOptions();
        autoCheck.setExcludeClassNamePatterns(List.of("ApplicationService"));
        CerbosAutoCheckAspect aspect = new CerbosAutoCheckAspect(null, null, autoCheck);
        Method method = ApplicationService.class.getDeclaredMethod("save");

        assertFalse(aspect.shouldAutoCheck(ApplicationService.class, method));
    }

    @Test
    void autoCheckFilterCanDisableAutoCheck() throws NoSuchMethodException {
        CerbosAutoCheckOptions autoCheck = new CerbosAutoCheckOptions();
        autoCheck.setEnabled(false);
        CerbosAutoCheckAspect aspect = new CerbosAutoCheckAspect(null, null, autoCheck);
        Method method = ApplicationService.class.getDeclaredMethod("save");

        assertFalse(aspect.shouldAutoCheck(ApplicationService.class, method));
    }

    private static AspectJExpressionPointcut autoCheckPointcut() {
        try {
            Method method = CerbosAutoCheckAspect.class.getDeclaredMethod("check", org.aspectj.lang.ProceedingJoinPoint.class);
            Around around = method.getAnnotation(Around.class);
            AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
            pointcut.setExpression(around.value());
            return pointcut;
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
