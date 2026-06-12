package io.cerboshelper.mybatis.check;

import com.fasterxml.jackson.databind.JsonNode;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosResourceCheckExecutorTest {
    @Test
    void mappedResourceCheckPassesDtoDirectlyToAuthorizationClient() {
        CerbosPrincipalResolver principalResolver = () -> Optional.of(
                new CerbosPrincipalEnvelope("42", List.of("authenticated"), Map.of(), "default")
        );
        AtomicReference<DocumentDto> authorizedResource = new AtomicReference<>();
        AtomicReference<String> authorizedAction = new AtomicReference<>();
        CerbosResourceCheckExecutor executor = new CerbosResourceCheckExecutor(
                new AllowingAuthorizationClient(authorizedResource, authorizedAction),
                principalResolver,
                CerbosAccessDeniedHandler.securityException()
        );
        DocumentDto document = new DocumentDto(7);
        document.setOwnerBy("42");
        document.setOwnerGroupBy(100L);

        executor.authorizeMappedResource("test.DocumentMapper.update", "update", document);

        assertEquals("update", authorizedAction.get());
        assertEquals(7, authorizedResource.get().documentId);
        assertEquals("42", authorizedResource.get().getOwnerBy());
    }

    @Test
    void mappedResourceCheckFailsBeforeCerbosCallWhenOwnerAttributesAreMissing() {
        CerbosPrincipalResolver principalResolver = () -> Optional.of(
                new CerbosPrincipalEnvelope("42", List.of("authenticated"), Map.of(), "default")
        );
        CerbosResourceCheckExecutor executor = new CerbosResourceCheckExecutor(
                new AllowingAuthorizationClient(new AtomicReference<>(), new AtomicReference<>()),
                principalResolver,
                CerbosAccessDeniedHandler.securityException()
        );

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> executor.authorizeMappedResource("test.DocumentMapper.update", "update", new DocumentDto(7))
        );

        assertTrue(exception.getMessage().contains("reason=MISSING_OWNER"));
    }

    @Test
    void mappedResourceCheckFailsBeforeCerbosCallWhenEitherOwnerAttributeIsMissing() {
        CerbosPrincipalResolver principalResolver = () -> Optional.of(
                new CerbosPrincipalEnvelope("42", List.of("authenticated"), Map.of(), "default")
        );
        CerbosResourceCheckExecutor executor = new CerbosResourceCheckExecutor(
                new AllowingAuthorizationClient(new AtomicReference<>(), new AtomicReference<>()),
                principalResolver,
                CerbosAccessDeniedHandler.securityException()
        );
        DocumentDto document = new DocumentDto(7);
        document.setOwnerBy("42");

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> executor.authorizeMappedResource("test.DocumentMapper.update", "update", document)
        );

        assertTrue(exception.getMessage().contains("reason=MISSING_OWNER"));
    }

    static class DocumentDto extends CerbosCommonDto {
        private final long documentId;

        DocumentDto(long documentId) {
            this.documentId = documentId;
        }
    }

    static class AllowingAuthorizationClient implements CerbosAuthorizationClient {
        private final AtomicReference<DocumentDto> authorizedResource;
        private final AtomicReference<String> authorizedAction;

        AllowingAuthorizationClient(AtomicReference<DocumentDto> authorizedResource, AtomicReference<String> authorizedAction) {
            this.authorizedResource = authorizedResource;
            this.authorizedAction = authorizedAction;
        }

        @Override
        public JsonNode planResources(Object principal, String resourceKind, String action) {
            return null;
        }

        @Override
        public boolean isAllowed(Object principal, Object resource, String action) {
            authorizedResource.set((DocumentDto) resource);
            authorizedAction.set(action);
            return true;
        }

        @Override
        public Map<String, String> checkResources(Object principal, List<?> resources, String action) {
            return Map.of();
        }

        @Override
        public JsonNode checkResourcesRaw(Object principal, List<?> resources, String action) {
            return null;
        }
    }
}
