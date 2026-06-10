package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultCerbosResourceResolverTest {
    @Test
    void updateUsesIncomingDtoWithoutExistingResourceLookup() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", long.class, DocumentDto.class);
        DocumentDto update = new DocumentDto(7);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{7L, update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "document");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{7L, update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertSame(update, resources.get(0));
        assertEquals(7, ((DocumentDto) resources.get(0)).documentId);
    }

    @Test
    void dtoResourceExpressionUsesIncomingDto() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", DocumentDto.class);
        DocumentDto update = new DocumentDto(9);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "document");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertSame(update, resources.get(0));
        assertEquals(9, ((DocumentDto) resources.get(0)).documentId);
    }

    @Test
    void dtoDoesNotNeedIdAnnotation() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updatePrivateMemo", PrivateMemoDto.class);
        PrivateMemoDto update = new PrivateMemoDto(30);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "privateMemo");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(PrivateMemoDto.class, resources.get(0));
        assertSame(update, resources.get(0));
        assertEquals(30, ((PrivateMemoDto) resources.get(0)).privateMemoId);
    }

    @Test
    void nullDtoIdDoesNotBlockOwnerBasedResourceResolution() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updatePrivateMemo", PrivateMemoDto.class);
        PrivateMemoDto update = new PrivateMemoDto(null);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "privateMemo");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context));

        assertEquals(1, resources.size());
        assertSame(update, resources.get(0));
    }

    static class DocumentService {
        @SuppressWarnings("unused")
        void updateDocument(long documentId, DocumentDto document) {
        }

        @SuppressWarnings("unused")
        void updateDocument(DocumentDto document) {
        }

        @SuppressWarnings("unused")
        void updatePrivateMemo(PrivateMemoDto privateMemo) {
        }
    }

    static class DocumentDto extends CerbosCommonDto {
        private final long documentId;

        DocumentDto(long documentId) {
            this.documentId = documentId;
        }
    }

    static class PrivateMemoDto extends CerbosCommonDto {
        private final Integer privateMemoId;

        PrivateMemoDto(Integer privateMemoId) {
            this.privateMemoId = privateMemoId;
        }
    }
}
