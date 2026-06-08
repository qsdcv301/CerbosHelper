package io.cerboshelper.mybatis.sql;

import java.util.Optional;

@FunctionalInterface
public interface CerbosSqlPredicateInjector {
    default Optional<String> predicateAlias(String sql, String resourceKind) {
        return Optional.empty();
    }

    CerbosSqlInjectionResult inject(String sql, String predicate);
}
