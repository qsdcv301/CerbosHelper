package io.cerboshelper.mybatis;

@FunctionalInterface
public interface CerbosAccessDeniedHandler {
    RuntimeException denied(CerbosDeniedDecision decision);

    static CerbosAccessDeniedHandler securityException() {
        return decision -> new SecurityException(decision.message());
    }
}
