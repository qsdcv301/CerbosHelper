package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cerboshelper.mybatis.auth.CerbosAuthorizationClient;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.check.CerbosResourceCheckExecutor;
import io.cerboshelper.mybatis.config.CerbosMethodRuleOptions;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.rule.CerbosCheckRuleResolver;
import io.cerboshelper.mybatis.rule.CerbosScopeRuleResolver;
import io.cerboshelper.mybatis.scope.CerbosMyBatisInterceptor;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CerbosMyBatisInterceptorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void resolvesPageHelperCountStatementBackToConventionScopedMapperMethod() throws Exception {
        CerbosMyBatisInterceptor interceptor = new CerbosMyBatisInterceptor(
                null,
                null,
                null,
                null,
                scopeResolver(methods -> methods.scope("read", "find"))
        );
        Method findScopedMethod = CerbosMyBatisInterceptor.class.getDeclaredMethod("findScopedMethod", MappedStatement.class);
        findScopedMethod.setAccessible(true);

        Object scopedMethod = findScopedMethod.invoke(
                interceptor,
                mappedStatement(ConventionMapper.class.getName() + ".findDocuments_COUNT")
        );

        assertNotNull(scopedMethod);
    }

    @Test
    void doesNotResolveScopedMapperWithoutConfiguredRules() throws Exception {
        CerbosMyBatisInterceptor interceptor = new CerbosMyBatisInterceptor(
                null,
                null,
                null,
                null,
                new CerbosScopeRuleResolver(new CerbosCommonResourceRegistry(List.of(DocumentResource.class)), new CerbosMethodRuleOptions())
        );
        Method findScopedMethod = CerbosMyBatisInterceptor.class.getDeclaredMethod("findScopedMethod", MappedStatement.class);
        findScopedMethod.setAccessible(true);

        Object scopedMethod = findScopedMethod.invoke(
                interceptor,
                mappedStatement(ConventionMapper.class.getName() + ".findDocuments")
        );

        org.junit.jupiter.api.Assertions.assertNull(scopedMethod);
    }

    @Test
    void appliesPlanPredicateWithInjectorProvidedAlias() throws Exception {
        CerbosMyBatisInterceptor interceptor = new CerbosMyBatisInterceptor(
                new PlanAuthorizationClient(),
                new CerbosPlanToSqlConverter(CerbosResourceColumns.builder()
                        .resource("document", DocumentResource.class)
                        .build()),
                new DefaultCerbosSqlPredicateInjector(),
                () -> Optional.of("user-1"),
                null
        );
        Method applyCerbosScope = CerbosMyBatisInterceptor.class.getDeclaredMethod(
                "applyCerbosScope",
                MappedStatement.class,
                BoundSql.class,
                String.class,
                String.class,
                Object.class
        );
        applyCerbosScope.setAccessible(true);
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(
                configuration,
                "test.DocumentMapper.findDocuments",
                parameter -> new BoundSql(configuration, "SELECT 1", List.of(), parameter),
                SqlCommandType.SELECT
        ).build();
        BoundSql boundSql = new BoundSql(
                configuration,
                "SELECT d.* FROM document d WHERE d.deleted = ? ORDER BY d.id",
                List.of(new ParameterMapping.Builder(configuration, "deleted", Boolean.class).build()),
                new Object()
        );

        BoundSql scoped = (BoundSql) applyCerbosScope.invoke(
                interceptor,
                statement,
                boundSql,
                "document",
                "view",
                "user-1"
        );

        assertEquals(
                "SELECT __cerbos_scope.* FROM (SELECT d.* FROM document d WHERE d.deleted = ? ORDER BY d.id) __cerbos_scope WHERE (__cerbos_scope.owner_by = ?)",
                scoped.getSql()
        );
        assertEquals(2, scoped.getParameterMappings().size());
        assertEquals("user-1", scoped.getAdditionalParameter("__cerbos_scope_param_0"));
    }

    @Test
    void appliesCommandCheckFromMapperUpdateStatement() throws Exception {
        CerbosMethodRuleOptions rules = new CerbosMethodRuleOptions();
        rules.before("edit", "update");
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(DocumentResource.class));
        RecordingAuthorizationClient authorizationClient = new RecordingAuthorizationClient();
        CerbosResourceCheckExecutor checkExecutor = new CerbosResourceCheckExecutor(
                authorizationClient,
                () -> Optional.of("user-1"),
                CerbosAccessDeniedHandler.securityException()
        );
        CerbosMyBatisInterceptor interceptor = new CerbosMyBatisInterceptor(
                null,
                null,
                null,
                null,
                null,
                new CerbosCheckRuleResolver(registry, rules),
                checkExecutor
        );
        Method applyCerbosCommandCheck = CerbosMyBatisInterceptor.class.getDeclaredMethod(
                "applyCerbosCommandCheck",
                MappedStatement.class,
                Object.class
        );
        applyCerbosCommandCheck.setAccessible(true);
        DocumentResource document = new DocumentResource();
        document.setOwnerBy("user-1");
        document.setOwnerGroupBy(100L);

        applyCerbosCommandCheck.invoke(
                interceptor,
                mappedStatement(CommandMapper.class.getName() + ".update", SqlCommandType.UPDATE),
                document
        );

        assertEquals("edit", authorizationClient.action);
        assertEquals(document, authorizationClient.resource);
    }

    private MappedStatement mappedStatement(String id) {
        return mappedStatement(id, SqlCommandType.SELECT);
    }

    private MappedStatement mappedStatement(String id, SqlCommandType sqlCommandType) {
        Configuration configuration = new Configuration();
        return new MappedStatement.Builder(
                configuration,
                id,
                parameter -> new BoundSql(configuration, "SELECT 1", List.of(), parameter),
                sqlCommandType
        ).build();
    }

    private CerbosScopeRuleResolver scopeResolver(java.util.function.Consumer<CerbosMethodRuleOptions> customizer) {
        CerbosMethodRuleOptions rules = new CerbosMethodRuleOptions();
        customizer.accept(rules);
        return new CerbosScopeRuleResolver(new CerbosCommonResourceRegistry(List.of(DocumentResource.class)), rules);
    }

    interface ConventionMapper {
        List<DocumentResource> findDocuments();
    }

    interface CommandMapper {
        int update(DocumentResource document);
    }

    static class DocumentResource extends CerbosCommonDto {
        private long id;
    }

    static class PlanAuthorizationClient implements CerbosAuthorizationClient {
        @Override
        public JsonNode planResources(Object principal, String resourceKind, String action) {
            try {
                return OBJECT_MAPPER.readTree("""
                        {
                          "filter": {
                            "kind": "KIND_CONDITIONAL",
                            "condition": {
                              "expression": {
                                "operator": "eq",
                                "operands": [
                                  {"variable": "request.resource.attr.ownerBy"},
                                  {"value": "user-1"}
                                ]
                              }
                            }
                          }
                        }
                        """);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public boolean isAllowed(Object principal, Object resource, String action) {
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

    static class RecordingAuthorizationClient extends PlanAuthorizationClient {
        private Object resource;
        private String action;

        @Override
        public boolean isAllowed(Object principal, Object resource, String action) {
            this.resource = resource;
            this.action = action;
            return true;
        }
    }
}
