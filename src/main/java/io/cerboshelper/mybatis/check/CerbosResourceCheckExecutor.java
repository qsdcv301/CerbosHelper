package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CerbosResourceCheckExecutor {
    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosAccessDeniedHandler accessDeniedHandler;
    private final CerbosResourceResolver resourceResolver;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosResourceCheckExecutor(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler, CerbosResourceResolver resourceResolver) {
        this.authorizationClient = authorizationClient;
        this.principalResolver = principalResolver;
        this.accessDeniedHandler = accessDeniedHandler;
        this.resourceResolver = resourceResolver;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    public void authorize(Method method, Object[] args, CerbosMethodExpressionEvaluator.Context context, CerbosCheckSpec check) {
        authorizeResources(method, context, check, requireResources(method, resourceResolver.resolve(new CerbosResourceResolutionRequest(check, method, args, context))));
    }

    public void authorizeReturnedResource(Method method, CerbosMethodExpressionEvaluator.Context context, CerbosCheckSpec check, Object result) {
        List<Object> resources = returnedResources(result);
        if (resources.isEmpty()) {
            return;
        }
        authorizeResources(method, context, check, resources);
    }

    public CerbosMethodExpressionEvaluator.Context context(Method method, Object[] args) {
        return expressionEvaluator.context(method, args);
    }

    private void authorizeResources(Method method, CerbosMethodExpressionEvaluator.Context context, CerbosCheckSpec check, List<Object> resources) {
        Object principal = expressionEvaluator.principal(check.principal(), context, principalResolver);
        if (principal == null) {
            throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                    CerbosFailureReason.MISSING_PRINCIPAL,
                    check.action(),
                    null,
                    null,
                    "null",
                    "not-resolved"
            ));
        }
        for (Object resource : resources) {
            validateOwner(check, principal, resource);
            if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                        CerbosFailureReason.DENIED,
                        check.action(),
                        principal,
                        resource,
                        describe(principal),
                        describe(resource)
                ));
            }
        }
    }

    private void validateOwner(CerbosCheckSpec check, Object principal, Object resource) {
        if (!(resource instanceof CerbosCommonDto commonResource)) {
            return;
        }
        boolean missingOwnerBy = commonResource.getOwnerBy() == null || commonResource.getOwnerBy().isBlank();
        boolean missingOwnerOrgBy = commonResource.getOwnerOrgBy() == null;
        if (!missingOwnerBy || !missingOwnerOrgBy) {
            return;
        }
        throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                CerbosFailureReason.MISSING_OWNER,
                check.action(),
                principal,
                resource,
                describe(principal),
                describe(resource)
        ));
    }

    private List<Object> returnedResources(Object result) {
        List<Object> resources = new ArrayList<>();
        if (result == null) {
            return resources;
        }
        if (result instanceof Optional<?> optional) {
            optional.ifPresent(value -> addResource(resources, value));
            return resources;
        }
        addResource(resources, result);
        return resources;
    }

    private void addResource(List<Object> resources, Object resource) {
        if (resource instanceof CerbosCommonDto) {
            resources.add(resource);
        }
    }

    private List<Object> requireResources(Method method, List<Object> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource for " + method.getDeclaringClass().getName() + "." + method.getName()
                    + "(). Register a CerbosResourceResolver bean or pass a CerbosCommonDto argument with ownerBy/ownerOrgBy values.");
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
        if (CerbosCommonResourceRegistry.isCommonResourceType(value.getClass())) {
            return value.getClass().getSimpleName() + "(kind=" + decapitalize(value.getClass().getSimpleName()) + ")";
        }
        return value.getClass().getSimpleName();
    }

    private String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + value.substring(1);
    }
}
