package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;

public class CerbosResourceCheckExecutor {
    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosAccessDeniedHandler accessDeniedHandler;

    public CerbosResourceCheckExecutor(CerbosAuthorizationClient authorizationClient, CerbosPrincipalResolver principalResolver, CerbosAccessDeniedHandler accessDeniedHandler) {
        this.authorizationClient = authorizationClient;
        this.principalResolver = principalResolver;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    public void authorizeMappedResource(String statementId, String action, Object resource) {
        Object principal = principalResolver.currentPrincipal().orElse(null);
        if (principal == null) {
            throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                    CerbosFailureReason.MISSING_PRINCIPAL,
                    action,
                    null,
                    resource,
                    "null",
                    describe(resource)
            ));
        }
        validateOwner(action, principal, resource);
        if (!authorizationClient.isAllowed(principal, resource, action)) {
            throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                    CerbosFailureReason.DENIED,
                    action,
                    principal,
                    resource,
                    describe(principal),
                    statementId + ":" + describe(resource)
            ));
        }
    }

    private void validateOwner(String action, Object principal, Object resource) {
        if (!(resource instanceof CerbosCommonDto commonResource)) {
            return;
        }
        boolean missingOwnerBy = commonResource.getOwnerBy() == null || commonResource.getOwnerBy().isBlank();
        boolean missingOwnerGroupBy = commonResource.getOwnerGroupBy() == null;
        if (!missingOwnerBy && !missingOwnerGroupBy) {
            return;
        }
        throw accessDeniedHandler.denied(new CerbosDeniedDecision(
                CerbosFailureReason.MISSING_OWNER,
                action,
                principal,
                resource,
                describe(principal),
                describe(resource)
        ));
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
