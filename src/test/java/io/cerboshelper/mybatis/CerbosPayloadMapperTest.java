package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.auth.CerbosPrincipalEnvelope;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.model.CerbosId;
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

    @Test
    void principalEnvelopeBuilderSupportsExtensibleAttributes() {
        CerbosPrincipalEnvelope envelope = CerbosPrincipalEnvelope.builder("user-1")
                .attr("userId", "user-1")
                .attr("displayName", "User One")
                .attr("organizationTreeIds", List.of(100L, 101L))
                .attr("organizationTreeNames", List.of("Sales", "Sales Team 1"))
                .build();

        Map<String, Object> payload = payloadMapper.principalPayload(envelope);

        assertEquals("user-1", payload.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attr = (Map<String, Object>) payload.get("attr");
        assertEquals("User One", attr.get("displayName"));
        assertEquals(List.of(100L, 101L), attr.get("organizationTreeIds"));
        assertEquals(List.of("Sales", "Sales Team 1"), attr.get("organizationTreeNames"));
    }


    @Test
    void resourcePayloadSupportsCommonDtoWithoutResourceAnnotation() {
        CerbosPayloadMapper mapper = new CerbosPayloadMapper(
                new CerbosHelperProperties(),
                new CerbosCommonResourceRegistry(List.of(Memo.class))
        );

        Map<String, Object> payload = mapper.resourcePayload(new Memo(10L, "user-1", 100L));

        assertEquals("10", payload.get("id"));
        assertEquals("memo", payload.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attr = (Map<String, Object>) payload.get("attr");
        assertEquals("user-1", attr.get("ownerBy"));
        assertEquals(100L, attr.get("ownerOrgBy"));
    }

    @Test
    void resourcePayloadUsesCerbosIdAnnotationForNonStandardPrimaryKey() {
        CerbosPayloadMapper mapper = new CerbosPayloadMapper(
                new CerbosHelperProperties(),
                new CerbosCommonResourceRegistry(List.of(UserMemo.class))
        );

        Map<String, Object> payload = mapper.resourcePayload(new UserMemo(15, "user-1", 100L));

        assertEquals("15", payload.get("id"));
        assertEquals("userMemo", payload.get("kind"));
    }

    private static class Memo extends CerbosCommonDto {
        private final long id;

        private Memo(long id, String ownerBy, Long ownerOrgBy) {
            this.id = id;
            setOwnerBy(ownerBy);
            setOwnerOrgBy(ownerOrgBy);
        }
    }

    private static class UserMemo extends CerbosCommonDto {
        @CerbosId
        private final Integer userMemoId;

        private UserMemo(Integer userMemoId, String ownerBy, Long ownerOrgBy) {
            this.userMemoId = userMemoId;
            setOwnerBy(ownerBy);
            setOwnerOrgBy(ownerOrgBy);
        }
    }
}
