package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.convention.CerbosCheckConventionResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosCheckConventionResolverTest {
    private final CerbosCheckConventionResolver resolver = new CerbosCheckConventionResolver(
            new CerbosCommonResourceRegistry(List.of(DocumentDto.class))
    );
    private final CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(new DefaultListableBeanFactory());

    @Test
    void createMethodsAreNotAutoCheckTargets() throws Exception {
        Method method = DocumentService.class.getDeclaredMethod("createDocument", DocumentDto.class);

        assertTrue(resolver.resolve(method, expressionEvaluator.context(method, new Object[]{new DocumentDto(1)})).isEmpty());
    }

    @Test
    void readMethodsResolveViewWithoutPreloadResourceId() throws Exception {
        Method method = DocumentService.class.getDeclaredMethod("getDocument", long.class);

        var check = resolver.resolve(method, expressionEvaluator.context(method, new Object[]{1L})).orElseThrow();

        assertEquals("view", check.action());
        assertEquals("", check.resourceKind());
        assertEquals("", check.id());
        assertEquals("findById", check.finder());
    }

    @Test
    void updateMethodsStillResolveExistingResourceCheck() throws Exception {
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", long.class, DocumentDto.class);

        var check = resolver.resolve(method, expressionEvaluator.context(method, new Object[]{1L, new DocumentDto(1)})).orElseThrow();

        assertEquals("update", check.action());
        assertEquals("", check.resourceKind());
        assertEquals("documentId", check.id());
        assertEquals("findById", check.finder());
    }

    static class DocumentService {
        @SuppressWarnings("unused")
        void createDocument(DocumentDto document) {
        }

        @SuppressWarnings("unused")
        DocumentDto getDocument(long documentId) {
            return null;
        }

        @SuppressWarnings("unused")
        void updateDocument(long documentId, DocumentDto document) {
        }
    }

    static class DocumentDto extends CerbosCommonDto {
        private final long id;

        DocumentDto(long id) {
            this.id = id;
        }

        public long id() {
            return id;
        }
    }
}
