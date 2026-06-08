package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCerbosResourceResolverTest {
    @Test
    void idAndDtoArgumentsResolveOnlyExistingResourceForUpdate() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("documentMapper", new DocumentMapper());
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", long.class, DocumentDto.class);
        DocumentDto update = new DocumentDto(7);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{7L, update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "document", "", "documentId", "", "findById");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{7L, update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertEquals(7, ((DocumentDto) resources.get(0)).id());
    }

    @Test
    void dtoIdExpressionResolvesExistingResourceForUpdate() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("documentMapper", new DocumentMapper());
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", DocumentDto.class);
        DocumentDto update = new DocumentDto(9);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "document", "", "#document.id", "", "findById");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertEquals(9, ((DocumentDto) resources.get(0)).id());
    }

    @Test
    void createUsesRequestResource() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("createDocument", DocumentDto.class);
        DocumentDto create = new DocumentDto(0);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{create});
        CerbosCheckSpec check = new CerbosCheckSpec("create", "", "document", "", "", "", "findById");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{create}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertEquals(0, ((DocumentDto) resources.get(0)).id());
    }

    @Test
    void registeredResultMapIdPropertyCanResolvePrivateDtoFieldWithoutGetIdOverride() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("privateMemoMapper", new PrivateMemoMapper());
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(PrivateMemoDto.class));
        registry.registerIdProperty(PrivateMemoDto.class, "privateMemoId");
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator, registry);
        Method method = DocumentService.class.getDeclaredMethod("updatePrivateMemo", PrivateMemoDto.class);
        PrivateMemoDto update = new PrivateMemoDto(30);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "privateMemo", "", "#privateMemo.privateMemoId", "", "findById");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context));

        assertEquals(1, resources.size());
        assertInstanceOf(PrivateMemoDto.class, resources.get(0));
        assertEquals(30, ((PrivateMemoDto) resources.get(0)).privateMemoId);
    }

    @Test
    void registeredResultMapIdPropertyFailsClosedWhenExistingResourceIdIsNull() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(PrivateMemoDto.class));
        registry.registerIdProperty(PrivateMemoDto.class, "privateMemoId");
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator, registry);
        Method method = DocumentService.class.getDeclaredMethod("updatePrivateMemo", PrivateMemoDto.class);
        PrivateMemoDto update = new PrivateMemoDto(null);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "privateMemo", "", "#privateMemo.privateMemoId", "", "findById");

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{update}, context))
        );
    }

    static class DocumentService {
        @SuppressWarnings("unused")
        void updateDocument(long documentId, DocumentDto document) {
        }

        @SuppressWarnings("unused")
        void updateDocument(DocumentDto document) {
        }

        @SuppressWarnings("unused")
        void createDocument(DocumentDto document) {
        }

        @SuppressWarnings("unused")
        void updatePrivateMemo(PrivateMemoDto privateMemo) {
        }
    }

    static class DocumentMapper {
        @SuppressWarnings("unused")
        public Optional<DocumentDto> findById(long id) {
            return Optional.of(new DocumentDto(id));
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

    static class PrivateMemoMapper {
        @SuppressWarnings("unused")
        public Optional<PrivateMemoDto> findById(Integer id) {
            return Optional.of(new PrivateMemoDto(id));
        }
    }

    static class PrivateMemoDto extends CerbosCommonDto {
        private final Integer privateMemoId;

        PrivateMemoDto(Integer privateMemoId) {
            this.privateMemoId = privateMemoId;
        }
    }
}
