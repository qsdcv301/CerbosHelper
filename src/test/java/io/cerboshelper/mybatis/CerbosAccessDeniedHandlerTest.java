package io.cerboshelper.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosAccessDeniedHandlerTest {
    @Test
    void defaultHandlerCreatesSecurityException() {
        RuntimeException exception = CerbosAccessDeniedHandler.securityException()
                .denied(new CerbosDeniedDecision("update", "principal", "resource", "principal-1", "document-1"));

        assertInstanceOf(SecurityException.class, exception);
        assertTrue(exception.getMessage().contains("action=update"));
        assertTrue(exception.getMessage().contains("principal=principal-1"));
        assertTrue(exception.getMessage().contains("resource=document-1"));
    }
}
