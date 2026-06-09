package io.cerboshelper.mybatis.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosClientOptionsTest {
    @Test
    void targetFallsBackToGrpcPortFromHttpBaseUrl() {
        CerbosClientOptions options = new CerbosClientOptions();
        options.setBaseUrl("http://cerbos:3592");

        assertEquals("cerbos:3593", options.getTarget());
    }

    @Test
    void explicitTargetWinsOverBaseUrl() {
        CerbosClientOptions options = new CerbosClientOptions();
        options.setBaseUrl("http://cerbos:3592");
        options.setTarget("cerbos:3593");

        assertEquals("cerbos:3593", options.getTarget());
    }
}
