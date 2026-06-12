package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.config.CerbosMethodRuleOptions;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.rule.CerbosCheckRuleResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosCheckRuleResolverTest {
    @Test
    void mapperCommandIsNotCheckTargetWithoutConfiguredRules() throws Exception {
        CerbosCheckRuleResolver resolver = new CerbosCheckRuleResolver(
                new CerbosCommonResourceRegistry(List.of(DocumentDto.class)),
                new CerbosMethodRuleOptions()
        );
        Method method = DocumentMapper.class.getDeclaredMethod("update", DocumentDto.class);

        assertTrue(resolver.resolveCommand(method, new DocumentDto(1)).isEmpty());
    }

    @Test
    void excludedMapperCommandsDoNotResolveChecks() throws Exception {
        CerbosCheckRuleResolver resolver = resolver(methods -> {
            methods.excludePrefixes("admin");
            methods.before("edit", "adminUpdate");
        });
        Method method = DocumentMapper.class.getDeclaredMethod("adminUpdate", DocumentDto.class);

        assertTrue(resolver.resolveCommand(method, new DocumentDto(1)).isEmpty());
    }

    @Test
    void mapperCommandResolvesRegisteredDtoParameter() throws Exception {
        CerbosCheckRuleResolver resolver = resolver(methods -> methods.before("edit", "update"));
        Method method = DocumentMapper.class.getDeclaredMethod("update", DocumentDto.class);
        DocumentDto document = new DocumentDto(1);

        var check = resolver.resolveCommand(method, document).orElseThrow();

        assertEquals("edit", check.action());
        assertEquals(document, check.resource());
    }

    @Test
    void mapperCommandResolvesRegisteredDtoFromMyBatisParameterMap() throws Exception {
        CerbosCheckRuleResolver resolver = resolver(methods -> methods.before("edit", "update"));
        Method method = DocumentMapper.class.getDeclaredMethod("updateWithAudit", DocumentDto.class, String.class);
        DocumentDto document = new DocumentDto(1);

        var check = resolver.resolveCommand(method, java.util.Map.of("document", document, "auditUser", "system")).orElseThrow();

        assertEquals("edit", check.action());
        assertEquals(document, check.resource());
    }

    @Test
    void registeredResourceMapperFailsWhenCommandHasNoOwnerDtoParameter() throws Exception {
        CerbosCheckRuleResolver resolver = resolver(methods -> methods.before("delete", "delete"));
        Method method = DocumentMapper.class.getDeclaredMethod("delete", long.class);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolveCommand(method, 1L));
    }

    @Test
    void unrelatedMapperCommandIsIgnoredEvenWhenPrefixMatches() throws Exception {
        CerbosCheckRuleResolver resolver = resolver(methods -> methods.before("create", "insert"));
        Method method = UserMapper.class.getDeclaredMethod("insertUserTenant", UserTenantDto.class);

        assertTrue(resolver.resolveCommand(method, new UserTenantDto()).isEmpty());
    }

    private CerbosCheckRuleResolver resolver(java.util.function.Consumer<CerbosMethodRuleOptions> customizer) {
        CerbosMethodRuleOptions rules = new CerbosMethodRuleOptions();
        customizer.accept(rules);
        return new CerbosCheckRuleResolver(
                new CerbosCommonResourceRegistry(List.of(DocumentDto.class)),
                rules
        );
    }

    interface DocumentMapper {
        int update(DocumentDto document);

        int updateWithAudit(DocumentDto document, String auditUser);

        int adminUpdate(DocumentDto document);

        int delete(long id);
    }

    static class DocumentDto extends CerbosCommonDto {
        private final long documentId;

        DocumentDto(long documentId) {
            this.documentId = documentId;
        }
    }

    interface UserMapper {
        int insertUserTenant(UserTenantDto userTenant);
    }

    static class UserTenantDto {
    }
}
