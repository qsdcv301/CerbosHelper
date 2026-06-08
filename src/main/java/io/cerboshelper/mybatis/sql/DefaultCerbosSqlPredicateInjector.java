package io.cerboshelper.mybatis.sql;

import java.util.Optional;

public class DefaultCerbosSqlPredicateInjector implements CerbosSqlPredicateInjector {
    private static final String CERBOS_ALIAS = "cb";

    @Override
    public Optional<String> predicateAlias(String sql, String resourceKind) {
        return Optional.of(CERBOS_ALIAS);
    }

    @Override
    public CerbosSqlInjectionResult inject(String sql, String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return new CerbosSqlInjectionResult(sql, countPlaceholders(sql));
        }

        String originalSql = stripTrailingSemicolon(sql.strip());
        String scopedSql = "SELECT " + CERBOS_ALIAS + ".* FROM (" + originalSql + ") " + CERBOS_ALIAS
                + " WHERE (" + predicate + ")";
        return new CerbosSqlInjectionResult(scopedSql, countPlaceholders(originalSql));
    }

    private int countPlaceholders(String sql) {
        int count = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }

    private String stripTrailingSemicolon(String sql) {
        return sql.endsWith(";") ? sql.substring(0, sql.length() - 1).stripTrailing() : sql;
    }
}
