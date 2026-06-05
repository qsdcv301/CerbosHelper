package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultCerbosResourceResolverTest {
    @Test
    void idAndDtoArgumentsResolveExistingResourceWithStrippedDtoResourceKind() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("documentMapper", new DocumentMapper());
        CerbosMethodExpressionEvaluator expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
        DefaultCerbosResourceResolver resolver = new DefaultCerbosResourceResolver(beanFactory, expressionEvaluator);
        Method method = DocumentService.class.getDeclaredMethod("updateDocument", long.class, DocumentDto.class);
        DocumentDto update = new DocumentDto(7);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, new Object[]{7L, update});
        CerbosCheckSpec check = new CerbosCheckSpec("update", "", "document", "", "documentId", "", "findById");

        List<Object> resources = resolver.resolve(new CerbosResourceResolutionRequest(check, method, new Object[]{7L, update}, context));

        assertEquals(2, resources.size());
        assertInstanceOf(DocumentDto.class, resources.get(0));
        assertEquals(7, ((DocumentDto) resources.get(0)).id());
        assertSame(update, resources.get(1));
    }

    static class DocumentService {
        @SuppressWarnings("unused")
        void updateDocument(long documentId, DocumentDto document) {
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
}
