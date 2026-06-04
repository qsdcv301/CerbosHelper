package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CerbosPayloadMapperTest {
    private final CerbosPayloadMapper payloadMapper = new CerbosPayloadMapper(new CerbosHelperProperties());

    @Test
    void preservesPrincipalEnvelopePayload() {
        CerbosPrincipalEnvelope envelope = new CerbosPrincipalEnvelope(
                "user-1",
                List.of("SYSTEM_ADMIN", "TENANT_ADMIN"),
                Map.of("tenantId", 10L, "tenantAdmin", true),
                "default"
        );

        Map<String, Object> payload = payloadMapper.principalPayload(envelope);

        assertEquals("user-1", payload.get("id"));
        assertEquals(List.of("SYSTEM_ADMIN", "TENANT_ADMIN"), payload.get("roles"));
        assertEquals("default", payload.get("policyVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attr = (Map<String, Object>) payload.get("attr");
        assertEquals(10L, attr.get("tenantId"));
        assertEquals(true, attr.get("tenantAdmin"));
    }

    @Test
    void principalEnvelopeAllowsNullAttributeValues() {
        Map<String, Object> attr = new java.util.LinkedHashMap<>();
        attr.put("tenantId", null);
        CerbosPrincipalEnvelope envelope = CerbosPrincipalEnvelope.of("user-1", List.of("authenticated"), attr);

        Map<String, Object> payload = payloadMapper.principalPayload(envelope);

        @SuppressWarnings("unchecked")
        Map<String, Object> payloadAttr = (Map<String, Object>) payload.get("attr");
        assertNull(payloadAttr.get("tenantId"));
    }
}
