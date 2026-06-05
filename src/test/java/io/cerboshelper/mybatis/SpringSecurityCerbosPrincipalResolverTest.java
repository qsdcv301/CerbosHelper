package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.auth.SpringSecurityCerbosPrincipalResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityCerbosPrincipalResolverTest {
    @Test
    void returnsEmptyWhenSpringSecurityIsNotOnClasspath() {
        assertTrue(new SpringSecurityCerbosPrincipalResolver().currentPrincipal().isEmpty());
    }
}
