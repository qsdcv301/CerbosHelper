package io.cerboshelper.mybatis.check;

public record CerbosCheckSpec(
        String action,
        String principal,
        String resource,
        String resourceKind,
        String id,
        String mapper,
        String finder
) {
    public CerbosCheckSpec {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        principal = principal == null ? "" : principal;
        resource = resource == null ? "" : resource;
        resourceKind = resourceKind == null ? "" : resourceKind;
        id = id == null ? "" : id;
        mapper = mapper == null ? "" : mapper;
        finder = finder == null || finder.isBlank() ? "findById" : finder;
    }
}
