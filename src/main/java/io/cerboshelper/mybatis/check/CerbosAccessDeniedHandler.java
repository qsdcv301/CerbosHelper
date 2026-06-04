package io.cerboshelper.mybatis.check;

@FunctionalInterface
public interface CerbosAccessDeniedHandler {
    RuntimeException denied(CerbosDeniedDecision decision);

    static CerbosAccessDeniedHandler securityException() {
        return decision -> new SecurityException(decision.message());
    }
}
