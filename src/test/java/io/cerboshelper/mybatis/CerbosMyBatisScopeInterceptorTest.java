package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.convention.CerbosScopeConventionResolver;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.scope.CerbosMyBatisScopeInterceptor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CerbosMyBatisScopeInterceptorTest {
    @Test
    void resolvesPageHelperCountStatementBackToConventionScopedMapperMethod() throws Exception {
        CerbosMyBatisScopeInterceptor interceptor = new CerbosMyBatisScopeInterceptor(
                null,
                null,
                null,
                null,
                new CerbosScopeConventionResolver(new CerbosCommonResourceRegistry(List.of(DocumentResource.class)))
        );
        Method findScopedMethod = CerbosMyBatisScopeInterceptor.class.getDeclaredMethod("findScopedMethod", MappedStatement.class);
        findScopedMethod.setAccessible(true);

        Object scopedMethod = findScopedMethod.invoke(
                interceptor,
                mappedStatement(ConventionMapper.class.getName() + ".findDocuments_COUNT")
        );

        assertNotNull(scopedMethod);
    }

    private MappedStatement mappedStatement(String id) {
        Configuration configuration = new Configuration();
        return new MappedStatement.Builder(
                configuration,
                id,
                parameter -> new BoundSql(configuration, "SELECT 1", List.of(), parameter),
                SqlCommandType.SELECT
        ).build();
    }

    interface ConventionMapper {
        List<DocumentResource> findDocuments();
    }

    static class DocumentResource extends CerbosCommonDto {
        private long id;
    }
}
