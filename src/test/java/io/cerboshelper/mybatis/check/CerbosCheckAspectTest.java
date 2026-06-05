package io.cerboshelper.mybatis.check;

import com.fasterxml.jackson.databind.JsonNode;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosCheckAspectTest {
    @Test
    void createCheckAppliesOwnerDefaultsBeforeAuthorization() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosPrincipalResolver principalResolver = () -> Optional.of(
                new CerbosPrincipalEnvelope("42", List.of("authenticated"), Map.of(), "default")
        );
        AtomicReference<DocumentDto> authorizedResource = new AtomicReference<>();
        CerbosAuthorizationClient authorizationClient = new AllowingAuthorizationClient(authorizedResource);
        CerbosCheckAspect aspect = new CerbosCheckAspect(
                authorizationClient,
                beanFactory,
                principalResolver,
                CerbosAccessDeniedHandler.securityException(),
                new DefaultCerbosResourceResolver(beanFactory, new CerbosMethodExpressionEvaluator(beanFactory))
        );
        Method method = DocumentService.class.getDeclaredMethod("createDocument", DocumentDto.class);
        DocumentDto document = new DocumentDto("new", 7);
        CerbosMethodExpressionEvaluator.Context context = aspect.context(method, new Object[]{document});

        aspect.authorize(method, new Object[]{document}, context, new CerbosCheckSpec("create", "", "document", "", "", "", "findById"));

        assertEquals("42", authorizedResource.get().getOwnerBy());
        assertEquals(7L, authorizedResource.get().getOwnerOrgBy());
    }

    static class DocumentService {
        @SuppressWarnings("unused")
        void createDocument(DocumentDto document) {
        }
    }

    static class DocumentDto extends CerbosCommonDto {
        private final String id;
        private final Integer orgId;

        DocumentDto(String id, Integer orgId) {
            this.id = id;
            this.orgId = orgId;
        }

        public String getId() {
            return id;
        }

        public Integer getOrgId() {
            return orgId;
        }
    }

    static class AllowingAuthorizationClient implements CerbosAuthorizationClient {
        private final AtomicReference<DocumentDto> authorizedResource;

        AllowingAuthorizationClient(AtomicReference<DocumentDto> authorizedResource) {
            this.authorizedResource = authorizedResource;
        }

        @Override
        public JsonNode planResources(Object principal, String resourceKind, String action) {
            return null;
        }

        @Override
        public boolean isAllowed(Object principal, Object resource, String action) {
            authorizedResource.set((DocumentDto) resource);
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
