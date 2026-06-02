package io.cerboshelper.mybatis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CerbosPrincipalEnvelope(String id, List<String> roles, Map<String, Object> attr, String policyVersion) {
    public CerbosPrincipalEnvelope {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cerbos principal envelope id is required");
        }
        roles = roles == null || roles.isEmpty() ? List.of("authenticated") : List.copyOf(roles);
        attr = attr == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(attr));
        policyVersion = policyVersion == null || policyVersion.isBlank() ? "default" : policyVersion;
    }

    public static CerbosPrincipalEnvelope of(String id, List<String> roles, Map<String, Object> attr) {
        return new CerbosPrincipalEnvelope(id, roles, attr, "default");
    }
}
