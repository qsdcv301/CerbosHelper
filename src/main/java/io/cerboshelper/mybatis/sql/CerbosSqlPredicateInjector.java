package io.cerboshelper.mybatis.sql;

@FunctionalInterface
public interface CerbosSqlPredicateInjector {
    CerbosSqlInjectionResult inject(String sql, String predicate);
}
