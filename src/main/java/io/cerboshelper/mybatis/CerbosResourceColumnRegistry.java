package io.cerboshelper.mybatis;

import java.util.Optional;

public interface CerbosResourceColumnRegistry {
    Optional<String> columnFor(String resourceKind, String cerbosVariable);
}
