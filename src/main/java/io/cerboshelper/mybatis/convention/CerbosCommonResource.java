package io.cerboshelper.mybatis.convention;

public record CerbosCommonResource(
        String resourceKind,
        Class<?> resourceType,
        String sqlAlias
) {
    public CerbosCommonResource {
        if (resourceKind == null || resourceKind.isBlank()) {
            throw new IllegalArgumentException("resourceKind must not be blank");
        }
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType must not be null");
        }
        sqlAlias = sqlAlias == null || sqlAlias.isBlank() ? resourceKind : sqlAlias;
    }
}
