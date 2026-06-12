package io.cerboshelper.mybatis.config;

import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CerbosHelperConfigurerTest {
    @Test
    void configClassCanProvidePrincipalAndDeniedHandler() {
        CerbosAccessDeniedHandler handler = CerbosAccessDeniedHandler.securityException();
        CerbosHelperConfigurer configurer = new CerbosHelperConfigurer();

        new CerbosHelperConfig() {
            @Override
            public void configure(CerbosHelperConfigurer configurer) {
                configurer
                        .principalResolver(() -> Optional.of(new CerbosPrincipalEnvelope("7", List.of("user"), Map.of(), "default")))
                        .accessDeniedHandler(handler);
            }
        }.configure(configurer);

        assertEquals("7", ((CerbosPrincipalEnvelope) configurer.principalResolver().orElseThrow().currentPrincipal().orElseThrow()).id());
        assertSame(handler, configurer.accessDeniedHandler().orElseThrow());
    }

    @Test
    void configClassCanDeclareResourcesAndMethodRules() {
        CerbosHelperConfigurer configurer = new CerbosHelperConfigurer();

        new CerbosHelperConfig() {
            @Override
            public void configure(CerbosHelperConfigurer configurer) {
                configurer
                        .resources(resources -> resources.resource("document", DocumentDto.class))
                        .methodRules(methods -> {
                            methods.scope("read", "find", "list");
                            methods.before("edit", "update");
                            methods.excludeNames("findById");
                        });
            }
        }.configure(configurer);

        assertEquals("document", configurer.resources().resources().get(0).resourceKind());
        assertEquals(List.of("find", "list"), configurer.methodRules().scopeRules().get(0).methodNamePrefixes());
        assertEquals("edit", configurer.methodRules().checkRules().get(0).action());
        assertEquals(List.of("findById"), configurer.methodRules().excludeMethodNames());
    }

    static class DocumentDto extends CerbosCommonDto {
    }
}
