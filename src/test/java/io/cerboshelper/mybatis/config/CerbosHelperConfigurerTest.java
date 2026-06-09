package io.cerboshelper.mybatis.config;

import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.check.CerbosAccessDeniedHandler;
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
    void configClassCanOverrideAutoCheckScope() {
        CerbosHelperConfigurer configurer = new CerbosHelperConfigurer();
        CerbosHelperProperties properties = new CerbosHelperProperties();

        new CerbosHelperConfig() {
            @Override
            public void configure(CerbosHelperConfigurer configurer) {
                configurer.autoCheck(auto -> {
                    auto.setIncludeClassNamePatterns(List.of(".*CommandService"));
                    auto.setExcludeClassNamePatterns(List.of(".*UtilService"));
                });
            }
        }.configure(configurer);

        configurer.apply(properties);

        assertEquals(List.of(".*CommandService"), properties.getCheck().getAuto().getIncludeClassNamePatterns());
        assertEquals(List.of(".*UtilService"), properties.getCheck().getAuto().getExcludeClassNamePatterns());
    }
}
