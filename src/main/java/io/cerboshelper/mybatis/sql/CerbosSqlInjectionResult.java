package io.cerboshelper.mybatis.sql;

public record CerbosSqlInjectionResult(String sql, int parameterInsertionIndex) {
}
