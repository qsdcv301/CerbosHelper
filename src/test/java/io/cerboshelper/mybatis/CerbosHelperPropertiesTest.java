package io.cerboshelper.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosHelperPropertiesTest {
    @Test
    void usesExplicitGrpcTargetWhenConfigured() {
        CerbosHelperProperties properties = new CerbosHelperProperties();
        properties.setBaseUrl("http://cerbos:3592");
        properties.setTarget("cerbos-grpc:3593");

        assertEquals("cerbos-grpc:3593", properties.getTarget());
    }

    @Test
    void derivesGrpcTargetFromLegacyRestBaseUrl() {
        CerbosHelperProperties properties = new CerbosHelperProperties();
        properties.setBaseUrl("http://cerbos:3592");

        assertEquals("cerbos:3593", properties.getTarget());
    }
}
