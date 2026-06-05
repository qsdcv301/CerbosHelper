package io.cerboshelper.mybatis.auth;

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

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private List<String> roles = List.of("authenticated");
        private String policyVersion = "default";
        private final Map<String, Object> attr = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder policyVersion(String policyVersion) {
            this.policyVersion = policyVersion;
            return this;
        }

        public Builder attr(String name, Object value) {
            attr.put(name, value);
            return this;
        }

        public Builder attrs(Map<String, Object> values) {
            if (values != null) {
                attr.putAll(values);
            }
            return this;
        }

        public CerbosPrincipalEnvelope build() {
            return new CerbosPrincipalEnvelope(id, roles, attr, policyVersion);
        }
    }
}
