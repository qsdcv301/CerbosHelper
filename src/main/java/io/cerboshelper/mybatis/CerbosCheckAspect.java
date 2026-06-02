package io.cerboshelper.mybatis;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
public class CerbosCheckAspect {
    private final CerbosAuthorizationClient authorizationClient;
    private final BeanFactory beanFactory;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory) {
        this.authorizationClient = authorizationClient;
        this.beanFactory = beanFactory;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosCheck) || @annotation(io.cerboshelper.mybatis.CerbosChecks)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosCheck[] checks = method.getAnnotationsByType(CerbosCheck.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        for (CerbosCheck check : checks) {
            Object principal = expressionEvaluator.principal(check.principal(), context);
            Object resource = resolveResource(check, method, joinPoint.getArgs(), context);
            if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                throw new SecurityException("Cerbos denied " + check.action());
            }
        }
        return joinPoint.proceed();
    }

    private Object resolveResource(CerbosCheck check, Method method, Object[] args, CerbosMethodExpressionEvaluator.Context context) {
        if (!check.resource().isBlank()) {
            Object resource = tokenOrExpression(check.resource(), context);
            return applyIdIfPossible(resource, check, context);
        }
        if (!check.resourceKind().isBlank() && !check.id().isBlank()) {
            Object id = tokenOrExpression(check.id(), context);
            Object mapper = beanFactory.getBean(check.resourceKind() + "Mapper");
            return unwrap(invoke(mapper, "findById", id));
        }
        for (Object arg : args) {
            if (arg != null && arg.getClass().isAnnotationPresent(CerbosResource.class)) {
                return arg;
            }
        }
        Object resource = context.variable("resource");
        if (resource != null) {
            return resource;
        }
        throw new IllegalArgumentException("Cannot resolve Cerbos resource for " + method.getName() + ". Provide resource, resourceKind/id, or a @CerbosResource argument.");
    }

    private Object tokenOrExpression(String value, CerbosMethodExpressionEvaluator.Context context) {
        if (value.startsWith("#") || value.startsWith("@")) {
            return expressionEvaluator.value(value, context);
        }
        Object variable = context.variable(value);
        return variable != null ? variable : value;
    }

    private Object applyIdIfPossible(Object resource, CerbosCheck check, CerbosMethodExpressionEvaluator.Context context) {
        if (resource == null || check.id().isBlank()) {
            return resource;
        }
        Object id = tokenOrExpression(check.id(), context);
        try {
            Method withId = resource.getClass().getMethod("withId", id.getClass());
            return withId.invoke(resource, id);
        } catch (ReflectiveOperationException ignored) {
        }
        for (Method method : resource.getClass().getMethods()) {
            if (method.getName().equals("withId") && method.getParameterCount() == 1) {
                try {
                    return method.invoke(resource, id);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot apply Cerbos resource id using withId(...)", exception);
                }
            }
        }
        return resource;
    }

    private Object invoke(Object target, String methodName, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                try {
                    return method.invoke(target, arg);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot invoke " + target.getClass().getName() + "." + methodName + "(...)", exception);
                }
            }
        }
        throw new IllegalArgumentException("Cannot find " + target.getClass().getName() + "." + methodName + "(...)");
    }

    private Object unwrap(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElseThrow();
        }
        return value;
    }
}
