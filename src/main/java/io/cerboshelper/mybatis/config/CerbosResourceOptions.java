package io.cerboshelper.mybatis.config;

import io.cerboshelper.mybatis.convention.CerbosCommonResource;
import io.cerboshelper.mybatis.model.CerbosCommonDto;

import java.util.ArrayList;
import java.util.List;

public final class CerbosResourceOptions {
    private final List<CerbosCommonResource> resources = new ArrayList<>();

    public CerbosResourceOptions resource(String resourceKind, Class<? extends CerbosCommonDto> resourceType) {
        return resource(resourceKind, resourceType, resourceKind);
    }

    public CerbosResourceOptions resource(String resourceKind, Class<? extends CerbosCommonDto> resourceType, String sqlAlias) {
        resources.add(new CerbosCommonResource(requireResourceKind(resourceKind), resourceType, sqlAlias));
        return this;
    }

    public List<CerbosCommonResource> resources() {
        return List.copyOf(resources);
    }

    private String requireResourceKind(String resourceKind) {
        if (resourceKind == null || resourceKind.isBlank()) {
            throw new IllegalArgumentException("resourceKind must not be blank");
        }
        return resourceKind;
    }
}
