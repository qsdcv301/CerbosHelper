package io.cerboshelper.mybatis;

import java.util.Optional;

public interface CerbosResourceColumnRegistry {
    Optional<String> columnFor(String resourceKind, String cerbosVariable);

    static CerbosResourceColumns.Builder builder() {
        return CerbosResourceColumns.builder();
    }
}
