package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.annotation.CerbosCheck;
import io.cerboshelper.mybatis.annotation.CerbosChecks;
import io.cerboshelper.mybatis.annotation.CerbosResource;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@Aspect
public class CerbosCheckAspect {
    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosAccessDeniedHandler accessDeniedHandler;
    private final CerbosResourceResolver resourceResolver;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler, CerbosResourceResolver resourceResolver) {
        this.authorizationClient = authorizationClient;
        this.principalResolver = principalResolver;
        this.accessDeniedHandler = accessDeniedHandler;
        this.resourceResolver = resourceResolver;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.annotation.CerbosCheck) || @annotation(io.cerboshelper.mybatis.annotation.CerbosChecks)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosCheck[] checks = method.getAnnotationsByType(CerbosCheck.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        for (CerbosCheck check : checks) {
            Object principal = expressionEvaluator.principal(check.principal(), context, principalResolver);
            for (Object resource : requireResources(method, resourceResolver.resolve(new CerbosResourceResolutionRequest(check, method, joinPoint.getArgs(), context)))) {
                if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                    throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                            check.action(),
                            principal,
                            resource,
                            describe(principal),
                            describe(resource)
                    ));
                }
            }
        }
        return joinPoint.proceed();
    }

    private List<Object> requireResources(Method method, List<Object> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource for " + method.getDeclaringClass().getName() + "." + method.getName()
                    + "(). Register a CerbosResourceResolver bean, use a @CerbosResource method argument, "
                    + "@CerbosCheck(resourceKind = \"...\", id = \"...\"), or @CerbosCheck(resource = \"...\").");
        }
        return resources;
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CerbosPrincipalEnvelope envelope) {
            return value.getClass().getSimpleName() + "(id=" + envelope.id() + ", roles=" + envelope.roles() + ")";
        }
        String id = readId(value).map(Object::toString).orElse("unknown");
        CerbosResource resource = value.getClass().getAnnotation(CerbosResource.class);
        if (resource != null) {
            return value.getClass().getSimpleName() + "(kind=" + resource.kind() + ", id=" + id + ")";
        }
        return value.getClass().getSimpleName() + "(id=" + id + ")";
    }

    private Optional<Object> readId(Object value) {
        for (String methodName : List.of("id", "getId")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                return Optional.ofNullable(method.invoke(value));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

}
