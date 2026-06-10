package io.cerboshelper.mybatis.check;

public record CerbosCheckSpec(
        String action,
        String principal,
        String resource
) {
    public CerbosCheckSpec {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        principal = principal == null ? "" : principal;
        resource = resource == null ? "" : resource;
    }
}
