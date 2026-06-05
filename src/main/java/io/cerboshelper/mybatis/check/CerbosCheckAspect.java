package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

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

    public void authorize(Method method, Object[] args, CerbosMethodExpressionEvaluator.Context context, CerbosCheckSpec check) {
        Object principal = expressionEvaluator.principal(check.principal(), context, principalResolver);
        for (Object resource : requireResources(method, resourceResolver.resolve(new CerbosResourceResolutionRequest(check, method, args, context)))) {
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

    public CerbosMethodExpressionEvaluator.Context context(Method method, Object[] args) {
        return expressionEvaluator.context(method, args);
    }

    private List<Object> requireResources(Method method, List<Object> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource for " + method.getDeclaringClass().getName() + "." + method.getName()
                    + "(). Register a CerbosResourceResolver bean, pass a CerbosCommonDto argument, "
                    + "or use a {resource}Id parameter that matches a {resource}Mapper.findById(...) bean.");
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
        if (CerbosCommonResourceRegistry.isCommonResourceType(value.getClass())) {
            return value.getClass().getSimpleName() + "(kind=" + decapitalize(value.getClass().getSimpleName()) + ", id=" + id + ")";
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

    private String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + value.substring(1);
    }

}
